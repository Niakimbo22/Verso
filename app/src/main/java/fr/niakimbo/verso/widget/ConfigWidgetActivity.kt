package fr.niakimbo.verso.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Modele
import kotlinx.coroutines.launch

private val Rouge = Color(0xFFD71921)
private val Discret = Color(0xFF8A8A8A)
private val Panneau = Color(0xFF111111)
private val Bord = Color(0xFF2A2A2A)

/**
 * Réglage d'un widget : à la pose, puis via appui long › « Paramètres du widget ».
 * L'aperçu est le vrai widget (mêmes RemoteViews que sur l'écran d'accueil), animation comprise.
 */
class ConfigWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultat = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // Retour arrière = widget annulé (à la pose) ou réglages inchangés.
        setResult(RESULT_CANCELED, resultat)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = application as VersoApp
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        val format = Format.depuisClasse(info?.provider?.className) ?: Format.CARRE
        val initial = app.stylesWidget.lire(id, format)

        enableEdgeToEdge()
        setContent {
            EcranConfig(app, format, initial) { style ->
                app.stylesWidget.ecrire(id, style)
                lifecycleScope.launch {
                    WidgetMaj.majTous(app)
                    setResult(RESULT_OK, resultat)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun EcranConfig(app: VersoApp, format: Format, initial: StyleWidget, onValider: (StyleWidget) -> Unit) {
    var style by remember { mutableStateOf(initial) }
    val modele by produceState<Modele?>(null) {
        value = app.store.modeleActif?.let { app.catalogue.resoudre(it) }
            ?: app.catalogue.contenu().entrees.firstOrNull { !it.cache }?.let(app.catalogue::resoudre)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Widget ${format.label}", color = Color.White, fontSize = 26.sp)
        Spacer(Modifier.height(16.dp))

        // Faux fond d'écran, pour juger le mode transparent.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A4A6B), Color(0xFF8C5A7A), Color(0xFFD9A066))))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val (l, h) = taillesApercu(format)
            AndroidView(
                factory = { FrameLayout(it) },
                update = { cadre ->
                    val rv = RenduWidget.construire(cadre.context, format, style, modele, cliquable = false)
                    cadre.removeAllViews()
                    cadre.addView(rv.apply(cadre.context, cadre))
                },
                modifier = Modifier.size(l, h),
            )
        }
        Spacer(Modifier.height(24.dp))

        Options("Thème", StyleWidget.Theme.entries.map { it to it.label }, style.theme) { style = style.copy(theme = it) }
        Options("Fond", StyleWidget.Fond.entries.map { it to it.label }, style.fond) { style = style.copy(fond = it) }
        Options("Forme des points", StyleWidget.Forme.entries.map { it to it.label }, style.forme) { style = style.copy(forme = it) }
        Options("Taille des points", StyleWidget.Taille.entries.map { it to it.label }, style.taille) { style = style.copy(taille = it) }
        val clair = style.theme == StyleWidget.Theme.CLAIR
        Options(
            "LEDs allumées",
            listOf(false to if (clair) "Noires" else "Blanches", true to "Rouges"),
            style.accent,
        ) { style = style.copy(accent = it) }
        Options(
            "Animation",
            listOf(true to "Animé", false to "Fixe"),
            style.anime,
            aide = "Animé : l'animation tourne seulement quand le widget est à l'écran. Fixe : première image, si ça rame.",
        ) { style = style.copy(anime = it) }
        if (format.aNom) {
            Options("Nom du modèle", listOf(true to "Affiché", false to "Masqué"), style.nom) { style = style.copy(nom = it) }
        }
        if (format.aFleches || format.aListe) {
            Options(
                "Boutons",
                listOf(true to "Affichés", false to "Masqués"),
                style.boutons,
                aide = if (format.aFleches) "‹ › = précédent / suivant, ☰ = choisir un modèle." else "☰ = choisir un modèle.",
            ) { style = style.copy(boutons = it) }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Tap sur la matrice = favori suivant (toute la bibliothèque s'il n'y a aucun favori).",
            color = Discret, fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onValider(style) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Rouge, contentColor = Color.White),
        ) { Text("Enregistrer") }
    }
}

private fun taillesApercu(format: Format): Pair<Dp, Dp> = when (format) {
    Format.CARRE, Format.ROND -> 170.dp to 170.dp
    Format.BARRE -> 300.dp to 64.dp
    Format.MINI -> 80.dp to 80.dp
}

@Composable
private fun <T> Options(titre: String, options: List<Pair<T, String>>, choisi: T, aide: String? = null, onChoix: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(titre.uppercase(), color = Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (valeur, label) ->
                val actif = valeur == choisi
                val forme = RoundedCornerShape(12.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .clip(forme)
                        .background(if (actif) Rouge else Panneau)
                        .border(1.dp, if (actif) Rouge else Bord, forme)
                        .clickable { onChoix(valeur) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = Color.White, fontSize = 13.sp) }
            }
        }
        aide?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Discret, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
