package fr.niakimbo.verso.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val apkUrl: String)

object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/Niakimbo22/Verso/releases/latest"

    /** Dernière release publique avec un APK, ou null s'il n'y en a pas (encore). */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Verso-Android")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub a répondu ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    return@withContext Release(
                        version = json.getString("tag_name").removePrefix("v"),
                        apkUrl = asset.getString("browser_download_url"),
                    )
                }
            }
            null
        } catch (_: FileNotFoundException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Compare deux versions « 0.1.12 » (préfixe « v » et suffixe « -local » ignorés). */
    fun isNewer(remote: String, installed: String): Boolean {
        val a = parts(remote)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        Regex("\\d+").findAll(version.substringBefore('-')).map { it.value.toInt() }.toList()
}
