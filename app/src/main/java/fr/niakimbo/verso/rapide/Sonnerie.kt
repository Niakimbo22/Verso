package fr.niakimbo.verso.rapide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import fr.niakimbo.verso.R
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * La sonnerie du minuteur : service au premier plan qui joue fort sur le flux « alarme »
 * (passe le mode silencieux), vibre, et ouvre l'écran « Terminé » ([SonneActivity]) par-dessus tout,
 * même verrouillé. Elle s'arrête au bouton Arrêter, à +1 min, ou seule au bout de 10 min.
 *
 * Les sons « Verso » sont synthétisés (aucun fichier) : bips aigus façon Nothing, très audibles.
 */
class Sonnerie : Service() {
    private var piste: AudioTrack? = null
    private var lecteur: MediaPlayer? = null
    private var volumeAvant = -1
    private var eveil: PowerManager.WakeLock? = null
    private val principal = Handler(Looper.getMainLooper())
    private val finAuto = Runnable { (application as VersoApp).let { a -> a.appScope.launch { Minuteur.annuler(a) } } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARRETER -> {
                (application as VersoApp).let { a -> a.appScope.launch { Minuteur.arreterSonnerie(a) } }
                return START_NOT_STICKY
            }
            ACTION_PLUS -> {
                (application as VersoApp).let { a -> a.appScope.launch { Minuteur.ajouter(a) } }
                return START_NOT_STICKY
            }
        }
        val test = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        startForeground(NOTIF, notification(test), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (piste == null && lecteur == null) jouer(test)
        principal.removeCallbacks(finAuto)
        if (test) principal.postDelayed({ stopSelf() }, 4_000) else principal.postDelayed(finAuto, 10 * 60_000L)
        return START_NOT_STICKY
    }

    private fun jouer(test: Boolean) {
        eveil = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "verso:sonnerie").apply { acquire(11 * 60_000L) }
        val audio = getSystemService(AudioManager::class.java)
        if (Minuteur.volumeMax(this)) {
            volumeAvant = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            } catch (_: Exception) {
                volumeAvant = -1
            }
        }
        val attributs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        when (Minuteur.son(this)) {
            Minuteur.Son.TELEPHONE -> {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                try {
                    lecteur = MediaPlayer().apply {
                        setAudioAttributes(attributs)
                        setDataSource(this@Sonnerie, uri)
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (_: Exception) {
                    lecteur = null
                    jouerSynthese(Minuteur.Son.PULSE, attributs)
                }
            }
            else -> jouerSynthese(Minuteur.son(this), attributs)
        }
        if (Minuteur.vibre(this)) vibreur().vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 900, 700), if (test) -1 else 0),
            android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun jouerSynthese(son: Minuteur.Son, attributs: AudioAttributes) {
        val pcm = if (son == Minuteur.Son.SIRENE) sirene() else pulse()
        piste = AudioTrack.Builder()
            .setAudioAttributes(attributs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(TAUX).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .apply {
                write(pcm, 0, pcm.size)
                setLoopPoints(0, pcm.size, -1)
                play()
            }
    }

    private fun vibreur(): Vibrator = getSystemService(VibratorManager::class.java).defaultVibrator

    override fun onDestroy() {
        principal.removeCallbacksAndMessages(null)
        piste?.run { try { stop() } catch (_: Exception) {}; release() }
        piste = null
        lecteur?.run { try { stop() } catch (_: Exception) {}; release() }
        lecteur = null
        vibreur().cancel()
        if (volumeAvant >= 0) try {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, volumeAvant, 0)
        } catch (_: Exception) {
        }
        eveil?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun notification(test: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Minuteur terminé", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "La sonnerie du minuteur Verso"
                setSound(null, null) // le son est joué par Verso lui-même
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val ecran = PendingIntent.getActivity(
            this, 0, Intent(this, SonneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, Sonnerie::class.java).setAction(a), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notif_verso)
            .setContentTitle(if (test) "🔊 Essai de la sonnerie" else "⏳ Minuteur terminé")
            .setContentText("Verso")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setColor(0xFFD71921.toInt())
            .setContentIntent(ecran)
            .apply { if (!test) setFullScreenIntent(ecran, true) }
            .addAction(Notification.Action.Builder(null, "Arrêter", action(ACTION_ARRETER, 1)).build())
            .apply { if (!test) addAction(Notification.Action.Builder(null, "+1 min", action(ACTION_PLUS, 2)).build()) }
            .build()
    }

    companion object {
        private const val CANAL = "minuteur"
        private const val NOTIF = 4207
        private const val TAUX = 44_100
        private const val ACTION_ARRETER = "fr.niakimbo.verso.minuteur.ARRETER"
        private const val ACTION_PLUS = "fr.niakimbo.verso.minuteur.PLUS"
        private const val EXTRA_TEST = "test"

        fun demarrer(c: Context) {
            c.startForegroundService(Intent(c, Sonnerie::class.java))
        }

        /** Fait sonner 4 s, pour choisir le son. */
        fun tester(c: Context) {
            c.stopService(Intent(c, Sonnerie::class.java))
            c.startForegroundService(Intent(c, Sonnerie::class.java).putExtra(EXTRA_TEST, true))
        }

        fun arreter(c: Context) {
            c.stopService(Intent(c, Sonnerie::class.java))
        }

        // ----- Synthèse -----

        /** Ton carré adouci (fondamentale + harmoniques impaires) : perçant, donc très audible. */
        private fun ton(out: ShortArray, debut: Int, n: Int, freq: (Float) -> Double, volume: Double = 0.92) {
            var phase = 0.0
            val attaque = min(n / 6, TAUX / 200)
            for (i in 0 until n) {
                val t = i.toFloat() / n
                phase += 2 * PI * freq(t) / TAUX
                val s = sin(phase) + sin(3 * phase) / 3 + sin(5 * phase) / 5
                val env = when {
                    i < attaque -> i.toDouble() / attaque
                    i > n - attaque -> (n - i).toDouble() / attaque
                    else -> 1.0
                }
                val v = (s * 0.78 * env * volume * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767)
                if (debut + i < out.size) out[debut + i] = v.toShort()
            }
        }

        private fun ms(x: Int) = TAUX * x / 1000

        /** Verso Pulse : 3 bips montants, un trille, un silence (1,6 s, en boucle). */
        private fun pulse(): ShortArray {
            val out = ShortArray(ms(1600))
            var t = 0
            for (f in listOf(1760.0, 2093.0, 2637.0)) {
                ton(out, t, ms(90), { f })
                t += ms(140)
            }
            t += ms(60)
            for (k in 0 until 6) {
                ton(out, t, ms(55), { if (k % 2 == 0) 2637.0 else 3136.0 })
                t += ms(60)
            }
            return out
        }

        /** Verso Sirène : montée et descente 900 ↔ 2600 Hz (1,2 s, en boucle). */
        private fun sirene(): ShortArray {
            val out = ShortArray(ms(1200))
            ton(out, 0, out.size, { t -> 900.0 + 1700.0 * (if (t < 0.5f) t * 2 else (1 - t) * 2) })
            return out
        }
    }
}
