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
 * Seul point d'accès à la matrice dans toute l'app (VERSO_SPEC §1 et §3.3).
 *
 * Une seule instance par processus, portée par [fr.niakimbo.verso.VersoApp] :
 * le GlyphMatrixManager du SDK est un singleton, on ne l'initialise qu'une fois
 * et on n'appelle jamais unInit() depuis un composant.
 */
interface MatrixController {
    /** Nom de l'implémentation, affiché dans l'écran de test. */
    val backendName: String

    val state: StateFlow<MatrixState>

    /** Dernière image demandée (null = matrice éteinte), pour l'aperçu. */
    val lastFrame: StateFlow<MatrixFrame?>

    val log: MatrixLog

    /** Se connecte au service Glyph. Sans effet si déjà connecté ou en cours. */
    fun connect()

    /**
     * Affiche une image sur la matrice.
     * [journal] = false pour les images d'une animation : seules les erreurs sont journalisées.
     */
    fun show(frame: MatrixFrame, journal: Boolean = true)

    /** Éteint la matrice (rend la main au système). */
    fun clear()
}
