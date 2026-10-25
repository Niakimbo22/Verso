package fr.niakimbo.verso.createur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dessine un modèle par blocs à un instant donné (VERSO_SPEC §3.5).
 *
 * Chaque bloc produit un petit calque, centré dans son rectangle puis découpé à ses bords ;
 * les calques se superposent dans l'ordre (une LED garde la valeur la plus lumineuse).
 * Le rendu est calculé en direct, comme l'horloge : rien n'est enregistré à part la description des blocs.
 */
object MoteurBlocs {

    /** Ce dont les blocs ont besoin pour se dessiner. [ms] sert aux blocs animés (texte, effet). */
    class Instant(val heure: LocalTime, val date: LocalDate, val batterie: Int, val ms: Long)

    fun instant(heure: LocalTime = LocalTime.now()) =
        Instant(heure, LocalDate.now(), Capteurs.batterie(), System.currentTimeMillis())

    /** Images d'un autre modèle (blocs Dessin et Effet), null = introuvable. */
    fun interface Ressources {
        fun images(id: String): List<Modele.Image>?
    }

    private const val PLEIN = MatrixFrame.MAX
    private const val PISTE = 28
    private const val CADRAN = 50
    private const val REPERE = 140
    private val SEMAINE = listOf("LU", "MA", "ME", "JE", "VE", "SA", "DI")

    /** Modèle jouable : rendu en direct, aperçu, et cadence (null = une image par minute). */
    fun modele(def: DefinitionBlocs, ressources: Ressources): Modele {
        val blocs = def.blocs
        val rendu: (LocalTime) -> MatrixFrame = { h -> rendre(blocs, instant(h), ressources) }
        return Modele(
            id = def.id,
            nom = def.nom,
            type = Modele.Type.STATIQUE,
            images = listOf(Modele.Image(rendu(LocalTime.now()), 1000)),
            dynamique = Modele.Dynamique.BLOCS,
            rendu = rendu,
            categorie = def.categorie,
            periodeMs = periode(blocs, ressources),
        )
    }

    /** Intervalle entre deux images : le plus court demandé par les blocs, null = la minute suffit. */
    fun periode(blocs: List<Bloc>, ressources: Ressources): Long? = blocs.mapNotNull { b ->
        when (b.type) {
            TypeBloc.SECONDES -> 1000L
            TypeBloc.TEXTE -> if (texteDefile(b)) pasTexte(b) else null
            TypeBloc.EFFET -> ressources.images(b.option(Options.MODELE_EFFET))
                ?.takeIf { it.size > 1 }
                ?.let { imgs -> (imgs.minOf { it.dureeMs } / vitesseEffet(b)).toLong().coerceAtLeast(Modele.MIN_DUREE_MS) }
            else -> null
        }
    }.minOrNull()

    fun rendre(blocs: List<Bloc>, i: Instant, ressources: Ressources): MatrixFrame {
        val px = IntArray(MatrixFrame.SIZE)
        for (b in blocs) {
            val calque = try {
                calque(b, i, ressources)
            } catch (_: Exception) {
                null // un bloc mal réglé ne doit jamais empêcher les autres de s'afficher
            } ?: continue
            poser(px, b, calque)
        }
        return MatrixFrame(px)
    }

    // ───────────────────────── Calques ─────────────────────────

    /** Petite image 0-255 d'un bloc, avant luminosité et placement. */
    class Calque(val l: Int, val h: Int) {
        val v = IntArray(l * h)
        operator fun set(x: Int, y: Int, valeur: Int) {
            if (x in 0 until l && y in 0 until h) v[y * l + x] = max(v[y * l + x], valeur.coerceIn(0, PLEIN))
        }
        operator fun get(x: Int, y: Int) = v[y * l + x]
    }

