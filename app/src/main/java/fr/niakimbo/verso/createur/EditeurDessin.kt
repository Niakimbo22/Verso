package fr.niakimbo.verso.createur

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.ui.DotMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Une image en cours d'édition : 169 luminosités 0-255 et sa durée. */
private class ImageEd(val px: IntArray, val duree: Long)

/** Tout le dessin à un instant (sert aussi à « Annuler »). */
private data class Doc(val images: List<ImageEd>, val index: Int) {
    val courante get() = images[index]
    fun avecImage(i: Int, img: ImageEd) = copy(images = images.toMutableList().also { it[i] = img })
    fun transformer(f: (IntArray) -> IntArray) = avecImage(index, ImageEd(f(courante.px), courante.duree))
}

private enum class Trace(val symbole: String, val nom: String) {
    CRAYON("✎", "Crayon"), GOMME("⌫", "Gomme"), REMPLIR("◉", "Remplir"),
    LIGNE("╱", "Ligne"), CERCLE("○", "Cercle"), DISQUE("●", "Disque"),
}

/** 5 luminosités suffisent à l'œil (niveaux 1, 3, 5, 7, 9 du format « dessin »). */
private val LUMINOSITES = listOf(1, 3, 5, 7, 9)

/** Vitesse de l'animation : une durée pour toutes les images. */
private val VITESSES = listOf("Très lente" to 500L, "Lente" to 300L, "Normale" to 150L, "Rapide" to 80L, "Très rapide" to 40L)

private const val HISTORIQUE_MAX = 60

private class Depart(val doc: Doc, val nom: String, val rayon: String)

/**
 * Éditeur dessin (étape 7, refait le 25/10 en version simple) : un seul écran.
 * Grande matrice où l'on dessine au doigt, crayon / gomme / remplir, 5 luminosités,
 * bande d'images pour animer, ▶ et ↶. Le reste (formes, symétrie, tourner, vitesse…) est dans « ⋯ ».
 * Ce qui est dessiné s'affiche aussi sur la vraie matrice tant que l'éditeur est à l'écran.
 */
