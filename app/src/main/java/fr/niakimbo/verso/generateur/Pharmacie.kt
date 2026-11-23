package fr.niakimbo.verso.generateur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.VersionModele
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Croix de pharmacie façon enseigne LED qui part en vrille (pour le troll) :
 * clignote, tourne, rebondit partout, explose, se remplit, scintille…
 */
object Pharmacie {
    private const val PAS_MS = 60L
    private const val C = 6f // centre de la matrice

    private val NUMERO = Reglage.Choix(
        cle = "v1.numero", titre = "Numéro",
        description = "Tout le spectacle, ou un seul effet en boucle.",
        options = listOf(
            "show" to "🎪 Tout le show",
            "clignote" to "⚡ Clignote",
            "tourne" to "🌀 Tourne",
            "rebond" to "🏓 Rebondit partout",
            "zoom" to "🔍 Zoom",
            "explose" to "💥 Explose",
            "vague" to "🌊 Vagues",
            "contour" to "🔄 Contour qui court",
            "balayage" to "📡 Balayage",
            "scintille" to "✨ Scintille",
            "secoue" to "🫨 Secoué",
        ),
        defaut = "show",
    )
    private val MELANGE = Reglage.Interrupteur(
        cle = "v1.melange", titre = "Ordre au hasard",
        description = "Le show enchaîne les effets dans le désordre.",
        defaut = true,
    )
    private val FOLIE = Reglage.Curseur(
        cle = "v1.folie", titre = "Folie",
        description = "1 = enseigne normale, 3 = complètement perchée.",
        min = 1f, max = 3f, defaut = 2f, crans = 2,
        format = { listOf("😐 Calme", "😵 Folle", "🤪 Perchée")[(it.roundToInt() - 1).coerceIn(0, 2)] },
    )

    /** Intensité (0..1) d'un point dans une croix centrée en (cx, cy), bras [s], demi-épaisseur [w], angle [a]. */
    private fun dansCroix(px: Float, py: Float, cx: Float, cy: Float, s: Float, w: Float, a: Float): Boolean {
        val dx = px - cx
        val dy = py - cy
        val u = dx * cos(a) + dy * sin(a)
        val v = -dx * sin(a) + dy * cos(a)
        return (abs(u) <= w && abs(v) <= s) || (abs(v) <= w && abs(u) <= s)
    }

    /** Croix rendue avec un léger lissage (4 échantillons par LED). */
    private fun croix(cx: Float = C, cy: Float = C, s: Float = 5.5f, w: Float = 1.6f, a: Float = 0f, lum: Float = 1f): MatrixFrame =
        image { x, y ->
            var n = 0
            for (oy in listOf(-0.25f, 0.25f)) for (ox in listOf(-0.25f, 0.25f)) if (dansCroix(x + ox, y + oy, cx, cy, s, w, a)) n++
            n / 4f * lum
        }

    private fun image(f: (Int, Int) -> Float): MatrixFrame = MatrixFrame(
        IntArray(MatrixFrame.SIZE) { i ->
            val x = i % MatrixFrame.SIDE
            val y = i / MatrixFrame.SIDE
            if (!MatrixFrame.isOnMatrix(x, y)) 0 else (f(x, y).coerceIn(0f, 1f) * MatrixFrame.MAX).roundToInt()
        },
    )

    private val base by lazy { croix() }
    private fun estCroix(x: Int, y: Int) = base[x, y] > 0

    // ——— Les numéros : chacun renvoie ses images (une toutes les 60 ms) ———

    private fun clignote(folie: Int): List<MatrixFrame> = buildList {
        val fois = 3 + folie * 2
        repeat(fois) { k ->
            val tenue = if (folie == 3 && k % 2 == 1) 1 else 3
            repeat(tenue) { add(base) }
            repeat(tenue) { add(MatrixFrame.blank()) }
        }
        // Stroboscope final.
        repeat(4 * folie) { add(if (it % 2 == 0) base else MatrixFrame.blank()) }
    }

