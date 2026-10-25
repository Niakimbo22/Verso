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
 * Éditeur dessin (étape 7, version simple du 25/10).
 *
 * Pendant le dessin, la vraie matrice montre le dessin en cours. Dès que l'éditeur quitte l'écran
 * (fermé, ou simplement en arrière-plan), la matrice reprend le modèle actif : l'éditeur ne doit
 * jamais garder la matrice pour lui (cause du « radar figé » de la v0.1.28).
 */
class CreateurActivity : ComponentActivity() {

    private val app get() = application as VersoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.matrix.connect()
        val id = intent.getStringExtra(EXTRA_ID)
        val copie = intent.getStringExtra(EXTRA_COPIE)
        setContent {
            EditeurDessin(app, id, copie, onFermer = ::finish)
        }
    }

    override fun onPause() {
        super.onPause()
        rendreLaMatrice()
    }

    /** Remet le modèle actif sur la matrice (ou l'éteint s'il n'y en a pas). */
    private fun rendreLaMatrice() {
        val app = app
        val edite = intent.getStringExtra(EXTRA_ID)
        app.appScope.launch {
            val actif = app.store.modeleActif
            if (actif == null) {
                app.player.eteindre()
            } else {
                app.player.reprendre()
                // Le dessin modifié est celui affiché : on relance sa nouvelle version.
                if (actif == edite) app.player.rafraichir(actif)
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_COPIE = "copie"

        /** [id] : dessin créé à modifier. [copie] : modèle de la bibliothèque à reprendre comme point de départ. */
        fun ouvrir(context: Context, id: String? = null, copie: String? = null) {
            context.startActivity(
                Intent(context, CreateurActivity::class.java)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_COPIE, copie),
            )
        }
    }
}
