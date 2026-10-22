package fr.niakimbo.verso.matrix

import android.content.Context

/**
 * Compilé quand l'AAR du SDK Glyph est absent : le build passe quand même,
 * et l'app tourne avec un contrôleur factice.
 */
internal object GlyphBackend {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, history: MatrixHistory): MatrixController = FakeMatrixController()
}
