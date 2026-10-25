package fr.niakimbo.verso.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.verso.createur.MesModelesStore
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.ui.verre.Rouge
import java.text.Normalizer

internal const val TOUS = "Tous"
internal const val FAVORIS = "★"
internal const val RECENTS = "◷ Récents"
internal const val MES = MesModelesStore.RAYON

/**
 * Rayon choisi et recherche de la bibliothèque, partagés entre les puces dans la page et
 * la barre collante en verre qui apparaît en haut quand on a défilé plus bas.
 */
class FiltreBibliotheque {
    var rayon by mutableStateOf(TOUS)
    var recherche by mutableStateOf("")
    var chercher by mutableStateOf(false)

    /** Bas des puces de la page dans la fenêtre (px) : remontées sous le haut de l'écran, la barre collante prend le relais. */
    var basPuces by mutableFloatStateOf(Float.MAX_VALUE)

    /** Rayons proposés et nombre de modèles dans chacun (remplis par la bibliothèque). */
    var rayons by mutableStateOf<List<Pair<String, Int>>>(emptyList())

    fun basculerRecherche() {
        chercher = !chercher
        if (!chercher) recherche = ""
    }

    companion object {
        /** Sans accents ni majuscules : « egal » trouve « Égaliseur ». */
        fun simple(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
    }
}

/** La rangée de puces (🔍 + rayons) et, si ouverte, le champ de recherche. */
@Composable
fun LignePuces(filtre: FiltreBibliotheque, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PuceVerre("🔍", filtre.chercher) { filtre.basculerRecherche() }
            filtre.rayons.forEach { (r, n) ->
                PuceVerre("$r $n", r == filtre.rayon && filtre.recherche.isBlank()) {
                    filtre.rayon = r
                    filtre.recherche = ""
                }
            }
        }
        if (filtre.chercher) {
            PanneauVerre(Modifier.fillMaxWidth().padding(top = 10.dp), forme = RoundedCornerShape(50)) {
                BasicTextField(
                    value = filtre.recherche,
                    onValueChange = { filtre.recherche = it.take(30) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Rouge),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    decorationBox = { champ ->
                        if (filtre.recherche.isEmpty()) Text("Chercher un modèle (pluie, cœur, chat…)", color = Discret, fontSize = 15.sp)
                        champ()
                    },
                )
            }
        }
    }
}

/** Barre collante en verre, en haut de l'écran principal, quand les puces de la page ont défilé hors de vue. */
@Composable
fun BarreRayonsCollante(filtre: FiltreBibliotheque, modifier: Modifier = Modifier) {
    PanneauVerre(modifier.fillMaxWidth(), forme = RoundedCornerShape(26.dp)) {
        LignePuces(filtre, Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
    }
}
