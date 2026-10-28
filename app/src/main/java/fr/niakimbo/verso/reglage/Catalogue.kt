package fr.niakimbo.verso.reglage

import fr.niakimbo.verso.createur.MesModelesStore
import fr.niakimbo.verso.createur.MoteurBlocs
import fr.niakimbo.verso.generateur.Chargement
import fr.niakimbo.verso.generateur.Coeur
import fr.niakimbo.verso.generateur.Compteur
import fr.niakimbo.verso.generateur.HorlogeReglable
import fr.niakimbo.verso.generateur.EgaliseurLive
import fr.niakimbo.verso.generateur.Live
import fr.niakimbo.verso.generateur.MeteoLive
import fr.niakimbo.verso.generateur.MusiqueLive
import fr.niakimbo.verso.generateur.Onde
import fr.niakimbo.verso.generateur.Pluie
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.modele.ModeleRepository
import kotlinx.coroutines.flow.StateFlow

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
    /** Rayon de la bibliothèque. */
    val categorie: String = AUTRES,
    /** Modèle créé dans Verso (éditeur dessin ou blocs) : modifiable, exportable, supprimable. */
    val perso: MesModelesStore.Perso? = null,
) {
    fun avecCategorie(c: String?) = EntreeCatalogue(id, nom, versions, cache, c ?: AUTRES, perso)

    companion object {
        const val AUTRES = "Autres"
    }

    val parDefaut: VersionModele get() = versions.last()
    fun version(id: String?): VersionModele = versions.firstOrNull { it.id == id } ?: parDefaut
}

/**
 * Tous les modèles disponibles : ceux calculés dans l'app (réglables en détail), ceux
 * des fichiers JSON (réglages communs seulement) et ceux créés dans Verso (« Mes modèles »).
 */
class Catalogue(
    private val repository: ModeleRepository,
    private val store: ReglagesStore,
    private val mesModeles: MesModelesStore,
) {
    data class Contenu(val entrees: List<EntreeCatalogue>, val erreurs: List<String>) {
        fun parId(id: String) = entrees.firstOrNull { it.id == id }
    }

    /** Change quand un modèle est créé, modifié ou supprimé : la bibliothèque se recharge. */
    val revision: StateFlow<Int> = mesModeles.revision

    @Volatile
    private var integres: Contenu? = null

    @Volatile
    private var cache: Pair<Int, Contenu>? = null

    suspend fun contenu(): Contenu {
        val rev = mesModeles.revision.value
        cache?.let { (r, c) -> if (r == rev) return c }
        val base = integres ?: chargerIntegres().also { integres = it }
        val persos = mesModeles.tous().map(::entreePerso)
        // Les créations d'abord : sinon elles se perdent derrière les 90 modèles intégrés.
        // distinctBy : un même id ne peut jamais apparaître deux fois dans la bibliothèque.
        val contenu = Contenu((persos + base.entrees).distinctBy { it.id }, base.erreurs + mesModeles.erreurs)
        cache = rev to contenu
        return contenu
    }

    private suspend fun chargerIntegres(): Contenu {
        val biblio = repository.bibliotheque()
        val calcules = listOf(HorlogeReglable.entree, Coeur.entree, Onde.entree, Pluie.entree, Chargement.entree, Compteur.entree)
            .associateBy { it.id }
        // Modèles live (météo, musique) : ils reprennent les icônes de la bibliothèque. Rayon « Live », en tête.
        val pluie = Pluie.entree.versions.first().construire(Valeurs())
        val icone: (String) -> Modele? = { id -> if (id == "pluie") pluie else biblio.parId(id) }
        // Tout ce qui suit le vrai monde (heure, météo, musique, son) : rayon « Live », en tête.
        val live = listOf(HorlogeReglable.entree, fr.niakimbo.verso.generateur.MinuteurLive.entree, fr.niakimbo.verso.generateur.ChronoLive.entree, MeteoLive.entree(icone), MusiqueLive.entree(icone), EgaliseurLive.entree(icone))
            .map { it.avecCategorie(Live.RAYON) }
        val idsLive = live.map { it.id }.toSet()
        val entrees = live + biblio.modeles.filter { it.id !in idsLive }.map { m ->
            (calcules[m.id] ?: EntreeCatalogue(
                id = m.id,
                nom = m.nom,
                versions = listOf(VersionModele("v1", "V1", "Dessin d'origine.", emptyList()) { m }),
            )).avecCategorie(m.categorie)
        } + calcules.values.filter { c -> c.id !in idsLive && biblio.modeles.none { it.id == c.id } }
        return Contenu(entrees, biblio.erreurs)
    }

    private fun entreePerso(p: MesModelesStore.Perso): EntreeCatalogue = when (p) {
        is MesModelesStore.Perso.Dessin -> EntreeCatalogue(
            id = p.id, nom = p.nom, categorie = p.categorie, perso = p,
            versions = listOf(VersionModele("v1", "V1", "Ton dessin.", emptyList()) { p.modele }),
        )
        is MesModelesStore.Perso.Blocs -> EntreeCatalogue(
            id = p.id, nom = p.nom, categorie = p.categorie, perso = p,
            versions = listOf(
                VersionModele("v1", "V1", "Modèle par blocs, calculé en direct.", emptyList()) {
                    MoteurBlocs.modele(p.def, ressources())
                },
            ),
        )
    }

    /**
     * Images des modèles qu'un bloc Dessin ou Effet peut reprendre : modèles intégrés et dessins créés,
     * avec leurs réglages. Jamais un autre modèle par blocs (pas de boucle possible).
     */
    fun ressources(): MoteurBlocs.Ressources {
        val memo = HashMap<String, List<Modele.Image>?>()
        return MoteurBlocs.Ressources { id ->
            synchronized(memo) {
                memo.getOrPut(id) {
                    val entree = (cache?.second ?: integres)?.parId(id)
                        ?.takeIf { it.perso !is MesModelesStore.Perso.Blocs }
                        ?: return@getOrPut null
                    val m = resoudre(entree)
                    if (m.dynamique != null) listOf(Modele.Image(m.frameDynamique(), 1000)) else m.images
                }
            }
        }
    }

    /** Ressources à jour pour l'éditeur de blocs (catalogue chargé). */
    suspend fun ressourcesEditeur(): MoteurBlocs.Ressources {
        contenu()
        return ressources()
    }

    /**
     * Modèle pour un widget. Le lanceur ne peut que faire défiler des images déjà prêtes : un modèle qui suit
     * le son en direct (Égaliseur V2, Musique en direct) y serait figé. Il est remplacé par une petite boucle
     * d'égaliseur (même nom, même id) : immobile sans musique, animée quand un son joue, selon le réglage
     * « Mouvement » (Au son / Toujours / Jamais) ; le widget est redessiné quand la musique démarre ou s'arrête.
     */
    suspend fun resoudreWidget(id: String): Modele? {
        if (id !in fr.niakimbo.verso.auto.Evaluation.MODELES_SON) return resoudre(id)
        val vrai = resoudre(id) ?: return null
        val valeurs = store.lire(id).valeurs
        val anim = EgaliseurLive.pourWidget(valeurs, fr.niakimbo.verso.auto.Musique.joue.value)
        return Personnalisation.appliquer(anim, valeurs).copy(id = vrai.id, nom = vrai.nom)
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
