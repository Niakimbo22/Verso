package fr.niakimbo.verso.modele

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Les derniers modèles affichés (à la main ou par une règle), du plus récent au plus ancien : rayon « Récents ». */
class RecentsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso", Context.MODE_PRIVATE)
    private val _ids = MutableStateFlow(prefs.getString(CLE, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList())
    val ids: StateFlow<List<String>> = _ids

    @Synchronized
    fun ajouter(id: String) {
        val liste = (listOf(id) + _ids.value.filter { it != id }).take(MAX)
        if (liste == _ids.value) return
        prefs.edit().putString(CLE, liste.joinToString(",")).apply()
        _ids.value = liste
    }

    private companion object {
        const val CLE = "recents"
        const val MAX = 12
    }
}
