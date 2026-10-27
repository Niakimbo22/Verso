package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.auto.EtatMeteo
import fr.niakimbo.verso.auto.Meteo
import fr.niakimbo.verso.auto.Musique
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Modèles « live » (étape 9, partie 3) : dessinés en direct à partir de données réelles.
 * Rayon « Live » de la bibliothèque ; utilisables partout (tap, favoris, widget, règles).
 */
object Live {
    const val RAYON = "Live"

    /** Images d'une animation à l'instant [ms] (boucle sur la somme des durées). */
    internal fun imageA(m: Modele, ms: Long): MatrixFrame {
        if (m.images.size == 1) return m.apercu
        val total = m.images.sumOf { it.dureeMs }.coerceAtLeast(1)
        var t = ms % total
        for (i in m.images) {
            if (t < i.dureeMs) return i.frame
            t -= i.dureeMs
        }
        return m.images.last().frame
    }

    // Chiffres 3×5 (et le signe moins), ligne par ligne.
    private val CHIFFRES = listOf(
        "XXX X.X X.X X.X XXX", ".X. XX. .X. .X. XXX", "XXX ..X XXX X.. XXX", "XXX ..X XXX ..X XXX",
        "X.X X.X XXX ..X ..X", "XXX X.. XXX ..X XXX", "XXX X.. XXX X.X XXX", "XXX ..X .X. .X. .X.",
        "XXX X.X XXX X.X XXX", "XXX X.X XXX ..X XXX",
    ).map { it.split(" ") }

    internal fun chiffre(g: Grille, d: Int, x0: Int, y0: Int, v: Int = MatrixFrame.MAX) {
        CHIFFRES[d.coerceIn(0, 9)].forEachIndexed { y, l -> l.forEachIndexed { x, c -> if (c == 'X') g.allumer(x0 + x, y0 + y, v) } }
    }

    /** « 12° », « 5° », « -5° », « -12 » centrés sur la matrice. */
    internal fun temperature(t: Int, v: Int = MatrixFrame.MAX): Grille {
        val g = Grille()
        val y0 = 4
        fun degre(x: Int) {
            g.allumer(x, y0, v); g.allumer(x + 1, y0, v); g.allumer(x, y0 + 1, v); g.allumer(x + 1, y0 + 1, v)
        }
        fun moins(x0: Int, l: Int) = (0 until l).forEach { g.allumer(x0 + it, y0 + 2, v) }
        val n = abs(t)
        when {
            t <= -10 -> { moins(0, 2); chiffre(g, n / 10 % 10, 3, y0, v); chiffre(g, n % 10, 7, y0, v) }
            t < 0 -> { moins(1, 3); chiffre(g, n, 5, y0, v); degre(9) }
            n >= 10 -> { chiffre(g, n / 10 % 10, 1, y0, v); chiffre(g, n % 10, 5, y0, v); degre(9) }
            else -> { chiffre(g, n, 3, y0, v); degre(7) }
        }
        return g
    }

    /** « -- » discret : pas encore de mesure. */
    internal fun inconnu(): Grille = Grille().apply { for (x in listOf(3, 4, 5, 7, 8, 9)) allumer(x, 6, Grille.niveau(4)) }
}

/** Météo réelle : l'icône animée du ciel (soleil, lune, nuages, pluie, neige, orage) et la température. */
object MeteoLive {
    const val ID = "meteo-live"

    private val AFFICHER = Reglage.Choix(
        cle = "v1.afficher", titre = "Afficher",
        description = "L'icône du ciel, la température, ou les deux à tour de rôle (5 s puis 3 s).",
        options = listOf("alterne" to "Les deux", "icone" to "Icône", "temperature" to "Température"),
        defaut = "alterne",
    )

    /** Modèle d'icône pour cette mesure (id d'un modèle de la bibliothèque). */
    fun icone(e: EtatMeteo): String = when {
        e.ciel == Meteo.SOLEIL -> if (e.jour) "soleil" else "lune"
        e.code == 2 -> if (e.jour) "eclaircie" else "nuage"
        e.ciel == Meteo.PLUIE -> "pluie"
        e.ciel == Meteo.NEIGE -> "neige-nuage"
        e.ciel == Meteo.ORAGE -> "orage"
        else -> "nuage"
    }

