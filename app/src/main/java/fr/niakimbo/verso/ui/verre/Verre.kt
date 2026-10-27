package fr.niakimbo.verso.ui.verre

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import fr.niakimbo.verso.ui.ModePerf
import fr.niakimbo.verso.ui.ReglagesUi
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Liquid Glass de Verso (façon iOS) : réfraction sur les bords, flou, vibrance et reflet,
 * calculés au GPU par la bibliothèque Backdrop (Kyant, Apache 2.0).
 *
 * Le verre a besoin de quelque chose derrière lui : [EcranVerre] dessine un fond (lueurs rouges
 * et blanches, grille de points) que le verre réfracte.
 *
 * Performance (26/10, mesurée par le banc du Diagnostic) :
 * - le fond ne bouge plus tout seul (option « Fond animé » dans Plus) : sinon chaque verre de
 *   l'écran recalcule flou + réfraction 20 fois par seconde, même quand on ne touche à rien ;
 * - les lambdas passées à Backdrop sont mémorisées : Backdrop compare ses paramètres et, s'ils
 *   changent, reconstruit ses effets (flou + shader) — avant, à chaque recomposition ;
 * - le fond et le contenu ont leur propre calque : les calques de verre qui les englobent les
 *   réutilisent au lieu de tout redessiner.
 */

val Rouge = Color(0xFFD71921)
val Discret = Color(0xFF9A9A9A)

/** Ce que le verre voit derrière lui (null = pas de fond : panneau sombre classique). */
val LocalFond = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Verre « flottant » (mini-lecteur, barres du haut, boutons flottants, menu) : il ne floute que le fond,
 * pas le contenu qui défile dessous (28/10, « au plus fluide mais je veux du style »). Le style est
 * compensé par un verre fumé plus dense, un dégradé de lumière en haut et une lueur rouge au bas.
 */
val LocalFlottant = staticCompositionLocalOf { false }

/**
 * Écran en verre :
 * - [contenu] en verre sur le fond ;
 * - [feuille] (facultative, ex. la bibliothèque) par-dessus le contenu : son verre floute le contenu ;
 * - [dessus] (barres et boutons flottants) en verre « flottant » sur le fond seul ([LocalFlottant]).
 *
 * Avant le 28/10, [dessus] floutait tout l'écran (fond + contenu + feuille) : tout l'écran était
 * enregistré dans un calque à chaque image, et chaque verre du contenu y était recalculé une 2ᵉ fois.
 * Le banc (« ancien moteur ») garde ce comportement pour la comparaison.
 */
@Composable
fun EcranVerre(
    defilement: ScrollState? = null,
    dessus: @Composable BoxScope.() -> Unit = {},
    feuille: (@Composable BoxScope.() -> Unit)? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    ReglagesUi.charger(LocalContext.current)
    val fond = rememberLayerBackdrop()
    val principal = rememberLayerBackdrop()
    val tout = rememberLayerBackdrop()
    val ancien = ModePerf.ancien
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(if (ancien) Modifier.fillMaxSize().layerBackdrop(tout) else Modifier.fillMaxSize()) {
            val base: @Composable () -> Unit = {
                FondAnime(defilement, Modifier.fillMaxSize().layerBackdrop(fond))
                CompositionLocalProvider(LocalFond provides fond) {
                    // Calque à part : redessiner le verre qui le recouvre ne redessine pas tout le contenu.
                    Box(Modifier.fillMaxSize().graphicsLayer(), content = contenu)
                }
            }
            if (feuille == null) {
                base()
            } else {
                Box(Modifier.fillMaxSize().layerBackdrop(principal)) { base() }
                CompositionLocalProvider(LocalFond provides principal) {
                    Box(Modifier.fillMaxSize(), content = feuille)
                }
            }
        }
        if (ancien) {
            CompositionLocalProvider(LocalFond provides tout) { dessus() }
        } else {
            CompositionLocalProvider(LocalFond provides fond, LocalFlottant provides true) { dessus() }
        }
    }
}

