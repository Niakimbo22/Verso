package fr.niakimbo.verso.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuminositeTest {
    private fun image(v: Int) = MatrixFrame(IntArray(MatrixFrame.SIZE) { v })

    private fun un(v: Int, canal: Canal, pct: Int) = Luminosite.versGlyph(image(v), canal, pct)[0]

    @Test
    fun pixelEteintResteEteint() {
        assertEquals(0, un(0, Canal.TOY, 100))
        assertEquals(0, un(0, Canal.APP, 100))
    }

    @Test
    fun pleinPotToyEtApp() {
        assertEquals(4095, un(255, Canal.TOY, 100))
        assertEquals(1024, un(255, Canal.APP, 100), 1.0)
    }

    @Test
    fun demiTeinteSousLeQuart() {
        assertTrue(un(128, Canal.TOY, 100) < 4095 / 4)
    }

    @Test
    fun jamaisEteintParErreur() {
        assertEquals(1, un(1, Canal.APP, 5))
    }

    @Test
    fun monotoneCroissant() {
        var prec = 0
        for (v in 0..255) {
            val g = un(v, Canal.TOY, 50)
            assertTrue("v=$v", g >= prec)
            prec = g
        }
    }
}
