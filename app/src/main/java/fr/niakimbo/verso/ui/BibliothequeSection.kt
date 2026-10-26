package fr.niakimbo.verso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.createur.MesModelesStore
import kotlinx.coroutines.launch
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixState
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.FavorisStore
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
private val Panel = Color(0xFF0E0E0E).copy(alpha = 0.72f)
private val Border = Color(0xFF2A2A2A)

private const val COLONNES = 3


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
    filtre: FiltreBibliotheque = remember { FiltreBibliotheque() },
) {
    val actif by player.actif.collectAsStateWithLifecycle()
    val affiche by controller.lastFrame.collectAsStateWithLifecycle()
    val revision by reglages.revision.collectAsStateWithLifecycle()
    val favorisIds by favoris.ids.collectAsStateWithLifecycle()
    var fiche by remember { mutableStateOf<EntreeCatalogue?>(null) }
    var rayon by filtre::rayon

    val context = LocalContext.current
    val app = context.applicationContext as VersoApp
    val recentsIds by app.recents.ids.collectAsStateWithLifecycle()
    val vibreur = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Dernier modèle éteint depuis l'app ou le widget : le bouton propose de le rallumer.
    val dernier = remember(affiche == null, actif) { app.store.dernierModele }
    val entreeDernier = entrees?.firstOrNull { it.id == dernier }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // La grande matrice, posée sur un disque de verre. Tap = réglages du modèle affiché.
        PanneauVerre(
            Modifier.size(224.dp),
            forme = CircleShape,
            onClick = { if (actif != null) fiche = entrees?.firstOrNull { it.id == actif?.id } },
        ) {
            DotMatrix(affiche, Modifier.size(224.dp).padding(22.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = actif?.nom ?: if (affiche == null) "Matrice éteinte" else "Aperçu",
            color = Color.White,
            fontSize = 16.sp,
        )
        if (actif != null) {
            Text("touche la matrice pour ses réglages", color = Dim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (affiche == null && entreeDernier != null) {
            BoutonVerre("Rallumer « ${entreeDernier.nom} »", {
                scope.launch { fr.niakimbo.verso.rapide.ActionsRapides.rallumer(app) }
            }, principal = true)
        } else if (affiche != null) {
            BoutonVerre("Éteindre la matrice", { scope.launch { fr.niakimbo.verso.rapide.ActionsRapides.eteindre(app) } }, principal = true)
        }
        Spacer(Modifier.height(12.dp))
        fr.niakimbo.verso.glyph.CarteBoutonGlyph()

        TextButton(onClick = {
            vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
            // Redémarrer le processus = l'équivalent de « Forcer l'arrêt », le seul remède sûr.
            app.redemarrer("bouton Matrice figée", rouvrir = true)
        }) { Text("↻ Matrice figée ? Redémarrer Verso", color = Dim, fontSize = 12.sp) }

        // La matrice ne répond plus : on le dit ici (pas seulement dans Diagnostic) et on propose de relancer.
        val etatMatrice by controller.state.collectAsStateWithLifecycle()
        val redemarrage by controller.redemarrageConseille.collectAsStateWithLifecycle()
        (etatMatrice as? MatrixState.Error)?.let { erreur ->
            Spacer(Modifier.height(14.dp))
            val forme = RoundedCornerShape(16.dp)
            PanneauVerre(Modifier.fillMaxWidth(), forme = forme, actif = true) {
            Column(Modifier.padding(14.dp)) {
                Text("La matrice ne répond pas", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(erreur.message, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                BoutonVerre(if (redemarrage) "Redémarrer Verso" else "Reconnecter", {
                    if (redemarrage) app.redemarrer("bouton Redémarrer Verso", rouvrir = true)
                    else app.relancerMatrice("bouton Reconnecter")
                }, principal = true, taille = 14.sp)
            }
            }
        }

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
        BarreCreer(onImporte = { rayon = MES })
        Spacer(Modifier.height(12.dp))

        if (!entrees.isNullOrEmpty()) {
            val rayons = listOf(TOUS, FAVORIS, RECENTS, MES) + entrees.map { it.categorie }.distinct().filter { it != MES }
            filtre.rayons = rayons.map { r ->
                r to when (r) {
                    TOUS -> entrees.size
                    FAVORIS -> favorisIds.count { id -> entrees.any { it.id == id } }
                    RECENTS -> recentsIds.count { id -> entrees.any { it.id == id } }
                    MES -> entrees.count { it.perso != null }
                    else -> entrees.count { it.categorie == r }
                }
            }.filter { (r, n) -> n > 0 || r == TOUS || r == FAVORIS }
            // Les puces de la page ; hors de vue, la barre collante en verre prend le relais (VersoScreen).
            LignePuces(filtre, Modifier.onGloballyPositioned { filtre.basPuces = it.positionInWindow().y + it.size.height })
            Spacer(Modifier.height(12.dp))
        }
        val cherche = FiltreBibliotheque.simple(filtre.recherche)
        val visibles = entrees?.let { l ->
            when {
                cherche.isNotEmpty() -> l.filter { FiltreBibliotheque.simple(it.nom).contains(cherche) || FiltreBibliotheque.simple(it.categorie).contains(cherche) }
                rayon == TOUS -> l
                rayon == FAVORIS -> favorisIds.mapNotNull { id -> l.firstOrNull { it.id == id } }
                rayon == RECENTS -> recentsIds.mapNotNull { id -> l.firstOrNull { it.id == id } }
                rayon == MES -> l.filter { it.perso != null }
                else -> l.filter { it.categorie == rayon }
            }
        }
        if (cherche.isNotEmpty() && visibles != null) {
            Text("${visibles.size} résultat${if (visibles.size > 1) "s" else ""} pour « ${filtre.recherche.trim()} »", color = Dim, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
        // Toutes les cartes s'animent, mais seulement quand elles sont à l'écran (voir CarteModele).
        val animer = true

        when {
            visibles == null -> Text("Chargement…", color = Dim, fontSize = 14.sp)
            visibles.isEmpty() -> Text(
                when {
                    cherche.isNotEmpty() -> "Aucun modèle ne correspond."
                    rayon == RECENTS -> "Rien d'affiché récemment."
                    rayon == FAVORIS -> "Aucun favori : touche ☆ sur un modèle."
                    rayon == MES -> "Rien pour l'instant : crée un dessin avec le bouton ci-dessus."
                    else -> "Aucun modèle trouvé (voir Diagnostic)."
                },
                color = Dim, fontSize = 14.sp,
            )
            else -> {
                // Une carte de la grille ; dans ★, l'appui long sert à déplacer (⚙ ouvre toujours les réglages).
                // Modèles résolus gardés ici : une carte déplacée (★) ne recalcule pas son aperçu.
                val resolus = remember(entrees, revision) { mutableStateMapOf<String, Modele>() }
                val carte: @Composable (EntreeCatalogue, Modifier, Boolean) -> Unit = { entree, mod, appuiLongReglages ->
                    val modele = resolus[entree.id]
                    if (modele == null) {
                        LaunchedEffect(entree.id, revision) {
                            resolus[entree.id] = withContext(Dispatchers.Default) { catalogue.resoudre(entree) }
                        }
                    }
                    CarteModele(
                        nom = entree.nom,
                        perso = entree.perso != null,
                        modele = modele,
                        actif = entree.id == actif?.id,
                        animer = animer || entree.id == actif?.id,
                        favori = entree.id in favorisIds,
                        onFavori = { favoris.basculer(entree.id) },
                        onTap = {
                            modele?.let {
                                vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                player.jouer(it)
                            }
                        },
                        onReglages = { fiche = entree },
                        appuiLongReglages = appuiLongReglages,
                        modifier = mod,
                    )
                }
                if (rayon == FAVORIS && cherche.isEmpty()) {
                    Text(
                        "Appui long sur une carte puis glisse pour changer l'ordre : c'est celui du widget et de la fenêtre ☰.",
                        color = Dim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    GrilleReordonnable(visibles, onOrdre = { favoris.ordonner(it) }) { entree, mod -> carte(entree, mod, false) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visibles.chunked(COLONNES).forEach { ligne ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ligne.forEach { entree -> carte(entree, Modifier.weight(1f), true) }
                                // Cases vides pour garder la grille alignée sur la dernière ligne.
                                repeat(COLONNES - ligne.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
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

    /**
     * Verso vient de (re)démarrer (mise à jour, bouton Redémarrer…) : le système relance le Toy tout
     * seul, mais il lui faut parfois jusqu'à une minute. Pas une erreur, on attend.
     */
    data object Relance : EtatToy

    /** Le système l'a délié : un autre Toy a sans doute été choisi. */
    data class Delie(val depuis: Long, val cause: String?) : EtatToy

    /** Jamais lié par le système. */
    data object Jamais : EtatToy

    companion object {
        /** Délai laissé au système pour relancer le Toy après un démarrage de Verso. */
        const val ATTENTE_RELANCE_MS = 3 * 60_000L

        fun calculer(
            lie: Boolean,
            derniereLiaison: Long,
            dernierDeliement: Long,
            cause: String? = null,
            depuisDemarrageMs: Long = Long.MAX_VALUE,
        ): EtatToy = when {
            lie -> Lie
            derniereLiaison == 0L -> Jamais
            dernierDeliement < derniereLiaison && depuisDemarrageMs < ATTENTE_RELANCE_MS -> Relance
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
        EtatToy.Relance -> Triple(
            Dim,
            "Relance du Glyph Toy…",
            "Verso vient de redémarrer : le système relance le Toy tout seul (jusqu'à une minute). Rien à faire.",
        )
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
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(22.dp), actif = etat == EtatToy.Jamais) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(couleur, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(titre, color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(texte, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
        if (etat != EtatToy.Lie && etat != EtatToy.Relance) {
            Spacer(Modifier.height(10.dp))
            BoutonVerre(
                if (etat == EtatToy.Jamais) "Choisir Verso comme Glyph Toy" else "Ouvrir le choix du Glyph Toy",
                onChoisirToy, principal = true, taille = 14.sp,
            )
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarteModele(
    nom: String,
    perso: Boolean,
    modele: Modele?,
    actif: Boolean,
    animer: Boolean,
    favori: Boolean,
    onFavori: () -> Unit,
    onTap: () -> Unit,
    onReglages: () -> Unit,
    modifier: Modifier,
    appuiLongReglages: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    var visible by remember { mutableStateOf(false) }
    Column(
        modifier
            .suivreVisibilite { visible = it }
            .clip(shape)
            .background(Panel)
            .border(if (actif) 2.dp else 1.dp, if (actif) Red else Border, shape)
            .combinedClickable(onClick = onTap, onLongClick = if (appuiLongReglages) onReglages else null)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            DotMatrix(
                modele?.let { if (animer && visible) apercuAnime(it) else if (it.dynamique != null) it.frameDynamique() else it.apercu },
                Modifier.fillMaxWidth().aspectRatio(1f),
            )
            if (modele?.estVivant == true) {
                Text("▶", color = Dim, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd))
            }
            if (perso) {
                Text("✎", color = Red, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart))
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
            delay(modele.msAvantProchaineImage())
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

/**
 * Grille des favoris qu'on réordonne au doigt : appui long sur une carte, puis glisser.
 * Les autres cartes se décalent pendant le déplacement ; l'ordre est enregistré au lâcher.
 *
 * Les cases de la grille ont toutes la même taille et ne bougent pas : on suit l'index de la case
 * survolée, ce qui évite de dépendre de la position (en cours de mise à jour) de la carte déplacée.
 */
@Composable
private fun GrilleReordonnable(
    entrees: List<EntreeCatalogue>,
    onOrdre: (List<String>) -> Unit,
    carte: @Composable (EntreeCatalogue, Modifier) -> Unit,
) {
    val idsRecus = entrees.map { it.id }
    var ordre by remember(idsRecus) { mutableStateOf(idsRecus) }
    var tire by remember { mutableStateOf<Int?>(null) }
    var decalage by remember { mutableStateOf(Offset.Zero) }
    val cases = remember { mutableStateMapOf<Int, Rect>() }
    var conteneur by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val vibreur = LocalHapticFeedback.current
    val ordreActuel by rememberUpdatedState(ordre)
    val parId = entrees.associateBy { it.id }

    fun finir() {
        if (tire != null) onOrdre(ordreActuel)
        tire = null
        decalage = Offset.Zero
    }

    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { conteneur = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        tire = cases.entries.firstOrNull { it.value.contains(pos) && it.key < ordreActuel.size }?.key
                        decalage = Offset.Zero
                        if (tire != null) vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, delta ->
                        val i = tire ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        decalage += delta
                        val depart = cases[i] ?: return@detectDragGesturesAfterLongPress
                        val centre = depart.center + decalage
                        val j = cases.entries.firstOrNull { (k, r) -> k != i && k < ordreActuel.size && r.contains(centre) }?.key
                            ?: return@detectDragGesturesAfterLongPress
                        val arrivee = cases[j] ?: return@detectDragGesturesAfterLongPress
                        ordre = ordreActuel.toMutableList().apply { add(j, removeAt(i)) }
                        // La carte change de case : on compense pour qu'elle reste sous le doigt.
                        decalage += depart.topLeft - arrivee.topLeft
                        tire = j
                        vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = ::finir,
                    onDragCancel = ::finir,
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordre.chunked(COLONNES).forEachIndexed { r, ligne ->
            val ligneTiree = tire?.let { it / COLONNES == r } == true
            Row(
                Modifier.fillMaxWidth().zIndex(if (ligneTiree) 1f else 0f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ligne.forEachIndexed { c, id ->
                    val index = r * COLONNES + c
                    val enMain = index == tire
                    key(id) {
                    Box(
                        Modifier
                            .weight(1f)
                            .zIndex(if (enMain) 1f else 0f)
                            .onGloballyPositioned { coord -> conteneur?.let { cases[index] = it.localBoundingBoxOf(coord) } },
                    ) {
                        parId[id]?.let { entree ->
                            carte(
                                entree,
                                Modifier.fillMaxWidth().graphicsLayer {
                                    if (enMain) {
                                        translationX = decalage.x
                                        translationY = decalage.y
                                        scaleX = 1.07f
                                        scaleY = 1.07f
                                        alpha = 0.92f
                                    }
                                },
                            )
                        }
                    }
                    }
                }
                repeat(COLONNES - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
