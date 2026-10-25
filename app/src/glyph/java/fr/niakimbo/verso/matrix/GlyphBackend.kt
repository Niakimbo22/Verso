package fr.niakimbo.verso.matrix

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Compilé seulement quand l'AAR du SDK Glyph est présent (voir app/build.gradle.kts). */
internal object GlyphBackend {
    fun create(context: Context, history: MatrixHistory): MatrixController = GlyphMatrixController(context, history)
}

/**
 * Implémentation réelle, basée sur le SDK Glyph Matrix de Nothing.
 *
 * Tous les appels au SDK passent par un seul thread ([worker]) : pas d'appel IPC sur le
 * thread principal, et pas de course entre connexion, affichage et extinction.
 * Le GlyphMatrixManager est un singleton du processus, partagé par l'app et le Toy : on
 * l'initialise une fois et on ne fait **jamais** unInit() (VERSO_SPEC §2). Constat du 25/10
 * (v0.1.30) : après unInit(), un nouvel init() ne reçoit plus jamais de réponse du service Glyph.
 * Si la liaison au service est perdue, seul un redémarrage du processus la rétablit
 * ([redemarrageConseille]).
 *
 * Couche « app » (constat du 25/10, v0.1.45, d'après le schéma de priorités officiel du SDK) :
 * sur le Phone (4a) Pro, le Toy AOD est la couche la PLUS BASSE ; l'affichage d'une app
 * (`setAppMatrixFrame`) passe devant lui. Cette couche garde sa dernière image tant qu'on
 * n'appelle pas `closeAppMatrix()`, même Verso arrêté. Or chaque démarrage de Verso commence
 * sur le canal app : la première image partait dans cette couche, puis le Toy se liait…
 * et la matrice restait figée sur cette image pour toujours (seule la tuile Glyph off/on
 * la vidait). D'où [fermerCoucheApp] : à la bascule vers le Toy et à chaque enregistrement
 * en canal Toy (couche laissée par un processus précédent).
 *
 * Deux protections contre la matrice « figée » (constaté le 25/10, v0.1.28) :
 * - les images ne s'empilent jamais : seule la dernière commande (image ou extinction) attend
 *   son tour, une animation ne peut donc pas prendre de retard sur le SDK ;
 * - un chien de garde note dans le journal les appels au SDK anormalement longs (sans rien couper).
 */
