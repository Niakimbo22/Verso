package fr.niakimbo.verso.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import fr.niakimbo.verso.R
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dessine un widget Verso.
 *
 * La matrice est faite de trois images carrées empilées (voir outils/gen_masques_widget.py) :
 * un disque teinté « LED éteinte », l'image du modèle (ALPHA_8, teintée « LED allumée »),
 * et un masque percé d'un trou par LED, teinté couleur du disque. L'image du modèle est minuscule
 * (45×45 octets) : un widget peut donc embarquer une longue animation.
 *
 * L'animation est jouée par le lanceur lui-même (ViewFlipper) : Verso n'a pas besoin de tourner,
 * et le ViewFlipper s'arrête tout seul quand le widget n'est plus visible (app ouverte, écran éteint).
 */
object RenduWidget {
    /** Pixels par case dans l'image du modèle : au-delà d'1/3 de case, le flou du redimensionnement touche le bord des points. */
    private const val PX = 3
    /** 13 LEDs + 1 case de marge de chaque côté, comme les calques. */
    private const val CASES = MatrixFrame.SIDE + 2
    const val COTE = CASES * PX

    /** Taille d'une image du modèle dans le widget, en octets. */
    private val OCTETS_IMAGE = Bitmap.createBitmap(COTE, COTE, Bitmap.Config.ALPHA_8).allocationByteCount

    /**
     * Poids maximal des images envoyées au lanceur. Le système relaie le widget au lanceur par un
     * échange limité à ~500 Ko, et un refus à ce stade passe inaperçu : on garde de la marge.
     */
    const val BUDGET_OCTETS = 380_000

    /** En dessous, le ViewFlipper et l'œil ne suivent plus. */
    private const val INTERVALLE_MIN_MS = 40

    /** Au-delà, trop de vues dans le lanceur : on allège. */
    private const val VUES_MAX = 600

    data class Couleurs(val fond: Int, val disque: Int, val eteinte: Int, val allumee: Int, val texte: Int, val texteDiscret: Int)

    fun couleurs(style: StyleWidget): Couleurs {
        val rouge = 0xFFD71921.toInt()
        return when (style.theme) {
            StyleWidget.Theme.SOMBRE -> Couleurs(
                fond = 0xFF000000.toInt(), disque = 0xFF000000.toInt(), eteinte = 0xFF1E1E1E.toInt(),
                allumee = if (style.accent) rouge else 0xFFFFFFFF.toInt(),
                texte = 0xFFFFFFFF.toInt(), texteDiscret = 0xFF8A8A8A.toInt(),
            )
            StyleWidget.Theme.CLAIR -> Couleurs(
                fond = 0xFFF2F2F2.toInt(), disque = 0xFFF2F2F2.toInt(), eteinte = 0xFFD6D6D6.toInt(),
                allumee = if (style.accent) rouge else 0xFF111111.toInt(),
                texte = 0xFF111111.toInt(), texteDiscret = 0xFF6A6A6A.toInt(),
            )
        }
    }

    private fun masque(style: StyleWidget): Int = when (style.forme) {
        StyleWidget.Forme.ROND -> when (style.taille) {
            StyleWidget.Taille.PETIT -> R.drawable.widget_masque_rond_petit
            StyleWidget.Taille.MOYEN -> R.drawable.widget_masque_rond_moyen
            StyleWidget.Taille.GRAND -> R.drawable.widget_masque_rond_grand
        }
        StyleWidget.Forme.CARRE -> when (style.taille) {
            StyleWidget.Taille.PETIT -> R.drawable.widget_masque_carre_petit
            StyleWidget.Taille.MOYEN -> R.drawable.widget_masque_carre_moyen
            StyleWidget.Taille.GRAND -> R.drawable.widget_masque_carre_grand
        }
    }

    /** Ce que le widget va jouer : images (déjà allégées si besoin) et intervalle du ViewFlipper. */
    class Sequence(val images: List<Bitmap>, val ordre: IntArray, val intervalleMs: Int, val allegee: Boolean)

