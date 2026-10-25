package fr.niakimbo.verso.matrix

import kotlinx.coroutines.flow.StateFlow

/** État de la connexion à la Glyph Matrix. */
sealed interface MatrixState {
    /** Connexion au service Glyph en cours. */
    data object Connecting : MatrixState

    /** Connecté et enregistré : on peut afficher. */
    data object Ready : MatrixState

    /** Matrice inutilisable dans ce contexte (pas le bon téléphone, build sans SDK…). */
    data class Unavailable(val reason: String) : MatrixState

    /** Échec (enregistrement refusé, exception du SDK…). */
    data class Error(val message: String) : MatrixState
}

/**
 * Par où passent les images.
 *
 * - [APP] : `setAppMatrixFrame`, l'affichage d'une app. Il ne tient que tant que l'app est
 *   au premier plan, et n'importe quel Glyph Toy passe devant.
 * - [TOY] : `setMatrixFrame`, quand le système a lié le Glyph Toy de Verso. C'est le canal
 *   prioritaire : il reste affiché app fermée.
 */
enum class Canal { APP, TOY }

/**
 * Seul point d'accès à la matrice dans toute l'app (VERSO_SPEC §1 et §3.3).
 *
 * Une seule instance par processus, portée par [fr.niakimbo.verso.VersoApp] et partagée
 * par l'écran ET le Glyph Toy : le GlyphMatrixManager du SDK est un singleton, on ne
 * l'initialise qu'une fois et on n'appelle jamais unInit() depuis un composant.
 */
interface MatrixController {
    /** Nom de l'implémentation, affiché dans le Diagnostic. */
    val backendName: String

    val state: StateFlow<MatrixState>

    /** Canal utilisé pour les prochaines images. */
    val canal: StateFlow<Canal>

    /** Dernière image demandée (null = matrice éteinte), pour l'aperçu. */
    val lastFrame: StateFlow<MatrixFrame?>

    /** Nombre d'images réellement acceptées par le SDK depuis le lancement (mesure des animations). */
    val imagesEnvoyees: Long

    val log: MatrixLog

    /** Se connecte au service Glyph. Sans effet si déjà connecté ou en cours. */
    fun connect()

    /**
     * Se réenregistre auprès du service Glyph (sans unInit) : à chaque liaison du Toy, à l'ouverture
     * de l'app, ou par le bouton « Relancer ».
     */
    fun relancer(raison: String)

    /**
     * Vrai quand la liaison au service Glyph est perdue pour de bon dans ce processus
     * (le service ne répond plus) : seul un redémarrage de Verso la rétablit.
     */
    val redemarrageConseille: StateFlow<Boolean>

    /** Bascule vers le canal du Glyph Toy (lié par le système) ou revient au canal app. */
    fun setCanal(canal: Canal)

    /**
     * Affiche une image sur la matrice.
     * [journal] = false pour les images d'une animation : seules les erreurs sont journalisées.
     */
    fun show(frame: MatrixFrame, journal: Boolean = true)

    /** Éteint la matrice (rend la main au système). */
    fun clear()

    /** Expérience : appelle setGlyphMatrixTimeout(actif), méthode non documentée du SDK. */
    fun setDelaiExtinction(actif: Boolean)
}
