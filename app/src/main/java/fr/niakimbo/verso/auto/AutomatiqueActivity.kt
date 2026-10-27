package fr.niakimbo.verso.auto

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.reglage.Catalogue
import fr.niakimbo.verso.reglage.EntreeCatalogue
import fr.niakimbo.verso.ui.DotMatrix
import fr.niakimbo.verso.ui.apercuAnime
import fr.niakimbo.verso.ui.verre.BoutonVerre
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.EcranVerre
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.ui.verre.RondVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import java.time.DayOfWeek
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Règles automatiques, en Liquid Glass : la liste (avec des idées toutes faites), l'éditeur
 * « Si… alors… » (une phrase résume la règle en haut), le catalogue des conditions et le choix du modèle.
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

/** [appsDAbord] : ouvrir tout de suite le choix des apps (idée dont l'app n'a pas été trouvée). */
private data class Edition(val regle: Regle, val nouvelle: Boolean, val appsDAbord: Boolean = false)

/** Une règle « App ouverte » passe en tête : elle doit gagner tant que l'app est à l'écran. */
private fun enTete(r: Regle) = r.conditions.any { it is Declencheur.AppOuverte }

/** Nom lisible d'un modèle. */
private fun nomDe(contenu: Catalogue.Contenu?): (String) -> String = { id -> contenu?.parId(id)?.nom ?: id }

@Composable
private fun EcranAuto(app: VersoApp, onFermer: () -> Unit) {
    val revision by app.catalogue.revision.collectAsStateWithLifecycle()
    val contenu by produceState<Catalogue.Contenu?>(null, revision) { value = app.catalogue.contenu() }
    var edition by remember { mutableStateOf<Edition?>(null) }

    val ed = edition
    if (ed != null) {
        BackHandler { edition = null }
        EditeurRegle(app, ed, contenu, onFini = { edition = null })
    } else {
        ListeRegles(app, contenu, onFermer, onEditer = { edition = it })
    }
}

/** Barre du haut flottante, en verre : ← titre … [à droite]. */
@Composable
private fun BoxScope.BarreHaut(titre: String, onRetour: () -> Unit, droite: @Composable () -> Unit = {}) {
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RondVerre("←", onRetour)
        Spacer(Modifier.width(12.dp))
        PanneauVerre(Modifier.weight(1f), forme = RoundedCornerShape(50)) {
            Text(
                titre, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        droite()
    }
}

/** Colonne qui défile sous les barres flottantes. */
@Composable
private fun Defilant(scroll: ScrollState, contenu: @Composable () -> Unit) {
    Column(
        Modifier
            .verticalScroll(scroll)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 76.dp, bottom = 110.dp),
    ) { contenu() }
}

// --------------------------------------------------------------------------------------------
// Liste des règles
// --------------------------------------------------------------------------------------------

