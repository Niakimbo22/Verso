package fr.niakimbo.verso.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.glyph.BoutonGlyph
import fr.niakimbo.verso.matrix.MatrixState
import fr.niakimbo.verso.rapide.ActionsRapides
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import fr.niakimbo.verso.ui.verre.contourActif
import fr.niakimbo.verso.ui.verre.rememberPressionVerre
import fr.niakimbo.verso.ui.verre.verre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Écran principal, réorganisé le 26/10 :
 * 1. ce qu'on fait tout le temps, visible tout de suite : la matrice, ‹ ⏻ ›, favori / réglages / options ;
 * 2. l'état (Toy, Glyph) en deux pastilles ; les grandes cartes seulement s'il y a un problème ou si on touche ;
 * 3. accès rapide : favoris / récents en une rangée ; « Tous les modèles » ouvre la bibliothèque complète ;
 * 4. Automatique ; Widgets et Plus (fond animé, matrice figée, diagnostic) repliés.
 */

@Composable
internal fun Titre(texte: String, modifier: Modifier = Modifier) {
    Text(texte, color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, modifier = modifier)
}

/** La grande matrice et les commandes de tous les jours. */
@Composable
internal fun Heros(app: VersoApp, entrees: List<EntreeCatalogue>?, onFiche: (EntreeCatalogue) -> Unit, modifier: Modifier = Modifier) {
    // L'image est lue au dessin : la matrice se redessine seule, l'écran ne se recompose pas à chaque image.
    val frame = app.matrix.lastFrame.collectAsStateWithLifecycle()
    val eteinte by remember { derivedStateOf { frame.value == null } }
    // Bibliothèque ouverte par-dessus : la grande matrice garde son image (le mini-lecteur reste en direct).
    val fige = LocalFige.current
    val imageFigee = remember(fige) { frame.value }
    val actif by app.player.actif.collectAsStateWithLifecycle()
    val glyph by BoutonGlyph.allume.collectAsStateWithLifecycle()
    val favorisIds by app.favoris.ids.collectAsStateWithLifecycle()
    val enCours by app.moteurRegles.enCours.collectAsStateWithLifecycle()
    val regles by app.moteurRegles.store.regles.collectAsStateWithLifecycle()
    val autoActif by app.moteurRegles.store.actif.collectAsStateWithLifecycle()
    val finVeille by ActionsRapides.finVeille.collectAsStateWithLifecycle()
    val regle = regles.firstOrNull { it.id == enCours }?.takeIf { autoActif }
    val portee = rememberCoroutineScope()
    var options by rememberSaveable { mutableStateOf(false) }

    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(finVeille) {
        while (finVeille > 0) {
            maintenant = System.currentTimeMillis()
            delay(20_000)
        }
    }
    val resteVeille = if (finVeille > maintenant) ((finVeille - maintenant + 59_999) / 60_000).toInt() else 0

    val dernier = remember(eteinte, actif) { app.store.dernierModele }
    val nomDernier = entrees?.firstOrNull { it.id == dernier }?.nom
    val allume = actif != null && glyph != false
    val entreeActive = actif?.let { a -> entrees?.firstOrNull { it.id == a.id } }
    val favori = actif?.id in favorisIds

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Disque de verre ; tap = réglages du modèle affiché.
        PanneauVerre(Modifier.size(236.dp), forme = CircleShape, onClick = { entreeActive?.let(onFiche) }) {
            DotMatrix({ if (fige) imageFigee else frame.value }, Modifier.size(236.dp).padding(23.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            actif?.nom ?: if (eteinte) "Matrice éteinte" else "Aperçu",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            when {
                resteVeille > 0 -> "Veille dans $resteVeille min"
                regle != null -> "Auto · « ${regle.nom} »"
                actif != null -> "touche la matrice pour ses réglages"
                else -> " "
            },
            color = if (regle != null || resteVeille > 0) Rouge else Discret, fontSize = 12.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RondVerre("‹", { portee.launch { ActionsRapides.changer(app, -1) } }, taille = 24.sp)
            BoutonVerre(
                when {
                    allume -> "🔌  Éteindre"
                    nomDernier != null -> "▶  Rallumer « $nomDernier »"
                    else -> "▶  Rallumer"
                },
                { portee.launch { ActionsRapides.basculer(app) } },
                Modifier.weight(1f), principal = true, taille = 15.sp,
            )
            RondVerre("›", { portee.launch { ActionsRapides.changer(app, 1) } }, taille = 24.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuceVerre(if (favori) "★  Favori" else "☆  Favori", favori, Modifier.weight(1f)) {
                actif?.let { app.favoris.basculer(it.id) }
            }
            PuceVerre("⚙  Réglages", false, Modifier.weight(1f)) { entreeActive?.let(onFiche) }
            PuceVerre("◐  Options", options, Modifier.weight(1f)) { options = !options }
        }
        // Luminosité, minuteur de veille, au hasard (les mêmes que l'appui long sur le mini-lecteur).
        AnimatedVisibility(options, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Options(app, actif != null, resteVeille, onFermer = { options = false })
            }
        }
    }
}

/** Deux pastilles d'état (Glyph Toy, bouton Glyph) ; toucher = détails. Problème = détails ouverts. */
@Composable
internal fun LigneStatut(
    etatToy: EtatToy,
    onChoisirToy: () -> Unit,
    details: Boolean,
    onDetails: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val disponible by BoutonGlyph.disponible.collectAsStateWithLifecycle()
    val glyph by BoutonGlyph.allume.collectAsStateWithLifecycle()
    val texteToy = when (etatToy) {
        EtatToy.Lie -> "Toy actif"
        EtatToy.Relance -> "Toy : relance…"
        is EtatToy.ChoisiNonLance -> "Toy pas lancé"
        is EtatToy.Delie -> "Toy arrêté"
        EtatToy.Jamais -> "Toy pas choisi"
    }
    val (couleurGlyph, texteGlyph) = when {
        !disponible -> Discret to "Bouton Glyph"
        glyph == true -> Vert to "Glyph allumé"
        glyph == false -> Rouge to "Glyph éteint"
        else -> Discret to "Glyph prêt"
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pastille(couleurToy(etatToy), texteToy, details, Modifier.weight(1f)) { onDetails(!details) }
            Pastille(couleurGlyph, texteGlyph, details, Modifier.weight(1f)) { onDetails(!details) }
        }
        AnimatedVisibility(details || etatToy.probleme, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                StatutToy(etatToy, onChoisirToy)
                if (details) {
                    Spacer(Modifier.height(10.dp))
                    fr.niakimbo.verso.glyph.CarteBoutonGlyph()
                }
            }
        }
    }
}

