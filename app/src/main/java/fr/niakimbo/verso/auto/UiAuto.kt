package fr.niakimbo.verso.auto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import fr.niakimbo.verso.ui.verre.Discret
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

internal val Fond = Color.Black
internal val Forme = RoundedCornerShape(24.dp)

/** Picto 9×9 en points ronds, façon Glyph. */
@Composable
internal fun Picto(type: TypeQuand, couleur: Color, modifier: Modifier) {
    Canvas(modifier) {
        val n = type.points.size
        val cell = size.minDimension / n
        val r = cell * 0.38f
        type.points.forEachIndexed { y, ligne ->
            ligne.forEachIndexed { x, c ->
                drawCircle(
                    if (c == 'X') couleur else Color.White.copy(alpha = 0.08f),
                    r,
                    Offset(cell * (x + 0.5f), cell * (y + 0.5f)),
                )
            }
        }
    }
}

/** Titre de section en petites capitales espacées, comme le reste de Verso. */
@Composable
internal fun Etiquette(texte: String, modifier: Modifier = Modifier, couleur: Color = Discret) {
    Text(texte, color = couleur, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, modifier = modifier)
}

/**
 * Liste qu'on réordonne au doigt : appui long sur un élément, puis glisser (même geste que les favoris).
 * L'ordre est enregistré au lâcher.
 */
@Composable
internal fun <T> ListeReordonnable(
    elements: List<T>,
    cle: (T) -> String,
    onOrdre: (List<String>) -> Unit,
    contenu: @Composable (T, Boolean) -> Unit,
) {
    val idsRecus = elements.map(cle)
    var ordre by remember(idsRecus) { mutableStateOf(idsRecus) }
    var tire by remember { mutableStateOf<Int?>(null) }
    var decalage by remember { mutableStateOf(0f) }
    val cases = remember { mutableStateMapOf<Int, Rect>() }
    var conteneur by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val vibreur = LocalHapticFeedback.current
    val ordreActuel by rememberUpdatedState(ordre)
    val parId = elements.associateBy(cle)

    fun finir() {
        if (tire != null) onOrdre(ordreActuel)
        tire = null
        decalage = 0f
    }

    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { conteneur = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        tire = cases.entries.firstOrNull { it.value.contains(pos) && it.key < ordreActuel.size }?.key
                        decalage = 0f
                        if (tire != null) vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, delta ->
                        val i = tire ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        decalage += delta.y
                        val depart = cases[i] ?: return@detectDragGesturesAfterLongPress
                        val centre = Offset(depart.center.x, depart.center.y + decalage)
                        val j = cases.entries.firstOrNull { (k, r) -> k != i && k < ordreActuel.size && r.contains(centre) }?.key
                            ?: return@detectDragGesturesAfterLongPress
                        val arrivee = cases[j] ?: return@detectDragGesturesAfterLongPress
                        ordre = ordreActuel.toMutableList().apply { add(j, removeAt(i)) }
                        decalage += depart.top - arrivee.top
                        tire = j
                        vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDragEnd = ::finir,
                    onDragCancel = ::finir,
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordre.forEachIndexed { index, id ->
            val enMain = index == tire
            key(id) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (enMain) 1f else 0f)
                        .onGloballyPositioned { c -> conteneur?.let { cases[index] = it.localBoundingBoxOf(c) } }
                        .graphicsLayer {
                            if (enMain) {
                                translationY = decalage
                                scaleX = 1.03f
                                scaleY = 1.03f
                                alpha = 0.92f
                            }
                        },
                ) {
                    parId[id]?.let { contenu(it, enMain) }
                }
            }
        }
    }
}
