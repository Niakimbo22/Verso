package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Les 8 phases de la Lune, dans l'ordre du cycle. */
internal enum class PhaseLune(val nom: String, val emoji: String) {
    NOUVELLE("Nouvelle lune", "🌑"),
    PREMIER_CROISSANT("Premier croissant", "🌒"),
    PREMIER_QUARTIER("Premier quartier", "🌓"),
    GIBBEUSE_CROISSANTE("Gibbeuse croissante", "🌔"),
    PLEINE("Pleine lune", "🌕"),
    GIBBEUSE_DECROISSANTE("Gibbeuse décroissante", "🌖"),
    DERNIER_QUARTIER("Dernier quartier", "🌗"),
    DERNIER_CROISSANT("Dernier croissant", "🌘"),
}

/** Phase nommée pour [fraction] du cycle : les 4 principales ont une fenêtre de ±1/16 (≈ ±1,8 jour). */
internal fun phaseNommee(fraction: Double): PhaseLune {
    val f = ((fraction % 1.0) + 1.0) % 1.0
    val k = (f * 16).toInt() // seizièmes de cycle, 0..15
    return when (k) {
        15, 0 -> PhaseLune.NOUVELLE
        1, 2 -> PhaseLune.PREMIER_CROISSANT
        3, 4 -> PhaseLune.PREMIER_QUARTIER
        5, 6 -> PhaseLune.GIBBEUSE_CROISSANTE
        7, 8 -> PhaseLune.PLEINE
        9, 10 -> PhaseLune.GIBBEUSE_DECROISSANTE
        11, 12 -> PhaseLune.DERNIER_QUARTIER
        else -> PhaseLune.DERNIER_CROISSANT
    }
}

/** La Lune du jour : sa phase (dessin net), avec ses mers et ses cratères. */
object LuneLive {
    const val ID = "lune-live"

    private const val CYCLE_JOURS = 29.530588853
    private const val RAYON = 5.5
    private const val CENTRE = 6
    private const val SUR = 4 // sur-échantillonnage 4×4 par LED
    private const val CENDREE = 0.08

    /**
     * Relief de la face visible, vu de l'hémisphère Nord : M = mer sombre, m = mer claire,
     * T = cratère brillant (Copernic, Tycho), le reste = terres claires.
     * Mers de la Pluie et des Nuées à gauche, de la Sérénité et de la Tranquillité au centre-droit, des Crises à droite.
     */
    private val RELIEF = listOf(
        ".............",
        ".............",
        "...MM..m.....",
        "..MMM.mm.....",
        ".MM....mM.M..",
        ".MM.T..MMMM..",
        ".MM.....MmM..",
        "..MM.....m...",
        "...MMM.......",
        "....m........",
        "......T......",
        ".............",
        ".............",
    )

    /** Luminosité du sol en (x, y) : 1 cratère brillant, 0,82 terres, 0,55 mer claire, 0,38 mer sombre. */
    private fun sol(x: Int, y: Int, sud: Boolean): Double {
        val cx = if (sud) MatrixFrame.SIDE - 1 - x else x
        return when (RELIEF[y][cx]) {
            'T' -> 1.0
            'M' -> 0.38
            'm' -> 0.55
            else -> 0.82
        }
    }

    // Nouvelle lune de référence : 2000-01-06 18:14 UTC.
    private val REFERENCE = LocalDateTime.of(2000, 1, 6, 18, 14).toEpochSecond(ZoneOffset.UTC)

    // Coins libres du disque (sur la matrice, hors de la Lune) et déphasage de chaque étoile.
    private val ETOILES = listOf(Triple(2, 2, 0.0), Triple(10, 2, 1.7), Triple(3, 11, 3.1), Triple(11, 9, 4.6))

