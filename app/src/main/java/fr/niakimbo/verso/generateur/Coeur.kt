package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Formats
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import kotlin.math.roundToLong

/** Cœur qui bat : rythme, double battement, plein ou contour. */
object Coeur {
    private val BPM = Reglage.Curseur(
        cle = "v1.bpm", titre = "Rythme",
        description = "Battements par minute. 60 = calme, 120 = cœur qui s'emballe.",
        min = 40f, max = 160f, defaut = 70f, crans = 23, format = Formats.unite("bpm"),
    )
    private val DOUBLE = Reglage.Interrupteur(
        cle = "v1.double", titre = "Double battement",
        description = "« Boum-boum » comme un vrai cœur. Désactivé : un seul battement par cycle.",
        defaut = true,
    )
    private val STYLE = Reglage.Choix(
        cle = "v1.style", titre = "Style",
        description = "Cœur rempli, ou seulement son contour.",
        options = listOf("plein" to "Plein", "contour" to "Contour"),
        defaut = "plein",
    )
    private val REPOS = Reglage.Choix(
        cle = "v1.repos", titre = "Entre deux battements",
        description = "Ce qui reste affiché pendant la pause : le petit cœur, ou un cœur faiblement allumé.",
        options = listOf("petit" to "Petit cœur", "pale" to "Grand cœur pâle"),
        defaut = "petit",
    )

    private val petit = Grille.depuisArt(
        ".............",
        ".............",
        ".............",
        "...XX...XX...",
        "..XXXX.XXXX..",
        "..XXXXXXXXX..",
        "..XXXXXXXXX..",
        "...XXXXXXX...",
        "....XXXXX....",
        ".....XXX.....",
        "......X......",
        ".............",
        ".............",
    )
    private val grand = Grille.depuisArt(
        ".............",
        ".............",
        "..XXX...XXX..",
        ".XXXXX.XXXXX.",
        ".XXXXXXXXXXX.",
        ".XXXXXXXXXXX.",
        ".XXXXXXXXXXX.",
        "..XXXXXXXXX..",
        "...XXXXXXX...",
        "....XXXXX....",
        ".....XXX.....",
        "......X......",
        ".............",
    )

    private fun contour(g: Grille): Grille = Grille().also { c ->
        for (y in 0 until Grille.N) for (x in 0 until Grille.N) {
            if (g.get(x, y) == 0) continue
            val bord = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (a, b) -> g.get(a, b) == 0 }
            if (bord) c.allumer(x, y, g.get(x, y))
        }
    }

    private fun pale(g: Grille): Grille = Grille().also { p ->
        for (y in 0 until Grille.N) for (x in 0 until Grille.N) if (g.get(x, y) > 0) p.allumer(x, y, Grille.niveau(2))
    }

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Cœur qui bat, rythme et style réglables.",
        reglages = listOf(BPM, DOUBLE, STYLE, REPOS),
    ) { v ->
        val forme: (Grille) -> Grille = if (v.s(STYLE) == "contour") ::contour else { g -> g }
        val haut = forme(grand)
        val bas = if (v.s(REPOS) == "pale") forme(pale(grand)) else forme(petit)
        val cycle = 60_000.0 / v.f(BPM)
        fun ms(part: Double) = (cycle * part).roundToLong()
        val images = if (v.b(DOUBLE)) {
            listOf(haut to ms(0.14), bas to ms(0.12), haut to ms(0.14), bas to ms(0.60))
        } else {
            listOf(haut to ms(0.22), bas to ms(0.78))
        }
        anime("coeur", "Cœur", images)
    }

    val entree = EntreeCatalogue("coeur", "Cœur", listOf(v1))
}
