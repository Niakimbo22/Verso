package fr.niakimbo.verso.matrix

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Garde la trace des connexions réussies, pour le test des 48 h du mode debug Glyph
 * (VERSO_SPEC §2) : si la matrice refuse Verso après 48 h, on saura depuis quand
 * elle marchait.
 */
class MatrixHistory(context: Context) {
    data class Snapshot(val firstSuccess: Long?, val lastSuccess: Long?, val lastFailure: Long?, val failureReason: String?)

    private val prefs = context.applicationContext.getSharedPreferences("matrix_history", Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<Snapshot> = _snapshot

    fun recordSuccess() {
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            if (!prefs.contains(KEY_FIRST)) putLong(KEY_FIRST, now)
            putLong(KEY_LAST, now)
        }.apply()
        _snapshot.value = read()
    }

    fun recordFailure(reason: String) {
        prefs.edit()
            .putLong(KEY_FAIL, System.currentTimeMillis())
            .putString(KEY_FAIL_REASON, reason)
            .apply()
        _snapshot.value = read()
    }

    private fun read() = Snapshot(
        firstSuccess = prefs.getLongOrNull(KEY_FIRST),
        lastSuccess = prefs.getLongOrNull(KEY_LAST),
        lastFailure = prefs.getLongOrNull(KEY_FAIL),
        failureReason = prefs.getString(KEY_FAIL_REASON, null),
    )

    private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    companion object {
        private const val KEY_FIRST = "first_success"
        private const val KEY_LAST = "last_success"
        private const val KEY_FAIL = "last_failure"
        private const val KEY_FAIL_REASON = "last_failure_reason"

        private val FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.FRENCH).withZone(ZoneId.systemDefault())

        fun format(epochMillis: Long): String = FORMAT.format(Instant.ofEpochMilli(epochMillis))
    }
}
