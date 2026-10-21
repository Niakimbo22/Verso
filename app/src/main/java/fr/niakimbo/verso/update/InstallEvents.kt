package fr.niakimbo.verso.update

import kotlinx.coroutines.flow.MutableStateFlow

/** Résultat d'une installation, posté par [InstallResultReceiver] (même processus). */
object InstallEvents {
    val failure = MutableStateFlow<String?>(null)
}
