package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.Valeurs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NcModsV2Test {
    private val version = NcModsV2.entree.parDefaut
    private fun modele(vararg v: Pair<String, Any>) = version.construire(Valeurs(v.toMap()))

    @Test
    fun animationComplete() {
        val m = modele("v1.fondu" to 0f, "v1.pause" to 0f)
        assertEquals(89, m.images.size)
        assertTrue(m.images.sumOf { it.dureeMs } in 6000L..7000L)
    }

    @Test
    fun fonduEtPause() {
        val m = modele("v1.fondu" to 1f, "v1.pause" to 5f)
        // 1 s de fondu = 20 pas de 50 ms, qui montent en luminosité.
        assertEquals(89 + 20 + 1, m.images.size)
        val lum = m.images.take(20).map { it.frame.pixels.max() }
        assertTrue(lum.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(5000L, m.images.last().dureeMs)
        assertEquals(0, m.images.last().frame.litCount)
    }

    @Test
    fun pauseExacteMemeAccelere() {
        val m = modele("v1.pause" to 4f, "commun.vitesse" to 2f)
        assertEquals(8000L, m.images.last().dureeMs) // divisé par 2 ensuite → 4 s
    }
}
