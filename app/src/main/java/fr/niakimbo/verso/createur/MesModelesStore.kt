package fr.niakimbo.verso.createur

import android.content.Context
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.modele.ModeleJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/**
 * Modèles créés sur le téléphone (rayon « Mes modèles », VERSO_SPEC §3.5) : un fichier JSON par modèle
 * dans `files/mes_modeles/`, au même format que les modèles intégrés (dessins) ou au format blocs.
 *
 * Les ids commencent par `perso-` suivi de la date de création : ils ne croisent jamais
 * ceux des modèles intégrés, et l'ordre des fichiers suit l'ordre de création.
 */
class MesModelesStore(context: Context) {

    /** Un modèle créé dans Verso. */
    sealed interface Perso {
        val id: String
        val nom: String
        val categorie: String

        data class Dessin(val modele: Modele) : Perso {
            override val id get() = modele.id
            override val nom get() = modele.nom
            override val categorie get() = modele.categorie ?: RAYON
        }

        data class Blocs(val def: DefinitionBlocs) : Perso {
            override val id get() = def.id
            override val nom get() = def.nom
            override val categorie get() = def.categorie
        }
    }

    private val dossier = File(context.applicationContext.filesDir, "mes_modeles")
    private val verrou = Mutex()

    @Volatile
    private var cache: List<Perso>? = null

    private val _revision = MutableStateFlow(0)

    /** Change à chaque ajout, modification ou suppression. */
    val revision: StateFlow<Int> = _revision

    /** Fichiers illisibles (signalés dans le Diagnostic, jamais bloquants). */
    @Volatile
    var erreurs: List<String> = emptyList()
        private set

    suspend fun tous(): List<Perso> = cache ?: verrou.withLock { cache ?: withContext(Dispatchers.IO) { charger() } }

    suspend fun parId(id: String): Perso? = tous().firstOrNull { it.id == id }

    private fun charger(): List<Perso> {
        val liste = mutableListOf<Perso>()
        val errs = mutableListOf<String>()
        dossier.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.forEach { f ->
            try {
                liste += lire(JSONObject(f.readText()))
            } catch (e: Exception) {
                errs += "Mes modèles/${f.name} : ${e.message}"
            }
        }
        erreurs = errs
        cache = liste
        return liste
    }

    /** Ajoute ou remplace (même id) un modèle. [apercu] sert aux modèles par blocs. */
    suspend fun enregistrer(p: Perso, apercu: MatrixFrame? = null) {
        verrou.withLock {
            withContext(Dispatchers.IO) {
                if (cache == null) charger()
                dossier.mkdirs()
                val json = versJson(p, apercu)
                val tmp = File(dossier, "${p.id}.json.tmp")
                tmp.writeText(json.toString(2))
                if (!tmp.renameTo(fichier(p.id))) {
                    fichier(p.id).writeText(json.toString(2))
                    tmp.delete()
                }
                val actuelle = cache.orEmpty()
                cache = if (actuelle.any { it.id == p.id }) actuelle.map { if (it.id == p.id) p else it }
                else (actuelle + p).sortedBy { it.id }
            }
        }
        _revision.value++
    }

    suspend fun supprimer(id: String) {
        verrou.withLock {
            withContext(Dispatchers.IO) {
                if (cache == null) charger()
                fichier(id).delete()
                cache = cache.orEmpty().filterNot { it.id == id }
            }
        }
        _revision.value++
    }

    /** Texte JSON d'un modèle (format de partage, lisible). */
    suspend fun exporter(id: String, apercu: MatrixFrame? = null): String? =
        parId(id)?.let { versJson(it, apercu).toString(2) }

    /**
     * Importe un ou plusieurs modèles (un objet JSON, ou un tableau d'objets).
     * Chaque modèle reçoit un nouvel id `perso-…` : un import ne remplace jamais rien.
     * Renvoie les modèles ajoutés ; lève une exception avec un message lisible si rien n'est valide.
     */
    suspend fun importer(texte: String, apercu: (Perso) -> MatrixFrame?): List<Perso> {
        val racine = JSONTokener(texte.trim()).nextValue()
        val objets = when (racine) {
            is JSONObject -> listOf(racine)
            is JSONArray -> (0 until racine.length()).map { racine.getJSONObject(it) }
            else -> throw IllegalArgumentException("Ce n'est pas un modèle Verso (JSON attendu).")
        }
        val ajoutes = mutableListOf<Perso>()
        val problemes = mutableListOf<String>()
        for (o in objets) {
            try {
                if (!o.has("id")) o.put("id", "import")
                val p = lire(o)
                val id = nouvelId()
                val copie = when (p) {
                    is Perso.Dessin -> Perso.Dessin(p.modele.copy(id = id, categorie = p.modele.categorie ?: RAYON))
                    is Perso.Blocs -> Perso.Blocs(p.def.copy(id = id))
                }
                enregistrer(copie, apercu(copie))
                ajoutes += copie
            } catch (e: Exception) {
                problemes += e.message ?: e.javaClass.simpleName
            }
        }
        if (ajoutes.isEmpty()) throw IllegalArgumentException(problemes.firstOrNull() ?: "Aucun modèle trouvé.")
        return ajoutes
    }

    private fun fichier(id: String) = File(dossier, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private var dernierId = 0L

    /** Id unique et croissant (date de création en base 36). */
    @Synchronized
    fun nouvelId(): String {
        var t = System.currentTimeMillis()
        if (t <= dernierId) t = dernierId + 1
        dernierId = t
        return "perso-" + t.toString(36)
    }

    companion object {
        /** Rayon par défaut des modèles créés. */
        const val RAYON = "Mes modèles"

        fun lire(json: JSONObject): Perso =
            if (BlocsJson.estBlocs(json)) Perso.Blocs(BlocsJson.parse(json))
            else Perso.Dessin(ModeleJson.parse(json).let { it.copy(categorie = it.categorie ?: RAYON) })

        fun versJson(p: Perso, apercu: MatrixFrame?): JSONObject = when (p) {
            is Perso.Dessin -> ModeleJson.toJson(p.modele)
            is Perso.Blocs -> BlocsJson.toJson(p.def, apercu ?: MatrixFrame.blank())
        }
    }
}
