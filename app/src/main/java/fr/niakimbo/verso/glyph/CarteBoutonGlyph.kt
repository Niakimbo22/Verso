package fr.niakimbo.verso.glyph

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.rapide.ActionsRapides
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import kotlinx.coroutines.launch

/**
 * État du vrai bouton Glyph sous la grande matrice.
 * - Service pas activé : explication courte + guide pas à pas pour l'activer.
 * - Activé : état de la Glyph Interface et un bouton pour l'allumer / l'éteindre vraiment.
 */
@Composable
fun CarteBoutonGlyph() {
    val context = LocalContext.current
    val app = context.applicationContext as VersoApp
    val disponible by BoutonGlyph.disponible.collectAsStateWithLifecycle()
    val allume by BoutonGlyph.allume.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var guide by remember { mutableStateOf(false) }
    var occupe by remember { mutableStateOf(false) }

    if (disponible) {
        PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(22.dp), actif = allume == false) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(if (allume == false) Rouge else if (allume == true) Color(0xFF3DDC84) else Discret, CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (allume) {
                            true -> "Glyph allumé"
                            false -> "Glyph éteint"
                            null -> "Bouton Glyph prêt"
                        },
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("⏻ dans Verso appuie sur la vraie tuile Glyph", color = Discret, fontSize = 11.sp)
                }
                BoutonVerre(
                    if (occupe) "…" else if (allume == false) "Allumer" else "Éteindre",
                    {
                        if (occupe) return@BoutonVerre
                        occupe = true
                        scope.launch {
                            val r = if (allume == false) ActionsRapides.rallumer(app) else ActionsRapides.eteindre(app)
                            if (r == BoutonGlyph.Resultat.Introuvable) {
                                Toast.makeText(context, "Tuile Glyph introuvable dans le menu déroulant (voir le journal du Toy)", Toast.LENGTH_LONG).show()
                            }
                            occupe = false
                        }
                    },
                    principal = allume == false, taille = 13.sp,
                )
            }
        }
        return
    }

    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("⏻ Vrai bouton Glyph : pas encore activé", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sans lui, si le Glyph est coupé dans le menu déroulant, Verso ne peut pas le rallumer. " +
                    "Une fois activé, ⏻ (app, mini-lecteur, widget, tuile, raccourci) appuie sur la tuile Glyph à ta place.",
                color = Discret, fontSize = 12.sp, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (guide) "▾ Comment l'activer (1 min)" else "▸ Comment l'activer (1 min)",
                color = Color.White, fontSize = 13.sp,
                modifier = Modifier.appui({ guide = !guide }).padding(vertical = 6.dp),
            )
            if (guide) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Etape("1", "Ouvre la fiche de Verso, touche ⋮ en haut à droite, puis « Autoriser les paramètres restreints ». (Si ce choix n'existe pas, passe à l'étape 2.)")
                    BoutonVerre("Ouvrir la fiche de Verso", {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }, Modifier.fillMaxWidth(), taille = 13.sp)
                    Etape("2", "Dans Accessibilité, touche « Verso · bouton Glyph » (parfois dans « Applications téléchargées »), puis active-le et accepte.")
                    BoutonVerre("Ouvrir Accessibilité", {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.fillMaxWidth(), principal = true, taille = 13.sp)
                    Etape("3", "Reviens ici : cette carte affiche alors l'état du Glyph et un bouton pour tester.")
                }
            }
        }
    }
}

@Composable
private fun Etape(numero: String, texte: String) {
    Row {
        Text(numero, color = Rouge, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        Text(texte, color = Discret, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
