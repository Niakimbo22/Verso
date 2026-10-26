package fr.niakimbo.verso.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.toy.ChoixToy
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.LocalFond
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.contourActif
import fr.niakimbo.verso.ui.verre.verre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Red = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)
private val Panel = Color(0xFF0E0E0E).copy(alpha = 0.72f)
private val Border = Color(0xFF2A2A2A)

private const val COLONNES = 3

/**
 * Modèles résolus (version + réglages appliqués), partagés par l'écran principal et la bibliothèque.
 * Une nouvelle instance à chaque changement de réglages ou de bibliothèque.
 * Seuls les modèles réellement affichés sont calculés (en arrière-plan).
 */
class CacheModeles(private val catalogue: Catalogue) {
    private val resolus = mutableStateMapOf<String, Modele>()

    @Composable
    fun modele(entree: EntreeCatalogue): Modele? {
        val m = resolus[entree.id]
        if (m == null) {
            LaunchedEffect(entree.id) {
                resolus[entree.id] = withContext(Dispatchers.Default) { catalogue.resoudre(entree) }
            }
        }
        return m
    }

    fun deja(id: String): Modele? = resolus[id]
}

/** Rayons proposés (avec le nombre de modèles) : Tous, ★, Récents, Mes modèles, puis chaque rayon. */
fun calculerRayons(entrees: List<EntreeCatalogue>, favorisIds: List<String>, recentsIds: List<String>): List<Pair<String, Int>> {
    val ids = entrees.mapTo(HashSet()) { it.id }
    val rayons = listOf(TOUS, FAVORIS, RECENTS, MES) + entrees.map { it.categorie }.distinct().filter { it != MES }
    return rayons.map { r ->
        r to when (r) {
            TOUS -> entrees.size
            FAVORIS -> favorisIds.count { it in ids }
            RECENTS -> recentsIds.count { it in ids }
            MES -> entrees.count { it.perso != null }
            else -> entrees.count { it.categorie == r }
        }
    }.filter { (r, n) -> n > 0 || r == TOUS || r == FAVORIS }
}

