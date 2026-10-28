package fr.niakimbo.verso.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Navigation
import kotlinx.coroutines.launch

/** Taps sur le widget (suivant, précédent) et alarme « minute » de l'horloge. */
class WidgetActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val action = intent.action ?: return
        val attente = goAsync()
        app.appScope.launch {
            try {
                when (action) {
                    SUIVANT -> changer(app, +1)
                    PRECEDENT -> changer(app, -1)
                    ONOFF -> basculer(app)
                    MINUTE -> WidgetMaj.majTous(app)
                    AFFICHER -> intent.getStringExtra(EXTRA_MODELE)?.let { afficher(app, it) }
                    CHRONO -> fr.niakimbo.verso.rapide.Chrono.basculer(app)
                    CHRONO_ZERO -> fr.niakimbo.verso.rapide.Chrono.remettre(app)
                }
            } finally {
                attente.finish()
            }
        }
    }

    private suspend fun changer(app: VersoApp, pas: Int) {
        val suivant = Navigation.voisin(ordreDefilement(app), app.store.modeleActif, pas) ?: return
        val modele = app.catalogue.resoudre(suivant) ?: return
        app.matrix.connect()
        app.player.jouer(modele)
        WidgetMaj.majTous(app)
    }

    /** Widget avec un modèle épinglé : l'afficher au dos (choix « à la main », comme un tap dans l'app). */
    private suspend fun afficher(app: VersoApp, id: String) {
        val modele = app.catalogue.resoudre(id) ?: return
        app.matrix.connect()
        app.player.jouer(modele)
        WidgetMaj.majTous(app)
    }

    /** ⏻ : le vrai bouton Glyph (voir ActionsRapides.basculer). */
    private suspend fun basculer(app: VersoApp) {
        fr.niakimbo.verso.rapide.ActionsRapides.basculer(app)
        WidgetMaj.majTous(app)
    }

    companion object {
        const val ONOFF = "fr.niakimbo.verso.widget.ONOFF"
        const val SUIVANT = "fr.niakimbo.verso.widget.SUIVANT"
        const val PRECEDENT = "fr.niakimbo.verso.widget.PRECEDENT"
        const val MINUTE = "fr.niakimbo.verso.widget.MINUTE"
        const val AFFICHER = "fr.niakimbo.verso.widget.AFFICHER"
        const val CHRONO = "fr.niakimbo.verso.widget.CHRONO"
        const val CHRONO_ZERO = "fr.niakimbo.verso.widget.CHRONO_ZERO"
        private const val EXTRA_MODELE = "modele"

        /** Une intention par modèle (l'adresse les distingue : sinon Android les confondrait). */
        fun afficher(context: Context, modele: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            ("afficher:$modele").hashCode(),
            Intent(context, WidgetActions::class.java).setAction(AFFICHER)
                .setData(android.net.Uri.parse("verso://afficher/${android.net.Uri.encode(modele)}"))
                .putExtra(EXTRA_MODELE, modele),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun intention(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, WidgetActions::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Les favoris dans l'ordre, ou toute la bibliothèque s'il n'y en a aucun. */
        suspend fun ordreDefilement(app: VersoApp): List<String> {
            val visibles = app.catalogue.contenu().entrees.filter { !it.cache }.map { it.id }
            return app.favoris.ids.value.filter { it in visibles }.ifEmpty { visibles }
        }
    }
}
