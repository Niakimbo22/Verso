package fr.niakimbo.verso.widget.perso

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import fr.niakimbo.verso.R
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.widget.ChoixRapideActivity
import fr.niakimbo.verso.widget.RenduWidget
import fr.niakimbo.verso.widget.StyleWidget
import fr.niakimbo.verso.widget.WidgetActions

/**
 * Dessine un widget perso : chaque bloc est une petite vue ajoutée dans la colonne (addView).
 * L'heure et la date sont des TextClock : le téléphone les tient à jour seul, sans réveiller Verso.
 * La matrice reprend les calques des autres widgets (animation jouée par le lanceur).
 */
object RenduPerso {

    /** Ce qui change d'un instant à l'autre et que le widget affiche. */
    data class Etat(
        val modele: Modele?,
        val batterie: Int,
        val enCharge: Boolean,
        val regleAuto: String?,
        val glyphEteint: Boolean,
        /** Le Glyph Toy de Verso est lié (le modèle reste au dos app fermée). */
        val toyLie: Boolean = false,
    )

    fun construire(context: Context, w: WidgetPerso, etat: Etat, cliquable: Boolean = true, budget: Int = RenduWidget.BUDGET_OCTETS): RemoteViews {
        val clair = w.theme == WidgetPerso.Theme.CLAIR
        val ligne = w.disposition == WidgetPerso.Disposition.LIGNE
        val rv = RemoteViews(context.packageName, if (ligne) R.layout.widget_perso_ligne else R.layout.widget_perso_colonne)
        rv.setInt(android.R.id.background, "setBackgroundResource", when (w.fond) {
            WidgetPerso.Fond.TRANSPARENT -> 0
            WidgetPerso.Fond.VERRE -> R.drawable.widget_fond_verre
            WidgetPerso.Fond.PLEIN -> if (clair) R.drawable.widget_fond_clair else R.drawable.widget_fond_sombre
        })

        val gravite = when (w.alignement) {
            WidgetPerso.Alignement.GAUCHE -> Gravity.START
            WidgetPerso.Alignement.CENTRE -> Gravity.CENTER_HORIZONTAL
            WidgetPerso.Alignement.DROITE -> Gravity.END
        }
        rv.setInt(R.id.blocs, "setGravity", gravite or Gravity.CENTER_VERTICAL)
        // Mises à jour successives : on repart de zéro (sinon les blocs s'ajouteraient aux anciens).
        rv.removeAllViews(R.id.blocs)
        if (ligne) rv.removeAllViews(R.id.zone_matrice)

        val couleurNormale = if (clair) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        val couleurDiscrete = if (clair) 0xFF6A6A6A.toInt() else 0xFF9A9A9A.toInt()
        fun couleur(b: WidgetPerso.Bloc) = when (b.couleur) {
            WidgetPerso.Couleur.NORMALE -> couleurNormale
            WidgetPerso.Couleur.ROUGE -> 0xFFD71921.toInt()
            WidgetPerso.Couleur.DISCRETE -> couleurDiscrete
            WidgetPerso.Couleur.PRINCIPALE -> w.accent ?: couleurNormale
        }

        var matricePosee = false
        for (b in w.blocs) {
            val enfant: RemoteViews? = when (b.type) {
                WidgetPerso.Type.MATRICE -> {
                    // Une seule matrice par widget (poids des images pour le lanceur).
                    if (matricePosee) null else {
                        matricePosee = true
                        matrice(context, w, b, etat.modele, cliquable, budget)
                    }
                }
                WidgetPerso.Type.HEURE -> horloge(context, R.layout.perso_heure, couleur(b), taille(b, 24f, 36f, 56f, 84f), gravite)
                WidgetPerso.Type.DATE -> horloge(context, R.layout.perso_date, couleur(b), taille(b, 11f, 13f, 16f, 20f), gravite)
                WidgetPerso.Type.NOM -> texte(context, (etat.modele?.nom ?: "Matrice éteinte").uppercase(), couleur(b), taille(b, 10f, 13f, 17f, 22f), gravite)
                WidgetPerso.Type.BATTERIE -> texte(context, (if (etat.enCharge) "⚡" else "") + "${etat.batterie}%", couleur(b), taille(b, 18f, 26f, 34f, 52f), gravite)
                WidgetPerso.Type.AUTO -> etat.regleAuto?.let { texte(context, "AUTO · ${it.uppercase()}", couleur(b), taille(b, 9f, 11f, 13f, 15f), gravite) }
                WidgetPerso.Type.TEXTE -> b.texte.takeIf { it.isNotBlank() }?.let { texte(context, it, couleur(b), taille(b, 11f, 15f, 20f, 28f), gravite) }
                WidgetPerso.Type.BOUTONS -> boutons(context, couleur(b), etat, cliquable, gravite)
                WidgetPerso.Type.ESPACE -> RemoteViews(context.packageName, R.layout.perso_espace)
                WidgetPerso.Type.CHRONO -> chrono(context, couleur(b), taille(b, 20f, 28f, 40f, 60f), gravite)
                WidgetPerso.Type.BOUTONS_CHRONO -> boutonsChrono(context, couleur(b), cliquable, gravite)
                WidgetPerso.Type.GLYPH -> {
                    val (t, c) = when {
                        etat.glyphEteint -> "○ GLYPH ÉTEINT" to 0xFFD71921.toInt()
                        etat.modele == null -> "○ MATRICE ÉTEINTE" to couleurDiscrete
                        etat.toyLie -> "● AU DOS · TOY ACTIF" to 0xFF3DDC84.toInt()
                        else -> "● GLYPH ALLUMÉ" to 0xFF3DDC84.toInt()
                    }
                    texte(context, t, c, taille(b, 9f, 11f, 13f, 15f), gravite)
                }
            }
            enfant ?: continue
            if (ligne && b.type == WidgetPerso.Type.MATRICE) rv.addView(R.id.zone_matrice, enfant) else rv.addView(R.id.blocs, enfant)
        }
        if (ligne && !matricePosee) rv.setViewVisibility(R.id.zone_matrice, android.view.View.GONE)
        return rv
    }

