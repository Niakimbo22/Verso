package fr.niakimbo.verso.widget.perso

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.EcranVerre
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.widget.WidgetMaj
import java.util.UUID
import kotlinx.coroutines.launch

// ============================================================================================
// Activités
// ============================================================================================

/**
 * Galerie des widgets (façon iPhone) : on choisit un genre, on fait défiler ses tailles, on règle
 * le style, puis « Ajouter le widget ». Avec [EXTRA_POSE] : on modifie un widget déjà posé (taille imposée).
 */
open class GalerieActivity : ComponentActivity() {
    /** Widget posé à régler (pose depuis le lanceur, ou « Modifier ») ; INVALID = ajout d'un nouveau. */
    protected open fun pose(): Int = intent.getIntExtra(EXTRA_POSE, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VersoApp
        val pose = pose()
        val taille = if (pose != AppWidgetManager.INVALID_APPWIDGET_ID) {
            TailleW.de(AppWidgetManager.getInstance(this).getAppWidgetInfo(pose)?.provider?.className)
        } else null
        val actuel = if (pose != AppWidgetManager.INVALID_APPWIDGET_ID && taille != null && app.widgetsPerso.regle(pose)) app.widgetsPerso.pour(pose, taille) else null
        if (pose != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pose))
        }
        enableEdgeToEdge()
        setContent {
            EcranGalerie(app, taille, actuel, poseExistante = pose != AppWidgetManager.INVALID_APPWIDGET_ID, onFermer = ::finish) { w ->
                if (pose != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    app.widgetsPerso.enregistrer(pose, w)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pose))
                    lifecycleScope.launch {
                        WidgetMaj.majTous(app, forcer = true)
                        finish()
                    }
                } else if (PoseRecepteur.poser(this, w)) {
                    Toast.makeText(this, "Confirme l'ajout sur l'écran d'accueil", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Ton écran d'accueil ne le permet pas : appui long sur l'écran › Widgets › Verso · ${w.taille.nom} ${w.taille.label}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_POSE = "pose"

        fun ouvrir(context: Context, pose: Int? = null) = context.startActivity(
            Intent(context, GalerieActivity::class.java)
                .apply { pose?.let { putExtra(EXTRA_POSE, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Ouvert par le lanceur à la pose d'un widget Verso (ou « Paramètres du widget ») : la galerie, taille imposée. */
class ConfigWidgetPersoActivity : GalerieActivity() {
    override fun pose(): Int =
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        if (pose() == AppWidgetManager.INVALID_APPWIDGET_ID) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }
}

/** « Ajouter le widget » : le lanceur rappelle ici avec l'id du widget posé, qui prend le réglage choisi. */
class PoseRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val pose = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val jeton = intent.getStringExtra(EXTRA_JETON) ?: return
        val w = app.widgetsPerso.reprendreAttente(jeton) ?: return
        if (pose == AppWidgetManager.INVALID_APPWIDGET_ID) return
        app.widgetsPerso.enregistrer(pose, w)
        val fin = goAsync()
        app.appScope.launch {
            try { WidgetMaj.majTous(app, forcer = true) } finally { fin.finish() }
        }
    }

    companion object {
        private const val EXTRA_JETON = "jeton"

        /** Demande au lanceur de poser le widget à SA taille ; faux si le lanceur ne sait pas faire. */
        fun poser(context: Context, w: WidgetVerso): Boolean {
            val app = context.applicationContext as VersoApp
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val jeton = UUID.randomUUID().toString()
            app.widgetsPerso.mettreEnAttente(jeton, w)
            val rappel = PendingIntent.getBroadcast(
                context, jeton.hashCode(),
                Intent(context, PoseRecepteur::class.java).putExtra(EXTRA_JETON, jeton),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            return manager.requestPinAppWidget(ComponentName(context, w.taille.classe), null, rappel)
        }
    }
}

// ============================================================================================
// Aperçu : le vrai widget, à sa vraie proportion, sur un faux fond d'écran coloré
// ============================================================================================

/** Taille d'une case de l'écran d'accueil, estimée d'après la largeur de l'écran (4 colonnes). */
@Composable
private fun caseEcran(): Dp = ((LocalConfiguration.current.screenWidthDp - 56) / 4).dp

@Composable
fun ApercuWidget(app: VersoApp, w: WidgetVerso, modifier: Modifier = Modifier, echelle: Float = 1f) {
    val modele by app.player.actif.collectAsStateWithLifecycle()
    val apercuModele by produceState<Modele?>(modele, modele) {
        value = modele ?: app.catalogue.contenu().entrees.firstOrNull { !it.cache }?.let(app.catalogue::resoudre)
    }
    val c = caseEcran() * echelle
    val plan = remember(w) { w.plan() }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { cadre ->
            cadre.removeAllViews()
            // Un aperçu raté ne doit jamais faire planter l'écran : on le note et on affiche un message.
            try {
                val rv = RenduPerso.construire(cadre.context, plan, WidgetMaj.etatPerso(app, apercuModele), cliquable = false)
                cadre.addView(rv.apply(cadre.context, cadre))
            } catch (e: Exception) {
                app.journalToy.ajouter("Aperçu du widget impossible : ${e.javaClass.simpleName} ${e.message}")
                cadre.addView(android.widget.TextView(cadre.context).apply {
                    text = "Aperçu impossible"
                    setTextColor(android.graphics.Color.WHITE)
                })
            }
        },
        modifier = modifier.size(c * w.taille.l, c * w.taille.h),
    )
}

/** Faux fond d'écran coloré : le verre du widget se voit comme sur un vrai fond. */
@Composable
private fun FondEcran(modifier: Modifier = Modifier, contenu: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF243A6B), Color(0xFF7A3E73), Color(0xFFD17B4B), Color(0xFFF1C27D))))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
        content = contenu,
    )
}

