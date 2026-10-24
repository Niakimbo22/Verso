package fr.niakimbo.verso.toy

import android.content.Context
import android.net.Uri

/**
 * Cherche dans les réglages Android ceux qui parlent de Glyph / Toy / AOD.
 *
 * But : trouver où Nothing OS mémorise le Toy AOD choisi, pour que Verso sache s'il est
 * sélectionné même quand le système ne l'a pas (encore) lié. Lecture seule.
 * Android peut refuser la lecture de certaines tables : c'est noté tel quel.
 */
object ReglagesGlyph {
    private val TABLES = listOf("global", "secure", "system")
    private val MOTS = listOf("glyph", "toy", "aod", "matrix")

    fun chercher(context: Context): List<String> {
        val lignes = mutableListOf<String>()
        for (table in TABLES) {
            try {
                context.contentResolver.query(
                    Uri.parse("content://settings/$table"),
                    arrayOf("name", "value"),
                    null,
                    null,
                    null,
                )?.use { c ->
                    var trouves = 0
                    while (c.moveToNext()) {
                        val nom = c.getString(0) ?: continue
                        if (MOTS.none { nom.contains(it, ignoreCase = true) }) continue
                        trouves++
                        lignes += "$table/$nom = ${c.getString(1)?.take(120)}"
                    }
                    lignes += "$table : ${c.count} réglages lus, $trouves liés au Glyph"
                } ?: run { lignes += "$table : lecture impossible (null)" }
            } catch (e: Exception) {
                lignes += "$table : refusé (${e.javaClass.simpleName}: ${e.message?.take(80)})"
            }
        }
        return lignes
    }
}
