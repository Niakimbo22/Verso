package fr.niakimbo.verso.auto

import android.content.Context
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** Le temps qu'il fait, lu chez Open-Meteo (gratuit, sans clé ni compte). */
data class EtatMeteo(
    /** Code météo WMO (0 = ciel clair, 61 = pluie…). */
    val code: Int,
    val temperature: Int,
    val jour: Boolean,
    /** Ville utilisée. */
    val lieu: String,
    /** Heure de la mesure (ms epoch). */
    val luA: Long,
) {
    /** Famille du ciel : [Meteo.SOLEIL], [Meteo.NUAGES], [Meteo.PLUIE], [Meteo.NEIGE] ou [Meteo.ORAGE]. */
    val ciel: String get() = Meteo.ciel(code)
}

/**
 * Météo réelle (étape 9, partie 3) : sert au modèle live « Météo » et à la condition « Météo ».
 * Rafraîchie au plus toutes les 30 min, seulement quand quelqu'un en a besoin (modèle affiché,
 * règle qui l'utilise). La dernière mesure est gardée sur le téléphone.
 */
object Meteo {
    const val SOLEIL = "soleil"
    const val NUAGES = "nuages"
    const val PLUIE = "pluie"
    const val NEIGE = "neige"
    const val ORAGE = "orage"
    val CIELS = listOf(SOLEIL, NUAGES, PLUIE, NEIGE, ORAGE)

    /** Ville par défaut (Nico) si aucune n'est choisie dans « Nuit / jour ». */
    val MEYLAN = Lieu("Meylan", 45.2097, 5.7792)

    /** Au-delà, la mesure est jugée vieille et redemandée. */
    const val VALIDITE_MS = 30 * 60_000L

    private val _etat = MutableStateFlow<EtatMeteo?>(null)
    val etat: StateFlow<EtatMeteo?> = _etat

    private val _erreur = MutableStateFlow<String?>(null)

    /** Dernière erreur (pas d'Internet…), null si la dernière demande a marché. */
    val erreur: StateFlow<String?> = _erreur

    private val verrou = Mutex()

    @Volatile
    private var app: VersoApp? = null

    @Volatile
    private var dernierEssai = 0L

    fun init(a: VersoApp) {
        app = a
        _etat.value = lire(a)
    }

    fun ciel(code: Int): String = when (code) {
        0, 1 -> SOLEIL
        in 51..67, in 80..82 -> PLUIE
        in 71..77, 85, 86 -> NEIGE
        in 95..99 -> ORAGE
        else -> NUAGES // 2, 3 (nuageux), 45, 48 (brouillard)
    }

    fun emoji(ciel: String?): String = when (ciel) {
        SOLEIL -> "☀️"
        NUAGES -> "☁️"
        PLUIE -> "🌧️"
        NEIGE -> "❄️"
        ORAGE -> "⛈️"
        else -> "🌡️"
    }

    fun nom(ciel: String): String = when (ciel) {
        SOLEIL -> "Soleil"
        NUAGES -> "Nuages"
        PLUIE -> "Pluie"
        NEIGE -> "Neige"
        ORAGE -> "Orage"
        else -> ciel
    }

    /** Mesure vieille (ou absente) ? */
    fun vieille(): Boolean = _etat.value?.let { System.currentTimeMillis() - it.luA > VALIDITE_MS } ?: true

    /**
     * Redemande la météo si elle est vieille, sans attendre (appelable 10 fois par seconde : il ne se
     * passe quelque chose qu'au plus une fois par minute en cas d'échec).
     */
    fun rafraichirSiVieille() {
        val a = app ?: return
        if (!vieille()) return
        if (System.currentTimeMillis() - dernierEssai < 60_000L) return
        dernierEssai = System.currentTimeMillis()
        a.appScope.launch { rafraichir(force = true) }
    }

    /** Demande la météo maintenant ([force]) ou si elle est vieille. Vrai si une nouvelle mesure est arrivée. */
    suspend fun rafraichir(force: Boolean = false): Boolean = verrou.withLock {
        val a = app ?: return false
        if (!force && !vieille()) return false
        dernierEssai = System.currentTimeMillis()
        val lieu = a.moteurRegles.store.lieu.value ?: MEYLAN
        val nouveau = try {
            telecharger(lieu)
        } catch (e: Exception) {
            _erreur.value = "Météo indisponible (Internet ?)"
            return false
        }
        _erreur.value = null
        val ancienCiel = _etat.value?.ciel
        _etat.value = nouveau
        ecrire(a, nouveau)
        if (nouveau.ciel != ancienCiel) a.moteurRegles.evaluer("météo")
        true
    }

    private suspend fun telecharger(lieu: Lieu): EtatMeteo = withContext(Dispatchers.IO) {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
                lieu.lat, lieu.lon,
            ),
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            EtatMeteo(
                code = o.getInt("weather_code"),
                temperature = o.getDouble("temperature_2m").roundToInt(),
                jour = o.optInt("is_day", 1) == 1,
                lieu = lieu.nom,
                luA = System.currentTimeMillis(),
            )
        } finally {
            c.disconnect()
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences("verso_meteo", Context.MODE_PRIVATE)

    private fun lire(c: Context): EtatMeteo? = try {
        prefs(c).getString("etat", null)?.let { JSONObject(it) }?.let {
            EtatMeteo(it.getInt("code"), it.getInt("t"), it.getBoolean("jour"), it.getString("lieu"), it.getLong("lu"))
        }
    } catch (_: Exception) {
        null
    }

    private fun ecrire(c: Context, e: EtatMeteo) = prefs(c).edit().putString(
        "etat",
        JSONObject().put("code", e.code).put("t", e.temperature).put("jour", e.jour).put("lieu", e.lieu).put("lu", e.luA).toString(),
    ).apply()
}
