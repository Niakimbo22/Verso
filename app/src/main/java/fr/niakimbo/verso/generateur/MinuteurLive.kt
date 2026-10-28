package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.rapide.Minuteur
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import java.time.LocalTime
import kotlin.math.abs

/**
 * Sablier au dos pour le minuteur (28/10, Nico : « une sorte de sablier, comme ce que Nothing propose »).
 *
 * Deux bulbes de 25 grains (lignes 1-5 et 7-11, larges de 9, 7, 5, 3, 1). Le sable du haut se creuse
 * par le milieu, celui du bas monte en tas ; un grain tombe par le col tant que ça tourne.
 * Le grain en cours d'écoulement a une luminosité partielle : le sable bouge en douceur, même sur 2 h.
 * En pause : le sable respire. Fini : le sablier clignote fort (pendant la sonnerie).
 */
object MinuteurLive {
    const val ID = Minuteur.ID

    private const val C = 6

    private val FILET = Reglage.Interrupteur(
        cle = "v1.filet", titre = "Filet de sable",
        description = "Un grain tombe par le col.",
        defaut = true,
    )
    private val VERRE = Reglage.Interrupteur(
        cle = "v1.verre", titre = "Verre visible",
        description = "Le contour du sablier reste allumé faiblement.",
        defaut = true,
    )
    private val CHIFFRES = Reglage.Interrupteur(
        cle = "v1.chiffres", titre = "Minutes restantes",
        description = "Toutes les 10 s, les minutes qui restent s'affichent 2 s.",
        defaut = false,
    )

    /** Cases du bulbe du haut, dans l'ordre où le sable les quitte (en haut d'abord, par le milieu). */
    private val HAUT: List<Pair<Int, Int>> = (1..5).flatMap { y ->
        val demi = 5 - y
        (C - demi..C + demi).map { it to y }.sortedBy { abs(it.first - C) }
    }

    /** Cases du bulbe du bas, dans l'ordre où le sable les remplit (en bas d'abord, en tas). */
    private val BAS: List<Pair<Int, Int>> = (11 downTo 7).flatMap { y ->
        val demi = y - 7
        (C - demi..C + demi).map { it to y }.sortedBy { abs(it.first - C) }
    }

    /** Montures du haut et du bas. */
    private val MONTURES = (4..8).flatMap { listOf(it to 0, it to 12) }

    private val SABLE = MatrixFrame.MAX

    fun frame(e: Minuteur.Etat, maintenant: Long, filet: Boolean = true, verre: Boolean = true, chiffres: Boolean = false): MatrixFrame {
        if (e.sonne) return frameSonne(maintenant)
        val reste = e.reste(maintenant)
        if (chiffres && (e.enMarche || e.enPause) && maintenant / 1000 % 10 >= 8) return minutes(reste)

        val g = Grille()
        val ecoule = e.ecoule(maintenant)
        val total = HAUT.size.toFloat()
        val tombes = ecoule * total
        val pleins = tombes.toInt()
        val partiel = tombes - pleins

        // En pause : le sable respire doucement.
        val lum = if (e.enPause && maintenant % 1600 > 900) Grille.niveau(4) else SABLE
        val vide = if (verre) Grille.niveau(1) else 0

        if (verre) MONTURES.forEach { (x, y) -> g.allumer(x, y, Grille.niveau(3)) }

        HAUT.forEachIndexed { i, (x, y) ->
            val v = when {
                i < pleins -> vide
                i == pleins -> maxOf(vide, (lum * (1 - partiel)).toInt())
                else -> lum
            }
            if (v > 0) g.allumer(x, y, v)
        }
        BAS.forEachIndexed { i, (x, y) ->
            val v = when {
                i < pleins -> lum
                i == pleins -> maxOf(vide, (lum * partiel).toInt())
                else -> vide
            }
            if (v > 0) g.allumer(x, y, v)
        }
        // Le col.
        g.allumer(C, 6, if (e.enMarche && filet) SABLE else if (verre) vide.coerceAtLeast(Grille.niveau(1)) else 0)

        // Le grain qui tombe : du col jusqu'au sommet du tas.
        if (e.enMarche && filet && pleins < BAS.size) {
            val sommet = BAS.take(pleins + 1).minOf { it.second } // ligne la plus haute du tas (ou fond)
            val trajet = (7 until sommet).toList()
            if (trajet.isNotEmpty()) {
                val y = trajet[((maintenant / 90) % trajet.size).toInt()]
                g.allumer(C, y, Grille.niveau(7))
            }
        }
        return g.frame()
    }

    /** Fin : le sablier se retourne en clignotant, plein feu. */
    fun frameSonne(maintenant: Long): MatrixFrame {
        val g = Grille()
        val phase = (maintenant / 300) % 2 == 0L
        val bulbe = if (phase) HAUT else BAS
        val autre = if (phase) BAS else HAUT
        bulbe.forEach { (x, y) -> g.allumer(x, y, MatrixFrame.MAX) }
        autre.forEach { (x, y) -> g.allumer(x, y, Grille.niveau(2)) }
        MONTURES.forEach { (x, y) -> g.allumer(x, y, MatrixFrame.MAX) }
        g.allumer(C, 6, MatrixFrame.MAX)
        return g.frame()
    }

    /** Les minutes restantes (arrondies au-dessus), en gros chiffres. */
    private fun minutes(resteMs: Long): MatrixFrame {
        val g = Grille()
        val m = ((resteMs + 59_999) / 60_000).coerceAtMost(99).toInt()
        if (m >= 10) {
            Live.chiffre(g, m / 10, 3, 4)
            Live.chiffre(g, m % 10, 7, 4)
        } else {
            Live.chiffre(g, m, 5, 4)
        }
        return g.frame()
    }

    val entree: EntreeCatalogue = run {
        val v1 = VersionModele(
            id = "v1", nom = "Sablier",
            description = "⏳ Règle la durée avec la roulette (bouton ci-dessous ou widget Minuteur).",
            reglages = listOf(FILET, VERRE, CHIFFRES),
        ) { v ->
            val filet = v.b(FILET)
            val verre = v.b(VERRE)
            val chiffres = v.b(CHIFFRES)
            val rendu: (LocalTime) -> MatrixFrame = { _ ->
                frame(Minuteur.etat.value, System.currentTimeMillis(), filet, verre, chiffres)
            }
            Modele(
                id = ID,
                nom = "Minuteur",
                type = Modele.Type.STATIQUE,
                images = listOf(Modele.Image(frame(Minuteur.Etat(true, 600_000, System.currentTimeMillis() + 380_000), System.currentTimeMillis(), filet, verre), 1000)),
                dynamique = Modele.Dynamique.HORLOGE,
                rendu = rendu,
                periodeMs = 90,
            )
        }
        EntreeCatalogue(ID, "Minuteur", listOf(v1))
    }
}
