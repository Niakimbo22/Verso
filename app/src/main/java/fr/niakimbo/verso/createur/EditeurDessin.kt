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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.ui.DotMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Une image en cours d'édition : 169 luminosités 0-255 et sa durée. */
private class ImageEd(val px: IntArray, val duree: Long)

/** Tout le dessin à un instant (sert aussi aux annulations). */
private data class Doc(val images: List<ImageEd>, val index: Int) {
    val courante get() = images[index]
    fun avecImage(i: Int, img: ImageEd) = copy(images = images.toMutableList().also { it[i] = img })
}

private enum class Outil(val label: String) {
    CRAYON("✎ Crayon"), GOMME("⌫ Gomme"), REMPLIR("◉ Remplir"), LIGNE("╱ Ligne"),
    CERCLE("○ Cercle"), DISQUE("● Disque"), PIPETTE("⌖ Pipette"),
}

private val DUREES = listOf(40L, 60L, 80L, 100L, 150L, 200L, 300L, 500L, 1000L, 2000L)
private const val HISTORIQUE_MAX = 60

/** Ce qui a été chargé pour démarrer l'éditeur. */
private class Depart(val doc: Doc, val nom: String, val rayon: String, val rayons: List<String>)

/**
 * Éditeur dessin complet (étape 7) : crayon, gomme, 9 luminosités, remplissage, ligne, cercle,
 * symétrie, décaler / tourner, plusieurs images avec leur durée, pelure d'oignon, annuler / rétablir,
 * aperçu en direct sur la vraie matrice.
 */
