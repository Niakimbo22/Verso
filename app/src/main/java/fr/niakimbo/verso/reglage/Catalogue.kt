package fr.niakimbo.verso.reglage

import fr.niakimbo.verso.generateur.Chargement
import fr.niakimbo.verso.generateur.Coeur
import fr.niakimbo.verso.generateur.Compteur
import fr.niakimbo.verso.generateur.HorlogeReglable
import fr.niakimbo.verso.generateur.Onde
import fr.niakimbo.verso.generateur.Pluie
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.modele.ModeleRepository

/**
 * Une version d'un modèle (V1, V2…) : ses réglages propres et la façon de le dessiner.
 * Quand un modèle est amélioré, l'ancienne version reste disponible.
 */
class VersionModele(
    val id: String,
    val nom: String,
    val description: String,
    val reglages: List<Reglage>,
    val construire: (Valeurs) -> Modele,
)

/** Un modèle de la bibliothèque, avec toutes ses versions (la dernière est celle par défaut). */
class EntreeCatalogue(
    val id: String,
    val nom: String,
    val versions: List<VersionModele>,
    /** Modèle de test : absent de la bibliothèque et du widget, lancé depuis le Diagnostic. */
    val cache: Boolean = false,
) {
    val parDefaut: VersionModele get() = versions.last()
    fun version(id: String?): VersionModele = versions.firstOrNull { it.id == id } ?: parDefaut
}

/**
 * Tous les modèles disponibles : ceux calculés dans l'app (réglables en détail) et ceux
 * des fichiers JSON (réglages communs seulement).
 */
class Catalogue(
    private val repository: ModeleRepository,
    private val store: ReglagesStore,
) {
    data class Contenu(val entrees: List<EntreeCatalogue>, val erreurs: List<String>) {
        fun parId(id: String) = entrees.firstOrNull { it.id == id }
    }

    @Volatile
    private var cache: Contenu? = null

    suspend fun contenu(): Contenu = cache ?: run {
        val biblio = repository.bibliotheque()
        val calcules = listOf(HorlogeReglable.entree, Coeur.entree, Onde.entree, Pluie.entree, Chargement.entree, Compteur.entree)
            .associateBy { it.id }
        val entrees = biblio.modeles.map { m ->
            calcules[m.id] ?: EntreeCatalogue(
                id = m.id,
                nom = m.nom,
                versions = listOf(VersionModele("v1", "V1", "Dessin d'origine.", emptyList()) { m }),
            )
        } + calcules.values.filter { c -> biblio.modeles.none { it.id == c.id } }
        Contenu(entrees, biblio.erreurs).also { cache = it }
    }

    /** Modèle prêt à jouer : version choisie, réglages propres puis réglages communs. */
    suspend fun resoudre(id: String): Modele? = contenu().parId(id)?.let(::resoudre)

    fun resoudre(entree: EntreeCatalogue): Modele {
        val choix = store.lire(entree.id)
        return resoudre(entree, choix.version, choix.valeurs)
    }

    fun resoudre(entree: EntreeCatalogue, versionId: String?, valeurs: Valeurs): Modele {
        val version = entree.version(versionId)
        val base = version.construire(valeurs)
        return Personnalisation.appliquer(base, valeurs)
    }
}
