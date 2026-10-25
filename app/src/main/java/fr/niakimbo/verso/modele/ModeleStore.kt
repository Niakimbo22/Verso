package fr.niakimbo.verso.modele

import android.content.Context

/**
 * Ce qui doit survivre à la fermeture de l'app et au redémarrage :
 * le modèle actif (relu par le Glyph Toy) et les réglages expérimentaux du Diagnostic.
 */
class ModeleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso", Context.MODE_PRIVATE)

    /** Id du modèle à afficher, null = matrice éteinte. */
    var modeleActif: String?
        get() = prefs.getString(CLE_ACTIF, null)
        set(value) = prefs.edit().putString(CLE_ACTIF, value).apply()

    /** Expérience : garder le processeur éveillé pour que les animations tournent écran éteint. */
    var animationsEveillees: Boolean
        get() = prefs.getBoolean(CLE_EVEIL, false)
        set(value) = prefs.edit().putBoolean(CLE_EVEIL, value).apply()

    /**
     * Expérience : valeur passée à setGlyphMatrixTimeout (méthode non documentée du SDK).
     * null = on n'y touche pas (comportement du système).
     */
    var delaiExtinction: Boolean?
        get() = if (prefs.contains(CLE_DELAI)) prefs.getBoolean(CLE_DELAI, true) else null
        set(value) = prefs.edit().apply { if (value == null) remove(CLE_DELAI) else putBoolean(CLE_DELAI, value) }.apply()

    /** Dernière fois que le système a lié le Glyph Toy de Verso (ms epoch, 0 = jamais). */
    var derniereLiaison: Long
        get() = prefs.getLong(CLE_LIAISON, 0L)
        set(value) = prefs.edit().putLong(CLE_LIAISON, value).apply()

    /** Dernière fois que le système l'a délié (choix d'un autre Toy, ou fin de session). */
    var dernierDeliement: Long
        get() = prefs.getLong(CLE_DELIEMENT, 0L)
        set(value) = prefs.edit().putLong(CLE_DELIEMENT, value).apply()

    /** Dernier modèle affiché avant une extinction depuis le widget (pour le rallumer). */
    var dernierModele: String?
        get() = prefs.getString(CLE_DERNIER, null)
        set(value) = prefs.edit().putString(CLE_DERNIER, value).apply()

    /** Cause probable du dernier déliement (économie de batterie, Ne pas déranger…), null = inconnue. */
    var contexteDeliement: String?
        get() = prefs.getString(CLE_CONTEXTE, null)
        set(value) = prefs.edit().putString(CLE_CONTEXTE, value).apply()

    private companion object {
        const val CLE_DERNIER = "widget_dernier_modele"
        const val CLE_CONTEXTE = "toy_contexte_deliement"
        const val CLE_LIAISON = "toy_derniere_liaison"
        const val CLE_DELIEMENT = "toy_dernier_deliement"
        const val CLE_ACTIF = "modele_actif"
        const val CLE_EVEIL = "experience_eveil"
        const val CLE_DELAI = "experience_delai_extinction"
    }
}
