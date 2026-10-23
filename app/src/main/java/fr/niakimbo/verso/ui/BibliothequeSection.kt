package fr.niakimbo.verso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.modele.ModelePlayer
import kotlinx.coroutines.delay

private val Red = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)
private val Panel = Color(0xFF111111)
private val Border = Color(0xFF2A2A2A)

private const val COLONNES = 3

/**
 * Bibliothèque (étape 3) : aperçu de ce qui est sur la matrice, grille des modèles,
 * tap = affichage immédiat, bouton pour éteindre.
 */
@Composable
fun BibliothequeSection(
    modeles: List<Modele>?,
    controller: MatrixController,
    player: ModelePlayer,
) {
    val actif by player.actif.collectAsStateWithLifecycle()
    val affiche by controller.lastFrame.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DotMatrix(
            affiche,
            Modifier
                .size(200.dp)
                .background(Color.Black, CircleShape)
                .border(1.dp, Border, CircleShape)
                .padding(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = actif?.nom ?: if (affiche == null) "Matrice éteinte" else "Image de test",
            color = Color.White,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = player::eteindre,
            enabled = affiche != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Red,
                contentColor = Color.White,
                disabledContainerColor = Panel,
                disabledContentColor = Dim,
            ),
        ) { Text("Éteindre la matrice") }

        Spacer(Modifier.height(28.dp))
        SectionTitle("MODÈLES")
        Spacer(Modifier.height(12.dp))

        when {
            modeles == null -> Text("Chargement…", color = Dim, fontSize = 14.sp)
            modeles.isEmpty() -> Text("Aucun modèle trouvé (voir Diagnostic).", color = Dim, fontSize = 14.sp)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                modeles.chunked(COLONNES).forEach { ligne ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ligne.forEach { modele ->
                            CarteModele(
                                modele = modele,
                                actif = modele.id == actif?.id,
                                onTap = { player.jouer(modele) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Cases vides pour garder la grille alignée sur la dernière ligne.
                        repeat(COLONNES - ligne.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteModele(modele: Modele, actif: Boolean, onTap: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Panel)
            .border(if (actif) 2.dp else 1.dp, if (actif) Red else Border, shape)
            .clickable(onClick = onTap)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            DotMatrix(apercuAnime(modele), Modifier.fillMaxWidth().aspectRatio(1f))
            if (modele.estAnime) {
                Text(
                    "▶",
                    color = Dim,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            modele.nom,
            color = if (actif) Color.White else Color(0xFFDDDDDD),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fait tourner l'animation dans l'aperçu de la carte (uniquement à l'écran, pas sur la matrice). */
@Composable
private fun apercuAnime(modele: Modele) = if (!modele.estAnime) {
    modele.apercu
} else {
    var index by remember(modele.id) { mutableStateOf(0) }
    LaunchedEffect(modele.id) {
        while (true) {
            delay(modele.images[index].dureeMs)
            index = (index + 1) % modele.images.size
        }
    }
    modele.images[index].frame
}