@Composable
private fun Pastille(couleur: Color, texte: String, ouvert: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PanneauVerre(modifier, forme = RoundedCornerShape(50), actif = false, onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(couleur, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(texte, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (ouvert) "▾" else "▸", color = Discret, fontSize = 12.sp)
        }
    }
}

/** La matrice ne répond plus : toujours visible, avec le bon remède. */
@Composable
internal fun CarteErreurMatrice(app: VersoApp) {
    val etatMatrice by app.matrix.state.collectAsStateWithLifecycle()
    val redemarrage by app.matrix.redemarrageConseille.collectAsStateWithLifecycle()
    val erreur = etatMatrice as? MatrixState.Error ?: return
    Column {
        PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(16.dp), actif = true) {
            Column(Modifier.padding(14.dp)) {
                Text("La matrice ne répond pas", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(erreur.message, color = Discret, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(8.dp))
                BoutonVerre(if (redemarrage) "Redémarrer Verso" else "Reconnecter", {
                    if (redemarrage) app.redemarrer("bouton Redémarrer Verso", rouvrir = true)
                    else app.relancerMatrice("bouton Reconnecter")
                }, principal = true, taille = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Accès rapide : ★ Favoris ou ◷ Récents en une rangée qui défile ; « Tout voir » ouvre la bibliothèque. */
@Composable
internal fun RangeeRapide(
    app: VersoApp,
    entrees: List<EntreeCatalogue>?,
    cache: CacheModeles,
    onTout: (String) -> Unit,
    onFiche: (EntreeCatalogue) -> Unit,
) {
    val favorisIds by app.favoris.ids.collectAsStateWithLifecycle()
    val recentsIds by app.recents.ids.collectAsStateWithLifecycle()
    val actif by app.player.actif.collectAsStateWithLifecycle()
    var onglet by rememberSaveable { mutableStateOf(FAVORIS) }
    val parId = remember(entrees) { entrees.orEmpty().associateBy { it.id } }
    val liste = (if (onglet == FAVORIS) favorisIds else recentsIds).distinct().mapNotNull { parId[it] }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PuceVerre("★  Favoris", onglet == FAVORIS) { onglet = FAVORIS }
            PuceVerre("◷  Récents", onglet == RECENTS) { onglet = RECENTS }
            Spacer(Modifier.weight(1f))
            Text(
                if (onglet == FAVORIS && liste.size > 1) "Ordre ›" else "Tout voir ›",
                color = Discret, fontSize = 13.sp,
                modifier = Modifier.appui({ onTout(onglet) }).padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (liste.isEmpty()) {
            Text(
                if (onglet == FAVORIS) "Aucun favori pour l'instant : touche ☆ sous la matrice ou dans « Tous les modèles »."
                else "Rien d'affiché récemment.",
                color = Discret, fontSize = 12.sp, lineHeight = 16.sp,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(liste, key = { it.id }) { e ->
                    TuileModele(app, e, cache, e.id == actif?.id, onFiche)
                }
            }
        }
    }
}

/** Petite tuile de verre (rangée rapide) : tap = afficher, appui long = réglages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TuileModele(app: VersoApp, entree: EntreeCatalogue, cache: CacheModeles, actif: Boolean, onFiche: (EntreeCatalogue) -> Unit) {
    val modele = cache.modele(entree)
    val vibreur = LocalHapticFeedback.current
    val pression = rememberPressionVerre()
    val forme = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(96.dp)
            .then(pression.gestes)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    modele?.let {
                        vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        app.player.jouer(it)
                    }
                },
                onLongClick = {
                    vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                    onFiche(entree)
                },
            )
            .verre(forme, if (actif) Rouge.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f), flou = 6.dp, refraction = 14.dp, ombre = false, pression = pression)
            .contourActif(actif, forme)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ApercuModele(modele, animer = !LocalFige.current && (ReglagesUi.apercusAnimes || actif), modifier = Modifier.size(76.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            entree.nom, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Carte qui ouvre la bibliothèque complète. */
@Composable
internal fun CarteTousModeles(entrees: List<EntreeCatalogue>?, onOuvrir: () -> Unit) {
    val n = entrees?.size
    val rayons = entrees?.map { it.categorie }?.distinct()?.size
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(24.dp), onClick = onOuvrir) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("▦", color = Color.White, fontSize = 24.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Tous les modèles", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (n == null) "Chargement…" else "$n modèles · $rayons rayons · 🔍 · créer un dessin",
                    color = Discret, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", color = Discret, fontSize = 26.sp)
        }
    }
}

/** Carte de verre qui se déplie (Widgets, Plus). */
@Composable
internal fun Repliable(
    symbole: String,
    titre: String,
    sousTitre: String,
    ouvert: Boolean,
    onBasculer: () -> Unit,
    contenu: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(24.dp), actif = false, onClick = onBasculer) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(symbole, color = Color.White, fontSize = 22.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(titre, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(sousTitre, color = Discret, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(if (ouvert) "▾" else "▸", color = Discret, fontSize = 16.sp)
            }
        }
        AnimatedVisibility(ouvert, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(14.dp))
                contenu()
            }
        }
    }
}

