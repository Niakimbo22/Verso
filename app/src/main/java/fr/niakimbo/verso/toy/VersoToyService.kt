package fr.niakimbo.verso.toy

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.matrix.Canal
import kotlinx.coroutines.launch

/**
 * Glyph Toy « Verso » (Always-on Glyph Toy du Phone (4a) Pro).
 *
 * Le système lie ce service quand Verso est choisi dans
 * Réglages › Glyph Interface › Flip to Glyph › Always-on Glyph Toy, puis lui envoie
 * EVENT_AOD environ une fois par minute. Un Toy passe devant l'affichage des apps :
 * c'est ce qui permet au modèle de rester affiché app fermée.
 *
 * Ce service ne parle jamais au SDK directement : il bascule le [fr.niakimbo.verso.matrix.MatrixController]
 * partagé sur le canal Toy et laisse le [fr.niakimbo.verso.modele.ModelePlayer] afficher le modèle mémorisé.
 * Pas d'unInit() ici (VERSO_SPEC §2), contrairement à l'exemple de Nothing.
 */
class VersoToyService : Service() {

    private val app get() = application as VersoApp

    private var nbAod = 0
    private var imagesAuDernierPoint = 0L

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == MSG_GLYPH_TOY) {
                val event = msg.data?.getString(MSG_GLYPH_TOY_DATA)
                when (event) {
                    EVENT_AOD -> surAod()
                    null -> Unit
                    else -> app.journalToy.ajouter("Événement Toy « $event »")
                }
            }
            super.handleMessage(msg)
        }
    }
    private val messenger = Messenger(handler)

    private val ecran = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> app.journalToy.ajouter("Écran allumé · ${bilanImages()}")
                Intent.ACTION_SCREEN_OFF -> app.journalToy.ajouter("Écran éteint · ${bilanImages()}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app.journalToy.ajouter("Service Toy créé")
        imagesAuDernierPoint = app.matrix.imagesEnvoyees
        registerReceiver(ecran, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, RECEIVER_NOT_EXPORTED)
    }

    override fun onBind(intent: Intent?): IBinder {
        app.journalToy.ajouter("Lié par le système : Verso est le Glyph Toy")
        brancher()
        return messenger.binder
    }

    override fun onRebind(intent: Intent?) {
        app.journalToy.ajouter("Re-lié par le système")
        brancher()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        app.journalToy.ajouter("Délié par le système · ${bilanImages()}")
        app.toyLie.value = false
        app.matrix.setCanal(Canal.APP)
        app.player.setToyLie(false)
        // false : le système rappellera onBind() la prochaine fois (comme l'exemple officiel).
        return false
    }

    override fun onDestroy() {
        app.journalToy.ajouter("Service Toy détruit")
        try {
            unregisterReceiver(ecran)
        } catch (_: IllegalArgumentException) {
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun brancher() {
        app.toyLie.value = true
        app.matrix.setCanal(Canal.TOY)
        app.matrix.connect()
        app.player.setToyLie(true)
        app.appScope.launch {
            if (app.store.modeleActif == null) {
                app.journalToy.ajouter("Aucun modèle choisi dans Verso : matrice vide")
            }
            app.player.reprendre()
        }
    }

    private fun surAod() {
        nbAod++
        app.journalToy.ajouter("AOD n°$nbAod · ${bilanImages()}")
        app.matrix.connect()
        app.player.signalMinute()
    }

    /** Images envoyées depuis le dernier point du journal : mesure si l'animation tourne écran éteint. */
    private fun bilanImages(): String {
        val total = app.matrix.imagesEnvoyees
        val delta = total - imagesAuDernierPoint
        imagesAuDernierPoint = total
        return "$delta images depuis le point précédent"
    }

    private companion object {
        // Constantes de com.nothing.ketchum.GlyphToy, recopiées pour compiler aussi sans le SDK.
        const val MSG_GLYPH_TOY = 1
        const val MSG_GLYPH_TOY_DATA = "data"
        const val EVENT_AOD = "aod"
    }
}
