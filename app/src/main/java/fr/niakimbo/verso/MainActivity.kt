package fr.niakimbo.verso

import android.content.Intent
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
                catalogue = app.catalogue,
                reglages = app.reglages,
                store = app.store,
                journalToy = app.journalToy,
                // toyLie change à chaque liaison/déliaison : les dates relues ici sont à jour.
                etatToy = EtatToy.calculer(toyLie, app.store.derniereLiaison, app.store.dernierDeliement),
                onChoisirToy = { app.journalToy.ajouter(ChoixToy.ouvrir(this)) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as VersoApp
        app.player.setAppVisible(true)
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
