package fr.niakimbo.verso.modele

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * l'app est à l'écran, ou Verso est le Glyph Toy lié par le système — et **jamais écran éteint**.
 * Constat du 25/10 (v0.1.41 à 0.1.43) : écran éteint, le service Glyph du système perd des images
 * même à 1 i/s et finit par se figer (seule la tuile Glyph off/on du menu déroulant le débloque).
 * Écran éteint, la matrice garde donc une image fixe, redessinée à chaque EVENT_AOD (≈ 1/min),
 * comme l'horloge qui a toujours marché. L'animation repart au rallumage de l'écran.
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

    /** Écran du téléphone allumé (suivi par SCREEN_ON / SCREEN_OFF). */
    @Volatile
    private var ecranAllume =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    init {
        context.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> setEcranAllume(true)
                    Intent.ACTION_SCREEN_OFF -> setEcranAllume(false)
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, Context.RECEIVER_NOT_EXPORTED)

        // Si la matrice (re)devient prête, on renvoie tout de suite l'image en cours :
        // elle a pu être demandée avant la fin de la connexion.
        scope.launch {
            controller.state.collect { state ->
                if (state == MatrixState.Ready) {
                    synchronized(this@ModelePlayer) { dernierFrame }?.let { controller.show(it, journal = false) }
                    store.delaiExtinction?.let(controller::setDelaiExtinction)
                }
            }
        }
    }

    /** Choix de l'utilisateur (app, plus tard widget) : affiche et mémorise. */
    fun jouer(modele: Modele) {
        store.modeleActif = modele.id
        demarrer(modele)
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
    private fun setEcranAllume(allume: Boolean) {
        ecranAllume = allume
        if (!allume && boucle?.isActive == true) {
            controller.log.info("Écran éteint : animation en pause, image fixe (MAJ chaque minute)")
        }
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

    private fun doitTourner() = ecranAllume && (appVisible || toyLie)

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
                }
            } else {
                while (isActive) {
                    for (image in modele.images) {
                        envoyer(image.frame) ?: return@launch
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
    }
}