/**
 * Fond : noir, trois lueurs (rouge Nothing, blanc, rouge sombre) qui suivent le défilement (parallaxe),
 * plus une grille de points discrète (une seule commande de dessin, positions calculées une fois).
 * Les lueurs ne dérivent toutes seules que si « Fond animé » est activé (20 images/s).
 */
@Composable
private fun FondAnime(defilement: ScrollState?, modifier: Modifier) {
    val anime = ReglagesUi.fondAnime || ModePerf.ancien
    val temps = remember { mutableFloatStateOf(0f) }
    if (anime) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(50)
                temps.floatValue += 0.05f
            }
        }
    }
    val ancien = ModePerf.ancien
    Spacer(
        modifier
            .graphicsLayer()
            .drawWithCache {
                val pas = 18.dp.toPx()
                val r = 1.1.dp.toPx()
                val w = size.width
                val h = size.height
                // Grille un peu plus haute que l'écran : on la décale de moins d'un pas en défilant.
                val points = ArrayList<Offset>()
                var y = 0f
                while (y < h + pas) {
                    var x = pas / 2
                    while (x < w) {
                        points += Offset(x, y)
                        x += pas
                    }
                    y += pas
                }
                val point = Color.White.copy(alpha = 0.07f)
                onDrawBehind {
                    val d = defilement?.value?.toFloat() ?: 0f
                    val t = temps.floatValue
                    drawRect(Color.Black)
                    lueur(w * (0.85f + 0.06f * sin(t * 0.21f)), h * 0.12f - d * 0.25f + 40f * cos(t * 0.17f), w * 0.8f, Rouge.copy(alpha = 0.7f))
                    lueur(w * (0.08f + 0.05f * cos(t * 0.13f)), h * 0.48f - d * 0.12f + 60f * sin(t * 0.11f), w * 0.7f, Color.White.copy(alpha = 0.18f))
                    lueur(w * (0.55f + 0.1f * sin(t * 0.09f)), h * 0.92f - d * 0.18f, w * 0.85f, Color(0xFF9A1018).copy(alpha = 0.7f))
                    val decalage = -(d * 0.5f) % pas
                    if (ancien) {
                        // Ancien rendu (pour la comparaison du banc) : un cercle par point.
                        for (p in points) drawCircle(point, r, Offset(p.x, p.y + decalage))
                    } else {
                        translate(top = decalage) {
                            drawPoints(points, PointMode.Points, point, strokeWidth = r * 2, cap = StrokeCap.Round)
                        }
                    }
                }
            },
    )
}