    /**
     * @param cliquable faux pour l'aperçu de l'écran de réglage (aucune action).
     * @param budget poids maximal des images ; [WidgetMaj] le baisse si le lanceur refuse.
     */
    fun construire(
        context: Context,
        format: Format,
        style: StyleWidget,
        modele: Modele?,
        cliquable: Boolean = true,
        budget: Int = BUDGET_OCTETS,
    ): RemoteViews {
        val c = couleurs(style)
        val rv = RemoteViews(context.packageName, format.layout)

        rv.setInt(android.R.id.background, "setBackgroundResource", when {
            style.fond == StyleWidget.Fond.TRANSPARENT -> 0
            style.theme == StyleWidget.Theme.CLAIR -> R.drawable.widget_fond_clair
            else -> R.drawable.widget_fond_sombre
        })

        // Matrice
        rv.setInt(R.id.disque, "setColorFilter", c.eteinte)
        rv.setImageViewResource(R.id.masque, masque(style))
        rv.setInt(R.id.masque, "setColorFilter", c.disque)

        // Le lanceur réapplique les actions sur les vues existantes quand le layout ne change pas :
        // sans ça, les images s'ajouteraient à celles de la mise à jour précédente.
        rv.removeAllViews(R.id.flipper)
        val seq = sequence(modele, style.anime, budget)
        if (seq != null) {
            rv.setInt(R.id.flipper, "setFlipInterval", seq.intervalleMs)
            for (i in seq.ordre) {
                val enfant = RemoteViews(context.packageName, R.layout.widget_image)
                enfant.setImageViewBitmap(R.id.image, seq.images[i])
                enfant.setInt(R.id.image, "setColorFilter", c.allumee)
                rv.addView(R.id.flipper, enfant)
            }
        }

        // Nom
        if (format.aNom) {
            rv.setViewVisibility(R.id.nom, if (style.nom) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.nom, (modele?.nom ?: "Éteinte").uppercase())
            rv.setTextColor(R.id.nom, if (modele == null) c.texteDiscret else c.texte)
        }

        // Boutons
        val boutons = if (style.boutons) View.VISIBLE else View.GONE
        if (format.aFleches) {
            for (id in intArrayOf(R.id.btn_prec, R.id.btn_suiv)) {
                rv.setViewVisibility(id, boutons)
                rv.setTextColor(id, c.texteDiscret)
            }
        }
        if (format.aListe) {
            rv.setViewVisibility(R.id.btn_liste, boutons)
            rv.setTextColor(R.id.btn_liste, c.texteDiscret)
        }

        if (cliquable) {
            rv.setOnClickPendingIntent(R.id.matrice, WidgetActions.intention(context, WidgetActions.SUIVANT))
            if (format.aFleches) {
                rv.setOnClickPendingIntent(R.id.btn_prec, WidgetActions.intention(context, WidgetActions.PRECEDENT))
                rv.setOnClickPendingIntent(R.id.btn_suiv, WidgetActions.intention(context, WidgetActions.SUIVANT))
            }
            if (format.aListe) {
                rv.setOnClickPendingIntent(
                    R.id.btn_liste,
                    PendingIntent.getActivity(
                        context, 0,
                        Intent(context, ChoixRapideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        }
        return rv
    }

    /** Null = matrice éteinte (rien à afficher sous le masque). */
    fun sequence(modele: Modele?, anime: Boolean, budget: Int = BUDGET_OCTETS): Sequence? {
        modele ?: return null
        // Horloge : une image, remise à jour chaque minute par WidgetMaj.
        if (modele.dynamique != null || !anime || !modele.estAnime) {
            val f = if (modele.dynamique != null) modele.frameDynamique() else modele.apercu
            return Sequence(listOf(image(f)), intArrayOf(0), 1000, allegee = false)
        }

        // Trop d'images pour le budget : on en garde une sur [pas], en cumulant les durées.
        var images = modele.images
        val maxImages = (budget / OCTETS_IMAGE).coerceAtLeast(2)
        val pas = ceil(images.size.toDouble() / maxImages).toInt().coerceAtLeast(1)
        if (pas > 1) {
            images = images.chunked(pas).map { groupe -> Modele.Image(groupe.first().frame, groupe.sumOf { it.dureeMs }) }
        }

        // Le ViewFlipper n'a qu'un intervalle : une image longue est répétée plusieurs fois.
        var intervalle = images.map { it.dureeMs.toInt() }.reduce(::pgcd)
        if (intervalle < INTERVALLE_MIN_MS) intervalle = max(INTERVALLE_MIN_MS, images.minOf { it.dureeMs }.toInt())
        var repetitions = images.map { (it.dureeMs.toDouble() / intervalle).roundToInt().coerceAtLeast(1) }
        if (repetitions.sum() > VUES_MAX) {
            val facteur = ceil(repetitions.sum().toDouble() / VUES_MAX).toInt()
            intervalle *= facteur
            repetitions = images.map { (it.dureeMs.toDouble() / intervalle).roundToInt().coerceAtLeast(1) }
        }

        val bitmaps = images.map { image(it.frame) }
        val ordre = repetitions.flatMapIndexed { i, n -> List(n) { i } }.toIntArray()
        return Sequence(bitmaps, ordre, intervalle, allegee = pas > 1)
    }

    private tailrec fun pgcd(a: Int, b: Int): Int = if (b == 0) a else pgcd(b, a % b)

    /**
     * Image du modèle : chaque LED = un bloc de PX×PX pixels dont l'opacité suit sa luminosité
     * (même courbe que l'aperçu de l'app), LEDs éteintes transparentes. Rien ne dépasse du disque
     * des calques (rayon 7 cases) pour que le mode « fond transparent » reste propre.
     */
    fun image(frame: MatrixFrame): Bitmap {
        val bmp = Bitmap.createBitmap(COTE, COTE, Bitmap.Config.ALPHA_8)
        val ligne = bmp.rowBytes
        val octets = ByteArray(ligne * COTE)
        val centre = COTE / 2f
        val rayonMax = 6.95f * PX
        for (y in 0 until MatrixFrame.SIDE) {
            for (x in 0 until MatrixFrame.SIDE) {
                val v = frame[x, y]
                if (v <= 0 || !MatrixFrame.isOnMatrix(x, y)) continue
                val a = ((0.3f + 0.7f * v / MatrixFrame.MAX) * 255).roundToInt().coerceIn(0, 255).toByte()
                for (py in 0 until PX) for (px in 0 until PX) {
                    val gx = (x + 1) * PX + px
                    val gy = (y + 1) * PX + py
                    if (hypot(gx + 0.5f - centre, gy + 0.5f - centre) > rayonMax) continue
                    octets[gy * ligne + gx] = a
                }
            }
        }
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(octets))
        return bmp
    }
}
