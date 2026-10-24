package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Formats
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Horloge à l'heure réelle : à aiguilles ou numérique. */
object HorlogeReglable {
    private val STYLE = Reglage.Choix(
        cle = "v1.style", titre = "Style",
        description = "Horloge à aiguilles, ou heure en chiffres (heures en haut, minutes en bas).",
        options = listOf("aiguilles" to "Aiguilles", "numerique" to "Chiffres"),
        defaut = "aiguilles",
    )
    private val CADRAN = Reglage.Choix(
        cle = "v1.cadran", titre = "Cadran (aiguilles)",
        description = "Contour de l'horloge à aiguilles : cercle complet, seulement 12-3-6-9, ou rien.",
        options = listOf("complet" to "Complet", "reperes" to "Repères", "aucun" to "Aucun"),
        defaut = "complet",
    )
    private val LUM_CADRAN = Reglage.Curseur(
        cle = "v1.lum_cadran", titre = "Luminosité du cadran",
        description = "Intensité du contour et des repères, par rapport aux aiguilles (toujours à fond).",
        min = 10f, max = 100f, defaut = 30f, crans = 8, format = Formats.pourcent,
    )
    private val FORMAT = Reglage.Choix(
        cle = "v1.format", titre = "Format (chiffres)",
        description = "Pour le style Chiffres : 24 h (14:05) ou 12 h (02:05).",
        options = listOf("24" to "24 h", "12" to "12 h"),
        defaut = "24",
    )

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Heure réelle, mise à jour chaque minute.",
        reglages = listOf(STYLE, CADRAN, LUM_CADRAN, FORMAT),
    ) { v ->
        val rendu: (LocalTime) -> MatrixFrame = if (v.s(STYLE) == "numerique") {
            { h -> numerique(h, v.s(FORMAT) == "12") }
        } else {
            val lum = (MatrixFrame.MAX * v.f(LUM_CADRAN) / 100f).roundToInt()
            val cadran = v.s(CADRAN)
            ({ h -> aiguilles(h, cadran, lum) })
        }
        Modele(
            id = "horloge",
            nom = "Horloge",
            type = Modele.Type.STATIQUE,
            images = listOf(Modele.Image(rendu(LocalTime.of(10, 10)), 1000)),
            dynamique = Modele.Dynamique.HORLOGE,
            rendu = rendu,
        )
    }

    val entree = EntreeCatalogue("horloge", "Horloge", listOf(v1))

    private fun aiguilles(h: LocalTime, cadran: String, lum: Int): MatrixFrame {
        val g = Grille()
        val c = Grille.C
        val reperes = listOf(c to 0, 12 to c, c to 12, 0 to c)
        if (cadran == "complet") {
            for (y in 0 until Grille.N) for (x in 0 until Grille.N) {
                val d2 = (x - c) * (x - c) + (y - c) * (y - c)
                if (MatrixFrame.isOnMatrix(x, y) && d2 >= 30) g.allumer(x, y, (lum * 0.5f).roundToInt().coerceAtLeast(1))
            }
        }
        if (cadran != "aucun") reperes.forEach { (x, y) -> g.allumer(x, y, lum) }
        val minutes = h.minute + h.second / 60.0
        val heures = (h.hour % 12) + minutes / 60.0
        aiguille(g, heures / 12.0, 3.2)
        aiguille(g, minutes / 60.0, 5.2)
        g.allumer(c, c, MatrixFrame.MAX)
        return g.frame()
    }

    private fun aiguille(g: Grille, tours: Double, longueur: Double) {
        val a = tours * 2 * PI
        val pas = max(4, (longueur * 3).roundToInt())
        for (i in 1..pas) {
            val t = i.toDouble() / pas
            g.allumer(
                (Grille.C + sin(a) * longueur * t).roundToInt(),
                (Grille.C - cos(a) * longueur * t).roundToInt(),
                MatrixFrame.MAX,
            )
        }
    }

    /** Chiffres 3×5 : heures sur les lignes 1-5, minutes sur les lignes 7-11. */
    private fun numerique(h: LocalTime, douze: Boolean): MatrixFrame {
        val g = Grille()
        val heure = if (douze) (h.hour % 12).let { if (it == 0) 12 else it } else h.hour
        fun nombre(n: Int, y0: Int) {
            chiffre(g, n / 10, 3, y0)
            chiffre(g, n % 10, 7, y0)
        }
        nombre(heure, 1)
        nombre(h.minute, 7)
        return g.frame()
    }

    private val POLICE = arrayOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
    )

    private fun chiffre(g: Grille, d: Int, x0: Int, y0: Int) {
        val motif = POLICE[d]
        for (i in motif.indices) if (motif[i] == '1') g.allumer(x0 + i % 3, y0 + i / 3, MatrixFrame.MAX)
    }
}
