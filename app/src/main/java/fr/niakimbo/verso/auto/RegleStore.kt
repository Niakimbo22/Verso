package fr.niakimbo.verso.auto

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/**
 * Règles automatiques (ordre de la liste = priorité) et mémoire du moteur.
 * Tout est relu au démarrage du processus : le moteur reprend là où il en était.
 */
class RegleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso_auto", Context.MODE_PRIVATE)

    private val _regles = MutableStateFlow(lire())
    val regles: StateFlow<List<Regle>> = _regles

    private val _actif = MutableStateFlow(prefs.getBoolean(CLE_ACTIF, true))

    /** Interrupteur général « Automatique ». */
    val actif: StateFlow<Boolean> = _actif

    fun setActif(v: Boolean) {
        prefs.edit().putBoolean(CLE_ACTIF, v).apply()
        _actif.value = v
    }

    private val _lieu = MutableStateFlow(lireLieu())

    /** Ville pour le lever / coucher du soleil (null = pas encore choisie). */
    val lieu: StateFlow<Lieu?> = _lieu

    fun setLieu(l: Lieu?) {
        prefs.edit().apply {
            if (l == null) remove(CLE_LIEU) else putString(CLE_LIEU, JSONObject().put("nom", l.nom).put("lat", l.lat).put("lon", l.lon).toString())
        }.apply()
        _lieu.value = l
    }

    @Synchronized
    fun enregistrer(r: Regle) {
        val liste = _regles.value.toMutableList()
        val i = liste.indexOfFirst { it.id == r.id }
        if (i >= 0) liste[i] = r else liste.add(r)
        ecrire(liste)
    }

    @Synchronized
    fun supprimer(id: String) = ecrire(_regles.value.filter { it.id != id })

    @Synchronized
    fun ordonner(ids: List<String>) {
        val parId = _regles.value.associateBy { it.id }
        val liste = ids.mapNotNull { parId[it] } + _regles.value.filter { it.id !in ids }
        if (liste != _regles.value) ecrire(liste)
    }

    fun nouvelId(): String = "regle-" + java.lang.Long.toString(System.nanoTime() xor System.currentTimeMillis(), 36).trimStart('-')

    // --- Mémoire du moteur (voir Decision) ---

    var memoire: Decision.Memoire
        get() {
            val enCours = prefs.getString(CLE_EN_COURS, null)?.let {
                Decision.EnCours(it, prefs.getString(CLE_EN_COURS_MODELE, null), prefs.getBoolean(CLE_EN_COURS_REVENIR, true))
            }
            return Decision.Memoire(enCours, prefs.getString(CLE_BASE, null))
        }
        set(m) {
            prefs.edit()
                .putString(CLE_EN_COURS, m.enCours?.signature)
                .putString(CLE_EN_COURS_MODELE, m.enCours?.modele)
                .putBoolean(CLE_EN_COURS_REVENIR, m.enCours?.revenir ?: true)
                .putString(CLE_BASE, m.base)
                .apply()
        }

    /** Jour (epochDay) où chaque règle « moment » s'est déclenchée pour la dernière fois : une fois par jour. */
    fun dernierMoment(id: String): Long = prefs.getLong(CLE_MOMENT + id, -1)
    fun setDernierMoment(id: String, jour: Long) = prefs.edit().putLong(CLE_MOMENT + id, jour).apply()

    /** Prochain changement de favori d'une règle « Défiler » (ms epoch, 0 = aucun). */
    var prochainDefilement: Long
        get() = prefs.getLong(CLE_DEFILER, 0L)
        set(v) = prefs.edit().putLong(CLE_DEFILER, v).apply()

    /** Appareils Bluetooth connectés (tenu à jour par [BluetoothRecepteur], même app fermée). */
    var bluetooth: Set<String>
        get() = prefs.getStringSet(CLE_BT, emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet(CLE_BT, v).apply()

    private fun ecrire(liste: List<Regle>) {
        prefs.edit().putString(CLE_REGLES, JSONArray(liste.map(::versJson)).toString()).apply()
        _regles.value = liste
    }

    private fun lire(): List<Regle> = try {
        val a = JSONArray(prefs.getString(CLE_REGLES, null) ?: "[]")
        (0 until a.length()).mapNotNull { depuisJson(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun lireLieu(): Lieu? = try {
        prefs.getString(CLE_LIEU, null)?.let { JSONObject(it) }?.let { Lieu(it.getString("nom"), it.getDouble("lat"), it.getDouble("lon")) }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CLE_ACTIF = "actif"
        private const val CLE_REGLES = "regles"
        private const val CLE_EN_COURS = "en_cours"
        private const val CLE_EN_COURS_MODELE = "en_cours_modele"
        private const val CLE_EN_COURS_REVENIR = "en_cours_revenir"
        private const val CLE_BASE = "base"
        private const val CLE_MOMENT = "moment_"
        private const val CLE_DEFILER = "prochain_defilement"
        private const val CLE_LIEU = "lieu"
        private const val CLE_BT = "bluetooth_connectes"

        /** Format documenté dans VERSO_SPEC §3.6. */
        fun versJson(r: Regle): JSONObject = JSONObject().apply {
            put("id", r.id)
            put("nom", r.nom)
            put("active", r.active)
            put("revenir", r.revenir)
            put("toutes", r.toutes)
            put("si", JSONArray(r.conditions.map(::declencheurJson)))
            put("alors", JSONObject().apply {
                when (val a = r.action) {
                    is Action.Afficher -> put("type", "modele").put("modele", a.modele)
                    Action.Eteindre -> put("type", "eteindre")
                    Action.Hasard -> put("type", "hasard")
                    is Action.Defiler -> put("type", "defiler").put("minutes", a.minutes)
                }
            })
        }

        private fun jours(j: Set<DayOfWeek>) = JSONArray(j.sorted().map { it.value })

        private fun declencheurJson(d: Declencheur): JSONObject = JSONObject().apply {
            put("type", d.type)
            when (d) {
                is Declencheur.Plage -> put("debut", d.debut).put("fin", d.fin).put("jours", jours(d.jours))
                is Declencheur.AHeure -> put("minute", d.minute).put("jours", jours(d.jours))
                is Declencheur.Reveil -> put("debut", d.debut).put("fin", d.fin)
                is Declencheur.Soleil -> put("nuit", d.nuit).put("decalage", d.decalage)
                is Declencheur.Date -> put("jour", d.jour).put("mois", d.mois)
                is Declencheur.AvantAlarme -> put("minutes", d.minutes)
                is Declencheur.Charge -> d.source?.let { put("source", it) }
                is Declencheur.BatterieFaible -> put("seuil", d.seuil)
                is Declencheur.BatteriePleine -> put("seuil", d.seuil)
                is Declencheur.Ecran -> put("verrouille", d.verrouille)
                is Declencheur.Sonnerie -> d.mode?.let { put("mode", it) }
                is Declencheur.Casque -> d.appareil?.let { put("appareil", it) }
                is Declencheur.Bluetooth -> d.appareil?.let { put("appareil", it) }
                is Declencheur.Reseau -> put("reseau", d.type_)
                Declencheur.Economie, Declencheur.NePasDeranger, Declencheur.Avion, Declencheur.Lampe -> Unit
            }
        }

        private fun lireJours(q: JSONObject): Set<DayOfWeek> =
            q.optJSONArray("jours")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optInt(i).takeIf { it in 1..7 }?.let(DayOfWeek::of) }.toSet()
            }?.takeIf { it.isNotEmpty() } ?: Declencheur.TOUS_LES_JOURS

        private fun minute(q: JSONObject, cle: String, defaut: Int) = q.optInt(cle, defaut).coerceIn(0, 1439)
        private fun texte(q: JSONObject, cle: String) = q.optString(cle).takeIf { it.isNotBlank() }

        private fun depuisJsonDeclencheur(q: JSONObject): Declencheur? = when (q.optString("type")) {
            Declencheur.PLAGE -> Declencheur.Plage(minute(q, "debut", 22 * 60), minute(q, "fin", 7 * 60), lireJours(q))
            Declencheur.A_HEURE -> Declencheur.AHeure(minute(q, "minute", 18 * 60), lireJours(q))
            Declencheur.REVEIL -> Declencheur.Reveil(minute(q, "debut", 300), minute(q, "fin", 720))
            Declencheur.SOLEIL -> Declencheur.Soleil(q.optBoolean("nuit", true), q.optInt("decalage", 0).coerceIn(-180, 180))
            Declencheur.DATE -> Declencheur.Date(q.optInt("jour", 1).coerceIn(1, 31), q.optInt("mois", 1).coerceIn(1, 12))
            Declencheur.ALARME -> Declencheur.AvantAlarme(q.optInt("minutes", 30).coerceIn(1, 720))
            Declencheur.CHARGE -> Declencheur.Charge(texte(q, "source"))
            Declencheur.BATTERIE -> Declencheur.BatterieFaible(q.optInt("seuil", 20).coerceIn(1, 99))
            Declencheur.PLEINE -> Declencheur.BatteriePleine(q.optInt("seuil", 100).coerceIn(50, 100))
            Declencheur.ECONOMIE -> Declencheur.Economie
            Declencheur.ECRAN -> Declencheur.Ecran(q.optBoolean("verrouille", true))
            Declencheur.DND -> Declencheur.NePasDeranger
            Declencheur.SONNERIE -> Declencheur.Sonnerie(texte(q, "mode"))
            Declencheur.AVION -> Declencheur.Avion
            Declencheur.LAMPE -> Declencheur.Lampe
            Declencheur.CASQUE -> Declencheur.Casque(texte(q, "appareil"))
            Declencheur.BLUETOOTH -> Declencheur.Bluetooth(texte(q, "appareil"))
            Declencheur.RESEAU -> Declencheur.Reseau(texte(q, "reseau") ?: Declencheur.WIFI)
            else -> null
        }

        fun depuisJson(o: JSONObject): Regle? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            // Format v1 (partie 1) : une seule condition « quand » et un « modele ».
            val conditions = o.optJSONArray("si")?.let { a -> (0 until a.length()).mapNotNull { depuisJsonDeclencheur(a.getJSONObject(it)) } }
                ?: listOfNotNull(o.optJSONObject("quand")?.let(::depuisJsonDeclencheur))
            if (conditions.isEmpty()) return null
            val action = o.optJSONObject("alors")?.let { a ->
                when (a.optString("type")) {
                    "modele" -> texte(a, "modele")?.let { Action.Afficher(it) } ?: Action.Eteindre
                    "hasard" -> Action.Hasard
                    "defiler" -> Action.Defiler(a.optInt("minutes", 15).coerceIn(1, 1440))
                    else -> Action.Eteindre
                }
            } ?: if (o.isNull("modele") || !o.has("modele")) Action.Eteindre else Action.Afficher(o.optString("modele"))
            return Regle(
                id = id,
                nom = o.optString("nom"),
                conditions = conditions,
                action = action,
                toutes = o.optBoolean("toutes", true),
                active = o.optBoolean("active", true),
                revenir = o.optBoolean("revenir", true),
            )
        }
    }
}
