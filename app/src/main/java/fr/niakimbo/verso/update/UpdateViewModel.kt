package fr.niakimbo.verso.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.niakimbo.verso.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release, val awaitingPermission: Boolean = false) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String, val release: Release? = null) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Checking)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        check()
        viewModelScope.launch {
            InstallEvents.failure.collect { message ->
                if (message != null) {
                    _state.value = UpdateState.Error(message)
                    InstallEvents.failure.value = null
                }
            }
        }
    }

    fun check() {
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = try {
                val latest = UpdateChecker.fetchLatest()
                if (latest != null && UpdateChecker.isNewer(latest.version, BuildConfig.VERSION_NAME)) {
                    UpdateState.Available(latest)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Vérification impossible")
            }
        }
    }

    /** L'utilisateur doit d'abord autoriser « installer des apps inconnues » ; on reprend au retour. */
    fun awaitPermission() {
        val current = _state.value
        if (current is UpdateState.Available) _state.value = current.copy(awaitingPermission = true)
    }

    fun onResume(canInstall: Boolean) {
        val current = _state.value
        if (current is UpdateState.Available && current.awaitingPermission && canInstall) {
            downloadAndInstall(current.release)
        }
    }

    fun downloadAndInstall(release: Release) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                _state.value = UpdateState.Downloading(0f)
                val apk = ApkInstaller.download(context, release.apkUrl) { progress ->
                    _state.value = UpdateState.Downloading(progress)
                }
                _state.value = UpdateState.Installing
                ApkInstaller.install(context, apk)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Mise à jour impossible", release)
            }
        }
    }
}
