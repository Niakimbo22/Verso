package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Petit poulpe mignon en pixel art (clin d'œil à Claude) : il flotte, fait coucou,
 * regarde autour de lui, file vers le haut, lâche de l'encre et souffle des bulles.
 */
object Poulpe {
    private const val PAS_MS = 70L
    private const val C = 6 // colonne centrale de la matrice

    private val SCENE = Reglage.Choix(
        cle = "v1.scene", titre = "Scène",
        description = "Toute sa petite vie, ou une scène seule en boucle.",
        options = listOf(
            "vie" to "🐙 Il vit sa vie",
            "nage" to "🌊 Ondule",
            "coucou" to "👋 Coucou",
            "regarde" to "👀 Regarde",
            "monte" to "⬆️ Propulsion",
            "encre" to "💨 Encre",
            "bulles" to "🫧 Bulles",
        ),
        defaut = "vie",
    )
    private val MOUVEMENTS = Reglage.Curseur(
        cle = "v1.mouvements", titre = "Mouvements",
        description = "Vitesse et amplitude des tentacules.",
        min = 1f, max = 3f, defaut = 2f, crans = 2,
        format = { listOf("😴 Tranquille", "🙂 Normal", "🤸 Excité")[(it.roundToInt() - 1).coerceIn(0, 2)] },
    )
    private val YEUX = Reglage.Interrupteur(
        cle = "v1.yeux", titre = "Yeux qui clignent",
        description = "Le poulpe cligne des yeux de temps en temps.",
        defaut = true,
    )

    // ——— Dessin ———

    /** État du poulpe à un instant donné. */
    private data class Pose(
        val top: Float = 2f, // ligne du haut de la tête
        val phase: Float = 0f, // phase d'ondulation des tentacules
        val amp: Float = 0.9f, // amplitude de l'ondulation (en LED)
        val serre: Boolean = false, // tentacules resserrées (propulsion)
        val pupille: Int = 0, // décalage des yeux (-1, 0, 1)
        val clos: Boolean = false, // yeux fermés (clignement)
        val bras: Float = 0f, // 0 = tentacule qui pend, 1 = bras levé
        val brasPhase: Float = 0f, // phase du va-et-vient du bras
        val lum: Float = 1f, // luminosité globale du poulpe
        val nuage: Float = 0f, // rayon du nuage d'encre (0 = aucun)
        val nuageLum: Float = 1f,
        val nuageY: Int = 8,
        val bulles: List<Pair<Int, Int>> = emptyList(),
    )

    /** Calque de luminosités 13×13 ; ce qui sort de la matrice est ignoré. */
    private class Toile {
        val v = FloatArray(MatrixFrame.SIZE)
        private fun dedans(x: Int, y: Int) = x in 0 until MatrixFrame.SIDE && y in 0 until MatrixFrame.SIDE
        fun poser(x: Int, y: Int, l: Float) {
            if (dedans(x, y)) v[y * MatrixFrame.SIDE + x] = l
        }
        fun fond(x: Int, y: Int, l: Float) {
            if (dedans(x, y)) v[y * MatrixFrame.SIDE + x] = maxOf(v[y * MatrixFrame.SIDE + x], l)
        }
    }

    private fun image(f: (Int, Int) -> Float): MatrixFrame = MatrixFrame(
        IntArray(MatrixFrame.SIZE) { i ->
            val x = i % MatrixFrame.SIDE
            val y = i / MatrixFrame.SIDE
            if (!MatrixFrame.isOnMatrix(x, y)) 0 else (f(x, y).coerceIn(0f, 1f) * MatrixFrame.MAX).roundToInt()
        },
    )

    /** Demi-largeur de chaque ligne de la tête (dôme de 7 LED au plus large). */
    private val DEMI = intArrayOf(1, 2, 3, 3, 3, 3)

    private fun rendre(p: Pose): MatrixFrame {
        val t = Toile()
        val top = p.top.roundToInt()

        // Nuage d'encre derrière le poulpe : pâle, un peu troué sur les bords.
        if (p.nuage > 0f) {
            for (y in 0 until MatrixFrame.SIDE) for (x in 0 until MatrixFrame.SIDE) {
                val d = hypot((x - C).toFloat(), (y - p.nuageY).toFloat())
                if (d > p.nuage) continue
                val coeur = d < p.nuage * 0.55f
                if (!coeur && (x * 7 + y * 3) % 6 == 0) continue
                t.fond(x, y, (if (coeur) 0.5f else 0.3f) * p.nuageLum)
            }
        }

        // Tête.
        for (r in DEMI.indices) for (dx in -DEMI[r]..DEMI[r]) t.poser(C + dx, top + r, p.lum)
        // Yeux : LED éteintes, sauf pendant un clignement (la tête reste pleine).
        if (!p.clos) {
            t.poser(C - 2 + p.pupille, top + 3, 0f)
            t.poser(C + 2 + p.pupille, top + 3, 0f)
        }

        // Tentacules fines qui ondulent, de plus en plus vers le bout.
        val offsets = if (p.serre) intArrayOf(-2, -1, 1, 2) else intArrayOf(-3, -1, 1, 3)
        val longueurs = if (p.serre) intArrayOf(4, 5, 5, 4) else intArrayOf(3, 4, 4, 3)
        val amp = if (p.serre) p.amp * 0.4f else p.amp
        val bras = p.bras > 0.02f
        for (i in offsets.indices) {
            if (bras && i == 3) continue // celle-ci devient le bras qui fait coucou
            val n = longueurs[i]
            for (j in 0 until n) {
                val prog = (j + 1) / n.toFloat()
                val dx = amp * sin(p.phase - i * 1.5f - j * 0.7f) * prog
                val x = (C + offsets[i] + dx).roundToInt()
                t.poser(x, top + 6 + j, (if (j == n - 1) 0.6f else 1f) * p.lum)
            }
        }
        if (bras) {
            // Bras articulé depuis l'épaule : angle 0 = vers le bas, ~2,2 rad = levé vers l'extérieur.
            val a = p.bras * 2.2f + sin(p.brasPhase) * 0.4f * p.bras
            val sx = (C + 3).toFloat()
            val sy = (top + 5).toFloat()
            for (j in 1..4) {
                val x = (sx + sin(a) * j).roundToInt()
                val y = (sy + cos(a) * j).roundToInt()
                t.poser(x, y, (if (j == 4) 0.6f else 1f) * p.lum)
            }
        }

        // Bulles.
        for ((bx, by) in p.bulles) t.fond(bx, by, 0.5f)

        return image { x, y -> t.v[y * MatrixFrame.SIDE + x] }
    }

    // ——— Réglage de la vitesse selon « Mouvements » ———

    private fun vit(m: Int) = when (m) { 1 -> 0.28f; 2 -> 0.4f; else -> 0.55f }
    private fun amplitude(m: Int) = when (m) { 1 -> 0.6f; 2 -> 0.9f; else -> 1.3f }
    private fun pose(k: Int, m: Int, top: Float = 2f) = Pose(top = top, phase = k * vit(m), amp = amplitude(m))

    // ——— Scènes : chacune renvoie ses poses (une toutes les 70 ms) ———

    /** Il flotte en place, léger bob vertical d'une LED. */
    private fun nage(m: Int): List<Pose> {
        val n = 64 - 8 * m
        return List(n) { k -> pose(k, m, top = 2f + sin(2 * PI.toFloat() * k / n)) }
    }

    /** Une tentacule se lève, fait coucou, puis redescend. */
    private fun coucou(m: Int): List<Pose> = buildList {
        val periode = 10 - 2 * m
        val vague = periode * (m + 1)
        var k = 0
        fun ajoute(bras: Float, bp: Float) { add(pose(k, m).copy(bras = bras, brasPhase = bp)); k++ }
        repeat(4) { ajoute(0f, 0f) }
        for (i in 1..6) ajoute(i / 6f, 0f)
        for (i in 0 until vague) ajoute(1f, 2 * PI.toFloat() * i / periode)
        for (i in 5 downTo 0) ajoute(i / 6f, 0f)
        repeat(4) { ajoute(0f, 0f) }
    }

    /** Les yeux vont à gauche, à droite, puis reviennent au centre. */
    private fun regarde(m: Int): List<Pose> {
        val tenue = 14 - 2 * m
        val seq = buildList {
            repeat(6) { add(0) }
            repeat(tenue) { add(-1) }
            repeat(2) { add(0) }
            repeat(tenue) { add(1) }
            repeat(6) { add(0) }
        }
        return seq.mapIndexed { k, pup -> pose(k, m).copy(pupille = pup) }
    }

    /** Se contracte, file vers le haut, sort de l'écran, revient par le bas. */
    private fun monte(m: Int): List<Pose> = buildList {
        var k = 0
        fun ajoute(top: Float, serre: Boolean, amp: Float = amplitude(m)) {
            add(Pose(top = top, phase = k * vit(m), amp = amp, serre = serre)); k++
        }
        // Contraction (il s'accroupit d'une LED).
        repeat(3) { ajoute(2f, false) }
        repeat(3) { ajoute(3f, true) }
        // Départ en accélérant jusqu'à sortir par le haut.
        val sortie = 14 - 2 * m
        for (i in 1..sortie) {
            val u = i / sortie.toFloat()
            ajoute(3f - 15f * u * u, true, amplitude(m) * 0.5f)
        }
        repeat(3) { add(Pose(top = 30f)); k++ } // hors écran : plus rien (vide)
        // Retour par le bas en freinant.
        for (i in 1..sortie) {
            val u = i / sortie.toFloat()
            ajoute(2f + 11f * (1f - u) * (1f - u), true, amplitude(m) * 0.5f)
        }
        // Il se détend.
        repeat(8) { ajoute(2f, false) }
    }

    /** Sursaut, nuage d'encre qui grossit puis s'estompe, le poulpe réapparaît. */
    private fun encre(m: Int): List<Pose> = buildList {
        var k = 0
        fun ajoute(p: Pose) { add(p.copy(phase = k * vit(m), amp = amplitude(m))); k++ }
        repeat(4) { ajoute(Pose()) }
        repeat(3) { ajoute(Pose(top = 1f, serre = true)) }
        for (j in 1..10) ajoute(Pose(top = 2f, lum = if (j < 3) 0.5f else 0.2f, nuage = 1f + j * 0.4f, nuageY = 8))
        for (j in 1..12) {
            val f = j / 12f
            ajoute(Pose(top = 2f, lum = 0.2f + 0.8f * f, nuage = 5f + j * 0.15f, nuageLum = 1f - f, nuageY = 8))
        }
        repeat(4) { ajoute(Pose()) }
    }

    /** Des bulles montent de sa tête (et de ses côtés) pendant qu'il ondule. */
    private fun bulles(m: Int): List<Pose> {
        val n = 56
        val top = 3
        val parRang = 5 - m // images par LED de montée
        val espace = 6
        // Point de départ des bulles (x, y) : au-dessus de la tête et sur les côtés.
        val origines = listOf(C + 1 to top - 1, C - 4 to top + 1, C - 1 to top - 1, C + 4 to top)
        return List(n) { k ->
            val bs = buildList {
                for (b in 0..k / espace) {
                    val age = k - b * espace
                    if (age < 0) continue
                    val (ox, oy) = origines[b % origines.size]
                    val rang = age / parRang
                    val y = oy - rang
                    if (y < 0) continue
                    val sens = if (ox < C) 1 else -1
                    add(ox + sens * (rang % 2) to y)
                }
            }
            pose(k, m, top = top.toFloat()).copy(bulles = bs)
        }
    }

    private val scenes: Map<String, (Int) -> List<Pose>> = linkedMapOf(
        "nage" to ::nage,
        "coucou" to ::coucou,
        "regarde" to ::regarde,
        "monte" to ::monte,
        "bulles" to ::bulles,
        "encre" to ::encre,
    )

    /** Ordre du mode « vie » : il commence calmement, finit en douceur. */
    private val ORDRE_VIE = listOf("nage", "coucou", "regarde", "monte", "bulles", "encre", "nage")

    /** Images d'une scène ou de toute sa vie, avec clignements si [yeux]. Exposé pour les tests. */
    internal fun images(scene: String, mouvements: Int, yeux: Boolean = true): List<MatrixFrame> {
        val m = mouvements.coerceIn(1, 3)
        val liste = if (scene == "vie") ORDRE_VIE else listOf(scene)
        val poses = liste.flatMap { scenes.getValue(it)(m) }
        return poses.mapIndexed { i, p ->
            // Clignement de 2 images, de temps en temps (sauf quand le poulpe est caché dans l'encre).
            val cligne = yeux && i % 29 in 20..21 && p.lum >= 1f
            rendre(if (cligne) p.copy(clos = true) else p)
        }
    }

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Petit poulpe qui vit sa vie au dos.",
        reglages = listOf(SCENE, MOUVEMENTS, YEUX),
    ) { v ->
        val imgs = images(v.s(SCENE), v.i(MOUVEMENTS), v.b(YEUX))
        Modele(
            id = "poulpe", nom = "Poulpe", type = Modele.Type.ANIME,
            images = imgs.map { Modele.Image(it, PAS_MS) },
        )
    }

    val entree = EntreeCatalogue("poulpe", "Poulpe", listOf(v1), categorie = "Formes")
}