    /** Centre le calque dans le rectangle du bloc, coupe ce qui dépasse, applique la luminosité. */
    private fun poser(px: IntArray, b: Bloc, c: Calque) {
        val ox = b.x + Math.floorDiv(b.l - c.l, 2)
        val oy = b.y + Math.floorDiv(b.h - c.h, 2)
        val lum = b.niveau.coerceIn(1, 9) * PLEIN / 9f
        for (cy in 0 until c.h) for (cx in 0 until c.l) {
            val valeur = c[cx, cy]
            if (valeur <= 0) continue
            val x = ox + cx
            val y = oy + cy
            if (x < b.x || x >= b.x + b.l || y < b.y || y >= b.y + b.h) continue
            if (x !in 0 until MatrixFrame.SIDE || y !in 0 until MatrixFrame.SIDE || !MatrixFrame.isOnMatrix(x, y)) continue
            val v = (valeur * lum / PLEIN).roundToInt().coerceAtLeast(1)
            val k = y * MatrixFrame.SIDE + x
            if (v > px[k]) px[k] = v
        }
    }

    private fun calque(b: Bloc, i: Instant, r: Ressources): Calque? = when (b.type) {
        TypeBloc.HEURE -> heure(b, i.heure)
        TypeBloc.DATE -> date(b, i.date)
        TypeBloc.BATTERIE -> batterie(b, i.batterie)
        TypeBloc.SECONDES -> secondes(b, i.heure)
        TypeBloc.TEXTE -> texte(b, i.ms)
        TypeBloc.DESSIN -> r.images(b.option(Options.MODELE_DESSIN))?.firstOrNull()?.let { image(b, it.frame) }
        TypeBloc.EFFET -> r.images(b.option(Options.MODELE_EFFET))?.let { effet(b, it, i.ms) }
        TypeBloc.MINUTEUR -> minuteur(b, i.heure)
    }

    /** Texte en police 3×5, sur une ligne. */
    fun ecrire(texte: String, echelle: Int = 1): Calque {
        val cols = Police.colonnes(texte)
        val c = Calque(max(1, cols.size * echelle), Police.HAUTEUR * echelle)
        cols.forEachIndexed { x, col ->
            for (y in 0 until Police.HAUTEUR) if (col[y]) {
                for (dy in 0 until echelle) for (dx in 0 until echelle) c[x * echelle + dx, y * echelle + dy] = PLEIN
            }
        }
        return c
    }

    /** Deux textes l'un sous l'autre, séparés d'une ligne (11 LEDs de haut). */
    private fun deuxLignes(haut: String, bas: String): Calque {
        val a = ecrire(haut)
        val b = ecrire(bas)
        val c = Calque(max(a.l, b.l), a.h + 1 + b.h)
        copier(a, c, (c.l - a.l) / 2, 0)
        copier(b, c, (c.l - b.l) / 2, a.h + 1)
        return c
    }

    private fun copier(src: Calque, dst: Calque, ox: Int, oy: Int) {
        for (y in 0 until src.h) for (x in 0 until src.l) if (src[x, y] > 0) dst[ox + x, oy + y] = src[x, y]
    }

    private fun deux(n: Int) = n.toString().padStart(2, '0')

    private fun heure(b: Bloc, h: LocalTime): Calque {
        if (b.option(Options.STYLE_HEURE) == "aiguilles") return aiguilles(b, h)
        val hh = if (b.option(Options.FORMAT) == "12") (h.hour % 12).let { if (it == 0) 12 else it } else h.hour
        return when (b.option(Options.AFFICHER)) {
            "hh" -> ecrire(deux(hh))
            "mm" -> ecrire(deux(h.minute))
            else -> deuxLignes(deux(hh), deux(h.minute))
        }
    }

