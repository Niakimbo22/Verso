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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.BuildConfig
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixHistory
import fr.niakimbo.verso.modele.ModelePlayer
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.ReglagesStore
import fr.niakimbo.verso.modele.ModeleStore
import fr.niakimbo.verso.toy.JournalToy
import fr.niakimbo.verso.generateur.Compteur
import fr.niakimbo.verso.modele.FavorisStore
import fr.niakimbo.verso.update.UpdateState

private val NothingRed = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)

@Composable
fun VersoScreen(
    state: UpdateState,
    onUpdate: (UpdateState.Available) -> Unit,
    onRetry: (UpdateState.Error) -> Unit,
    matrix: MatrixController,
    matrixHistory: MatrixHistory,
    player: ModelePlayer,
    favoris: FavorisStore,
    catalogue: Catalogue,
    reglages: ReglagesStore,
    store: ModeleStore,
    journalToy: JournalToy,
    etatToy: EtatToy,
    onChoisirToy: () -> Unit,
    sectionWidgets: @Composable () -> Unit = {},
    sectionAuto: @Composable () -> Unit = {},
) {
    // Rechargée quand un modèle est créé, modifié ou supprimé (rayon « Mes modèles »).
    val revisionModeles by catalogue.revision.collectAsStateWithLifecycle()
    val bibliotheque by produceState<Catalogue.Contenu?>(null, catalogue, revisionModeles) {
        value = catalogue.contenu()
    }
    var diagnosticOuvert by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Verso",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = Dim,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        UpdateSection(state, onUpdate, onRetry)
        Spacer(Modifier.height(28.dp))
        BibliothequeSection(bibliotheque?.entrees?.filter { !it.cache }, catalogue, reglages, matrix, player, favoris, etatToy, onChoisirToy)

        Spacer(Modifier.height(36.dp))
        sectionAuto()

        Spacer(Modifier.height(36.dp))
        sectionWidgets()

        Spacer(Modifier.height(36.dp))
        val erreurs = bibliotheque?.erreurs.orEmpty()
        TextButton(onClick = { diagnosticOuvert = !diagnosticOuvert }) {
            Text(
                (if (diagnosticOuvert) "▾ " else "▸ ") + "DIAGNOSTIC" + if (erreurs.isNotEmpty()) " (${erreurs.size} ⚠)" else "",
                color = Dim,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
        }
        if (diagnosticOuvert) {
            Spacer(Modifier.height(8.dp))
            DiagnosticToy(matrix, journalToy, store)
            Spacer(Modifier.height(24.dp))
            MatrixTestSection(matrix, player, matrixHistory, erreurs, onCompteur = {
                bibliotheque?.parId(Compteur.ID)?.let { player.jouer(catalogue.resoudre(it)) }
            })
        }
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
