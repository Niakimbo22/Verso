package fr.niakimbo.verso

import android.app.Application
import fr.niakimbo.verso.createur.Capteurs
import fr.niakimbo.verso.createur.MesModelesStore
import fr.niakimbo.verso.matrix.GlyphBackend
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixHistory
import fr.niakimbo.verso.modele.ModelePlayer
import fr.niakimbo.verso.modele.ModeleRepository
import fr.niakimbo.verso.modele.FavorisStore
import fr.niakimbo.verso.modele.ModeleStore
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.ReglagesStore
import fr.niakimbo.verso.toy.JournalToy
import fr.niakimbo.verso.widget.StylesWidgetStore
import fr.niakimbo.verso.widget.WidgetMaj
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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
    val reglages: ReglagesStore by lazy { ReglagesStore(this) }
    val mesModeles: MesModelesStore by lazy { MesModelesStore(this) }
    val catalogue: Catalogue by lazy { Catalogue(modeles, reglages, mesModeles) }
    val player: ModelePlayer by lazy { ModelePlayer(this, matrix, catalogue, store, appScope) }
    val journalToy: JournalToy by lazy { JournalToy(this) }
    val favoris: FavorisStore by lazy { FavorisStore(this) }
    val stylesWidget: StylesWidgetStore by lazy { StylesWidgetStore(this) }

    /** Essai d'affichage par le canal app après un démarrage (DemarrageReceiver), annulé si le Toy se lie. */
    @Volatile
    var essaiDemarrage: Job? = null

    /** Vrai tant que le système a lié le Glyph Toy de Verso (Verso choisi comme Toy). */
    val toyLie = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        Capteurs.init(this)
        // Chaque (re)démarrage du processus est noté : on voit qui a réveillé Verso
        // (ouverture de l'app, liaison du Toy, démarrage du téléphone).
        journalToy.ajouter("Processus Verso démarré")

        // Widgets : redessinés quand le modèle actif, ses réglages ou le style d'un widget changent.
        appScope.launch {
            combine(player.actif, reglages.revision, stylesWidget.revision, mesModeles.revision) { actif, r, w, m -> listOf(actif?.id, r, w, m) }
                .collectLatest { WidgetMaj.majTous(this@VersoApp) }
        }
    }
}