    private fun aiguilles(b: Bloc, h: LocalTime): Calque {
        val c = Calque(b.l, b.h)
        val cx = (b.l - 1) / 2.0
        val cy = (b.h - 1) / 2.0
        val r = (min(b.l, b.h) - 1) / 2.0
        if (b.option(Options.CADRAN) == "oui" && r >= 3) {
            for (y in 0 until b.h) for (x in 0 until b.l) {
                if (abs(hypot(x - cx, y - cy) - r) < 0.5) c[x, y] = CADRAN
            }
            for (k in 0 until 4) {
                val a = k * PI / 2
                c[(cx + sin(a) * r).roundToInt(), (cy - cos(a) * r).roundToInt()] = REPERE
            }
        }
        val minutes = h.minute + h.second / 60.0
        val heures = (h.hour % 12) + minutes / 60.0
        if (r >= 2) trait(c, cx, cy, heures / 12.0, r * 0.55)
        trait(c, cx, cy, minutes / 60.0, r * 0.9)
        c[cx.roundToInt(), cy.roundToInt()] = PLEIN
        return c
    }

    /** Aiguille partant du centre ; [tours] = angle (0 = midi, 0,25 = 3 h). */
    private fun trait(c: Calque, cx: Double, cy: Double, tours: Double, longueur: Double) {
        val a = tours * 2 * PI
        val pas = max(4, (longueur * 3).roundToInt())
        for (k in 1..pas) {
            val t = k.toDouble() / pas
            c[(cx + sin(a) * longueur * t).roundToInt(), (cy - cos(a) * longueur * t).roundToInt()] = PLEIN
        }
    }

    private fun date(b: Bloc, d: LocalDate): Calque = when (b.option(Options.FORMAT_DATE)) {
        "jourmois" -> deuxLignes(deux(d.dayOfMonth), deux(d.monthValue))
        "semaine" -> ecrire(SEMAINE[d.dayOfWeek.value - 1])
        else -> ecrire(d.dayOfMonth.toString())
    }

    private fun batterie(b: Bloc, niveau: Int): Calque {
        val f = niveau / 100.0
        val piste = b.option(Options.PISTE) == "oui"
        return when (b.option(Options.STYLE_BATTERIE)) {
            "barre" -> Calque(b.l, b.h).also { c ->
                val n = (b.l * f).roundToInt().let { if (niveau > 0) it.coerceAtLeast(1) else it }
                for (y in 0 until b.h) for (x in 0 until b.l) c[x, y] = if (x < n) PLEIN else if (piste) PISTE else 0
            }
            "chiffre" -> ecrire(niveau.toString())
            else -> anneau(b, f, piste, point = false)
        }
    }

    private fun secondes(b: Bloc, h: LocalTime): Calque =
        anneau(b, (h.second + h.nano / 1e9) / 60.0, b.option(Options.PISTE) == "oui", point = b.option(Options.STYLE_SECONDES) == "point")

    /**
     * Cercle le plus grand qui tient dans le bloc, rempli dans le sens des aiguilles d'une montre
     * à partir de midi jusqu'à la fraction [f] (ou un seul point à cette position).
     */
    private fun anneau(b: Bloc, f: Double, piste: Boolean, point: Boolean): Calque {
        val c = Calque(b.l, b.h)
        val cx = (b.l - 1) / 2.0
        val cy = (b.h - 1) / 2.0
        val r = (min(b.l, b.h) - 1) / 2.0
        val cases = ArrayList<Triple<Int, Int, Double>>()
        for (y in 0 until b.h) for (x in 0 until b.l) {
            // Le cercle extérieur de la matrice n'a pas de LED dans les coins : on suit le disque réel.
            val gx = b.x + x
            val gy = b.y + y
            if (!MatrixFrame.isOnMatrix(gx, gy)) continue
            if (abs(hypot(x - cx, y - cy) - r) >= 0.6) continue
            var a = atan2(x - cx, -(y - cy)) / (2 * PI)
            if (a < 0) a += 1.0
            cases += Triple(x, y, a)
        }
        if (cases.isEmpty()) return c
        val proche = if (point) cases.minBy { (_, _, a) -> min(abs(a - f), 1 - abs(a - f)) } else null
        for ((x, y, a) in cases) {
            val allume = if (point) proche!!.first == x && proche.second == y else a < f
            c[x, y] = if (allume) PLEIN else if (piste) PISTE else 0
        }
        return c
    }

