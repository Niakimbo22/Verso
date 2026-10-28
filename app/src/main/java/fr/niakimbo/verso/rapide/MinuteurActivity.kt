package fr.niakimbo.verso.rapide

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.generateur.MinuteurLive
import fr.niakimbo.verso.ui.DotMatrix
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.LocalFond
import fr.niakimbo.verso.ui.verre.PuceVerre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val Rouge = Color(0xFFD71921)
private val Gris = Color(0xFF8A8A8A)
private val Tnum = "tnum"

/**
 * Le minuteur, façon app Horloge de l'iPhone : une feuille par-dessus l'écran d'accueil (ou Verso),
 * avec la roulette heures / minutes / secondes, puis l'anneau du décompte. Onglet Chrono en secondaire.
 */
class MinuteurActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VersoApp
        Minuteur.lire(app)
        Chrono.lire(app)
        app.matrix.connect()
        setContent { Feuille(app, onFermer = ::finish, onDemander = ::demanderNotifs) }
    }

    /** Sans notifications, l'écran « Terminé » ne peut pas s'ouvrir tout seul : on demande une fois. */
    private fun demanderNotifs() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
        }
    }
}

@Composable
private fun Feuille(app: VersoApp, onFermer: () -> Unit, onDemander: () -> Unit) {
    var chrono by remember { mutableStateOf(Minuteur.modeChrono(app)) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onFermer),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible, enter = slideInVertically { it / 2 } + fadeIn()) {
            val forme = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
            val fond = rememberLayerBackdrop()
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(forme)
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), forme)
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                // Fond que le verre réfracte : noir avec une lueur rouge Nothing.
                Box(
                    Modifier.matchParentSize().layerBackdrop(fond).background(Color(0xFF070707)).background(
                        Brush.radialGradient(listOf(Rouge.copy(alpha = 0.22f), Color.Transparent), center = Offset(200f, 0f), radius = 900f),
                    ),
                )
                CompositionLocalProvider(LocalFond provides fond) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.width(38.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PuceVerre("⏳  Minuteur", !chrono) { chrono = false; Minuteur.setModeChrono(app, false) }
                            PuceVerre("⏱️  Chrono", chrono) { chrono = true; Minuteur.setModeChrono(app, true) }
                        }
                        Spacer(Modifier.height(18.dp))
                        AnimatedContent(chrono, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "onglet") { c ->
                            if (c) OngletChrono(app) else OngletMinuteur(app, onDemander)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Minuteur

@Composable
private fun OngletMinuteur(app: VersoApp, onDemander: () -> Unit) {
    val etat by Minuteur.etat.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()
    AnimatedContent(etat.inactif, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) }, label = "minuteur") { inactif ->
        if (inactif) ChoixDuree(app, onDemarrer = { ms ->
            onDemander()
            portee.launch { Minuteur.demarrer(app, ms) }
        }) else Decompte(app, etat)
    }
}

@Composable
private fun ChoixDuree(app: VersoApp, onDemarrer: (Long) -> Unit) {
    val derniere = remember { Minuteur.derniereDuree(app) / 1000 }
    var h by remember { mutableIntStateOf((derniere / 3600).toInt()) }
    var m by remember { mutableIntStateOf(((derniere / 60) % 60).toInt()) }
    var s by remember { mutableIntStateOf((derniere % 60).toInt()) }
    val total = (h * 3600L + m * 60L + s) * 1000L
    // Les roues se recréent pour sauter à une durée récente.
    var cle by remember { mutableIntStateOf(0) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(ITEM * VISIBLES), contentAlignment = Alignment.Center) {
            // Bande de sélection en verre, comme sur l'iPhone.
            Box(
                Modifier.fillMaxWidth().height(ITEM).clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.09f)),
            )
            androidx.compose.runtime.key(cle) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoueEtiquette(h, 24, "h") { h = it }
                    RoueEtiquette(m, 60, "min") { m = it }
                    RoueEtiquette(s, 60, "s") { s = it }
                }
            }
            // Fondu en haut et en bas : effet cylindre.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color(0xFF070707), 0.28f to Color.Transparent, 0.72f to Color.Transparent, 1f to Color(0xFF070707),
                    ),
                ),
            )
        }

        val recentes = remember { Minuteur.recentes(app) }
        if (recentes.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Récents", color = Gris, fontSize = 12.sp)
                recentes.forEach { r ->
                    PuceVerre(Minuteur.texte(r), r == total) {
                        val sec = r / 1000
                        h = (sec / 3600).toInt(); m = ((sec / 60) % 60).toInt(); s = (sec % 60).toInt()
                        cle++
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(84.dp))
            RondIos("Démarrer", Rouge, actif = total > 0) { onDemarrer(total) }
        }
        Spacer(Modifier.height(14.dp))
        ReglagesSonnerie(app)
    }
}

