package fr.niakimbo.verso.modele

import fr.niakimbo.verso.matrix.MatrixFrame

/**
 * Un modèle affichable sur la matrice (VERSO_SPEC §3.4).
 *
 * Même format pour les modèles intégrés, l'éditeur (plus tard), les modèles dynamiques et Trame.
 */
data class Modele(
    val id: String,
    val nom: String,
    val type: Type,
    val images: List<Image>,
) {
    init {
        require(id.isNotBlank()) { "Un modèle doit avoir un id" }
        require(images.isNotEmpty()) { "Le modèle « $id » n'a aucune image" }
        if (type == Type.STATIQUE) {
            require(images.size == 1) { "Le modèle statique « $id » doit avoir exactement 1 image (reçu ${images.size})" }
        }
    }

    enum class Type(val json: String) {
        STATIQUE("statique"),
        ANIME("anime");

        companion object {
            fun fromJson(value: String): Type =
                entries.firstOrNull { it.json == value }
                    ?: throw IllegalArgumentException("Type inconnu « $value » (attendu : statique ou anime)")
        }
    }

    /** Une image du modèle et sa durée d'affichage (ignorée pour un modèle statique). */
    data class Image(val frame: MatrixFrame, val dureeMs: Long) {
        init {
            require(dureeMs >= MIN_DUREE_MS) { "Durée d'image trop courte : $dureeMs ms (minimum $MIN_DUREE_MS)" }
        }
    }

    /** Image à montrer dans les aperçus (la première). */
    val apercu: MatrixFrame get() = images.first().frame

    val estAnime: Boolean get() = type == Type.ANIME && images.size > 1

    companion object {
        /** En dessous, la matrice ne suit plus et l'œil ne voit pas la différence. */
        const val MIN_DUREE_MS = 40L
        const val DUREE_PAR_DEFAUT_MS = 200L
    }
}
