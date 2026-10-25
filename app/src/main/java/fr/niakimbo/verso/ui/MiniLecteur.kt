package fr.niakimbo.verso.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Navigation
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import fr.niakimbo.verso.widget.WidgetActions
import kotlinx.coroutines.launch

/**
 * Mini-lecteur flottant en verre (façon iPhone), en bas de l'écran principal : le modèle affiché
 * reste sous les yeux pendant qu'on fait défiler la bibliothèque.
 * ‹ › = modèle précédent / suivant (favoris, ou toute la bibliothèque, comme le widget) ; ⏻ = éteindre / rallumer ;
 * toucher le reste = remonter à la grande matrice.
 */
@Composable
fun MiniLecteur(app: VersoApp, modifier: Modifier = Modifier, onRemonter: () -> Unit) {
    val frame by app.matrix.lastFrame.collectAsStateWithLifecycle()
    val actif by app.player.actif.collectAsStateWithLifecycle()
    val enCours by app.moteurRegles.enCours.collectAsStateWithLifecycle()
    val regles by app.moteurRegles.store.regles.collectAsStateWithLifecycle()
    val autoActif by app.moteurRegles.store.actif.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val regle = regles.firstOrNull { it.id == enCours }?.takeIf { autoActif }

    fun changer(pas: Int) = scope.launch {
        val id = Navigation.voisin(WidgetActions.ordreDefilement(app), app.store.modeleActif, pas) ?: return@launch
        app.catalogue.resoudre(id)?.let(app.player::jouer)
    }

    fun basculer() = scope.launch {
        if (app.store.modeleActif != null) {
            app.player.eteindre()
        } else {
            val id = app.store.dernierModele ?: WidgetActions.ordreDefilement(app).firstOrNull() ?: return@launch
            app.catalogue.resoudre(id)?.let(app.player::jouer)
        }
    }

    PanneauVerre(modifier.fillMaxWidth(), forme = RoundedCornerShape(30.dp)) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).appui(onRemonter, vibrer = false), verticalAlignment = Alignment.CenterVertically) {
                DotMatrix(frame, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        actif?.nom ?: if (frame == null) "Matrice éteinte" else "Aperçu",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        regle?.let { "Auto · « ${it.nom} »" } ?: "Au dos du téléphone",
                        color = if (regle != null) Rouge else Discret, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Bouton("‹") { changer(-1) }
            Bouton(if (actif != null) "⏻" else "▶", couleur = if (actif != null) Color.White else Rouge) { basculer() }
            Bouton("›") { changer(1) }
        }
    }
}

@Composable
private fun Bouton(symbole: String, couleur: Color = Color.White, onClick: () -> Unit) {
    Text(
        symbole, color = couleur, fontSize = 22.sp,
        modifier = Modifier.appui(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