private fun DrawScope.lueur(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(
    Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
    radius = r, center = Offset(cx, cy),
)

// ============================================================================================
// Appui façon iOS 26 : le verre gonfle un peu, s'étire vers le doigt, et une lueur suit le doigt.
// ============================================================================================

private const val SHADER_LUEUR = """
uniform float2 position;
uniform float rayon;
uniform float intensite;
half4 main(float2 coord) {
    float i = smoothstep(rayon, rayon * 0.35, distance(coord, position)) * intensite;
    return half4(i, i, i, i);
}"""

/**
 * État d'appui d'un élément de verre. Ne consomme aucun évènement : le clic et le défilement
 * marchent comme avant ; si le doigt part en défilement, l'effet se relâche.
 */
@Stable
class PressionVerre internal constructor(private val portee: CoroutineScope) {
    private val progres = Animatable(0f)
    private val position = Animatable(Offset.Zero, Offset.VectorConverter)
    private var depart = Offset.Zero
    private val shader by lazy { RuntimeShader(SHADER_LUEUR) }

    val valeur: Float get() = progres.value

    private fun relacher() {
        portee.launch { progres.animateTo(0f, spring(0.5f, 300f)) }
        portee.launch { position.animateTo(depart, spring(0.5f, 300f)) }
    }

    val gestes: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            depart = down.position
            portee.launch { position.snapTo(down.position) }
            portee.launch { progres.animateTo(1f, spring(0.5f, 300f)) }
            while (true) {
                val evt = awaitPointerEvent(PointerEventPass.Final)
                val c = evt.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                // Mouvement pris par un défilement : on relâche tout de suite.
                if (c.isConsumed && c.positionChange() != Offset.Zero) break
                portee.launch { position.snapTo(c.position) }
            }
            relacher()
        }
    }

    /** Gonfle et étire le verre (calque) : lu au moment du dessin, sans recomposer. */
    internal val calque: GraphicsLayerScope.() -> Unit = {
        val p = progres.value
        if (p > 0f && size.height > 0f) {
            val grandit = lerp(1f, 1f + 4.dp.toPx() / size.height, p)
            val maxi = size.minDimension
            val o = position.value - depart
            translationX = maxi * tanh(0.05f * o.x / maxi)
            translationY = maxi * tanh(0.05f * o.y / maxi)
            val etire = 4.dp.toPx() / size.height
            val angle = atan2(o.y, o.x)
            scaleX = grandit + etire * abs(cos(angle) * o.x / size.maxDimension) * min(size.width / size.height, 1f)
            scaleY = grandit + etire * abs(sin(angle) * o.y / size.maxDimension) * min(size.height / size.width, 1f)
        }
    }

    /** Lueur sous le doigt (dessinée dans le verre). */
    internal val lueur: DrawScope.() -> Unit = {
        val p = progres.value
        if (p > 0.001f) {
            drawRect(Color.White.copy(alpha = 0.07f * p), blendMode = BlendMode.Plus)
            val pos = position.value
            shader.setFloatUniform("position", pos.x.coerceIn(0f, size.width), pos.y.coerceIn(0f, size.height))
            shader.setFloatUniform("rayon", size.minDimension * 1.4f)
            shader.setFloatUniform("intensite", 0.16f * p)
            drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
        }
    }
}

@Composable
fun rememberPressionVerre(): PressionVerre {
    val portee = rememberCoroutineScope()
    return remember(portee) { PressionVerre(portee) }
}

/** Clic d'un élément de verre : petite vibration + effet d'appui iOS. */
@Composable
fun Modifier.clicVerre(pression: PressionVerre, onClick: () -> Unit): Modifier {
    val vibreur = LocalHapticFeedback.current
    return this
        .then(pression.gestes)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * Le verre lui-même. Sans fond disponible (aperçu, fenêtre ☰…), un panneau sombre classique.
 * [teinte] colore le verre (rouge pour un bouton principal). [refraction] : épaisseur du bord qui réfracte.
 * [pression] : effet d'appui iOS. [exporte] : ce verre sert lui-même de fond aux verres posés dessus.
 */
@Composable
fun Modifier.verre(
    forme: CornerBasedShape = RoundedCornerShape(24.dp),
    teinte: Color = Color.White.copy(alpha = 0.05f),
    flou: Dp = 7.dp,
    refraction: Dp = 22.dp,
    ombre: Boolean = true,
    irisation: Boolean = true,
    pression: PressionVerre? = null,
    exporte: LayerBackdrop? = null,
): Modifier {
    val fond = LocalFond.current
        ?: return this.clip(forme).background(Color(0xFF111111)).background(teinte).border(1.dp, Color(0xFF2A2A2A), forme)
    // Banc de comparaison : l'ancien code recréait ces lambdas à chaque recomposition.
    val cle: Any = if (ModePerf.ancien) Any() else Unit
    // Qualité (Plus, ou optimisation automatique du banc) : Équilibrée = sans irisation ;
    // Légère = flou et réfraction réduits, sans irisation ni ombre. Le banc « ancien » garde la qualité haute.
    val q = if (ModePerf.ancien) ReglagesUi.QualiteVerre.HAUTE else ReglagesUi.qualiteVerre
    val legere = q == ReglagesUi.QualiteVerre.LEGERE
    val flou = if (legere) flou * 0.6f else flou
    val refraction = if (legere) refraction * 0.6f else refraction
    val irisation = irisation && q == ReglagesUi.QualiteVerre.HAUTE
    val ombre = ombre && !legere
    val flottant = LocalFlottant.current && !ModePerf.ancien
    val shape = remember(forme, cle) { { forme } }
    val effets: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember(flou, refraction, irisation, cle) {
        {
            vibrancy()
            blur(flou.toPx())
            // Réfraction forte sur les bords + légère irisation (comme le Liquid Glass d'iOS).
            lens(refraction.toPx(), refraction.toPx() * 2.6f, depthEffect = true, chromaticAberration = irisation)
        }
    }
    val reflet = remember(cle) { { Highlight(width = 1.dp, style = HighlightStyle.Default(intensity = 0.85f)) } }
    val ombrage: (() -> Shadow?)? = remember(ombre, cle) { if (ombre) ({ Shadow(radius = 22.dp, color = Color.Black.copy(alpha = 0.45f)) }) else null }
    val surface: DrawScope.() -> Unit = remember(teinte, cle, flottant) {
        if (!flottant) {
            { drawRect(Color.Black.copy(alpha = 0.10f)); drawRect(teinte) }
        } else {
            {
                // Verre fumé : le contenu qui passe dessous n'est plus flouté, on le couvre joliment.
                drawRect(Color.Black.copy(alpha = 0.34f))
                drawRect(teinte)
                // Lumière qui tombe du haut, comme sur une vraie plaque de verre.
                drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.13f), 0.5f to Color.White.copy(alpha = 0.02f), 1f to Color.Transparent))
                // Lueur rouge Nothing au bas, très légère.
                drawRect(Brush.radialGradient(listOf(Rouge.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 1.1f), radius = size.maxDimension * 0.6f))
                // Liseré lumineux en haut.
                drawRect(Color.White.copy(alpha = 0.22f), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }
        }
    }
    return this.drawBackdrop(
        backdrop = fond,
        shape = shape,
        effects = effets,
        highlight = reflet,
        shadow = ombrage,
        layerBlock = pression?.calque,
        exportedBackdrop = exporte,
        onDrawSurface = surface,
        onDrawFront = pression?.lueur,
    )
}

