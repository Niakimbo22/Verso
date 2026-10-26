package fr.niakimbo.verso.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Réglages d'affichage de l'app (pas de la matrice). */
object ReglagesUi {
    private var prefs: SharedPreferences? = null

    /** Lueurs du fond qui dérivent toutes seules (20 images/s). Coupé par défaut : voir Verre.kt. */
    var fondAnime by mutableStateOf(false)
        private set

    /** Texte du dernier banc de fluidité (gardé pour pouvoir le relire / le capturer plus tard). */
    var derniereMesure by mutableStateOf<String?>(null)
        private set

    fun charger(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("verso_ui", Context.MODE_PRIVATE)
        prefs = p
        fondAnime = p.getBoolean("fond_anime", false)
        derniereMesure = p.getString("derniere_mesure", null)
    }

    fun changerFondAnime(v: Boolean) {
        fondAnime = v
        prefs?.edit()?.putBoolean("fond_anime", v)?.apply()
    }

    fun enregistrerMesure(texte: String) {
        derniereMesure = texte
        prefs?.edit()?.putString("derniere_mesure", texte)?.apply()
    }
}

/**
 * Mode « ancien moteur », uniquement pendant le banc de fluidité : reproduit les causes de lenteur
 * de la v0.1.67 (fond animé à 20 i/s dessiné point par point, effets du verre recréés à chaque
 * recomposition, grille des 90 modèles non paresseuse, aperçus et écran entier recomposés à chaque
 * image de la matrice) pour comparer sur le même téléphone, au même moment.
 */
object ModePerf {
    var ancien by mutableStateOf(false)
}

/**
 * Mesure des vraies images affichées par la fenêtre, avec l'API officielle d'Android
 * (`Window.addOnFrameMetricsAvailableListener`) : une entrée par image réellement dessinée.
 * « En retard » = l'image a dépassé l'échéance donnée par le système (`FrameMetrics.DEADLINE`).
 */
class Enregistreur(private val window: Window) {
    private val totaux = ArrayList<Long>()
    private val gpu = ArrayList<Long>()
    private var retards = 0
    private val verrou = Any()
    private val fil = HandlerThread("verso-mesure").apply { start() }
    private val ecoute = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = m.getMetric(FrameMetrics.TOTAL_DURATION)
        val echeance = m.getMetric(FrameMetrics.DEADLINE)
        synchronized(verrou) {
            totaux += total
            gpu += m.getMetric(FrameMetrics.GPU_DURATION)
            if (echeance in 1 until total) retards++
        }
    }
    private var debut = 0L

    fun demarrer() {
        debut = SystemClock.elapsedRealtime()
        window.addOnFrameMetricsAvailableListener(ecoute, Handler(fil.looper))
    }

    fun arreter(): Mesure.Resultat {
        val duree = SystemClock.elapsedRealtime() - debut
        runCatching { window.removeOnFrameMetricsAvailableListener(ecoute) }
        fil.quitSafely()
        return synchronized(verrou) { Mesure.calculer(totaux.toList(), gpu.toList(), retards, duree) }
    }
}

object Mesure {
    data class Resultat(
        val images: Int,
        val dureeMs: Long,
        val medianeMs: Double,
        val p95Ms: Double,
        val gpuMedianMs: Double,
        val retards: Int,
    ) {
        val parSeconde: Double get() = if (dureeMs > 0) images * 1000.0 / dureeMs else 0.0
        val pourcentRetard: Double get() = if (images > 0) 100.0 * retards / images else 0.0
    }

    fun calculer(totauxNs: List<Long>, gpuNs: List<Long>, retards: Int, dureeMs: Long) = Resultat(
        images = totauxNs.size,
        dureeMs = dureeMs,
        medianeMs = centile(totauxNs, 0.5) / 1e6,
        p95Ms = centile(totauxNs, 0.95) / 1e6,
        gpuMedianMs = centile(gpuNs, 0.5) / 1e6,
        retards = retards,
    )

    fun centile(valeurs: List<Long>, q: Double): Double {
        if (valeurs.isEmpty()) return 0.0
        val tri = valeurs.sorted()
        val i = ((tri.size - 1) * q).toInt().coerceIn(0, tri.size - 1)
        return tri[i].toDouble()
    }
}

/**
 * Banc de fluidité du Diagnostic : le même parcours, deux fois (ancien moteur puis nouveau),
 * sans toucher l'écran. ~35 s. Les branchements sont posés par l'écran principal.
 */
