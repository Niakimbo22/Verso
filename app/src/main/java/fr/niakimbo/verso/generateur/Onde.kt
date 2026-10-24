package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Formats
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Onde sinusoïdale qui défile. */
object Onde {
    private val AMPLITUDE = Reglage.Curseur(
        cle = "v1.amplitude", titre = "Hauteur des vagues",
        description = "Écart entre le haut et le bas de l'onde. 1 = presque plat, 5 = vagues qui touchent les bords.",
        min = 1f, max = 5f, defaut = 3f, crans = 7, format = Formats.decimal("LEDs"),
    )
    private val VAGUES = Reglage.Curseur(
        cle = "v1.vagues", titre = "Nombre de vagues",
        description = "Combien de bosses on voit en même temps sur la largeur de la matrice.",
        min = 0.5f, max = 3f, defaut = 1f, crans = 4, format = Formats.decimal(""),
    )
    private val EPAISSEUR = Reglage.Choix(
        cle = "v1.epaisseur", titre = "Épaisseur",
        description = "Finesse du trait de l'onde.",
        options = listOf("fine" to "Fine", "normale" to "Normale", "epaisse" to "Épaisse"),
        defaut = "normale",
    )
    private val SENS = Reglage.Choix(
        cle = "v1.sens", titre = "Sens",
        description = "Direction dans laquelle l'onde avance.",
        options = listOf("droite" to "Vers la droite", "gauche" to "Vers la gauche"),
        defaut = "gauche",
    )
    private val DOUBLE = Reglage.Interrupteur(
        cle = "v1.double", titre = "Deuxième onde",
        description = "Ajoute une seconde onde plus pâle, décalée, qui se croise avec la première.",
        defaut = false,
    )

    private const val IMAGES = 16

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Onde qui défile, forme réglable.",
        reglages = listOf(AMPLITUDE, VAGUES, EPAISSEUR, SENS, DOUBLE),
    ) { v ->
        val amp = v.f(AMPLITUDE)
        val vagues = v.f(VAGUES)
        val (coeur, halo) = when (v.s(EPAISSEUR)) {
            "fine" -> 0.5f to 0.9f
            "epaisse" -> 1.0f to 1.9f
            else -> 0.6f to 1.3f
        }
        val sens = if (v.s(SENS) == "droite") -1f else 1f
        val images = (0 until IMAGES).map { f ->
            val g = Grille()
            fun trace(phase: Float, fort: Int, faible: Int) {
                for (x in 0 until Grille.N) {
                    val yf = Grille.C + amp * sin(2 * PI * (vagues * x / Grille.N + sens * f / IMAGES + phase)).toFloat()
                    for (y in 0 until Grille.N) {
                        val d = abs(y - yf)
                        when {
                            d < coeur -> g.allumer(x, y, fort)
                            d < halo -> g.allumer(x, y, faible)
                        }
                    }
                }
            }
            if (v.b(DOUBLE)) trace(0.5f, Grille.niveau(4), Grille.niveau(1))
            trace(0f, Grille.niveau(9), Grille.niveau(4))
            g to 90L
        }
        anime("onde", "Onde", images)
    }

    val entree = EntreeCatalogue("onde", "Onde", listOf(v1))
}
