package fr.niakimbo.verso.auto

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.Rouge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Le vrai son pour les égaliseurs « live » (Égaliseur, Musique en direct).
 *
 * [Visualizer] : Android donne le spectre (FFT) du son qui sort du téléphone, ~20 fois par seconde.
 * Ce n'est PAS le micro, mais Android exige l'autorisation « Micro » pour ça.
 *
 * Deux sources, essayées l'une après l'autre :
 * 1. la sortie générale du téléphone (session 0) ;
 * 2. la session audio annoncée par l'app de musique (Spotify, YouTube Music… envoient
 *    [AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION] pour les égaliseurs) : utile quand
 *    le téléphone rend la session 0 muette.
 *
 * Le spectre est découpé en [BANDES] bandes logarithmiques (graves → aigus), chacune en gain automatique.
 * [diag] dit en clair où ça bloque (pour la fiche du modèle et le journal du Toy).
 */
object Spectre {
    const val BANDES = 9

    /** Ce qui se passe, en une ligne lisible. */
    enum class Diag(val texte: String, val ok: Boolean = false) {
        ARRET("⏸ En attente (le modèle n'est pas affiché)"),
        MICRO("🎤 Autorisation Micro manquante"),
        REFUSE("❌ Android refuse la mesure du son"),
        MUET("🔇 Mesure active, mais aucun son ne remonte"),
        OK("✅ Le vrai son arrive", ok = true),
    }

    private val _diag = MutableStateFlow(Diag.ARRET)
    val diag: StateFlow<Diag> = _diag

    /** Source utilisée : « sortie du téléphone » ou « session de l'app ». */
    @Volatile
    var source: String? = null
        private set

    private val fil = HandlerThread("verso-spectre").apply { start() }
    private val main = Handler(fil.looper)

    private var visu: Visualizer? = null
    private var sessionVisu = 0

    @Volatile
    private var dernierUsage = 0L

    @Volatile
    private var derniereCapture = 0L

    @Volatile
    private var dernierSon = 0L
    private var debutCapture = 0L
    private var prochainEssai = 0L

    /** Sessions audio annoncées par les apps (la plus récente en dernier). */
    private val sessions = LinkedHashMap<Int, String>()

    @Volatile
    private var echantillonnage = 48_000f

    private val niveaux = FloatArray(BANDES)
    private val maxi = FloatArray(BANDES) { 1f }

    /** Bornes des bandes (Hz), réparties en log de 40 Hz à 14 kHz. */
    private val BORNES = FloatArray(BANDES + 1) { i -> 40f * (14_000f / 40f).pow(i / BANDES.toFloat()) }

    @Volatile
    private var app: VersoApp? = null
    private var dernierDiagNote: Diag? = null

