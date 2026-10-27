package fr.niakimbo.verso.matrix

/**
 * Une image 13×13 pour la matrice du Phone (4a) Pro.
 *
 * [pixels] contient 169 luminosités 0-255, ligne par ligne (index = y * 13 + x),
 * comme le format des modèles de VERSO_SPEC §3.4.
 */
class MatrixFrame(pixels: IntArray) {
    init {
        require(pixels.size == SIZE) { "Une image doit contenir $SIZE pixels (reçu ${pixels.size})" }
    }

    val pixels: IntArray = IntArray(SIZE) { pixels[it].coerceIn(0, MAX) }

    operator fun get(x: Int, y: Int): Int = pixels[y * SIDE + x]

    /** Nombre de LEDs allumées (hors coins, qui n'existent pas physiquement). */
    val litCount: Int
        get() = (0 until SIZE).count { pixels[it] > 0 && isOnMatrix(it % SIDE, it / SIDE) }

    /** Même image, luminosité multipliée par [f] (0 = éteinte, 1 = inchangée) : pour les fondus. */
    fun attenue(f: Float): MatrixFrame {
        val k = f.coerceIn(0f, 1f)
        return MatrixFrame(IntArray(SIZE) { (pixels[it] * k).toInt() })
    }

    /** Conversion vers le format du SDK Glyph : luminosité 0-4095. */
    fun toGlyphColors(): IntArray = IntArray(SIZE) { pixels[it] * GLYPH_MAX / MAX }

    companion object {
        const val SIDE = 13
        const val SIZE = SIDE * SIDE
        const val MAX = 255
        const val GLYPH_MAX = 4095

        /**
         * La matrice est ronde : les coins de la grille 13×13 n'ont pas de LED.
         * Approximation par un disque de rayon 6,5 centré sur (6, 6).
         */
        fun isOnMatrix(x: Int, y: Int): Boolean {
            val dx = x - SIDE / 2
            val dy = y - SIDE / 2
            return dx * dx + dy * dy <= 42
        }

        fun blank(): MatrixFrame = MatrixFrame(IntArray(SIZE))

        /** Construit une image depuis 13 lignes de 13 caractères ('X' = allumé). */
        fun fromArt(vararg rows: String, brightness: Int = MAX): MatrixFrame {
            require(rows.size == SIDE) { "Il faut $SIDE lignes (reçu ${rows.size})" }
            val px = IntArray(SIZE)
            rows.forEachIndexed { y, row ->
                require(row.length == SIDE) { "Ligne $y : $SIDE caractères attendus (reçu ${row.length})" }
                row.forEachIndexed { x, c -> if (c == 'X') px[y * SIDE + x] = brightness }
            }
            return MatrixFrame(px)
        }
    }
}

/** Images de l'écran de test (étape 2). */
object TestPatterns {
    val centerPixel: MatrixFrame
        get() = MatrixFrame(IntArray(MatrixFrame.SIZE).also { it[6 * MatrixFrame.SIDE + 6] = MatrixFrame.MAX })

    val heart: MatrixFrame = MatrixFrame.fromArt(
        ".............",
        "..XXX...XXX..",
        ".XXXXX.XXXXX.",
        ".XXXXXXXXXXX.",
        ".XXXXXXXXXXX.",
        ".XXXXXXXXXXX.",
        "..XXXXXXXXX..",
        "...XXXXXXX...",
        "....XXXXX....",
        ".....XXX.....",
        "......X......",
        ".............",
        ".............",
    )

    val allOn: MatrixFrame
        get() = MatrixFrame(IntArray(MatrixFrame.SIZE) { MatrixFrame.MAX })
}
