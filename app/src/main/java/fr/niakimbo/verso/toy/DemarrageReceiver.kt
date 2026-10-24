package fr.niakimbo.verso.toy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Démarrage du téléphone (ou mise à jour de Verso).
 *
 * Constat du 24/10 : après un redémarrage, le système n'a pas relancé le Glyph Toy de Verso
 * tout seul, et la matrice est restée éteinte. Ici on note le démarrage dans le journal,
 * puis on surveille : si le Toy n'est toujours pas lié, on tente l'affichage par le canal app
 * (au cas où il tienne quand aucun Toy n'est actif) et on le note, pour comprendre qui réveille quoi.
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

        app.appScope.launch {
            for (attente in ATTENTES_S) {
                delay(attente * 1000L)
                if (app.toyLie.value) return@launch
                app.journalToy.ajouter("$quoi + ${attente}s : Toy pas encore lié par le système")
            }
            if (app.store.modeleActif != null) {
                app.journalToy.ajouter("Essai : affichage du modèle par le canal app (sans Toy)")
                app.player.reprendre()
            }
        }
    }

    private companion object {
        val ATTENTES_S = longArrayOf(15, 45)
    }
}
