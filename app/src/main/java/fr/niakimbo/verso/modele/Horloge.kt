package fr.niakimbo.verso.modele

import fr.niakimbo.verso.matrix.MatrixFrame
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Horloge à aiguilles dessinée à l'heure réelle sur la matrice ronde 13×13.
 *
 * Cadran discret (12, 3, 6 et 9 plus marqués), grande aiguille longue,
 * petite aiguille courte, centre allumé. Une image par minute suffit.
 */
object Horloge {
    private const val C = MatrixFrame.SIDE / 2
    private const val CADRAN = 70
    private const val REPERE = 150
    private const val AIGUILLE = MatrixFrame.MAX

    private val fond: IntArray by lazy {
        val px = IntArray(MatrixFrame.SIZE)
        for (y in 0 until MatrixFrame.SIDE) {
            for (x in 0 until MatrixFrame.SIDE) {
                val dx = x - C
                val dy = y - C
                val d2 = dx * dx + dy * dy
                // Anneau extérieur : dernière couronne de LEDs du disque.
                if (MatrixFrame.isOnMatrix(x, y) && d2 >= 30) px[y * MatrixFrame.SIDE + x] = CADRAN
            }
        }
        // 12, 3, 6, 9
        for ((x, y) in listOf(C to 0, 12 to C, C to 12, 0 to C)) px[y * MatrixFrame.SIDE + x] = REPERE
        px
    }

    fun frame(heure: LocalTime = LocalTime.now()): MatrixFrame {
        val px = fond.copyOf()
        val minutes = heure.minute + heure.second / 60.0
        val heures = (heure.hour % 12) + minutes / 60.0
        aiguille(px, angle = heures / 12.0, longueur = 3.2)
        aiguille(px, angle = minutes / 60.0, longueur = 5.2)
        px[C * MatrixFrame.SIDE + C] = AIGUILLE
        return MatrixFrame(px)
    }

    /** Délai jusqu'au début de la minute suivante (pour se caler sur l'heure pile). */
    fun msAvantProchaineMinute(heure: LocalTime = LocalTime.now()): Long {
        val ecoule = heure.second * 1000L + heure.nano / 1_000_000L
        return (60_000L - ecoule).coerceIn(250L, 60_000L)
    }

    /** [angle] en tours (0 = midi, 0,25 = 3 h). */
    private fun aiguille(px: IntArray, angle: Double, longueur: Double) {
        val rad = angle * 2 * Math.PI
        val ex = sin(rad) * longueur
        val ey = -cos(rad) * longueur
        val pas = max(4, (longueur * 3).roundToInt())
        for (i in 1..pas) {
            val t = i.toDouble() / pas
            val x = (C + ex * t).roundToInt()
            val y = (C + ey * t).roundToInt()
            if (x in 0 until MatrixFrame.SIDE && y in 0 until MatrixFrame.SIDE && MatrixFrame.isOnMatrix(x, y)) {
                px[y * MatrixFrame.SIDE + x] = AIGUILLE
            }
        }
    }
}
