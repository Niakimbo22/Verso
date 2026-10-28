package fr.niakimbo.verso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.createur.CreateurActivity
import fr.niakimbo.verso.createur.MesModelesStore
import fr.niakimbo.verso.modele.Modele
import kotlinx.coroutines.launch
import fr.niakimbo.verso.modele.ModelePlayer
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.reglage.Personnalisation
import fr.niakimbo.verso.reglage.Reglage
import fr.niakimbo.verso.reglage.ReglagesStore
import fr.niakimbo.verso.reglage.Valeurs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Red = Color(0xFFD71921)
private val Dim = Color(0xFF8A8A8A)
private val Panel = Color(0xFF111111)
private val Border = Color(0xFF2A2A2A)
private val Fond = Color(0xFF0A0A0A)

/**
 * Fiche d'un modèle : aperçu en direct, choix de la version (V1, V2…) et tous ses réglages.
 *
 * Chaque changement est enregistré tout de suite. Si le modèle est celui affiché sur la matrice,
 * il y est mis à jour en direct (et donc aussi dans le Glyph Toy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheModele(
    entree: EntreeCatalogue,
    catalogue: Catalogue,
    reglages: ReglagesStore,
    player: ModelePlayer,
    onFermer: () -> Unit,
) {
    val initial = remember(entree.id) { reglages.lire(entree.id) }
    var versionId by remember(entree.id) { mutableStateOf(entree.version(initial.version).id) }
    var valeurs by remember(entree.id) { mutableStateOf(initial.valeurs) }
    val version = entree.version(versionId)

    fun sauver() = reglages.ecrire(entree.id, ReglagesStore.Choix(versionId, valeurs))

    // Après chaque enregistrement, mise à jour de la matrice si ce modèle y est affiché.
    val revision by reglages.revision.collectAsStateWithLifecycle()
    val revisionOuverture = remember(entree.id) { reglages.revision.value }
    LaunchedEffect(revision) {
        if (revision == revisionOuverture) return@LaunchedEffect
        delay(150)
        player.rafraichir(entree.id)
    }

    val apercu by produceState<Modele?>(null, versionId, valeurs) {
        value = withContext(Dispatchers.Default) { catalogue.resoudre(entree, versionId, valeurs) }
    }

    ModalBottomSheet(
        onDismissRequest = onFermer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fond,
        contentColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(entree.nom, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            ApercuModele(
                apercu, animer = true,
                modifier = Modifier
                    .size(170.dp)
                    .background(Color.Black, CircleShape)
                    .border(1.dp, Border, CircleShape)
                    .padding(10.dp),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    sauver()
                    apercu?.let(player::jouer)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
            ) { Text("Afficher sur la matrice") }

            // ----- Versions -----
            if (entree.versions.size > 1) {
                Spacer(Modifier.height(24.dp))
                Titre("VERSION")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entree.versions.forEach { v ->
                        Option(
                            texte = v.nom + if (v === entree.parDefaut) " (récente)" else "",
                            choisi = v.id == versionId,
                            modifier = Modifier.weight(1f),
                        ) {
                            versionId = v.id
                            sauver()
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(version.description, color = Dim, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
            if (entree.id == fr.niakimbo.verso.rapide.Chrono.ID) {
                Spacer(Modifier.height(12.dp))
                val app = LocalContext.current.applicationContext as fr.niakimbo.verso.VersoApp
                val chrono by fr.niakimbo.verso.rapide.Chrono.etat.collectAsStateWithLifecycle()
                val portee = rememberCoroutineScope()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    fr.niakimbo.verso.ui.verre.BoutonVerre(
                        if (chrono.enMarche) "⏸  Pause" else "▶  Démarrer",
                        { portee.launch { fr.niakimbo.verso.rapide.Chrono.basculer(app) } },
                        Modifier.weight(1f), principal = !chrono.enMarche, taille = 15.sp,
                    )
                    if (!chrono.aZero) fr.niakimbo.verso.ui.verre.BoutonVerre(
                        "↺  Zéro", { portee.launch { fr.niakimbo.verso.rapide.Chrono.remettre(app) } }, Modifier.weight(1f), taille = 15.sp,
                    )
                }
            }
            if (entree.id == fr.niakimbo.verso.generateur.MusiqueLive.ID || entree.id == fr.niakimbo.verso.generateur.EgaliseurLive.ID) {
                Spacer(Modifier.height(12.dp))
                fr.niakimbo.verso.auto.PermissionSpectre()
            }

            // ----- Réglages de la version -----
            if (version.reglages.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Titre(if (entree.versions.size > 1) "RÉGLAGES DE LA ${version.nom}" else "RÉGLAGES")
                version.reglages.forEach { r ->
                    Spacer(Modifier.height(10.dp))
                    Controle(r, valeurs, onChange = { valeurs = it }, onFini = ::sauver)
                }
            }

            // ----- Réglages communs -----
            Spacer(Modifier.height(24.dp))
            Titre("RÉGLAGES COMMUNS")
            Personnalisation.reglages(anime = apercu?.estAnime ?: true).forEach { r ->
                Spacer(Modifier.height(10.dp))
                Controle(r, valeurs, onChange = { valeurs = it }, onFini = ::sauver)
            }

            // ----- Créations : modifier, partager, supprimer ; modèles intégrés : en faire une copie -----
            Spacer(Modifier.height(24.dp))
            ActionsModele(entree, apercu, onFermer)

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = {
                val cles = (version.reglages + Personnalisation.reglages(true)).map { it.cle }
                valeurs = valeurs.sans(cles)
                sauver()
            }) { Text("Réinitialiser les réglages de cette version", color = Dim) }
            TextButton(onClick = {
                sauver()
                onFermer()
            }) { Text("Fermer", color = Color.White) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Titre(texte: String) {
    Text(
        texte,
        color = Dim,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Un réglage : titre + valeur actuelle, le contrôle, puis une phrase qui explique ce qu'il fait. */
