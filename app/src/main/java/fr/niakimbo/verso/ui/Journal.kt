package fr.niakimbo.verso.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import fr.niakimbo.verso.BuildConfig
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.LocalFond
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import fr.niakimbo.verso.ui.verre.contourActif
import fr.niakimbo.verso.ui.verre.verre
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Journal des mises à jour (`assets/journal.json`, la plus récente en premier) : pour chaque version,
 * une phrase « sert à … » facile à lire, puis tous les détails sur demande.
 *
 * Règle d'écriture : chaque PR ajoute son entrée en tête avec `"version": null` (le numéro n'est connu
 * qu'à la fabrication) et remplit la version de l'entrée précédente. Une entrée de tête sans version
 * = la version installée. La Release GitHub reprend l'entrée de tête (`outils/notes_release.py`).
 */
object Journal {
    data class Section(val titre: String, val points: List<String>)

    data class Entree(
        val pr: Int,
        val version: String?,
        val date: String,
        val titre: String,
        val sert: String,
        val sections: List<Section>,
        val tester: List<String>,
    )

    @Volatile private var cache: List<Entree>? = null

    fun lire(context: Context): List<Entree> = cache ?: charger(context).also { cache = it }

    private fun charger(context: Context): List<Entree> = try {
        val texte = context.assets.open("journal.json").bufferedReader().use { it.readText() }
        val a = JSONArray(texte)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val secs = o.optJSONArray("sections") ?: JSONArray()
            val tester = o.optJSONArray("tester") ?: JSONArray()
            Entree(
                pr = o.optInt("pr"),
                // Tête sans version = celle qu'on vient d'installer.
                version = if (o.isNull("version")) (if (i == 0) BuildConfig.VERSION_NAME else null) else o.optString("version"),
                date = o.optString("date"),
                titre = o.optString("titre"),
                sert = o.optString("sert"),
                sections = (0 until secs.length()).map { j ->
                    val s = secs.getJSONObject(j)
                    val pts = s.optJSONArray("points") ?: JSONArray()
                    Section(s.optString("titre"), (0 until pts.length()).map { pts.getString(it) })
                },
                tester = (0 until tester.length()).map { tester.getString(it) },
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Vrai une seule fois par version installée : la fenêtre « Nouveautés » s'ouvre alors toute seule. */
    fun nouveauteAMontrer(context: Context): Boolean {
        val p = context.getSharedPreferences("verso_ui", Context.MODE_PRIVATE)
        return p.getString("nouveautes_vues", null) != BuildConfig.VERSION_NAME
    }

    fun marquerVue(context: Context) {
        context.getSharedPreferences("verso_ui", Context.MODE_PRIVATE).edit()
            .putString("nouveautes_vues", BuildConfig.VERSION_NAME).apply()
    }
}

private val FormatJour = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRENCH)

private fun jour(d: String): String = runCatching { java.time.LocalDate.parse(d).format(FormatJour) }.getOrDefault(d)

/**
 * Feuille de verre plein écran qui monte du bas (même geste que la bibliothèque) :
 * poignée à tirer vers le bas, ✕ pour fermer.
 */
@Composable
internal fun FeuilleVerre(
    titre: String,
    sousTitre: String,
    onFermer: () -> Unit,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val portee = rememberCoroutineScope()
    val glisse = remember { Animatable(0f) }
    val seuil = with(LocalDensity.current) { 140.dp.toPx() }
    val forme = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
    val fondFeuille = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f))) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .graphicsLayer { translationY = glisse.value }
                .verre(forme, teinte = Color(0xFF0A0A0A).copy(alpha = 0.55f), flou = 26.dp, refraction = 30.dp, exporte = fondFeuille)
                .contourActif(false, forme),
        ) {
            CompositionLocalProvider(LocalFond provides fondFeuille) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = { if (glisse.value > seuil) onFermer() else portee.launch { glisse.animateTo(0f) } },
                                onDragCancel = { portee.launch { glisse.animateTo(0f) } },
                            ) { change, dy ->
                                change.consume()
                                portee.launch { glisse.snapTo((glisse.value + dy).coerceAtLeast(0f)) }
                            }
                        }
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        Modifier.padding(top = 10.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)).align(Alignment.CenterHorizontally),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(titre, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(sousTitre, color = Discret, fontSize = 12.sp)
                        }
                        RondVerre("✕", onFermer, taille = 16.sp)
                    }
                }
                contenu()
            }
        }
    }
}

/** Le journal complet : une carte par version, la dernière ouverte. */
@Composable
internal fun FeuilleJournal(onFermer: () -> Unit) {
    val context = LocalContext.current
    val entrees = remember { Journal.lire(context) }
    FeuilleVerre("Journal", "Chaque mise à jour, à quoi elle sert, et tous les détails", onFermer) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(entrees, key = { _, e -> e.pr }) { i, e -> CarteVersion(e, ouverteAuDepart = i == 0, actuelle = i == 0) }
        }
    }
}

/** Fenêtre « Nouveautés » après une mise à jour : l'entrée de la version installée, en entier. */
@Composable
internal fun FeuilleNouveautes(onFermer: () -> Unit, onJournal: () -> Unit) {
    val context = LocalContext.current
    val e = remember { Journal.lire(context).firstOrNull() }
    FeuilleVerre("Nouveautés", "Verso v${BuildConfig.VERSION_NAME}", onFermer) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 130.dp)) {
            if (e == null) {
                Text("Journal introuvable.", color = Discret, fontSize = 14.sp)
            } else {
                CarteVersion(e, ouverteAuDepart = true, actuelle = true, repliable = false)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonVerre("Compris", onFermer, Modifier.weight(1f), principal = true, taille = 15.sp)
                BoutonVerre("Tout le journal", onJournal, Modifier.weight(1f), taille = 15.sp)
            }
        }
    }
}

/** Une version : la phrase « sert à » toujours visible, les détails dépliables. */
@Composable
private fun CarteVersion(e: Journal.Entree, ouverteAuDepart: Boolean, actuelle: Boolean, repliable: Boolean = true) {
    var ouverte by rememberSaveable(e.pr) { mutableStateOf(ouverteAuDepart) }
    PanneauVerre(
        Modifier.fillMaxWidth(),
        forme = RoundedCornerShape(24.dp),
        actif = actuelle,
        onClick = if (repliable) ({ ouverte = !ouverte }) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (actuelle) Rouge.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(e.version?.let { "v$it" } ?: "PR #${e.pr}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
                Text(jour(e.date), color = Discret, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (repliable) Text(if (ouverte) "▾" else "▸", color = Discret, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(e.titre, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(e.sert, color = Color(0xFFDDDDDD), fontSize = 13.sp, lineHeight = 18.sp)
            AnimatedVisibility(ouverte, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    e.sections.forEach { s ->
                        Spacer(Modifier.height(14.dp))
                        Text(s.titre.uppercase(), color = Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        s.points.forEach { p -> Puce(p) }
                    }
                    if (e.tester.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("À TESTER", color = Rouge, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        e.tester.forEach { p -> Puce(p, "□") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Puce(texte: String, symbole: String = "•") {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(symbole, color = Discret, fontSize = 13.sp, modifier = Modifier.width(16.dp))
        Text(texte, color = Color(0xFFCCCCCC), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** Petit lien texte réutilisable. */
@Composable
internal fun Lien(texte: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(texte, color = Discret, fontSize = 13.sp, modifier = modifier.appui(onClick).padding(vertical = 6.dp))
}
