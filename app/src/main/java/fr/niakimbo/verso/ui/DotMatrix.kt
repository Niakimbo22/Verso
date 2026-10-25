package fr.niakimbo.verso.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import fr.niakimbo.verso.matrix.MatrixFrame

private val LedOff = Color(0xFF1E1E1E)

/**
 * Prévient quand l'élément entre ou sort de l'écran (en défilant) : les aperçus ne s'animent
 * que lorsqu'on les voit, ce qui permet d'animer toute la bibliothèque sans ralentir l'app.
 */
fun Modifier.suivreVisibilite(onChange: (Boolean) -> Unit): Modifier = onGloballyPositioned { c ->
    val b = c.boundsInWindow() // déjà coupé par la zone qui défile : vide quand l'élément est hors écran
    onChange(b.width > 0f && b.height > 0f)
}

/**
 * Rendu dot-matrix d'une image 13×13, rond comme la vraie matrice du Phone (4a) Pro.
 * [frame] null = matrice éteinte. La taille vient du [modifier].
 */
@Composable
fun DotMatrix(frame: MatrixFrame?, modifier: Modifier = Modifier, ledOff: Color = LedOff) {
    Canvas(modifier) {
        val cell = size.minDimension / MatrixFrame.SIDE
        val radius = cell * 0.36f
        val originX = (size.width - cell * MatrixFrame.SIDE) / 2f
        val originY = (size.height - cell * MatrixFrame.SIDE) / 2f
        for (y in 0 until MatrixFrame.SIDE) {
            for (x in 0 until MatrixFrame.SIDE) {
                if (!MatrixFrame.isOnMatrix(x, y)) continue
                val value = frame?.get(x, y) ?: 0
                val color = if (value > 0) {
                    Color.White.copy(alpha = 0.3f + 0.7f * value / MatrixFrame.MAX)
                } else {
                    ledOff
                }
                drawCircle(color, radius, Offset(originX + cell * (x + 0.5f), originY + cell * (y + 0.5f)))
            }
        }
    }
}
