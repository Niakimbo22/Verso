package fr.niakimbo.verso

import android.app.Application
import fr.niakimbo.verso.matrix.GlyphBackend
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixHistory
import fr.niakimbo.verso.modele.ModelePlayer
import fr.niakimbo.verso.modele.ModeleRepository
import fr.niakimbo.verso.modele.ModeleStore
import fr.niakimbo.verso.toy.JournalToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Porte l'unique [MatrixController] du processus : une seule connexion à la Glyph Matrix,
 * partagée par l'écran et le Glyph Toy (et plus tard le widget et les autres apps).
 */
class VersoApp : Application() {
    /** Vit aussi longtemps que le processus (animations, Glyph Toy). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val matrixHistory: MatrixHistory by lazy { MatrixHistory(this) }
    val matrix: MatrixController by lazy { GlyphBackend.create(this, matrixHistory) }
    val modeles: ModeleRepository by lazy { ModeleRepository(this) }
    val store: ModeleStore by lazy { ModeleStore(this) }
    val player: ModelePlayer by lazy { ModelePlayer(this, matrix, modeles, store, appScope) }
    val journalToy: JournalToy by lazy { JournalToy(this) }

    /** Vrai tant que le système a lié le Glyph Toy de Verso (Verso choisi comme Toy). */
    val toyLie = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        // Chaque (re)démarrage du processus est noté : on voit qui a réveillé Verso
        // (ouverture de l'app, liaison du Toy, démarrage du téléphone).
        journalToy.ajouter("Processus Verso démarré")
    }
}
