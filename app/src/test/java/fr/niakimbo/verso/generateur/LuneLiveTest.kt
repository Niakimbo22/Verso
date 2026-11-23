package fr.niakimbo.verso.generateur

import java.time.LocalDate
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
}
