package fr.niakimbo.verso.auto

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Applique les règles automatiques. Vit avec le processus Verso (gardé en vie par le Glyph Toy) :
 * écoute la charge, la batterie, les casques, Ne pas déranger et le déverrouillage ; une alarme
 * le réveille aux débuts et fins des plages horaires, même processus arrêté.
 *
 * Il ne touche jamais la matrice directement : il passe par [fr.niakimbo.verso.modele.ModelePlayer.appliquerAuto].
 */
class MoteurRegles(private val app: VersoApp) {
    val store = RegleStore(app)
    private val mutex = Mutex()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val notifs = app.getSystemService(NotificationManager::class.java)

    private val _enCours = MutableStateFlow<String?>(null)

    /** Id de la règle « pendant » appliquée en ce moment (null = aucune). */
    val enCours: StateFlow<String?> = _enCours

    private var dernierEtatBatterie: Pair<Boolean, Int>? = null

    private val recepteur = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    // Diffusé à chaque % : on ne réévalue que si une règle batterie/charge peut changer.
                    val etat = lireBatterie(intent)
                    if (etat == dernierEtatBatterie) return
                    dernierEtatBatterie = etat
                    evaluer("batterie")
                }
                Intent.ACTION_USER_PRESENT -> surDeverrouillage()
                else -> evaluer(intent.action ?: "?")
            }
        }
    }

    private val casques = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>?) = evaluer("casque connecté")
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) = evaluer("casque déconnecté")
    }

    fun demarrer() {
        _enCours.value = store.memoire.enCours?.signature?.substringBefore('|')
        app.registerReceiver(recepteur, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
        }, Context.RECEIVER_NOT_EXPORTED)
        audio.registerAudioDeviceCallback(casques, Handler(Looper.getMainLooper()))
        // Une règle ajoutée, modifiée, réordonnée ou l'interrupteur général : on réévalue.
        app.appScope.launch {
            combine(store.regles, store.actif) { r, a -> r to a }.drop(1).collect { evaluer("règles modifiées") }
        }
        // Choix fait à la main : il tient jusqu'au prochain changement automatique (option A).
        app.player.surChoixManuel = { id ->
            app.appScope.launch { mutex.withLock { store.memoire = Decision.choixManuel(store.memoire, id) } }
        }
        evaluer("démarrage de Verso")
        // Processus relancé après le déverrouillage du matin (Toy relié à la fin de l'horaire du coucher…).
        if (!app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) surDeverrouillage()
    }

    /** Relit la situation et applique la règle gagnante si elle a changé. */
    fun evaluer(raison: String) {
        app.appScope.launch {
            mutex.withLock {
                val regles = if (store.actif.value) store.regles.value else emptyList()
                val ctx = contexte()
                val gagnante = Evaluation.gagnante(regles, ctx)
                val avant = store.memoire
                val r = Decision.evaluer(avant, gagnante, app.store.modeleActif)
                if (r.memoire != avant) store.memoire = r.memoire
                _enCours.value = gagnante?.id
                r.afficher?.let { cible ->
                    app.journalToy.ajouter(
                        "Auto ($raison) : " + (gagnante?.let { "« ${it.nom} »" } ?: "fin de règle") +
                            " → " + (cible.modele ?: "matrice éteinte"),
                    )
                    afficher(cible.modele)
                }
                programmerAlarme(regles, ctx.maintenant)
            }
        }
    }

    private suspend fun afficher(id: String?) {
        if (id == null) {
            app.player.appliquerAuto(null)
            return
        }
        val modele = app.catalogue.resoudre(id)
        if (modele == null) {
            app.journalToy.ajouter("Auto : modèle « $id » introuvable, rien n'est changé")
            return
        }
        app.player.appliquerAuto(modele)
    }

    private fun surDeverrouillage() {
        app.appScope.launch {
            mutex.withLock {
                if (!store.actif.value) return@withLock
                val maintenant = LocalDateTime.now()
                val jour = maintenant.toLocalDate().toEpochDay()
                if (store.dernierReveil == jour) return@withLock
                val regle = store.regles.value.firstOrNull { r ->
                    r.active && (r.quand as? Declencheur.Reveil)?.let { Evaluation.dansFenetreReveil(it, maintenant) } == true
                } ?: return@withLock
                store.dernierReveil = jour
                val res = Decision.moment(store.memoire, regle.modele, app.store.modeleActif)
                store.memoire = res.memoire
                res.afficher?.let {
                    app.journalToy.ajouter("Auto (réveil) : « ${regle.nom} » → ${it.modele ?: "matrice éteinte"}")
                    afficher(it.modele)
                }
            }
        }
    }

    fun contexte(): Contexte {
        val batterie = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val (enCharge, niveau) = lireBatterie(batterie)
        val filtre = notifs.currentInterruptionFilter
        return Contexte(
            maintenant = LocalDateTime.now(),
            enCharge = enCharge,
            batterie = niveau,
            casques = casquesConnectes(),
            nePasDeranger = filtre != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filtre != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
        )
    }

    /** Noms des casques / écouteurs branchés en ce moment (lu sans permission, via l'audio). */
    fun casquesConnectes(): List<String> =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in TYPES_CASQUE }
            .map { it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: nomType(it.type) }
            .distinct()

    private fun lireBatterie(i: Intent?): Pair<Boolean, Int> {
        val n = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val max = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val niveau = if (n < 0 || max <= 0) 100 else (n * 100 / max).coerceIn(0, 100)
        val branche = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return branche to niveau
    }

    /** Alarme exacte au prochain début ou fin de plage (réveille le téléphone : la matrice change face cachée). */
    private fun programmerAlarme(regles: List<Regle>, maintenant: LocalDateTime) {
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            app, 0, Intent(app, AlarmeRegles::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val quand = Evaluation.prochaineFrontiere(regles, maintenant)
        if (quand == null) {
            am.cancel(pi)
            return
        }
        val ms = quand.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // USE_EXACT_ALARM (déjà déclaré pour le widget horloge) donne le droit aux alarmes exactes.
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun nomType(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Casque filaire"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "Casque USB"
        else -> "Écouteurs Bluetooth"
    }

    private companion object {
        val TYPES_CASQUE = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}

/**
 * Début ou fin d'une plage horaire, et changement d'heure / de fuseau : le moteur réévalue.
 * Si le processus était arrêté, ce récepteur le relance (et VersoApp démarre le moteur).
 */
class AlarmeRegles : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val fin = goAsync()
        app.moteurRegles.evaluer(
            when (intent.action) {
                Intent.ACTION_TIME_CHANGED -> "heure changée"
                Intent.ACTION_TIMEZONE_CHANGED -> "fuseau changé"
                else -> "plage horaire"
            },
        )
        // Laisse au moteur le temps d'appliquer le modèle avant que le système reprenne la main.
        Handler(Looper.getMainLooper()).postDelayed({ fin.finish() }, 3_000)
    }
}