/**
 * Bibliothèque complète, en feuille de verre qui monte du bas (façon iOS), ouverte seulement quand
 * on en a besoin. Rayons, 🔍, Récents, Mes modèles, créer / importer, réordonner ★ : tout y est.
 *
 * Grille **paresseuse** : seules les cartes à l'écran existent (et s'animent) ; on peut parcourir
 * 90 modèles sans calculer les 90.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeuilleBibliotheque(
    entrees: List<EntreeCatalogue>?,
    cache: CacheModeles,
    filtre: FiltreBibliotheque,
    banc: BancFluidite,
    onFermer: () -> Unit,
    onFiche: (EntreeCatalogue) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VersoApp
    val actif by app.player.actif.collectAsStateWithLifecycle()
    val favorisIds by app.favoris.ids.collectAsStateWithLifecycle()
    val recentsIds by app.recents.ids.collectAsStateWithLifecycle()
    val vibreur = LocalHapticFeedback.current
    val portee = rememberCoroutineScope()
    if (ModePerf.ancien) {
        // Banc : l'ancienne bibliothèque relisait l'image de la matrice tout en haut → tout recomposé à chaque image.
        val f by app.matrix.lastFrame.collectAsStateWithLifecycle()
        f.hashCode()
    }

    val grille = rememberLazyGridState()
    val defilAncien = rememberScrollState()
    val defilFavoris = rememberScrollState()
    DisposableEffect(banc) {
        banc.defilerBibliotheque = { px, ms ->
            if (ModePerf.ancien) defilAncien.animateScrollBy(px, tween(ms)) else grille.animateScrollBy(px, tween(ms))
        }
        onDispose { banc.defilerBibliotheque = null }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        banc.ouverteA = SystemClock.uptimeMillis()
    }

    // Glisser la poignée vers le bas = fermer.
    val glisse = remember { Animatable(0f) }
    val seuil = with(LocalDensity.current) { 140.dp.toPx() }
    val forme = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val fondFeuille = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .graphicsLayer { translationY = glisse.value }
                .verre(forme, teinte = Color(0xFF0A0A0A).copy(alpha = 0.50f), flou = 26.dp, refraction = 30.dp, exporte = fondFeuille)
                .contourActif(false, forme),
        ) {
            CompositionLocalProvider(LocalFond provides fondFeuille) {
                // En-tête : poignée, titre, ✕ — on peut le tirer vers le bas pour fermer.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (glisse.value > seuil) onFermer() else portee.launch { glisse.animateTo(0f) }
                                },
                                onDragCancel = { portee.launch { glisse.animateTo(0f) } },
                            ) { change, dy ->
                                change.consume()
                                portee.launch { glisse.snapTo((glisse.value + dy).coerceAtLeast(0f)) }
                            }
                        }
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        Modifier
                            .padding(top = 10.dp)
                            .size(width = 40.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                            .align(Alignment.CenterHorizontally),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Modèles", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Tap = afficher · ☆ = favori · appui long ou ⚙ = réglages",
                                color = Dim, fontSize = 11.sp,
                            )
                        }
                        RondVerre("✕", onFermer, taille = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (!entrees.isNullOrEmpty()) {
                        filtre.rayons = calculerRayons(entrees, favorisIds, recentsIds)
                        LignePuces(filtre)
                    }
                    Spacer(Modifier.height(10.dp))
                }

                val rayon = filtre.rayon
                val cherche = FiltreBibliotheque.simple(filtre.recherche)
                val visibles = entrees?.let { l ->
                    when {
                        cherche.isNotEmpty() -> l.filter { FiltreBibliotheque.simple(it.nom).contains(cherche) || FiltreBibliotheque.simple(it.categorie).contains(cherche) }
                        rayon == TOUS -> l
                        rayon == FAVORIS -> favorisIds.distinct().mapNotNull { id -> l.firstOrNull { it.id == id } }
                        rayon == RECENTS -> recentsIds.distinct().mapNotNull { id -> l.firstOrNull { it.id == id } }
                        rayon == MES -> l.filter { it.perso != null }
                        else -> l.filter { it.categorie == rayon }
                    }
                }

                val carte: @Composable (EntreeCatalogue, Modifier, Boolean) -> Unit = { entree, mod, appuiLongReglages ->
                    val modele = cache.modele(entree)
                    CarteModele(
                        nom = entree.nom,
                        perso = entree.perso != null,
                        modele = modele,
                        actif = entree.id == actif?.id,
                        favori = entree.id in favorisIds,
                        onFavori = { app.favoris.basculer(entree.id) },
                        onTap = {
                            modele?.let {
                                vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                app.player.jouer(it)
                            }
                        },
                        onReglages = { onFiche(entree) },
                        appuiLongReglages = appuiLongReglages,
                        suivreVisible = ModePerf.ancien || !appuiLongReglages,
                        modifier = mod,
                    )
                }

                val entete: @Composable () -> Unit = {
                    Column {
                        BarreCreer(onImporte = { filtre.rayon = MES; filtre.recherche = "" })
                        Spacer(Modifier.height(10.dp))
                        when {
                            cherche.isNotEmpty() && visibles != null -> Text(
                                "${visibles.size} résultat${if (visibles.size > 1) "s" else ""} pour « ${filtre.recherche.trim()} »",
                                color = Dim, fontSize = 12.sp,
                            )
                            rayon == FAVORIS -> Text(
                                "Appui long sur une carte puis glisse pour changer l'ordre : c'est celui du widget, du mini-lecteur et de la fenêtre ☰.",
                                color = Dim, fontSize = 11.sp,
                            )
                        }
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
                        }
                    }
                }

                val l = visibles.orEmpty()
                val marge = Modifier.padding(horizontal = 16.dp)
                when {
                    // ★ : peu de cartes, grille qu'on réordonne au doigt.
                    rayon == FAVORIS && cherche.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(defilFavoris).then(marge).padding(bottom = 130.dp)) {
                        entete()
                        Spacer(Modifier.height(10.dp))
                        GrilleReordonnable(l, onOrdre = { app.favoris.ordonner(it) }) { entree, mod -> carte(entree, mod, false) }
                    }
                    // Ancien moteur (banc) : les 90 cartes composées d'un coup, comme en v0.1.67.
                    ModePerf.ancien -> Column(Modifier.fillMaxSize().verticalScroll(defilAncien).then(marge).padding(bottom = 130.dp)) {
                        entete()
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            l.chunked(COLONNES).forEach { ligne ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ligne.forEach { e -> carte(e, Modifier.weight(1f), true) }
                                    repeat(COLONNES - ligne.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(COLONNES),
                        state = grille,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "entete", span = { GridItemSpan(maxLineSpan) }) { entete() }
                        if (rayon == TOUS && cherche.isEmpty()) {
                            // « Tous » rangé par rayons, avec un titre par rayon (comme la bibliothèque d'apps d'iOS).
                            l.groupBy { it.categorie }.forEach { (cat, liste) ->
                                titreRayon(cat, liste.size)
                                items(liste, key = { it.id }) { e -> carte(e, Modifier, true) }
                            }
                        } else {
                            items(l, key = { it.id }) { e -> carte(e, Modifier, true) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.titreRayon(nom: String, n: Int) {
    item(key = "rayon-$nom", span = { GridItemSpan(maxLineSpan) }) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(nom.uppercase(), color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("$n", color = Dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
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

    /** À montrer en grand sur l'écran principal (sinon : une simple pastille). */
    val probleme: Boolean get() = this != Lie && this != Relance

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

