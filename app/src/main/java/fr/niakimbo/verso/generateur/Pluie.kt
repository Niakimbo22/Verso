package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Formats
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.Valeurs
import fr.niakimbo.verso.reglage.VersionModele
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import java.util.Random

/**
 * Pluie, en deux versions :
 * - V1 : la première pluie (boucle courte, 11 gouttes fixes) ;
 * - V2 : averse naturelle sur un long cycle, entièrement réglable.
 */
object Pluie {

    // ---------- V1 ----------

    private val V1_TRAINEE = Reglage.Curseur(
        cle = "v1.trainee", titre = "Longueur des gouttes",
        description = "Nombre de LEDs allumées derrière chaque goutte. 1 = simples points, 5 = longs filets.",
        min = 1f, max = 5f, defaut = 3f, crans = 3, format = Formats.unite("LEDs"),
    )
    private val V1_GOUTTES = Reglage.Curseur(
        cle = "v1.gouttes", titre = "Nombre de gouttes",
        description = "Combien de gouttes tombent en même temps dans la boucle.",
        min = 3f, max = 11f, defaut = 11f, crans = 7, format = Formats.unite("gouttes"),
    )

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "La première pluie : boucle courte (1,4 s) avec des gouttes toujours aux mêmes endroits.",
        reglages = listOf(V1_GOUTTES, V1_TRAINEE),
    ) { v ->
        // (colonne, décalage, vitesse) : la pluie d'origine.
        val drops = listOf(
            Triple(1, 0, 1), Triple(3, 5, 1), Triple(5, 9, 2), Triple(6, 2, 1), Triple(8, 7, 2), Triple(10, 4, 1),
            Triple(11, 11, 1), Triple(4, 12, 2), Triple(9, 1, 1), Triple(2, 15, 1), Triple(7, 13, 1),
        ).take(v.i(V1_GOUTTES))
        val trainee = traineeDegrade(v.i(V1_TRAINEE))
        val l = 18
        val images = (0 until l).map { f ->
            val g = Grille()
            for ((x, off, vit) in drops) {
                val tete = (off + f * vit) % l - 3
                trainee.forEachIndexed { k, n -> g.allumer(x, tete - k, n) }
            }
            g to 80L
        }
        anime("pluie", "Pluie", images)
    }

    // ---------- V2 ----------

    private val DENSITE = Reglage.Curseur(
        cle = "v2.densite", titre = "Densité",
        description = "Quantité de gouttes. 50 % = petite pluie clairsemée, 100 % = normal, 250 % = déluge.",
        min = 20f, max = 250f, defaut = 100f, crans = 22, format = Formats.pourcent,
    )
    private val AVERSES = Reglage.Interrupteur(
        cle = "v2.averses", titre = "Averses",
        description = "La pluie se renforce puis se calme au fil du cycle (~18 s). Désactivé : intensité constante.",
        defaut = true,
    )
    private val VENT = Reglage.Curseur(
        cle = "v2.vent", titre = "Vent",
        description = "Incline la pluie. À gauche = les gouttes partent vers la gauche, à droite = vers la droite.",
        min = -2f, max = 2f, defaut = 0f, crans = 3,
        format = { when (it.roundToInt()) { -2 -> "← fort"; -1 -> "← léger"; 1 -> "léger →"; 2 -> "fort →"; else -> "Aucun" } },
    )
    private val TRAINEE = Reglage.Choix(
        cle = "v2.trainee", titre = "Longueur des gouttes",
        description = "Taille du filet lumineux derrière chaque goutte.",
        options = listOf("courte" to "Courte", "normale" to "Normale", "longue" to "Longue"),
        defaut = "normale",
    )
    private val BRUINE = Reglage.Curseur(
        cle = "v2.bruine", titre = "Part de bruine",
        description = "Proportion de petites gouttes lentes et pâles.",
        min = 0f, max = 80f, defaut = 25f, crans = 15, format = Formats.pourcent,
    )
    private val GROSSES = Reglage.Curseur(
        cle = "v2.grosses", titre = "Part de grosses gouttes",
        description = "Proportion de gouttes rapides et très brillantes. Le reste est de la pluie normale.",
        min = 0f, max = 80f, defaut = 15f, crans = 15, format = Formats.pourcent,
    )
    private val ECLABOUSSURES = Reglage.Interrupteur(
        cle = "v2.eclaboussures", titre = "Éclaboussures",
        description = "Petites gouttes qui rebondissent quand la pluie touche le bas du cercle.",
        defaut = true,
    )
    private val ORAGE = Reglage.Interrupteur(
        cle = "v2.orage", titre = "Orage",
        description = "Ajoute des éclairs : un flash de toute la matrice puis un zigzag lumineux.",
        defaut = false,
    )
    private val ECLAIRS = Reglage.Curseur(
        cle = "v2.eclairs", titre = "Nombre d'éclairs",
        description = "Éclairs par cycle de ~18 s (seulement si Orage est activé).",
        min = 1f, max = 6f, defaut = 2f, crans = 4, format = Formats.unite("par cycle"),
    )
    private val VARIANTE = Reglage.Curseur(
        cle = "v2.variante", titre = "Variante",
        description = "Change le tirage au hasard : chaque numéro donne une pluie différente, avec les mêmes réglages.",
        min = 1f, max = 20f, defaut = 1f, crans = 18, format = { "n°${it.roundToInt()}" },
    )

    private const val L = 260
    private const val DUREE = 70L

    private enum class Type(val vitesse: Float, val trainee: IntArray) {
        BRUINE(0.5f, intArrayOf(5, 2)),
        PLUIE(1f, intArrayOf(9, 5, 2)),
        GROSSE(1.5f, intArrayOf(9, 7, 4, 2)),
    }

    private class Goutte(val depart: Int, val x0: Int, val type: Type)

    private val v2 = VersionModele(
        id = "v2", nom = "V2",
        description = "Averse naturelle : gouttes au hasard, 3 tailles, intensité qui varie, cycle de ~18 s.",
        reglages = listOf(DENSITE, AVERSES, VENT, TRAINEE, BRUINE, GROSSES, ECLABOUSSURES, ORAGE, ECLAIRS, VARIANTE),
    ) { v ->
        val rng = Random(22L + v.i(VARIANTE) * 7919L)
        val densite = v.f(DENSITE) / 100f
        val pente = v.f(VENT) * 0.25f // pixels de côté par pixel de chute
        val pBruine = v.f(BRUINE) / 100f
        val pGrosse = (v.f(GROSSES) / 100f).coerceAtMost(1f - pBruine)
        val averses = v.b(AVERSES)
        val longueur = v.s(TRAINEE)

        fun intensite(f: Int) =
            if (averses) 0.25f + 0.75f * (0.5f - 0.5f * cos(2 * PI * f / L).toFloat()) else 0.65f

        // Avec du vent, les gouttes peuvent naître hors du cercle et y entrer en biais.
        val decalage = (abs(pente) * Grille.N).roundToInt()
        val xMin = if (pente > 0) 1 - decalage else 1
        val xMax = if (pente < 0) Grille.N - 2 + decalage else Grille.N - 2

        val gouttes = mutableListOf<Goutte>()
        for (f in 0 until L) {
            var n = intensite(f) * 0.9f * densite
            while (n > 0f) {
                if (rng.nextFloat() < minOf(n, 1f)) {
                    val r = rng.nextFloat()
                    val type = when {
                        r < pBruine -> Type.BRUINE
                        r < pBruine + pGrosse -> Type.GROSSE
                        else -> Type.PLUIE
                    }
                    gouttes += Goutte(f, xMin + rng.nextInt(xMax - xMin + 1), type)
                }
                n -= 1f
            }
        }

        fun traineeDe(type: Type): IntArray = when (longueur) {
            "courte" -> type.trainee.copyOf(maxOf(1, type.trainee.size - 2))
            "longue" -> type.trainee + intArrayOf(1, 1)
            else -> type.trainee
        }

        // Position de la tête d'une goutte après t images.
        fun tete(g: Goutte, t: Int): Pair<Int, Int> {
            val chute = floor(t * g.type.vitesse).toInt()
            return (g.x0 + (chute * pente).roundToInt()) to (chute - 1)
        }

        // Image d'impact (tête sous le bas du cercle) de chaque goutte, ou -1.
        fun impact(g: Goutte): Int {
            for (t in 0 until 60) {
                val (x, y) = tete(g, t)
                if (x !in 0 until Grille.N) { if (y > Grille.N) return -1 else continue }
                if (y > Grille.bas(x)) return t
            }
            return -1
        }
        val impacts = gouttes.map(::impact)

        val eclairs = if (v.b(ORAGE)) {
            val n = v.i(ECLAIRS)
            (0 until n).map { k -> (k * L / n + rng.nextInt(maxOf(1, L / n - 6))) to zigzag(rng) }
        } else emptyList()

        val images = (0 until L).map { f ->
            val g = Grille()
            gouttes.forEachIndexed { i, goutte ->
                val t = ((f - goutte.depart) % L + L) % L
                val fin = impacts[i]
                if (fin < 0 || t < fin) {
                    val (_, y) = tete(goutte, t)
                    traineeDe(goutte.type).forEachIndexed { k, n ->
                        val yy = y - k
                        val chute = yy + 1
                        val xx = goutte.x0 + (chute * pente).roundToInt()
                        if (yy >= 0) g.allumer(xx, yy, Grille.niveau(n))
                    }
                } else if (v.b(ECLABOUSSURES) && goutte.type != Type.BRUINE && t - fin <= 1) {
                    val (x, _) = tete(goutte, fin)
                    val haut = t == fin
                    for (dx in intArrayOf(-1, 1)) {
                        val xx = x + dx
                        if (xx !in 0 until Grille.N) continue
                        g.allumer(xx, Grille.bas(xx) - if (haut) 1 else 0, Grille.niveau(if (haut) 4 else 2))
                    }
                }
            }
            for ((debut, trace) in eclairs) {
                when (((f - debut) % L + L) % L) {
                    0 -> { remplir(g, Grille.niveau(6)); trace.forEach { (x, y) -> g.allumer(x, y, 255) } }
                    1 -> trace.forEach { (x, y) -> g.allumer(x, y, 255) }
                    2 -> { remplir(g, Grille.niveau(2)); trace.forEach { (x, y) -> g.allumer(x, y, Grille.niveau(5)) } }
                    3 -> trace.forEach { (x, y) -> g.allumer(x, y, Grille.niveau(2)) }
                }
            }
            g to DUREE
        }
        anime("pluie", "Pluie", images)
    }

    val entree = EntreeCatalogue("pluie", "Pluie", listOf(v1, v2))

    private fun remplir(g: Grille, v: Int) {
        for (y in 0 until Grille.N) for (x in 0 until Grille.N) g.allumer(x, y, v)
    }

    /** Éclair : zigzag du haut vers le bas du cercle. */
    private fun zigzag(rng: Random): List<Pair<Int, Int>> {
        var x = 4 + rng.nextInt(5)
        val fin = 8 + rng.nextInt(3)
        return (0..fin).map { y ->
            if (y > 0) x = (x + rng.nextInt(3) - 1).coerceIn(2, 10)
            x to y
        }
    }

    private fun traineeDegrade(longueur: Int): List<Int> =
        listOf(9, 5, 2, 1, 1).take(longueur).map { Grille.niveau(it) }
}
