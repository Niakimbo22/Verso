package fr.niakimbo.verso.auto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.EcranVerre
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Une app du téléphone qu'on peut choisir dans une condition. */
data class AppInstallee(val pkg: String, val nom: String)

/** Apps du téléphone (celles du lanceur) et leurs icônes, gardées le temps du processus. */
object Apps {
    @Volatile
    private var liste: List<AppInstallee>? = null
    private val icones = HashMap<String, ImageBitmap?>()

    suspend fun installees(context: Context): List<AppInstallee> = liste ?: withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val lanceur = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(lanceur, 0)
            .map { AppInstallee(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { sansAccents(it.nom) }
            .also { liste = it }
    }

    suspend fun icone(context: Context, pkg: String): ImageBitmap? {
        synchronized(icones) { if (pkg in icones) return icones[pkg] }
        val img = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        synchronized(icones) { icones[pkg] = img }
        return img
    }

    /** Nom d'une app installée (ou null si elle n'est plus là). */
    fun nom(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        null
    }

    fun sansAccents(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}

/** Icône ronde d'une app (lettre en attendant). */
@Composable
internal fun IconeApp(pkg: String, nom: String, modifier: Modifier = Modifier.size(36.dp)) {
    val context = LocalContext.current
    val icone by produceState<ImageBitmap?>(null, pkg) { value = Apps.icone(context, pkg) }
    Box(modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        val i = icone
        if (i != null) Image(i, contentDescription = nom, modifier = Modifier.fillMaxWidth())
        else Text(nom.take(1).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Bouton qui montre les apps choisies (icônes + noms) et ouvre le choix. Vide = [tout].
 */
@Composable
internal fun BoutonApps(apps: List<AppChoisie>, tout: String, onOuvrir: () -> Unit) {
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(20.dp), onClick = onOuvrir) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (apps.isEmpty()) {
                Text("✱", color = Rouge, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            } else {
                apps.take(4).forEach {
                    IconeApp(it.pkg, it.nom, Modifier.size(30.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                apps.noms(tout), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("›", color = Discret, fontSize = 22.sp)
        }
    }
}

/**
 * Choix des apps : recherche, une ligne par app (icône, nom, ✓). [unique] : une seule app.
 * Rien de coché = « toutes » (si [tout] n'est pas null).
 */
@Composable
internal fun ChoixApps(
    titre: String,
    choisies: List<AppChoisie>,
    tout: String?,
    onRetour: () -> Unit,
    onValider: (List<AppChoisie>) -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<AppInstallee>?>(null) { value = Apps.installees(context) }
    var selection by remember { mutableStateOf(choisies) }
    var recherche by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    EcranVerre(
        defilement = scroll,
        dessus = {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RondVerre("←", onRetour)
                Spacer(Modifier.width(12.dp))
                PanneauVerre(Modifier.weight(1f), forme = RoundedCornerShape(50)) {
                    BasicTextField(
                        value = recherche, onValueChange = { recherche = it.take(30) }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Rouge),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                        decorationBox = { champ ->
                            if (recherche.isEmpty()) Text("🔍  $titre", color = Discret, fontSize = 16.sp, maxLines = 1)
                            champ()
                        },
                    )
                }
            }
            BoutonVerre(
                when {
                    selection.isNotEmpty() -> "✓  OK · ${selection.size}"
                    tout != null -> "✓  $tout"
                    else -> "Choisis une app"
                },
                { if (selection.isNotEmpty() || tout != null) onValider(selection) },
                principal = selection.isNotEmpty() || tout != null,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        },
    ) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 76.dp, bottom = 110.dp),
        ) {
            val liste = apps
            if (liste == null) {
                Text("Chargement…", color = Discret, fontSize = 13.sp)
                return@Column
            }
            // Les apps cochées d'abord : on voit tout de suite ce qui est choisi.
            val q = Apps.sansAccents(recherche.trim())
            val filtrees = liste.filter { q.isEmpty() || Apps.sansAccents(it.nom).contains(q) }
            val choisiesPkg = selection.map { it.pkg }.toSet()
            val (cochees, autres) = filtrees.partition { it.pkg in choisiesPkg }
            if (tout != null && q.isEmpty()) {
                LigneApp(null, "✱  $tout", selection.isEmpty()) { selection = emptyList() }
            }
            (cochees + autres).forEach { a ->
                LigneApp(a.pkg, a.nom, a.pkg in choisiesPkg) {
                    selection = if (a.pkg in choisiesPkg) selection.filter { it.pkg != a.pkg } else selection + AppChoisie(a.pkg, a.nom)
                }
            }
            if (filtrees.isEmpty()) Text("Aucune app « $recherche ».", color = Discret, fontSize = 13.sp)
        }
    }
}

/** Une ligne simple (pas de verre : il peut y avoir 100 apps). */
@Composable
private fun LigneApp(pkg: String?, nom: String, coche: Boolean, onClick: () -> Unit) {
    val forme = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .appui(onClick)
            .clip(forme)
            .background(if (coche) Rouge.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.35f))
            .border(if (coche) 1.5.dp else 0.5.dp, if (coche) Rouge else Color.White.copy(alpha = 0.10f), forme)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg != null) {
            IconeApp(pkg, nom)
            Spacer(Modifier.width(12.dp))
        }
        Text(nom, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(if (coche) "✓" else "", color = Rouge, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Carte « il manque une autorisation » : un titre court, un bouton qui ouvre le bon réglage,
 * et la porte de secours des apps installées hors Play Store (réglage grisé).
 * Elle se cache toute seule dès que l'autorisation est donnée.
 */
@Composable
internal fun CartePermission(
    emoji: String,
    titre: String,
    bouton: String,
    autorise: (Context) -> Boolean,
    ouvrir: (Context) -> Unit,
) {
    val context = LocalContext.current
    var ok by remember { mutableStateOf(autorise(context)) }
    LaunchedEffect(Unit) {
        while (!ok) {
            delay(1_000)
            ok = autorise(context)
        }
    }
    if (ok) return
    PanneauVerre(Modifier.fillMaxWidth(), actif = true, forme = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$emoji  $titre", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            BoutonVerre(bouton, { ouvrir(context) }, Modifier.fillMaxWidth(), principal = true, taille = 14.sp)
            Text(
                "🔒 Grisé ? Fiche de Verso › ⋮ › Autoriser les paramètres restreints",
                color = Discret, fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.appui({
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }),
            )
        }
    }
}