internal val Vert = Color(0xFF3DDC84)
internal val Ambre = Color(0xFFFFB300)
private val FormatDate = java.time.format.DateTimeFormatter.ofPattern("dd/MM à HH:mm")

private fun date(ms: Long): String =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(FormatDate)

/** Couleur de la pastille d'état du Toy. */
internal fun couleurToy(etat: EtatToy): Color = when (etat) {
    EtatToy.Lie -> Vert
    EtatToy.Relance -> Dim
    is EtatToy.ChoisiNonLance, is EtatToy.Delie -> Ambre
    EtatToy.Jamais -> Red
}

/** Indique si Verso est le Glyph Toy (affichage permanent) et aide à le (re)lancer sinon. */
@Composable
internal fun StatutToy(etat: EtatToy, onChoisirToy: () -> Unit) {
    val (titre, texte) = when (etat) {
        EtatToy.Lie -> "Verso est ton Glyph Toy" to "Le modèle choisi reste affiché app fermée."
        EtatToy.Relance -> "Relance du Glyph Toy…" to
            "Verso vient de redémarrer : le système relance le Toy tout seul (jusqu'à une minute). Rien à faire."
        is EtatToy.ChoisiNonLance -> "Verso est choisi, mais le système ne l'a pas lancé" to
            ("Dernier lancement : ${date(etat.depuis)}. Verrouille puis déverrouille le téléphone ; " +
                "si rien ne change, éteins puis rallume Glyph Interface.")
        is EtatToy.Delie -> "Le système a arrêté le Toy Verso" to
            ("Le ${date(etat.depuis)}" + (etat.cause?.let { ", avec $it" } ?: "") +
                ". Le système le coupe parfois tout seul (ou un autre Toy a été choisi). Pour le remettre :\n${ChoixToy.CHEMIN}")
        EtatToy.Jamais -> "Verso n'est pas ton Glyph Toy" to
            "L'affichage s'arrête dès que tu quittes l'app. Choisis Verso dans :\n${ChoixToy.CHEMIN}"
    }
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(22.dp), actif = etat == EtatToy.Jamais) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(couleurToy(etat), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(titre, color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(texte, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
            if (etat.probleme) {
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

/**
 * Carte de la grille (pas de verre : trop lourd à 90 ; fond noir translucide).
 * [suivreVisible] : dans une liste non paresseuse, l'aperçu ne s'anime que quand la carte est à l'écran.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarteModele(
    nom: String,
    perso: Boolean,
    modele: Modele?,
    actif: Boolean,
    favori: Boolean,
    onFavori: () -> Unit,
    onTap: () -> Unit,
    onReglages: () -> Unit,
    modifier: Modifier,
    appuiLongReglages: Boolean = true,
    suivreVisible: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    var visible by remember { mutableStateOf(!suivreVisible) }
    Column(
        modifier
            .let { if (suivreVisible) it.suivreVisibilite { v -> visible = v } else it }
            .clip(shape)
            .background(Panel)
            .border(if (actif) 2.dp else 1.dp, if (actif) Red else Border, shape)
            .combinedClickable(onClick = onTap, onLongClick = if (appuiLongReglages) onReglages else null)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            ApercuModele(modele, animer = visible, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
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

/**
 * Fait tourner l'animation (ou l'horloge) dans un aperçu (uniquement à l'écran, pas sur la matrice).
 * L'image change pendant la composition : préférer [ApercuModele] / [rememberApercu] (dessin seul).
 */
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
