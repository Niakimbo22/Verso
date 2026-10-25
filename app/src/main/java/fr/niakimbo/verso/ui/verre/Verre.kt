package fr.niakimbo.verso.ui.verre

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Liquid Glass de Verso (façon iOS) : réfraction sur les bords, flou, vibrance et reflet,
 * calculés au GPU par la bibliothèque Backdrop (Kyant, Apache 2.0).
 *
 * Le verre a besoin de quelque chose derrière lui : [EcranVerre] dessine un fond animé
 * (lueurs rouges et blanches, grille de points) que le verre réfracte.
 */

val Rouge = Color(0xFFD71921)
val Discret = Color(0xFF9A9A9A)

/** Ce que le verre voit derrière lui (null = pas de fond : panneau sombre classique). */
val LocalFond = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Écran en verre : fond animé, [contenu] (en verre sur le fond), et [dessus] (barres et boutons flottants,
 * en verre sur le fond ET le contenu qui défile dessous).
 */
@Composable
fun EcranVerre(
    defilement: ScrollState? = null,
    dessus: @Composable BoxScope.() -> Unit = {},
    contenu: @Composable BoxScope.() -> Unit,
) {
    val fond = rememberLayerBackdrop()
    val tout = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().layerBackdrop(tout)) {
            FondAnime(defilement, Modifier.fillMaxSize().layerBackdrop(fond))
            CompositionLocalProvider(LocalFond provides fond) { contenu() }
        }
        CompositionLocalProvider(LocalFond provides tout) { dessus() }
    }
}

/**
 * Fond : noir, trois lueurs (rouge Nothing, blanc, rouge sombre) qui dérivent lentement et
 * suivent le défilement (parallaxe), plus une grille de points discrète.
 * Animé à 20 images/s seulement : assez pour que le verre bouge, sans vider la batterie.
 */
@Composable
private fun FondAnime(defilement: ScrollState?, modifier: Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            t += 0.05f
        }
    }
    Canvas(modifier) {
        val d = defilement?.value?.toFloat() ?: 0f
        drawRect(Color.Black)
        fun lueur(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(
            Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
            radius = r, center = Offset(cx, cy),
        )
        val w = size.width
        val h = size.height
        lueur(w * (0.85f + 0.06f * sin(t * 0.21f)), h * 0.12f - d * 0.25f + 40f * cos(t * 0.17f), w * 0.75f, Rouge.copy(alpha = 0.55f))
        lueur(w * (0.08f + 0.05f * cos(t * 0.13f)), h * 0.48f - d * 0.12f + 60f * sin(t * 0.11f), w * 0.65f, Color.White.copy(alpha = 0.13f))
        lueur(w * (0.55f + 0.1f * sin(t * 0.09f)), h * 0.92f - d * 0.18f, w * 0.8f, Color(0xFF7A0C10).copy(alpha = 0.6f))
        // Grille de points (comme une matrice éteinte géante).
        val pas = 18.dp.toPx()
        val r = 1.1.dp.toPx()
        val decalage = -(d * 0.5f) % pas
        val point = Color.White.copy(alpha = 0.07f)
        var y = decalage
        while (y < h) {
            var x = pas / 2
            while (x < w) {
                drawCircle(point, r, Offset(x, y))
                x += pas
            }
            y += pas
        }
    }
}

/**
 * Le verre lui-même. Sans fond disponible (aperçu, fenêtre ☰…), un panneau sombre classique.
 * [teinte] colore le verre (rouge pour un bouton principal). [refraction] : épaisseur du bord qui réfracte.
 */
@Composable
fun Modifier.verre(
    forme: CornerBasedShape = RoundedCornerShape(24.dp),
    teinte: Color = Color.White.copy(alpha = 0.06f),
    flou: Dp = 12.dp,
    refraction: Dp = 16.dp,
    ombre: Boolean = true,
): Modifier {
    val fond = LocalFond.current
        ?: return this.clip(forme).background(Color(0xFF111111)).background(teinte).border(1.dp, Color(0xFF2A2A2A), forme)
    return this.drawBackdrop(
        backdrop = fond,
        shape = { forme },
        effects = {
            vibrancy()
            blur(flou.toPx())
            lens(refraction.toPx(), refraction.toPx() * 2f, depthEffect = true)
        },
        highlight = { Highlight.Default },
        shadow = if (ombre) ({ Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.4f)) }) else null,
        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.22f)); drawRect(teinte) },
    )
}

/** Effet d'appui façon iOS : le verre s'enfonce un peu, avec une petite vibration. */
@Composable
fun Modifier.appui(onClick: () -> Unit, vibrer: Boolean = true): Modifier {
    val source = remember { MutableInteractionSource() }
    val enfonce by source.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (enfonce) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "appui")
    val vibreur = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = echelle; scaleY = echelle }
        .clickable(interactionSource = source, indication = null) {
            if (vibrer) vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Contour rouge des éléments actifs / choisis, posé par-dessus le verre. */
fun Modifier.contourActif(actif: Boolean, forme: CornerBasedShape): Modifier =
    if (actif) this.border(1.5.dp, Rouge, forme) else this.border(0.5.dp, Color.White.copy(alpha = 0.12f), forme)

/** Panneau de verre (cartes). */
@Composable
fun PanneauVerre(
    modifier: Modifier = Modifier,
    forme: CornerBasedShape = RoundedCornerShape(24.dp),
    actif: Boolean = false,
    teinte: Color = if (actif) Rouge.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.06f),
    onClick: (() -> Unit)? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .let { if (onClick != null) it.appui(onClick) else it }
            .verre(forme, teinte)
            .contourActif(actif, forme),
        content = contenu,
    )
}

/** Bouton de verre ; [principal] = teinté rouge Nothing. */
@Composable
fun BoutonVerre(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    taille: TextUnit = 16.sp,
    couleurTexte: Color = Color.White,
) {
    val forme = RoundedCornerShape(50)
    Box(
        modifier
            .appui(onClick)
            .verre(forme, if (principal) Rouge.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.08f), refraction = 12.dp)
            .contourActif(false, forme),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte, color = couleurTexte, fontSize = taille, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 15.dp),
        )
    }
}

/** Petite puce de verre (choix, filtres) ; [choisie] = teintée rouge. */
@Composable
fun PuceVerre(texte: String, choisie: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forme = RoundedCornerShape(50)
    Box(
        modifier
            .appui(onClick)
            .verre(forme, if (choisie) Rouge.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f), flou = 8.dp, refraction = 8.dp, ombre = false)
            .contourActif(false, forme),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte, color = Color.White, fontSize = 13.sp,
            fontWeight = if (choisie) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            maxLines = 1,
        )
    }
}

/** Bouton rond de verre (←, ✕…). */
@Composable
fun RondVerre(symbole: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(50)
    Box(
        modifier.appui(onClick).verre(forme, refraction = 10.dp).contourActif(false, forme),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbole, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}
