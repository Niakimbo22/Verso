package fr.niakimbo.verso.createur

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.ui.DotMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Un modèle qu'un bloc Dessin ou Effet peut reprendre (choisi dans une liste avec aperçu). */
private class Source(val id: String, val nom: String, val modele: Modele)

private class DepartBlocs(
    val nom: String,
    val rayon: String,
    val blocs: List<Bloc>,
    val rayons: List<String>,
    val ressources: MoteurBlocs.Ressources,
    val sources: List<Source>,
)

/** Exemples pour démarrer sans partir de rien. */
private val EXEMPLES: List<Pair<String, List<Bloc>>> = listOf(
    "Cadran + secondes" to listOf(
        Bloc(TypeBloc.SECONDES, 0, 0, 13, 13, niveau = 4),
        Bloc(TypeBloc.HEURE, 1, 1, 11, 11, options = mapOf("style" to "aiguilles", "cadran" to "non")),
    ),
    "Heure + batterie" to listOf(
        Bloc(TypeBloc.BATTERIE, 0, 0, 13, 13, niveau = 3),
        Bloc(TypeBloc.HEURE, 3, 1, 7, 11),
    ),
    "Jour + date" to listOf(
        Bloc(TypeBloc.DATE, 3, 1, 7, 5, options = mapOf("format" to "semaine")),
        Bloc(TypeBloc.DATE, 3, 7, 7, 5, niveau = 6),
    ),
    "Texte défilant" to listOf(
        Bloc(TypeBloc.TEXTE, 0, 4, 13, 5, options = mapOf("texte" to "BONJOUR NICO")),
    ),
)

/**
 * Créateur dynamique par blocs (étape 7) : des blocs posés sur la matrice (heure, date, batterie,
 * secondes, texte, dessin, effet, compte à rebours), chacun avec sa place, sa taille et sa luminosité.
 * L'aperçu est calculé en direct, ici et sur la vraie matrice.
 */
