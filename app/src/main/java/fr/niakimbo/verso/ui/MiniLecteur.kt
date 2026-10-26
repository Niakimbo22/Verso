package fr.niakimbo.verso.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.rapide.ActionsRapides
import fr.niakimbo.verso.ui.verre.Discret
import fr.niakimbo.verso.ui.verre.PanneauVerre
import fr.niakimbo.verso.ui.verre.PuceVerre
import fr.niakimbo.verso.ui.verre.Rouge
import fr.niakimbo.verso.ui.verre.appui
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mini-lecteur flottant en verre (façon iPhone), en bas de l'écran principal : le modèle affiché
 * reste sous les yeux pendant qu'on fait défiler la bibliothèque.
 * - glisser à gauche / droite = modèle suivant / précédent ; ‹ › aussi ;
 * - ⏻ / ▶ = éteindre / rallumer ;
 * - toucher = remonter à la grande matrice ; appui long = luminosité, 🎲 au hasard, minuteur de veille.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniLecteur(app: VersoApp, modifier: Modifier = Modifier, onRemonter: () -> Unit) {
    val frame by app.matrix.lastFrame.collectAsStateWithLifecycle()
    val actif by app.player.actif.collectAsStateWithLifecycle()
    val enCours by app.moteurRegles.enCours.collectAsStateWithLifecycle()
    val regles by app.moteurRegles.store.regles.collectAsStateWithLifecycle()
    val autoActif by app.moteurRegles.store.actif.collectAsStateWithLifecycle()
    val finVeille by ActionsRapides.finVeille.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val vibreur = LocalHapticFeedback.current
    val regle = regles.firstOrNull { it.id == enCours }?.takeIf { autoActif }
    var options by remember { mutableStateOf(false) }

    // Glisser : la barre suit un peu le doigt, puis revient en place.
    val decalage = remember { Animatable(0f) }
    val seuil = with(LocalDensity.current) { 56.dp.toPx() }

    // Minuteur : minutes restantes, recalculées toutes les 20 s.
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(finVeille) {
        while (finVeille > 0) {
            maintenant = System.currentTimeMillis()
            delay(20_000)
        }
    }
    val resteVeille = if (finVeille > maintenant) ((finVeille - maintenant + 59_999) / 60_000).toInt() else 0

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(options, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Options(app, actif != null, resteVeille, onFermer = { options = false })
        }

        PanneauVerre(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = decalage.value }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (abs(total) > seuil) {
                                vibreur.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { ActionsRapides.changer(app, if (total < 0) 1 else -1) }
                            }
                            scope.launch { decalage.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { decalage.animateTo(0f) } },
                    ) { change, delta ->
                        change.consume()
                        total += delta
                        scope.launch { decalage.snapTo(total * 0.35f) }
                    }
                },
            forme = RoundedCornerShape(30.dp),
            actif = options,
        ) {
            Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (options) options = false else onRemonter() },
                            onLongClick = {
                                vibreur.performHapticFeedback(HapticFeedbackType.LongPress)
                                options = !options
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotMatrix(frame, Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            actif?.nom ?: if (frame == null) "Matrice éteinte" else "Aperçu",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                resteVeille > 0 -> "Veille dans $resteVeille min"
                                regle != null -> "Auto · « ${regle.nom} »"
                                else -> "Glisse pour changer · appui long = options"
                            },
                            color = if (regle != null || resteVeille > 0) Rouge else Discret, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Bouton("‹") { scope.launch { ActionsRapides.changer(app, -1) } }
                val glyph by fr.niakimbo.verso.glyph.BoutonGlyph.allume.collectAsStateWithLifecycle()
                val allume = actif != null && glyph != false
                Bouton(if (allume) "⏻" else "▶", couleur = if (allume) Color.White else Rouge) {
                    scope.launch { ActionsRapides.basculer(app) }
                }
                Bouton("›") { scope.launch { ActionsRapides.changer(app, 1) } }
            }
        }
    }
}

/** Options de l'appui long : luminosité du modèle affiché, au hasard, minuteur de veille. */
@Composable
private fun Options(app: VersoApp, allume: Boolean, resteVeille: Int, onFermer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val actifId by app.player.actif.collectAsStateWithLifecycle()
    var lum by remember(actifId?.id) { mutableFloatStateOf(ActionsRapides.luminositeActuelle(app)) }
    PanneauVerre(Modifier.fillMaxWidth(), forme = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LUMINOSITÉ", color = Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                Text("${lum.toInt()} %", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = lum,
                onValueChange = { lum = it },
                onValueChangeFinished = { scope.launch { ActionsRapides.luminosite(app, lum) } },
                valueRange = 10f..100f,
                enabled = allume,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Rouge, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.height(4.dp))
            Text("VEILLE", color = Discret, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    PuceVerre("$m min", false, Modifier.weight(1f)) { ActionsRapides.veille(app, m); onFermer() }
                }
                PuceVerre(if (resteVeille > 0) "Annuler" else "Off", resteVeille > 0, Modifier.weight(1f)) { ActionsRapides.veille(app, 0) }
            }
            Spacer(Modifier.height(12.dp))
            PuceVerre("🎲  Un favori au hasard", false, Modifier.fillMaxWidth()) {
                scope.launch { ActionsRapides.aleatoire(app) }
            }
        }
    }
}

@Composable
private fun Bouton(symbole: String, couleur: Color = Color.White, onClick: () -> Unit) {
    Text(
        symbole, color = couleur, fontSize = 22.sp,
        modifier = Modifier.appui(onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
