package fr.niakimbo.verso.reglage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Version choisie et réglages de chaque modèle, gardés sur le téléphone.
 * Relu par le Glyph Toy : les réglages s'appliquent aussi app fermée.
 */
class ReglagesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso_reglages", Context.MODE_PRIVATE)

    data class Choix(val version: String?, val valeurs: Valeurs)

    private val _revision = MutableStateFlow(0)

    /** Change à chaque modification : l'interface s'en sert pour se redessiner. */
    val revision: StateFlow<Int> = _revision

    fun lire(modeleId: String): Choix {
        val json = prefs.getString(modeleId, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return Choix(null, Valeurs())
        val version = json.optString("version", "").ifBlank { null }
        val brut = json.optJSONObject("valeurs")
        val map = mutableMapOf<String, Any>()
        brut?.keys()?.forEach { k -> brut.opt(k)?.let { map[k] = it } }
        return Choix(version, Valeurs(map))
    }

    @Synchronized
    fun ecrire(modeleId: String, choix: Choix) {
        val json = JSONObject().apply {
            choix.version?.let { put("version", it) }
            put("valeurs", JSONObject(choix.valeurs.brut()))
        }
        prefs.edit().putString(modeleId, json.toString()).apply()
        _revision.value++
    }
}
