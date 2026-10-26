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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import fr.niakimbo.verso.auto.ListeReordonnable
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.EcranVerre
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import fr.niakimbo.verso.widget.WidgetMaj
import kotlinx.coroutines.launch

// ============================================================================================
// Activités
// ============================================================================================

/**
 * Créer ou modifier un widget perso. Sans [EXTRA_DESIGN] : on commence par choisir un point de départ.
 * Avec [AppWidgetManager.EXTRA_APPWIDGET_ID] (depuis la pose d'un widget) : à l'enregistrement,
 * le widget posé prend ce design.
 */
class EditeurWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VersoApp
        val pose = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val design = intent.getStringExtra(EXTRA_DESIGN)?.let(app.widgetsPerso::parId)
        setContent {
            EcranEditeur(app, design, onFini = { enregistre ->
                if (enregistre != null && pose != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    app.widgetsPerso.attribuer(pose, enregistre.id)
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pose))
                }
                lifecycleScope.launch {
                    WidgetMaj.majTous(app, forcer = true)
                    finish()
                }
            })
        }
    }

    companion object {
        const val EXTRA_DESIGN = "design"

        fun ouvrir(context: Context, designId: String? = null, appWidgetId: Int? = null) = context.startActivity(
            Intent(context, EditeurWidgetActivity::class.java)
                .putExtra(EXTRA_DESIGN, designId)
                .apply { appWidgetId?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** À la pose d'un « Verso · Perso » (ou « Paramètres du widget ») : quel widget afficher ? */
class ConfigWidgetPersoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pose = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultat = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pose)
        setResult(RESULT_CANCELED, resultat)
        if (pose == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val app = application as VersoApp
        setContent {
            EcranChoixDesign(
                app,
                actuel = app.widgetsPerso.idPour(pose),
                onChoix = { design ->
                    app.widgetsPerso.attribuer(pose, design.id)
                    lifecycleScope.launch {
                        WidgetMaj.majTous(app, forcer = true)
                        setResult(RESULT_OK, resultat)
                        finish()
                    }
                },
                onNouveau = {
                    // L'éditeur attribue le widget à l'enregistrement.
                    setResult(RESULT_OK, resultat)
                    EditeurWidgetActivity.ouvrir(this, null, pose)
                    finish()
                },
                onRetour = ::finish,
            )
        }
    }
}

/** « Poser sur l'écran d'accueil » : le lanceur rappelle ici avec l'id du widget posé. */
class PoseRecepteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val pose = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val design = intent.getStringExtra(EditeurWidgetActivity.EXTRA_DESIGN) ?: return
        if (pose == AppWidgetManager.INVALID_APPWIDGET_ID) return
        app.widgetsPerso.attribuer(pose, design)
        val fin = goAsync()
        app.appScope.launch {
            try { WidgetMaj.majTous(app, forcer = true) } finally { fin.finish() }
        }
    }

    companion object {
        /** Demande au lanceur de poser le widget ; faux si le lanceur ne sait pas faire. */
        fun poser(context: Context, designId: String): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val rappel = PendingIntent.getBroadcast(
                context, designId.hashCode(),
                Intent(context, PoseRecepteur::class.java).putExtra(EditeurWidgetActivity.EXTRA_DESIGN, designId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            return manager.requestPinAppWidget(ComponentName(context, WidgetPersoProvider::class.java), null, rappel)
        }
    }
}

// ============================================================================================
// Aperçu : le vrai widget, sur un faux fond d'écran
// ============================================================================================

enum class TailleApercu(val label: String, val l: Dp, val h: Dp) {
    CARRE("2 × 2", 170.dp, 170.dp), LARGE("4 × 2", 330.dp, 170.dp), BARRE("4 × 1", 330.dp, 84.dp)
}

@Composable
fun ApercuWidget(app: VersoApp, w: WidgetPerso, taille: TailleApercu, modifier: Modifier = Modifier) {
    val modele by app.player.actif.collectAsStateWithLifecycle()
    val apercuModele by produceState<Modele?>(modele, modele) {
        value = modele ?: app.catalogue.contenu().entrees.firstOrNull { !it.cache }?.let(app.catalogue::resoudre)
    }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B3A5C), Color(0xFF7A4B6E), Color(0xFFC98F5A))))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { FrameLayout(it) },
            update = { cadre ->
                cadre.removeAllViews()
                // Un aperçu raté ne doit jamais faire planter l'éditeur : on le note et on affiche un message.
                try {
                    val etat = WidgetMaj.etatPerso(app, apercuModele)
                    val rv = RenduPerso.construire(cadre.context, w, etat, cliquable = false)
                    cadre.addView(rv.apply(cadre.context, cadre))
                } catch (e: Exception) {
                    app.journalToy.ajouter("Aperçu du widget « ${w.nom} » impossible : ${e.javaClass.simpleName} ${e.message}")
                    cadre.addView(android.widget.TextView(cadre.context).apply {
                        text = "Aperçu impossible (voir le journal)"
                        setTextColor(android.graphics.Color.WHITE)
                    })
                }
            },
            modifier = Modifier.size(taille.l, taille.h),
        )
    }
}

