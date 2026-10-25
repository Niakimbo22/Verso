package fr.niakimbo.verso.modele

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Modèles favoris, dans l'ordre choisi (glisser-déposer dans le rayon ★ ; un nouveau favori arrive à la fin).
 * Le widget et la fenêtre ☰ suivent cet ordre ; sans favori, le widget fait défiler toute la bibliothèque.
 */
class FavorisStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(lire())

    /** Ids des favoris, dans l'ordre. */
    val ids: StateFlow<List<String>> = _ids

    fun estFavori(id: String) = id in _ids.value

    @Synchronized
    fun basculer(id: String) {
        val liste = _ids.value.toMutableList()
        if (!liste.remove(id)) liste.add(id)
        prefs.edit().putString(CLE, liste.joinToString(SEP)).apply()
        _ids.value = liste
    }

    /**
     * Nouvel ordre des favoris. Les favoris absents de [ordre] (ex. modèle pas encore chargé)
     * gardent leur place relative, à la fin.
     */
    @Synchronized
    fun ordonner(ordre: List<String>) {
        val actuels = _ids.value
        val liste = ordre.filter { it in actuels }.distinct() + actuels.filter { it !in ordre }
        if (liste == actuels) return
        prefs.edit().putString(CLE, liste.joinToString(SEP)).apply()
        _ids.value = liste
    }

    private fun lire(): List<String> =
        prefs.getString(CLE, null)?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private companion object {
        const val CLE = "favoris"
        const val SEP = ","
    }
}

/** Passe d'un modèle à l'autre dans une liste (favoris, ou toute la bibliothèque). */
object Navigation {
    /**
     * Id voisin de [actuel] dans [ordre] ([pas] = +1 suivant, -1 précédent), en bouclant.
     * Si [actuel] n'est pas dans la liste, on part du début (ou de la fin en reculant).
     */
    fun voisin(ordre: List<String>, actuel: String?, pas: Int): String? {
        if (ordre.isEmpty()) return null
        val i = ordre.indexOf(actuel)
        if (i < 0) return if (pas >= 0) ordre.first() else ordre.last()
        return ordre[Math.floorMod(i + pas, ordre.size)]
    }
}