@Composable
fun EditeurBlocs(app: VersoApp, id: String?, onFermer: () -> Unit) {
    val depart by produceState<DepartBlocs?>(null) {
        val contenu = app.catalogue.contenu()
        val rayons = contenu.entrees.filter { !it.cache }.map { it.categorie }.distinct()
        val sources = withContext(Dispatchers.Default) {
            contenu.entrees
                .filter { !it.cache && it.perso !is MesModelesStore.Perso.Blocs }
                .map { Source(it.id, it.nom, app.catalogue.resoudre(it)) }
        }
        val ressources = app.catalogue.ressourcesEditeur()
        val existant = id?.let { app.mesModeles.parId(it) } as? MesModelesStore.Perso.Blocs
        value = DepartBlocs(
            nom = existant?.nom ?: "",
            rayon = existant?.categorie ?: MesModelesStore.RAYON,
            blocs = existant?.def?.blocs ?: emptyList(),
            rayons = rayons,
            ressources = ressources,
            sources = sources,
        )
    }
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        val d = depart
        if (d == null) {
            Text("Chargement…", color = C.Discret, modifier = Modifier.align(Alignment.Center))
        } else {
            Editeur(app, id, d, onFermer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editeur(app: VersoApp, idExistant: String?, depart: DepartBlocs, onFermer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var blocs by remember { mutableStateOf(depart.blocs) }
    var choisi by remember { mutableIntStateOf(if (depart.blocs.isEmpty()) -1 else depart.blocs.size - 1) }
    var nom by remember { mutableStateOf(depart.nom) }
    var rayon by remember { mutableStateOf(depart.rayon) }
    var modifie by remember { mutableStateOf(false) }
    var surMatrice by remember { mutableStateOf(true) }
    var ajout by remember { mutableStateOf(false) }
    var choixModele by remember { mutableStateOf<OptionBloc.Modele?>(null) }
    var quitter by remember { mutableStateOf(false) }
    var enregistrement by remember { mutableStateOf(false) }

    fun changerBlocs(l: List<Bloc>) {
        blocs = l
        modifie = true
    }

    fun changerChoisi(f: (Bloc) -> Bloc) {
        val i = choisi
        if (i !in blocs.indices) return
        changerBlocs(blocs.toMutableList().also { it[i] = f(it[i]).borne() })
    }

    // Rendu en direct (cadence des blocs, au moins une fois par seconde dans l'éditeur).
    var frame by remember { mutableStateOf(MatrixFrame.blank()) }
    LaunchedEffect(blocs, surMatrice) {
        val periode = (MoteurBlocs.periode(blocs, depart.ressources) ?: 1000L).coerceAtMost(1000L)
        while (true) {
            frame = MoteurBlocs.rendre(blocs, MoteurBlocs.instant(), depart.ressources)
            if (surMatrice) app.player.afficherImage(frame)
            delay(periode - System.currentTimeMillis() % periode)
        }
    }

    fun enregistrer() {
        if (enregistrement) return
        enregistrement = true
        scope.launch {
            val def = DefinitionBlocs(
                id = idExistant ?: app.mesModeles.nouvelId(),
                nom = nom.trim().ifBlank { "Mon modèle dynamique" },
                categorie = rayon.trim().ifBlank { MesModelesStore.RAYON },
                blocs = blocs,
            )
            val apercu = MoteurBlocs.rendre(def.blocs, MoteurBlocs.instant(LocalTime.of(10, 10)), depart.ressources)
            app.mesModeles.enregistrer(MesModelesStore.Perso.Blocs(def), apercu)
            Toast.makeText(context, "« ${def.nom} » enregistré dans Mes modèles", Toast.LENGTH_SHORT).show()
            onFermer()
        }
    }

    BackHandler { if (modifie) quitter = true else onFermer() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BarreHaut(
            titre = if (idExistant == null) "Nouveau dynamique" else "Modifier",
            action = if (enregistrement) "…" else "Enregistrer",
            actionActive = true,
            onRetour = { if (modifie) quitter = true else onFermer() },
            onAction = ::enregistrer,
        )
        Spacer(Modifier.height(12.dp))

        // ----- Aperçu + contours des blocs ; glisser = déplacer le bloc choisi, tap = choisir -----
        val blocsActuels by rememberUpdatedState(blocs)
        val choisiActuel by rememberUpdatedState(choisi)
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .aspectRatio(1f)
                .background(Color.Black, CircleShape)
                .border(1.dp, C.Bord, CircleShape)
                .padding(6.dp),
        ) {
            DotMatrix(frame, Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val cote = size.width / MatrixFrame.SIDE.toFloat()
                        fun caseDe(o: Offset) =
                            (o.x / cote).toInt().coerceIn(0, MatrixFrame.SIDE - 1) to (o.y / cote).toInt().coerceIn(0, MatrixFrame.SIDE - 1)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val debut = caseDe(down.position)
                            val l = blocsActuels
                            // Bloc touché : le choisi s'il est sous le doigt, sinon le plus haut qui contient la case.
                            val dessous = l.indices.filter { i ->
                                val b = l[i]
                                debut.first in b.x until b.x + b.l && debut.second in b.y until b.y + b.h
                            }
                            val cible = if (choisiActuel in dessous) choisiActuel else dessous.lastOrNull() ?: return@awaitEachGesture
                            down.consume()
                            choisi = cible
                            val origine = l[cible]
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                val c = caseDe(ch.position)
                                val deplace = origine.copy(x = origine.x + c.first - debut.first, y = origine.y + c.second - debut.second).borne()
                                if (deplace != blocsActuels.getOrNull(cible)) {
                                    changerBlocs(blocsActuels.toMutableList().also { it[cible] = deplace })
                                }
                            }
                        }
                    },
            ) {
                val cote = size.minDimension / MatrixFrame.SIDE
                blocs.forEachIndexed { i, b ->
                    val actif = i == choisi
                    drawRect(
                        color = if (actif) C.Rouge else Color.White.copy(alpha = 0.18f),
                        topLeft = Offset(b.x * cote + 1f, b.y * cote + 1f),
                        size = Size(b.l * cote - 2f, b.h * cote - 2f),
                        style = Stroke(width = if (actif) 3f else 1.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RangeeTouches {
            Touche("Sur la matrice", actif = surMatrice) { surMatrice = !surMatrice }
            Touche("＋ Ajouter un bloc", actif = ajout) { ajout = !ajout }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Touche un bloc pour le choisir, fais-le glisser pour le déplacer.",
            color = C.Discret, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
        )

        if (ajout || blocs.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            TitreSection("AJOUTER UN BLOC")
            Spacer(Modifier.height(6.dp))
            TypeBloc.entries.chunked(2).forEach { ligne ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ligne.forEach { t ->
                        CarteType(t, Modifier.weight(1f)) {
                            changerBlocs(blocs + Bloc.nouveau(t))
                            choisi = blocs.size - 1
                            ajout = false
                        }
                    }
                    if (ligne.size < 2) Spacer(Modifier.weight(1f))
                }
            }
            if (blocs.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                TitreSection("OU PARTIR D'UN EXEMPLE")
                Spacer(Modifier.height(6.dp))
                RangeeTouches {
                    EXEMPLES.forEach { (titre, l) ->
                        Touche(titre, petit = true) {
                            changerBlocs(l)
                            choisi = l.size - 1
                            ajout = false
                            if (nom.isBlank()) nom = titre
                        }
                    }
                }
            }
        }

        // ----- Liste des blocs (ordre = superposition) -----
        if (blocs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TitreSection("BLOCS (le dernier passe devant)")
            Spacer(Modifier.height(6.dp))
            blocs.forEachIndexed { i, b ->
                LigneBloc(
                    b, choisi = i == choisi,
                    onTap = { choisi = i },
                    onMonter = if (i > 0) ({
                        changerBlocs(blocs.toMutableList().apply { add(i - 1, removeAt(i)) })
                        choisi = i - 1
                    }) else null,
                    onDescendre = if (i < blocs.size - 1) ({
                        changerBlocs(blocs.toMutableList().apply { add(i + 1, removeAt(i)) })
                        choisi = i + 1
                    }) else null,
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        // ----- Réglages du bloc choisi -----
        blocs.getOrNull(choisi)?.let { b ->
            Spacer(Modifier.height(12.dp))
            TitreSection("${b.type.nom.uppercase()} · RÉGLAGES")
            Spacer(Modifier.height(4.dp))
            Text(b.type.description, color = C.Discret, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Pas("X", b.x, 0, MatrixFrame.SIDE - b.l) { v -> changerChoisi { it.copy(x = v) } }
                Pas("Y", b.y, 0, MatrixFrame.SIDE - b.h) { v -> changerChoisi { it.copy(y = v) } }
                Pas("Largeur", b.l, 1, MatrixFrame.SIDE) { v -> changerChoisi { it.copy(l = v, x = minOf(it.x, MatrixFrame.SIDE - v)) } }
                Pas("Hauteur", b.h, 1, MatrixFrame.SIDE) { v -> changerChoisi { it.copy(h = v, y = minOf(it.y, MatrixFrame.SIDE - v)) } }
            }
            Spacer(Modifier.height(8.dp))
            RangeeTouches {
                Touche("Plein écran", petit = true) { changerChoisi { it.copy(x = 0, y = 0, l = MatrixFrame.SIDE, h = MatrixFrame.SIDE) } }
                Touche("Centrer", petit = true) {
                    changerChoisi { it.copy(x = (MatrixFrame.SIDE - it.l) / 2, y = (MatrixFrame.SIDE - it.h) / 2) }
                }
                Touche("Taille conseillée", petit = true) {
                    val n = Bloc.nouveau(b.type)
                    changerChoisi { it.copy(x = n.x, y = n.y, l = n.l, h = n.h) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Luminosité", color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..9).forEach { n -> Niveau(n, actif = b.niveau == n, modifier = Modifier.weight(1f)) { changerChoisi { it.copy(niveau = n) } } }
            }
            b.type.optionsUtiles(b).forEach { o ->
                Spacer(Modifier.height(12.dp))
                ControleOption(o, b, depart.sources, onChange = { v -> changerChoisi { it.avec(o, v) } }, onChoisirModele = { choixModele = o as? OptionBloc.Modele })
            }
            Spacer(Modifier.height(12.dp))
            RangeeTouches {
                Touche("⧉ Dupliquer le bloc", petit = true) {
                    changerBlocs(blocs + b)
                    choisi = blocs.size - 1
                }
                Touche("🗑 Supprimer le bloc", petit = true) {
                    changerBlocs(blocs.toMutableList().apply { removeAt(choisi) })
                    choisi = blocs.size - 1
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        TitreSection("NOM")
        Spacer(Modifier.height(6.dp))
        NomEtRayon(
            nom = nom, onNom = { nom = it; modifie = true },
            rayon = rayon, onRayon = { rayon = it; modifie = true },
            rayons = depart.rayons,
        )
        Spacer(Modifier.height(18.dp))
        Touche(if (enregistrement) "…" else "Enregistrer", actif = true, modifier = Modifier.fillMaxWidth(), dispo = blocs.isNotEmpty(), onTap = ::enregistrer)
        Spacer(Modifier.height(24.dp))
    }

    choixModele?.let { o ->
        ModalBottomSheet(
            onDismissRequest = { choixModele = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0A0A0A),
            contentColor = Color.White,
        ) {
            ChoixSource(
                sources = depart.sources.filter { !o.animes || it.modele.estAnime },
                actuel = blocs.getOrNull(choisi)?.option(o),
                animer = o.animes,
            ) { id ->
                changerChoisi { it.avec(o, id) }
                choixModele = null
            }
        }
    }

    if (quitter) {
        Confirmer(
            titre = "Quitter sans enregistrer ?",
            texte = "Les modifications de ce modèle seront perdues.",
            oui = "Quitter",
            onOui = onFermer,
            onNon = { quitter = false },
        )
    }
}

@Composable
private fun CarteType(t: TypeBloc, modifier: Modifier, onTap: () -> Unit) {
    val forme = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(forme)
            .background(C.Panneau)
            .border(1.dp, C.Bord, forme)
            .clickable(onClick = onTap)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(t.symbole, color = C.Rouge, fontSize = 16.sp, modifier = Modifier.width(26.dp))
        Text(t.nom, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LigneBloc(b: Bloc, choisi: Boolean, onTap: () -> Unit, onMonter: (() -> Unit)?, onDescendre: (() -> Unit)?) {
    val forme = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forme)
            .background(C.Panneau)
            .border(if (choisi) 2.dp else 1.dp, if (choisi) C.Rouge else C.Bord, forme)
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(b.type.symbole, color = C.Rouge, fontSize = 15.sp, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(b.type.nom, color = Color.White, fontSize = 13.sp)
            Text(
                resume(b), color = C.Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Touche("▲", petit = true, dispo = onMonter != null) { onMonter?.invoke() }
        Spacer(Modifier.width(4.dp))
        Touche("▼", petit = true, dispo = onDescendre != null) { onDescendre?.invoke() }
    }
}

private fun resume(b: Bloc): String {
    val opts = b.type.optionsUtiles(b).joinToString(" · ") { o ->
        when (o) {
            is OptionBloc.Choix -> o.libelle(b.option(o))
            else -> b.option(o)
        }
    }
    return "${b.x},${b.y} ${b.l}×${b.h} · lum ${b.niveau}" + if (opts.isNotEmpty()) " · $opts" else ""
}

@Composable
private fun ControleOption(
    o: OptionBloc,
    b: Bloc,
    sources: List<Source>,
    onChange: (String) -> Unit,
    onChoisirModele: () -> Unit,
) {
    val forme = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Panneau, forme)
            .border(1.dp, C.Bord, forme)
            .padding(12.dp),
    ) {
        Text(o.titre, color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        when (o) {
            is OptionBloc.Choix -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                o.choix.forEach { (valeur, libelle) ->
                    Touche(libelle, actif = b.option(o) == valeur, petit = true, modifier = Modifier.weight(1f)) { onChange(valeur) }
                }
            }
            is OptionBloc.Texte -> {
                var texte by remember(b.type, o.cle, b.option(o)) { mutableStateOf(b.option(o)) }
                val valide = o.accepte(texte)
                OutlinedTextField(
                    value = texte,
                    onValueChange = { v ->
                        texte = v.take(o.longueurMax)
                        if (o.accepte(texte) && texte.isNotEmpty()) onChange(texte)
                    },
                    singleLine = true,
                    isError = !valide,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Rouge, unfocusedBorderColor = C.Bord,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Rouge,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is OptionBloc.Modele -> {
                val actuel = sources.firstOrNull { it.id == b.option(o) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, C.Bord, RoundedCornerShape(12.dp))
                        .clickable(onClick = onChoisirModele)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotMatrix(actuel?.modele?.apercu, Modifier.size(40.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(actuel?.nom ?: "Modèle introuvable", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Changer ›", color = C.Rouge, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(o.description, color = C.Discret, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

/** Liste des modèles à reprendre dans un bloc Dessin ou Effet. */
@Composable
private fun ChoixSource(sources: List<Source>, actuel: String?, animer: Boolean, onChoisir: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
    ) {
        Text(if (animer) "Choisir une animation" else "Choisir un dessin", fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(10.dp))
        sources.chunked(4).forEach { ligne ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ligne.forEach { s ->
                    val forme = RoundedCornerShape(12.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(forme)
                            .background(C.Panneau)
                            .border(if (s.id == actuel) 2.dp else 1.dp, if (s.id == actuel) C.Rouge else C.Bord, forme)
                            .clickable { onChoisir(s.id) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DotMatrix(s.modele.apercu, Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(3.dp))
                        Text(s.nom, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(4 - ligne.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
