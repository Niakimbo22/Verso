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
import fr.niakimbo.verso.ui.VersoScreen
import fr.niakimbo.verso.update.UpdateState
import fr.niakimbo.verso.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updates: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by updates.state.collectAsStateWithLifecycle()
            VersoScreen(
                state = state,
                onUpdate = ::onUpdateClicked,
                onRetry = { current -> retry(current) },
            )
        }
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
