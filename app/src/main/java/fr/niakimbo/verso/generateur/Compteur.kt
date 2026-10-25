package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.VersionModele

/**
 * Modèle de test (Diagnostic) : 00 → 59, un nombre par seconde.
 * Sert à vérifier que l'animation du widget se met en pause quand il n'est pas visible :
 * au retour sur l'accueil, le nombre doit reprendre là où il était.
 */
object Compteur {
    const val ID = "compteur-test"
    private const val IMAGES = 60

    // Chiffres 3×5, ligne par ligne.
    private val CHIFFRES = listOf(
        "XXX X.X X.X X.X XXX", "..X ..X ..X ..X ..X", "XXX ..X XXX X.. XXX", "XXX ..X XXX ..X XXX",
        "X.X X.X XXX ..X ..X", "XXX X.. XXX ..X XXX", "XXX X.. XXX X.X XXX", "XXX ..X ..X ..X ..X",
        "XXX X.X XXX X.X XXX", "XXX X.X XXX ..X XXX",
    ).map { it.split(" ") }

    private fun dessiner(g: Grille, chiffre: Int, x0: Int, y0: Int) {
        CHIFFRES[chiffre].forEachIndexed { y, ligne ->
            ligne.forEachIndexed { x, c -> if (c == 'X') g.allumer(x0 + x, y0 + y, Grille.niveau(9)) }
        }
    }

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Compte de 00 à 59, une image par seconde.",
        reglages = emptyList(),
    ) {
        val images = (0 until IMAGES).map { n ->
            val g = Grille()
            dessiner(g, n / 10, 3, 4)
            dessiner(g, n % 10, 7, 4)
            g to 1000L
        }
        anime(ID, "Compteur test", images)
    }

    val entree = EntreeCatalogue(ID, "Compteur test", listOf(v1), cache = true)
}