    private fun tourne(folie: Int): List<MatrixFrame> {
        val n = 30 + 10 * folie
        val tours = 1f + folie
        return List(n) { k ->
            val t = k / n.toFloat()
            // Accélère puis freine.
            val e = (1 - cos(t * PI.toFloat())) / 2
            croix(a = e * tours * PI.toFloat())
        }
    }

    private fun rebond(folie: Int): List<MatrixFrame> {
        val r = Random(7)
        var x = 4f
        var y = 5f
        var vx = 0.55f + 0.25f * folie
        var vy = 0.38f + 0.2f * folie
        var a = 0f
        val limite = 3.2f
        return List(45 + 10 * folie) {
            x += vx; y += vy; a += 0.15f * folie
            val dx = x - C
            val dy = y - C
            val d = hypot(dx, dy)
            if (d > limite) {
                // Rebond sur le bord du cercle (réflexion autour de la normale) + un peu de hasard.
                val nx = dx / d
                val ny = dy / d
                val p = vx * nx + vy * ny
                vx -= 2 * p * nx; vy -= 2 * p * ny
                vx += (r.nextFloat() - 0.5f) * 0.2f; vy += (r.nextFloat() - 0.5f) * 0.2f
                x = C + nx * limite; y = C + ny * limite
            }
            croix(x, y, s = 2.6f, w = 0.9f, a = if (folie >= 2) a else 0f)
        }
    }

    private fun zoom(folie: Int): List<MatrixFrame> = buildList {
        val rebonds = 1 + folie
        repeat(rebonds) {
            for (k in 0..10) add(croix(s = 0.6f + k * 0.5f, w = 0.5f + k * 0.11f))
            for (k in 10 downTo 0) add(croix(s = 0.6f + k * 0.5f, w = 0.5f + k * 0.11f))
        }
        repeat(6) { add(base) }
    }

    private fun explose(folie: Int): List<MatrixFrame> = buildList {
        val r = Random(11)
        val pts = buildList {
            for (y in 0 until MatrixFrame.SIDE) for (x in 0 until MatrixFrame.SIDE) if (estCroix(x, y)) add(x to y)
        }
        val v = pts.map { (x, y) ->
            val a = r.nextFloat() * 2 * PI.toFloat()
            val s = (0.3f + r.nextFloat() * 0.5f) * folie
            Triple(x, y, (cos(a) * s) to (sin(a) * s))
        }
        repeat(4) { add(base) }
        // Gonfle avant d'exploser.
        repeat(3) { add(croix(s = 6f, w = 2f)) }
        val n = 14
        fun etape(t: Float) = image { x, y ->
            var l = 0f
            for ((px, py, d) in v) {
                val qx = px + d.first * t * 10
                val qy = py + d.second * t * 10
                if (abs(qx - x) < 0.6f && abs(qy - y) < 0.6f) l = maxOf(l, 1f - t * 0.6f)
            }
            l
        }
        for (k in 1..n) add(etape(k / n.toFloat()))
        repeat(3) { add(MatrixFrame.blank()) }
        // Se reforme.
        for (k in n downTo 1) add(etape(k / n.toFloat()))
        repeat(5) { add(base) }
    }

    private fun vague(folie: Int): List<MatrixFrame> = List(36 + 6 * folie) { k ->
        image { x, y ->
            if (!estCroix(x, y)) 0f
            else {
                val d = hypot(x - C, y - C)
                val o = sin(d * 1.3f - k * 0.45f * folie)
                0.15f + 0.85f * ((o + 1) / 2)
            }
        }
    }

    /** Points du contour de la croix (bras de 3 LED, de 1 à 11), parcourus dans l'ordre, sens horaire. */
    private val contourOrdonne: List<Pair<Int, Int>> by lazy {
        val coins = listOf(5 to 1, 7 to 1, 7 to 5, 11 to 5, 11 to 7, 7 to 7, 7 to 11, 5 to 11, 5 to 7, 1 to 7, 1 to 5, 5 to 5)
        buildList {
            for (k in coins.indices) {
                val (x0, y0) = coins[k]
                val (x1, y1) = coins[(k + 1) % coins.size]
                val n = maxOf(abs(x1 - x0), abs(y1 - y0))
                for (i in 0 until n) add(x0 + (x1 - x0) * i / n to y0 + (y1 - y0) * i / n)
            }
        }
    }

