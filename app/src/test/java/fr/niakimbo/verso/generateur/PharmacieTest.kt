package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.Valeurs
import org.junit.Assert.assertTrue
import org.junit.Test

class PharmacieTest {
    private fun modele(vararg v: Pair<String, Any>) = Pharmacie.entree.parDefaut.construire(Valeurs(v.toMap()))

    @Test
    fun chaqueNumeroDessineQuelqueChose() {
        for (n in listOf("clignote", "tourne", "rebond", "zoom", "explose", "vague", "contour", "balayage", "scintille", "secoue")) {
            for (f in 1..3) {
                val m = modele("v1.numero" to n, "v1.folie" to f.toFloat())
                assertTrue("$n folie $f", m.images.size >= 10)
                assertTrue("$n folie $f allumé", m.images.any { it.frame.litCount > 0 })
            }
        }
    }

    @Test
    fun showComplet() {
        val m = modele()
        assertTrue(m.images.sumOf { it.dureeMs } in 20_000L..45_000L)
    }
}
