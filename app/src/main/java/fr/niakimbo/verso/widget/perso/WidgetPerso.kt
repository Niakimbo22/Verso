package fr.niakimbo.verso.widget.perso

/**
 * Plan de dessin d'un widget Verso (ce que [RenduPerso] met dedans). Nico ne le manipule jamais :
 * il est fabriqué à partir d'un [WidgetVerso] (genre + taille + style), comme dans la galerie de l'iPhone.
 */
data class WidgetPerso(
    val nom: String,
    val blocs: List<Bloc>,
    val disposition: Disposition = Disposition.COLONNE,
    val fond: Fond = Fond.VERRE,
    val theme: Theme = Theme.SOMBRE,
    val alignement: Alignement = Alignement.CENTRE,
    /** LEDs de la matrice en rouge Nothing. */
    val ledsRouges: Boolean = false,
    val pointsCarres: Boolean = false,
) {
    enum class Disposition { COLONNE, LIGNE }
    enum class Fond { PLEIN, VERRE, TRANSPARENT }
    enum class Theme { SOMBRE, CLAIR }
    enum class Alignement { GAUCHE, CENTRE, DROITE }

    data class Bloc(
        val type: Type,
        val taille: Taille = Taille.M,
        val couleur: Couleur = Couleur.NORMALE,
        val texte: String = "",
    )

    enum class Type { MATRICE, HEURE, DATE, NOM, BATTERIE, AUTO, TEXTE, BOUTONS, ESPACE }
    enum class Taille { S, M, L, XL }
    enum class Couleur { NORMALE, ROUGE, DISCRETE }
}
