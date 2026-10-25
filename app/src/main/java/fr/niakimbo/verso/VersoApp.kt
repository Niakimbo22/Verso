package fr.niakimbo.verso

import android.app.Application
import fr.niakimbo.verso.createur.Capteurs
import fr.niakimbo.verso.createur.MesModelesStore
import fr.niakimbo.verso.matrix.GlyphBackend
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixHistory
import fr.niakimbo.verso.matrix.MatrixState
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

    @Volatile
    private var derniereRelance = 0L

    /**
     * Refait la connexion à la matrice (voir MatrixController.relancer). Appelé à chaque liaison du Toy,
     * à l'ouverture de l'app, à l'allumage de l'écran et par le bouton « Relancer la matrice ».
     * [delaiMin] évite de relancer en boucle ; dans ce cas, une matrice pas encore prête est juste connectée.
     */
    fun relancerMatrice(raison: String, delaiMin: Long = 0L) {
        val maintenant = System.currentTimeMillis()
        if (delaiMin > 0) {
            if (matrix.state.value != MatrixState.Ready) {
                matrix.connect()
                return
            }
            if (maintenant - derniereRelance < delaiMin) return
        }
        derniereRelance = maintenant
        matrix.relancer(raison)
    }

    /**
     * Redémarre le processus Verso : seul moyen de retrouver le service Glyph quand sa liaison
     * est perdue. [rouvrir] relance l'écran de Verso ; sinon le système relance tout seul le
     * Glyph Toy (service lié), qui reprend le modèle actif.
     */
    fun redemarrer(raison: String, rouvrir: Boolean) {
        journalToy.ajouter("Redémarrage de Verso : $raison")
        getSharedPreferences("verso", MODE_PRIVATE).edit()
            .putLong(CLE_DERNIER_REDEMARRAGE, System.currentTimeMillis()).commit()
        if (rouvrir) {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * App fermée et liaison Glyph perdue alors qu'elle avait marché : redémarrage automatique,
     * au plus une fois toutes les 10 min.
     */
    private fun surveillerLiaison() {
        // Matrice prête au moins une fois dans ce processus. Sinon (Glyph Interface coupée, horaire
        // du coucher…), redémarrer ne servirait à rien et tournerait en boucle : on ne le fait pas.
        val dejaPrete = java.util.concurrent.atomic.AtomicBoolean(false)
        appScope.launch { matrix.state.collect { if (it == MatrixState.Ready) dejaPrete.set(true) } }
        appScope.launch {
            matrix.redemarrageConseille.collect { perdu ->
                if (!perdu || !dejaPrete.get()) return@collect
                val dernier = getSharedPreferences("verso", MODE_PRIVATE).getLong(CLE_DERNIER_REDEMARRAGE, 0L)
                if (System.currentTimeMillis() - dernier < 10 * 60_000L) {
                    journalToy.ajouter("Liaison Glyph perdue, redémarrage automatique déjà tenté il y a moins de 10 min")
                    return@collect
                }
                if (player.appVisible) return@collect // l'utilisateur a le bouton « Redémarrer Verso »
                redemarrer("liaison au service Glyph perdue", rouvrir = false)
            }
        }
    }

    private companion object {
        const val CLE_DERNIER_REDEMARRAGE = "dernier_redemarrage"
    }

    /** Essai d'affichage par le canal app après un démarrage (DemarrageReceiver), annulé si le Toy se lie. */
    @Volatile
    var essaiDemarrage: Job? = null

    /** Démarrage de ce processus (le système met jusqu'à une minute à relier le Toy ensuite). */
    val demarrage: Long = System.currentTimeMillis()

    /** Vrai tant que le système a lié le Glyph Toy de Verso (Verso choisi comme Toy). */
    val toyLie = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        Capteurs.init(this)
        surveillerLiaison()
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
