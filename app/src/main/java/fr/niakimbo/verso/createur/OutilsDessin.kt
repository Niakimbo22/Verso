package fr.niakimbo.verso.createur

import fr.niakimbo.verso.matrix.MatrixFrame
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Opérations de l'éditeur dessin sur une image 13×13 (169 luminosités 0-255, ligne par ligne).
 * Fonctions pures : elles renvoient une nouvelle image et ne touchent jamais à celle reçue.
 * Rien n'est jamais dessiné hors du disque de la matrice (les coins n'ont pas de LED).
 */
object OutilsDessin {
    const val N = MatrixFrame.SIDE

    /** Niveau 1-9 de la palette → luminosité 0-255 (même échelle que le format « dessin »). */
    fun luminosite(niveau: Int): Int = niveau.coerceIn(0, 9) * MatrixFrame.MAX / 9

    /** Niveau 0-9 le plus proche d'une luminosité (pour la pipette). */
    fun niveau(valeur: Int): Int = if (valeur <= 0) 0 else ((valeur * 9 + MatrixFrame.MAX / 2) / MatrixFrame.MAX).coerceIn(1, 9)

    data class Symetrie(val horizontale: Boolean = false, val verticale: Boolean = false)

    fun dansMatrice(x: Int, y: Int) = x in 0 until N && y in 0 until N && MatrixFrame.isOnMatrix(x, y)

    /** Points de [points] et leurs reflets selon la symétrie (gauche/droite, haut/bas). */
    fun refleter(points: Collection<Pair<Int, Int>>, s: Symetrie): Set<Pair<Int, Int>> {
        val out = LinkedHashSet<Pair<Int, Int>>()
        for ((x, y) in points) {
            out += x to y
            if (s.horizontale) out += (N - 1 - x) to y
            if (s.verticale) out += x to (N - 1 - y)
            if (s.horizontale && s.verticale) out += (N - 1 - x) to (N - 1 - y)
        }
        return out
    }

    fun poser(px: IntArray, points: Collection<Pair<Int, Int>>, valeur: Int, s: Symetrie): IntArray {
        val out = px.copyOf()
        for ((x, y) in refleter(points, s)) if (dansMatrice(x, y)) out[y * N + x] = valeur
        return out
    }

    /** Segment de (x0, y0) à (x1, y1), extrémités comprises (Bresenham). */
    fun ligne(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val pts = ArrayList<Pair<Int, Int>>()
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            pts += x to y
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        return pts
    }

    /** Cercle de centre (cx, cy) passant par (x, y). [plein] = disque rempli. */
    fun cercle(cx: Int, cy: Int, x: Int, y: Int, plein: Boolean): List<Pair<Int, Int>> {
        val r = hypot((x - cx).toDouble(), (y - cy).toDouble())
        if (r < 0.5) return listOf(cx to cy)
        val pts = ArrayList<Pair<Int, Int>>()
        for (py in 0 until N) for (qx in 0 until N) {
            val d = hypot((qx - cx).toDouble(), (py - cy).toDouble())
            if (if (plein) d <= r + 0.35 else abs(d - r) < 0.55) pts += qx to py
        }
        return pts
    }

    /** Remplit la zone de même luminosité autour de (x, y) (voisins haut/bas/gauche/droite). */
    fun remplir(px: IntArray, x: Int, y: Int, valeur: Int, s: Symetrie): IntArray {
        if (!dansMatrice(x, y)) return px
        val departs = refleter(listOf(x to y), s).filter { (a, b) -> dansMatrice(a, b) }
        var out = px.copyOf()
        for ((ax, ay) in departs) {
            val cible = out[ay * N + ax]
            if (cible == valeur) continue
            val pile = ArrayDeque<Pair<Int, Int>>()
            pile.addLast(ax to ay)
            val copie = out.copyOf()
            while (pile.isNotEmpty()) {
                val (cx, cy) = pile.removeLast()
                if (!dansMatrice(cx, cy) || copie[cy * N + cx] != cible) continue
                copie[cy * N + cx] = valeur
                pile.addLast(cx + 1 to cy); pile.addLast(cx - 1 to cy)
                pile.addLast(cx to cy + 1); pile.addLast(cx to cy - 1)
            }
            out = copie
        }
        return out
    }

    /** Décale tout le dessin d'une case, en faisant le tour (ce qui sort d'un côté rentre de l'autre). */
    fun decaler(px: IntArray, dx: Int, dy: Int): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until N) for (x in 0 until N) {
            out[Math.floorMod(y + dy, N) * N + Math.floorMod(x + dx, N)] = px[y * N + x]
        }
        return out
    }

    /** Quart de tour (sens des aiguilles d'une montre si [horaire]). */
    fun tourner(px: IntArray, horaire: Boolean): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until N) for (x in 0 until N) {
            val (nx, ny) = if (horaire) (N - 1 - y) to x else y to (N - 1 - x)
            out[ny * N + nx] = px[y * N + x]
        }
        return out
    }

    fun retourner(px: IntArray, horizontal: Boolean): IntArray {
        val out = IntArray(px.size)
        for (y in 0 until N) for (x in 0 until N) {
            val (nx, ny) = if (horizontal) (N - 1 - x) to y else x to (N - 1 - y)
            out[ny * N + nx] = px[y * N + x]
        }
        return out
    }

    /** Négatif : sur le disque, allumé ↔ éteint. */
    fun inverser(px: IntArray): IntArray = IntArray(px.size) { i ->
        if (MatrixFrame.isOnMatrix(i % N, i / N)) MatrixFrame.MAX - px[i] else 0
    }

    /** Assombrit (−) ou éclaircit (+) d'un niveau toutes les LEDs allumées. */
    fun nuancer(px: IntArray, pas: Int): IntArray = IntArray(px.size) { i ->
        val v = px[i]
        if (v <= 0) 0 else luminosite((niveau(v) + pas).coerceIn(1, 9))
    }

    fun vide() = IntArray(N * N)

    /** Plus grande luminosité (pour savoir si une image est vide). */
    fun plusClair(px: IntArray) = px.fold(0) { a, b -> max(a, b) }
}
