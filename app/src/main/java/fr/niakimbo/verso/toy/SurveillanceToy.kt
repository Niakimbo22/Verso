package fr.niakimbo.verso.toy

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import fr.niakimbo.verso.MainActivity
import fr.niakimbo.verso.R
import fr.niakimbo.verso.VersoApp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Le système délie parfois le Glyph Toy de Verso sans qu'on touche à rien (constaté le 25/10 à 01:44).
 *
 * - Au moment où ça arrive, on note l'état du téléphone (écran, économie de batterie, Ne pas déranger,
 *   batterie, AOD) : le journal dit ensuite ce qui a pu le provoquer.
 * - 2 minutes plus tard, si le système ne l'a pas relancé, une notification le signale ;
 *   un tap ouvre directement le choix du Glyph Toy. Elle disparaît dès que le Toy est relié.
 */
class SurveillanceToy : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VERIFIER) return
        val app = context.applicationContext as VersoApp
        val store = app.store
        val toujoursDelie = !app.toyLie.value && store.dernierDeliement >= store.derniereLiaison
        if (!toujoursDelie) return
        app.journalToy.ajouter("Toy toujours arrêté 2 min après : notification envoyée")
        notifier(context, store.dernierDeliement, store.contexteDeliement)
    }

    companion object {
        private const val ACTION_VERIFIER = "fr.niakimbo.verso.toy.VERIFIER"
        private const val CANAL = "toy"
        private const val ID_NOTIF = 1
        private const val DELAI_MS = 2 * 60_000L
        const val EXTRA_CHOISIR_TOY = "choisir_toy"

        /** Appelé quand le système délie le Toy : on note le contexte et on programme la vérification. */
        fun surDeliement(context: Context): String {
            val app = context.applicationContext as VersoApp
            val contexte = contexte(context)
            app.store.contexteDeliement = contexte.resume
            val am = context.getSystemService(AlarmManager::class.java)
            am?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + DELAI_MS, pendingVerifier(context))
            return contexte.detail
        }

        /** Toy relié : plus rien à signaler. */
        fun surLiaison(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingVerifier(context))
            context.getSystemService(NotificationManager::class.java)?.cancel(ID_NOTIF)
        }

        private fun pendingVerifier(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, SurveillanceToy::class.java).setAction(ACTION_VERIFIER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        class Contexte(val detail: String, val resume: String?)

        /** État du téléphone au moment du déliement. [Contexte.resume] = cause probable, en clair. */
        fun contexte(context: Context): Contexte {
            val pm = context.getSystemService(PowerManager::class.java)
            val nm = context.getSystemService(NotificationManager::class.java)
            val ecranAllume = pm?.isInteractive == true
            val economie = pm?.isPowerSaveMode == true
            val npd = nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            val batterie = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val niveau = batterie?.let {
                val n = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (n >= 0 && s > 0) n * 100 / s else null
            }
            val enCharge = (batterie?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val aod = try {
                Settings.Secure.getInt(context.contentResolver, "doze_always_on", -1)
            } catch (_: Exception) {
                -1
            }

            val detail = buildList {
                add(if (ecranAllume) "écran allumé" else "écran éteint")
                if (economie) add("ÉCONOMIE DE BATTERIE")
                if (npd) add("NE PAS DÉRANGER")
                niveau?.let { add("batterie $it %" + if (enCharge) " (en charge)" else "") }
                when (aod) {
                    0 -> add("AOD désactivé")
                    1 -> add("AOD activé")
                }
            }.joinToString(" · ")

            val resume = when {
                economie -> "économie de batterie activée"
                npd -> "Ne pas déranger ou mode Coucher actif"
                aod == 0 -> "écran toujours allumé (AOD) désactivé"
                else -> null
            }
            return Contexte(detail, resume)
        }

        private fun notifier(context: Context, depuis: Long, cause: String?) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (!nm.areNotificationsEnabled()) return
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Glyph Toy arrêté", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Prévient quand le système arrête l'affichage permanent de Verso."
                },
            )
            val heure = Instant.ofEpochMilli(depuis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            val ouvrir = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_CHOISIR_TOY, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val texte = "Coupé par le système à $heure" + (cause?.let { " ($it)" } ?: "") + ". Touche pour le remettre."
            val notif = android.app.Notification.Builder(context, CANAL)
                .setSmallIcon(R.drawable.ic_notif_verso)
                .setContentTitle("La matrice ne s'affiche plus")
                .setContentText(texte)
                .setStyle(android.app.Notification.BigTextStyle().bigText(texte))
                .setContentIntent(ouvrir)
                .setAutoCancel(true)
                .build()
            nm.notify(ID_NOTIF, notif)
        }
    }
}