    private fun taille(b: WidgetPerso.Bloc, s: Float, m: Float, l: Float, xl: Float) = when (b.taille) {
        WidgetPerso.Taille.S -> s; WidgetPerso.Taille.M -> m; WidgetPerso.Taille.L -> l; WidgetPerso.Taille.XL -> xl
    }

    private fun texte(context: Context, t: String, couleur: Int, sp: Float, gravite: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.perso_texte).apply {
            setTextViewText(R.id.texte, t)
            setTextColor(R.id.texte, couleur)
            setTextViewTextSize(R.id.texte, TypedValue.COMPLEX_UNIT_SP, sp)
            setInt(R.id.texte, "setGravity", gravite)
        }

    private fun horloge(context: Context, layout: Int, couleur: Int, sp: Float, gravite: Int): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setTextColor(R.id.horloge, couleur)
            setTextViewTextSize(R.id.horloge, TypedValue.COMPLEX_UNIT_SP, sp)
            setInt(R.id.horloge, "setGravity", gravite)
        }

    private fun matrice(context: Context, w: WidgetPerso, b: WidgetPerso.Bloc, modele: Modele?, cliquable: Boolean, budget: Int): RemoteViews {
        val layout = when (b.taille) {
            WidgetPerso.Taille.S -> R.layout.perso_matrice_s
            WidgetPerso.Taille.M -> R.layout.perso_matrice_m
            WidgetPerso.Taille.L -> R.layout.perso_matrice_l
            WidgetPerso.Taille.XL -> R.layout.perso_matrice_xl
        }
        // On reprend le style des widgets classiques pour les couleurs et le masque des points.
        val style = StyleWidget(
            theme = if (w.theme == WidgetPerso.Theme.CLAIR) StyleWidget.Theme.CLAIR else StyleWidget.Theme.SOMBRE,
            fond = StyleWidget.Fond.TRANSPARENT,
            forme = if (w.pointsCarres) StyleWidget.Forme.CARRE else StyleWidget.Forme.ROND,
            taille = StyleWidget.Taille.MOYEN,
            accent = w.ledsRouges,
        )
        val c0 = RenduWidget.couleurs(style)
        val c = c0.copy(allumee = w.accent ?: c0.allumee)
        // Sur un fond « Aucun » ou « Verre », le disque et le masque prennent une teinte neutre.
        val disque = if (w.fond == WidgetPerso.Fond.PLEIN) c.disque else if (w.theme == WidgetPerso.Theme.CLAIR) 0xFFF2F2F2.toInt() else 0xFF000000.toInt()
        val rv = RemoteViews(context.packageName, layout)
        // LEDs éteintes cachées : le disque prend la couleur du masque, seules les LEDs allumées se voient.
        rv.setInt(R.id.disque, "setColorFilter", if (w.ledsEteintes) c.eteinte else disque)
        rv.setImageViewResource(R.id.masque, RenduWidget.masque(style))
        rv.setInt(R.id.masque, "setColorFilter", disque)
        rv.removeAllViews(R.id.flipper)
        RenduWidget.sequence(modele, anime = w.anime, budget = budget)?.let { seq ->
            rv.setInt(R.id.flipper, "setFlipInterval", seq.intervalleMs)
            for (i in seq.ordre) {
                val img = RemoteViews(context.packageName, R.layout.widget_image)
                img.setImageViewBitmap(R.id.image, seq.images[i])
                img.setInt(R.id.image, "setColorFilter", c.allumee)
                rv.addView(R.id.flipper, img)
            }
        }
        if (cliquable) rv.setOnClickPendingIntent(R.id.matrice, actionTap(context, w))
        return rv
    }

    /** Tap sur la matrice, selon le réglage du widget. */
    private fun actionTap(context: Context, w: WidgetPerso): PendingIntent = when (w.tap) {
        WidgetPerso.Tap.SUIVANT -> WidgetActions.intention(context, WidgetActions.SUIVANT)
        WidgetPerso.Tap.ONOFF -> WidgetActions.intention(context, WidgetActions.ONOFF)
        WidgetPerso.Tap.AFFICHER -> w.modeleFixe?.let { WidgetActions.afficher(context, it) }
            ?: fenetreChoix(context)
        WidgetPerso.Tap.VERSO -> PendingIntent.getActivity(
            context, 1,
            Intent(context, fr.niakimbo.verso.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        WidgetPerso.Tap.CHOISIR -> fenetreChoix(context)
    }

    private fun fenetreChoix(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, ChoixRapideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Chronometer : le lanceur fait tourner le temps lui-même, sans réveiller Verso. */
    private fun chrono(context: Context, couleur: Int, sp: Float, gravite: Int): RemoteViews {
        val e = fr.niakimbo.verso.rapide.Chrono.lire(context)
        return RemoteViews(context.packageName, R.layout.perso_chrono).apply {
            setChronometer(R.id.chrono, fr.niakimbo.verso.rapide.Chrono.baseWidget(e), null, e.enMarche)
            setTextColor(R.id.chrono, if (e.enMarche || e.aZero) couleur else 0xFFD71921.toInt())
            setTextViewTextSize(R.id.chrono, TypedValue.COMPLEX_UNIT_SP, sp)
            setInt(R.id.chrono, "setGravity", gravite)
        }
    }

    private fun boutonsChrono(context: Context, couleur: Int, cliquable: Boolean, gravite: Int): RemoteViews {
        val e = fr.niakimbo.verso.rapide.Chrono.lire(context)
        return RemoteViews(context.packageName, R.layout.perso_boutons_chrono).apply {
            setInt(R.id.rangee, "setGravity", gravite or Gravity.CENTER_VERTICAL)
            setImageViewResource(R.id.btn_chrono, if (e.enMarche) R.drawable.ic_w_pause else R.drawable.ic_w_lecture)
            setInt(R.id.btn_chrono, "setColorFilter", if (e.enMarche) couleur else 0xFFD71921.toInt())
            setInt(R.id.btn_zero, "setColorFilter", couleur)
            setViewVisibility(R.id.btn_zero, if (e.aZero) android.view.View.GONE else android.view.View.VISIBLE)
            if (cliquable) {
                setOnClickPendingIntent(R.id.btn_chrono, WidgetActions.intention(context, WidgetActions.CHRONO))
                setOnClickPendingIntent(R.id.btn_zero, WidgetActions.intention(context, WidgetActions.CHRONO_ZERO))
            }
        }
    }

    private fun boutons(context: Context, couleur: Int, etat: Etat, cliquable: Boolean, gravite: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.perso_boutons).apply {
            setInt(R.id.rangee, "setGravity", gravite or Gravity.CENTER_VERTICAL)
            // Icônes dessinées (et pas les caractères ‹ ⏻ ›) : la police du lanceur n'a pas « ⏻ ».
            for (id in intArrayOf(R.id.btn_prec, R.id.btn_suiv)) setInt(id, "setColorFilter", couleur)
            val eteint = etat.modele == null || etat.glyphEteint
            setImageViewResource(R.id.btn_onoff, if (eteint) R.drawable.ic_w_lecture else R.drawable.ic_w_marche)
            setInt(R.id.btn_onoff, "setColorFilter", if (eteint) 0xFFD71921.toInt() else couleur)
            if (cliquable) {
                setOnClickPendingIntent(R.id.btn_prec, WidgetActions.intention(context, WidgetActions.PRECEDENT))
                setOnClickPendingIntent(R.id.btn_suiv, WidgetActions.intention(context, WidgetActions.SUIVANT))
                setOnClickPendingIntent(R.id.btn_onoff, WidgetActions.intention(context, WidgetActions.ONOFF))
            }
        }
}
