package fr.niakimbo.verso.rapide

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.auto.AutomatiqueActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Raccourcis de l'icône Verso (appui long sur l'icône) : suivant, aléatoire, éteindre / rallumer,
 * Automatique. Écran invisible : fait l'action, montre un petit message et se ferme.
 */
class RaccourciActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VersoApp
        when (intent.action) {
            AUTOMATIQUE -> {
                AutomatiqueActivity.ouvrir(this)
                finish()
                return
            }
        }
        app.appScope.launch {
            when (intent.action) {
                SUIVANT -> ActionsRapides.changer(app, 1)
                ALEATOIRE -> ActionsRapides.aleatoire(app)
                BASCULER -> ActionsRapides.basculer(app)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RaccourciActivity, app.player.actif.value?.nom ?: "Matrice éteinte", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val SUIVANT = "fr.niakimbo.verso.SUIVANT"
        const val ALEATOIRE = "fr.niakimbo.verso.ALEATOIRE"
        const val BASCULER = "fr.niakimbo.verso.BASCULER"
        const val AUTOMATIQUE = "fr.niakimbo.verso.AUTOMATIQUE"
    }
}
