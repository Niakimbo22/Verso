package fr.niakimbo.verso.modele

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bibliothèque des modèles intégrés, lus depuis les fichiers JSON de `assets/modeles/`.
 *
 * L'ordre d'affichage suit le nom de fichier (`01-horloge.json`, `02-coeur.json`…).
 * Un fichier invalide est ignoré et son erreur remontée dans [Bibliotheque.erreurs],
 * pour qu'un modèle cassé ne fasse jamais planter l'app.
 */
class ModeleRepository(private val context: Context) {

    data class Bibliotheque(val modeles: List<Modele>, val erreurs: List<String>) {
        fun parId(id: String): Modele? = modeles.firstOrNull { it.id == id }
    }

    @Volatile
    private var cache: Bibliotheque? = null

    suspend fun bibliotheque(): Bibliotheque = cache ?: withContext(Dispatchers.IO) {
        cache ?: charger().also { cache = it }
    }

    private fun charger(): Bibliotheque {
        val assets = context.assets
        val fichiers = assets.list(DOSSIER).orEmpty().filter { it.endsWith(".json") }.sorted()
        val modeles = mutableListOf<Modele>()
        val erreurs = mutableListOf<String>()
        for (fichier in fichiers) {
            try {
                val texte = assets.open("$DOSSIER/$fichier").bufferedReader().use { it.readText() }
                val modele = ModeleJson.parse(texte)
                if (modeles.any { it.id == modele.id }) {
                    erreurs += "$fichier : id « ${modele.id} » déjà utilisé"
                } else {
                    modeles += modele
                }
            } catch (e: Exception) {
                erreurs += "$fichier : ${e.message}"
            }
        }
        return Bibliotheque(modeles, erreurs)
    }

    companion object {
        const val DOSSIER = "modeles"
    }
}
