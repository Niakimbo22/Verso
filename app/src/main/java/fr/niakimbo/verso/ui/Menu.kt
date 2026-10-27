package fr.niakimbo.verso.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.BuildConfig
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import fr.niakimbo.verso.ui.verre.contourActif
import fr.niakimbo.verso.ui.verre.verre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Où mène une entrée du menu latéral. */
enum class Destination {
    ACCUEIL, MODELES, FAVORIS, RECENTS, DESSINS, CREER,
    AUTO, WIDGET_AJOUT, WIDGETS,
    GLYPH, TOY,
    NOUVEAUTES, JOURNAL, PLUS, DIAGNOSTIC, FIGEE,
}

private data class Entree(val symbole: String, val texte: String, val destination: Destination, val sous: String? = null)

private val GROUPES: List<Pair<String, List<Entree>>> = listOf(
    "MODÈLES" to listOf(
        Entree("⌂", "Accueil", Destination.ACCUEIL),
        Entree("▦", "Tous les modèles", Destination.MODELES, "rayons, recherche"),
        Entree("★", "Favoris", Destination.FAVORIS, "et leur ordre"),
        Entree("◷", "Récents", Destination.RECENTS),
        Entree("✎", "Mes dessins", Destination.DESSINS, "créer, importer, partager"),
        Entree("＋", "Créer un dessin", Destination.CREER),
    ),
    "AUTOMATIQUE" to listOf(
        Entree("⚡", "Règles automatiques", Destination.AUTO, "la nuit, en charge, au réveil…"),
    ),
    "WIDGETS" to listOf(
        Entree("＋", "Ajouter un widget", Destination.WIDGET_AJOUT, "galerie, 8 tailles"),
        Entree("▣", "Mes widgets", Destination.WIDGETS, "posés, anciens widgets"),
    ),
    "GLYPH" to listOf(
        Entree("🔌", "Bouton Glyph", Destination.GLYPH, "état, activation"),
        Entree("◎", "Choisir le Glyph Toy", Destination.TOY, "affichage app fermée"),
    ),
    "INFOS" to listOf(
        Entree("✦", "Nouveautés", Destination.NOUVEAUTES, "v${BuildConfig.VERSION_NAME}"),
        Entree("☰", "Journal des mises à jour", Destination.JOURNAL, "toutes les versions"),
        Entree("⚙", "Diagnostic et fluidité", Destination.DIAGNOSTIC),
        Entree("↻", "Matrice figée ? Redémarrer", Destination.FIGEE),
    ),
)

/**
 * Menu latéral ☰ en verre : glisse depuis la gauche avec un ressort, ses entrées arrivent une par une,
 * on le ferme en le poussant vers la gauche, en touchant à côté ou avec le retour.
 * Rien n'y est caché qui ne soit aussi sur l'écran : c'est un raccourci vers chaque endroit.
 */
@Composable
fun BoxScope.MenuLateral(ouvert: Boolean, onFermer: () -> Unit, onAller: (Destination) -> Unit) {
    val largeur = 304.dp
    val largeurPx = with(LocalDensity.current) { largeur.toPx() }
    val progres = remember { Animatable(0f) }
    val portee = rememberCoroutineScope()
    LaunchedEffect(ouvert) {
        progres.animateTo(if (ouvert) 1f else 0f, spring(dampingRatio = if (ouvert) 0.78f else 1f, stiffness = 380f))
    }
    if (!ouvert && progres.value == 0f) return

    // Voile : touche à côté = fermer.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progres.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFermer),
    )

    val forme = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
    Column(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(largeur)
            .graphicsLayer { translationX = -(1f - progres.value) * (largeurPx + 40f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (progres.value < 0.65f) onFermer()
                        else portee.launch { progres.animateTo(1f, spring(0.8f, 400f)) }
                    },
                    onDragCancel = { portee.launch { progres.animateTo(1f) } },
                ) { change, dx ->
                    change.consume()
                    portee.launch { progres.snapTo((progres.value + dx / largeurPx).coerceIn(0f, 1f)) }
                }
            }
            .verre(forme, teinte = Color(0xFF0A0A0A).copy(alpha = 0.55f), flou = 24.dp, refraction = 26.dp)
            .contourActif(false, forme)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        var i = 0
        Apparition(ouvert, i++) { EnTeteMenu() }
        Spacer(Modifier.height(12.dp))
        GROUPES.forEach { (titre, entrees) ->
            Apparition(ouvert, i++) {
                Text(titre, color = Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp))
            }
            entrees.forEach { e ->
                Apparition(ouvert, i++) { LigneMenu(e) { onAller(e.destination) } }
            }
        }
        Apparition(ouvert, i++) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fond animé", color = Color.White, fontSize = 14.sp)
                    Text("lueurs qui bougent (plus gourmand)", color = Discret, fontSize = 11.sp)
                }
                Switch(
                    checked = ReglagesUi.fondAnime,
                    onCheckedChange = { ReglagesUi.changerFondAnime(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
                )
            }
        }
    }
}

/** Chaque ligne arrive à son tour (glisse depuis la gauche + fondu), à l'ouverture. */
@Composable
private fun Apparition(ouvert: Boolean, rang: Int, contenu: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(ouvert) {
        if (ouvert) {
            a.snapTo(0f)
            delay(60L + 28L * rang)
            a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f))
        }
    }
    val decal = with(LocalDensity.current) { 36.dp.toPx() }
    Box(Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationX = -(1f - a.value) * decal }) { contenu() }
}

@Composable
private fun EnTeteMenu() {
    val app = LocalContext.current.applicationContext as VersoApp
    val frame = app.matrix.lastFrame.collectAsStateWithLifecycle()
    val actif by app.player.actif.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp).background(Color.Black, RoundedCornerShape(50))) {
            DotMatrix({ frame.value }, Modifier.size(58.dp).padding(5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Verso", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "v${BuildConfig.VERSION_NAME} · " + (actif?.nom ?: "matrice éteinte"),
                color = Discret, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LigneMenu(e: Entree, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .appui(onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(e.symbole, color = Color.White, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.texte, color = Color.White, fontSize = 15.sp)
            e.sous?.let { Text(it, color = Discret, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("›", color = Discret, fontSize = 18.sp)
    }
}
