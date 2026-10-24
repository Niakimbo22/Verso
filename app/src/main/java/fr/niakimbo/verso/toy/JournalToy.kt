package fr.niakimbo.verso.toy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Journal du Glyph Toy, gardé sur le téléphone (SharedPreferences).
 *
 * Le Toy vit surtout app fermée et écran éteint : un journal en mémoire serait perdu
 * avant que tu ouvres l'app. Celui-ci garde les [MAX] dernières lignes, avec la date.
 */
class JournalToy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso_journal_toy", Context.MODE_PRIVATE)

    private val _lignes = MutableStateFlow(charger())
    val lignes: StateFlow<List<String>> = _lignes

    @Synchronized
    fun ajouter(message: String) {
        val ligne = LocalDateTime.now().format(FORMAT) + "  " + message
        val nouvelles = (_lignes.value + ligne).takeLast(MAX)
        _lignes.value = nouvelles
        prefs.edit().putString(CLE, nouvelles.joinToString(SEP)).apply()
    }

    @Synchronized
    fun vider() {
        _lignes.value = emptyList()
        prefs.edit().remove(CLE).apply()
    }

    fun asText(): String = _lignes.value.joinToString("\n")

    private fun charger(): List<String> =
        prefs.getString(CLE, null)?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val MAX = 300
        const val CLE = "lignes"
        const val SEP = "\u001E"
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
    }
}