// ============================================================================================
// Éditeur
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
private fun Etiquette(t: String) =
    Text(t, color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)

@Composable
private fun Aide(t: String) = Text(t, color = Discret, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
private fun <T> Choix(titre: String, options: List<Pair<String, T>>, valeur: T, onChoix: (T) -> Unit) {
    Column {
        Text(titre, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (libelle, v) -> PuceVerre(libelle, v == valeur, Modifier.weight(1f)) { onChoix(v) } }
        }
    }
}

@Composable
private fun EcranEditeur(app: VersoApp, initial: WidgetPerso?, onFini: (WidgetPerso?) -> Unit) {
    val store = app.widgetsPerso
    val context = LocalContext.current
    var w by remember { mutableStateOf(initial) }
    var taille by remember { mutableStateOf(TailleApercu.CARRE) }
    var ouvert by remember { mutableIntStateOf(-1) }
    var ajouter by remember { mutableStateOf(false) }
    var confirmer by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    // Étape 0 : choisir un point de départ.
    val courant = w
    if (courant == null) {
        EcranDepart(app, onChoix = { w = it }, onRetour = { onFini(null) })
        return
    }
    BackHandler { onFini(null) }

    fun enregistrer(): WidgetPerso {
        val propre = courant.copy(nom = courant.nom.trim().ifBlank { "Widget" })
        store.enregistrer(propre)
        return propre
    }

    EcranVerre(
        defilement = scroll,
        dessus = {
            Barre(if (initial == null) "Nouveau widget" else "Modifier le widget") { onFini(null) }
            BoutonVerre(
                "✓  Enregistrer", {
                    val e = enregistrer()
                    Toast.makeText(context, "« ${e.nom} » enregistré", Toast.LENGTH_SHORT).show()
                    onFini(e)
                }, principal = true,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp).padding(top = 76.dp, bottom = 110.dp),
        ) {
            // Aperçu réel.
            ApercuWidget(app, courant, taille, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TailleApercu.entries.forEach { t -> PuceVerre(t.label, t == taille, Modifier.weight(1f)) { taille = t } }
            }
            Spacer(Modifier.height(6.dp))
            Aide("Aperçu en vrai. Sur l'écran d'accueil, appui long sur le widget pour le redimensionner.")

            // Nom.
            Spacer(Modifier.height(24.dp))
            Etiquette("NOM")
            Spacer(Modifier.height(8.dp))
            PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(50)) {
                BasicTextField(
                    value = courant.nom, onValueChange = { w = courant.copy(nom = it.take(30)) }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Rouge),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
                )
            }

            // Contenu.
            Spacer(Modifier.height(24.dp))
            Etiquette("CONTENU · ${courant.blocs.size}")
            Spacer(Modifier.height(4.dp))
            Aide("De haut en bas. Appui long puis glisser pour l'ordre, toucher un bloc pour le régler.")
            Spacer(Modifier.height(10.dp))
            // Clé stable par position pour la liste réordonnable.
            val cles = courant.blocs.mapIndexed { i, b -> "$i:${b.type}" }
            ListeReordonnable(cles.zip(courant.blocs), { it.first }, onOrdre = { ordre ->
                val parCle = cles.zip(courant.blocs).toMap()
                w = courant.copy(blocs = ordre.mapNotNull { parCle[it] })
                ouvert = -1
            }) { (cle, bloc), _ ->
                val i = cles.indexOf(cle)
                CarteBloc(
                    bloc, ouvert = i == ouvert,
                    onOuvrir = { ouvert = if (ouvert == i) -1 else i },
                    onSupprimer = {
                        w = courant.copy(blocs = courant.blocs.filterIndexed { j, _ -> j != i })
                        ouvert = -1
                    },
                    onChange = { nb -> w = courant.copy(blocs = courant.blocs.mapIndexed { j, x -> if (j == i) nb else x }) },
                )
            }
            Spacer(Modifier.height(10.dp))
            BoutonVerre(if (ajouter) "Fermer" else "＋  Ajouter un bloc", { ajouter = !ajouter }, Modifier.fillMaxWidth(), taille = 14.sp)
            if (ajouter) {
                Spacer(Modifier.height(10.dp))
                CatalogueBlocs(courant) { type ->
                    w = courant.copy(blocs = courant.blocs + WidgetPerso.Bloc(type, if (type == WidgetPerso.Type.HEURE) WidgetPerso.Taille.L else WidgetPerso.Taille.M))
                    ouvert = courant.blocs.size
                    ajouter = false
                }
            }

            // Style.
            Spacer(Modifier.height(28.dp))
            Etiquette("STYLE")
            Spacer(Modifier.height(10.dp))
            PanneauVerre(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Choix("Disposition", WidgetPerso.Disposition.entries.map { it.label to it }, courant.disposition) { w = courant.copy(disposition = it) }
                    Choix("Fond", WidgetPerso.Fond.entries.map { it.label to it }, courant.fond) { w = courant.copy(fond = it) }
                    Choix("Thème", WidgetPerso.Theme.entries.map { it.label to it }, courant.theme) { w = courant.copy(theme = it) }
                    Choix("Alignement", WidgetPerso.Alignement.entries.map { it.label to it }, courant.alignement) { w = courant.copy(alignement = it) }
                    if (courant.blocs.any { it.type == WidgetPerso.Type.MATRICE }) {
                        Choix("LEDs de la matrice", listOf((if (courant.theme == WidgetPerso.Theme.CLAIR) "Noires" else "Blanches") to false, "Rouges" to true), courant.ledsRouges) { w = courant.copy(ledsRouges = it) }
                        Choix("Points", listOf("Ronds" to false, "Carrés" to true), courant.pointsCarres) { w = courant.copy(pointsCarres = it) }
                    }
                    if (courant.fond == WidgetPerso.Fond.VERRE) Aide("Le lanceur ne permet pas le vrai flou : « Verre » est un fond translucide avec un liseré clair.")
                }
            }

            // Poser / supprimer.
            Spacer(Modifier.height(28.dp))
            BoutonVerre("⊕  Poser sur l'écran d'accueil", {
                val e = enregistrer()
                if (!PoseRecepteur.poser(context, e.id)) {
                    Toast.makeText(context, "Ton écran d'accueil ne le permet pas : appui long sur l'écran › Widgets › Verso · Perso.", Toast.LENGTH_LONG).show()
                }
            }, Modifier.fillMaxWidth(), taille = 14.sp)
            Spacer(Modifier.height(6.dp))
            Aide("Ou : appui long sur l'écran d'accueil › Widgets › Verso › « Verso · Perso », puis choisis ce widget.")
            if (initial != null) {
                Spacer(Modifier.height(16.dp))
                BoutonVerre("Supprimer ce widget", { confirmer = true }, Modifier.fillMaxWidth(), taille = 14.sp, couleurTexte = Rouge)
            }
        }
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            containerColor = Color(0xFF151515), titleContentColor = Color.White, textContentColor = Discret,
            title = { Text("Supprimer « ${courant.nom} » ?") },
            text = { Text("Les widgets posés qui l'affichent prendront un autre de tes widgets.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmer = false
                    store.supprimer(courant.id)
                    onFini(null)
                }) { Text("Supprimer", color = Rouge) }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler", color = Color.White) } },
        )
    }
}

