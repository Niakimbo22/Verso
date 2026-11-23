package fr.niakimbo.verso.matrix

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Luminosité de la matrice : courbe perceptive + luminosité générale.
 *
 * - [GAMMA] : l'œil voit en gamma, pas en linéaire. Sans courbe, les demi-teintes paraissent
 *   presque pleines (50 % de valeur ≈ 22 % de lumière avec 2,2).
 * - [GAIN_APP] : Nothing OS atténue d'environ ×4 les images du canal Toy (constaté par d'autres
 *   apps sur le (4a) Pro), mais pas celles du canal app. On compense côté app.
 */
object Luminosite {
    const val GLYPH_MAX = 4095
    const val GAMMA = 2.2
    const val GAIN_APP = 0.25f
    const val MIN = 5
    const val MAX = 100
    const val DEFAUT = 50

    private const val CLE = "luminosite_generale"

    private val _generale = MutableStateFlow(DEFAUT)

    /** Luminosité générale en %, de [MIN] à [MAX]. */
    val generale: StateFlow<Int> = _generale

    fun init(context: Context) {
        val v = context.getSharedPreferences("verso", Context.MODE_PRIVATE).getInt(CLE, DEFAUT)
        _generale.value = v.coerceIn(MIN, MAX)
    }

    fun regler(context: Context, pourcent: Int) {
        val v = pourcent.coerceIn(MIN, MAX)
        context.getSharedPreferences("verso", Context.MODE_PRIVATE).edit().putInt(CLE, v).apply()
        _generale.value = v
    }

    /** Image 0-255 → luminosités Glyph 0-4095 (pixel éteint = 0, sinon jamais moins de 1). */
    fun versGlyph(frame: MatrixFrame, canal: Canal, pourcent: Int = generale.value): IntArray {
        val gain = pourcent / 100.0 * (if (canal == Canal.APP) GAIN_APP.toDouble() else 1.0)
        return IntArray(MatrixFrame.SIZE) {
            val v = frame.pixels[it]
            if (v <= 0) 0
            else (GLYPH_MAX * (v / MatrixFrame.MAX.toDouble()).pow(GAMMA) * gain).roundToInt().coerceIn(1, GLYPH_MAX)
        }
    }
}