/** Effet d'appui simple (éléments qui ne sont pas en verre) : s'enfonce un peu, avec une petite vibration. */
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

/** Panneau de verre (cartes). Avec [onClick] : effet d'appui iOS. */
@Composable
fun PanneauVerre(
    modifier: Modifier = Modifier,
    forme: CornerBasedShape = RoundedCornerShape(24.dp),
    actif: Boolean = false,
    teinte: Color = if (actif) Rouge.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.06f),
    onClick: (() -> Unit)? = null,
    contenu: @Composable BoxScope.() -> Unit,
) {
    val pression = if (onClick != null) rememberPressionVerre() else null
    Box(
        modifier
            .let { if (onClick != null && pression != null) it.clicVerre(pression, onClick) else it }
            .verre(forme, teinte, pression = pression)
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
    val pression = rememberPressionVerre()
    Box(
        modifier
            .clicVerre(pression, onClick)
            .verre(forme, if (principal) Rouge.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.08f), refraction = 12.dp, pression = pression)
            .contourActif(false, forme),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte, color = couleurTexte, fontSize = taille, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 15.dp),
        )
    }
}

/** Petite puce de verre (choix, filtres) ; [choisie] = teintée rouge. */
@Composable
fun PuceVerre(texte: String, choisie: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forme = RoundedCornerShape(50)
    val pression = rememberPressionVerre()
    Box(
        modifier
            .clicVerre(pression, onClick)
            // Petites puces : pas d'irisation (invisible à cette taille, et ça allège le shader).
            .verre(
                forme, if (choisie) Rouge.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.06f),
                flou = 8.dp, refraction = 8.dp, ombre = false, irisation = false, pression = pression,
            )
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

/** Bouton rond de verre (←, ✕, ‹ ›…). */
@Composable
fun RondVerre(symbole: String, onClick: () -> Unit, modifier: Modifier = Modifier, couleur: Color = Color.White, taille: TextUnit = 20.sp) {
    val forme = RoundedCornerShape(50)
    val pression = rememberPressionVerre()
    Box(
        modifier.clicVerre(pression, onClick).verre(forme, refraction = 10.dp, pression = pression).contourActif(false, forme),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbole, color = couleur, fontSize = taille, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}