    private val AFFICHAGE = Reglage.Choix(
        cle = "v1.affichage", titre = "Affichage",
        description = "Le dessin net de la phase, ou l'éclairage exact du jour.",
        options = listOf("phase" to "🌙 Phase du jour", "exacte" to "🎯 Éclairage exact"),
        defaut = "phase",
    )
    private val CENDREE_SW = Reglage.Interrupteur(
        cle = "v1.cendree", titre = "Face sombre visible",
        description = "La partie dans l'ombre reste très faiblement allumée (lumière cendrée).",
        defaut = true,
    )
    private val HEMISPHERE = Reglage.Choix(
        cle = "v1.hemisphere", titre = "Hémisphère",
        description = "Nord : la Lune croît vers la droite. Sud : image en miroir.",
        options = listOf("nord" to "Nord", "sud" to "Sud"),
        defaut = "nord",
    )
    private val ETOILES_SW = Reglage.Interrupteur(
        cle = "v1.etoiles", titre = "Étoiles",
        description = "Quelques petites étoiles qui scintillent dans les coins.",
        defaut = false,
    )

    /** Âge de la Lune en fraction du cycle : 0 nouvelle, 0,5 pleine, 1 de nouveau nouvelle. */
    internal fun phase(date: LocalDate): Double {
        val t = date.atTime(12, 0).toEpochSecond(ZoneOffset.UTC) // midi UTC
        val jours = (t - REFERENCE) / 86_400.0
        val f = (jours / CYCLE_JOURS) % 1.0
        return if (f < 0) f + 1.0 else f
    }

    /**
     * Dessin de la Lune pour [fraction] du cycle. [t] = temps en secondes, pour le scintillement.
     */
    internal fun dessin(
        fraction: Double,
        cendree: Boolean = true,
        sud: Boolean = false,
        etoiles: Boolean = false,
        t: Double = 0.0,
    ): MatrixFrame {
        val f = ((fraction % 1.0) + 1.0) % 1.0
        val c = cos(2 * PI * f)
        val croissante = f <= 0.5
        val n = MatrixFrame.SIDE
        val eclaire = DoubleArray(MatrixFrame.SIZE)
        val disque = DoubleArray(MatrixFrame.SIZE)

        for (y in 0 until n) for (x in 0 until n) {
            if (!MatrixFrame.isOnMatrix(x, y)) continue
            var l = 0
            var d = 0
            for (j in 0 until SUR) for (i in 0 until SUR) {
                var dx = x + (i + 0.5) / SUR - 0.5 - CENTRE
                val dy = y + (j + 0.5) / SUR - 0.5 - CENTRE
                if (sud) dx = -dx
                val h = RAYON * RAYON - dy * dy
                if (dx * dx > h) continue
                d++
                val xt = sqrt(h) * c // demi-ellipse du terminateur
                if (if (croissante) dx >= xt else dx <= -xt) l++
            }
            val k = y * n + x
            eclaire[k] = l / (SUR * SUR).toDouble()
            disque[k] = d / (SUR * SUR).toDouble()
        }

        // Mince croissant : jamais invisible entre 0,02 et 0,98 (on garde au moins 3 LED du bord éclairé).
        if (f in 0.02..0.98 && eclaire.count { it > 0.0 } < 3) {
            val sens = if (croissante) 1.0 else -1.0
            (0 until MatrixFrame.SIZE)
                .filter { disque[it] > 0.0 }
                .sortedByDescending { sens * ((if (sud) -1 else 1) * ((it % n) - CENTRE)) - 0.01 * Math.abs((it / n) - CENTRE) }
                .take(3)
                .forEach { eclaire[it] = max(eclaire[it], 0.3) }
        }

        val px = IntArray(MatrixFrame.SIZE)
        for (k in px.indices) {
            val l = eclaire[k]
            val albedo = sol(k % n, k / n, sud)
            var v = min(1.0, l * 1.15) * albedo
            if (l > 0.0) v = max(v, 0.3) // une LED du croissant ne disparaît pas
            if (cendree) v += max(0.0, disque[k] - l) * CENDREE * albedo
            px[k] = (min(1.0, v) * MatrixFrame.MAX).roundToInt()
        }

        if (etoiles) {
            for ((x, y, ph) in ETOILES) {
                val k = y * n + x
                if (px[k] > 0) continue
                val lum = 0.2 + 0.5 * (0.5 + 0.5 * sin(t * 1.6 + ph))
                px[k] = (lum * MatrixFrame.MAX).roundToInt()
            }
        }
        return MatrixFrame(px)
    }

