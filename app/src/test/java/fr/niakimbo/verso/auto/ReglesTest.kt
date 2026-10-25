package fr.niakimbo.verso.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Moteur des règles automatiques, sans téléphone : `./gradlew testReleaseUnitTest`. */
class ReglesTest {

    // Dimanche 25/10/2026.
    private fun t(h: Int, m: Int = 0, jour: Int = 25) = LocalDateTime.of(2026, 10, jour, h, m)

    private fun ctx(
        h: Int, m: Int = 0, jour: Int = 25,
        charge: Boolean = false, batterie: Int = 80,
        casques: List<String> = emptyList(), dnd: Boolean = false,
    ) = Contexte(t(h, m, jour), charge, batterie, casques, dnd)

    private val nuit = Regle("nuit", "Nuit", Declencheur.Plage(22 * 60, 7 * 60), "lune")
    private val charge = Regle("charge", "Charge", Declencheur.Charge, "chargement")

    @Test
    fun plageSimple() {
        val p = Declencheur.Plage(9 * 60, 17 * 60)
        assertTrue(Evaluation.dansPlage(p, t(9)))
        assertTrue(Evaluation.dansPlage(p, t(16, 59)))
        assertFalse(Evaluation.dansPlage(p, t(17)))
        assertFalse(Evaluation.dansPlage(p, t(8, 59)))
    }

    @Test
    fun plagePasseMinuit() {
        val p = nuit.quand as Declencheur.Plage
        assertTrue(Evaluation.dansPlage(p, t(23)))
        assertTrue(Evaluation.dansPlage(p, t(2)))
        assertTrue(Evaluation.dansPlage(p, t(6, 59)))
        assertFalse(Evaluation.dansPlage(p, t(7)))
        assertFalse(Evaluation.dansPlage(p, t(12)))
    }

    @Test
    fun plageJours() {
        // Vendredi et samedi soir seulement.
        val p = Declencheur.Plage(22 * 60, 2 * 60, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
        assertTrue(Evaluation.dansPlage(p, t(23, jour = 24))) // samedi 23h
        assertTrue(Evaluation.dansPlage(p, t(1, jour = 25))) // dimanche 1h = nuit de samedi
        assertFalse(Evaluation.dansPlage(p, t(23, jour = 25))) // dimanche 23h
        assertFalse(Evaluation.dansPlage(p, t(1, jour = 24 - 4))) // mardi 1h
    }

    @Test
    fun priorite_premiereDeLaListe() {
        val regles = listOf(charge, nuit)
        assertEquals("charge", Evaluation.gagnante(regles, ctx(23, charge = true))?.id)
        assertEquals("nuit", Evaluation.gagnante(regles, ctx(23))?.id)
        assertNull(Evaluation.gagnante(regles, ctx(12)))
        assertNull(Evaluation.gagnante(listOf(nuit.copy(active = false)), ctx(23)))
    }

    @Test
    fun batterieEtCasque() {
        val faible = Declencheur.BatterieFaible(15)
        assertTrue(Evaluation.vraie(faible, ctx(12, batterie = 15)))
        assertFalse(Evaluation.vraie(faible, ctx(12, batterie = 16)))
        assertFalse(Evaluation.vraie(faible, ctx(12, batterie = 5, charge = true)))
        assertTrue(Evaluation.vraie(Declencheur.Casque(), ctx(12, casques = listOf("Nothing Ear"))))
        assertFalse(Evaluation.vraie(Declencheur.Casque("AirPods"), ctx(12, casques = listOf("Nothing Ear"))))
        assertTrue(Evaluation.vraie(Declencheur.Casque("nothing ear"), ctx(12, casques = listOf("Nothing Ear"))))
    }

    @Test
    fun prochaineFrontiere() {
        val regles = listOf(nuit)
        assertEquals(t(22), Evaluation.prochaineFrontiere(regles, t(12, 30)))
        assertEquals(t(7, jour = 26), Evaluation.prochaineFrontiere(regles, t(22)))
        assertNull(Evaluation.prochaineFrontiere(listOf(charge), t(12)))
    }

    @Test
    fun regleQuiCommencePuisFinit_revientAuModeleDavant() {
        var m = Decision.Memoire()
        val debut = Decision.evaluer(m, nuit, affiche = "pluie")
        assertEquals("lune", debut.afficher?.modele)
        m = debut.memoire
        // Toujours la nuit : rien ne bouge.
        assertNull(Decision.evaluer(m, nuit, "lune").afficher)
        val fin = Decision.evaluer(m, null, "lune")
        assertEquals("pluie", fin.afficher?.modele)
        assertNull(fin.memoire.enCours)
    }

    @Test
    fun optionA_choixManuelTientJusquauProchainChangement() {
        var m = Decision.evaluer(Decision.Memoire(), nuit, "pluie").memoire
        // 23h : Nico choisit Coeur à la main.
        m = Decision.choixManuel(m, "coeur")
        // La règle de nuit continue : aucune image imposée.
        assertNull(Decision.evaluer(m, nuit, "coeur").afficher)
        // On branche le téléphone : nouvelle règle, elle s'applique.
        val r = Decision.evaluer(m, charge, "coeur")
        assertEquals("chargement", r.afficher?.modele)
        // On débranche à 8h (plus de nuit) : retour au choix manuel, pas à Pluie.
        val fin = Decision.evaluer(r.memoire, null, "chargement")
        assertEquals("coeur", fin.afficher?.modele)
    }

    @Test
    fun choixManuelPuisFinDeRegle_garderLeChoix() {
        var m = Decision.evaluer(Decision.Memoire(), nuit, "pluie").memoire
        m = Decision.choixManuel(m, "coeur")
        val fin = Decision.evaluer(m, null, "coeur")
        assertNull(fin.afficher) // déjà Coeur : on ne touche à rien
    }

    @Test
    fun sansRevenir_leModeleDeLaRegleReste() {
        val r = nuit.copy(revenir = false)
        val m = Decision.evaluer(Decision.Memoire(), r, "pluie").memoire
        val fin = Decision.evaluer(m, null, "lune")
        assertNull(fin.afficher)
        assertEquals("lune", fin.memoire.base)
    }

    @Test
    fun regleModifieePendantQuelleSApplique_reappliquee() {
        val m = Decision.evaluer(Decision.Memoire(), nuit, "pluie").memoire
        val r = Decision.evaluer(m, nuit.copy(modele = "etoile"), "lune")
        assertEquals("etoile", r.afficher?.modele)
        assertEquals("pluie", r.memoire.base)
    }

    @Test
    fun eteindreEstUnModele() {
        val dodo = nuit.copy(modele = null)
        val r = Decision.evaluer(Decision.Memoire(), dodo, "pluie")
        assertTrue(r.afficher != null)
        assertNull(r.afficher!!.modele)
        assertEquals("pluie", Decision.evaluer(r.memoire, null, null).afficher?.modele)
    }

    @Test
    fun reveil() {
        val r = Declencheur.Reveil()
        assertTrue(Evaluation.dansFenetreReveil(r, t(7, 30)))
        assertFalse(Evaluation.dansFenetreReveil(r, t(2)))
        assertFalse(Evaluation.dansFenetreReveil(r, t(13)))
        // Réveil pendant une règle : affiché, et devient le modèle à remettre ensuite.
        val m = Decision.evaluer(Decision.Memoire(), charge, "pluie").memoire
        val res = Decision.moment(m, "soleil", "chargement")
        assertEquals("soleil", res.afficher?.modele)
        assertEquals("soleil", Decision.evaluer(res.memoire, null, "soleil").memoire.base)
    }
}
