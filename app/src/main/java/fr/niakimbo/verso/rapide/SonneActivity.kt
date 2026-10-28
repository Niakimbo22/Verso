package fr.niakimbo.verso.rapide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.generateur.MinuteurLive
import fr.niakimbo.verso.ui.DotMatrix
import fr.niakimbo.verso.ui.verre.BoutonVerre
import kotlinx.coroutines.launch

private val Rouge = Color(0xFFD71921)

/** Écran « Terminé » du minuteur : par-dessus tout, même téléphone verrouillé. */
class SonneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val app = application as VersoApp
        setContent {
            val etat by Minuteur.etat.collectAsStateWithLifecycle()
            // Arrêtée ailleurs (notification, widget, +1 min) : l'écran se ferme.
            LaunchedEffect(etat.sonne) { if (!etat.sonne) finish() }
            EcranTermine(
                onArreter = { app.appScope.launch { Minuteur.arreterSonnerie(app) }; finish() },
                onPlus = { app.appScope.launch { Minuteur.ajouter(app) }; finish() },
            )
        }
    }
}

@Composable
private fun EcranTermine(onArreter: () -> Unit, onPlus: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val halo by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "halo")
    val temps by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800)), label = "temps")
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(340.dp).graphicsLayer { alpha = halo }) {
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Rouge.copy(alpha = 0.45f), Color.Transparent)),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("MINUTEUR", color = Color(0xFF8A8A8A), fontSize = 13.sp, letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            Text("Terminé", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(36.dp))
            DotMatrix(
                frame = MinuteurLive.frameSonne((temps * 1800).toLong()),
                modifier = Modifier.size(200.dp),
            )
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BoutonVerre("+1 min", onPlus, Modifier.weight(1f), taille = 18.sp)
                BoutonVerre("Arrêter", onArreter, Modifier.weight(1.4f), principal = true, taille = 18.sp)
            }
        }
    }
}