    fun init(a: VersoApp) {
        app = a
        // Les apps de musique annoncent leur session audio (c'est ce qu'utilisent les égaliseurs du téléphone).
        a.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                if (id == 0) return
                val pkg = i.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "?"
                main.post {
                    if (i.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                        sessions.remove(id); sessions[id] = pkg
                    } else {
                        sessions.remove(id)
                        if (id == sessionVisu) arreter()
                    }
                }
            }
        }, IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }, Context.RECEIVER_EXPORTED)
    }

    fun autorise(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Niveaux 0..1 des bandes (graves d'abord) :
     * - null = pas de mesure (pas d'autorisation, Android refuse) → le modèle garde son animation ;
     * - tout à 0 = mesure active mais silence (pause, ou son coupé par Android).
     * Appelable à chaque image.
     */
    fun niveaux(): FloatArray? {
        val maintenant = SystemClock.elapsedRealtime()
        dernierUsage = maintenant
        main.post(::entretenir)
        if (maintenant - derniereCapture > 800) return null
        if (maintenant - dernierSon > 400) return FloatArray(BANDES)
        return synchronized(niveaux) { niveaux.copyOf() }
    }

    /** Démarre, change de source si la sortie générale reste muette, s'arrête 3 s après le dernier dessin. */
    private fun entretenir() {
        val a = app ?: return
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant - dernierUsage > 3_000) {
            if (visu != null) arreter()
            noter(Diag.ARRET)
            return
        }
        if (!autorise(a)) {
            noter(Diag.MICRO)
            return
        }
        val v = visu
        if (v == null) {
            if (maintenant >= prochainEssai) ouvrir(0)
            return
        }
        // Sortie générale muette depuis 3 s alors qu'une app a annoncé sa session : on essaie celle-ci.
        val muet = maintenant - dernierSon > 3_000 && maintenant - debutCapture > 3_000
        if (muet) {
            noter(Diag.MUET)
            val autre = sessions.keys.lastOrNull()
            if (autre != null && autre != sessionVisu) {
                arreter()
                ouvrir(autre)
            }
        } else if (maintenant - dernierSon <= 3_000) {
            noter(Diag.OK)
        }
    }

    private fun ouvrir(session: Int) {
        val a = app ?: return
        try {
            val v = Visualizer(session)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            echantillonnage = (v.samplingRate / 1000f).takeIf { it > 0f } ?: 48_000f
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, rate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                        if (fft != null) analyser(fft)
                    }
                },
                minOf(Visualizer.getMaxCaptureRate(), 20_000),
                false, true,
            )
            v.enabled = true
            visu = v
            sessionVisu = session
            debutCapture = SystemClock.elapsedRealtime()
            source = if (session == 0) "sortie du téléphone" else "session de ${sessions[session] ?: "l'app"}"
            a.journalToy.ajouter("Égaliseur réel : mesure lancée (${source})")
        } catch (e: Exception) {
            prochainEssai = SystemClock.elapsedRealtime() + 10_000
            if (_diag.value != Diag.REFUSE) {
                a.journalToy.ajouter("Égaliseur réel : refusé par Android (${e.javaClass.simpleName} : ${e.message ?: "?"})")
            }
            noter(Diag.REFUSE)
            // La sortie générale est refusée : la session de l'app peut, elle, marcher.
            if (session == 0) sessions.keys.lastOrNull()?.let { ouvrirSansBoucle(it) }
        }
    }

    private var essaiSession = false
    private fun ouvrirSansBoucle(session: Int) {
        if (essaiSession) return
        essaiSession = true
        try {
            ouvrir(session)
        } finally {
            essaiSession = false
        }
    }

    private fun arreter() {
        visu?.let {
            try {
                it.enabled = false
                it.release()
            } catch (_: Exception) {
            }
        }
        visu = null
        sessionVisu = 0
        prochainEssai = 0
    }

    private fun noter(d: Diag) {
        if (_diag.value == d) return
        _diag.value = d
        // Le journal du Toy garde les étapes utiles pour comprendre (pas l'attente).
        if (d != Diag.ARRET && d != dernierDiagNote) {
            dernierDiagNote = d
            app?.journalToy?.ajouter("Égaliseur réel : ${d.texte}" + (source?.let { " · $it" } ?: ""))
        }
    }

    /**
     * FFT (format Android : [0] = continu, [1] = Nyquist, puis réel / imaginaire par fréquence)
     * → énergie moyenne par bande → échelle log → gain automatique → montée rapide, descente douce.
     */
    private fun analyser(fft: ByteArray) {
        val n = fft.size / 2
        if (n < 8) return
        val hzParCase = echantillonnage / 2f / n
        var total = 0f
        val bruts = FloatArray(BANDES)
        for (b in 0 until BANDES) {
            val de = (BORNES[b] / hzParCase).toInt().coerceIn(1, n - 1)
            val a = (BORNES[b + 1] / hzParCase).toInt().coerceIn(de + 1, n)
            var somme = 0f
            for (k in de until a) somme += hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
            val moyenne = somme / (a - de)
            total += moyenne
            bruts[b] = ln(1f + moyenne)
        }
        val maintenant = SystemClock.elapsedRealtime()
        derniereCapture = maintenant
        if (total > 2f) dernierSon = maintenant
        synchronized(niveaux) {
            for (b in 0 until BANDES) {
                maxi[b] = max(max(bruts[b], 1f), maxi[b] * 0.995f)
                // Un peu de plancher retiré : le bruit de fond ne fait pas lever les barres.
                val cible = ((bruts[b] / maxi[b] - 0.25f) / 0.75f).coerceIn(0f, 1f)
                niveaux[b] = if (cible > niveaux[b]) cible else niveaux[b] * 0.75f + cible * 0.25f
            }
        }
    }

    /** Regroupe les bandes en [n] (pour un égaliseur à moins de barres). */
    fun regrouper(v: FloatArray, n: Int): FloatArray = FloatArray(n) { i ->
        val de = i * v.size / n
        val a = ((i + 1) * v.size / n).coerceAtLeast(de + 1)
        (de until a).maxOf { v[it] }
    }
}

/**
 * Carte « 🎤 Égaliseur sur la vraie musique » : l'autorisation Micro si elle manque, puis l'état
 * de la mesure en direct (pour savoir tout de suite si ça marche, et sinon pourquoi).
 */
@Composable
fun PermissionSpectre() {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(Spectre.autorise(context)) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    var diag by remember { mutableStateOf(Spectre.diag.value) }
    LaunchedEffect(Unit) {
        while (true) {
            ok = Spectre.autorise(context)
            diag = Spectre.diag.value
            delay(500)
        }
    }
    PanneauVerre(Modifier.fillMaxWidth(), actif = !ok || !diag.ok, forme = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎤  Égaliseur sur la vraie musique", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (!ok) {
                BoutonVerre("Autoriser", { demande.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth(), principal = true, taille = 14.sp)
                Text(
                    "🔇 Verso n'écoute pas le micro : il lit le son qui sort du téléphone (Android demande « Micro » pour ça).",
                    color = Discret, fontSize = 11.sp, lineHeight = 15.sp,
                )
            } else {
                Text(
                    diag.texte + (Spectre.source?.takeIf { diag.ok }?.let { " · $it" } ?: ""),
                    color = if (diag.ok) Color.White else Rouge, fontSize = 13.sp,
                )
                if (!diag.ok) Text("▶ Lance une musique, le modèle affiché.", color = Discret, fontSize = 11.sp)
            }
        }
    }
}
