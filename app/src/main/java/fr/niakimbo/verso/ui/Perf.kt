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
import kotlinx.coroutines.launch

/** Réglages d'affichage de l'app (pas de la matrice). */
object ReglagesUi {
    private var prefs: SharedPreferences? = null

    /** Lueurs du fond qui dérivent toutes seules (20 images/s). Coupé par défaut : voir Verre.kt. */
    var fondAnime by mutableStateOf(false)
        private set

    /** Texte du dernier banc de fluidité (gardé pour pouvoir le relire / le capturer plus tard). */
    var derniereMesure by mutableStateOf<String?>(null)
        private set

    /** Qualité du verre : réglée à la main (Plus) ou par l'optimisation automatique du banc. */
    enum class QualiteVerre(val label: String) { HAUTE("Haute"), EQUILIBREE("Équilibrée"), LEGERE("Légère") }

    var qualiteVerre by mutableStateOf(QualiteVerre.HAUTE)
        private set

    /** Aperçus animés dans la bibliothèque et l'accès rapide (la grande matrice et le mini-lecteur restent animés). */
    var apercusAnimes by mutableStateOf(true)
        private set

    fun changerQualiteVerre(q: QualiteVerre) {
        qualiteVerre = q
        prefs?.edit()?.putString("qualite_verre", q.name)?.apply()
    }

    fun changerApercusAnimes(v: Boolean) {
        apercusAnimes = v
        prefs?.edit()?.putBoolean("apercus_animes", v)?.apply()
    }

    fun charger(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("verso_ui", Context.MODE_PRIVATE)
        prefs = p
        qualiteVerre = QualiteVerre.entries.firstOrNull { it.name == p.getString("qualite_verre", null) } ?: QualiteVerre.HAUTE
        apercusAnimes = p.getBoolean("apercus_animes", true)
        fondAnime = p.getBoolean("fond_anime", false)
        derniereMesure = p.getString("derniere_mesure", null)
        rapport = p.getString("dernier_rapport", null)
    }

    fun changerFondAnime(v: Boolean) {
        fondAnime = v
        prefs?.edit()?.putBoolean("fond_anime", v)?.apply()
    }

    /** Dernier banc en JSON (affichage en barres). */
    var rapport by mutableStateOf<String?>(null)
        private set

