package fr.niakimbo.verso.widget.perso

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.widget.WidgetMaj
import kotlinx.coroutines.launch

/** « Verso · Perso » : un widget dessiné d'après un des widgets créés dans Verso (Mes widgets). */
class WidgetPersoProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as VersoApp
        val attente = goAsync()
        app.appScope.launch {
            try { WidgetMaj.majTous(app, forcer = true) } finally { attente.finish() }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val app = context.applicationContext as VersoApp
        ids.forEach {
            app.widgetsPerso.oublier(it)
            WidgetMaj.oublier(it)
        }
    }
}
