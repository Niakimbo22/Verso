package fr.niakimbo.verso.auto

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Applique les règles automatiques. Vit avec le processus Verso (gardé en vie par le Glyph Toy) :
 * écoute la batterie, l'écran, la sonnerie, le réseau, les casques, la lampe… ; une alarme le réveille
 * aux moments où une règle peut changer (plages, heures précises, soleil, alarme, défilement), même processus arrêté.
 *
 * Il ne touche jamais la matrice directement : il passe par [fr.niakimbo.verso.modele.ModelePlayer.appliquerAuto].
 */
class MoteurRegles(private val app: VersoApp) {
    val store = RegleStore(app)
    private val mutex = Mutex()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val notifs = app.getSystemService(NotificationManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private val reseaux = app.getSystemService(ConnectivityManager::class.java)
    private val alarmes = app.getSystemService(AlarmManager::class.java)
    private val cameras = app.getSystemService(CameraManager::class.java)

    private val _enCours = MutableStateFlow<String?>(null)

    /** Id de la règle « pendant » appliquée en ce moment (null = aucune). */
    val enCours: StateFlow<String?> = _enCours

    private val _contexte = MutableStateFlow<Contexte?>(null)

    /** Dernière situation lue : l'éditeur montre en direct si une condition est vraie. */
    val contexteActuel: StateFlow<Contexte?> = _contexte

    private var dernierEtatBatterie: Triple<Boolean, Int, Int>? = null
    private val lampes = ConcurrentHashMap<String, Boolean>()

    private val recepteur = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    // Diffusé à chaque % : on ne réévalue que si l'état utile change.
                    val branche = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val etat = Triple(branche != 0, niveau(intent), branche)
                    if (etat == dernierEtatBatterie) return
                    dernierEtatBatterie = etat
                    evaluer("batterie")
                }
                Intent.ACTION_USER_PRESENT -> evaluer("déverrouillage", deverrouillage = true)
                else -> evaluer(intent.action?.substringAfterLast('.')?.lowercase() ?: "?")
            }
        }
    }

    private val casques = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>?) = evaluer("casque")
        override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) = evaluer("casque")
    }

    private val reseau = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluer("réseau")
        override fun onLost(network: Network) = evaluer("réseau")
    }

    private val lampe = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (lampes.put(cameraId, enabled) != enabled) evaluer("lampe")
        }
    }

    fun demarrer() {
        _enCours.value = store.memoire.enCours?.signature?.substringBefore('|')
        app.registerReceiver(recepteur, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }, Context.RECEIVER_NOT_EXPORTED)
        val principal = Handler(Looper.getMainLooper())
        audio.registerAudioDeviceCallback(casques, principal)
        try {
            reseaux.registerDefaultNetworkCallback(reseau, principal)
        } catch (_: Exception) {
        }
        try {
            cameras.registerTorchCallback(lampe, principal)
        } catch (_: Exception) {
        }
        // Une règle ajoutée, modifiée, réordonnée, l'interrupteur général ou la ville : on réévalue.
        app.appScope.launch {
            combine(store.regles, store.actif, store.lieu) { r, a, l -> Triple(r, a, l) }.drop(1).collect { evaluer("règles modifiées") }
        }
        // Choix fait à la main : il tient jusqu'au prochain changement automatique (option A).
        app.player.surChoixManuel = { id ->
            app.appScope.launch { mutex.withLock { store.memoire = Decision.choixManuel(store.memoire, id) } }
        }
        // Processus relancé après le déverrouillage du matin (Toy relié à la fin de l'horaire du coucher…).
        evaluer("démarrage de Verso", deverrouillage = !keyguard.isKeyguardLocked)
    }

    /** Relit la situation, applique la règle gagnante si elle a changé, puis les moments et le défilement. */
    fun evaluer(raison: String, deverrouillage: Boolean = false) {
        app.appScope.launch {
            mutex.withLock {
                val regles = if (store.actif.value) store.regles.value else emptyList()
                val ctx = contexte()
                _contexte.value = ctx
                val affiche = app.store.modeleActif

                // 1. Règles « pendant ».
                val gagnante = Evaluation.gagnante(regles, ctx)
                val avant = store.memoire
                val cible = if (gagnante != null && gagnante.signature != avant.enCours?.signature) cible(gagnante, affiche) else null
                val r = Decision.evaluer(avant, gagnante, cible, affiche)
                if (r.memoire != avant) store.memoire = r.memoire
                _enCours.value = gagnante?.id
                if (gagnante?.signature != avant.enCours?.signature) {
                    store.prochainDefilement = (gagnante?.action as? Action.Defiler)
                        ?.let { System.currentTimeMillis() + it.minutes * 60_000L } ?: 0L
                }
                r.afficher?.let { c ->
                    noter(raison, gagnante?.nom ?: "fin de règle", c.modele)
                    afficher(c.modele)
                }

                // 2. Défilement des favoris pendant une règle « Défiler ».
                val defiler = gagnante?.action as? Action.Defiler
                if (defiler != null && r.afficher == null && store.prochainDefilement in 1..System.currentTimeMillis()) {
                    store.prochainDefilement = System.currentTimeMillis() + defiler.minutes * 60_000L
                    val suivant = favoriSuivant(app.store.modeleActif)
                    val res = Decision.defiler(store.memoire, suivant, app.store.modeleActif)
                    store.memoire = res.memoire
                    res.afficher?.let { noter("défilement", gagnante.nom, it.modele); afficher(it.modele) }
                }

                // 3. Moments (réveil, heure précise) : une fois par jour chacun.
                val jour = ctx.maintenant.toLocalDate().toEpochDay()
                for (m in regles.filter { it.active && it.estMoment }) {
                    if (store.dernierMoment(m.id) == jour) continue
                    val pret = Evaluation.momentPret(m, ctx) { d -> estLeMoment(d, ctx.maintenant, deverrouillage) }
                    if (!pret) continue
                    store.setDernierMoment(m.id, jour)
                    val res = Decision.moment(store.memoire, cible(m, app.store.modeleActif), app.store.modeleActif)
                    store.memoire = res.memoire
                    res.afficher?.let { noter(raison, m.nom, it.modele); afficher(it.modele) }
                }

                programmerAlarme(regles, ctx)
            }
        }
    }

    private fun estLeMoment(d: Declencheur, t: LocalDateTime, deverrouillage: Boolean): Boolean = when (d) {
        // L'alarme peut arriver avec un peu de retard (téléphone en veille) : 3 minutes de marge.
        is Declencheur.AHeure -> t.dayOfWeek in d.jours && (t.hour * 60 + t.minute - d.minute) in 0..2
        is Declencheur.Reveil -> deverrouillage && Evaluation.dansFenetreReveil(d, t)
        else -> false
    }

    /** Le modèle qu'une règle affiche en commençant (null = éteindre). */
    private suspend fun cible(r: Regle, affiche: String?): String? = when (val a = r.action) {
        is Action.Afficher -> a.modele
        Action.Eteindre -> null
        Action.Hasard -> candidats().filter { it != affiche }.randomOrNull() ?: affiche
        is Action.Defiler -> favoriSuivant(affiche)
    }

    /** Favoris, ou toute la bibliothèque s'il n'y en a pas. */
    private suspend fun candidats(): List<String> {
        val ids = app.catalogue.contenu().entrees.filter { !it.cache }.map { it.id }
        return app.favoris.ids.value.filter { it in ids }.ifEmpty { ids }
    }

    private suspend fun favoriSuivant(affiche: String?): String? {
        val liste = candidats()
        if (liste.isEmpty()) return affiche
        val i = liste.indexOf(affiche)
        return liste[(i + 1).mod(liste.size)]
    }

    private fun noter(raison: String, regle: String, modele: String?) =
        app.journalToy.ajouter("Auto ($raison) : « $regle » → ${modele ?: "matrice éteinte"}")

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

    fun contexte(): Contexte {
        val batterie = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val branche = batterie?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val filtre = notifs.currentInterruptionFilter
        val caps = try {
            reseaux.activeNetwork?.let { reseaux.getNetworkCapabilities(it) }
        } catch (_: Exception) {
            null
        }
        val zone = ZoneId.systemDefault()
        return Contexte(
            maintenant = LocalDateTime.now(),
            enCharge = branche != 0,
            batterie = niveau(batterie),
            casques = casquesConnectes(),
            nePasDeranger = filtre != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filtre != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            sourceCharge = when (branche) {
                0 -> null
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> Declencheur.SANS_FIL
                else -> Declencheur.CABLE
            },
            economie = power.isPowerSaveMode,
            verrouille = keyguard.isKeyguardLocked,
            sonnerie = when (audio.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> Declencheur.SILENCIEUX
                AudioManager.RINGER_MODE_VIBRATE -> Declencheur.VIBREUR
                else -> null
            },
            avion = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0,
            lampe = lampes.values.any { it },
            bluetooth = if (bluetoothAutorise()) store.bluetooth.toList() else emptyList(),
            reseau = when {
                caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Declencheur.AUCUN
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Declencheur.WIFI
                else -> Declencheur.DONNEES
            },
            prochaineAlarme = alarmes.nextAlarmClock?.triggerTime?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
            lieu = store.lieu.value,
            zone = zone,
        )
    }

    fun bluetoothAutorise(): Boolean =
        app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Noms des casques / écouteurs branchés en ce moment (lu sans permission, via l'audio). */
    fun casquesConnectes(): List<String> =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in TYPES_CASQUE }
            .map { it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: nomType(it.type) }
            .distinct()

    /** Appareils Bluetooth associés au téléphone (pour les choisir dans l'éditeur). */
    @Suppress("MissingPermission")
    fun appareilsBluetooth(): List<String> = try {
        if (!bluetoothAutorise()) emptyList()
        else app.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.bondedDevices
            ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }?.sorted().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun niveau(i: Intent?): Int {
        val n = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val max = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (n < 0 || max <= 0) 100 else (n * 100 / max).coerceIn(0, 100)
    }

    /** Alarme exacte au prochain moment où une règle peut changer (réveille le téléphone : la matrice change face cachée). */
    private fun programmerAlarme(regles: List<Regle>, ctx: Contexte) {
        val pi = PendingIntent.getBroadcast(
            app, 0, Intent(app, AlarmeRegles::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val frontiere = Evaluation.prochaineFrontiere(regles, ctx)?.atZone(ctx.zone)?.toInstant()?.toEpochMilli()
        val defilement = store.prochainDefilement.takeIf { it > 0 }
        val ms = listOfNotNull(frontiere, defilement).minOrNull()
        if (ms == null) {
            alarmes.cancel(pi)
            return
        }
        // USE_EXACT_ALARM (déjà déclaré pour le widget horloge) donne le droit aux alarmes exactes.
        if (alarmes.canScheduleExactAlarms()) {
            alarmes.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
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
 * Moment où une règle peut changer, et changement d'heure / de fuseau : le moteur réévalue.
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
                else -> "heure"
            },
        )
        // Laisse au moteur le temps d'appliquer le modèle avant que le système reprenne la main.
        Handler(Looper.getMainLooper()).postDelayed({ fin.finish() }, 3_000)
    }
}

/**
 * Connexion / déconnexion d'un appareil Bluetooth (montre, voiture…). Déclaré dans le manifeste :
 * reçu même app fermée, la liste des appareils connectés reste à jour.
 */
class BluetoothRecepteur : BroadcastReceiver() {
    @Suppress("MissingPermission", "DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val moteur = app.moteurRegles
        if (!moteur.bluetoothAutorise()) return
        val appareil: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val nom = try {
            appareil.name
        } catch (_: SecurityException) {
            null
        } ?: appareil.address
        val actuels = moteur.store.bluetooth.toMutableSet()
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> actuels += nom
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> actuels -= nom
            else -> return
        }
        moteur.store.bluetooth = actuels
        moteur.evaluer("Bluetooth")
    }
}
