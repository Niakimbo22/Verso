package fr.niakimbo.verso.auto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/**
 * Le vrai son pour l'égaliseur de « Musique en direct » (28/10, demande de Nico : « qu'il bouge
 * vraiment selon la musique »).
 *
 * [Visualizer] sur la sortie audio du téléphone (session 0) : Android donne le spectre (FFT) du son
 * qui sort, ~20 fois par seconde. Ce n'est PAS le micro, mais Android exige quand même l'autorisation
 * « Micro » pour ça. Le spectre est découpé en 5 bandes (graves → aigus), chacune réglée en gain
 * automatique pour que les barres bougent aussi à faible volume.
 *
 * Il ne tourne que si quelqu'un le demande (le modèle est dessiné) et s'arrête 3 s après.
 * Si Android coupe la capture (app fermée sur certains téléphones : le son arrive muet), [niveaux]
 * rend null et le modèle reprend l'animation.
 */
object Spectre {
    const val BANDES = 5

    /** Bornes des bandes en Hz : graves, bas-médiums, médiums, aigus, brillance. */
    private val BORNES = floatArrayOf(30f, 150f, 450f, 1_400f, 4_000f, 12_000f)

    private val fil = HandlerThread("verso-spectre").apply { start() }
    private val main = Handler(fil.looper)

    @Volatile
    private var visu: Visualizer? = null

    @Volatile
    private var dernierUsage = 0L

    @Volatile
    private var derniereCapture = 0L

    /** Dernier son non muet (le spectre peut arriver tout à zéro quand Android bloque la capture). */
    @Volatile
    private var dernierSon = 0L

    @Volatile
    private var prochainEssai = 0L

    /** Fréquence d'échantillonnage de la sortie (Hz). */
    @Volatile
    private var echantillonnage = 48_000f

    private val niveaux = FloatArray(BANDES)
    private val maxi = FloatArray(BANDES) { 1f }

    @Volatile
    private var app: VersoApp? = null
    private var echecNote = false

    fun init(a: VersoApp) {
        app = a
    }

    fun autorise(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Niveaux 0..1 des 5 bandes (graves d'abord), ou null s'il n'y a pas de vrai son mesuré récemment
     * (pas d'autorisation, capture impossible ou muette). Appelable à chaque image.
     */
    fun niveaux(): FloatArray? {
        val maintenant = SystemClock.elapsedRealtime()
        dernierUsage = maintenant
        if (visu == null) demarrer()
        if (maintenant - derniereCapture > 600 || maintenant - dernierSon > 1_500) return null
        return synchronized(niveaux) { niveaux.copyOf() }
    }

    private fun demarrer() {
        val a = app ?: return
        if (!autorise(a)) return
        val maintenant = SystemClock.elapsedRealtime()
        if (maintenant < prochainEssai) return
        prochainEssai = maintenant + 30_000
        main.post {
            if (visu != null) return@post
            try {
                val v = Visualizer(0)
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
                echecNote = false
                surveiller()
            } catch (e: Exception) {
                if (!echecNote) {
                    echecNote = true
                    a.journalToy.ajouter("Égaliseur réel indisponible (${e.javaClass.simpleName}) : animation à la place")
                }
            }
        }
    }

    /** Arrête la capture 3 s après le dernier dessin. */
    private fun surveiller() {
        main.postDelayed({
            if (SystemClock.elapsedRealtime() - dernierUsage > 3_000) arreter() else surveiller()
        }, 1_000)
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
        prochainEssai = 0
    }

    /**
     * FFT (format Android : [0] = continu, [1] = Nyquist, puis réel / imaginaire par fréquence)
     * → énergie moyenne par bande → échelle logarithmique → gain automatique → montée rapide, descente douce.
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
            for (k in de until a) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                somme += hypot(re, im)
            }
            val moyenne = somme / (a - de)
            total += moyenne
            bruts[b] = ln(1f + moyenne)
        }
        val maintenant = SystemClock.elapsedRealtime()
        derniereCapture = maintenant
        if (total > 1.5f) dernierSon = maintenant
        synchronized(niveaux) {
            for (b in 0 until BANDES) {
                maxi[b] = max(max(bruts[b], 0.8f), maxi[b] * 0.996f)
                val cible = (bruts[b] / maxi[b]).coerceIn(0f, 1f)
                niveaux[b] = if (cible > niveaux[b]) cible else niveaux[b] * 0.8f + cible * 0.2f
            }
        }
    }
}

/** Carte « 🎤 Égaliseur réel » : demande l'autorisation Micro (le son lu est celui qui sort du téléphone). */
@Composable
fun PermissionSpectre() {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(Spectre.autorise(context)) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    LaunchedEffect(Unit) {
        while (!ok) {
            delay(1_000)
            ok = Spectre.autorise(context)
        }
    }
    if (ok) return
    PanneauVerre(Modifier.fillMaxWidth(), actif = true, forme = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎤  Égaliseur sur la vraie musique", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            BoutonVerre("Autoriser", { demande.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth(), principal = true, taille = 14.sp)
            Text(
                "🔇 Verso n'écoute pas le micro : il lit le son qui sort du téléphone (Android demande « Micro » pour ça).",
                color = Discret, fontSize = 11.sp, lineHeight = 15.sp,
            )
        }
    }
}
