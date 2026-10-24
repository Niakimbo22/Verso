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
import androidx.compose.foundation.layout.width
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
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Horloge
import fr.niakimbo.verso.toy.ChoixToy
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
    etatToy: EtatToy,
    onChoisirToy: () -> Unit,
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

        Spacer(Modifier.height(20.dp))
        StatutToy(etatToy, onChoisirToy)

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

/** Où en est le Glyph Toy de Verso, vu depuis l'app. */
sealed interface EtatToy {
    /** Lié par le système en ce moment : affichage permanent. */
    data object Lie : EtatToy

    /** Lié la dernière fois, jamais délié depuis, mais pas relancé (ex. après un redémarrage). */
    data class ChoisiNonLance(val depuis: Long) : EtatToy

    /** Le système l'a délié : un autre Toy a sans doute été choisi. */
    data class Delie(val depuis: Long) : EtatToy

    /** Jamais lié par le système. */
    data object Jamais : EtatToy

    companion object {
        fun calculer(lie: Boolean, derniereLiaison: Long, dernierDeliement: Long): EtatToy = when {
            lie -> Lie
            derniereLiaison == 0L -> Jamais
            dernierDeliement < derniereLiaison -> ChoisiNonLance(derniereLiaison)
            else -> Delie(dernierDeliement)
        }
    }
}

private val Vert = Color(0xFF3DDC84)
private val Ambre = Color(0xFFFFB300)
private val FormatDate = java.time.format.DateTimeFormatter.ofPattern("dd/MM à HH:mm")

private fun date(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(FormatDate)

/** Indique si Verso est le Glyph Toy (affichage permanent) et aide à le (re)lancer sinon. */
@Composable
private fun StatutToy(etat: EtatToy, onChoisirToy: () -> Unit) {
    val (couleur, titre, texte) = when (etat) {
        EtatToy.Lie -> Triple(Vert, "Verso est ton Glyph Toy", "Le modèle choisi reste affiché app fermée.")
        is EtatToy.ChoisiNonLance -> Triple(
            Ambre,
            "Verso est choisi, mais le système ne l'a pas lancé",
            "Dernier lancement : ${date(etat.depuis)}. Verrouille puis déverrouille le téléphone ; " +
                "si rien ne change, éteins puis rallume Glyph Interface.",
        )
        is EtatToy.Delie -> Triple(
            Ambre,
            "Le système a arrêté le Toy Verso",
            "Le ${date(etat.depuis)}. Un autre Glyph Toy a peut-être été choisi. Pour revenir :\n${ChoixToy.CHEMIN}",
        )
        EtatToy.Jamais -> Triple(
            Red,
            "Verso n'est pas ton Glyph Toy",
            "L'affichage s'arrête dès que tu quittes l'app. Choisis Verso dans :\n${ChoixToy.CHEMIN}",
        )
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, shape)
            .border(1.dp, if (etat == EtatToy.Lie) Border else couleur, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(couleur, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(titre, color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(texte, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
        if (etat != EtatToy.Lie) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onChoisirToy,
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
            ) { Text(if (etat == EtatToy.Jamais) "Choisir Verso comme Glyph Toy" else "Ouvrir le choix du Glyph Toy") }
            if (etat == EtatToy.Jamais) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Si la matrice reste vide après le changement : éteins puis rallume Glyph Interface.",
                    color = Dim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
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
            if (modele.estVivant) {
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

/** Fait tourner l'animation (ou l'horloge) dans l'aperçu de la carte (uniquement à l'écran). */
@Composable
private fun apercuAnime(modele: Modele): MatrixFrame = if (modele.dynamique == Modele.Dynamique.HORLOGE) {
    var frame by remember { mutableStateOf(Horloge.frame()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Horloge.msAvantProchaineMinute())
            frame = Horloge.frame()
        }
    }
    frame
} else if (!modele.estAnime) {
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
