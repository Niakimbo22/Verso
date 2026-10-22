package fr.niakimbo.verso.matrix

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Journal en mémoire des échanges avec la matrice, affiché dans l'écran de test. */
class MatrixLog {
    enum class Level { INFO, OK, WARN, ERROR }

    data class Entry(val time: String, val level: Level, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun info(message: String) = add(Level.INFO, message)
    fun ok(message: String) = add(Level.OK, message)
    fun warn(message: String) = add(Level.WARN, message)

    fun error(message: String, error: Throwable? = null) {
        val detail = error?.let { " — ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
        add(Level.ERROR, message + detail)
        Log.e(TAG, message, error)
    }

    /** Texte brut du journal, pour le copier et l'envoyer. */
    fun asText(): String = entries.value.joinToString("\n") { "${it.time} ${it.level} ${it.message}" }

    private fun add(level: Level, message: String) {
        if (level != Level.ERROR) Log.i(TAG, message)
        val entry = Entry(LocalTime.now().format(FORMAT), level, message)
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
    }

    private companion object {
        const val TAG = "VersoMatrix"
        const val MAX_ENTRIES = 60
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