    /** [icones] : modèle d'une icône par id (bibliothèque intégrée). */
    fun entree(icones: (String) -> Modele?): EntreeCatalogue {
        val v1 = VersionModele(
            id = "v1", nom = "V1",
            description = "Le temps qu'il fait vraiment (Open-Meteo), mis à jour toutes les 30 min.",
            reglages = listOf(AFFICHER),
        ) { v ->
            val mode = v.s(AFFICHER)
            val rendu: (LocalTime) -> MatrixFrame = { _ ->
                Meteo.rafraichirSiVieille()
                val ms = System.currentTimeMillis()
                when (val e = Meteo.etat.value) {
                    null -> Live.inconnu().frame()
                    else -> {
                        val temp = mode == "temperature" || (mode == "alterne" && ms % 8_000 >= 5_000)
                        if (temp) Live.temperature(e.temperature).frame()
                        else icones(icone(e))?.let { Live.imageA(it, ms) } ?: Live.temperature(e.temperature).frame()
                    }
                }
            }
            Modele(
                id = ID,
                nom = "Météo réelle",
                type = Modele.Type.STATIQUE,
                images = listOf(Modele.Image(icones("eclaircie")?.apercu ?: Live.temperature(12).frame(), 1000)),
                dynamique = Modele.Dynamique.HORLOGE,
                rendu = rendu,
                periodeMs = 90,
            )
        }
        return EntreeCatalogue(ID, "Météo réelle", listOf(v1))
    }
}

/** Musique en direct : égaliseur qui danse pendant la lecture, pause, et l'anneau d'avancement du titre. */
object MusiqueLive {
    const val ID = "musique-live"

    private val ANNEAU = Reglage.Interrupteur(
        cle = "v1.anneau", titre = "Anneau d'avancement",
        description = "Le tour de la matrice se remplit au fil du titre.",
        defaut = true,
    )

    /** LEDs du bord de la matrice, dans le sens des aiguilles d'une montre depuis midi. */
    private val BORD: List<Pair<Int, Int>> = run {
        val n = MatrixFrame.SIDE
        val c = n / 2
        val bord = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until n) for (x in 0 until n) {
            if (!MatrixFrame.isOnMatrix(x, y)) continue
            val voisins = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
            if (voisins.any { (a, b) -> a !in 0 until n || b !in 0 until n || !MatrixFrame.isOnMatrix(a, b) }) bord += x to y
        }
        bord.sortedBy { (x, y) ->
            val a = atan2((x - c).toDouble(), (c - y).toDouble())
            if (a < 0) a + 2 * PI else a
        }
    }

    fun entree(icones: (String) -> Modele?): EntreeCatalogue {
        val v1 = VersionModele(
            id = "v1", nom = "V1",
            description = "Suit la musique du téléphone (Spotify, YouTube Music…). Demande l'accès aux notifications.",
            reglages = listOf(ANNEAU),
        ) { v ->
            val anneau = v.b(ANNEAU)
            val note = icones("musique")?.apercu
            val rendu: (LocalTime) -> MatrixFrame = { _ ->
                val e = Musique.etat.value
                val g = Grille()
                if (e == null) {
                    note?.let { f -> for (y in 0 until Grille.N) for (x in 0 until Grille.N) g.allumer(x, y, f[x, y] / 2) }
                } else {
                    if (e.enLecture) egaliseur(g, System.currentTimeMillis()) else pause(g)
                    if (anneau) e.avancement()?.let { p -> progression(g, p) }
                }
                g.frame()
            }
            Modele(
                id = ID,
                nom = "Musique en direct",
                type = Modele.Type.STATIQUE,
                images = listOf(Modele.Image(Grille().also { egaliseur(it, 1_234) ; progression(it, 0.4f) }.frame(), 1000)),
                dynamique = Modele.Dynamique.HORLOGE,
                rendu = rendu,
                periodeMs = 100,
            )
        }
        return EntreeCatalogue(ID, "Musique en direct", listOf(v1))
    }

    /** Cinq barres qui montent et descendent doucement, chacune à son rythme. */
    private fun egaliseur(g: Grille, ms: Long) {
        val t = ms / 1000.0
        val vitesses = doubleArrayOf(5.1, 7.3, 6.2, 8.4, 5.7)
        for (i in 0 until 5) {
            val a = 0.5 + 0.5 * sin(t * vitesses[i] + i * 1.7)
            val b = 0.65 + 0.35 * sin(t * vitesses[(i + 2) % 5] * 0.37 + i)
            val h = (1 + 6 * a * b).roundToInt().coerceIn(1, 7)
            val x = 2 + i * 2
            for (k in 0 until h) g.allumer(x, 9 - k, if (k == h - 1) MatrixFrame.MAX else Grille.niveau(6))
        }
    }

    private fun pause(g: Grille) {
        for (y in 4..8) for (x in listOf(4, 5, 7, 8)) g.allumer(x, y, Grille.niveau(7))
    }

    private fun progression(g: Grille, p: Float) {
        val n = (p * BORD.size).roundToInt()
        BORD.forEachIndexed { i, (x, y) -> g.allumer(x, y, if (i < n) MatrixFrame.MAX else Grille.niveau(1)) }
    }
}
