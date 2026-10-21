package fr.niakimbo.verso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.verso.BuildConfig
import fr.niakimbo.verso.update.UpdateState

private val NothingRed = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)

@Composable
fun VersoScreen(
    state: UpdateState,
    onUpdate: (UpdateState.Available) -> Unit,
    onRetry: (UpdateState.Error) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Verso",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = Dim,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(40.dp))
        UpdateSection(state, onUpdate, onRetry)
    }
}

@Composable
private fun UpdateSection(
    state: UpdateState,
    onUpdate: (UpdateState.Available) -> Unit,
    onRetry: (UpdateState.Error) -> Unit,
) {
    when (state) {
        UpdateState.Checking -> Note("Recherche de mises à jour…")
        UpdateState.UpToDate -> Note("À jour")
        is UpdateState.Available -> {
            Text(
                text = "Version ${state.release.version} disponible",
                color = Color.White,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onUpdate(state) },
                colors = ButtonDefaults.buttonColors(containerColor = NothingRed, contentColor = Color.White),
            ) {
                Text("Mettre à jour")
            }
            if (state.awaitingPermission) {
                Spacer(Modifier.height(12.dp))
                Note("Autorise l'installation d'apps depuis Verso, puis reviens ici.")
            }
        }
        is UpdateState.Downloading -> {
            Note("Téléchargement…")
            Spacer(Modifier.height(12.dp))
            if (state.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    color = NothingRed,
                    trackColor = Color(0xFF333333),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.6f),
                    color = NothingRed,
                    trackColor = Color(0xFF333333),
                )
            }
        }
        UpdateState.Installing -> Note("Installation…")
        is UpdateState.Error -> {
            Text(
                text = state.message,
                color = NothingRed,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { onRetry(state) }) {
                Text("Réessayer", color = Color.White)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, color = Dim, fontSize = 14.sp, textAlign = TextAlign.Center)
}
