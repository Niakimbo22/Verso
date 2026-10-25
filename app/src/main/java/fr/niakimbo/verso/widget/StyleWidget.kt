package fr.niakimbo.verso.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Personnalisation d'un widget posé (une par widget, réglée à la pose ou via « Paramètres du widget »). */
data class StyleWidget(
    val theme: Theme,
    val fond: Fond,
    val forme: Forme = Forme.ROND,
    val taille: Taille = Taille.MOYEN,
    /** LEDs allumées en rouge Nothing au lieu de blanc (ou noir en thème clair). */
    val accent: Boolean = false,
    val nom: Boolean = true,
    val boutons: Boolean = true,
    /** Faux = première image seulement (solution de secours si l'animation rame). */
    val anime: Boolean = true,
) {
    enum class Theme(val label: String) { SOMBRE("Sombre"), CLAIR("Clair") }
    enum class Fond(val label: String) { PLEIN("Plein"), TRANSPARENT("Transparent") }
    enum class Forme(val label: String) { ROND("Ronds"), CARRE("Carrés") }
    enum class Taille(val label: String) { PETIT("Petits"), MOYEN("Moyens"), GRAND("Gros") }

    fun versJson(): String = JSONObject()
        .put("theme", theme.name).put("fond", fond.name).put("forme", forme.name).put("taille", taille.name)
        .put("accent", accent).put("nom", nom).put("boutons", boutons).put("anime", anime)
        .toString()

    companion object {
        fun defaut(format: Format) = StyleWidget(
            theme = Theme.SOMBRE,
            // Le widget rond n'a de sens que sans cadre autour.
            fond = if (format == Format.ROND) Fond.TRANSPARENT else Fond.PLEIN,
        )

        fun depuisJson(texte: String, format: Format): StyleWidget {
            val d = defaut(format)
            val j = runCatching { JSONObject(texte) }.getOrNull() ?: return d
            fun <E : Enum<E>> e(cle: String, valeurs: Array<E>, defaut: E) =
                valeurs.firstOrNull { it.name == j.optString(cle) } ?: defaut
            return StyleWidget(
                theme = e("theme", Theme.entries.toTypedArray(), d.theme),
                fond = e("fond", Fond.entries.toTypedArray(), d.fond),
                forme = e("forme", Forme.entries.toTypedArray(), d.forme),
                taille = e("taille", Taille.entries.toTypedArray(), d.taille),
                accent = j.optBoolean("accent", d.accent),
                nom = j.optBoolean("nom", d.nom),
                boutons = j.optBoolean("boutons", d.boutons),
                anime = j.optBoolean("anime", d.anime),
            )
        }
    }
}

/** Styles de tous les widgets posés, gardés sur le téléphone. */
class StylesWidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso_widgets", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun lire(id: Int, format: Format): StyleWidget =
        prefs.getString(cle(id), null)?.let { StyleWidget.depuisJson(it, format) } ?: StyleWidget.defaut(format)

    fun ecrire(id: Int, style: StyleWidget) {
        prefs.edit().putString(cle(id), style.versJson()).apply()
        _revision.value++
    }

    fun supprimer(id: Int) {
        prefs.edit().remove(cle(id)).apply()
    }

    private fun cle(id: Int) = "w$id"
}
