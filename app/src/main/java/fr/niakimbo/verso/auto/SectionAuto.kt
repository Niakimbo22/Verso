package fr.niakimbo.verso.auto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.Rouge

/** Carte « Automatique » de l'écran principal : état en un coup d'œil, interrupteur, et accès aux règles. */
@Composable
fun SectionAuto(app: VersoApp) {
    val context = LocalContext.current
    val moteur = app.moteurRegles
    val regles by moteur.store.regles.collectAsStateWithLifecycle()
    val actif by moteur.store.actif.collectAsStateWithLifecycle()
    val enCours by moteur.enCours.collectAsStateWithLifecycle()
    val regle = regles.firstOrNull { it.id == enCours }?.takeIf { actif }
    val nbActives = regles.count { it.active }

    Column(Modifier.fillMaxWidth()) {
        Etiquette("AUTOMATIQUE", Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        PanneauVerre(Modifier.fillMaxWidth(), actif = regle != null, onClick = { AutomatiqueActivity.ouvrir(context) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Picto(
                    regle?.let { TypeQuand.de(it.conditions.first()) } ?: TypeQuand.HORAIRE,
                    if (regle != null) Rouge else if (actif && regles.isNotEmpty()) Color.White else Discret,
                    Modifier.size(30.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            regles.isEmpty() -> "Changer de modèle tout seul"
                            regle != null -> "« ${regle.nom} » en cours"
                            else -> "$nbActives règle${if (nbActives > 1) "s" else ""} active${if (nbActives > 1) "s" else ""}"
                        },
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            regles.isEmpty() -> "La nuit, en charge, au réveil, casque…"
                            !actif -> "Automatique coupé"
                            else -> "Toucher pour gérer les règles"
                        },
                        color = Discret, fontSize = 12.sp,
                    )
                }
                if (regles.isNotEmpty()) {
                    Switch(
                        checked = actif,
                        onCheckedChange = { moteur.store.setActif(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
                    )
                } else {
                    Text("›", color = Discret, fontSize = 24.sp)
                }
            }
        }
    }
}
