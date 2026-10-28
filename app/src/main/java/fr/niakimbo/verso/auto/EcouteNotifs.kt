package fr.niakimbo.verso.auto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * « Accès aux notifications » (étape 9, partie 2) : un seul service pour deux déclencheurs.
 * - Notification d'une app : le modèle d'une règle « flash » s'affiche quelques secondes, puis fondu.
 * - Musique en cours : les sessions média des apps (Spotify, YouTube Music…) ne sont lisibles
 *   qu'avec cet accès ([MediaSessionManager.getActiveSessions]).
 *
 * Rien ne quitte le téléphone : Verso ne lit que le nom de l'app, jamais le contenu des notifications.
 */
class EcouteNotifs : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _actif.value = true
        Musique.brancher(applicationContext)
    }

    override fun onListenerDisconnected() {
        _actif.value = false
        Musique.debrancher()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, ranking: RankingMap?) {
        sbn ?: return
        if (!aFlasher(sbn, ranking)) return
        (application as VersoApp).moteurRegles.notification(sbn.packageName)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = onNotificationPosted(sbn, null)

    /**
     * Une vraie nouvelle notification ? Pas Verso lui-même, pas une notification permanente
     * (musique, téléchargement), pas un résumé de groupe, pas une simple mise à jour silencieuse,
     * pas une notification discrète (« silencieuse » dans les réglages de l'app).
     */
    private fun aFlasher(sbn: StatusBarNotification, ranking: RankingMap?): Boolean {
        if (sbn.packageName == packageName) return false
        val n = sbn.notification
        if (sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val r = Ranking()
        if (ranking != null && ranking.getRanking(sbn.key, r) && r.isAmbient) return false
        // Même notification mise à jour (téléchargement, compteur…) : un seul flash.
        val maintenant = SystemClock.elapsedRealtime()
        val avant = vues[sbn.key]
        vues[sbn.key] = maintenant
        if (vues.size > 200) vues.entries.removeAll { maintenant - it.value > 60_000 }
        if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && avant != null) return false
        if (avant != null && maintenant - avant < 3_000) return false
        return true
    }

    private val vues = HashMap<String, Long>()

    companion object {
        private val _actif = MutableStateFlow(false)

        /** Le service est relié (accès accordé) ? */
        val actif: StateFlow<Boolean> = _actif

        fun composant(context: Context) = ComponentName(context, EcouteNotifs::class.java)

        /** Accès aux notifications accordé à Verso dans les réglages ? */
        fun autorise(context: Context): Boolean {
            val nom = composant(context).flattenToString()
            val liste = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            return liste.split(':').any { it.equals(nom, ignoreCase = true) }
        }

        /** Ouvre directement le réglage « Accès aux notifications » de Verso (ou la liste, à défaut). */
        fun ouvrirReglage(context: Context) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, composant(context).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(detail)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

/** Ce que joue une app en ce moment. [positionMs] a été lue à [lueA] ([SystemClock.elapsedRealtime]). */
data class EtatMusique(
    val pkg: String,
    val enLecture: Boolean,
    val titre: String?,
    val positionMs: Long,
    val dureeMs: Long,
    val vitesse: Float,
    val lueA: Long,
) {
    /** Position estimée maintenant (la session ne prévient pas à chaque seconde). */
    fun positionMaintenant(): Long {
        if (!enLecture) return positionMs
        val p = positionMs + ((SystemClock.elapsedRealtime() - lueA) * vitesse).toLong()
        return if (dureeMs > 0) p.coerceIn(0, dureeMs) else p.coerceAtLeast(0)
    }

    /** Avancement 0..1 du titre (null = durée inconnue, radio…). */
    fun avancement(): Float? = if (dureeMs > 0) (positionMaintenant().toFloat() / dureeMs).coerceIn(0f, 1f) else null
}

/**
 * Sessions média du téléphone (lecture / pause, titre, position), lues grâce à l'accès aux notifications.
 * Prévient le moteur des règles quand la liste des apps qui jouent change.
 */
object Musique {
    private val principal = Handler(Looper.getMainLooper())
    private var gestionnaire: MediaSessionManager? = null
    private var appli: Context? = null
    private val suivies = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var derniersLecteurs: List<String> = emptyList()

    private val _etat = MutableStateFlow<EtatMusique?>(null)

    /** La musique la plus intéressante en ce moment : celle qui joue, sinon la dernière en pause. */
    val etat: StateFlow<EtatMusique?> = _etat

    private var audioActif = false
    private val _joue = MutableStateFlow(false)

    /**
     * Un son de musique joue en ce moment (une session média, ou n'importe quelle app via AudioManager).
     * Passe à « vrai » tout de suite, à « faux » après 1,2 s (pas de clignotement entre deux pistes).
     */
    val joue: StateFlow<Boolean> = _joue
    private val arret = Runnable { _joue.value = false }

    /** [actif] : un lecteur média joue (rappel AudioPlaybackCallback), relevé par le moteur de règles. */
    fun signalerAudio(actif: Boolean) {
        audioActif = actif
        principal.post { majJoue() }
    }

    private fun majJoue() {
        if (lecteurs.isNotEmpty() || audioActif) {
            principal.removeCallbacks(arret)
            _joue.value = true
        } else if (_joue.value) {
            principal.removeCallbacks(arret)
            principal.postDelayed(arret, 1200)
        }
    }

    /** Paquets des apps qui jouent en ce moment. */
    @Volatile
    var lecteurs: List<String> = emptyList()
        private set

    private val reverifier = Runnable { (appli as? VersoApp)?.moteurRegles?.evaluer("musique arrêtée") }

    private val surSessions = MediaSessionManager.OnActiveSessionsChangedListener { suivre(it.orEmpty()) }

    fun brancher(context: Context) = principal.post {
        val c = context.applicationContext
        appli = c
        val g = c.getSystemService(MediaSessionManager::class.java) ?: return@post
        gestionnaire = g
        try {
            val composant = EcouteNotifs.composant(c)
            g.addOnActiveSessionsChangedListener(surSessions, composant, principal)
            suivre(g.getActiveSessions(composant))
        } catch (_: SecurityException) {
            // Accès retiré entre-temps.
        }
    }

    fun debrancher() = principal.post {
        try {
            gestionnaire?.removeOnActiveSessionsChangedListener(surSessions)
        } catch (_: Exception) {
        }
        suivies.values.forEach { (m, cb) -> m.unregisterCallback(cb) }
        suivies.clear()
        actualiser()
    }

    private fun suivre(sessions: List<MediaController>) {
        val tokens = sessions.map { it.sessionToken }.toSet()
        suivies.keys.filter { it !in tokens }.forEach { t -> suivies.remove(t)?.let { (m, cb) -> m.unregisterCallback(cb) } }
        for (m in sessions) {
            if (m.sessionToken in suivies) continue
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = actualiser()
                override fun onMetadataChanged(metadata: MediaMetadata?) = actualiser()
                override fun onSessionDestroyed() {
                    suivies.remove(m.sessionToken)
                    actualiser()
                }
            }
            m.registerCallback(cb, principal)
            suivies[m.sessionToken] = m to cb
        }
        actualiser()
    }

    private fun actualiser() {
        val controleurs = suivies.values.map { it.first }
        val etats = controleurs.map { m ->
            val p = m.playbackState
            EtatMusique(
                pkg = m.packageName,
                enLecture = p?.state == PlaybackState.STATE_PLAYING || p?.state == PlaybackState.STATE_BUFFERING,
                titre = m.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                positionMs = p?.position ?: 0L,
                dureeMs = m.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                vitesse = p?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                lueA = p?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            )
        }
        lecteurs = etats.filter { it.enLecture }.map { it.pkg }.distinct()
        _etat.value = etats.firstOrNull { it.enLecture } ?: etats.firstOrNull { it.titre != null }
        majJoue()
        if (lecteurs != derniersLecteurs) {
            derniersLecteurs = lecteurs
            (appli as? VersoApp)?.moteurRegles?.evaluer("musique")
            // Arrêt de la musique : le système met un peu de temps à couper le son, on revérifie.
            principal.removeCallbacks(reverifier)
            if (lecteurs.isEmpty()) principal.postDelayed(reverifier, 2_500)
        }
    }
}


