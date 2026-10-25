package fr.niakimbo.verso.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import fr.niakimbo.verso.R
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.launch

/** Les formats de widget proposés dans la liste des widgets du lanceur. */
enum class Format(
    val label: String,
    val layout: Int,
    val classe: Class<out VersoWidget>,
    /** Le format a une place pour le nom du modèle. */
    val aNom: Boolean,
    /** Le format a les boutons ‹ › (☰ est présent partout sauf Mini). */
    val aFleches: Boolean,
    val aListe: Boolean,
) {
    CARRE("Carré", R.layout.widget_carre, WidgetCarre::class.java, aNom = true, aFleches = true, aListe = true),
    ROND("Rond", R.layout.widget_rond, WidgetRond::class.java, aNom = false, aFleches = false, aListe = true),
    BARRE("Barre", R.layout.widget_barre, WidgetBarre::class.java, aNom = true, aFleches = true, aListe = true),
    MINI("Mini", R.layout.widget_mini, WidgetMini::class.java, aNom = false, aFleches = false, aListe = false);

    companion object {
        fun depuisClasse(nom: String?): Format? = entries.firstOrNull { it.classe.name == nom }
    }
}

/**
 * Widget écran d'accueil (VERSO_SPEC §3.2). Un fournisseur par format ; tout le dessin
 * est fait par [RenduWidget] et les mises à jour par [WidgetMaj].
 */
abstract class VersoWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as VersoApp
        val attente = goAsync()
        app.appScope.launch {
            // Forcé : le lanceur a pu perdre ses vues (redémarrage du lanceur, mise à jour de Verso).
            try { WidgetMaj.majTous(app, forcer = true) } finally { attente.finish() }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val app = context.applicationContext as VersoApp
        ids.forEach {
            app.stylesWidget.supprimer(it)
            WidgetMaj.oublier(it)
        }
    }

    /** Dernier widget de ce format retiré : on recalcule (l'alarme minute s'arrête s'il n'en reste aucun). */
    override fun onDisabled(context: Context) {
        val app = context.applicationContext as VersoApp
        val attente = goAsync()
        app.appScope.launch {
            try { WidgetMaj.majTous(app) } finally { attente.finish() }
        }
    }
}

class WidgetCarre : VersoWidget()
class WidgetRond : VersoWidget()
class WidgetBarre : VersoWidget()
class WidgetMini : VersoWidget()