// ============================================================================================
// Écrans
// ============================================================================================

@Composable
private fun BoxScope.Barre(titre: String, onRetour: () -> Unit) {
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RondVerre("←", onRetour)
        Spacer(Modifier.width(12.dp))
        PanneauVerre(Modifier.weight(1f), forme = RoundedCornerShape(50)) {
            Text(
                titre, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Etiquette(t: String) = Text(t, color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)

/**
 * [taille] imposée = widget déjà posé (on ne peut pas changer sa taille, seulement son contenu et son style).
 * [actuel] = son réglage actuel (on ouvre directement sa page).
 */
@Composable
private fun EcranGalerie(
    app: VersoApp,
    taille: TailleW?,
    actuel: WidgetVerso?,
    poseExistante: Boolean,
    onFermer: () -> Unit,
    onValider: (WidgetVerso) -> Unit,
) {
    var genre by remember { mutableStateOf(actuel?.genre) }
    val g = genre
    if (g != null) {
        BackHandler { if (actuel != null) onFermer() else genre = null }
        PageGenre(app, g, taille, actuel?.takeIf { it.genre == g }, poseExistante, onRetour = { if (actuel != null) onFermer() else genre = null }, onValider = onValider)
        return
    }

    val scroll = rememberScrollState()
    val genres = Genre.entries.filter { taille == null || taille in it.tailles }
    EcranVerre(defilement = scroll, dessus = { Barre(if (taille != null) "Widget ${taille.label}" else "Ajouter un widget", onFermer) }) {
        Column(
            Modifier.verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp).padding(top = 76.dp, bottom = 40.dp),
        ) {
            Text(
                if (taille != null) "Choisis ce que ce widget affiche." else "Choisis un widget, puis sa taille.",
                color = Discret, fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                genres.forEach { gg ->
                    val apercu = WidgetVerso(gg, taille ?: gg.tailles.firstOrNull { it == TailleW.T2x2 } ?: gg.tailles.first())
                    PanneauVerre(Modifier.fillMaxWidth(), onClick = { genre = gg }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(gg.titre, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text(gg.description, color = Discret, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                                Text("›", color = Discret, fontSize = 26.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            FondEcran(Modifier.fillMaxWidth()) { ApercuWidget(app, apercu, echelle = if (apercu.taille.l >= 4) 0.8f else 1f) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                gg.tailles.joinToString("  ·  ") { it.label },
                                color = Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Page d'un genre : les tailles défilent (comme sur l'iPhone), le style en dessous, puis « Ajouter ». */
@Composable
private fun PageGenre(
    app: VersoApp,
    genre: Genre,
    tailleImposee: TailleW?,
    actuel: WidgetVerso?,
    poseExistante: Boolean,
    onRetour: () -> Unit,
    onValider: (WidgetVerso) -> Unit,
) {
    val tailles = tailleImposee?.let { listOf(it) } ?: genre.tailles
    val pager = rememberPagerState(initialPage = tailles.indexOf(TailleW.T2x2).coerceAtLeast(0)) { tailles.size }
    var style by remember { mutableStateOf(actuel?.style ?: WidgetVerso.Style.VERRE) }
    var rouge by remember { mutableStateOf(actuel?.rouge ?: false) }
    var texte by remember { mutableStateOf(actuel?.texte ?: "") }
    val scroll = rememberScrollState()
    fun courant(page: Int) = WidgetVerso(genre, tailles[page], style, rouge, texte)

    EcranVerre(
        defilement = scroll,
        dessus = {
            Barre(genre.titre, onRetour)
            BoutonVerre(
                if (poseExistante) "✓  Enregistrer" else "＋  Ajouter le widget",
                { onValider(courant(pager.currentPage)) },
                principal = true,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()
                .padding(top = 76.dp, bottom = 110.dp),
        ) {
            Text(genre.description, color = Discret, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(16.dp))

            // Les tailles, à faire défiler.
            val hauteur = caseEcran() * 4 * 0.86f + 40.dp
            HorizontalPager(pager, Modifier.fillMaxWidth().height(hauteur)) { page ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FondEcran(Modifier.fillMaxWidth().height(caseEcran() * 4 * 0.86f)) {
                        ApercuWidget(app, courant(page), echelle = 0.86f)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val t = tailles[pager.currentPage]
            Text(
                "${t.nom}  ·  ${t.label}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (tailles.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tailles.indices.forEach { i ->
                        Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f)))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Glisse pour voir les autres tailles", color = Discret, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (tailleImposee != null) {
                Spacer(Modifier.height(4.dp))
                Text("Taille du widget posé (pour une autre taille, ajoute un nouveau widget)", color = Discret, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            // Style.
            Spacer(Modifier.height(24.dp))
            PanneauVerre(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Etiquette("STYLE")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WidgetVerso.Style.entries.forEach { s -> PuceVerre(s.label, s == style, Modifier.weight(1f)) { style = s } }
                        }
                    }
                    Column {
                        Etiquette("COULEUR")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PuceVerre(if (style == WidgetVerso.Style.CLAIR) "Noir" else "Blanc", !rouge, Modifier.weight(1f)) { rouge = false }
                            PuceVerre("Rouge Nothing", rouge, Modifier.weight(1f)) { rouge = true }
                        }
                    }
                    if (genre == Genre.MESSAGE) {
                        Column {
                            Etiquette("TON TEXTE")
                            Spacer(Modifier.height(8.dp))
                            PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(50)) {
                                BasicTextField(
                                    value = texte, onValueChange = { texte = it.take(40) }, singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Rouge),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
                                    decorationBox = { champ ->
                                        if (texte.isEmpty()) Text("Bonne journée", color = Discret, fontSize = 16.sp)
                                        champ()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Section « Widgets » de l'écran principal
// ============================================================================================

/** « ＋ Ajouter un widget » (galerie) et les widgets Verso posés (toucher = modifier). */
@Composable
fun SectionMesWidgets(app: VersoApp) {
    val context = LocalContext.current
    val revision by app.widgetsPerso.revision.collectAsStateWithLifecycle()
    val poses = remember(revision) { app.widgetsPerso.poses(context) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "WIDGETS", color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        BoutonVerre("＋  Ajouter un widget", { GalerieActivity.ouvrir(context) }, Modifier.fillMaxWidth(), principal = true)
        if (poses.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                poses.forEach { (id, t) ->
                    val w = app.widgetsPerso.pour(id, t)
                    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(20.dp), onClick = { GalerieActivity.ouvrir(context, id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${w.genre.titre} · ${t.nom} ${t.label}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Sur ton écran d'accueil · ${w.style.label}" + if (w.rouge) " · rouge" else "", color = Discret, fontSize = 12.sp)
                            }
                            Text("Modifier ›", color = Discret, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
