package fr.niakimbo.verso.rapide

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Navigation
import fr.niakimbo.verso.reglage.Personnalisation
import fr.niakimbo.verso.reglage.ReglagesStore
import fr.niakimbo.verso.widget.WidgetActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Actions d'un geste, partagées par le mini-lecteur, la tuile des réglages rapides et les raccourcis
 * de l'icône. Toutes passent par [fr.niakimbo.verso.modele.ModelePlayer] comme un tap dans l'app
 * (choix « à la main » pour les règles automatiques).
 */
object ActionsRapides {

    /** Modèle précédent (-1) ou suivant (+1) : favoris, ou toute la bibliothèque (comme le widget). */
    suspend fun changer(app: VersoApp, pas: Int) {
        val id = Navigation.voisin(WidgetActions.ordreDefilement(app), app.store.modeleActif, pas) ?: return
        app.matrix.connect()
        app.catalogue.resoudre(id)?.let(app.player::jouer)
    }

    /** Un favori au hasard (toute la bibliothèque s'il n'y en a pas), jamais celui déjà affiché. */
    suspend fun aleatoire(app: VersoApp) {
        val liste = WidgetActions.ordreDefilement(app)
        val id = liste.filter { it != app.store.modeleActif }.randomOrNull() ?: return
        app.matrix.connect()
        app.catalogue.resoudre(id)?.let(app.player::jouer)
    }

    /** Éteint, ou rallume le dernier modèle éteint. */
    suspend fun basculer(app: VersoApp) {
        if (app.store.modeleActif != null) {
            app.player.eteindre()
            return
        }
        val id = app.store.dernierModele ?: WidgetActions.ordreDefilement(app).firstOrNull() ?: return
        app.matrix.connect()
        app.catalogue.resoudre(id)?.let(app.player::jouer)
    }

    /** Luminosité (10-100 %) du modèle affiché : enregistrée dans ses réglages et appliquée tout de suite. */
    suspend fun luminosite(app: VersoApp, pourcent: Float) {
        val id = app.store.modeleActif ?: return
        val choix = app.reglages.lire(id)
        app.reglages.ecrire(id, ReglagesStore.Choix(choix.version, choix.valeurs.avec(Personnalisation.LUMINOSITE.cle, pourcent)))
        app.player.rafraichir(id)
    }

    fun luminositeActuelle(app: VersoApp): Float =
        app.store.modeleActif?.let { app.reglages.lire(it).valeurs.f(Personnalisation.LUMINOSITE) } ?: 100f

    // --- Minuteur de veille ---

    private val _finVeille = MutableStateFlow(0L)

    /** Heure (ms epoch) où la matrice s'éteindra, 0 = pas de minuteur. */
    val finVeille: StateFlow<Long> = _finVeille

    fun lireVeille(app: VersoApp) {
        val fin = app.getSharedPreferences("verso", Context.MODE_PRIVATE).getLong(CLE_VEILLE, 0L)
        _finVeille.value = if (fin > System.currentTimeMillis()) fin else 0L
    }

    /** Éteindre la matrice dans [minutes] (0 = annuler). */
    fun veille(app: VersoApp, minutes: Int) {
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(app, 1, Intent(app, MinuteurVeille::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fin = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        app.getSharedPreferences("verso", Context.MODE_PRIVATE).edit().putLong(CLE_VEILLE, fin).apply()
        _finVeille.value = fin
        if (fin == 0L) {
            am.cancel(pi)
            return
        }
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fin, pi)
        app.journalToy.ajouter("Minuteur de veille : matrice éteinte dans $minutes min")
    }

    internal fun veilleFinie(app: VersoApp) {
        app.getSharedPreferences("verso", Context.MODE_PRIVATE).edit().putLong(CLE_VEILLE, 0L).apply()
        _finVeille.value = 0L
    }

    private const val CLE_VEILLE = "fin_veille"
}

/** Fin du minuteur de veille : la matrice s'éteint (même app fermée). */
class MinuteurVeille : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val fin = goAsync()
        app.appScope.launch {
            try {
                ActionsRapides.veilleFinie(app)
                app.journalToy.ajouter("Minuteur de veille terminé : matrice éteinte")
                app.player.eteindre()
            } finally {
                fin.finish()
            }
        }
    }
}
