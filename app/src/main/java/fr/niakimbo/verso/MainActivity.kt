package fr.niakimbo.verso

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.niakimbo.verso.toy.ChoixToy
import fr.niakimbo.verso.toy.SurveillanceToy
import fr.niakimbo.verso.widget.SectionWidgets
import fr.niakimbo.verso.ui.EtatToy
import fr.niakimbo.verso.ui.VersoScreen
import kotlinx.coroutines.launch
import fr.niakimbo.verso.update.UpdateState
import fr.niakimbo.verso.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updates: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VersoApp
        app.matrix.connect()
        demanderNotifications()
        ouvrirChoixToySiDemande(intent)
        setContent {
            val state by updates.state.collectAsStateWithLifecycle()
            val toyLie by app.toyLie.collectAsStateWithLifecycle()
            VersoScreen(
                state = state,
                onUpdate = ::onUpdateClicked,
                onRetry = { current -> retry(current) },
                matrix = app.matrix,
                matrixHistory = app.matrixHistory,
                player = app.player,
                favoris = app.favoris,
                catalogue = app.catalogue,
                reglages = app.reglages,
                store = app.store,
                journalToy = app.journalToy,
                // toyLie change à chaque liaison/déliaison : les dates relues ici sont à jour.
                etatToy = EtatToy.calculer(toyLie, app.store.derniereLiaison, app.store.dernierDeliement, app.store.contexteDeliement),
                onChoisirToy = { app.journalToy.ajouter(ChoixToy.ouvrir(this)) },
                sectionWidgets = { SectionWidgets(app) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ouvrirChoixToySiDemande(intent)
    }

    /** Tap sur la notification « La matrice ne s'affiche plus » : direct au choix du Glyph Toy. */
    private fun ouvrirChoixToySiDemande(intent: Intent?) {
        if (intent?.getBooleanExtra(SurveillanceToy.EXTRA_CHOISIR_TOY, false) != true) return
        intent.removeExtra(SurveillanceToy.EXTRA_CHOISIR_TOY)
        val app = application as VersoApp
        app.journalToy.ajouter(ChoixToy.ouvrir(this))
    }

    /** Une fois : sans cette permission, Verso ne peut pas prévenir quand le Toy s'arrête. */
    private fun demanderNotifications() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as VersoApp
        app.player.setAppVisible(true)
        app.relancerMatrice("ouverture de Verso", delaiMin = 60_000L)
        // Relance le modèle mémorisé (ex. après que le système a fermé le processus).
        lifecycleScope.launch { app.player.reprendre() }
    }

    override fun onStop() {
        (application as VersoApp).player.setAppVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updates.onResume(packageManager.canRequestPackageInstalls())
    }

    private fun onUpdateClicked(state: UpdateState.Available) {
        if (packageManager.canRequestPackageInstalls()) {
            updates.downloadAndInstall(state.release)
        } else {
            updates.awaitPermission()
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun retry(state: UpdateState.Error) {
        val release = state.release
        if (release != null) updates.downloadAndInstall(release) else updates.check()
    }
}
