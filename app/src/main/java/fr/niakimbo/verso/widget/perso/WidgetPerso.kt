package fr.niakimbo.verso.widget.perso

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un widget créé dans Verso (« Mes widgets ») : une liste de blocs empilés (ou matrice à gauche
 * et le reste à droite) et un style. Plusieurs widgets posés peuvent utiliser le même, ou chacun le sien.
 */
data class WidgetPerso(
    val id: String,
    val nom: String,
    val blocs: List<Bloc>,
    val disposition: Disposition = Disposition.COLONNE,
    val fond: Fond = Fond.PLEIN,
    val theme: Theme = Theme.SOMBRE,
    val alignement: Alignement = Alignement.CENTRE,
    /** LEDs de la matrice en rouge Nothing. */
    val ledsRouges: Boolean = false,
    val pointsCarres: Boolean = false,
) {
    enum class Disposition(val label: String) { COLONNE("Empilé"), LIGNE("Matrice à gauche") }
    enum class Fond(val label: String) { PLEIN("Plein"), VERRE("Verre"), TRANSPARENT("Aucun") }
    enum class Theme(val label: String) { SOMBRE("Sombre"), CLAIR("Clair") }
    enum class Alignement(val label: String) { GAUCHE("Gauche"), CENTRE("Centre"), DROITE("Droite") }

    /** Un élément du widget. [texte] ne sert qu'au bloc Texte. */
    data class Bloc(
        val type: Type,
        val taille: Taille = Taille.M,
        val couleur: Couleur = Couleur.NORMALE,
        val texte: String = "",
    )

    enum class Type(val label: String, val symbole: String, val aide: String) {
        MATRICE("Matrice", "◉", "Le modèle affiché, animé comme au dos. Tap = choisir un modèle."),
        HEURE("Heure", "◷", "L'heure, tenue à jour par le téléphone lui-même."),
        DATE("Date", "▦", "Le jour et la date (« lundi 26 octobre »)."),
        NOM("Nom du modèle", "Aa", "Le nom du modèle affiché au dos."),
        BATTERIE("Batterie", "▮", "Le niveau de batterie, avec ⚡ en charge."),
        AUTO("Règle auto", "⟳", "La règle automatique en cours (rien sinon)."),
        TEXTE("Texte", "T", "Ton propre texte."),
        BOUTONS("Boutons", "‹⏻›", "Précédent, allumer / éteindre (le vrai bouton Glyph), suivant."),
        ESPACE("Espace", "␣", "Un peu d'air entre deux blocs."),
    }

    enum class Taille(val label: String) { S("Petit"), M("Moyen"), L("Grand") }
    enum class Couleur(val label: String) { NORMALE("Normale"), ROUGE("Rouge"), DISCRETE("Discrète") }

    fun versJson(): JSONObject = JSONObject()
        .put("id", id).put("nom", nom)
        .put("disposition", disposition.name).put("fond", fond.name).put("theme", theme.name)
        .put("alignement", alignement.name).put("ledsRouges", ledsRouges).put("pointsCarres", pointsCarres)
        .put("blocs", JSONArray(blocs.map {
            JSONObject().put("type", it.type.name).put("taille", it.taille.name).put("couleur", it.couleur.name).put("texte", it.texte)
        }))

    companion object {
        private inline fun <reified E : Enum<E>> e(o: JSONObject, cle: String, defaut: E): E =
            enumValues<E>().firstOrNull { it.name == o.optString(cle) } ?: defaut

        fun depuisJson(o: JSONObject): WidgetPerso? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val a = o.optJSONArray("blocs") ?: JSONArray()
            val blocs = (0 until a.length()).mapNotNull { i ->
                val b = a.optJSONObject(i) ?: return@mapNotNull null
                val type = Type.entries.firstOrNull { it.name == b.optString("type") } ?: return@mapNotNull null
                Bloc(type, e(b, "taille", Taille.M), e(b, "couleur", Couleur.NORMALE), b.optString("texte").take(40))
            }
            return WidgetPerso(
                id = id,
                nom = o.optString("nom").ifBlank { "Widget" },
                blocs = blocs,
                disposition = e(o, "disposition", Disposition.COLONNE),
                fond = e(o, "fond", Fond.PLEIN),
                theme = e(o, "theme", Theme.SOMBRE),
                alignement = e(o, "alignement", Alignement.CENTRE),
                ledsRouges = o.optBoolean("ledsRouges", false),
                pointsCarres = o.optBoolean("pointsCarres", false),
            )
        }

        /** Points de départ proposés à la création. */
        fun modeles(id: () -> String): List<WidgetPerso> = listOf(
            WidgetPerso(id(), "Horloge + matrice", listOf(Bloc(Type.HEURE, Taille.L), Bloc(Type.DATE, Taille.S, Couleur.DISCRETE), Bloc(Type.ESPACE), Bloc(Type.MATRICE, Taille.M))),
            WidgetPerso(id(), "Lecteur", listOf(Bloc(Type.MATRICE, Taille.M), Bloc(Type.NOM), Bloc(Type.AUTO, Taille.S, Couleur.ROUGE), Bloc(Type.BOUTONS)), disposition = Disposition.LIGNE, fond = Fond.VERRE, alignement = Alignement.GAUCHE),
            WidgetPerso(id(), "Infos", listOf(Bloc(Type.HEURE, Taille.L), Bloc(Type.DATE, Taille.S), Bloc(Type.BATTERIE, Taille.S, Couleur.DISCRETE)), alignement = Alignement.GAUCHE),
            WidgetPerso(id(), "Matrice", listOf(Bloc(Type.MATRICE, Taille.L)), fond = Fond.TRANSPARENT),
            WidgetPerso(id(), "Vide", emptyList()),
        )
    }
}

