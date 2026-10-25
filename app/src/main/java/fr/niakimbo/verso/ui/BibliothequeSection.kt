package fr.niakimbo.verso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import fr.niakimbo.verso.modele.FavorisStore
import fr.niakimbo.verso.modele.Horloge
import fr.niakimbo.verso.toy.ChoixToy
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.modele.ModelePlayer
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.ReglagesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Red = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)
private val Panel = Color(0xFF111111)
private val Border = Color(0xFF2A2A2A)

private const val COLONNES = 3
private const val TOUS = "Tous"
private const val FAVORIS = "★"

@Composable
private fun Puce(texte: String, actif: Boolean, onTap: () -> Unit) {
    val forme = RoundedCornerShape(50)
    Text(
        texte,
        color = if (actif) Color.White else Color(0xFFDDDDDD),
        fontSize = 13.sp,
        modifier = Modifier
            .clip(forme)
            .background(if (actif) Red else Panel)
            .border(1.dp, if (actif) Red else Border, forme)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Bibliothèque (étape 3) : aperçu de ce qui est sur la matrice, grille des modèles,
 * tap = affichage immédiat, bouton pour éteindre.
 */
@Composable
fun BibliothequeSection(
    entrees: List<EntreeCatalogue>?,
    catalogue: Catalogue,
    reglages: ReglagesStore,
    controller: MatrixController,
    player: ModelePlayer,
    favoris: FavorisStore,
    etatToy: EtatToy,
    onChoisirToy: () -> Unit,
) {
    val actif by player.actif.collectAsStateWithLifecycle()
    val affiche by controller.lastFrame.collectAsStateWithLifecycle()
    val revision by reglages.revision.collectAsStateWithLifecycle()
    val favorisIds by favoris.ids.collectAsStateWithLifecycle()
    var fiche by remember { mutableStateOf<EntreeCatalogue?>(null) }
    var rayon by rememberSaveable { mutableStateOf(TOUS) }

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

        Text(
            "Tap = afficher · ☆ = favori (le widget les fait défiler) · appui long ou ⚙ = réglages",
            color = Dim,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))

        if (!entrees.isNullOrEmpty()) {
            val rayons = listOf(TOUS, FAVORIS) + entrees.map { it.categorie }.distinct()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rayons.forEach { r ->
                    val n = when (r) {
                        TOUS -> entrees.size
                        FAVORIS -> favorisIds.count { id -> entrees.any { it.id == id } }
                        else -> entrees.count { it.categorie == r }
                    }
                    Puce("$r $n", r == rayon) { rayon = r }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        val visibles = entrees?.let { l ->
            when (rayon) {
                TOUS -> l
                FAVORIS -> favorisIds.mapNotNull { id -> l.firstOrNull { it.id == id } }
                else -> l.filter { it.categorie == rayon }
            }
        }
        // « Tous » affiche toute la bibliothèque : aperçus fixes, sinon des dizaines d'animations tournent en même temps.
        val animer = rayon != TOUS

        when {
            visibles == null -> Text("Chargement…", color = Dim, fontSize = 14.sp)
            visibles.isEmpty() -> Text(
                if (rayon == FAVORIS) "Aucun favori : touche ☆ sur un modèle." else "Aucun modèle trouvé (voir Diagnostic).",
                color = Dim, fontSize = 14.sp,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibles.chunked(COLONNES).forEach { ligne ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ligne.forEach { entree ->
                            val modele by produceState<Modele?>(null, entree, revision) {
                                value = withContext(Dispatchers.Default) { catalogue.resoudre(entree) }
                            }
                            CarteModele(
                                nom = entree.nom,
                                modele = modele,
                                actif = entree.id == actif?.id,
                                animer = animer || entree.id == actif?.id,
                                favori = entree.id in favorisIds,
                                onFavori = { favoris.basculer(entree.id) },
                                onTap = { modele?.let(player::jouer) },
                                onReglages = { fiche = entree },
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

    fiche?.let { entree ->
        FicheModele(
            entree = entree,
            catalogue = catalogue,
            reglages = reglages,
            player = player,
            onFermer = { fiche = null },
        )
    }
}

/** Où en est le Glyph Toy de Verso, vu depuis l'app. */
sealed interface EtatToy {
    /** Lié par le système en ce moment : affichage permanent. */
    data object Lie : EtatToy

    /** Lié la dernière fois, jamais délié depuis, mais pas relancé (ex. après un redémarrage). */
    data class ChoisiNonLance(val depuis: Long) : EtatToy

    /** Le système l'a délié : un autre Toy a sans doute été choisi. */
    data class Delie(val depuis: Long, val cause: String?) : EtatToy

    /** Jamais lié par le système. */
    data object Jamais : EtatToy

    companion object {
        fun calculer(lie: Boolean, derniereLiaison: Long, dernierDeliement: Long, cause: String? = null): EtatToy = when {
            lie -> Lie
            derniereLiaison == 0L -> Jamais
            dernierDeliement < derniereLiaison -> ChoisiNonLance(derniereLiaison)
            else -> Delie(dernierDeliement, cause)
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
            "Le ${date(etat.depuis)}" + (etat.cause?.let { ", avec $it" } ?: "") +
                ". Le système le coupe parfois tout seul (ou un autre Toy a été choisi). Pour le remettre :\n${ChoixToy.CHEMIN}",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarteModele(
    nom: String,
    modele: Modele?,
    actif: Boolean,
    animer: Boolean,
    favori: Boolean,
    onFavori: () -> Unit,
    onTap: () -> Unit,
    onReglages: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Panel)
            .border(if (actif) 2.dp else 1.dp, if (actif) Red else Border, shape)
            .combinedClickable(onClick = onTap, onLongClick = onReglages)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            DotMatrix(
                modele?.let { if (animer) apercuAnime(it) else if (it.dynamique != null) it.frameDynamique() else it.apercu },
                Modifier.fillMaxWidth().aspectRatio(1f),
            )
            if (modele?.estVivant == true) {
                Text("▶", color = Dim, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                nom,
                color = if (actif) Color.White else Color(0xFFDDDDDD),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (favori) "★" else "☆",
                color = if (favori) Red else Dim,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onFavori)
                    .padding(horizontal = 4.dp),
            )
            Text(
                "⚙",
                color = Dim,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReglages)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/** Fait tourner l'animation (ou l'horloge) dans un aperçu (uniquement à l'écran, pas sur la matrice). */
@Composable
internal fun apercuAnime(modele: Modele): MatrixFrame = if (modele.dynamique != null) {
    var frame by remember(modele) { mutableStateOf(modele.frameDynamique()) }
    LaunchedEffect(modele) {
        while (true) {
            delay(Horloge.msAvantProchaineMinute())
            frame = modele.frameDynamique()
        }
    }
    frame
} else if (!modele.estAnime) {
    modele.apercu
} else {
    var index by remember(modele) { mutableStateOf(0) }
    LaunchedEffect(modele) {
        while (true) {
            delay(modele.images[index % modele.images.size].dureeMs)
            index = (index + 1) % modele.images.size
        }
    }
    modele.images[index % modele.images.size].frame
}
