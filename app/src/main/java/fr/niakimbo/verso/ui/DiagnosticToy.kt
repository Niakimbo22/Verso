package fr.niakimbo.verso.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.matrix.Canal
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.modele.ModeleStore
import fr.niakimbo.verso.toy.JournalToy
import fr.niakimbo.verso.toy.ReglagesGlyph

private val Dim = Color(0xFF8A8A8A)

/**
 * Partie « Glyph Toy » du Diagnostic : canal utilisé, journal persistant du Toy
 * (pour savoir ce qui se passe app fermée / écran éteint) et expériences.
 */
@Suppress("DEPRECATION") // LocalClipboardManager reste le plus simple pour copier du texte
@Composable
fun DiagnosticToy(controller: MatrixController, journal: JournalToy, store: ModeleStore) {
    val canal by controller.canal.collectAsStateWithLifecycle()
    val lignes by journal.lignes.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    PanelBox {
        Text("GLYPH TOY", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        MonoLine(
            "Canal : " + if (canal == Canal.TOY) "Toy (setMatrixFrame)" else "App (setAppMatrixFrame)",
            if (canal == Canal.TOY) Color(0xFF3DDC84) else Color(0xFFDDDDDD),
        )
        MonoLine("Images envoyées : ${controller.imagesEnvoyees}")
        Spacer(Modifier.height(8.dp))
        val context = LocalContext.current
        OutlinedButton(
            onClick = {
                journal.ajouter("Recherche des réglages Glyph…")
                ReglagesGlyph.chercher(context).forEach(journal::ajouter)
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) { Text("Chercher les réglages Glyph", fontSize = 12.sp) }
    }

    Spacer(Modifier.height(16.dp))
    PanelBox {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "JOURNAL DU TOY (${lignes.size})",
                color = Dim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(journal.asText())) }) {
                Text("Copier", color = Color.White, fontSize = 12.sp)
            }
            TextButton(onClick = journal::vider) {
                Text("Vider", color = Dim, fontSize = 12.sp)
            }
        }
        if (lignes.isEmpty()) {
            MonoLine("(vide : Verso n'a jamais été lancé comme Toy)")
        } else {
            lignes.asReversed().take(40).forEach { MonoLine(it) }
        }
    }

    Spacer(Modifier.height(16.dp))
    PanelBox {
        Text("RÉGLAGES FIXES", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Text(
            "Animations écran éteint : toujours actives (processeur gardé éveillé pendant l'animation). " +
                "Délai d'extinction de la matrice : celui du système.",
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}
