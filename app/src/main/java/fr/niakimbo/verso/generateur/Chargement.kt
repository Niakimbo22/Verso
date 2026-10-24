package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Formats
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Point(s) qui tournent en rond, avec traînée. */
object Chargement {
    private val POINTS = Reglage.Curseur(
        cle = "v1.points", titre = "Nombre de points",
        description = "Combien de points tournent en même temps, répartis sur le cercle.",
        min = 1f, max = 3f, defaut = 1f, crans = 1, format = Formats.unite("point(s)"),
    )
    private val TRAINEE = Reglage.Curseur(
        cle = "v1.trainee", titre = "Longueur de la traînée",
        description = "Nombre de LEDs qui suivent chaque point en s'éteignant peu à peu.",
        min = 1f, max = 8f, defaut = 4f, crans = 6, format = Formats.unite("LEDs"),
    )
    private val RAYON = Reglage.Choix(
        cle = "v1.rayon", titre = "Taille du cercle",
        description = "Rayon du cercle sur lequel tournent les points.",
        options = listOf("grand" to "Grand", "moyen" to "Moyen", "petit" to "Petit"),
        defaut = "grand",
    )
    private val SENS = Reglage.Choix(
        cle = "v1.sens", titre = "Sens",
        description = "Sens de rotation.",
        options = listOf("horaire" to "Horaire", "anti" to "Antihoraire"),
        defaut = "horaire",
    )
    private val ANNEAU = Reglage.Interrupteur(
        cle = "v1.anneau", titre = "Anneau de fond",
        description = "Laisse le cercle faiblement allumé sous les points.",
        defaut = true,
    )

    private const val IMAGES = 16

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Indicateur de chargement, réglable.",
        reglages = listOf(POINTS, TRAINEE, RAYON, SENS, ANNEAU),
    ) { v ->
        val r = when (v.s(RAYON)) { "petit" -> 2.5; "moyen" -> 3.5; else -> 4.5 }
        val sens = if (v.s(SENS) == "anti") -1 else 1
        val points = v.i(POINTS)
        val trainee = v.i(TRAINEE)
        val images = (0 until IMAGES).map { f ->
            val g = Grille()
            if (v.b(ANNEAU)) {
                for (y in 0 until Grille.N) for (x in 0 until Grille.N) {
                    if (abs(hypot((x - Grille.C).toDouble(), (y - Grille.C).toDouble()) - r) < 0.5) g.allumer(x, y, Grille.niveau(1))
                }
            }
            repeat(points) { p ->
                for (k in trainee - 1 downTo 0) {
                    val niveau = 9f * (trainee - k) / trainee
                    val pos = f - k + p * IMAGES / points
                    val a = 2 * PI * pos / IMAGES * sens
                    val x = (Grille.C + r * sin(a)).roundToInt()
                    val y = (Grille.C - r * cos(a)).roundToInt()
                    g.allumer(x, y, Grille.niveau(niveau))
                }
            }
            g to 70L
        }
        anime("chargement", "Chargement", images)
    }

    val entree = EntreeCatalogue("chargement", "Chargement", listOf(v1))
}
