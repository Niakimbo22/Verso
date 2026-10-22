package fr.niakimbo.verso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.BuildConfig
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.matrix.MatrixHistory
import fr.niakimbo.verso.matrix.MatrixLog
import fr.niakimbo.verso.matrix.MatrixState
import fr.niakimbo.verso.matrix.TestPatterns

private val Red = Color(0xFFD71921)
private val Green = Color(0xFF3DDC84)
private val Amber = Color(0xFFFFB300)
private val Dim = Color(0xFF8A8A8A)
private val Panel = Color(0xFF111111)
private val Border = Color(0xFF2A2A2A)
private val LedOff = Color(0xFF1E1E1E)

/** Écran de test de l'étape 2 : connexion, 4 images de test, aperçu, historique et journal. */
@Composable
fun MatrixTestSection(controller: MatrixController, history: MatrixHistory) {
    val state by controller.state.collectAsStateWithLifecycle()
    val frame by controller.lastFrame.collectAsStateWithLifecycle()
    val entries by controller.log.entries.collectAsStateWithLifecycle()
    val past by history.snapshot.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionTitle("MATRICE")
        Spacer(Modifier.height(12.dp))
        StatusLine(state, onReconnect = controller::connect)
        Spacer(Modifier.height(20.dp))

        MatrixPreview(frame)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TestButton("Pixel central", Modifier.weight(1f)) { controller.show(TestPatterns.centerPixel) }
            TestButton("Image test", Modifier.weight(1f)) { controller.show(TestPatterns.heart) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TestButton("Tout allumer", Modifier.weight(1f)) { controller.show(TestPatterns.allOn) }
            Button(
                onClick = controller::clear,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
            ) { Text("Éteindre") }
        }

        Spacer(Modifier.height(24.dp))
        HistoryPanel(past)
        Spacer(Modifier.height(16.dp))
        LogPanel(entries, controller.log)

        Spacer(Modifier.height(16.dp))
        Hint("Implémentation : ${controller.backendName}" + if (BuildConfig.HAS_GLYPH_SDK) "" else " — SDK absent du build")
        Hint("Le mode debug Glyph (ADB) est requis et expire après 48 h.")
        Hint("Un Glyph Toy actif ou le bouton Glyph passent devant Verso.")
    }
}

@Composable
private fun StatusLine(state: MatrixState, onReconnect: () -> Unit) {
    val (color, text) = when (state) {
        MatrixState.Ready -> Green to "Prête"
        MatrixState.Connecting -> Amber to "Connexion…"
        is MatrixState.Unavailable -> Dim to state.reason
        is MatrixState.Error -> Red to state.message
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
    if (state is MatrixState.Error || state is MatrixState.Unavailable) {
        TextButton(onClick = onReconnect) { Text("Reconnecter", color = Color.White) }
    }
}

/** Aperçu rond de la matrice 13×13, comme la vraie. */
@Composable
private fun MatrixPreview(frame: MatrixFrame?) {
    Canvas(
        modifier = Modifier
            .size(220.dp)
            .background(Color.Black, CircleShape)
            .border(1.dp, Border, CircleShape)
            .padding(14.dp),
    ) {
        val cell = size.minDimension / MatrixFrame.SIDE
        val radius = cell * 0.36f
        for (y in 0 until MatrixFrame.SIDE) {
            for (x in 0 until MatrixFrame.SIDE) {
                if (!MatrixFrame.isOnMatrix(x, y)) continue
                val value = frame?.get(x, y) ?: 0
                val color = if (value > 0) {
                    Color.White.copy(alpha = 0.3f + 0.7f * value / MatrixFrame.MAX)
                } else {
                    LedOff
                }
                drawCircle(color, radius, Offset(cell * (x + 0.5f), cell * (y + 0.5f)))
            }
        }
    }
}

@Composable
private fun TestButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
    ) { Text(label) }
}

@Composable
private fun HistoryPanel(past: MatrixHistory.Snapshot) {
    PanelBox {
        Text("HISTORIQUE (test 48 h)", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        MonoLine("1re connexion OK : " + (past.firstSuccess?.let(MatrixHistory::format) ?: "jamais"))
        MonoLine("Dernière OK      : " + (past.lastSuccess?.let(MatrixHistory::format) ?: "jamais"))
        if (past.lastFailure != null) {
            MonoLine("Dernier échec    : " + MatrixHistory.format(past.lastFailure), Red)
            past.failureReason?.let { MonoLine(it, Red) }
        }
    }
}

@Suppress("DEPRECATION") // LocalClipboardManager reste le plus simple pour copier du texte
@Composable
private fun LogPanel(entries: List<MatrixLog.Entry>, log: MatrixLog) {
    val clipboard = LocalClipboardManager.current
    PanelBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "JOURNAL",
                color = Dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(log.asText())) }) {
                Text("Copier", color = Color.White, fontSize = 12.sp)
            }
        }
        if (entries.isEmpty()) {
            MonoLine("(vide)")
        } else {
            entries.asReversed().take(25).forEach { entry ->
                val color = when (entry.level) {
                    MatrixLog.Level.OK -> Green
                    MatrixLog.Level.WARN -> Amber
                    MatrixLog.Level.ERROR -> Red
                    MatrixLog.Level.INFO -> Color(0xFFDDDDDD)
                }
                MonoLine("${entry.time}  ${entry.message}", color)
            }
        }
    }
}

@Composable
private fun PanelBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun MonoLine(text: String, color: Color = Color(0xFFDDDDDD)) {
    Text(text, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Dim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
}