/** Interrupteur « Fond animé » (section Plus). */
@Composable
internal fun ReglageFondAnime() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Fond animé", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Les lueurs derrière le verre bougent toutes seules. Plus vivant, mais tout le verre de l'écran se recalcule 20 fois par seconde.",
                color = Discret, fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = ReglagesUi.fondAnime,
            onCheckedChange = { ReglagesUi.changerFondAnime(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
        )
    }
}

/** Banc de fluidité (Diagnostic) : mesure ancien / nouveau moteur sur ce téléphone, puis optimise. */
@Composable
internal fun SectionBanc(banc: BancFluidite) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    LaunchedEffect(Unit) { banc.relire() }
    fun lancer(rapide: Boolean) {
        val fenetre = context.activite()?.window ?: return
        if (!banc.enCours) portee.launch { banc.lancer(fenetre, rapide) }
    }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("📊 FLUIDITÉ")
        Spacer(Modifier.height(6.dp))
        Hint("Fait défiler l'app tout seul, avec l'ancien moteur puis le nouveau, puis règle ce qui rame. L'écran reste allumé. Ne touche à rien. Pire cas : affiche Pluie avant.")
        Spacer(Modifier.height(10.dp))
        if (banc.enCours) {
            ProgressionBanc(banc)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonVerre("📊  Complet · 35 s", { lancer(false) }, Modifier.weight(1f), principal = true, taille = 13.sp)
                BoutonVerre("⚡  Rapide · 15 s", { lancer(true) }, Modifier.weight(1f), taille = 13.sp)
            }
        }
        banc.dernier?.let { r ->
            Spacer(Modifier.height(14.dp))
            ResultatsBanc(banc, r)
        }
        Spacer(Modifier.height(14.dp))
        ReglagesPerf()
    }
}