    private fun texteDefile(b: Bloc): Boolean =
        Police.largeur(b.option(Options.TEXTE)) * echelle(b) > b.l

    private fun echelle(b: Bloc) = if (b.option(Options.TAILLE) == "grande") 2 else 1

    private fun pasTexte(b: Bloc): Long = when (b.option(Options.VITESSE_TEXTE)) {
        "lent" -> 150L
        "rapide" -> 60L
        else -> 100L
    }

    /** Texte qui tient : centré, immobile. Sinon il entre par la droite et sort par la gauche, en boucle. */
    private fun texte(b: Bloc, ms: Long): Calque {
        val texte = ecrire(b.option(Options.TEXTE).ifBlank { " " }, echelle(b))
        if (texte.l <= b.l) return texte
        val c = Calque(b.l, texte.h)
        val boucle = texte.l + b.l
        val decalage = ((ms / pasTexte(b)) % boucle).toInt()
        val debut = b.l - decalage
        for (y in 0 until texte.h) for (x in 0 until b.l) {
            val tx = x - debut
            if (tx in 0 until texte.l) c[x, y] = texte[tx, y]
        }
        return c
    }

    private fun vitesseEffet(b: Bloc): Double = b.option(Options.VITESSE_EFFET).toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0

    private fun effet(b: Bloc, images: List<Modele.Image>, ms: Long): Calque? {
        if (images.isEmpty()) return null
        val v = vitesseEffet(b)
        val total = images.sumOf { it.dureeMs }.coerceAtLeast(1)
        var t = ((ms * v).toLong() % total)
        for (img in images) {
            if (t < img.dureeMs) return image(b, img.frame)
            t -= img.dureeMs
        }
        return image(b, images.last().frame)
    }

    /** Image 13×13 d'un autre modèle, réduite dans le bloc ou découpée à sa place. */
    private fun image(b: Bloc, f: MatrixFrame): Calque {
        val c = Calque(b.l, b.h)
        val n = MatrixFrame.SIDE
        if (b.option(Options.AJUSTER) == "decouper" || (b.l == n && b.h == n)) {
            for (y in 0 until b.h) for (x in 0 until b.l) {
                val sx = b.x + x
                val sy = b.y + y
                if (sx in 0 until n && sy in 0 until n) c[x, y] = f[sx, sy]
            }
            return c
        }
        // Réduction : chaque case du bloc prend la LED la plus lumineuse de la zone qu'elle couvre
        // (un trait fin ne disparaît pas).
        for (y in 0 until b.h) for (x in 0 until b.l) {
            val x0 = floor(x * n.toDouble() / b.l).toInt()
            val x1 = max(x0 + 1, ceil((x + 1) * n.toDouble() / b.l).toInt()).coerceAtMost(n)
            val y0 = floor(y * n.toDouble() / b.h).toInt()
            val y1 = max(y0 + 1, ceil((y + 1) * n.toDouble() / b.h).toInt()).coerceAtMost(n)
            var m = 0
            for (sy in y0 until y1) for (sx in x0 until x1) m = max(m, f[sx, sy])
            c[x, y] = m
        }
        return c
    }

    private fun minuteur(b: Bloc, h: LocalTime): Calque {
        val (ch, cm) = b.option(Options.CIBLE).split(':').map { it.toInt() }
        val maintenant = h.hour * 60 + h.minute
        val restant = Math.floorMod(ch * 60 + cm - maintenant, 24 * 60)
        return when {
            restant < 60 -> ecrire(restant.toString())
            b.h >= 11 -> deuxLignes((restant / 60).toString(), deux(restant % 60))
            restant < 100 -> ecrire(restant.toString())
            else -> ecrire("${restant / 60}H")
        }
    }
}
