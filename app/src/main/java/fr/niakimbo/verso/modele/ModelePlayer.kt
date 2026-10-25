package fr.niakimbo.verso.modele

import android.content.Context
import android.os.PowerManager
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.matrix.MatrixState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Joue le modèle actif sur la matrice : image fixe, animation en boucle, ou modèle dynamique (horloge).
 *
 * Le modèle choisi est mémorisé ([ModeleStore]) : le Glyph Toy le relit quand le système le lance,
 * app fermée ou après un redémarrage.
 *
 * La boucle (animation / horloge) ne tourne que si quelqu'un peut la voir :
 * l'app est à l'écran, ou Verso est le Glyph Toy lié par le système.
 *
 * Tout ce qui s'affiche passe par ici, pour qu'une animation ne vienne jamais écraser
 * une image demandée ensuite (choix d'un autre modèle, image de test, extinction).
 */
class ModelePlayer(
    context: Context,
    private val controller: MatrixController,
    private val catalogue: Catalogue,
    private val store: ModeleStore,
    private val scope: CoroutineScope,
) {
    private val _actif = MutableStateFlow<Modele?>(null)

    /** Modèle en cours (null = matrice éteinte ou image de test). */
    val actif: StateFlow<Modele?> = _actif

    private var boucle: Job? = null
    private var dernierFrame: MatrixFrame? = null

    /** Vrai tant qu'un écran de Verso est au premier plan. */
    @Volatile
    var appVisible = false
        private set
    private var toyLie = false

    private val wakeLock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "verso:animation")
            .apply { setReferenceCounted(false) }

    init {
        // Si la matrice (re)devient prête, on renvoie tout de suite l'image en cours :
        // elle a pu être demandée avant la fin de la connexion.
        scope.launch {
            controller.state.collect { state ->
                if (state == MatrixState.Ready) {
                    synchronized(this@ModelePlayer) { dernierFrame }?.let { controller.show(it, journal = false) }
                }
            }
        }
    }

    /** Prévenu de chaque choix fait à la main (id, null = éteint) : le moteur des règles automatiques s'en sert. */
    @Volatile
    var surChoixManuel: ((String?) -> Unit)? = null

    /** Choix de l'utilisateur (app, widget, fenêtre ☰) : affiche et mémorise. */
    fun jouer(modele: Modele) {
        store.modeleActif = modele.id
        demarrer(modele)
        surChoixManuel?.invoke(modele.id)
    }

    /**
     * Changement décidé par une règle automatique ([modele] null = éteindre).
     *
     * Si personne ne peut voir la matrice (app fermée et Glyph Toy pas lié), on mémorise seulement :
     * le Toy le reprendra à sa liaison. On n'ouvre jamais la couche app de la matrice app fermée
     * (elle resterait collée devant le Toy, voir §3.3 de la spec).
     */
    @Synchronized
    fun appliquerAuto(modele: Modele?) {
        if (modele == null) {
            store.modeleActif?.let { store.dernierModele = it }
            store.modeleActif = null
            arreterBoucle()
            _actif.value = null
            dernierFrame = null
            if (doitTourner()) controller.clear()
            return
        }
        store.modeleActif = modele.id
        if (doitTourner()) {
            demarrer(modele)
        } else {
            arreterBoucle()
            _actif.value = modele
            dernierFrame = imageInitiale(modele)
        }
    }

    /** Relit le modèle mémorisé et le relance (Glyph Toy lié, retour dans l'app). */
    suspend fun reprendre() {
        val id = store.modeleActif ?: return
        val modele = catalogue.resoudre(id) ?: run {
            controller.log.warn("Modèle mémorisé « $id » introuvable")
            return
        }
        synchronized(this) {
            if (_actif.value?.id == modele.id && (boucle?.isActive == true || !modele.estVivant)) {
                // Déjà en place : on renvoie juste l'image courante (utile après un réveil).
                dernierFrame?.let { controller.show(it, journal = false) }
                return
            }
        }
        demarrer(modele)
    }

    /**
     * Les réglages ou la version d'un modèle ont changé : s'il est affiché, on le relance
     * avec ses nouveaux réglages (sur la matrice, et donc aussi dans le Glyph Toy).
     */
    suspend fun rafraichir(id: String) {
        if (_actif.value?.id != id) return
        val modele = catalogue.resoudre(id) ?: return
        demarrer(modele)
    }

    /** Affiche une image isolée (Diagnostic) en coupant le modèle en cours, sans la mémoriser. */
    @Synchronized
    fun afficherImage(frame: MatrixFrame) {
        arreterBoucle()
        _actif.value = null
        dernierFrame = frame
        controller.show(frame)
    }

    /** Éteint la matrice ; le modèle éteint est retenu pour « Rallumer » (app, widget On/Off). */
    @Synchronized
    fun eteindre() {
        store.modeleActif?.let { store.dernierModele = it }
        store.modeleActif = null
        arreterBoucle()
        _actif.value = null
        dernierFrame = null
        controller.clear()
        surChoixManuel?.invoke(null)
    }

    /**
     * Signal « minute » du Glyph Toy (EVENT_AOD) : on redessine l'horloge et on renvoie
     * l'image courante, au cas où le système l'aurait effacée.
     */
    @Synchronized
    fun signalMinute() {
        val modele = _actif.value
        val frame = if (modele?.dynamique != null) modele.frameDynamique() else dernierFrame
        if (frame != null) {
            dernierFrame = frame
            controller.show(frame, journal = false)
        }
        if (modele != null && modele.estVivant && boucle?.isActive != true && doitTourner()) {
            lancerBoucle(modele)
        }
    }

    @Synchronized
    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        actualiserBoucle()
    }

    @Synchronized
    fun setToyLie(lie: Boolean) {
        toyLie = lie
        actualiserBoucle()
    }

    @Synchronized
    private fun demarrer(modele: Modele) {
        arreterBoucle()
        _actif.value = modele
        val premiere = imageInitiale(modele)
        dernierFrame = premiere
        controller.show(premiere)
        if (modele.estVivant) {
            controller.log.info(
                if (modele.dynamique != null) "Modèle dynamique « ${modele.nom} »" + (modele.periodeMs?.let { " (une image toutes les $it ms)" } ?: "")
                else "Animation « ${modele.nom} » : ${modele.images.size} images",
            )
            if (doitTourner()) lancerBoucle(modele)
        }
    }

    private fun imageInitiale(modele: Modele): MatrixFrame =
        if (modele.dynamique != null) modele.frameDynamique() else modele.apercu

    private fun doitTourner() = appVisible || toyLie

    private fun actualiserBoucle() {
        val modele = _actif.value ?: return
        if (!modele.estVivant) return
        if (doitTourner() && boucle?.isActive != true) lancerBoucle(modele)
        if (!doitTourner()) arreterBoucle()
    }

    private fun lancerBoucle(modele: Modele) {
        arreterBoucle()
        boucle = scope.launch {
            if (modele.dynamique != null) {
                while (isActive) {
                    delay(modele.msAvantProchaineImage())
                    envoyer(modele.frameDynamique()) ?: return@launch
                    // Blocs rapides (texte défilant, secondes) : comme une animation.
                    if (modele.periodeMs != null) {
                        wakeLock.acquire(WAKE_LOCK_MS)
                    }
                }
            } else {
                while (isActive) {
                    for (image in modele.images) {
                        envoyer(image.frame) ?: return@launch
                        wakeLock.acquire(WAKE_LOCK_MS)
                        delay(image.dureeMs)
                    }
                }
            }
        }
    }

    /**
     * Envoie une image de la boucle, sous le même verrou que jouer()/eteindre() : une boucle
     * annulée ne peut plus glisser une image après celle qui l'a remplacée. Null = boucle annulée.
     */
    private fun CoroutineScope.envoyer(frame: MatrixFrame): Unit? = synchronized(this@ModelePlayer) {
        if (!isActive) return null
        dernierFrame = frame
        controller.show(frame, journal = false)
    }

    private fun arreterBoucle() {
        boucle?.cancel()
        boucle = null
        relacherEveil()
    }

    private fun relacherEveil() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        /** Renouvelé à chaque image : se relâche tout seul 1 min après l'arrêt de l'animation. */
        const val WAKE_LOCK_MS = 60_000L
    }
}
