package fr.niakimbo.verso.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import kotlinx.coroutines.delay

private val LedOff = Color(0xFF1E1E1E)

/**
 * Prévient quand l'élément entre ou sort de l'écran (en défilant) : utile dans une liste qui n'est
 * pas « paresseuse » (les listes paresseuses ne composent déjà que ce qui est à l'écran).
 */
fun Modifier.suivreVisibilite(onChange: (Boolean) -> Unit): Modifier = onGloballyPositioned { c ->
    val b = c.boundsInWindow() // déjà coupé par la zone qui défile : vide quand l'élément est hors écran
    onChange(b.width > 0f && b.height > 0f)
}

/**
 * Rendu dot-matrix d'une image 13×13, rond comme la vraie matrice du Phone (4a) Pro.
 * [frame] est lu **au moment du dessin** : une nouvelle image redessine les 169 points, sans
 * recomposer l'écran autour. null = matrice éteinte. La taille vient du [modifier].
 */
@Composable
fun DotMatrix(frame: () -> MatrixFrame?, modifier: Modifier = Modifier, ledOff: Color = LedOff) {
    Spacer(
        modifier.drawWithCache {
            val cell = size.minDimension / MatrixFrame.SIDE
            val radius = cell * 0.36f
            val originX = (size.width - cell * MatrixFrame.SIDE) / 2f
            val originY = (size.height - cell * MatrixFrame.SIDE) / 2f
            // Centres des LEDs qui existent (la matrice est ronde), calculés une fois par taille.
            val xs = ArrayList<Int>()
            val ys = ArrayList<Int>()
            val centres = ArrayList<Offset>()
            for (y in 0 until MatrixFrame.SIDE) for (x in 0 until MatrixFrame.SIDE) {
                if (!MatrixFrame.isOnMatrix(x, y)) continue
                xs += x
                ys += y
                centres += Offset(originX + cell * (x + 0.5f), originY + cell * (y + 0.5f))
            }
            onDrawBehind {
                val f = frame()
                for (i in centres.indices) {
                    val value = f?.get(xs[i], ys[i]) ?: 0
                    val color = if (value > 0) Color.White.copy(alpha = 0.3f + 0.7f * value / MatrixFrame.MAX) else ledOff
                    drawCircle(color, radius, centres[i])
                }
            }
        },
    )
}

/** Même rendu avec une image fixe (lue à la composition). */
@Composable
fun DotMatrix(frame: MatrixFrame?, modifier: Modifier = Modifier, ledOff: Color = LedOff) =
    DotMatrix({ frame }, modifier, ledOff)

/** Image affichée par un aperçu immobile. */
fun imageFixe(modele: Modele): MatrixFrame = if (modele.dynamique != null) modele.frameDynamique() else modele.apercu

/**
 * Aperçu qui tourne (animation, horloge) dans un [State] : à lire dans le dessin
 * (`DotMatrix({ etat.value })`) pour ne redessiner que la matrice à chaque image.
 */
@Composable
fun rememberApercu(modele: Modele?, animer: Boolean): State<MatrixFrame?> =
    produceState(modele?.let(::imageFixe), modele, animer) {
        val m = modele ?: run { value = null; return@produceState }
        value = imageFixe(m)
        if (!animer || !m.estVivant) return@produceState
        if (m.dynamique != null) {
            while (true) {
                delay(m.msAvantProchaineImage())
                value = m.frameDynamique()
            }
        } else {
            var i = 0
            while (true) {
                delay(m.images[i].dureeMs)
                i = (i + 1) % m.images.size
                value = m.images[i].frame
            }
        }
    }

/** Aperçu d'un modèle, animé ou non : seule la matrice se redessine. */
@Composable
fun ApercuModele(modele: Modele?, animer: Boolean, modifier: Modifier = Modifier) {
    if (ModePerf.ancien) {
        // Ancien chemin (banc de comparaison) : l'image change pendant la composition.
        DotMatrix(modele?.let { if (animer) apercuAnime(it) else imageFixe(it) }, modifier)
        return
    }
    val etat = rememberApercu(modele, animer)
    DotMatrix({ etat.value }, modifier)
}
