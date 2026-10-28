package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.auto.EtatMeteo
import fr.niakimbo.verso.auto.Meteo
import fr.niakimbo.verso.auto.Musique
import fr.niakimbo.verso.auto.Spectre
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
            description = "Suit la musique du téléphone (Spotify, YouTube Music…). Accès aux notifications pour le titre ; autorisation Micro pour que l'égaliseur suive le vrai son.",
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
                    if (!e.enLecture) pause(g)
                    // Le vrai son quand Android le donne (autorisation Micro), sinon l'animation.
                    else Spectre.niveaux()?.let { barres(g, Spectre.regrouper(it, 5)) } ?: egaliseur(g, System.currentTimeMillis())
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

    /** Cinq barres réglées par le vrai son : graves à gauche, aigus à droite (0..1 chacune). */
    private fun barres(g: Grille, n: FloatArray) {
        for (i in 0 until 5) {
            val h = (1 + 6 * n[i]).roundToInt().coerceIn(1, 7)
            val x = 2 + i * 2
            for (k in 0 until h) g.allumer(x, 9 - k, if (k == h - 1) MatrixFrame.MAX else Grille.niveau(6))
        }
    }

    /** Cinq barres qui montent et descendent doucement, chacune à son rythme (sans le vrai son). */
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

/**
 * Égaliseur (28/10, Nico : « qu'il bouge vraiment selon la musique », puis « il ressemble à rien ») :
 * même id que l'ancien modèle animé, qui reste en V1. La V2 suit le vrai son ([Spectre]) avec le
 * dessin classique de la V1 : barres épaisses espacées, base douce, sommet brillant et un point de
 * crête qui retombe doucement.
 * Pas de mesure possible (autorisation Micro, Android qui refuse) : l'animation V1 à la place.
 */
object EgaliseurLive {
    const val ID = "egaliseur"

    private val STYLE = Reglage.Choix(
        cle = "v2.forme", titre = "Style",
        description = "4 grosses barres (comme l'original), 6 barres fines, ou 6 barres qui s'ouvrent depuis le centre.",
        options = listOf("barres" to "Grosses", "fines" to "Fines", "centre" to "Centre"),
        defaut = "barres",
    )

    /** Crêtes : hauteur et heure du dernier maximum, par barre (un seul égaliseur affiché à la fois). */
    private val cretes = FloatArray(6)
    private val cretesA = LongArray(6)

    fun entree(icones: (String) -> Modele?): EntreeCatalogue {
        val ancien = icones(ID)
        val v1 = VersionModele("v1", "V1", "Animation d'origine (ne suit pas le son).", emptyList()) {
            ancien ?: Modele(ID, "Égaliseur", Modele.Type.STATIQUE, listOf(Modele.Image(Grille().frame(), 1000)))
        }
        val v2 = VersionModele(
            "v2", "V2",
            "Suit le vrai son du téléphone (autorisation Micro). Sans mesure possible : l'animation V1.",
            listOf(STYLE),
        ) { v ->
            val style = v.s(STYLE)
            val rendu: (LocalTime) -> MatrixFrame = { _ ->
                val n = Spectre.niveaux()
                if (n == null) ancien?.let { Live.imageA(it, System.currentTimeMillis()) } ?: Grille().frame()
                else dessiner(n, style, System.currentTimeMillis()).frame()
            }
            val exemple = FloatArray(Spectre.BANDES) { listOf(0.55f, 0.9f, 0.7f, 0.45f, 0.8f, 0.6f, 0.35f, 0.5f, 0.3f)[it] }
            Modele(
                id = ID,
                nom = "Égaliseur",
                type = Modele.Type.STATIQUE,
                images = listOf(Modele.Image(dessiner(exemple, style, 0, cretesFixes = true).frame(), 1000)),
                dynamique = Modele.Dynamique.HORLOGE,
                rendu = rendu,
                periodeMs = 50,
            )
        }
        return EntreeCatalogue(ID, "Égaliseur", listOf(v1, v2))
    }

    /** Colonnes de chaque barre selon le style. */
    private fun colonnes(style: String): List<List<Int>> = when (style) {
        "barres" -> listOf(listOf(1, 2), listOf(4, 5), listOf(7, 8), listOf(10, 11))
        else -> listOf(1, 3, 5, 7, 9, 11).map { listOf(it) }
    }

    private fun dessiner(niv: FloatArray, style: String, ms: Long, cretesFixes: Boolean = false): Grille {
        val g = Grille()
        val barres = colonnes(style)
        val n = Spectre.regrouper(niv, barres.size)
        val centre = style == "centre"
        // Bas de la zone : ligne 10 ; haut : ligne 2 (9 lignes, comme l'original).
        val bas = 10
        val hauteurMax = 9
        barres.forEachIndexed { i, cols ->
            val v = n[i].coerceIn(0f, 1f)
            if (centre) {
                val c = Grille.C
                val demi = (v * 5).roundToInt()
                for (x in cols) {
                    g.allumer(x, c, Grille.niveau(if (demi == 0) 2 else 7))
                    for (k in 1..demi) {
                        val lum = if (k == demi) MatrixFrame.MAX else Grille.niveau(3 + k)
                        g.allumer(x, c - k, lum); g.allumer(x, c + k, lum)
                    }
                }
                return@forEachIndexed
            }
            val h = (v * hauteurMax).roundToInt().coerceIn(1, hauteurMax)
            // Crête : remonte tout de suite, retombe d'une ligne toutes les 120 ms après 300 ms de pause.
            val crete = if (cretesFixes) (h + 1).coerceAtMost(hauteurMax) else synchronized(cretes) {
                if (h >= cretes[i]) { cretes[i] = h.toFloat(); cretesA[i] = ms }
                else {
                    val chute = ((ms - cretesA[i] - 300).coerceAtLeast(0) / 120f)
                    cretes[i] = (cretes[i] - chute).coerceAtLeast(h.toFloat())
                    if (chute > 0) cretesA[i] = ms - 300
                }
                cretes[i].roundToInt()
            }
            for (x in cols) {
                for (k in 0 until h) {
                    // Dégradé : base douce, sommet brillant.
                    val lum = if (k == h - 1) MatrixFrame.MAX else Grille.niveau((3 + k * 5 / hauteurMax).coerceAtMost(8))
                    g.allumer(x, bas - k, lum)
                }
                if (crete > h) g.allumer(x, bas - (crete - 1), Grille.niveau(6))
            }
        }
        return g
    }
}