/** Mes widgets (designs) et le design choisi pour chaque widget posé sur l'écran d'accueil. */
class WidgetsPersoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso_widgets_perso", Context.MODE_PRIVATE)

    private val _tous = MutableStateFlow(lire())
    val tous: StateFlow<List<WidgetPerso>> = _tous

    private val _revision = MutableStateFlow(0)

    /** Change à chaque modification (design ou attribution) : les widgets posés se redessinent. */
    val revision: StateFlow<Int> = _revision

    fun nouvelId(): String = "w-" + java.lang.Long.toString(System.currentTimeMillis(), 36) + (0..999).random()

    fun parId(id: String?): WidgetPerso? = _tous.value.firstOrNull { it.id == id }

    @Synchronized
    fun enregistrer(w: WidgetPerso) {
        val liste = _tous.value.toMutableList()
        val i = liste.indexOfFirst { it.id == w.id }
        if (i >= 0) liste[i] = w else liste.add(w)
        ecrire(liste)
    }

    @Synchronized
    fun supprimer(id: String) = ecrire(_tous.value.filter { it.id != id })

    /** Design affiché par le widget posé [appWidgetId] (à défaut : le premier de la liste). */
    fun pour(appWidgetId: Int): WidgetPerso? = parId(prefs.getString("pose_$appWidgetId", null)) ?: _tous.value.firstOrNull()

    fun idPour(appWidgetId: Int): String? = prefs.getString("pose_$appWidgetId", null)

    fun attribuer(appWidgetId: Int, designId: String) {
        prefs.edit().putString("pose_$appWidgetId", designId).apply()
        _revision.value++
    }

    fun oublier(appWidgetId: Int) {
        prefs.edit().remove("pose_$appWidgetId").apply()
    }

    private fun ecrire(liste: List<WidgetPerso>) {
        prefs.edit().putString("designs", JSONArray(liste.map { it.versJson() }).toString()).apply()
        _tous.value = liste
        _revision.value++
    }

    private fun lire(): List<WidgetPerso> = try {
        val a = JSONArray(prefs.getString("designs", null) ?: "[]")
        (0 until a.length()).mapNotNull { WidgetPerso.depuisJson(a.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }
}