/** Barre de progression + étape + Annuler (aussi en haut de l'écran pendant la mesure). */
@Composable
internal fun ProgressionBanc(banc: BancFluidite, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(banc.etape.ifBlank { "Préparation…" }, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${(banc.progression * 100).toInt()} %", color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(10.dp))
            Text("✕ Annuler", color = Rouge, fontSize = 13.sp, modifier = Modifier.appui({ banc.annuler() }).padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(banc.progression.coerceIn(0.02f, 1f)).height(6.dp).background(Rouge, RoundedCornerShape(50)))
        }
    }
}

/** Résultats en un coup d'œil : verdict, une carte par scène avec barres ancien / nouveau, réglages faits. */
@Composable
private fun ResultatsBanc(banc: BancFluidite, r: BancFluidite.Rapport) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var details by rememberSaveable { mutableStateOf(false) }
    val gains = r.lignes.mapNotNull { BancFluidite.gain(it.ancien.pourcentRetard, it.nouveau.pourcentRetard, plusEstMieux = false) }
    val moyen = if (gains.isEmpty()) null else gains.average().toInt()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(22.dp), actif = true) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    when {
                        moyen == null -> "📊 Mesure faite"
                        moyen >= 10 -> "🚀 Nouveau moteur : $moyen % d'images en retard en moins"
                        moyen > -10 -> "🤝 Pareil que l'ancien moteur"
                        else -> "⚠️ Nouveau moteur moins fluide (${-moyen} %)"
                    },
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp,
                )
                Text("${r.quand} · écran ${r.hz} Hz" + if (r.rapide) " · rapide" else "", color = Discret, fontSize = 11.sp)
            }
        }
        r.lignes.forEach { l -> CarteScene(l) }
        if (r.ouvAncien != null || r.ouvNouveau != null || Demarrage.ms != null) {
            PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (r.ouvAncien != null || r.ouvNouveau != null) {
                        Text("📚 Ouverture bibliothèque : ${r.ouvAncien ?: "?"} → ${r.ouvNouveau ?: "?"} ms", color = Color.White, fontSize = 13.sp)
                    }
                    Demarrage.ms?.let { Text("🚀 Ouverture de l'app : $it ms", color = Color.White, fontSize = 13.sp) }
                }
            }
        }
        // Optimisation automatique.
        PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (banc.ajustements.isEmpty()) "✅ Rien à changer : c'est fluide" else "🛠️ Réglé automatiquement", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                banc.ajustements.forEach { Text(it, color = Color(0xFFDDDDDD), fontSize = 13.sp, lineHeight = 18.sp) }
                if (banc.ajustements.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (banc.peutAnnuler) PuceVerre("↩️  Annuler", false, Modifier.weight(1f)) { banc.annulerAjustements() }
                        PuceVerre("🔁  Re-mesurer", false, Modifier.weight(1f)) {
                            val f = context.activite()?.window ?: return@PuceVerre
                            if (!banc.enCours) portee.launch { banc.lancer(f, rapide = true) }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuceVerre(if (details) "▾  Chiffres" else "▸  Chiffres", details, Modifier.weight(1f)) { details = !details }
            PuceVerre("📋  Copier", false, Modifier.weight(1f)) {
                val texte = ReglagesUi.derniereMesure ?: BancFluidite.texte(r)
                context.getSystemService(android.content.ClipboardManager::class.java)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("Banc Verso", texte))
                android.widget.Toast.makeText(context, "Rapport copié", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        if (details) PanelBox { MonoLine(ReglagesUi.derniereMesure ?: BancFluidite.texte(r)) }
    }
}

/** Une scène : % en retard et images/s, ancien (gris) contre nouveau (rouge). */
@Composable
private fun CarteScene(l: BancFluidite.Ligne) {
    val g = BancFluidite.gain(l.ancien.pourcentRetard, l.nouveau.pourcentRetard, plusEstMieux = false)
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${l.symbole}  ${l.scene}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                g?.let {
                    Text(
                        if (it >= 0) "−$it % retard" else "+${-it} % retard",
                        color = if (it >= 0) Vert else Ambre, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val maxRetard = maxOf(l.ancien.pourcentRetard, l.nouveau.pourcentRetard, 1.0)
            Barre("⏱️ en retard", l.ancien.pourcentRetard, l.nouveau.pourcentRetard, maxRetard, "%")
            Spacer(Modifier.height(6.dp))
            val maxIps = maxOf(l.ancien.parSeconde, l.nouveau.parSeconde, 1.0)
            Barre("🎞️ images/s", l.ancien.parSeconde, l.nouveau.parSeconde, maxIps, "")
        }
    }
}

@Composable
private fun Barre(titre: String, ancien: Double, nouveau: Double, max: Double, unite: String) {
    Column {
        Text(titre, color = Discret, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        listOf(Triple("ancien", ancien, Color.White.copy(alpha = 0.35f)), Triple("nouveau", nouveau, Rouge)).forEach { (nom, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(nom, color = Discret, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp))
                Box(Modifier.weight(1f).height(8.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))) {
                    Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).height(8.dp).background(c, RoundedCornerShape(50)))
                }
                Text(String.format(java.util.Locale.FRANCE, "%.1f%s", v, unite), color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp).padding(start = 6.dp))
            }
        }
    }
}

/** Réglages d'affichage (ceux que l'optimisation automatique peut changer), à la main. */
@Composable
internal fun ReglagesPerf() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🧊 Qualité du verre", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReglagesUi.QualiteVerre.entries.forEach { q ->
                PuceVerre(q.label, q == ReglagesUi.qualiteVerre, Modifier.weight(1f)) { ReglagesUi.changerQualiteVerre(q) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🖼️ Aperçus animés", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("bibliothèque et accès rapide", color = Discret, fontSize = 11.sp)
            }
            Switch(
                checked = ReglagesUi.apercusAnimes,
                onCheckedChange = { ReglagesUi.changerApercusAnimes(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
            )
        }
    }
}

internal fun Context.activite(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
