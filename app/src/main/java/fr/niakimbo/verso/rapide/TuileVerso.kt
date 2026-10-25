package fr.niakimbo.verso.rapide

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.launch

/**
 * Tuile « Verso » des réglages rapides (menu déroulant) : affiche le modèle en cours ;
 * tap = modèle suivant (ou rallumer si la matrice est éteinte) ; appui long = ouvrir Verso.
 */
class TuileVerso : TileService() {
    private val app get() = application as VersoApp

    override fun onStartListening() {
        super.onStartListening()
        maj()
    }

    override fun onClick() {
        super.onClick()
        app.appScope.launch {
            if (app.store.modeleActif == null) ActionsRapides.basculer(app) else ActionsRapides.changer(app, 1)
            maj()
        }
    }

    private fun maj() {
        val tuile = qsTile ?: return
        val actif = app.player.actif.value?.nom ?: app.store.modeleActif?.let { "Verso" }
        tuile.label = "Verso"
        tuile.subtitle = actif ?: "Éteinte"
        tuile.state = if (app.store.modeleActif != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tuile.updateTile()
    }
}