    private fun contour(folie: Int): List<MatrixFrame> {
        val pts = contourOrdonne
        val queue = 6 + 2 * folie
        val n = pts.size * (folie + 1) / 2 + pts.size
        return List(n) { k ->
            val tete = (k * folie) % pts.size
            val lum = HashMap<Pair<Int, Int>, Float>()
            for (q in 0 until queue) {
                val p = pts[(tete - q + pts.size * 4) % pts.size]
                lum[p] = maxOf(lum[p] ?: 0f, 1f - q / queue.toFloat())
                // Deux comètes opposées en mode perché.
                if (folie == 3) {
                    val p2 = pts[(tete - q + pts.size / 2 + pts.size * 4) % pts.size]
                    lum[p2] = maxOf(lum[p2] ?: 0f, 1f - q / queue.toFloat())
                }
            }
            image { x, y -> lum[x to y] ?: if (estCroix(x, y)) 0.2f else 0f }
        }
    }

    private fun balayage(folie: Int): List<MatrixFrame> = buildList {
        val passes = 1 + folie
        repeat(passes) { p ->
            val vertical = p % 2 == 1
            for (k in -2..14) add(
                image { x, y ->
                    if (!estCroix(x, y)) 0f
                    else {
                        val d = abs((if (vertical) y else x) - k)
                        if (d <= 1) 1f else 0.2f
                    }
                },
            )
        }
        // Remplissage ligne par ligne.
        for (k in 0..12) add(image { x, y -> if (estCroix(x, y) && y <= k) 1f else 0f })
        repeat(4) { add(base) }
    }

    private fun scintille(folie: Int): List<MatrixFrame> {
        val r = Random(3)
        return List(30 + 8 * folie) {
            image { x, y ->
                if (!estCroix(x, y)) 0f
                else if (r.nextFloat() < 0.25f + 0.15f * folie) 1f else 0.08f
            }
        }
    }

    private fun secoue(folie: Int): List<MatrixFrame> {
        val r = Random(5)
        return List(30 + 6 * folie) {
            val ampl = 0.6f * folie
            croix(
                cx = C + (r.nextFloat() - 0.5f) * 2 * ampl,
                cy = C + (r.nextFloat() - 0.5f) * 2 * ampl,
                a = if (folie == 3) (r.nextFloat() - 0.5f) * 0.6f else 0f,
            )
        }
    }

    private val numeros: Map<String, (Int) -> List<MatrixFrame>> = linkedMapOf(
        "clignote" to ::clignote,
        "tourne" to ::tourne,
        "rebond" to ::rebond,
        "zoom" to ::zoom,
        "explose" to ::explose,
        "vague" to ::vague,
        "contour" to ::contour,
        "balayage" to ::balayage,
        "scintille" to ::scintille,
        "secoue" to ::secoue,
    )

    /** Images d'un numéro ou du show complet. Exposé pour les tests. */
    internal fun images(numero: String, folie: Int, melange: Boolean): List<MatrixFrame> {
        val liste = if (numero == "show") {
            val ordre = numeros.keys.toList()
            if (melange) ordre.shuffled(Random(22)) else ordre
        } else listOf(numero)
        return liste.flatMap { numeros.getValue(it)(folie) }
    }

    private val v1 = VersionModele(
        id = "v1", nom = "V1",
        description = "Enseigne de pharmacie qui part en vrille.",
        reglages = listOf(NUMERO, FOLIE, MELANGE),
    ) { v ->
        val imgs = images(v.s(NUMERO), v.i(FOLIE), v.b(MELANGE))
        Modele(
            id = "pharmacie", nom = "Pharmacie", type = Modele.Type.ANIME,
            images = imgs.map { Modele.Image(it, PAS_MS) },
        )
    }

    val entree = EntreeCatalogue("pharmacie", "Pharmacie", listOf(v1), categorie = "Formes")
}