@Composable
fun EditeurDessin(app: VersoApp, id: String?, copie: String?, onFermer: () -> Unit) {
    val depart by produceState<Depart?>(null) {
        val contenu = app.catalogue.contenu()
        val rayons = contenu.entrees.filter { !it.cache }.map { it.categorie }.distinct()
        val existant = id?.let { app.mesModeles.parId(it) } as? MesModelesStore.Perso.Dessin
        value = when {
            existant != null -> Depart(
                Doc(existant.modele.images.map { ImageEd(it.frame.pixels.copyOf(), it.dureeMs) }, 0),
                existant.nom, existant.categorie, rayons,
            )
            copie != null -> {
                val entree = contenu.parId(copie)
                val m = entree?.let(app.catalogue::resoudre)
                val images = when {
                    m == null -> listOf(ImageEd(OutilsDessin.vide(), Modele.DUREE_PAR_DEFAUT_MS))
                    m.dynamique != null -> listOf(ImageEd(m.frameDynamique().pixels.copyOf(), Modele.DUREE_PAR_DEFAUT_MS))
                    else -> m.images.map { ImageEd(it.frame.pixels.copyOf(), it.dureeMs) }
                }
                Depart(Doc(images, 0), "${entree?.nom ?: "Dessin"} (copie)", MesModelesStore.RAYON, rayons)
            }
            else -> Depart(Doc(listOf(ImageEd(OutilsDessin.vide(), Modele.DUREE_PAR_DEFAUT_MS)), 0), "", MesModelesStore.RAYON, rayons)
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

@Composable
private fun Editeur(app: VersoApp, idExistant: String?, depart: Depart, onFermer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var doc by remember { mutableStateOf(depart.doc) }
    val passe = remember { ArrayDeque<Doc>() }
    val futur = remember { ArrayDeque<Doc>() }
    var nbHistorique by remember { mutableIntStateOf(0) } // force la recomposition des boutons ↶ ↷
    var modifie by remember { mutableStateOf(false) }

    var nom by remember { mutableStateOf(depart.nom) }
    var rayon by remember { mutableStateOf(depart.rayon) }
    var outil by remember { mutableStateOf(Outil.CRAYON) }
    var niveau by remember { mutableIntStateOf(9) }
    var symH by remember { mutableStateOf(false) }
    var symV by remember { mutableStateOf(false) }
    var pelure by remember { mutableStateOf(true) }
    var surMatrice by remember { mutableStateOf(true) }
    var lecture by remember { mutableStateOf(false) }
    var indexLecture by remember { mutableIntStateOf(0) }
    var quitter by remember { mutableStateOf(false) }
    var enregistrement by remember { mutableStateOf(false) }

    fun memoriser() {
        passe.addLast(doc)
        if (passe.size > HISTORIQUE_MAX) passe.removeFirst()
        futur.clear()
        nbHistorique++
        modifie = true
    }

    /** Change l'image courante (sans mémoriser : le geste l'a déjà fait). */
    fun dessiner(f: (IntArray) -> IntArray) {
        val img = doc.courante
        doc = doc.avecImage(doc.index, ImageEd(f(img.px), img.duree))
    }

    /** Action ponctuelle (bouton) : mémorisée pour pouvoir l'annuler. */
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
        enregistrement = true
        scope.launch {
            val id = idExistant ?: app.mesModeles.nouvelId()
            val images = doc.images.map { Modele.Image(MatrixFrame(it.px), it.duree.coerceAtLeast(Modele.MIN_DUREE_MS)) }
            val modele = Modele(
                id = id,
                nom = nom.trim().ifBlank { "Mon dessin" },
                type = if (images.size > 1) Modele.Type.ANIME else Modele.Type.STATIQUE,
                images = images,
                categorie = rayon.trim().ifBlank { MesModelesStore.RAYON },
            )
            app.mesModeles.enregistrer(MesModelesStore.Perso.Dessin(modele))
            Toast.makeText(context, "« ${modele.nom} » enregistré dans Mes modèles", Toast.LENGTH_SHORT).show()
            onFermer()
        }
    }

    BackHandler { if (modifie) quitter = true else onFermer() }

    // Aperçu en direct sur la vraie matrice : l'image en cours, ou l'animation pendant la lecture.
    LaunchedEffect(doc, surMatrice, lecture) {
        if (surMatrice && !lecture) app.player.afficherImage(MatrixFrame(doc.courante.px))
    }
    LaunchedEffect(lecture) {
        if (!lecture) return@LaunchedEffect
        indexLecture = 0
        while (true) {
            val images = doc.images
            val img = images[indexLecture % images.size]
            if (surMatrice) app.player.afficherImage(MatrixFrame(img.px))
            delay(img.duree.coerceAtLeast(Modele.MIN_DUREE_MS))
            indexLecture = (indexLecture + 1) % images.size
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BarreHaut(
            titre = if (idExistant == null) "Nouveau dessin" else "Modifier",
            action = if (enregistrement) "…" else "Enregistrer",
            actionActive = true,
            onRetour = { if (modifie) quitter = true else onFermer() },
            onAction = ::enregistrer,
        )
        Spacer(Modifier.height(12.dp))

        // ----- Toile -----
        val affichee = if (lecture) doc.images[indexLecture % doc.images.size] else doc.courante
        val precedente = if (pelure && !lecture && doc.index > 0) doc.images[doc.index - 1] else null
        val outilActuel by rememberUpdatedState(outil)
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
                        if (enLecture) return@awaitEachGesture
                        down.consume()
                        val o = outilActuel
                        val sym = symActuelle
                        val valeur = if (o == Outil.GOMME) 0 else OutilsDessin.luminosite(niveauActuel)
                        val debut = caseDe(down.position)
                        var dernier = debut
                        // Image de départ pour les formes (ligne, cercle) : redessinée à chaque mouvement.
                        val base = doc.courante.px
                        when (o) {
                            Outil.PIPETTE -> {
                                val v = base[debut.second * OutilsDessin.N + debut.first]
                                if (v > 0) niveau = OutilsDessin.niveau(v)
                                outil = Outil.CRAYON
                                return@awaitEachGesture
                            }
                            Outil.REMPLIR -> {
                                memoriser()
                                dessiner { OutilsDessin.remplir(it, debut.first, debut.second, valeur, sym) }
                                return@awaitEachGesture
                            }
                            Outil.CRAYON, Outil.GOMME -> {
                                memoriser()
                                dessiner { OutilsDessin.poser(it, listOf(debut), valeur, sym) }
                            }
                            else -> {
                                memoriser()
                                dessiner { OutilsDessin.poser(base, listOf(debut), valeur, sym) }
                            }
                        }
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            val c = caseDe(ch.position)
                            if (c == dernier) continue
                            when (o) {
                                Outil.CRAYON, Outil.GOMME -> {
                                    val trait = OutilsDessin.ligne(dernier.first, dernier.second, c.first, c.second)
                                    dessiner { OutilsDessin.poser(it, trait, valeur, sym) }
                                }
                                Outil.LIGNE -> dessiner {
                                    OutilsDessin.poser(base, OutilsDessin.ligne(debut.first, debut.second, c.first, c.second), valeur, sym)
                                }
                                Outil.CERCLE, Outil.DISQUE -> dessiner {
                                    OutilsDessin.poser(base, OutilsDessin.cercle(debut.first, debut.second, c.first, c.second, o == Outil.DISQUE), valeur, sym)
                                }
                                else -> Unit
                            }
                            dernier = c
                        }
                    }
                },
        )
        Spacer(Modifier.height(10.dp))

        // ----- Annuler, lecture, options d'affichage -----
        RangeeTouches {
            Touche("↶", dispo = passe.isNotEmpty() && nbHistorique >= 0 && !lecture) { annuler() }
            Touche("↷", dispo = futur.isNotEmpty() && nbHistorique >= 0 && !lecture) { retablir() }
            Touche(if (lecture) "❚❚ Pause" else "▶ Lire", actif = lecture, dispo = doc.images.size > 1 || lecture) { lecture = !lecture }
            Touche("Pelure", actif = pelure) { pelure = !pelure }
            Touche("Sur la matrice", actif = surMatrice) { surMatrice = !surMatrice }
        }
        Spacer(Modifier.height(14.dp))

        // ----- Outils -----
        TitreSection("OUTIL")
        Spacer(Modifier.height(6.dp))
        RangeeTouches {
            Outil.entries.forEach { o -> Touche(o.label, actif = outil == o, petit = true) { outil = o } }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..9).forEach { n ->
                Niveau(n, actif = niveau == n && outil != Outil.GOMME, modifier = Modifier.weight(1f)) {
                    niveau = n
                    if (outil == Outil.GOMME || outil == Outil.PIPETTE) outil = Outil.CRAYON
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RangeeTouches {
            Touche("↔ Symétrie", actif = symH, petit = true) { symH = !symH }
            Touche("↕ Symétrie", actif = symV, petit = true) { symV = !symV }
        }
        Spacer(Modifier.height(14.dp))

        // ----- Transformations de l'image courante -----
        TitreSection("IMAGE ${doc.index + 1} / ${doc.images.size}")
        Spacer(Modifier.height(6.dp))
        fun transformer(f: (IntArray) -> IntArray) = appliquer { d -> d.avecImage(d.index, ImageEd(f(d.courante.px), d.courante.duree)) }
        RangeeTouches {
            Touche("←", petit = true) { transformer { OutilsDessin.decaler(it, -1, 0) } }
            Touche("→", petit = true) { transformer { OutilsDessin.decaler(it, 1, 0) } }
            Touche("↑", petit = true) { transformer { OutilsDessin.decaler(it, 0, -1) } }
            Touche("↓", petit = true) { transformer { OutilsDessin.decaler(it, 0, 1) } }
            Touche("⟲", petit = true) { transformer { OutilsDessin.tourner(it, horaire = false) } }
            Touche("⟳", petit = true) { transformer { OutilsDessin.tourner(it, horaire = true) } }
            Touche("⇋ Retourner", petit = true) { transformer { OutilsDessin.retourner(it, horizontal = true) } }
            Touche("⇵ Retourner", petit = true) { transformer { OutilsDessin.retourner(it, horizontal = false) } }
        }
        Spacer(Modifier.height(6.dp))
        RangeeTouches {
            Touche("− Plus sombre", petit = true) { transformer { OutilsDessin.nuancer(it, -1) } }
            Touche("+ Plus clair", petit = true) { transformer { OutilsDessin.nuancer(it, +1) } }
            Touche("Négatif", petit = true) { transformer(OutilsDessin::inverser) }
            Touche("Effacer", petit = true) { transformer { OutilsDessin.vide() } }
        }
        Spacer(Modifier.height(16.dp))

        // ----- Images de l'animation -----
        TitreSection("IMAGES")
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            doc.images.forEachIndexed { i, img ->
                Vignette(img.px, i + 1, choisie = i == doc.index && !lecture, jouee = lecture && i == indexLecture % doc.images.size) {
                    if (!lecture) doc = doc.copy(index = i)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RangeeTouches {
            Touche("＋ Nouvelle", petit = true, dispo = !lecture) {
                appliquer { d ->
                    val l = d.images.toMutableList().apply { add(d.index + 1, ImageEd(OutilsDessin.vide(), d.courante.duree)) }
                    Doc(l, d.index + 1)
                }
            }
            Touche("⧉ Dupliquer", petit = true, dispo = !lecture) {
                appliquer { d ->
                    val l = d.images.toMutableList().apply { add(d.index + 1, ImageEd(d.courante.px.copyOf(), d.courante.duree)) }
                    Doc(l, d.index + 1)
                }
            }
            Touche("◀", petit = true, dispo = !lecture && doc.index > 0) {
                appliquer { d ->
                    val l = d.images.toMutableList().apply { add(d.index - 1, removeAt(d.index)) }
                    Doc(l, d.index - 1)
                }
            }
            Touche("▶", petit = true, dispo = !lecture && doc.index < doc.images.size - 1) {
                appliquer { d ->
                    val l = d.images.toMutableList().apply { add(d.index + 1, removeAt(d.index)) }
                    Doc(l, d.index + 1)
                }
            }
            Touche("🗑 Supprimer", petit = true, dispo = !lecture && doc.images.size > 1) {
                appliquer { d ->
                    val l = d.images.toMutableList().apply { removeAt(d.index) }
                    Doc(l, d.index.coerceAtMost(l.size - 1))
                }
            }
        }
        if (doc.images.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Durée de l'image ${doc.index + 1} : ${doc.courante.duree} ms",
                color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            RangeeTouches {
                DUREES.forEach { ms ->
                    Touche(if (ms >= 1000) "${ms / 1000} s" else "$ms", actif = doc.courante.duree == ms, petit = true) {
                        appliquer { d -> d.avecImage(d.index, ImageEd(d.courante.px, ms)) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Touche("Mettre ${doc.courante.duree} ms à toutes les images", petit = true) {
                val ms = doc.courante.duree
                appliquer { d -> d.copy(images = d.images.map { ImageEd(it.px, ms) }) }
            }
            Spacer(Modifier.height(4.dp))
            val total = doc.images.sumOf { it.duree }
            Text(
                "${doc.images.size} images · un tour dure ${"%.1f".format(total / 1000f)} s",
                color = C.Discret, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "Une seule image = modèle fixe. Ajoute des images pour l'animer.",
                color = C.Discret, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(18.dp))

        TitreSection("NOM")
        Spacer(Modifier.height(6.dp))
        NomEtRayon(
            nom = nom, onNom = { nom = it; modifie = true },
            rayon = rayon, onRayon = { rayon = it; modifie = true },
            rayons = depart.rayons,
        )
        Spacer(Modifier.height(18.dp))
        Touche(if (enregistrement) "…" else "Enregistrer", actif = true, modifier = Modifier.fillMaxWidth(), onTap = ::enregistrer)
        Spacer(Modifier.height(24.dp))
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

/**
 * Grande matrice éditable : LEDs éteintes en gris, image en blanc, et (pelure d'oignon)
 * l'image précédente en rouge très léger sous les LEDs éteintes.
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
private fun Vignette(px: IntArray, numero: Int, choisie: Boolean, jouee: Boolean, onTap: () -> Unit) {
    val forme = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .clip(forme)
            .background(C.Panneau)
            .border(if (choisie || jouee) 2.dp else 1.dp, if (choisie) C.Rouge else if (jouee) Color.White else C.Bord, forme)
            .clickable(onClick = onTap)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DotMatrix(MatrixFrame(px), Modifier.size(48.dp))
        Text(numero.toString(), color = C.Discret, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
