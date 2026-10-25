package fr.niakimbo.verso.createur

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.launch

/**
 * Créateur de modèles (étape 7) : éditeur dessin (statique ou animé) ou éditeur par blocs (dynamique).
 *
 * Pendant l'édition, ce qui est dessiné s'affiche en direct sur la vraie matrice (désactivable).
 * En sortant, la matrice reprend le modèle actif d'avant (ou s'éteint s'il n'y en avait pas).
 */
class CreateurActivity : ComponentActivity() {

    private val app get() = application as VersoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.matrix.connect()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: DESSIN
        val id = intent.getStringExtra(EXTRA_ID)
        val copie = intent.getStringExtra(EXTRA_COPIE)
        setContent {
            if (mode == BLOCS) {
                EditeurBlocs(app, id, onFermer = ::finish)
            } else {
                EditeurDessin(app, id, copie, onFermer = ::finish)
            }
        }
    }

    override fun onDestroy() {
        // Rend la matrice au modèle actif (l'aperçu en direct l'avait remplacé).
        if (isFinishing) {
            val app = app
            val edite = intent.getStringExtra(EXTRA_ID)
            app.appScope.launch {
                val actif = app.store.modeleActif
                if (actif == null) {
                    app.player.eteindre()
                } else {
                    app.player.reprendre()
                    // Le modèle modifié est celui affiché : on relance sa nouvelle version.
                    if (actif == edite) app.player.rafraichir(actif)
                }
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_ID = "id"
        const val EXTRA_COPIE = "copie"
        const val DESSIN = "dessin"
        const val BLOCS = "blocs"

        /** [id] : modèle créé à modifier. [copie] : modèle de la bibliothèque à reprendre comme point de départ. */
        fun ouvrir(context: Context, mode: String, id: String? = null, copie: String? = null) {
            context.startActivity(
                Intent(context, CreateurActivity::class.java)
                    .putExtra(EXTRA_MODE, mode)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_COPIE, copie),
            )
        }
    }
}
