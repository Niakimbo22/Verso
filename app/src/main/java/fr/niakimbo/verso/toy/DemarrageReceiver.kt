package fr.niakimbo.verso.toy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.matrix.Canal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Démarrage du téléphone (ou mise à jour de Verso).
 *
 * Constat du 24/10 : après un redémarrage, le système n'a pas relancé le Glyph Toy de Verso
 * tout seul, et la matrice est restée éteinte. Ici on note le démarrage dans le journal,
 * puis on surveille : si le Toy n'est toujours pas lié, on tente l'affichage par le canal app
 * (au cas où il tienne quand aucun Toy n'est actif) et on le note, pour comprendre qui réveille quoi.
 *
 * L'essai n'a lieu que si le Toy n'a été lié à aucun moment autour du démarrage : la liaison
 * annule l'essai en attente ([VersoApp.essaiDemarrage]), et une liaison récente (même juste
 * avant l'événement, ou suivie d'un déliement passager) suffit à l'écarter.
 */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val quoi = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "Téléphone démarré"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "Verso mis à jour"
            else -> return
        }
        app.journalToy.ajouter(quoi)
        app.matrix.connect()
        // Minuteur en cours : son alarme ne survit pas au redémarrage, on la reprogramme.
        app.appScope.launch { fr.niakimbo.verso.rapide.Minuteur.reprendre(app) }

        val debut = System.currentTimeMillis()
        app.essaiDemarrage?.cancel()
        app.essaiDemarrage = app.appScope.launch {
            for (attente in ATTENTES_S) {
                delay(attente * 1000L)
                if (toyRelie(app, debut)) return@launch
                app.journalToy.ajouter("$quoi + ${attente}s : Toy pas encore lié par le système")
            }
            if (app.store.modeleActif == null || toyRelie(app, debut) || app.matrix.canal.value == Canal.TOY) return@launch
            app.journalToy.ajouter("Essai : affichage du modèle par le canal app (sans Toy)")
            app.player.reprendre()
        }
    }

    /** Vrai si le Toy est lié, ou l'a été depuis peu avant l'événement (il va être relancé). */
    private fun toyRelie(app: VersoApp, debut: Long): Boolean =
        app.toyLie.value || app.store.derniereLiaison >= debut - MARGE_LIAISON_MS

    private companion object {
        val ATTENTES_S = longArrayOf(15, 45)

        /** Une liaison du Toy dans les 2 min avant l'événement compte aussi (mise à jour de Verso, redémarrage rapide). */
        const val MARGE_LIAISON_MS = 120_000L
    }
}
