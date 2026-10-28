package fr.niakimbo.verso.auto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret

/** Une règle active a besoin de savoir quelle app joue ou envoie une notif. */
fun besoinNotifs(regles: List<Regle>): Boolean = regles.any { r ->
    r.active && (
        r.conditions.any { it is Declencheur.Notification || it is Declencheur.Musique || it is Declencheur.AppOuverte } ||
            (r.action as? Action.Afficher)?.modele in Evaluation.MODELES_SON
        )
}

/**
 * La demande d'accès aux notifications (28/10, Nico : « créer une demande ») : une fenêtre qui s'ouvre
 * toute seule à l'ouverture de Verso tant qu'une règle en a besoin et que l'accès manque.
 * Android ne permet pas de l'accorder depuis une fenêtre : le bouton ouvre directement l'interrupteur de Verso,
 * et la fenêtre disparaît toute seule au retour si c'est accordé. « Plus tard » = jusqu'au prochain lancement.
 */
@Composable
fun DemandeNotifs(app: VersoApp) {
    val context = LocalContext.current
    val regles by app.moteurRegles.store.regles.collectAsStateWithLifecycle()
    var retour by remember { mutableIntStateOf(0) }
    var plusTard by remember { mutableStateOf(false) }
    val cycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cycle) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) retour++ }
        cycle.addObserver(o)
        onDispose { cycle.removeObserver(o) }
    }
    val autorise = remember(retour) { EcouteNotifs.autorise(context) }
    if (plusTard || autorise || !besoinNotifs(regles)) return
    AlertDialog(
        onDismissRequest = { plusTard = true },
        containerColor = Color(0xFF151515),
        title = { Text("🔔 Une autorisation à donner", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tes règles 🎵 son / 🔔 notif / 📱 app en ont besoin.", color = Color.White, fontSize = 15.sp)
                Text("1. Ouvre  →  2. Active « Verso »  →  3. Reviens", color = Discret, fontSize = 13.sp)
                Text("🔒 Grisé ? Fiche de Verso › ⋮ › Autoriser les paramètres restreints", color = Discret, fontSize = 12.sp)
                Text("👀 Verso lit seulement le nom de l'app.", color = Discret, fontSize = 12.sp)
            }
        },
        confirmButton = {
            BoutonVerre("Ouvrir", { EcouteNotifs.ouvrirReglage(context) }, Modifier.padding(start = 8.dp), principal = true, taille = 15.sp)
        },
        dismissButton = { TextButton(onClick = { plusTard = true }) { Text("Plus tard", color = Discret) } },
    )
}