private val ITEM = 46.dp
private const val VISIBLES = 5

@Composable
private fun RoueEtiquette(valeur: Int, n: Int, etiquette: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Roue(valeur, n, onChange, Modifier.width(62.dp))
        Text(etiquette, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(38.dp))
    }
}

/**
 * Une roue de la roulette : liste presque infinie (elle boucle), qui se cale au centre,
 * avec un « tic » à chaque cran et un effet cylindre (rotation, taille, transparence).
 */
@Composable
private fun Roue(valeur: Int, n: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    val tours = 400
    val milieu = n * (tours / 2)
    val etat = rememberLazyListState(initialFirstVisibleItemIndex = milieu + valeur)
    val fling = rememberSnapFlingBehavior(etat, SnapPosition.Center)
    val vibreur = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { ITEM.toPx() }

    // Valeur au centre.
    val centre by remember {
        derivedStateOf {
            val info = etat.layoutInfo
            val milieuVue = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - milieuVue) }?.index ?: (milieu + valeur)
        }
    }
    LaunchedEffect(etat) {
        var avant = -1
        snapshotFlow { centre % n }.collect { v ->
            if (avant != -1 && v != avant) vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            avant = v
            onChange(v)
        }
    }

    LazyColumn(
        state = etat,
        flingBehavior = fling,
        modifier = modifier.height(ITEM * VISIBLES),
        contentPadding = PaddingValues(vertical = ITEM * (VISIBLES / 2)),
        horizontalAlignment = Alignment.End,
    ) {
        items(n * tours) { i ->
            Box(
                Modifier.height(ITEM).fillMaxWidth().graphicsLayer {
                    // Distance au centre en crans : lue au dessin, sans recomposer.
                    val info = etat.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { x -> x.index == i }
                    val d = if (item == null) 2f else {
                        val milieuVue = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        ((item.offset + item.size / 2f - milieuVue) / itemPx).coerceIn(-2.6f, 2.6f)
                    }
                    rotationX = -d * 22f
                    val e = 1f - abs(d) * 0.07f
                    scaleX = e; scaleY = e
                    alpha = (1f - abs(d) * 0.33f).coerceIn(0.12f, 1f)
                    cameraDistance = 12f * density
                },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    "${i % n}", color = Color.White, fontSize = 25.sp, textAlign = TextAlign.End,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Tnum),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

/** Le décompte : un anneau rouge qui se vide, le temps au centre, l'heure de fin, le sablier en vrai. */
@Composable
private fun Decompte(app: VersoApp, e: Minuteur.Etat) {
    val portee = rememberCoroutineScope()
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(e) {
        while (true) {
            maintenant = System.currentTimeMillis()
            delay(50)
        }
    }
    val reste = e.reste(maintenant)
    val fraction by animateFloatAsState(1f - e.ecoule(maintenant), tween(120), label = "anneau")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(264.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val trait = 9.dp.toPx()
                val c = Stroke(trait, cap = StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension / 2 - trait / 2, style = Stroke(trait))
                drawArc(
                    if (e.enPause) Gris else Rouge, -90f, -360f * fraction, false,
                    topLeft = Offset(trait / 2, trait / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - trait, size.height - trait), style = c,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotMatrix({ MinuteurLive.frame(e, maintenant) }, Modifier.size(64.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    temps(reste), color = Color.White, fontSize = if (reste >= 3_600_000) 46.sp else 58.sp,
                    fontWeight = FontWeight.Thin, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Tnum),
                )
                val fin = if (e.enMarche) LocalTime.now().plusSeconds(reste / 1000).format(DateTimeFormatter.ofPattern("HH:mm")) else null
                Text(if (fin != null) "🔔 $fin" else "⏸ En pause", color = Gris, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RondIos("Annuler", Color(0xFF3A3A3A), actif = true) { portee.launch { Minuteur.annuler(app) } }
            BoutonVerre("+1 min", { portee.launch { Minuteur.ajouter(app) } }, taille = 15.sp)
            RondIos(if (e.enMarche) "Pause" else "Reprendre", if (e.enMarche) Color(0xFFFF9F0A) else Rouge, actif = true) {
                portee.launch { Minuteur.basculer(app) }
            }
        }
        Spacer(Modifier.height(14.dp))
        ReglagesSonnerie(app)
    }
}

/** « 4:32 », « 1:04:32 ». */
private fun temps(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Gros bouton rond à double anneau, comme Démarrer / Annuler sur l'iPhone. */
@Composable
private fun RondIos(texte: String, couleur: Color, actif: Boolean, taille: Dp = 84.dp, onClick: () -> Unit) {
    val vibreur = LocalHapticFeedback.current
    Box(
        Modifier.size(taille).clip(CircleShape).background(couleur.copy(alpha = if (actif) 0.32f else 0.12f))
            .clickable(enabled = actif) { vibreur.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .padding(3.dp)
            .border(2.dp, Color(0xFF070707), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texte, color = when { !actif -> Gris; couleur == Color(0xFF3A3A3A) -> Color.White; couleur == Rouge -> Color(0xFFFF5A52); else -> couleur },
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 🔔 Sonnerie : repliée par défaut (son, volume au max, vibration, essai). */
@Composable
private fun ReglagesSonnerie(app: VersoApp) {
    var ouvert by remember { mutableStateOf(false) }
    var son by remember { mutableStateOf(Minuteur.son(app)) }
    var volMax by remember { mutableStateOf(Minuteur.volumeMax(app)) }
    var vibre by remember { mutableStateOf(Minuteur.vibre(app)) }
    val forme = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(forme).background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), forme),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { ouvert = !ouvert }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔔  Sonnerie", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(son.label, color = Gris, fontSize = 14.sp)
            Text(if (ouvert) "  ⌃" else "  ›", color = Gris, fontSize = 15.sp)
        }
        AnimatedVisibility(ouvert) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Minuteur.Son.entries.forEach { x ->
                        PuceVerre(
                            when (x) { Minuteur.Son.PULSE -> "Pulse"; Minuteur.Son.SIRENE -> "Sirène"; Minuteur.Son.TELEPHONE -> "Téléphone" },
                            son == x,
                        ) { son = x; Minuteur.setSon(app, x) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PuceVerre(if (volMax) "🔊 Volume max" else "🔉 Volume actuel", volMax) { volMax = !volMax; Minuteur.setVolumeMax(app, volMax) }
                    PuceVerre("📳 Vibrer", vibre) { vibre = !vibre; Minuteur.setVibre(app, vibre) }
                }
                val contexte = LocalContext.current
                BoutonVerre("▶  Écouter 4 s", { Sonnerie.tester(contexte) }, Modifier.fillMaxWidth(), taille = 14.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Chrono

@Composable
private fun OngletChrono(app: VersoApp) {
    val e by Chrono.etat.collectAsStateWithLifecycle()
    val portee = rememberCoroutineScope()
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(e) {
        while (e.enMarche) {
            maintenant = System.currentTimeMillis()
            delay(33)
        }
        maintenant = System.currentTimeMillis()
    }
    val ms = e.ms(maintenant)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(ITEM * VISIBLES).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val s = ms / 1000
            val texte = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
            else "%02d:%02d,%02d".format(s / 60, s % 60, (ms / 10) % 100)
            Text(
                texte, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Thin,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Tnum),
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RondIos("Zéro", Color(0xFF3A3A3A), actif = !e.aZero) { portee.launch { Chrono.remettre(app) } }
            RondIos(if (e.enMarche) "Pause" else "Démarrer", if (e.enMarche) Color(0xFFFF9F0A) else Rouge, actif = true) {
                portee.launch { Chrono.basculer(app) }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