class BancFluidite {
    data class Ligne(val scene: String, val ancien: Mesure.Resultat, val nouveau: Mesure.Resultat)

    var enCours by mutableStateOf(false)
        private set
    var etape by mutableStateOf("")
        private set

    var defilerPage: (suspend (versLeBas: Boolean, ms: Int) -> Unit)? = null
    var ouvrirBibliotheque: ((Boolean) -> Unit)? = null
    var defilerBibliotheque: (suspend (px: Float, ms: Int) -> Unit)? = null

    /** Posé par la bibliothèque à sa première image affichée. */
    @Volatile var ouverteA = 0L

    suspend fun lancer(window: Window) {
        if (enCours) return
        enCours = true
        val parMode = mutableMapOf<Boolean, List<Pair<String, Mesure.Resultat>>>()
        val ouverture = mutableMapOf<Boolean, Long>()
        try {
            for (ancien in listOf(true, false)) {
                val nom = if (ancien) "ancien moteur" else "nouveau moteur"
                ModePerf.ancien = ancien
                ouvrirBibliotheque?.invoke(false)
                defilerPage?.invoke(false, 300)
                delay(1200)
                val lignes = mutableListOf<Pair<String, Mesure.Resultat>>()

                etape = "1/3 · repos ($nom)"
                lignes += "Repos 3 s" to mesurer(window) { delay(3000) }

                etape = "2/3 · écran principal ($nom)"
                lignes += "Écran principal" to mesurer(window) {
                    defilerPage?.invoke(true, 2000)
                    defilerPage?.invoke(false, 2000)
                }

                etape = "3/3 · bibliothèque ($nom)"
                ouverteA = 0L
                val t0 = SystemClock.uptimeMillis()
                ouvrirBibliotheque?.invoke(true)
                var attente = 0
                while (ouverteA == 0L && attente < 100) { delay(20); attente++ }
                if (ouverteA != 0L) ouverture[ancien] = ouverteA - t0
                delay(900)
                lignes += "Bibliothèque (90)" to mesurer(window) {
                    defilerBibliotheque?.invoke(3500f, 2500)
                    defilerBibliotheque?.invoke(-3500f, 2500)
                }
                ouvrirBibliotheque?.invoke(false)
                delay(600)
                parMode[ancien] = lignes
            }
        } finally {
            ModePerf.ancien = false
            etape = ""
            enCours = false
        }
        val a = parMode[true].orEmpty()
        val n = parMode[false].orEmpty()
        val lignes = a.zip(n).map { (x, y) -> Ligne(x.first, x.second, y.second) }
        ReglagesUi.enregistrerMesure(rapport(lignes, ouverture[true], ouverture[false], window))
    }

    private suspend fun mesurer(window: Window, action: suspend () -> Unit): Mesure.Resultat {
        val e = Enregistreur(window)
        e.demarrer()
        action()
        return e.arreter()
    }

    private fun rapport(lignes: List<Ligne>, ouvAncien: Long?, ouvNouveau: Long?, window: Window): String {
        val hz = window.decorView.display?.refreshRate?.toInt() ?: 0
        val f = java.util.Locale.FRANCE
        return buildString {
            append("Banc de fluidité · ${java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')} · écran $hz Hz\n")
            append("(ancien = causes de lenteur de la v0.1.67 reproduites ; nouveau = cette version)\n")
            lignes.forEach { l ->
                append("\n${l.scene}\n")
                fun ligne(nom: String, r: Mesure.Resultat) = append(
                    String.format(f, "  %-7s %4d images (%5.1f/s) · médiane %5.1f ms · 95%% < %5.1f ms · GPU %4.1f ms · en retard %4.1f %%\n",
                        nom, r.images, r.parSeconde, r.medianeMs, r.p95Ms, r.gpuMedianMs, r.pourcentRetard),
                )
                ligne("ancien", l.ancien)
                ligne("nouveau", l.nouveau)
            }
            if (ouvAncien != null || ouvNouveau != null) {
                append("\nOuverture de la bibliothèque : ancien ${ouvAncien ?: "?"} ms · nouveau ${ouvNouveau ?: "?"} ms\n")
            }
        }
    }
}

/** Temps d'ouverture de l'app : du lancement du processus à la première image de l'écran principal. */
object Demarrage {
    var ms by mutableStateOf<Long?>(null)
        private set

    private var fait = false

    /** Appelé à la première image ; ne compte que si le processus vient de démarrer (lancement à froid). */
    fun premiereImage() {
        if (fait) return
        fait = true
        val depuis = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
        if (depuis < 20_000) ms = depuis
    }
}
