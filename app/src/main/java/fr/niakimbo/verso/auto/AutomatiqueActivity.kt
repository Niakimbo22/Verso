package fr.niakimbo.verso.auto

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.ui.DotMatrix
import fr.niakimbo.verso.ui.apercuAnime
import java.time.DayOfWeek

/**
 * Règles automatiques : la liste (avec des idées toutes faites) et l'éditeur guidé « Si… alors… ».
 * Simple par défaut, avec « Plus d'options » replié pour aller plus loin.
 */
class AutomatiqueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VersoApp
        setContent { EcranAuto(app, onFermer = ::finish) }
    }

    companion object {
        fun ouvrir(context: Context) = context.startActivity(Intent(context, AutomatiqueActivity::class.java))
    }
}

/** Ce qu'on édite : une règle (nouvelle ou existante). */
private data class Edition(val regle: Regle, val nouvelle: Boolean)

@Composable
private fun EcranAuto(app: VersoApp, onFermer: () -> Unit) {
    val moteur = app.moteurRegles
    val regles by moteur.store.regles.collectAsStateWithLifecycle()
    val actif by moteur.store.actif.collectAsStateWithLifecycle()
    val enCours by moteur.enCours.collectAsStateWithLifecycle()
    val revision by app.catalogue.revision.collectAsStateWithLifecycle()
    val contenu by produceState<Catalogue.Contenu?>(null, revision) { value = app.catalogue.contenu() }
    var edition by remember { mutableStateOf<Edition?>(null) }

    val ed = edition
    if (ed != null) {
        BackHandler { edition = null }
        EditeurRegle(app, ed, contenu, onFini = { edition = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fond)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 24.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onFermer).padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("Automatique", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(
                checked = actif,
                onCheckedChange = { moteur.store.setActif(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
            )
        }
        Spacer(Modifier.height(16.dp))

        // En ce moment.
        val regleEnCours = regles.firstOrNull { it.id == enCours }
        Column(Modifier.fillMaxWidth().carte(actif = regleEnCours != null && actif).padding(16.dp)) {
            Etiquette("EN CE MOMENT", couleur = if (regleEnCours != null && actif) Rouge else Discret)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !actif -> "Automatique coupé : rien ne change tout seul."
                    regleEnCours != null -> "« ${regleEnCours.nom} » → ${nomModele(regleEnCours.modele, contenu)}"
                    regles.isEmpty() -> "Aucune règle. Prends une idée plus bas, ou crée la tienne."
                    else -> "Aucune règle en cours : ton choix reste affiché."
                },
                color = Color.White,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(28.dp))
        if (regles.isNotEmpty()) {
            Etiquette("RÈGLES · ${regles.size}")
            Spacer(Modifier.height(4.dp))
            Text(
                "Celle du haut gagne. Appui long puis glisser pour changer l'ordre.",
                color = Discret, fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            ListeReordonnable(regles, { it.id }, onOrdre = { moteur.store.ordonner(it) }) { regle, _ ->
                CarteRegle(
                    regle, contenu,
                    enCours = actif && regle.id == enCours,
                    onTap = { edition = Edition(regle, nouvelle = false) },
                    onActive = { moteur.store.enregistrer(regle.copy(active = it)) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(Forme)
                .background(Rouge)
                .clickable {
                    val type = TypeQuand.HORAIRE
                    edition = Edition(
                        Regle(moteur.store.nouvelId(), "", type.parDefaut(), app.store.modeleActif ?: "lune"),
                        nouvelle = true,
                    )
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("＋  Nouvelle règle", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Idées toutes faites.
        val idees = Recettes.toutes.filterNot { Recettes.dejaLa(it, regles) }
        if (idees.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Etiquette("IDÉES · UN TAP POUR AJOUTER")
            Spacer(Modifier.height(12.dp))
            val context = LocalContext.current
            val vibreur = LocalHapticFeedback.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                idees.forEach { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .carte()
                            .clickable {
                                moteur.store.enregistrer(Regle(moteur.store.nouvelId(), r.nom, r.quand, r.modele))
                                vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                                Toast.makeText(context, "« ${r.nom} » ajoutée", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Picto(TypeQuand.de(r.quand), Color.White, Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.titre, color = Color.White, fontSize = 14.sp)
                            Text(Textes.quand(r.quand), color = Discret, fontSize = 12.sp)
                        }
                        MiniApercu(r.modele, contenu, Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("＋", color = Rouge, fontSize = 20.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Une règle change le modèle quand elle commence. Si tu choisis un autre modèle à la main, " +
                "il reste jusqu'au prochain changement automatique. Quand une règle se termine, le modèle d'avant revient.",
            color = Discret, fontSize = 12.sp, lineHeight = 17.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CarteRegle(regle: Regle, contenu: Catalogue.Contenu?, enCours: Boolean, onTap: () -> Unit, onActive: (Boolean) -> Unit) {
    val type = TypeQuand.de(regle.quand)
    Row(
        Modifier
            .fillMaxWidth()
            .carte(actif = enCours)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Picto(type, if (!regle.active) Discret else if (enCours) Rouge else Color.White, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    regle.nom.ifBlank { type.titre },
                    color = if (regle.active) Color.White else Discret,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (enCours) {
                    Spacer(Modifier.width(8.dp))
                    Text("● EN COURS", color = Rouge, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Text(Textes.quand(regle.quand), color = Discret, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("→ ${nomModele(regle.modele, contenu)}", color = Discret, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        MiniApercu(regle.modele, contenu, Modifier.size(40.dp))
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = regle.active,
            onCheckedChange = onActive,
            colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
        )
    }
}

private fun nomModele(id: String?, contenu: Catalogue.Contenu?): String =
    if (id == null) "matrice éteinte" else contenu?.parId(id)?.nom ?: id

/** Aperçu fixe d'un modèle (null = matrice éteinte). */
@Composable
private fun MiniApercu(id: String?, contenu: Catalogue.Contenu?, modifier: Modifier, app: VersoApp = LocalContext.current.applicationContext as VersoApp) {
    val entree = id?.let { contenu?.parId(it) }
    val modele = remember(entree) { entree?.let { app.catalogue.resoudre(it) } }
    DotMatrix(modele?.let { if (it.dynamique != null) it.frameDynamique() else it.apercu }, modifier)
}

// --------------------------------------------------------------------------------------------
// Éditeur « Si… alors… »
// --------------------------------------------------------------------------------------------

@Composable
private fun EditeurRegle(app: VersoApp, edition: Edition, contenu: Catalogue.Contenu?, onFini: () -> Unit) {
    val store = app.moteurRegles.store
    val context = LocalContext.current
    var regle by remember(edition) { mutableStateOf(edition.regle) }
    var choixModele by remember { mutableStateOf(false) }
    var plus by remember { mutableStateOf(false) }
    var confirmer by remember { mutableStateOf(false) }
    val type = TypeQuand.de(regle.quand)

    if (choixModele) {
        BackHandler { choixModele = false }
        ChoixModele(app, contenu, regle.modele, onChoix = { regle = regle.copy(modele = it); choixModele = false }, onRetour = { choixModele = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fond)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 24.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onFini).padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (edition.nouvelle) "Nouvelle règle" else "Modifier la règle",
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(20.dp))

        // 1. SI…
        Etiquette("1 · SI…")
        Spacer(Modifier.height(10.dp))
        TypeQuand.entries.chunked(3).forEach { ligne ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { t ->
                    val choisi = t == type
                    Column(
                        Modifier
                            .weight(1f)
                            .carte(actif = choisi)
                            .clickable { if (!choisi) regle = regle.copy(quand = t.parDefaut()) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Picto(t, if (choisi) Rouge else Color.White, Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(t.titre, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(type.sousTitre, color = Discret, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        ReglagesSimples(regle.quand) { regle = regle.copy(quand = it) }

        // 2. ALORS…
        Spacer(Modifier.height(28.dp))
        Etiquette("2 · ALORS AFFICHER…")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().carte().clickable { choixModele = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val entree = regle.modele?.let { contenu?.parId(it) }
            val modele = remember(entree) { entree?.let { app.catalogue.resoudre(it) } }
            DotMatrix(modele?.let { apercuAnime(it) }, Modifier.size(84.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(nomModele(regle.modele, contenu), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Toucher pour changer", color = Discret, fontSize = 12.sp)
            }
            Text("›", color = Discret, fontSize = 24.sp)
        }

        // Plus d'options.
        Spacer(Modifier.height(24.dp))
        Text(
            (if (plus) "▾ " else "▸ ") + "PLUS D'OPTIONS",
            color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { plus = !plus }.padding(vertical = 8.dp),
        )
        if (plus) {
            Spacer(Modifier.height(8.dp))
            PlusDOptions(app, regle) { regle = it }
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!edition.nouvelle) {
                Box(
                    Modifier.weight(1f).clip(Forme).border(1.dp, Bord, Forme).clickable { confirmer = true }.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Supprimer", color = Rouge, fontSize = 15.sp) }
            }
            Box(
                Modifier.weight(2f).clip(Forme).background(Rouge).clickable {
                    store.enregistrer(regle.copy(nom = regle.nom.trim().ifBlank { type.titre }))
                    Toast.makeText(context, if (edition.nouvelle) "Règle ajoutée" else "Règle enregistrée", Toast.LENGTH_SHORT).show()
                    onFini()
                }.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("✓  Enregistrer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            containerColor = Panneau,
            titleContentColor = Color.White,
            textContentColor = Discret,
            title = { Text("Supprimer « ${regle.nom.ifBlank { type.titre }} » ?") },
            text = { Text("Si elle est en cours, le modèle d'avant revient.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmer = false
                    store.supprimer(regle.id)
                    onFini()
                }) { Text("Supprimer", color = Rouge) }
            },
            dismissButton = { TextButton(onClick = { confirmer = false }) { Text("Annuler", color = Color.White) } },
        )
    }
}

/** Ce qu'on règle sans ouvrir « Plus d'options » : les heures, le seuil de batterie. */
@Composable
private fun ReglagesSimples(d: Declencheur, onChange: (Declencheur) -> Unit) {
    when (d) {
        is Declencheur.Plage -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BoutonHeure("DE", d.debut, Modifier.weight(1f)) { onChange(d.copy(debut = it)) }
            BoutonHeure("À", d.fin, Modifier.weight(1f)) { onChange(d.copy(fin = it)) }
        }
        is Declencheur.BatterieFaible -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 20, 30).forEach { s ->
                Pastille("$s %", d.seuil == s, Modifier.weight(1f)) { onChange(d.copy(seuil = s)) }
            }
        }
        else -> Unit
    }
}

@Composable
private fun BoutonHeure(titre: String, minutes: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier
            .carte()
            .clickable {
                TimePickerDialog(
                    context, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                    { _, h, m -> onChange(h * 60 + m) }, minutes / 60, minutes % 60, true,
                ).show()
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Etiquette(titre)
        Spacer(Modifier.height(4.dp))
        Text(Textes.heure(minutes), color = Color.White, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlusDOptions(app: VersoApp, regle: Regle, onChange: (Regle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Nom.
        Column {
            Etiquette("NOM")
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = regle.nom,
                onValueChange = { onChange(regle.copy(nom = it.take(30))) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Rouge),
                modifier = Modifier.fillMaxWidth().carte().padding(14.dp),
                decorationBox = { champ ->
                    if (regle.nom.isEmpty()) Text(TypeQuand.de(regle.quand).titre, color = Discret, fontSize = 16.sp)
                    champ()
                },
            )
        }

        when (val d = regle.quand) {
            is Declencheur.Plage -> Column {
                Etiquette("JOURS · ${Textes.jours(d.jours)}")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Textes.lettres.forEach { (jour, lettre) ->
                        val dedans = jour in d.jours
                        Pastille(lettre, dedans, Modifier.weight(1f)) {
                            val j = if (dedans) d.jours - jour else d.jours + jour
                            if (j.isNotEmpty()) onChange(regle.copy(quand = d.copy(jours = j)))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pastille("Tous", d.jours == Declencheur.TOUS_LES_JOURS, Modifier.weight(1f)) {
                        onChange(regle.copy(quand = d.copy(jours = Declencheur.TOUS_LES_JOURS)))
                    }
                    Pastille("Semaine", Textes.jours(d.jours) == "en semaine", Modifier.weight(1f)) {
                        onChange(regle.copy(quand = d.copy(jours = DayOfWeek.entries.take(5).toSet())))
                    }
                    Pastille("Week-end", Textes.jours(d.jours) == "le week-end", Modifier.weight(1f)) {
                        onChange(regle.copy(quand = d.copy(jours = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Une plage qui passe minuit (ex. 22:00 → 07:00) compte pour le jour où elle commence. " +
                        "Si l'« Horaire du coucher » de Glyph Interface éteint la matrice la nuit, rien ne s'affichera pendant ce temps.",
                    color = Discret, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            is Declencheur.Reveil -> Column {
                Etiquette("FENÊTRE DU RÉVEIL")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonHeure("APRÈS", d.debut, Modifier.weight(1f)) { onChange(regle.copy(quand = d.copy(debut = it))) }
                    BoutonHeure("AVANT", d.fin, Modifier.weight(1f)) { onChange(regle.copy(quand = d.copy(fin = it))) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le premier déverrouillage dans cette fenêtre déclenche la règle, une fois par jour. " +
                        "Le modèle reste ensuite jusqu'au prochain changement.",
                    color = Discret, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            is Declencheur.Casque -> Column {
                Etiquette("QUEL CASQUE ?")
                Spacer(Modifier.height(8.dp))
                val connectes = remember { app.moteurRegles.casquesConnectes() }
                val noms = (listOfNotNull(d.appareil) + connectes).distinct()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pastille("N'importe lequel", d.appareil == null, Modifier.fillMaxWidth()) {
                        onChange(regle.copy(quand = d.copy(appareil = null)))
                    }
                    noms.forEach { nom ->
                        Pastille(nom, d.appareil == nom, Modifier.fillMaxWidth()) {
                            onChange(regle.copy(quand = d.copy(appareil = nom)))
                        }
                    }
                }
                if (connectes.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Connecte ton casque pour le voir apparaître ici.", color = Discret, fontSize = 12.sp)
                }
            }
            else -> Unit
        }

        if (!regle.quand.estMoment) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("À la fin, revenir au modèle d'avant", color = Color.White, fontSize = 14.sp)
                    Text(
                        if (regle.revenir) "Sinon, le modèle de la règle reste affiché." else "Le modèle de la règle restera affiché.",
                        color = Discret, fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = regle.revenir,
                    onCheckedChange = { onChange(regle.copy(revenir = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
                )
            }
        }
    }
}

/** Choix du modèle à afficher : éteindre, favoris, puis toute la bibliothèque. */
@Composable
private fun ChoixModele(app: VersoApp, contenu: Catalogue.Contenu?, actuel: String?, onChoix: (String?) -> Unit, onRetour: () -> Unit) {
    val favoris by app.favoris.ids.collectAsStateWithLifecycle()
    val entrees = contenu?.entrees?.filter { !it.cache }.orEmpty()
    val parId = entrees.associateBy { it.id }
    Column(
        Modifier
            .fillMaxSize()
            .background(Fond)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = Color.White, fontSize = 24.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onRetour).padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("Afficher…", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().carte(actif = actuel == null).clickable { onChoix(null) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotMatrix(null, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text("Éteindre la matrice", color = Color.White, fontSize = 15.sp)
        }
        if (contenu == null) {
            Spacer(Modifier.height(24.dp))
            Text("Chargement…", color = Discret)
            return@Column
        }
        val favs = favoris.mapNotNull { parId[it] }
        if (favs.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Etiquette("★ FAVORIS")
            Spacer(Modifier.height(10.dp))
            Grille(app, favs, actuel, onChoix)
        }
        entrees.groupBy { it.categorie }.forEach { (rayon, liste) ->
            Spacer(Modifier.height(24.dp))
            Etiquette(rayon.uppercase())
            Spacer(Modifier.height(10.dp))
            Grille(app, liste, actuel, onChoix)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Grille(app: VersoApp, entrees: List<EntreeCatalogue>, actuel: String?, onChoix: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entrees.chunked(4).forEach { ligne ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { e ->
                    val modele: Modele = remember(e) { app.catalogue.resoudre(e) }
                    Column(
                        Modifier.weight(1f).carte(actif = e.id == actuel).clickable { onChoix(e.id) }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DotMatrix(
                            if (modele.dynamique != null) modele.frameDynamique() else modele.apercu,
                            Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Text(
                            e.nom, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(4 - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
