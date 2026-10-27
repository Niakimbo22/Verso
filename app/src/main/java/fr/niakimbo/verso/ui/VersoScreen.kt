package fr.niakimbo.verso.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.BuildConfig
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.generateur.Compteur
import fr.niakimbo.verso.matrix.MatrixController
import fr.niakimbo.verso.matrix.MatrixHistory
import fr.niakimbo.verso.modele.FavorisStore
import fr.niakimbo.verso.modele.ModelePlayer
import fr.niakimbo.verso.modele.ModeleStore
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.ReglagesStore
import fr.niakimbo.verso.toy.JournalToy
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.EcranVerre
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.update.UpdateState
import kotlinx.coroutines.launch

private val NothingRed = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)

@Composable
fun VersoScreen(
    state: UpdateState,
    onUpdate: (UpdateState.Available) -> Unit,
    onRetry: (UpdateState.Error) -> Unit,
    matrix: MatrixController,
    matrixHistory: MatrixHistory,
    player: ModelePlayer,
    favoris: FavorisStore,
    catalogue: Catalogue,
    reglages: ReglagesStore,
    store: ModeleStore,
    journalToy: JournalToy,
    etatToy: EtatToy,
    onChoisirToy: () -> Unit,
    sectionWidgets: @Composable () -> Unit = {},
    sectionAuto: @Composable () -> Unit = {},
    /** Mini-lecteur flottant (modèle affiché), [onRemonter] = revenir à la grande matrice. */
    miniLecteur: @Composable (modifier: Modifier, onRemonter: () -> Unit) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val app = context.applicationContext as VersoApp
    // Rechargée quand un modèle est créé, modifié ou supprimé (rayon « Mes modèles »).
    val revisionModeles by catalogue.revision.collectAsStateWithLifecycle()
    val bibliotheque by produceState<Catalogue.Contenu?>(null, catalogue, revisionModeles) {
        value = catalogue.contenu()
    }
    val revisionReglages by reglages.revision.collectAsStateWithLifecycle()
    val entrees = remember(bibliotheque) { bibliotheque?.entrees?.filter { !it.cache } }
    val cache = remember(bibliotheque, revisionReglages) { CacheModeles(catalogue) }

    var bibliothequeOuverte by rememberSaveable { mutableStateOf(false) }
    var menuOuvert by rememberSaveable { mutableStateOf(false) }
    var journalOuvert by rememberSaveable { mutableStateOf(false) }
    var nouveautesOuvertes by rememberSaveable { mutableStateOf(false) }
    var detailsStatut by rememberSaveable { mutableStateOf(false) }
    // Position (dans la page) des sections qu'on rejoint depuis le menu.
    var yStatut by remember { mutableIntStateOf(0) }
    var yReglages by remember { mutableIntStateOf(0) }
    var fiche by remember { mutableStateOf<EntreeCatalogue?>(null) }
    var widgetsOuverts by rememberSaveable { mutableStateOf(false) }
    var plusOuvert by rememberSaveable { mutableStateOf(false) }
    var diagnosticOuvert by rememberSaveable { mutableStateOf(false) }
    val filtre = remember { FiltreBibliotheque() }
    val banc = remember { BancFluidite() }
    val defilement = rememberScrollState()
    val portee = rememberCoroutineScope()
    val vibreur = LocalHapticFeedback.current

    fun ouvrir(rayon: String) {
        filtre.rayon = rayon
        filtre.recherche = ""
        filtre.chercher = false
        bibliothequeOuverte = true
    }
    BackHandler(enabled = bibliothequeOuverte || journalOuvert || nouveautesOuvertes) {
        when {
            nouveautesOuvertes -> nouveautesOuvertes = false
            journalOuvert -> journalOuvert = false
            else -> bibliothequeOuverte = false
        }
    }
    // Déclaré après : passe en premier quand le menu est ouvert.
    BackHandler(enabled = menuOuvert) { menuOuvert = false }

    // Après une mise à jour : la fenêtre « Nouveautés » s'ouvre une fois.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Journal.nouveauteAMontrer(context)) {
            kotlinx.coroutines.delay(700)
            Journal.marquerVue(context)
            if (!banc.enCours) nouveautesOuvertes = true
        }
    }

    fun fermerFeuilles() {
        bibliothequeOuverte = false
        journalOuvert = false
        nouveautesOuvertes = false
    }

    fun aller(d: Destination) {
        menuOuvert = false
        when (d) {
            Destination.ACCUEIL -> { fermerFeuilles(); portee.launch { defilement.animateScrollTo(0) } }
            Destination.MODELES -> { fermerFeuilles(); ouvrir(TOUS) }
            Destination.FAVORIS -> { fermerFeuilles(); ouvrir(FAVORIS) }
            Destination.RECENTS -> { fermerFeuilles(); ouvrir(RECENTS) }
            Destination.DESSINS -> { fermerFeuilles(); ouvrir(MES) }
            Destination.CREER -> fr.niakimbo.verso.createur.CreateurActivity.ouvrir(context)
            Destination.AUTO -> fr.niakimbo.verso.auto.AutomatiqueActivity.ouvrir(context)
            Destination.WIDGET_AJOUT -> fr.niakimbo.verso.widget.perso.GalerieActivity.ouvrir(context)
            Destination.WIDGETS -> {
                fermerFeuilles(); widgetsOuverts = true
                portee.launch { defilement.animateScrollTo(yReglages) }
            }
            Destination.GLYPH -> {
                fermerFeuilles(); detailsStatut = true
                portee.launch { defilement.animateScrollTo(yStatut) }
            }
            Destination.TOY -> onChoisirToy()
            Destination.NOUVEAUTES -> { fermerFeuilles(); nouveautesOuvertes = true }
            Destination.JOURNAL -> { fermerFeuilles(); journalOuvert = true }
            Destination.PLUS, Destination.DIAGNOSTIC -> {
                fermerFeuilles(); plusOuvert = true; diagnosticOuvert = d == Destination.DIAGNOSTIC || diagnosticOuvert
                portee.launch { defilement.animateScrollTo(yReglages) }
            }
            Destination.FIGEE -> app.redemarrer("menu Matrice figée", rouvrir = true)
        }
    }
    DisposableEffect(banc) {
        banc.defilerPage = { bas, ms -> defilement.animateScrollTo(if (bas) defilement.maxValue else 0, tween(ms)) }
        banc.ouvrirBibliotheque = { o -> if (o) ouvrir(TOUS) else bibliothequeOuverte = false }
        onDispose {
            banc.defilerPage = null
            banc.ouvrirBibliotheque = null
        }
    }

    // Le mini-lecteur n'apparaît que quand la grande matrice n'est plus à l'écran (ou bibliothèque ouverte).
    var basHeros by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val herosCache by remember { derivedStateOf { defilement.value > basHeros } }
    val miniVisible = (ModePerf.ancien || bibliothequeOuverte || herosCache) && !journalOuvert && !nouveautesOuvertes

    EcranVerre(
        defilement = defilement,
        dessus = {
            AnimatedVisibility(
                visible = miniVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                miniLecteur(
                    Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    bibliothequeOuverte = false
                    portee.launch { defilement.animateScrollTo(0) }
                }
            }
            MenuLateral(menuOuvert, onFermer = { menuOuvert = false }, onAller = ::aller)
            if (banc.enCours) {
                PanneauVerre(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth(),
                    forme = RoundedCornerShape(24.dp),
                    actif = true,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("📊 Mesure en cours · ne touche pas l'écran · 🔆 l'écran reste allumé", color = Discret, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        ProgressionBanc(banc)
                    }
                }
            }
        },
        feuille = {
            AnimatedVisibility(
                visible = bibliothequeOuverte,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260)),
            ) {
                FeuilleBibliotheque(
                    entrees = entrees,
                    cache = cache,
                    filtre = filtre,
                    banc = banc,
                    onFermer = { bibliothequeOuverte = false },
                    onFiche = { fiche = it },
                )
            }
            val entree = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn()
            val sortie = slideOutVertically(tween(260)) { it } + fadeOut(tween(260))
            AnimatedVisibility(visible = journalOuvert, enter = entree, exit = sortie) {
                FeuilleJournal(onFermer = { journalOuvert = false })
            }
            AnimatedVisibility(visible = nouveautesOuvertes, enter = entree, exit = sortie) {
                FeuilleNouveautes(
                    onFermer = { nouveautesOuvertes = false },
                    onJournal = { nouveautesOuvertes = false; journalOuvert = true },
                )
            }
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalFige provides ((bibliothequeOuverte || journalOuvert || nouveautesOuvertes) && !ModePerf.ancien)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(defilement)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ModePerf.ancien) {
                // Banc : l'ancien écran relisait l'image de la matrice tout en haut → tout recomposé à chaque image.
                val f by matrix.lastFrame.collectAsStateWithLifecycle()
                f.hashCode()
            }
            EnTete(state, onUpdate, onRetry, onMenu = { menuOuvert = true })
            Spacer(Modifier.height(14.dp))
            Heros(
                app, entrees, onFiche = { fiche = it },
                modifier = Modifier.onGloballyPositioned { basHeros = (it.positionInParent().y + it.size.height * 0.6f).toInt() },
            )
            Spacer(Modifier.height(18.dp))
            CarteErreurMatrice(app)
            LigneStatut(
                etatToy, onChoisirToy, detailsStatut, { detailsStatut = it },
                Modifier.onGloballyPositioned { yStatut = (it.positionInParent().y - 120f).toInt().coerceAtLeast(0) },
            )

            Spacer(Modifier.height(30.dp))
            Titre("ACCÈS RAPIDE", Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            RangeeRapide(app, entrees, cache, onTout = { ouvrir(it) }, onFiche = { fiche = it })
            Spacer(Modifier.height(14.dp))
            CarteTousModeles(entrees) { ouvrir(TOUS) }

            Spacer(Modifier.height(30.dp))
            sectionAuto()

            Spacer(Modifier.height(30.dp))
            Titre("RÉGLAGES", Modifier.fillMaxWidth().onGloballyPositioned { yReglages = (it.positionInParent().y - 120f).toInt().coerceAtLeast(0) })
            Spacer(Modifier.height(12.dp))
            val revisionWidgets by app.widgetsPerso.revision.collectAsStateWithLifecycle()
            val nbWidgets = remember(revisionWidgets) { app.widgetsPerso.poses(context).size }
            Repliable(
                symbole = "▣",
                titre = "Widgets",
                sousTitre = if (nbWidgets == 0) "＋ Ajouter un widget (galerie, 8 tailles)" else "$nbWidgets sur ton écran d'accueil · ＋ Ajouter · modifier",
                ouvert = widgetsOuverts,
                onBasculer = { widgetsOuverts = !widgetsOuverts },
            ) { sectionWidgets() }

            Spacer(Modifier.height(10.dp))
            val erreurs = bibliotheque?.erreurs.orEmpty()
            Repliable(
                symbole = "⋯",
                titre = "Plus",
                sousTitre = "Fond animé · Matrice figée ? · Diagnostic et mesure de fluidité" +
                    if (erreurs.isNotEmpty()) " · ${erreurs.size} ⚠" else "",
                ouvert = plusOuvert,
                onBasculer = { plusOuvert = !plusOuvert },
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ReglageFondAnime()
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Redémarrer le processus = l'équivalent de « Forcer l'arrêt », le seul remède sûr.
                        app.redemarrer("bouton Matrice figée", rouvrir = true)
                    }) { Text("↻ Matrice figée ? Redémarrer Verso", color = Dim, fontSize = 12.sp) }
                    TextButton(onClick = { diagnosticOuvert = !diagnosticOuvert }) {
                        Text(
                            (if (diagnosticOuvert) "▾ " else "▸ ") + "DIAGNOSTIC" + if (erreurs.isNotEmpty()) " (${erreurs.size} ⚠)" else "",
                            color = Dim,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        )
                    }
                    if (diagnosticOuvert) {
                        Spacer(Modifier.height(8.dp))
                        SectionBanc(banc)
                        Spacer(Modifier.height(24.dp))
                        DiagnosticToy(matrix, journalToy, store)
                        Spacer(Modifier.height(24.dp))
                        MatrixTestSection(matrix, player, matrixHistory, erreurs, onCompteur = {
                            bibliotheque?.parId(Compteur.ID)?.let { player.jouer(catalogue.resoudre(it)) }
                        })
                    }
                }
            }
        }
        }
    }

    fiche?.let { entree ->
        FicheModele(
            entree = entree,
            catalogue = catalogue,
            reglages = reglages,
            player = player,
            onFermer = { fiche = null },
        )
    }
}

