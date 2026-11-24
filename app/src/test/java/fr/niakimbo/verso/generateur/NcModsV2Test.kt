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
        assertEquals(88, m.images.size)
        assertTrue(m.images.sumOf { it.dureeMs } in 6000L..7000L)
    }

    @Test
    fun balayageRegulier() {
        // La 1re vague (qui dévoile le NC) a des durées régulières : 13 images identiques.
        val m = modele("v1.fondu" to 0f, "v1.pause" to 0f)
        assertEquals(1, m.images.take(13).map { it.dureeMs }.toSet().size)
        // Elle part de rien : la 1re image n'affiche que le début du N.
        assertTrue(m.images[0].frame.litCount < 12)
    }

    @Test
    fun fonduSansRetard() {
        val sans = modele("v1.fondu" to 0f, "v1.pause" to 0f)
        val avec = modele("v1.fondu" to 1f, "v1.pause" to 0f)
        // Même nombre d'images, même durée : le fondu ne retarde pas le balayage.
        assertEquals(sans.images.size, avec.images.size)
        assertEquals(sans.images.sumOf { it.dureeMs }, avec.images.sumOf { it.dureeMs })
        // La luminosité de départ est plus faible avec le fondu.
        assertTrue(avec.images[2].frame.pixels.max() < sans.images[2].frame.pixels.max())
    }

    @Test
    fun pause() {
        val m = modele("v1.pause" to 5f)
        assertEquals(5000L, m.images.last().dureeMs)
        assertEquals(0, m.images.last().frame.litCount)
    }

    @Test
    fun pauseExacteMemeAccelere() {
        val m = modele("v1.pause" to 4f, "commun.vitesse" to 2f)
        assertEquals(8000L, m.images.last().dureeMs) // divisé par 2 ensuite → 4 s
    }
}