    /** Fraction de cycle dont on tire le dessin canonique (≈ 25 % éclairé pour un croissant, 75 % pour une gibbeuse). */
    private fun fractionCanonique(p: PhaseLune): Double = when (p) {
        PhaseLune.NOUVELLE -> 0.0
        PhaseLune.PREMIER_CROISSANT -> 1.0 / 6
        PhaseLune.PREMIER_QUARTIER -> 0.25
        PhaseLune.GIBBEUSE_CROISSANTE -> 1.0 / 3
        PhaseLune.PLEINE -> 0.5
        PhaseLune.GIBBEUSE_DECROISSANTE -> 2.0 / 3
        PhaseLune.DERNIER_QUARTIER -> 0.75
        PhaseLune.DERNIER_CROISSANT -> 5.0 / 6
    }

    /** Dessin canonique d'une phase (net, pas l'éclairage exact). */
    internal fun icone(
        p: PhaseLune,
        cendree: Boolean = true,
        sud: Boolean = false,
        etoiles: Boolean = false,
        t: Double = 0.0,
    ): MatrixFrame {
        if (p != PhaseLune.NOUVELLE) return dessin(fractionCanonique(p), cendree, sud, etoiles, t)
        // Nouvelle lune : seul le contour du disque, allumé à ~25 %.
        val n = MatrixFrame.SIDE
        val px = dessin(0.0, false, sud, etoiles, t).pixels.copyOf()
        fun dedans(x: Int, y: Int): Boolean {
            if (x !in 0 until n || y !in 0 until n || !MatrixFrame.isOnMatrix(x, y)) return false
            val dx = x - CENTRE
            val dy = y - CENTRE
            return dx * dx + dy * dy <= RAYON * RAYON
        }
        for (y in 0 until n) for (x in 0 until n) {
            if (!dedans(x, y)) continue
            if (!dedans(x - 1, y) || !dedans(x + 1, y) || !dedans(x, y - 1) || !dedans(x, y + 1)) {
                val k = y * n + x
                px[k] = max(px[k], MatrixFrame.MAX / 4)
            }
        }
        return MatrixFrame(px)
    }

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "La Lune du jour : sa phase, ses mers et ses cratères.",
        reglages = listOf(AFFICHAGE, CENDREE_SW, HEMISPHERE, ETOILES_SW),
    ) { v ->
        val cendree = v.b(CENDREE_SW)
        val sud = v.s(HEMISPHERE) == "sud"
        val etoiles = v.b(ETOILES_SW)
        val exacte = v.s(AFFICHAGE) == "exacte"

        fun image(t: Double): MatrixFrame {
            val f = phase(LocalDate.now())
            return if (exacte) dessin(f, cendree, sud, etoiles, t) else icone(phaseNommee(f), cendree, sud, etoiles, t)
        }

        val rendu: (LocalTime) -> MatrixFrame = { heure ->
            val t = heure.toSecondOfDay() + heure.nano / 1e9
            image(t)
        }
        Modele(
            id = ID, nom = "Phase de Lune", type = Modele.Type.STATIQUE,
            images = listOf(Modele.Image(image(0.0), 1000)),
            dynamique = Modele.Dynamique.HORLOGE,
            rendu = rendu,
            periodeMs = if (etoiles) 250 else null,
        )
    }

    val entree = EntreeCatalogue(ID, "Phase de Lune", listOf(v1))
}
