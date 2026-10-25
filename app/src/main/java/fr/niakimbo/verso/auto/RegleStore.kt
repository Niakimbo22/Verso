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

    fun nouvelId(): String = "regle-" + java.lang.Long.toString(System.currentTimeMillis(), 36)

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

    /** Jour (epochDay) du dernier réveil déclenché : un seul par jour. */
    var dernierReveil: Long
        get() = prefs.getLong(CLE_REVEIL, -1)
        set(v) = prefs.edit().putLong(CLE_REVEIL, v).apply()

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

    companion object {
        private const val CLE_ACTIF = "actif"
        private const val CLE_REGLES = "regles"
        private const val CLE_EN_COURS = "en_cours"
        private const val CLE_EN_COURS_MODELE = "en_cours_modele"
        private const val CLE_EN_COURS_REVENIR = "en_cours_revenir"
        private const val CLE_BASE = "base"
        private const val CLE_REVEIL = "dernier_reveil"

        /** Format documenté dans VERSO_SPEC §3.6. */
        fun versJson(r: Regle): JSONObject = JSONObject().apply {
            put("id", r.id)
            put("nom", r.nom)
            put("modele", r.modele ?: JSONObject.NULL)
            put("active", r.active)
            put("revenir", r.revenir)
            put("quand", JSONObject().apply {
                put("type", r.quand.type)
                when (val d = r.quand) {
                    is Declencheur.Plage -> {
                        put("debut", d.debut)
                        put("fin", d.fin)
                        put("jours", JSONArray(d.jours.sorted().map { it.value }))
                    }
                    is Declencheur.Reveil -> {
                        put("debut", d.debut)
                        put("fin", d.fin)
                    }
                    is Declencheur.BatterieFaible -> put("seuil", d.seuil)
                    is Declencheur.Casque -> d.appareil?.let { put("appareil", it) }
                    Declencheur.Charge, Declencheur.NePasDeranger -> Unit
                }
            })
        }

        fun depuisJson(o: JSONObject): Regle? {
            val q = o.optJSONObject("quand") ?: return null
            val quand: Declencheur = when (q.optString("type")) {
                Declencheur.PLAGE -> Declencheur.Plage(
                    q.optInt("debut", 22 * 60).coerceIn(0, 1439),
                    q.optInt("fin", 7 * 60).coerceIn(0, 1439),
                    q.optJSONArray("jours")?.let { a ->
                        (0 until a.length()).mapNotNull { i -> a.optInt(i).takeIf { it in 1..7 }?.let(DayOfWeek::of) }.toSet()
                    }?.takeIf { it.isNotEmpty() } ?: Declencheur.TOUS_LES_JOURS,
                )
                Declencheur.REVEIL -> Declencheur.Reveil(q.optInt("debut", 300).coerceIn(0, 1439), q.optInt("fin", 720).coerceIn(0, 1439))
                Declencheur.CHARGE -> Declencheur.Charge
                Declencheur.BATTERIE -> Declencheur.BatterieFaible(q.optInt("seuil", 20).coerceIn(1, 99))
                Declencheur.CASQUE -> Declencheur.Casque(q.optString("appareil").takeIf { it.isNotBlank() })
                Declencheur.DND -> Declencheur.NePasDeranger
                else -> return null
            }
            return Regle(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: return null,
                nom = o.optString("nom"),
                quand = quand,
                modele = if (o.isNull("modele")) null else o.optString("modele"),
                active = o.optBoolean("active", true),
                revenir = o.optBoolean("revenir", true),
            )
        }
    }
}
