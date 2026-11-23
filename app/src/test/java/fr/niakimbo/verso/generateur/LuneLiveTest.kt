package fr.niakimbo.verso.generateur

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class LuneLiveTest {
    private fun ecart(a: Double, b: Double) = min(abs(a - b), 1 - abs(a - b))

    @Test
    fun phasesConnues() {
        assertTrue(ecart(LuneLive.phase(LocalDate.of(2026, 1, 3)), 0.5) < 0.04) // pleine lune
        assertTrue(ecart(LuneLive.phase(LocalDate.of(2026, 1, 18)), 0.0) < 0.04) // nouvelle lune
    }

    @Test
    fun phasesNommees() {
        assertEquals(PhaseLune.NOUVELLE, phaseNommee(0.97))
        assertEquals(PhaseLune.NOUVELLE, phaseNommee(0.03))
        assertEquals(PhaseLune.PREMIER_CROISSANT, phaseNommee(0.1))
        assertEquals(PhaseLune.PREMIER_QUARTIER, phaseNommee(0.25))
        assertEquals(PhaseLune.GIBBEUSE_CROISSANTE, phaseNommee(0.4))
        assertEquals(PhaseLune.PLEINE, phaseNommee(0.5))
        assertEquals(PhaseLune.GIBBEUSE_DECROISSANTE, phaseNommee(0.6))
        assertEquals(PhaseLune.DERNIER_QUARTIER, phaseNommee(0.75))
        assertEquals(PhaseLune.DERNIER_CROISSANT, phaseNommee(0.9))
    }

    private fun fortes(p: PhaseLune): List<Int> {
        val f = LuneLive.icone(p)
        return (0 until 13 * 13).filter { f.pixels[it] >= 200 }
    }

    @Test
    fun iconePleine() {
        // Avec les mers sombres, la pleine lune n'est pas uniforme : on compte les LED bien allumées.
        val p = LuneLive.icone(PhaseLune.PLEINE).pixels
        assertTrue(p.count { it >= 90 } >= 80)
        assertTrue(fortes(PhaseLune.PLEINE).size >= 50)
    }

    @Test
    fun iconeNouvelleSansLedForte() = assertTrue(fortes(PhaseLune.NOUVELLE).isEmpty())

    @Test
    fun iconePremierQuartierADroite() {
        val l = fortes(PhaseLune.PREMIER_QUARTIER)
        assertTrue(l.isNotEmpty() && l.all { it % 13 >= 6 })
    }

    @Test
    fun iconeDernierQuartierAGauche() {
        val l = fortes(PhaseLune.DERNIER_QUARTIER)
        assertTrue(l.isNotEmpty() && l.all { it % 13 <= 6 })
    }
}