/** Un bloc du widget : symbole, nom, réglages si ouvert. */
@Composable
private fun CarteBloc(b: WidgetPerso.Bloc, ouvert: Boolean, onOuvrir: () -> Unit, onSupprimer: () -> Unit, onChange: (WidgetPerso.Bloc) -> Unit) {
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(20.dp), actif = ouvert) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).appui(onOuvrir), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.type.symbole, color = if (ouvert) Rouge else Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.type.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (b.type) {
                                WidgetPerso.Type.ESPACE -> "Espace"
                                WidgetPerso.Type.TEXTE -> b.texte.ifBlank { "Touche pour écrire ton texte" }
                                WidgetPerso.Type.MATRICE -> b.taille.label
                                else -> "${b.taille.label} · ${b.couleur.label}"
                            },
                            color = Discret, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text("✕", color = Discret, fontSize = 16.sp, modifier = Modifier.appui(onSupprimer).padding(8.dp))
            }
            if (ouvert && b.type != WidgetPerso.Type.ESPACE) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Aide(b.type.aide)
                    if (b.type == WidgetPerso.Type.TEXTE) {
                        PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(50)) {
                            BasicTextField(
                                value = b.texte, onValueChange = { onChange(b.copy(texte = it.take(40))) }, singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Rouge),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                                decorationBox = { champ ->
                                    if (b.texte.isEmpty()) Text("Ton texte (40 caractères)", color = Discret, fontSize = 15.sp)
                                    champ()
                                },
                            )
                        }
                    }
                    if (b.type != WidgetPerso.Type.BOUTONS) {
                        Choix("Taille", WidgetPerso.Taille.entries.map { it.label to it }, b.taille) { onChange(b.copy(taille = it)) }
                    }
                    if (b.type != WidgetPerso.Type.MATRICE) {
                        Choix("Couleur", WidgetPerso.Couleur.entries.map { it.label to it }, b.couleur) { onChange(b.copy(couleur = it)) }
                    }
                }
            }
        }
    }
}