    fun enregistrerRapport(json: String) {
        rapport = json
        prefs?.edit()?.putString("dernier_rapport", json)?.apply()
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
    /** Où part le temps de chaque image (étapes officielles de FrameMetrics, regroupées). */
    private val phases = Mesure.PHASES.associateWith { ArrayList<Long>() }
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
            phases.getValue(Mesure.ATTENTE).add(m.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
            phases.getValue(Mesure.ANIMATION).add(m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) + m.getMetric(FrameMetrics.ANIMATION_DURATION))
            phases.getValue(Mesure.MISE_EN_PAGE).add(m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
            phases.getValue(Mesure.DESSIN).add(m.getMetric(FrameMetrics.DRAW_DURATION))
            phases.getValue(Mesure.ENVOI).add(m.getMetric(FrameMetrics.SYNC_DURATION) + m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) + m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
            phases.getValue(Mesure.GPU).add(m.getMetric(FrameMetrics.GPU_DURATION))
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
        return synchronized(verrou) {
            Mesure.calculer(totaux.toList(), gpu.toList(), retards, duree)
                .copy(phases = phases.mapValues { (_, v) -> Mesure.centile(v, 0.5) / 1e6 })
        }
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
        /** Temps médian (ms) de chaque étape d'une image : voir [PHASES]. */
        val phases: Map<String, Double> = emptyMap(),
    ) {
        /** L'étape qui prend le plus de temps (et ses ms), en mots simples. */
        val plusLente: Pair<String, Double>? get() = phases.maxByOrNull { it.value }?.toPair()
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

    const val ATTENTE = "⏳ attente du système"
    const val ANIMATION = "👆 animations"
    const val MISE_EN_PAGE = "📐 mise en page"
    const val DESSIN = "✏️ dessin de l'écran"
    const val ENVOI = "🔄 envoi au GPU"
    const val GPU = "🎮 GPU"
    val PHASES = listOf(ATTENTE, ANIMATION, MISE_EN_PAGE, DESSIN, ENVOI, GPU)

    fun centile(valeurs: List<Long>, q: Double): Double {
        if (valeurs.isEmpty()) return 0.0
        val tri = valeurs.sorted()
        val i = ((tri.size - 1) * q).toInt().coerceIn(0, tri.size - 1)
        return tri[i].toDouble()
    }
}

/**
 * Banc de fluidité du Diagnostic : le même parcours, deux fois (ancien moteur puis nouveau),
 * sans toucher l'écran. Complet ≈ 35 s, rapide ≈ 15 s. Les branchements sont posés par l'écran principal.
 *
 * L'écran reste allumé pendant la mesure (`FLAG_KEEP_SCREEN_ON`, retiré à la fin, même en cas
 * d'annulation) : sinon la mise en veille automatique coupait la mesure au milieu.
 */
class BancFluidite {
    data class Ligne(val scene: String, val symbole: String, val ancien: Mesure.Resultat, val nouveau: Mesure.Resultat)

    /** Dernier banc complet, pour l'affichage en barres (gardé entre deux lancements). */
    data class Rapport(val quand: String, val hz: Int, val rapide: Boolean, val lignes: List<Ligne>, val ouvAncien: Long?, val ouvNouveau: Long?)

    var enCours by mutableStateOf(false)
        private set
    var etape by mutableStateOf("")
        private set
    /** 0 → 1 pendant la mesure (barre de progression). */
    var progression by mutableStateOf(0f)
        private set
    var dernier by mutableStateOf<Rapport?>(null)
        private set

    /** Ce que l'optimisation automatique a changé à la fin du dernier banc (vide = rien à changer). */
    var ajustements by mutableStateOf<List<String>>(emptyList())
        private set
    private var avantAjustement: Triple<ReglagesUi.QualiteVerre, Boolean, Boolean>? = null
    val peutAnnuler: Boolean get() = avantAjustement != null && ajustements.isNotEmpty()

    /**
     * Optimisation automatique, d'après le NOUVEAU moteur (ce que tu vis vraiment) :
     * - repos qui dessine encore beaucoup avec le fond animé → fond animé coupé ;
     * - écran principal en retard (> 8 % / > 20 %) → verre Équilibré (sans irisation) / Léger (moins de flou, sans ombre) ;
     * - bibliothèque en retard (> 8 %) → aperçus fixes dans la grille (la grande matrice reste animée) ;
     * - tout est fluide (< 3 %) et des réglages avaient été baissés → on remonte d'un cran.
     * Chaque changement est listé, et « Annuler » remet tout comme avant.
     */
    private fun optimiser(r: Rapport) {
        val n = r.lignes.associate { it.scene to it.nouveau }
        val repos = n["Repos"]
        val page = n["Écran principal"]
        val biblio = n["Bibliothèque (90)"]
        avantAjustement = Triple(ReglagesUi.qualiteVerre, ReglagesUi.apercusAnimes, ReglagesUi.fondAnime)
        val faits = mutableListOf<String>()
        if (ReglagesUi.fondAnime && repos != null && repos.parSeconde > 25) {
            ReglagesUi.changerFondAnime(false)
            faits += "🌫️ Fond animé coupé (${repos.parSeconde.toInt()} images/s au repos)"
        }
        val retardPage = page?.pourcentRetard ?: 0.0
        val voulu = when {
            retardPage > 20 -> ReglagesUi.QualiteVerre.LEGERE
            retardPage > 8 -> ReglagesUi.QualiteVerre.EQUILIBREE
            else -> null
        }
        if (voulu != null && voulu.ordinal > ReglagesUi.qualiteVerre.ordinal) {
            ReglagesUi.changerQualiteVerre(voulu)
            faits += "🧊 Verre « ${voulu.label} » (écran principal : ${retardPage.toInt()} % d'images en retard)"
        }
        val retardBiblio = biblio?.pourcentRetard ?: 0.0
        if (ReglagesUi.apercusAnimes && retardBiblio > 8) {
            ReglagesUi.changerApercusAnimes(false)
            faits += "🖼️ Aperçus fixes dans la bibliothèque (${retardBiblio.toInt()} % en retard)"
        }
        // Tout est fluide : on rend la qualité qu'on avait baissée, un cran à la fois.
        if (faits.isEmpty() && retardPage < 3 && retardBiblio < 3) {
            if (ReglagesUi.qualiteVerre != ReglagesUi.QualiteVerre.HAUTE) {
                val q = ReglagesUi.QualiteVerre.entries[ReglagesUi.qualiteVerre.ordinal - 1]
                ReglagesUi.changerQualiteVerre(q)
                faits += "✨ Verre remonté en « ${q.label} » (tout est fluide)"
            } else if (!ReglagesUi.apercusAnimes) {
                ReglagesUi.changerApercusAnimes(true)
                faits += "✨ Aperçus animés remis (tout est fluide)"
            }
        }
        ajustements = faits
    }

    fun annulerAjustements() {
        val (q, a, f) = avantAjustement ?: return
        ReglagesUi.changerQualiteVerre(q)
        ReglagesUi.changerApercusAnimes(a)
        ReglagesUi.changerFondAnime(f)
        ajustements = listOf("↩️ Réglages remis comme avant le banc")
        avantAjustement = null
    }

    var defilerPage: (suspend (versLeBas: Boolean, ms: Int) -> Unit)? = null
    var ouvrirBibliotheque: ((Boolean) -> Unit)? = null
    var defilerBibliotheque: (suspend (px: Float, ms: Int) -> Unit)? = null

    /** Posé par la bibliothèque à sa première image affichée. */
    @Volatile var ouverteA = 0L

    private var travail: kotlinx.coroutines.Job? = null

    fun annuler() {
        travail?.cancel()
    }

    suspend fun lancer(window: Window, rapide: Boolean = false) {
        if (enCours) return
        enCours = true
        progression = 0f
        travail = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        // Durées (ms) : repos, défilement de la page (×2), de la bibliothèque (×2).
        val repos = if (rapide) 1500L else 3000L
        val page = if (rapide) 1000 else 2000
        val biblio = if (rapide) 1200 else 2500
        val total = 2 * (1200 + repos + 2 * page + 900 + 2 * biblio + 600).toFloat()
        var fait = 0f
        suspend fun etapeDe(ms: Number, bloc: suspend () -> Unit) {
            val debut = fait
            // La barre n'avance qu'entre deux étapes : la faire bouger pendant la mesure ajoutait
            // elle-même des images (verre redessiné 20×/s) et faussait surtout le « Repos ».
            bloc()
            fait = debut + ms.toFloat()
            progression = (fait / total).coerceAtMost(1f)
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val parMode = mutableMapOf<Boolean, List<Pair<String, Mesure.Resultat>>>()
        val ouverture = mutableMapOf<Boolean, Long>()
        try {
            for (ancien in listOf(true, false)) {
                val nom = if (ancien) "ancien moteur" else "nouveau moteur"
                ModePerf.ancien = ancien
                ouvrirBibliotheque?.invoke(false)
                etapeDe(1200) {
                    defilerPage?.invoke(false, 300)
                    delay(900)
                }
                val lignes = mutableListOf<Pair<String, Mesure.Resultat>>()

                etape = "😴 repos · $nom"
                etapeDe(repos) { lignes += "Repos" to mesurer(window) { delay(repos) } }

                etape = "📜 écran principal · $nom"
                etapeDe(2 * page) {
                    lignes += "Écran principal" to mesurer(window) {
                        defilerPage?.invoke(true, page)
                        defilerPage?.invoke(false, page)
                    }
                }

                etape = "📚 bibliothèque · $nom"
                etapeDe(900) {
                    ouverteA = 0L
                    val t0 = SystemClock.uptimeMillis()
                    ouvrirBibliotheque?.invoke(true)
                    var attente = 0
                    while (ouverteA == 0L && attente < 100) { delay(20); attente++ }
                    if (ouverteA != 0L) ouverture[ancien] = ouverteA - t0
                    delay(600)
                }
                etapeDe(2 * biblio) {
                    lignes += "Bibliothèque (90)" to mesurer(window) {
                        defilerBibliotheque?.invoke(3500f, biblio)
                        defilerBibliotheque?.invoke(-3500f, biblio)
                    }
                }
                etapeDe(600) {
                    ouvrirBibliotheque?.invoke(false)
                    delay(600)
                }
                parMode[ancien] = lignes
            }
            val a = parMode[true].orEmpty()
            val n = parMode[false].orEmpty()
            val symboles = mapOf("Repos" to "😴", "Écran principal" to "📜", "Bibliothèque (90)" to "📚")
            val lignes = a.zip(n).map { (x, y) -> Ligne(x.first, symboles[x.first] ?: "•", x.second, y.second) }
            val hz = window.decorView.display?.refreshRate?.toInt() ?: 0
            val quand = java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')
            val r = Rapport(quand, hz, rapide, lignes, ouverture[true], ouverture[false])
            dernier = r
            optimiser(r)
            ReglagesUi.enregistrerMesure(texte(r) + if (ajustements.isEmpty()) "\n✅ Rien à changer : c'est fluide.\n" else "\n🛠️ Réglé automatiquement :\n" + ajustements.joinToString("\n") { "  $it" } + "\n")
            ReglagesUi.enregistrerRapport(versJson(r))
        } finally {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ModePerf.ancien = false
            ouvrirBibliotheque?.invoke(false)
            etape = ""
            progression = 0f
            enCours = false
            travail = null
        }
    }

    /** Relit le dernier banc enregistré (après un redémarrage de l'app). */
    fun relire() {
        if (dernier == null) dernier = ReglagesUi.rapport?.let(::depuisJson)
    }

    private suspend fun mesurer(window: Window, action: suspend () -> Unit): Mesure.Resultat {
        val e = Enregistreur(window)
        e.demarrer()
        try {
            action()
        } catch (c: kotlinx.coroutines.CancellationException) {
            // Annulé : on détache quand même l'écoute des images, puis on s'arrête.
            e.arreter()
            throw c
        }
        return e.arreter()
    }

    companion object {
        /** Gain en % (positif = mieux) : moins de retard, plus d'images par seconde… */
        fun gain(avant: Double, apres: Double, plusEstMieux: Boolean): Int? {
            if (avant <= 0.0 && apres <= 0.0) return null
            val base = if (plusEstMieux) avant else maxOf(avant, 0.0001)
            if (base <= 0.0) return null
            val g = if (plusEstMieux) (apres - avant) / base else (avant - apres) / base
            return (g * 100).toInt()
        }

        fun texte(r: Rapport): String {
            val f = java.util.Locale.FRANCE
            return buildString {
                append("📊 Banc de fluidité · ${r.quand} · écran ${r.hz} Hz" + (if (r.rapide) " · rapide" else "") + "\n")
                append("(ancien = causes de lenteur de la v0.1.67 reproduites ; nouveau = cette version)\n")
                r.lignes.forEach { l ->
                    append("\n${l.symbole} ${l.scene}\n")
                    fun ligne(nom: String, x: Mesure.Resultat) = append(
                        String.format(f, "  %-7s %4d images (%5.1f/s) · médiane %5.1f ms · 95%% < %5.1f ms · GPU %4.1f ms · en retard %4.1f %%\n",
                            nom, x.images, x.parSeconde, x.medianeMs, x.p95Ms, x.gpuMedianMs, x.pourcentRetard),
                    )
                    ligne("ancien", l.ancien)
                    ligne("nouveau", l.nouveau)
                    if (l.nouveau.phases.isNotEmpty()) {
                        append("  où part le temps (nouveau) : " + l.nouveau.phases.entries.sortedByDescending { it.value }
                            .joinToString(" · ") { (k, v) -> String.format(f, "%s %.1f ms", k, v) } + "\n")
                    }
                }
                if (r.ouvAncien != null || r.ouvNouveau != null) {
                    append("\n📚 Ouverture de la bibliothèque : ancien ${r.ouvAncien ?: "?"} ms · nouveau ${r.ouvNouveau ?: "?"} ms\n")
                }
                Demarrage.ms?.let { append("🚀 Ouverture de l'app : $it ms\n") }
            }
        }

        private fun resJson(x: Mesure.Resultat) = org.json.JSONObject()
            .put("i", x.images).put("d", x.dureeMs).put("m", x.medianeMs).put("p", x.p95Ms).put("g", x.gpuMedianMs).put("r", x.retards)
            .put("ph", org.json.JSONObject().apply { x.phases.forEach { (k, v) -> put(k, v) } })

        private fun resDe(o: org.json.JSONObject) = Mesure.Resultat(
            o.optInt("i"), o.optLong("d"), o.optDouble("m"), o.optDouble("p"), o.optDouble("g"), o.optInt("r"),
            o.optJSONObject("ph")?.let { ph -> ph.keys().asSequence().associateWith { ph.optDouble(it) } } ?: emptyMap(),
        )

        fun versJson(r: Rapport): String = org.json.JSONObject()
            .put("quand", r.quand).put("hz", r.hz).put("rapide", r.rapide)
            .put("oa", r.ouvAncien ?: -1).put("on", r.ouvNouveau ?: -1)
            .put("lignes", org.json.JSONArray().apply {
                r.lignes.forEach { l -> put(org.json.JSONObject().put("s", l.scene).put("e", l.symbole).put("a", resJson(l.ancien)).put("n", resJson(l.nouveau))) }
            }).toString()

        fun depuisJson(t: String): Rapport? = runCatching {
            val o = org.json.JSONObject(t)
            val ls = o.getJSONArray("lignes")
            Rapport(
                o.optString("quand"), o.optInt("hz"), o.optBoolean("rapide"),
                (0 until ls.length()).map { i ->
                    val l = ls.getJSONObject(i)
                    Ligne(l.optString("s"), l.optString("e"), resDe(l.getJSONObject("a")), resDe(l.getJSONObject("n")))
                },
                o.optLong("oa").takeIf { it >= 0 }, o.optLong("on").takeIf { it >= 0 },
            )
        }.getOrNull()
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
