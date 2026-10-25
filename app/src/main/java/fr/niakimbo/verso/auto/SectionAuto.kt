package fr.niakimbo.verso.auto

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp

/** Carte « Automatique » de l'écran principal : état en un coup d'œil, interrupteur, et accès aux règles. */
@Composable
fun SectionAuto(app: VersoApp) {
    val context = LocalContext.current
    val moteur = app.moteurRegles
    val regles by moteur.store.regles.collectAsStateWithLifecycle()
    val actif by moteur.store.actif.collectAsStateWithLifecycle()
    val enCours by moteur.enCours.collectAsStateWithLifecycle()
    val regle = regles.firstOrNull { it.id == enCours }?.takeIf { actif }

    Column(Modifier.fillMaxWidth()) {
        Etiquette("AUTOMATIQUE", Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .carte(actif = regle != null)
                .clickable { AutomatiqueActivity.ouvrir(context) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Picto(
                regle?.let { TypeQuand.de(it.quand) } ?: TypeQuand.HORAIRE,
                if (regle != null) Rouge else if (actif && regles.isNotEmpty()) Color.White else Discret,
                Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        regles.isEmpty() -> "Changer de modèle tout seul"
                        regle != null -> "En cours : « ${regle.nom} »"
                        else -> "${regles.count { it.active }} règle${if (regles.count { it.active } > 1) "s" else ""} active${if (regles.count { it.active } > 1) "s" else ""}"
                    },
                    color = Color.White, fontSize = 14.sp,
                )
                Text(
                    when {
                        regles.isEmpty() -> "La nuit, en charge, au réveil, casque branché…"
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