/** Les types de blocs, en tuiles. */
@Composable
private fun CatalogueBlocs(w: WidgetPerso, onChoix: (WidgetPerso.Type) -> Unit) {
    val dispo = WidgetPerso.Type.entries.filter { it != WidgetPerso.Type.MATRICE || w.blocs.none { b -> b.type == WidgetPerso.Type.MATRICE } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        dispo.chunked(3).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { t ->
                    PanneauVerre(Modifier.weight(1f), forme = RoundedCornerShape(20.dp), onClick = { onChoix(t) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t.symbole, color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(6.dp))
                            Text(t.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(3 - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Étape 0 : partir d'un modèle de widget (ou de zéro). */
@Composable
private fun EcranDepart(app: VersoApp, onChoix: (WidgetPerso) -> Unit, onRetour: () -> Unit) {
    BackHandler(onBack = onRetour)
    val scroll = rememberScrollState()
    val modeles = remember { WidgetPerso.modeles(app.widgetsPerso::nouvelId) }
    EcranVerre(defilement = scroll, dessus = { Barre("Nouveau widget", onRetour) }) {
        Column(
            Modifier.verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp).padding(top = 76.dp, bottom = 40.dp),
        ) {
            Etiquette("PARS D'UN MODÈLE")
            Spacer(Modifier.height(4.dp))
            Aide("Tu pourras tout changer ensuite : contenu, ordre, tailles, couleurs, fond.")
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                modeles.forEach { m ->
                    PanneauVerre(Modifier.fillMaxWidth(), onClick = { onChoix(m) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(m.nom, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            if (m.blocs.isEmpty()) {
                                Aide("Un widget vide : ajoute toi-même chaque bloc.")
                            } else {
                                ApercuWidget(
                                    app, m,
                                    if (m.disposition == WidgetPerso.Disposition.LIGNE) TailleApercu.BARRE else TailleApercu.CARRE,
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Choisir quel widget perso un widget posé affiche. */
@Composable
private fun EcranChoixDesign(app: VersoApp, actuel: String?, onChoix: (WidgetPerso) -> Unit, onNouveau: () -> Unit, onRetour: () -> Unit) {
    val tous by app.widgetsPerso.tous.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    EcranVerre(defilement = scroll, dessus = { Barre("Quel widget ?", onRetour) }) {
        Column(
            Modifier.verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp).padding(top = 76.dp, bottom = 40.dp),
        ) {
            BoutonVerre("＋  Créer un nouveau widget", onNouveau, Modifier.fillMaxWidth(), principal = true, taille = 14.sp)
            Spacer(Modifier.height(18.dp))
            if (tous.isEmpty()) Aide("Tu n'as pas encore de widget : crée le premier.")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                tous.forEach { d ->
                    PanneauVerre(Modifier.fillMaxWidth(), actif = d.id == actuel, onClick = { onChoix(d) }) {
                        Column(Modifier.padding(14.dp)) {
                            Text(d.nom, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            ApercuWidget(app, d, if (d.disposition == WidgetPerso.Disposition.LIGNE) TailleApercu.BARRE else TailleApercu.CARRE, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Section « Mes widgets » de l'écran principal
// ============================================================================================

/** Mes widgets : la liste (toucher = modifier) et « ＋ Créer un widget ». */
@Composable
fun SectionMesWidgets(app: VersoApp) {
    val context = LocalContext.current
    val tous by app.widgetsPerso.tous.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "MES WIDGETS", color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanneauVerre(Modifier.width(150.dp).height(120.dp), teinte = Rouge.copy(alpha = 0.5f), onClick = { EditeurWidgetActivity.ouvrir(context) }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("＋", color = Color.White, fontSize = 26.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Créer un widget", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Ce que tu veux dedans", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                }
            }
            tous.forEach { d ->
                PanneauVerre(Modifier.width(150.dp).height(120.dp), onClick = { EditeurWidgetActivity.ouvrir(context, d.id) }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(d.blocs.take(4).joinToString(" ") { it.type.symbole }, color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(d.nom, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Toucher pour modifier", color = Discret, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