@Composable
fun EditeurDessin(app: VersoApp, id: String?, copie: String?, onFermer: () -> Unit) {
    val depart by produceState<Depart?>(null) {
        val existant = id?.let { app.mesModeles.parId(it) } as? MesModelesStore.Perso.Dessin
        value = when {
            existant != null -> Depart(
                Doc(existant.modele.images.map { ImageEd(it.frame.pixels.copyOf(), it.dureeMs) }, 0),
                existant.nom, existant.categorie,
            )
            copie != null -> {
                val entree = app.catalogue.contenu().parId(copie)
                val m = entree?.let(app.catalogue::resoudre)
                val images = when {
                    m == null -> listOf(ImageEd(OutilsDessin.vide(), Modele.DUREE_PAR_DEFAUT_MS))
                    m.dynamique != null -> listOf(ImageEd(m.frameDynamique().pixels.copyOf(), Modele.DUREE_PAR_DEFAUT_MS))
                    else -> m.images.map { ImageEd(it.frame.pixels.copyOf(), it.dureeMs) }
                }
                Depart(Doc(images, 0), "${entree?.nom ?: "Dessin"} (copie)", MesModelesStore.RAYON)
            }
            else -> Depart(Doc(listOf(ImageEd(OutilsDessin.vide(), Modele.DUREE_PAR_DEFAUT_MS)), 0), "", MesModelesStore.RAYON)
        }
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
private fun Editeur(app: VersoApp, idExistant: String?, depart: Depart, onFermer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var doc by remember { mutableStateOf(depart.doc) }
    val passe = remember { ArrayDeque<Doc>() }
    val futur = remember { ArrayDeque<Doc>() }
    var nbHistorique by remember { mutableIntStateOf(0) } // relit ↶ ↷ après chaque changement
    var modifie by remember { mutableStateOf(false) }

    var nom by remember { mutableStateOf(depart.nom) }
    var trace by remember { mutableStateOf(Trace.CRAYON) }
    var niveau by remember { mutableIntStateOf(9) }
    var symH by remember { mutableStateOf(false) }
    var symV by remember { mutableStateOf(false) }
    var pelure by remember { mutableStateOf(true) }
    var surMatrice by remember { mutableStateOf(true) }
    var lecture by remember { mutableStateOf(false) }
    var indexLecture by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var renommer by remember { mutableStateOf(false) }
    var enregistrerApresNom by remember { mutableStateOf(false) }
    var quitter by remember { mutableStateOf(false) }
    var enregistrement by remember { mutableStateOf(false) }

    fun memoriser() {
        passe.addLast(doc)
        if (passe.size > HISTORIQUE_MAX) passe.removeFirst()
        futur.clear()
        nbHistorique++
        modifie = true
    }

    fun appliquer(f: (Doc) -> Doc) {
        memoriser()
        doc = f(doc)
    }

    fun annuler() {
        val d = passe.removeLastOrNull() ?: return
        futur.addLast(doc)
        doc = d
        nbHistorique++
    }

    fun retablir() {
        val d = futur.removeLastOrNull() ?: return
        passe.addLast(doc)
        doc = d
        nbHistorique++
    }

    fun enregistrer() {
        if (enregistrement) return
        if (nom.isBlank()) {
            enregistrerApresNom = true
            renommer = true
            return
        }
        enregistrement = true
        scope.launch {
            val id = idExistant ?: app.mesModeles.nouvelId()
            val images = doc.images.map { Modele.Image(MatrixFrame(it.px), it.duree.coerceAtLeast(Modele.MIN_DUREE_MS)) }
            val modele = Modele(
                id = id,
                nom = nom.trim(),
                type = if (images.size > 1) Modele.Type.ANIME else Modele.Type.STATIQUE,
                images = images,
                categorie = depart.rayon,
            )
            app.mesModeles.enregistrer(MesModelesStore.Perso.Dessin(modele))
            Toast.makeText(context, "« ${modele.nom} » enregistré dans Mes modèles", Toast.LENGTH_SHORT).show()
            onFermer()
        }
    }

    BackHandler { if (modifie) quitter = true else onFermer() }

    // Aperçu sur la vraie matrice, seulement tant que l'éditeur est à l'écran :
    // en arrière-plan, la matrice revient au modèle actif (CreateurActivity.onPause).
    val etat by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val aLEcran = etat.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(doc, surMatrice, lecture, aLEcran) {
        if (surMatrice && aLEcran && !lecture) app.player.afficherImage(MatrixFrame(doc.courante.px))
    }
    LaunchedEffect(lecture, aLEcran) {
        if (!lecture) return@LaunchedEffect
        while (true) {
            val images = doc.images
            val img = images[indexLecture % images.size]
            if (surMatrice && aLEcran) app.player.afficherImage(MatrixFrame(img.px))
            delay(img.duree.coerceAtLeast(Modele.MIN_DUREE_MS))
            indexLecture = (indexLecture + 1) % images.size
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ----- Barre du haut : ←, nom (tap = renommer), Enregistrer -----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "←", color = Color.White, fontSize = 24.sp,
                modifier = Modifier.clip(CircleShape).clickable { if (modifie) quitter = true else onFermer() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                (nom.ifBlank { "Sans nom" }) + "  ✎",
                color = if (nom.isBlank()) C.Discret else Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { renommer = true }.padding(6.dp),
            )
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(C.Rouge).clickable(onClick = ::enregistrer)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) { Text(if (enregistrement) "…" else "✓ Enregistrer", color = Color.White, fontSize = 14.sp) }
        }
        Spacer(Modifier.height(10.dp))

        // ----- La matrice où l'on dessine -----
        val affichee = if (lecture) doc.images[indexLecture % doc.images.size] else doc.courante
        val precedente = if (pelure && !lecture && doc.index > 0) doc.images[doc.index - 1] else null
        val traceActuel by rememberUpdatedState(trace)
        val niveauActuel by rememberUpdatedState(niveau)
        val symActuelle by rememberUpdatedState(OutilsDessin.Symetrie(symH, symV))
        val enLecture by rememberUpdatedState(lecture)

        Toile(
            px = affichee.px,
            pelure = precedente?.px,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    val cote = size.width / OutilsDessin.N.toFloat()
                    fun caseDe(o: Offset) =
                        (o.x / cote).toInt().coerceIn(0, OutilsDessin.N - 1) to (o.y / cote).toInt().coerceIn(0, OutilsDessin.N - 1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (enLecture) lecture = false
                        down.consume()
                        val t = traceActuel
                        val sym = symActuelle
                        val valeur = if (t == Trace.GOMME) 0 else OutilsDessin.luminosite(niveauActuel)
                        val debut = caseDe(down.position)
                        var dernier = debut
                        // `doc` est un état : on relit toujours sa dernière valeur, même entre deux images d'écran.
                        val base = doc.courante.px
                        memoriser()
                        fun dessiner(f: (IntArray) -> IntArray) { doc = doc.transformer(f) }
                        when (t) {
                            Trace.REMPLIR -> {
                                dessiner { OutilsDessin.remplir(it, debut.first, debut.second, valeur, sym) }
                                return@awaitEachGesture
                            }
                            Trace.CRAYON, Trace.GOMME -> dessiner { OutilsDessin.poser(it, listOf(debut), valeur, sym) }
                            else -> dessiner { OutilsDessin.poser(base, listOf(debut), valeur, sym) }
                        }
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            val c = caseDe(ch.position)
                            if (c == dernier) continue
                            when (t) {
                                Trace.CRAYON, Trace.GOMME -> {
                                    val segment = OutilsDessin.ligne(dernier.first, dernier.second, c.first, c.second)
                                    dessiner { OutilsDessin.poser(it, segment, valeur, sym) }
                                }
                                Trace.LIGNE -> dessiner {
                                    OutilsDessin.poser(base, OutilsDessin.ligne(debut.first, debut.second, c.first, c.second), valeur, sym)
                                }
                                Trace.CERCLE, Trace.DISQUE -> dessiner {
                                    OutilsDessin.poser(base, OutilsDessin.cercle(debut.first, debut.second, c.first, c.second, t == Trace.DISQUE), valeur, sym)
                                }
                                Trace.REMPLIR -> Unit
                            }
                            dernier = c
                        }
                    }
                },
        )
        Spacer(Modifier.height(6.dp))
        val indice = when {
            lecture -> "Lecture… touche la matrice pour arrêter"
            trace == Trace.LIGNE || trace == Trace.CERCLE || trace == Trace.DISQUE -> "${trace.nom} : pose le doigt, glisse, lâche"
            symH || symV -> "Symétrie activée"
            OutilsDessin.plusClair(doc.courante.px) == 0 -> "Dessine au doigt sur la matrice"
            else -> ""
        }
        Text(indice, color = C.Discret, fontSize = 12.sp, modifier = Modifier.height(18.dp))
        Spacer(Modifier.height(8.dp))

        // ----- Outils principaux + luminosité -----
        val formeChoisie = trace == Trace.LIGNE || trace == Trace.CERCLE || trace == Trace.DISQUE
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Outil(Trace.CRAYON.symbole, "Crayon", actif = trace == Trace.CRAYON, modifier = Modifier.weight(1f)) { trace = Trace.CRAYON }
            Outil(Trace.GOMME.symbole, "Gomme", actif = trace == Trace.GOMME, modifier = Modifier.weight(1f)) { trace = Trace.GOMME }
            Outil(Trace.REMPLIR.symbole, "Remplir", actif = trace == Trace.REMPLIR, modifier = Modifier.weight(1f)) { trace = Trace.REMPLIR }
            if (formeChoisie) {
                Outil(trace.symbole, trace.nom, actif = true, modifier = Modifier.weight(1f)) { menu = true }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panneau).border(1.dp, C.Bord, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Luminosité", color = C.Discret, fontSize = 12.sp, modifier = Modifier.weight(1f))
            LUMINOSITES.forEach { n ->
                val choisi = niveau == n && trace != Trace.GOMME
                Box(
                    Modifier.padding(horizontal = 5.dp).size(34.dp).clip(CircleShape)
                        .border(2.dp, if (choisi) C.Rouge else Color.Transparent, CircleShape)
                        .clickable {
                            niveau = n
                            if (trace == Trace.GOMME) trace = Trace.CRAYON
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f + 0.75f * n / 9f)))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ----- Images de l'animation -----
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            doc.images.forEachIndexed { i, img ->
                Vignette(img.px, choisie = i == doc.index && !lecture, jouee = lecture && i == indexLecture % doc.images.size) {
                    lecture = false
                    doc = doc.copy(index = i)
                }
            }
            // ＋ : nouvelle image, copie de l'actuelle (on n'a plus qu'à changer ce qui bouge).
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, C.Bord, RoundedCornerShape(10.dp))
                    .clickable {
                        lecture = false
                        appliquer { d ->
                            Doc(d.images.toMutableList().apply { add(d.index + 1, ImageEd(d.courante.px.copyOf(), d.courante.duree)) }, d.index + 1)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("＋", color = Color.White, fontSize = 22.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (doc.images.size == 1) "＋ ajoute une image pour animer ton dessin"
            else "Image ${doc.index + 1} sur ${doc.images.size} · ＋ copie l'image affichée",
            color = C.Discret, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))

        // ----- Lire, annuler, rétablir, plus -----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Outil(if (lecture) "❚❚" else "▶", if (lecture) "Pause" else "Lire", actif = lecture, dispo = doc.images.size > 1, modifier = Modifier.weight(1f)) {
                if (!lecture) indexLecture = doc.index
                lecture = !lecture
            }
            Outil("↶", "Annuler", dispo = passe.isNotEmpty() && nbHistorique >= 0, modifier = Modifier.weight(1f)) { annuler() }
            Outil("↷", "Rétablir", dispo = futur.isNotEmpty() && nbHistorique >= 0, modifier = Modifier.weight(1f)) { retablir() }
            Outil("⋯", "Plus", modifier = Modifier.weight(1f)) { menu = true }
        }
        Spacer(Modifier.height(24.dp))
    }

    // ----- Menu « ⋯ » : tout ce qui sert moins souvent -----
    if (menu) {
        ModalBottomSheet(
            onDismissRequest = { menu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0A0A0A),
            contentColor = Color.White,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            ) {
                TitreSection("FORMES")
                Spacer(Modifier.height(8.dp))
                LignePuces {
                    listOf(Trace.LIGNE, Trace.CERCLE, Trace.DISQUE).forEach { f ->
                        Puce("${f.symbole} ${f.nom}", actif = trace == f) { trace = f; menu = false }
                    }
                }
                Spacer(Modifier.height(18.dp))
                TitreSection("SYMÉTRIE")
                Spacer(Modifier.height(8.dp))
                LignePuces {
                    Puce("↔ Gauche-droite", actif = symH) { symH = !symH }
                    Puce("↕ Haut-bas", actif = symV) { symV = !symV }
                }
                Spacer(Modifier.height(18.dp))
                TitreSection("IMAGE AFFICHÉE")
                Spacer(Modifier.height(8.dp))
                LignePuces {
                    Puce("←") { appliquer { it.transformer { px -> OutilsDessin.decaler(px, -1, 0) } } }
                    Puce("→") { appliquer { it.transformer { px -> OutilsDessin.decaler(px, 1, 0) } } }
                    Puce("↑") { appliquer { it.transformer { px -> OutilsDessin.decaler(px, 0, -1) } } }
                    Puce("↓") { appliquer { it.transformer { px -> OutilsDessin.decaler(px, 0, 1) } } }
                    Puce("⟳ Tourner") { appliquer { it.transformer { px -> OutilsDessin.tourner(px, horaire = true) } } }
                    Puce("⇋ Retourner") { appliquer { it.transformer { px -> OutilsDessin.retourner(px, horizontal = true) } } }
                }
                Spacer(Modifier.height(8.dp))
                LignePuces {
                    Puce("Effacer") { appliquer { it.transformer { OutilsDessin.vide() } } }
                    Puce("Négatif") { appliquer { it.transformer(OutilsDessin::inverser) } }
                    Puce("◀ Avancer", dispo = doc.index > 0) {
                        appliquer { d -> Doc(d.images.toMutableList().apply { add(d.index - 1, removeAt(d.index)) }, d.index - 1) }
                    }
                    Puce("Reculer ▶", dispo = doc.index < doc.images.size - 1) {
                        appliquer { d -> Doc(d.images.toMutableList().apply { add(d.index + 1, removeAt(d.index)) }, d.index + 1) }
                    }
                    Puce("🗑 Supprimer l'image", dispo = doc.images.size > 1) {
                        appliquer { d ->
                            val l = d.images.toMutableList().apply { removeAt(d.index) }
                            Doc(l, d.index.coerceAtMost(l.size - 1))
                        }
                    }
                }
                if (doc.images.size > 1) {
                    Spacer(Modifier.height(18.dp))
                    TitreSection("VITESSE DE L'ANIMATION")
                    Spacer(Modifier.height(8.dp))
                    val actuelle = doc.images.map { it.duree }.distinct().singleOrNull()
                    LignePuces {
                        VITESSES.forEach { (libelle, ms) ->
                            Puce(libelle, actif = actuelle == ms) {
                                appliquer { d -> d.copy(images = d.images.map { ImageEd(it.px, ms) }) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LignePuces {
                        Puce("Pelure d'oignon", actif = pelure) { pelure = !pelure }
                    }
                    Text(
                        "Pelure d'oignon : l'image d'avant apparaît en rouge pâle, pour dessiner la suite du mouvement.",
                        color = C.Discret, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                TitreSection("MATRICE")
                Spacer(Modifier.height(8.dp))
                LignePuces {
                    Puce("Voir le dessin au dos du téléphone", actif = surMatrice) { surMatrice = !surMatrice }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (renommer) {
        var texte by remember { mutableStateOf(nom) }
        AlertDialog(
            onDismissRequest = { renommer = false; enregistrerApresNom = false },
            containerColor = C.Panneau,
            titleContentColor = Color.White,
            title = { Text("Nom du dessin") },
            text = {
                OutlinedTextField(
                    value = texte,
                    onValueChange = { texte = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("ex. Chat qui cligne") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.Rouge, unfocusedBorderColor = C.Bord,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = C.Rouge,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    nom = texte.trim()
                    modifie = true
                    renommer = false
                    if (enregistrerApresNom && nom.isNotBlank()) enregistrer()
                    enregistrerApresNom = false
                }) { Text(if (enregistrerApresNom) "Enregistrer" else "OK", color = C.Rouge) }
            },
            dismissButton = {
                TextButton(onClick = { renommer = false; enregistrerApresNom = false }) { Text("Annuler", color = Color.White) }
            },
        )
    }

    if (quitter) {
        Confirmer(
            titre = "Quitter sans enregistrer ?",
            texte = "Les modifications de ce dessin seront perdues.",
            oui = "Quitter",
            onOui = onFermer,
            onNon = { quitter = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LignePuces(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/**
 * Grande matrice éditable : LEDs éteintes en gris, dessin en blanc, et (pelure d'oignon)
 * l'image précédente en rouge pâle sous les LEDs éteintes.
 */
@Composable
private fun Toile(px: IntArray, pelure: IntArray?, modifier: Modifier) {
    Box(
        modifier
            .background(Color.Black, CircleShape)
            .border(1.dp, C.Bord, CircleShape)
            .padding(6.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cote = size.minDimension / OutilsDessin.N
            val r = cote * 0.42f
            for (y in 0 until OutilsDessin.N) for (x in 0 until OutilsDessin.N) {
                if (!MatrixFrame.isOnMatrix(x, y)) continue
                val v = px[y * OutilsDessin.N + x]
                val p = pelure?.get(y * OutilsDessin.N + x) ?: 0
                val couleur = when {
                    v > 0 -> Color.White.copy(alpha = 0.3f + 0.7f * v / MatrixFrame.MAX)
                    p > 0 -> C.Rouge.copy(alpha = 0.18f + 0.3f * p / MatrixFrame.MAX)
                    else -> C.Eteinte
                }
                drawCircle(couleur, r, Offset(cote * (x + 0.5f), cote * (y + 0.5f)))
            }
        }
    }
}

@Composable
private fun Vignette(px: IntArray, choisie: Boolean, jouee: Boolean, onTap: () -> Unit) {
    val forme = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(58.dp)
            .clip(forme)
            .background(C.Panneau)
            .border(if (choisie || jouee) 2.dp else 1.dp, if (choisie) C.Rouge else if (jouee) Color.White else C.Bord, forme)
            .clickable(onClick = onTap)
            .padding(5.dp),
    ) {
        DotMatrix(MatrixFrame(px), Modifier.fillMaxSize())
    }
}
