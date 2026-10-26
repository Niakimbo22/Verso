package fr.niakimbo.verso.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp

/**
 * Liste des widgets posés, avec un accès direct à leurs réglages : plus simple que de chercher
 * « Paramètres du widget » dans le menu du lanceur. Rien ne s'affiche s'il n'y a aucun widget.
 */
@Composable
fun SectionWidgets(app: VersoApp, titre: String = "WIDGETS") {
    val context = LocalContext.current
    val revision by app.stylesWidget.revision.collectAsStateWithLifecycle()
    val widgets = remember(revision) {
        val manager = AppWidgetManager.getInstance(context)
        Format.entries.flatMap { f -> manager.getAppWidgetIds(ComponentName(context, f.classe)).map { f to it } }
    }
    if (widgets.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Text(
            titre, color = Color(0xFF8A8A8A), fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            widgets.forEachIndexed { i, (format, id) ->
                val style = app.stylesWidget.lire(id, format)
                fr.niakimbo.verso.ui.verre.PanneauVerre(
                    Modifier.fillMaxWidth(),
                    forme = RoundedCornerShape(20.dp),
                    onClick = {
                        context.startActivity(
                            Intent(context, ConfigWidgetActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        )
                    },
                ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${format.label} n°${i + 1}", color = Color.White, fontSize = 14.sp)
                        Text(
                            "Tap : ${style.tap.label} · ${style.theme.label} · ${if (style.anime) "Animé" else "Fixe"}",
                            color = Color(0xFF8A8A8A), fontSize = 12.sp,
                        )
                    }
                    Text("⚙", color = Color.White, fontSize = 18.sp)
                }
                }
            }
        }
    }
}
