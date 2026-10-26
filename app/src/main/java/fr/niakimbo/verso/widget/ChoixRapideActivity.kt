package fr.niakimbo.verso.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.MainActivity
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.launch
import fr.niakimbo.verso.modele.Modele
import fr.niakimbo.verso.ui.DotMatrix
import fr.niakimbo.verso.ui.apercuAnime
import fr.niakimbo.verso.ui.suivreVisibilite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Rouge = Color(0xFFD71921)
private val Discret = Color(0xFF8A8A8A)
private val Panneau = Color(0xFF111111)
private val Bord = Color(0xFF2A2A2A)
private const val COLONNES = 4

/**
 * Fenêtre du bouton ☰ du widget : petite grille par-dessus l'écran d'accueil.
 * Tap = le modèle s'affiche et la fenêtre se ferme ; tap à côté = fermer.
 */
class ChoixRapideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VersoApp
        app.matrix.connect()
        setContent {
            Fenetre(
                app,
                onChoisir = { modele ->
                    app.player.jouer(modele)
                    finish()
                },
                onEteindre = {
                    app.appScope.launch { fr.niakimbo.verso.rapide.ActionsRapides.eteindre(app) }
                    finish()
                },
                onOuvrirVerso = {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                },
                onFermer = ::finish,
            )
        }
    }
}

private class Choix(val id: String, val modele: Modele, val perso: Boolean)

private enum class Onglet { FAVORIS, MES, TOUS }

@Composable
private fun Fenetre(
    app: VersoApp,
    onChoisir: (Modele) -> Unit,
    onEteindre: () -> Unit,
    onOuvrirVerso: () -> Unit,
    onFermer: () -> Unit,
) {
    val favorisIds by app.favoris.ids.collectAsStateWithLifecycle()
    val tous by produceState<List<Choix>?>(null) {
        value = withContext(Dispatchers.Default) {
            app.catalogue.contenu().entrees.filter { !it.cache }.map { Choix(it.id, app.catalogue.resoudre(it), it.perso != null) }
        }
    }
    var onglet by remember { mutableStateOf(if (favorisIds.isNotEmpty()) Onglet.FAVORIS else Onglet.TOUS) }
    val aDesCreations = tous?.any { it.perso } == true
    val actif = app.store.modeleActif

    // Voile : tap à côté de la carte = fermer.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFermer)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val forme = RoundedCornerShape(24.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(forme)
                .background(Color.Black)
                .border(1.dp, Bord, forme)
                // Absorbe les taps pour qu'ils ne ferment pas la fenêtre.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Onglet("★ Favoris", onglet == Onglet.FAVORIS, Modifier.weight(1f)) { onglet = Onglet.FAVORIS }
                if (aDesCreations) Onglet("✎ Mes", onglet == Onglet.MES, Modifier.weight(1f)) { onglet = Onglet.MES }
                Onglet("Tous", onglet == Onglet.TOUS, Modifier.weight(1f)) { onglet = Onglet.TOUS }
            }
            Spacer(Modifier.height(14.dp))

            val liste = tous?.let { l ->
                when (onglet) {
                    Onglet.FAVORIS -> favorisIds.mapNotNull { id -> l.firstOrNull { it.id == id } }
                    Onglet.MES -> l.filter { it.perso }
                    Onglet.TOUS -> l
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                when {
                    liste == null -> Text("Chargement…", color = Discret, fontSize = 13.sp)
                    liste.isEmpty() -> Text(
                        "Aucun favori. Dans Verso, touche ☆ sur un modèle pour l'ajouter.",
                        color = Discret, fontSize = 13.sp,
                    )
                    else -> liste.chunked(COLONNES).forEach { ligne ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ligne.forEach { c ->
                                Case(c, c.id == actif, Modifier.weight(1f)) { onChoisir(c.modele) }
                            }
                            repeat(COLONNES - ligne.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Onglet("Éteindre", false, Modifier.weight(1f), onEteindre)
                Onglet("Ouvrir Verso", false, Modifier.weight(1f), onOuvrirVerso)
            }
        }
    }
}

@Composable
private fun Case(c: Choix, actif: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val forme = RoundedCornerShape(14.dp)
    // Animée seulement quand elle est à l'écran (la liste « Tous » est longue).
    var animer by remember { mutableStateOf(false) }
    Column(
        modifier
            .suivreVisibilite { animer = it }
            .clip(forme)
            .background(Panneau)
            .border(if (actif) 2.dp else 1.dp, if (actif) Rouge else Bord, forme)
            .clickable(onClick = onTap)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val m = c.modele
        DotMatrix(
            if (animer) apercuAnime(m) else if (m.dynamique != null) m.frameDynamique() else m.apercu,
            Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            c.modele.nom, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Onglet(texte: String, actif: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val forme = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(forme)
            .background(if (actif) Rouge else Panneau)
            .border(1.dp, if (actif) Rouge else Bord, forme)
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texte, color = Color.White, fontSize = 13.sp) }
}
