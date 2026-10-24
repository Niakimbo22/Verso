package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import kotlin.math.roundToInt

/** Petite toile 13×13 pour dessiner les modèles calculés. Les pixels hors du disque sont ignorés. */
class Grille {
    val px = IntArray(MatrixFrame.SIZE)

    /** Allume (x, y) à [v] (0-255) sans jamais assombrir un pixel déjà plus clair. */
    fun allumer(x: Int, y: Int, v: Int) {
        if (x !in 0 until N || y !in 0 until N || !MatrixFrame.isOnMatrix(x, y)) return
        val i = y * N + x
        if (v > px[i]) px[i] = v.coerceAtMost(MatrixFrame.MAX)
    }

    fun get(x: Int, y: Int): Int = if (x in 0 until N && y in 0 until N) px[y * N + x] else 0

    fun frame(): MatrixFrame = MatrixFrame(px)

    companion object {
        const val N = MatrixFrame.SIDE
        const val C = N / 2

        /** Niveau 0-9 (comme dans les fichiers « dessin ») → luminosité 0-255. */
        fun niveau(n: Number): Int = (n.toFloat() * MatrixFrame.MAX / 9f).roundToInt().coerceIn(0, MatrixFrame.MAX)

        fun depuisArt(vararg lignes: String): Grille = Grille().also { g ->
            lignes.forEachIndexed { y, l -> l.forEachIndexed { x, c -> if (c == 'X') g.allumer(x, y, MatrixFrame.MAX) } }
        }

        /** Ligne la plus haute / la plus basse du disque pour une colonne. */
        fun haut(x: Int): Int = (0 until N).first { MatrixFrame.isOnMatrix(x, it) }
        fun bas(x: Int): Int = (0 until N).last { MatrixFrame.isOnMatrix(x, it) }
    }
}

/** Raccourci : liste d'images à durée fixe → modèle animé. */
internal fun anime(id: String, nom: String, images: List<Pair<Grille, Long>>): Modele = Modele(
    id = id,
    nom = nom,
    type = if (images.size > 1) Modele.Type.ANIME else Modele.Type.STATIQUE,
    images = images.map { (g, d) -> Modele.Image(g.frame(), d.coerceAtLeast(Modele.MIN_DUREE_MS)) },
)