@Composable
private fun ListeRegles(app: VersoApp, contenu: Catalogue.Contenu?, onFermer: () -> Unit, onEditer: (Edition) -> Unit) {
    val moteur = app.moteurRegles
    val regles by moteur.store.regles.collectAsStateWithLifecycle()
    val actif by moteur.store.actif.collectAsStateWithLifecycle()
    val enCours by moteur.enCours.collectAsStateWithLifecycle()
    val modeleActif by app.player.actif.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val nom = nomDe(contenu)
    val portee = rememberCoroutineScope()

    fun nouvelle() = onEditer(
        Edition(
            Regle(moteur.store.nouvelId(), "", listOf(TypeQuand.HORAIRE.parDefaut()), Action.Afficher(app.store.modeleActif ?: "lune")),
            nouvelle = true,
        ),
    )

    EcranVerre(
        defilement = scroll,
        dessus = {
            BarreHaut("Automatique", onFermer) {
                Spacer(Modifier.width(10.dp))
                PanneauVerre(forme = RoundedCornerShape(50)) {
                    Switch(
                        checked = actif, onCheckedChange = { moteur.store.setActif(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            BoutonVerre(
                "＋  Nouvelle règle", ::nouvelle, principal = true,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        },
    ) {
        Defilant(scroll) {
            // En ce moment.
            val regleEnCours = regles.firstOrNull { it.id == enCours }?.takeIf { actif }
            PanneauVerre(Modifier.fillMaxWidth(), actif = regleEnCours != null) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    fr.niakimbo.verso.ui.ApercuModele(modeleActif, animer = true, modifier = Modifier.size(92.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Etiquette("EN CE MOMENT", couleur = if (regleEnCours != null) Rouge else Discret)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            modeleActif?.nom ?: "Matrice éteinte",
                            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                !actif -> "Automatique coupé : rien ne change tout seul."
                                regleEnCours != null -> "Règle « ${regleEnCours.nom} »"
                                regles.isEmpty() -> "Aucune règle pour l'instant."
                                else -> "Ton choix, jusqu'au prochain changement."
                            },
                            color = Discret, fontSize = 13.sp,
                        )
                    }
                }
            }

            if (regles.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Etiquette("MES RÈGLES · ${regles.size}")
                Spacer(Modifier.height(4.dp))
                Text("Celle du haut gagne. Appui long puis glisser pour l'ordre.", color = Discret, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                ListeReordonnable(regles, { it.id }, onOrdre = { moteur.store.ordonner(it) }) { regle, _ ->
                    CarteRegle(
                        app, regle, contenu,
                        enCours = actif && regle.id == enCours,
                        onTap = { onEditer(Edition(regle, nouvelle = false)) },
                        onActive = { moteur.store.enregistrer(regle.copy(active = it)) },
                    )
                }
            }

            val idees = Recettes.toutes.filterNot { Recettes.dejaLa(it, regles) }
            if (idees.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Etiquette("IDÉES · UN TAP POUR AJOUTER")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    idees.forEach { r ->
                        val exemple = remember(r) { r.regle("x") }
                        PanneauVerre(
                            Modifier.width(150.dp),
                            onClick = {
                                val genre = r.apps
                                if (genre == null) {
                                    moteur.store.enregistrer(r.regle(moteur.store.nouvelId()))
                                    Toast.makeText(context, "« ${r.nom} » ajoutée", Toast.LENGTH_SHORT).show()
                                } else portee.launch {
                                    // Idée « App ouverte » : on cherche l'app sur le téléphone ; pas trouvée = on la fait choisir.
                                    val apps = Apps.suggerees(context, genre)
                                    val regle = r.regle(moteur.store.nouvelId()).let { base ->
                                        base.copy(conditions = base.conditions.map { c -> if (c is Declencheur.AppOuverte) c.copy(apps = apps) else c })
                                    }
                                    if (apps.isEmpty()) {
                                        onEditer(Edition(regle, nouvelle = true, appsDAbord = true))
                                    } else {
                                        moteur.store.enregistrer(regle, enHaut = true)
                                        Toast.makeText(context, "« ${r.nom} » ajoutée · ${apps.noms("")}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    exemple.conditions.take(2).forEach {
                                        Picto(TypeQuand.de(it), Color.White, Modifier.size(22.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text("＋", color = Rouge, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(r.titre, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, minLines = 3)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Une règle change le modèle quand elle commence. Un modèle choisi à la main reste jusqu'au " +
                    "prochain changement automatique. À la fin d'une règle, le modèle d'avant revient.",
                color = Discret, fontSize = 12.sp, lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun CarteRegle(app: VersoApp, regle: Regle, contenu: Catalogue.Contenu?, enCours: Boolean, onTap: () -> Unit, onActive: (Boolean) -> Unit) {
    PanneauVerre(Modifier.fillMaxWidth(), actif = enCours, onClick = onTap) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    regle.conditions.take(4).forEach {
                        Picto(TypeQuand.de(it), if (!regle.active) Discret else if (enCours) Rouge else Color.White, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    if (enCours) Text("● EN COURS", color = Rouge, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    regle.nom.ifBlank { TypeQuand.de(regle.conditions.first()).titre },
                    color = if (regle.active) Color.White else Discret, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(Textes.phrase(regle, nomDe(contenu)), color = Discret, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            ApercuAction(app, regle.action, contenu, Modifier.size(44.dp), anime = false)
            Spacer(Modifier.width(4.dp))
            Switch(checked = regle.active, onCheckedChange = onActive, colors = SwitchDefaults.colors(checkedTrackColor = Rouge))
        }
    }
}

/** Aperçu de ce que fait une action : le modèle, ou un symbole (hasard, défilement, éteint). */
@Composable
private fun ApercuAction(app: VersoApp, action: Action, contenu: Catalogue.Contenu?, modifier: Modifier, anime: Boolean) {
    when (action) {
        is Action.Afficher -> {
            val entree = contenu?.parId(action.modele)
            val modele = remember(entree) { entree?.let { app.catalogue.resoudre(it) } }
            fr.niakimbo.verso.ui.ApercuModele(modele, anime, modifier)
        }
        Action.Eteindre -> DotMatrix(null, modifier)
        Action.Hasard, is Action.Defiler -> Box(modifier, contentAlignment = Alignment.Center) {
            DotMatrix(null, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(if (action == Action.Hasard) "?" else "⇄", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// --------------------------------------------------------------------------------------------
// Éditeur « Si… alors… »
// --------------------------------------------------------------------------------------------

private sealed class Vue {
    data object Regle : Vue()
    data object Catalogue : Vue()
    data object Modele : Vue()

    /** Choix des apps de la condition n° [index]. */
    data class Apps(val index: Int) : Vue()
}

@Composable
private fun EditeurRegle(app: VersoApp, edition: Edition, contenu: Catalogue.Contenu?, onFini: () -> Unit) {
    val store = app.moteurRegles.store
    val context = LocalContext.current
    var regle by remember(edition) { mutableStateOf(edition.regle) }
    var vue by remember { mutableStateOf<Vue>(if (edition.appsDAbord) Vue.Apps(0) else Vue.Regle) }
    var ouverte by remember { mutableIntStateOf(regle.conditions.lastIndex) }
    var confirmer by remember { mutableStateOf(false) }
    var plus by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    // La situation actuelle, relue toutes les 3 s : « vrai maintenant » sous chaque condition.
    val ctx by produceState(app.moteurRegles.contexte()) {
        while (true) {
            delay(3_000)
            value = app.moteurRegles.contexte()
        }
    }

    when (vue) {
        Vue.Catalogue -> {
            BackHandler { vue = Vue.Regle }
            CatalogueConditions(onRetour = { vue = Vue.Regle }) { type ->
                regle = regle.copy(conditions = regle.conditions + type.parDefaut())
                ouverte = regle.conditions.lastIndex
                // « App ouverte » sans app ne sert à rien : on choisit l'app tout de suite.
                vue = if (type == TypeQuand.APP) Vue.Apps(regle.conditions.lastIndex) else Vue.Regle
            }
            return
        }
        Vue.Modele -> {
            BackHandler { vue = Vue.Regle }
            ChoixModele(app, contenu, (regle.action as? Action.Afficher)?.modele, onRetour = { vue = Vue.Regle }) {
                regle = regle.copy(action = Action.Afficher(it))
                vue = Vue.Regle
            }
            return
        }
        is Vue.Apps -> {
            val i = (vue as Vue.Apps).index
            val d = regle.conditions.getOrNull(i)
            BackHandler { vue = Vue.Regle }
            val apps = when (d) {
                is Declencheur.Notification -> d.apps
                is Declencheur.Musique -> d.apps
                is Declencheur.AppOuverte -> d.apps
                else -> emptyList()
            }
            ChoixApps(
                titre = "Chercher une app",
                choisies = apps,
                // « App ouverte » : il faut au moins une app.
                tout = if (d is Declencheur.AppOuverte) null else "Toutes les apps",
                onRetour = { vue = Vue.Regle },
            ) { choix ->
                val nouveau = when (d) {
                    is Declencheur.Notification -> d.copy(apps = choix)
                    is Declencheur.Musique -> d.copy(apps = choix)
                    is Declencheur.AppOuverte -> d.copy(apps = choix)
                    else -> d
                }
                if (nouveau != null) regle = regle.copy(conditions = regle.conditions.mapIndexed { j, c -> if (j == i) nouveau else c })
                vue = Vue.Regle
            }
            return
        }
        Vue.Regle -> Unit
    }

    // Une règle « flash » (notification) n'affiche qu'un modèle : pas de défilement ni d'extinction.
    LaunchedEffect(regle.estFlash, regle.action) {
        if (regle.estFlash && (regle.action is Action.Defiler || regle.action == Action.Eteindre)) {
            regle = regle.copy(action = Action.Afficher(app.store.modeleActif ?: "notification"))
        }
    }

    fun enregistrer() {
        val nom = regle.nom.trim().ifBlank { TypeQuand.de(regle.conditions.first()).titre }
        val enHaut = edition.nouvelle && enTete(regle)
        store.enregistrer(regle.copy(nom = nom), enHaut = enHaut)
        Toast.makeText(
            context,
            when {
                enHaut -> "Règle ajoutée ⬆ en tête"
                edition.nouvelle -> "Règle ajoutée"
                else -> "Règle enregistrée"
            },
            Toast.LENGTH_SHORT,
        ).show()
        onFini()
    }

    EcranVerre(
        defilement = scroll,
        dessus = {
            BarreHaut(if (edition.nouvelle) "Nouvelle règle" else "Modifier la règle", onFini)
            BoutonVerre(
                "✓  Enregistrer", ::enregistrer, principal = true,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp),
            )
        },
    ) {
        Defilant(scroll) {
            // Phrase résumé + aperçu.
            val maintenant = remember(regle, ctx) { Evaluation.vraie(regle, ctx) }
            PanneauVerre(Modifier.fillMaxWidth(), actif = maintenant && !regle.estMoment) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ApercuAction(app, regle.action, contenu, Modifier.size(84.dp), anime = true)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            Textes.phrase(regle, nomDe(contenu)),
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when {
                            regle.estFlash -> "⚡ À chaque notif : ${regle.secondesFlash} s, puis fondu"
                            regle.estMoment -> "Se déclenche au moment choisi, une fois par jour."
                            maintenant -> "● Ce serait le cas maintenant"
                            else -> "○ Pas le cas en ce moment"
                        },
                        color = if (maintenant && !regle.estMoment) Rouge else Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // SI…
            Spacer(Modifier.height(28.dp))
            Etiquette("SI…")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                regle.conditions.forEachIndexed { i, d ->
                    CarteCondition(
                        app, d, ctx,
                        onApps = { vue = Vue.Apps(i) },
                        ouverte = i == ouverte,
                        supprimable = regle.conditions.size > 1,
                        onOuvrir = { ouverte = if (ouverte == i) -1 else i },
                        onSupprimer = {
                            regle = regle.copy(conditions = regle.conditions.filterIndexed { j, _ -> j != i })
                            ouverte = -1
                        },
                        onChange = { nouveau -> regle = regle.copy(conditions = regle.conditions.mapIndexed { j, c -> if (j == i) nouveau else c }) },
                    )
                }
            }
            if (regle.conditions.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PuceVerre("Toutes (ET)", regle.toutes, Modifier.weight(1f)) { regle = regle.copy(toutes = true) }
                    PuceVerre("Au moins une (OU)", !regle.toutes, Modifier.weight(1f)) { regle = regle.copy(toutes = false) }
                }
            }
            Spacer(Modifier.height(12.dp))
            BoutonVerre("＋  Ajouter une condition", { vue = Vue.Catalogue }, Modifier.fillMaxWidth(), taille = 14.sp)

            // ALORS…
            Spacer(Modifier.height(28.dp))
            Etiquette("ALORS…")
            Spacer(Modifier.height(10.dp))
            val a = regle.action
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PuceVerre("Modèle", a is Action.Afficher, Modifier.weight(1f)) {
                    if (a !is Action.Afficher) regle = regle.copy(action = Action.Afficher(app.store.modeleActif ?: "lune"))
                }
                PuceVerre("Hasard", a == Action.Hasard, Modifier.weight(1f)) { regle = regle.copy(action = Action.Hasard) }
                if (!regle.estFlash) {
                    PuceVerre("Défiler", a is Action.Defiler, Modifier.weight(1f)) { if (a !is Action.Defiler) regle = regle.copy(action = Action.Defiler()) }
                    PuceVerre("Éteindre", a == Action.Eteindre, Modifier.weight(1f)) { regle = regle.copy(action = Action.Eteindre) }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (a) {
                is Action.Afficher -> PanneauVerre(Modifier.fillMaxWidth(), onClick = { vue = Vue.Modele }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ApercuAction(app, a, contenu, Modifier.size(56.dp), anime = false)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nomDe(contenu)(a.modele), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Toucher pour changer de modèle", color = Discret, fontSize = 12.sp)
                        }
                        Text("›", color = Discret, fontSize = 24.sp)
                    }
                }
                Action.Hasard -> Aide("Un favori au hasard à chaque déclenchement (toute la bibliothèque si tu n'as pas de favori).")
                is Action.Defiler -> Column {
                    Aide("Les favoris s'affichent les uns après les autres, dans leur ordre ★, tant que la règle dure.")
                    Spacer(Modifier.height(10.dp))
                    Choix(listOf(5, 10, 15, 30, 60).map { "$it min" to it }, a.minutes) { regle = regle.copy(action = Action.Defiler(it)) }
                }
                Action.Eteindre -> Aide("La matrice s'éteint tant que la règle dure.")
            }

            // Plus d'options.
            Spacer(Modifier.height(24.dp))
            Text(
                (if (plus) "▾ " else "▸ ") + "PLUS D'OPTIONS",
                color = Discret, fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
                modifier = Modifier.appui({ plus = !plus }).padding(vertical = 10.dp),
            )
            if (plus) {
                Spacer(Modifier.height(14.dp))
                PanneauVerre(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Etiquette("NOM")
                            Spacer(Modifier.height(8.dp))
                            BasicTextField(
                                value = regle.nom,
                                onValueChange = { regle = regle.copy(nom = it.take(30)) },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                                cursorBrush = SolidColor(Rouge),
                                decorationBox = { champ ->
                                    if (regle.nom.isEmpty()) Text(TypeQuand.de(regle.conditions.first()).titre, color = Discret, fontSize = 17.sp)
                                    champ()
                                },
                            )
                        }
                        if (regle.estPendant) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("À la fin, revenir au modèle d'avant", color = Color.White, fontSize = 14.sp)
                                    Text(
                                        if (regle.revenir) "Sinon, le modèle de la règle reste affiché." else "Le modèle de la règle restera affiché.",
                                        color = Discret, fontSize = 12.sp,
                                    )
                                }
                                Switch(
                                    checked = regle.revenir, onCheckedChange = { regle = regle.copy(revenir = it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Rouge),
                                )
                            }
                        }
                    }
                }
            }

            if (!edition.nouvelle) {
                Spacer(Modifier.height(28.dp))
                BoutonVerre("Supprimer la règle", { confirmer = true }, Modifier.fillMaxWidth(), taille = 14.sp, couleurTexte = Rouge)
            }
        }
    }

    if (confirmer) {
        AlertDialog(
            onDismissRequest = { confirmer = false },
            containerColor = Color(0xFF151515),
            titleContentColor = Color.White,
            textContentColor = Discret,
            title = { Text("Supprimer « ${regle.nom.ifBlank { TypeQuand.de(regle.conditions.first()).titre }} » ?") },
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

@Composable
private fun Aide(texte: String) = Text(texte, color = Discret, fontSize = 12.sp, lineHeight = 17.sp)

/** Rangée de puces à choix unique. */
@Composable
private fun <T> Choix(options: List<Pair<String, T>>, valeur: T, onChoix: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (libelle, v) -> PuceVerre(libelle, v == valeur, Modifier.weight(1f)) { onChoix(v) } }
    }
}

/** Une condition : picto, titre, résumé, « vrai maintenant », et ses réglages quand elle est ouverte. */
@Composable
private fun CarteCondition(
    app: VersoApp,
    d: Declencheur,
    ctx: Contexte,
    onApps: () -> Unit,
    ouverte: Boolean,
    supprimable: Boolean,
    onOuvrir: () -> Unit,
    onSupprimer: () -> Unit,
    onChange: (Declencheur) -> Unit,
) {
    val type = TypeQuand.de(d)
    val vrai = !d.estMoment && !d.estFlash && Evaluation.vraie(d, ctx)
    PanneauVerre(Modifier.fillMaxWidth(), actif = ouverte) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).appui(onOuvrir)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Picto(type, if (ouverte) Rouge else Color.White, Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(type.titre, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(Textes.quand(d).replaceFirstChar { it.uppercase() }, color = Discret, fontSize = 12.sp, maxLines = 2)
                        }
                        Text(
                            if (d.estFlash) "⚡" else if (d.estMoment) "◷" else if (vrai) "●" else "○",
                            color = if (vrai) Rouge else Discret, fontSize = 14.sp,
                        )
                    }
                }
                if (supprimable) {
                    Spacer(Modifier.width(8.dp))
                    Text("✕", color = Discret, fontSize = 16.sp, modifier = Modifier.appui(onSupprimer).padding(6.dp))
                }
            }
            if (ouverte) {
                Spacer(Modifier.height(14.dp))
                ReglagesCondition(app, d, ctx, onApps, onChange)
            }
        }
    }
}


/** Réglages de chaque type de condition : toujours des boutons, jamais de menu caché. */
@Composable
private fun ReglagesCondition(app: VersoApp, d: Declencheur, ctx: Contexte, onApps: () -> Unit, onChange: (Declencheur) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (d) {
            is Declencheur.Plage -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonHeure("DE", d.debut, Modifier.weight(1f)) { onChange(d.copy(debut = it)) }
                    BoutonHeure("À", d.fin, Modifier.weight(1f)) { onChange(d.copy(fin = it)) }
                }
                Jours(d.jours) { onChange(d.copy(jours = it)) }
                Aide("Une plage qui passe minuit compte pour le jour où elle commence. Même heure de début et de fin = toute la journée.")
            }
            is Declencheur.AHeure -> {
                BoutonHeure("À", d.minute, Modifier.fillMaxWidth()) { onChange(d.copy(minute = it)) }
                Jours(d.jours) { onChange(d.copy(jours = it)) }
            }
            is Declencheur.Reveil -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonHeure("APRÈS", d.debut, Modifier.weight(1f)) { onChange(d.copy(debut = it)) }
                    BoutonHeure("AVANT", d.fin, Modifier.weight(1f)) { onChange(d.copy(fin = it)) }
                }
                Aide("Le premier déverrouillage dans cette fenêtre, une fois par jour.")
            }
            is Declencheur.Soleil -> {
                Choix(listOf("La nuit" to true, "Le jour" to false), d.nuit) { onChange(d.copy(nuit = it)) }
                Choix(listOf("−1 h" to -60, "−30" to -30, "Pile" to 0, "+30" to 30, "+1 h" to 60), d.decalage) { onChange(d.copy(decalage = it)) }
                ChoixVille(app)
            }
            is Declencheur.Date -> {
                val context = LocalContext.current
                BoutonGrand("LE", "${d.jour} ${Textes.nomMois(d.mois)}", Modifier.fillMaxWidth()) {
                    DatePickerDialog(
                        context, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                        { _, _, m, j -> onChange(Declencheur.Date(j, m + 1)) },
                        java.time.LocalDate.now().year, d.mois - 1, d.jour,
                    ).show()
                }
                Aide("Toute la journée, chaque année.")
            }
            is Declencheur.AvantAlarme -> {
                Choix(listOf("15 min" to 15, "30 min" to 30, "1 h" to 60, "2 h" to 120), d.minutes) { onChange(d.copy(minutes = it)) }
                Aide(
                    ctx.prochaineAlarme?.let { "Prochaine alarme : ${"%02d:%02d".format(it.hour, it.minute)} (le ${it.dayOfMonth}/${it.monthValue})." }
                        ?: "Aucune alarme programmée dans l'app Horloge pour l'instant.",
                )
            }
            is Declencheur.Charge -> Choix(
                listOf("Tout" to null, "Câble" to Declencheur.CABLE, "Sans fil" to Declencheur.SANS_FIL), d.source,
            ) { onChange(d.copy(source = it)) }
            is Declencheur.BatterieFaible -> Choix(listOf(10, 15, 20, 30).map { "$it %" to it }, d.seuil) { onChange(d.copy(seuil = it)) }
            is Declencheur.BatteriePleine -> Choix(listOf(80, 90, 100).map { "$it %" to it }, d.seuil) { onChange(d.copy(seuil = it)) }
            is Declencheur.Ecran -> Choix(listOf("Verrouillé" to true, "Déverrouillé" to false), d.verrouille) { onChange(d.copy(verrouille = it)) }
            is Declencheur.Sonnerie -> Choix(
                listOf("Les deux" to null, "Silencieux" to Declencheur.SILENCIEUX, "Vibreur" to Declencheur.VIBREUR), d.mode,
            ) { onChange(d.copy(mode = it)) }
            is Declencheur.Reseau -> Choix(
                listOf("Wi-Fi" to Declencheur.WIFI, "Données" to Declencheur.DONNEES, "Hors ligne" to Declencheur.AUCUN), d.type_,
            ) { onChange(d.copy(type_ = it)) }
            is Declencheur.Casque -> {
                val connectes = remember { app.moteurRegles.casquesConnectes() }
                Appareils((listOfNotNull(d.appareil) + connectes).distinct(), d.appareil) { onChange(d.copy(appareil = it)) }
                if (connectes.isEmpty()) Aide("Branche ton casque pour le voir apparaître ici.")
            }
            is Declencheur.Bluetooth -> ReglageBluetooth(app, d, onChange)
            is Declencheur.Notification -> {
                PermissionNotifs()
                BoutonApps(d.apps, "Toutes les apps", onApps)
                Choix(listOf(3, 5, 10, 30).map { "$it s" to it }, d.secondes) { onChange(d.copy(secondes = it)) }
                Aide("⚡ Flash puis fondu · 🌙 rien si la matrice est éteinte")
            }
            is Declencheur.Musique -> {
                PermissionNotifs()
                BoutonApps(d.apps, "N'importe quelle app", onApps)
                val joue = Musique.etat.collectAsStateWithLifecycle().value
                Aide(
                    if (joue?.enLecture == true) "▶ En ce moment : ${Apps.nom(LocalContext.current, joue.pkg) ?: joue.pkg}" + (joue.titre?.let { " · $it" } ?: "")
                    else "⏸ Rien ne joue en ce moment",
                )
            }
            is Declencheur.AppOuverte -> {
                CartePermission(
                    emoji = "📱",
                    titre = "Active « Verso · Glyph et apps » (Accessibilité)",
                    bouton = "Ouvrir Accessibilité",
                    autorise = AppPremierPlan::disponible,
                    ouvrir = AppPremierPlan::ouvrirAccessibilite,
                )
                if (!AppPremierPlan.disponible(LocalContext.current)) {
                    val context = LocalContext.current
                    Text(
                        "ou « Accès aux données d'utilisation » (un peu plus lent)",
                        color = Discret, fontSize = 11.sp,
                        modifier = Modifier.appui({ AppPremierPlan.ouvrirUsage(context) }).padding(vertical = 2.dp),
                    )
                }
                BoutonApps(d.apps, "Choisis une app", onApps)
                Aide("↩ Le modèle d'avant revient quand tu quittes l'app")
            }
            is Declencheur.Meteo -> ReglageMeteo(app, d, onChange)
            Declencheur.Economie, Declencheur.NePasDeranger, Declencheur.Avion, Declencheur.Lampe ->
                Aide("Rien à régler.")
        }
    }
}

/** Les ciels qui déclenchent la règle (plusieurs possibles), la météo du moment et la ville. */
@Composable
private fun ReglageMeteo(app: VersoApp, d: Declencheur.Meteo, onChange: (Declencheur) -> Unit) {
    val etat by Meteo.etat.collectAsStateWithLifecycle()
    val erreur by Meteo.erreur.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Meteo.rafraichir() }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Meteo.CIELS.forEach { c ->
            val dedans = c in d.ciels
            PuceVerre(Meteo.emoji(c), dedans, Modifier.weight(1f)) {
                val nouveaux = if (dedans) d.ciels - c else d.ciels + c
                if (nouveaux.isNotEmpty()) onChange(d.copy(ciels = nouveaux))
            }
        }
    }
    Aide(
        etat?.let { e ->
            val min = ((System.currentTimeMillis() - e.luA) / 60_000).coerceAtLeast(0)
            "${Meteo.emoji(e.ciel)} Maintenant : ${Meteo.nom(e.ciel)} · ${e.temperature}° · ${e.lieu} · il y a $min min"
        } ?: erreur ?: "Météo en cours de lecture…",
    )
    ChoixVille(app)
}

/** « Accès aux notifications » (notifs + musique), avec bouton direct vers le réglage. */
@Composable
private fun PermissionNotifs() = CartePermission(
    emoji = "🔔",
    titre = "Active « Accès aux notifications »",
    bouton = "Ouvrir le réglage",
    autorise = EcouteNotifs::autorise,
    ouvrir = EcouteNotifs::ouvrirReglage,
)

@Composable
private fun Appareils(noms: List<String>, choisi: String?, onChoix: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PuceVerre("N'importe lequel", choisi == null, Modifier.fillMaxWidth()) { onChoix(null) }
        noms.forEach { n -> PuceVerre(n, choisi == n, Modifier.fillMaxWidth()) { onChoix(n) } }
    }
}

@Composable
private fun ReglageBluetooth(app: VersoApp, d: Declencheur.Bluetooth, onChange: (Declencheur) -> Unit) {
    var autorise by remember { mutableStateOf(app.moteurRegles.bluetoothAutorise()) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { autorise = it }
    if (!autorise) {
        Aide("Verso a besoin de l'autorisation « Appareils à proximité » pour savoir quand ta montre ou ta voiture se connecte. Rien ne quitte le téléphone.")
        BoutonVerre("Autoriser", { demande.launch(Manifest.permission.BLUETOOTH_CONNECT) }, Modifier.fillMaxWidth(), principal = true, taille = 14.sp)
        return
    }
    val associes = remember { app.moteurRegles.appareilsBluetooth() }
    Appareils((listOfNotNull(d.appareil) + associes).distinct(), d.appareil) { onChange(d.copy(appareil = it)) }
    Aide("Un appareil compte comme connecté à partir de sa prochaine connexion.")
}

/** La ville du lever / coucher du soleil : saisie libre, cherchée avec le géocodeur du téléphone. */
@Composable
private fun ChoixVille(app: VersoApp) {
    val store = app.moteurRegles.store
    val lieu by store.lieu.collectAsStateWithLifecycle()
    var saisie by remember { mutableStateOf(lieu?.nom ?: "") }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Aide(Textes.soleil(lieu))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanneauVerre(Modifier.weight(1f), forme = RoundedCornerShape(50)) {
            BasicTextField(
                value = saisie, onValueChange = { saisie = it.take(40) }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Rouge),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                decorationBox = { champ ->
                    if (saisie.isEmpty()) Text("Ta ville (ex. Meylan)", color = Discret, fontSize = 15.sp)
                    champ()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        BoutonVerre("OK", {
            val nom = saisie.trim()
            if (nom.isEmpty()) return@BoutonVerre
            message = "Recherche…"
            try {
                Geocoder(context, Locale.FRANCE).getFromLocationName(nom, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(adresses: MutableList<android.location.Address>) {
                        val a = adresses.firstOrNull()
                        if (a == null) {
                            message = "Ville introuvable."
                        } else {
                            store.setLieu(Lieu(a.locality ?: nom, a.latitude, a.longitude))
                            message = null
                        }
                    }

                    override fun onError(erreur: String?) {
                        message = "Recherche impossible (Internet ?)."
                    }
                })
            } catch (_: Exception) {
                message = "Recherche impossible sur ce téléphone."
            }
        }, taille = 14.sp)
    }
    message?.let { Aide(it) }
}

@Composable
private fun Jours(jours: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Textes.lettres.forEach { (jour, lettre) ->
            val dedans = jour in jours
            PuceVerre(lettre, dedans, Modifier.weight(1f)) {
                val j = if (dedans) jours - jour else jours + jour
                if (j.isNotEmpty()) onChange(j)
            }
        }
    }
    Choix(
        listOf("Tous" to Declencheur.TOUS_LES_JOURS, "Semaine" to Textes.semaine, "Week-end" to Textes.weekEnd),
        jours,
    ) { onChange(it) }
}

@Composable
private fun BoutonHeure(titre: String, minutes: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    BoutonGrand(titre, Textes.heure(minutes), modifier) {
        TimePickerDialog(
            context, android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, h, m -> onChange(h * 60 + m) }, minutes / 60, minutes % 60, true,
        ).show()
    }
}

/** Grande valeur à toucher (heure, date). */
@Composable
private fun BoutonGrand(titre: String, valeur: String, modifier: Modifier, onClick: () -> Unit) {
    PanneauVerre(modifier, forme = RoundedCornerShape(20.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Etiquette(titre)
            Spacer(Modifier.height(2.dp))
            Text(valeur, color = Color.White, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

// --------------------------------------------------------------------------------------------
// Catalogue des conditions
// --------------------------------------------------------------------------------------------

@Composable
private fun CatalogueConditions(onRetour: () -> Unit, onChoix: (TypeQuand) -> Unit) {
    val scroll = rememberScrollState()
    EcranVerre(defilement = scroll, dessus = { BarreHaut("Ajouter une condition", onRetour) }) {
        Defilant(scroll) {
            Famille.entries.forEach { f ->
                Etiquette(f.titre.uppercase())
                Spacer(Modifier.height(10.dp))
                TypeQuand.entries.filter { it.famille == f }.chunked(3).forEach { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ligne.forEach { t ->
                            PanneauVerre(Modifier.weight(1f), forme = RoundedCornerShape(22.dp), onClick = { onChoix(t) }) {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Picto(t, Color.White, Modifier.size(34.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text(t.titre, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.sousTitre, color = Discret, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, minLines = 2)
                                }
                            }
                        }
                        repeat(3 - ligne.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// Choix du modèle
// --------------------------------------------------------------------------------------------

@Composable
private fun ChoixModele(app: VersoApp, contenu: Catalogue.Contenu?, actuel: String?, onRetour: () -> Unit, onChoix: (String) -> Unit) {
    val favoris by app.favoris.ids.collectAsStateWithLifecycle()
    val entrees = contenu?.entrees?.filter { !it.cache }.orEmpty()
    val parId = entrees.associateBy { it.id }
    val scroll = rememberScrollState()
    EcranVerre(defilement = scroll, dessus = { BarreHaut("Afficher…", onRetour) }) {
        Defilant(scroll) {
            if (contenu == null) {
                Aide("Chargement…")
                return@Defilant
            }
            val favs = favoris.mapNotNull { parId[it] }
            if (favs.isNotEmpty()) {
                Etiquette("★ FAVORIS")
                Spacer(Modifier.height(10.dp))
                Grille(app, favs, actuel, onChoix)
                Spacer(Modifier.height(24.dp))
            }
            entrees.groupBy { it.categorie }.forEach { (rayon, liste) ->
                Etiquette(rayon.uppercase())
                Spacer(Modifier.height(10.dp))
                Grille(app, liste, actuel, onChoix)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Grille(app: VersoApp, entrees: List<EntreeCatalogue>, actuel: String?, onChoix: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entrees.chunked(4).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { e ->
                    val modele = remember(e) { app.catalogue.resoudre(e) }
                    val forme = RoundedCornerShape(18.dp)
                    // Cartes simples (pas de verre) : 90 verres à la fois ralentiraient l'écran.
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .appui({ onChoix(e.id) })
                                .clip(forme)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(if (e.id == actuel) 2.dp else 0.5.dp, if (e.id == actuel) Rouge else Color.White.copy(alpha = 0.12f), forme)
                                .padding(6.dp),
                        ) {
                            DotMatrix(if (modele.dynamique != null) modele.frameDynamique() else modele.apercu, Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(e.nom, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