@Composable
private fun Controle(r: Reglage, v: Valeurs, onChange: (Valeurs) -> Unit, onFini: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, shape)
            .border(1.dp, Border, shape)
            .padding(14.dp),
    ) {
        when (r) {
            is Reglage.Curseur -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.titre, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(r.format(v.f(r)), color = Red, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = v.f(r),
                    onValueChange = { onChange(v.avec(r.cle, it)) },
                    onValueChangeFinished = onFini,
                    valueRange = r.min..r.max,
                    steps = r.crans,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Red,
                        inactiveTrackColor = Border,
                        activeTickColor = Color.White.copy(alpha = 0.4f),
                        inactiveTickColor = Dim.copy(alpha = 0.4f),
                    ),
                )
            }
            is Reglage.Interrupteur -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.titre, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = v.b(r),
                        onCheckedChange = {
                            onChange(v.avec(r.cle, it))
                            onFini()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Red),
                    )
                }
            }
            is Reglage.Choix -> {
                Text(r.titre, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.options.forEach { (valeur, libelle) ->
                        Option(libelle, v.s(r) == valeur, Modifier.weight(1f)) {
                            onChange(v.avec(r.cle, valeur))
                            onFini()
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(r.description, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun Option(texte: String, choisi: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (choisi) Red else Color.Transparent,
            contentColor = Color.White,
        ),
    ) { Text(texte, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun ActionsModele(entree: EntreeCatalogue, apercu: Modele?, onFermer: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VersoApp
    val scope = rememberCoroutineScope()
    var confirmer by remember { mutableStateOf(false) }
    val perso = entree.perso
    val fichier = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = ecrireJson(context, app, entree.id, uri)
            Toast.makeText(context, if (ok) "Fichier enregistré" else "Impossible d'écrire le fichier", Toast.LENGTH_SHORT).show()
        }
    }

    Titre(if (perso != null) "MA CRÉATION" else "CRÉER À PARTIR DE CE MODÈLE")
    Spacer(Modifier.height(8.dp))
    if (perso != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Les anciens modèles par blocs (v0.1.26-28) restent jouables, mais ne se modifient plus.
            if (perso is MesModelesStore.Perso.Dessin) {
                Option("✎ Modifier", false, Modifier.weight(1f)) {
                    CreateurActivity.ouvrir(context, id = entree.id)
                    onFermer()
                }
            }
            Option("⇪ Partager", false, Modifier.weight(1f)) { scope.launch { partagerJson(context, app, entree.id) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Option("⇩ Fichier .json", false, Modifier.weight(1f)) { fichier.launch(nomFichier(entree.nom)) }
            Option("🗑 Supprimer", false, Modifier.weight(1f)) { confirmer = true }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Partager envoie le modèle en JSON (même format que les modèles intégrés) : il s'importe dans Verso avec « Importer ».",
            color = Dim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Option("✎ Dessiner à partir de ce modèle", false, Modifier.fillMaxWidth()) {
            CreateurActivity.ouvrir(context, copie = entree.id)
            onFermer()
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (apercu?.dynamique != null) "Le dessin part de l'image actuelle (un modèle calculé en direct ne peut pas être copié tel quel)."
            else "Le modèle d'origine ne change pas : tu modifies une copie, rangée dans « Mes modèles ».",
            color = Dim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            containerColor = Panel,
            titleContentColor = Color.White,
            textContentColor = Dim,
            title = { Text("Supprimer « ${entree.nom} » ?") },
            text = { Text("Le modèle disparaît de la bibliothèque, des favoris et du widget. Pense à le partager d'abord pour en garder une copie.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmer = false
                    scope.launch {
                        supprimerPerso(app, entree.id)
                        Toast.makeText(context, "« ${entree.nom} » supprimé", Toast.LENGTH_SHORT).show()
                        onFermer()
                    }
                }) { Text("Supprimer", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler", color = Color.White) } },
        )
    }
}