private class GlyphMatrixController(
    context: Context,
    private val history: MatrixHistory,
) : MatrixController {
    override val backendName = "SDK Glyph (${Glyph.DEVICE_25111p})"

    private val app = context.applicationContext

    private val worker: ScheduledExecutorService = nouveauWorker()
    private val manager: GlyphMatrixManager by lazy { GlyphMatrixManager.getInstance(app) }

    @Volatile
    private var serviceConnected = false

    /** init() déjà appelé au moins une fois dans ce processus. */
    @Volatile
    private var initialise = false

    /** Tentatives de liaison au service Glyph restées sans réponse, d'affilée. */
    @Volatile
    private var liaisonsSansReponse = 0

    private val _redemarrage = MutableStateFlow(false)
    override val redemarrageConseille: StateFlow<Boolean> = _redemarrage
    private var connectTimeout: ScheduledFuture<*>? = null

    private val _state = MutableStateFlow<MatrixState>(MatrixState.Unavailable("Pas encore connecté"))
    override val state: StateFlow<MatrixState> = _state

    private val _canal = MutableStateFlow(Canal.APP)
    override val canal: StateFlow<Canal> = _canal

    /**
     * La couche app de la matrice contient peut-être une image (envoyée par ce processus, ou
     * laissée par un processus précédent : d'où vrai au départ). Voir [fermerCoucheApp].
     */
    @Volatile
    private var coucheAppOuverte = true

    private val _lastFrame = MutableStateFlow<MatrixFrame?>(null)
    override val lastFrame: StateFlow<MatrixFrame?> = _lastFrame

    private val compteur = AtomicLong()
    override val imagesEnvoyees: Long get() = compteur.get()

    override val log = MatrixLog()

    /** Dernière commande d'affichage pas encore envoyée (les précédentes sont dépassées). */
    private sealed interface Commande {
        class Image(val frame: MatrixFrame, val journal: Boolean) : Commande
        data object Eteindre : Commande
    }

    private val commande = AtomicReference<Commande?>(null)
    private val envoiPrevu = AtomicBoolean(false)

    /** Tâche du SDK en cours (pour le chien de garde). */
    private class Tache(val nom: String, val debut: Long) {
        @Volatile
        var signale = false
    }

    @Volatile
    private var enCours: Tache? = null

    private val garde = Executors.newSingleThreadScheduledExecutor { Thread(it, "verso-matrix-garde").apply { isDaemon = true } }

    init {
        garde.scheduleWithFixedDelay(::surveiller, 2, 2, TimeUnit.SECONDS)
    }

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            executer("connexion") {
                serviceConnected = true
                liaisonsSansReponse = 0
                _redemarrage.value = false
                connectTimeout?.cancel(false)
                log.ok("Service Glyph connecté")
                registerDevice()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            executer("déconnexion") {
                serviceConnected = false
                _state.value = MatrixState.Unavailable("Service Glyph déconnecté")
                log.warn("Service Glyph déconnecté : touche « Reconnecter ».")
            }
        }
    }

    private fun nouveauWorker(): ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "verso-matrix").apply { isDaemon = true } }

    /** Lance [bloc] sur le thread du SDK, sous la surveillance du chien de garde. */
    private fun executer(nom: String, bloc: () -> Unit) {
        try {
            worker.execute {
                val t = Tache(nom, SystemClock.uptimeMillis())
                enCours = t
                try {
                    bloc()
                } catch (e: Throwable) {
                    log.error("Erreur interne ($nom)", e)
                } finally {
                    if (enCours === t) enCours = null
                }
            }
        } catch (_: RejectedExecutionException) {
            // Ne devrait pas arriver : le thread du SDK n'est jamais arrêté.
        }
    }

    /**
     * Chien de garde, en observation seulement : il note dans le journal un appel au SDK
     * anormalement long, mais ne touche jamais à la connexion.
     *
     * Constat du 25/10 (v0.1.33) : téléphone posé face cachée, l'écran s'éteint, le processeur se met
     * en veille au milieu d'un envoi, et l'horloge murale continue d'avancer. L'ancien chien de garde
     * croyait le SDK bloqué, coupait la connexion… et la matrice restait figée au bout de 5 s.
     * D'où l'horloge « uptime » (arrêtée pendant la veille) et l'absence de toute action.
     */
    private fun surveiller() {
        val t = enCours ?: return
        val duree = SystemClock.uptimeMillis() - t.debut
        if (duree < BLOCAGE_MS || t.signale) return
        t.signale = true
        log.warn("Appel au SDK lent : « ${t.nom} » dure depuis ${duree / 1000} s (aucune action)")
    }

    override fun connect() {
        executer("connect") {
            when {
                _state.value == MatrixState.Ready || _state.value == MatrixState.Connecting -> Unit
                serviceConnected -> registerDevice()
                else -> bindService()
            }
        }
    }

    /** Sans unInit() : on se réenregistre si le service est joint, sinon on tente de le joindre. */
    override fun relancer(raison: String) {
        executer("relance") {
            log.info("Relance de la matrice ($raison)")
            if (serviceConnected) registerDevice() else bindService()
        }
    }

    override fun show(frame: MatrixFrame, journal: Boolean) {
        _lastFrame.value = frame
        poster(Commande.Image(frame, journal))
    }

    override fun clear() {
        _lastFrame.value = null
        poster(Commande.Eteindre)
    }

    private fun poster(c: Commande) {
        commande.set(c)
        planifierEnvoi()
    }

    private fun planifierEnvoi() {
        if (!envoiPrevu.compareAndSet(false, true)) return
        executer("envoi") {
            envoiPrevu.set(false)
            when (val c = commande.getAndSet(null)) {
                is Commande.Image -> envoyer(c.frame, c.journal)
                Commande.Eteindre -> eteindre()
                null -> Unit
            }
        }
    }

    private fun envoyer(frame: MatrixFrame, journal: Boolean) {
        if (_state.value != MatrixState.Ready) {
            if (journal) log.warn("Image non envoyée : matrice pas prête (${describe(_state.value)})")
            return
        }
        try {
            val couleurs = frame.toGlyphColors()
            // Canal Toy : setMatrixFrame (prioritaire, tient app fermée).
            // Canal app : setAppMatrixFrame (recommandé par Nothing hors Toy).
            if (_canal.value == Canal.TOY) {
                manager.setMatrixFrame(couleurs)
            } else {
                manager.setAppMatrixFrame(couleurs)
                coucheAppOuverte = true
            }
            compteur.incrementAndGet()
            if (journal) log.info("Image envoyée (${_canal.value}) : ${frame.litCount} LEDs allumées")
        } catch (e: GlyphException) {
            fail("Le SDK a refusé l'image", e)
        } catch (t: Throwable) {
            fail("Erreur à l'envoi de l'image", t)
        }
    }

    private fun eteindre() {
        if (_state.value != MatrixState.Ready) {
            log.warn("Extinction ignorée : matrice pas prête (${describe(_state.value)})")
            return
        }
        if (_canal.value == Canal.TOY) {
            // Un Toy ne « rend » pas la matrice : il affiche une image noire.
            try {
                manager.setMatrixFrame(IntArray(MatrixFrame.SIZE))
                log.info("Matrice éteinte (Toy : image noire)")
            } catch (e: Throwable) {
                fail("Impossible d'éteindre la matrice (Toy)", e)
            }
            return
        }
        try {
            manager.closeAppMatrix()
            coucheAppOuverte = false
            log.info("Matrice éteinte (closeAppMatrix)")
        } catch (t: Throwable) {
            // Système trop ancien pour closeAppMatrix : on envoie une image noire à la place.
            log.warn("closeAppMatrix indisponible (${t.message}), envoi d'une image noire")
            try {
                manager.setAppMatrixFrame(IntArray(MatrixFrame.SIZE))
            } catch (e: Throwable) {
                fail("Impossible d'éteindre la matrice", e)
            }
        }
    }

    override fun setCanal(canal: Canal) {
        executer("canal") {
            if (_canal.value == canal) return@executer
            _canal.value = canal
            log.info(
                if (canal == Canal.TOY) "Canal Toy : Verso est le Glyph Toy actif (setMatrixFrame)"
                else "Canal app : Verso n'est plus lié comme Toy (setAppMatrixFrame)",
            )
            if (canal == Canal.TOY && _state.value == MatrixState.Ready && fermerCoucheApp("bascule vers le Toy")) {
                // La couche app ne cache plus le Toy : on lui renvoie l'image en cours.
                _lastFrame.value?.let { envoyer(it, journal = false) }
            }
        }
    }

    /**
     * Vide la couche app de la matrice (`closeAppMatrix`) pour que le Toy, couche la plus basse
     * sur le Phone (4a) Pro, redevienne visible. Sans effet si la couche est déjà vide.
     * Vrai si la couche a été fermée.
     */
    private fun fermerCoucheApp(raison: String): Boolean {
        if (!coucheAppOuverte) return false
        return try {
            manager.closeAppMatrix()
            coucheAppOuverte = false
            log.ok("Couche app fermée ($raison) : le Toy redevient visible")
            true
        } catch (t: Throwable) {
            log.error("closeAppMatrix refusé ($raison)", t)
            false
        }
    }

    override fun setDelaiExtinction(actif: Boolean) {
        executer("délai d'extinction") {
            if (_state.value != MatrixState.Ready) {
                log.warn("setGlyphMatrixTimeout($actif) ignoré : matrice pas prête")
                return@executer
            }
            try {
                manager.setGlyphMatrixTimeout(actif)
                log.ok("setGlyphMatrixTimeout($actif) accepté")
            } catch (t: Throwable) {
                log.error("setGlyphMatrixTimeout($actif) refusé", t)
            }
        }
    }

    private fun bindService() {
        _state.value = MatrixState.Connecting
        if (!Common.is25111p()) {
            log.warn("Modèle « ${Build.MODEL} » non reconnu comme Phone (4a) Pro : on tente quand même.")
        }
        log.info("Connexion au service Glyph…")
        try {
            if (!initialise) {
                manager.init(callback)
                initialise = true
            } else {
                // Déjà initialisé : un second init() ne rebranche rien. On attend le rappel du service.
                log.info("init() déjà fait : en attente du service Glyph")
            }
        } catch (t: Throwable) {
            fail("Impossible de joindre le service Glyph", t)
            return
        }
        connectTimeout?.cancel(false)
        connectTimeout = try {
            worker.schedule({
                if (!serviceConnected && _state.value == MatrixState.Connecting) {
                    liaisonsSansReponse++
                    if (initialise && liaisonsSansReponse >= LIAISONS_AVANT_REDEMARRAGE) _redemarrage.value = true
                    fail(
                        if (_redemarrage.value) "Liaison au service Glyph perdue : il faut redémarrer Verso"
                        else "Le service Glyph ne répond pas",
                    )
                }
            }, CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        } catch (_: RejectedExecutionException) {
            null
        }
    }

    private fun registerDevice() {
        _state.value = MatrixState.Connecting
        val accepted = try {
            manager.register(Glyph.DEVICE_25111p)
        } catch (t: Throwable) {
            fail("Erreur pendant l'enregistrement auprès du service Glyph", t)
            return
        }
        if (accepted) {
            // Avant « prête » : l'image renvoyée ensuite par le ModelePlayer arrive après la fermeture.
            if (_canal.value == Canal.TOY) fermerCoucheApp("enregistrement en canal Toy")
            _state.value = MatrixState.Ready
            history.recordSuccess()
            log.ok("Enregistré (${Glyph.DEVICE_25111p}) : matrice prête")
        } else {
            fail("Enregistrement refusé par le système. Le mode debug Glyph est-il actif ? (il expire après 48 h)")
        }
    }

    private fun fail(message: String, error: Throwable? = null) {
        val detail = error?.message?.let { " ($it)" }.orEmpty()
        _state.value = MatrixState.Error(message + detail)
        history.recordFailure(message + detail)
        log.error(message, error)
    }

    private fun describe(state: MatrixState): String = when (state) {
        MatrixState.Connecting -> "connexion en cours"
        MatrixState.Ready -> "prête"
        is MatrixState.Unavailable -> state.reason
        is MatrixState.Error -> state.message
    }

    private companion object {
        const val CONNECT_TIMEOUT_S = 6L

        /** Après 2 attentes de 6 s sans réponse du service, on conseille de redémarrer le processus. */
        const val LIAISONS_AVANT_REDEMARRAGE = 2

        /** Un appel IPC au service Glyph prend quelques ms : au-delà de 4 s (hors veille), c'est anormal. */
        const val BLOCAGE_MS = 4_000L
    }
}
