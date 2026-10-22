package fr.niakimbo.verso.matrix

import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Compilé seulement quand l'AAR du SDK Glyph est présent (voir app/build.gradle.kts). */
internal object GlyphBackend {
    fun create(context: Context, history: MatrixHistory): MatrixController = GlyphMatrixController(context, history)
}

/**
 * Implémentation réelle, basée sur le SDK Glyph Matrix de Nothing.
 *
 * Tous les appels au SDK passent par un seul thread ([worker]) : pas d'appel IPC sur le
 * thread principal, et pas de course entre connexion, affichage et extinction.
 * Le GlyphMatrixManager est un singleton du processus : on l'initialise une fois et on
 * ne fait jamais unInit() (VERSO_SPEC §2).
 */
private class GlyphMatrixController(
    context: Context,
    private val history: MatrixHistory,
) : MatrixController {
    override val backendName = "SDK Glyph (${Glyph.DEVICE_25111p})"

    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "verso-matrix") }
    private val manager: GlyphMatrixManager by lazy { GlyphMatrixManager.getInstance(app) }

    private var serviceConnected = false
    private var connectTimeout: ScheduledFuture<*>? = null

    private val _state = MutableStateFlow<MatrixState>(MatrixState.Unavailable("Pas encore connecté"))
    override val state: StateFlow<MatrixState> = _state

    private val _lastFrame = MutableStateFlow<MatrixFrame?>(null)
    override val lastFrame: StateFlow<MatrixFrame?> = _lastFrame

    override val log = MatrixLog()

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            worker.execute {
                serviceConnected = true
                connectTimeout?.cancel(false)
                log.ok("Service Glyph connecté")
                registerDevice()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            worker.execute {
                serviceConnected = false
                _state.value = MatrixState.Unavailable("Service Glyph déconnecté")
                log.warn("Service Glyph déconnecté : touche « Reconnecter ».")
            }
        }
    }

    override fun connect() {
        worker.execute {
            when {
                _state.value == MatrixState.Ready || _state.value == MatrixState.Connecting -> Unit
                serviceConnected -> registerDevice()
                else -> bindService()
            }
        }
    }

    override fun show(frame: MatrixFrame) {
        worker.execute {
            _lastFrame.value = frame
            if (_state.value != MatrixState.Ready) {
                log.warn("Image non envoyée : matrice pas prête (${describe(_state.value)})")
                return@execute
            }
            try {
                manager.setAppMatrixFrame(frame.toGlyphColors())
                log.info("Image envoyée : ${frame.litCount} LEDs allumées")
            } catch (e: GlyphException) {
                fail("Le SDK a refusé l'image", e)
            } catch (t: Throwable) {
                fail("Erreur à l'envoi de l'image", t)
            }
        }
    }

    override fun clear() {
        worker.execute {
            _lastFrame.value = null
            if (_state.value != MatrixState.Ready) {
                log.warn("Extinction ignorée : matrice pas prête (${describe(_state.value)})")
                return@execute
            }
            try {
                manager.closeAppMatrix()
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
    }

    private fun bindService() {
        _state.value = MatrixState.Connecting
        if (!Common.is25111p()) {
            log.warn("Modèle « ${Build.MODEL} » non reconnu comme Phone (4a) Pro : on tente quand même.")
        }
        log.info("Connexion au service Glyph…")
        try {
            manager.init(callback)
        } catch (t: Throwable) {
            fail("Impossible de joindre le service Glyph", t)
            return
        }
        connectTimeout?.cancel(false)
        connectTimeout = worker.schedule({
            if (!serviceConnected && _state.value == MatrixState.Connecting) {
                fail("Le service Glyph ne répond pas (système Nothing trop ancien ?)")
            }
        }, CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
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
    }
}