/** « Verso », la version, et la mise à jour quand il y en a une. */
@Composable
private fun EnTete(
    state: UpdateState,
    onUpdate: (UpdateState.Available) -> Unit,
    onRetry: (UpdateState.Error) -> Unit,
    onMenu: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Menu latéral : raccourci vers chaque endroit de l'app.
            fr.niakimbo.verso.ui.verre.RondVerre("☰", onMenu, taille = 18.sp)
            Spacer(Modifier.width(12.dp))
            Text("Verso", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "v${BuildConfig.VERSION_NAME}" + when (state) {
                    UpdateState.Checking -> " · recherche…"
                    UpdateState.UpToDate -> " · à jour"
                    else -> ""
                },
                color = Dim, fontSize = 13.sp,
            )
        }
        when (state) {
            UpdateState.Checking, UpdateState.UpToDate -> Unit
            is UpdateState.Available -> {
                Spacer(Modifier.height(10.dp))
                BoutonVerre("Mettre à jour · v${state.release.version}", { onUpdate(state) }, Modifier.fillMaxWidth(), principal = true)
                state.release.sert?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Dim, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (state.awaitingPermission) {
                    Spacer(Modifier.height(8.dp))
                    Note("Autorise l'installation d'apps depuis Verso, puis reviens ici.")
                }
            }
            is UpdateState.Downloading -> {
                Spacer(Modifier.height(10.dp))
                Note("Téléchargement…")
                Spacer(Modifier.height(8.dp))
                if (state.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NothingRed,
                        trackColor = Color(0xFF333333),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = NothingRed,
                        trackColor = Color(0xFF333333),
                    )
                }
            }
            UpdateState.Installing -> {
                Spacer(Modifier.height(10.dp))
                Note("Installation…")
            }
            is UpdateState.Error -> {
                Spacer(Modifier.height(10.dp))
                Text(state.message, color = NothingRed, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onRetry(state) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Réessayer", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, color = Dim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
