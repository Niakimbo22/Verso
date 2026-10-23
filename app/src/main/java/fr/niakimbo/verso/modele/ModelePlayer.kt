package fr.niakimbo.verso.modele

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
 * Joue un [Modele] sur la matrice : une image fixe, ou une animation en boucle.
 *
 * Étape 3 : l'animation tourne dans le processus de l'app (donc tant que Verso est ouvert).
 * Étape 4 : le foreground service réutilisera ce même lecteur pour qu'elle continue en arrière-plan.
 *
 * Tout ce qui s'affiche passe par ici, pour qu'une animation ne vienne jamais écraser
 * une image demandée ensuite (choix d'un autre modèle, image de test, extinction).
 */
class ModelePlayer(
    private val controller: MatrixController,
    private val scope: CoroutineScope,
) {
    private val _actif = MutableStateFlow<Modele?>(null)

    /** Modèle en cours d'affichage (null = matrice éteinte ou image de test). */
    val actif: StateFlow<Modele?> = _actif

    private var animation: Job? = null

    init {
        // Si la matrice (re)devient prête, on renvoie le modèle statique en cours :
        // il a pu être choisi avant la fin de la connexion. Une animation, elle, se renvoie toute seule.
        scope.launch {
            controller.state.collect { state ->
                val modele = _actif.value
                if (state == MatrixState.Ready && modele != null && !modele.estAnime) {
                    controller.show(modele.apercu, journal = false)
                }
            }
        }
    }

    @Synchronized
    fun jouer(modele: Modele) {
        arreterAnimation()
        _actif.value = modele
        if (!modele.estAnime) {
            controller.show(modele.apercu)
            return
        }
        controller.log.info("Animation « ${modele.nom} » : ${modele.images.size} images")
        animation = scope.launch {
            while (isActive) {
                for (image in modele.images) {
                    // Sous le même verrou que jouer()/eteindre() : une animation annulée
                    // ne peut plus glisser une image après celle qui l'a remplacée.
                    synchronized(this@ModelePlayer) {
                        if (!isActive) return@launch
                        controller.show(image.frame, journal = false)
                    }
                    delay(image.dureeMs)
                }
            }
        }
    }

    /** Affiche une image isolée (écran Diagnostic) en coupant le modèle en cours. */
    @Synchronized
    fun afficherImage(frame: MatrixFrame) {
        arreterAnimation()
        _actif.value = null
        controller.show(frame)
    }

    @Synchronized
    fun eteindre() {
        arreterAnimation()
        _actif.value = null
        controller.clear()
    }

    private fun arreterAnimation() {
        animation?.cancel()
        animation = null
    }
}
