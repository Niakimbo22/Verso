package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.Valeurs
import org.junit.Assert.assertTrue
import org.junit.Test

class PoulpeTest {
    private fun modele(vararg v: Pair<String, Any>) = Poulpe.entree.parDefaut.construire(Valeurs(v.toMap()))

    @Test
    fun chaqueSceneDessineQuelqueChose() {
        for (s in listOf("nage", "coucou", "regarde", "monte", "encre", "bulles")) for (m in 1..3) {
            val mod = modele("v1.scene" to s, "v1.mouvements" to m.toFloat())
            assertTrue("$s $m", mod.images.size >= 10)
            assertTrue("$s $m allumé", mod.images.any { it.frame.litCount > 0 })
        }
    }

    @Test
    fun vieComplete() {
        assertTrue(modele().images.sumOf { it.dureeMs } in 15_000L..35_000L)
    }
}
