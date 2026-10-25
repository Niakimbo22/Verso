package fr.niakimbo.verso.matrix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Implémentation factice, utilisée quand l'APK est compilé sans l'AAR du SDK Glyph.
 * Elle met à jour l'aperçu et le journal, mais ne touche à aucun matériel.
 */
class FakeMatrixController : MatrixController {
    override val backendName = "Factice (build sans SDK Glyph)"

    private val _state = MutableStateFlow<MatrixState>(
        MatrixState.Unavailable("APK compilé sans le SDK Glyph : ajoute le secret GLYPH_SDK_AAR_BASE64 côté CI."),
    )
    override val state: StateFlow<MatrixState> = _state

    private val _canal = MutableStateFlow(Canal.APP)
    override val canal: StateFlow<Canal> = _canal

    private val _lastFrame = MutableStateFlow<MatrixFrame?>(null)
    override val lastFrame: StateFlow<MatrixFrame?> = _lastFrame

    private val compteur = AtomicLong()
    override val imagesEnvoyees: Long get() = compteur.get()

    override val log = MatrixLog()

    override fun connect() {
        log.warn("Build sans SDK Glyph : rien ne s'affichera sur la matrice.")
    }

    override fun relancer(raison: String) {
        log.info("[factice] Relance ($raison)")
    }

    override fun setCanal(canal: Canal) {
        if (_canal.value != canal) log.info("[factice] Canal : $canal")
        _canal.value = canal
    }

    override fun show(frame: MatrixFrame, journal: Boolean) {
        _lastFrame.value = frame
        compteur.incrementAndGet()
        if (journal) log.info("[factice] Image de ${frame.litCount} LEDs (aperçu seulement)")
    }

    override fun clear() {
        _lastFrame.value = null
        log.info("[factice] Matrice éteinte (aperçu seulement)")
    }

    override fun setDelaiExtinction(actif: Boolean) {
        log.info("[factice] setGlyphMatrixTimeout($actif)")
    }
}
