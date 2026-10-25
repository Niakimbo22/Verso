package fr.niakimbo.verso.createur

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Couleurs Nothing partagées par les deux éditeurs. */
internal object C {
    val Rouge = Color(0xFFD71921)
    val Discret = Color(0xFF8A8A8A)
    val Panneau = Color(0xFF111111)
    val Bord = Color(0xFF2A2A2A)
    val Eteinte = Color(0xFF1E1E1E)
}

@Composable
internal fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        color = C.Discret,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Petit bouton carré (outil, action), rouge quand il est actif. */
@Composable
internal fun Touche(
    texte: String,
    actif: Boolean = false,
    modifier: Modifier = Modifier,
    dispo: Boolean = true,
    petit: Boolean = false,
    onTap: () -> Unit,
) {
    val forme = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(forme)
            .background(if (actif) C.Rouge else C.Panneau)
            .border(1.dp, if (actif) C.Rouge else C.Bord, forme)
            .clickable(enabled = dispo, onClick = onTap)
            .alpha(if (dispo) 1f else 0.35f)
            .padding(horizontal = if (petit) 8.dp else 10.dp, vertical = if (petit) 7.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texte, color = Color.White, fontSize = if (petit) 12.sp else 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Rangée de touches qui défile si elle est trop large. */
@Composable
internal fun RangeeTouches(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Barre du haut : ← quitter, titre, action principale. */
@Composable
internal fun BarreHaut(titre: String, action: String, actionActive: Boolean, onRetour: () -> Unit, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "←",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onRetour).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(titre, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        Touche(action, actif = actionActive, onTap = onAction)
    }
}

/** Nom et rayon du modèle, avec les rayons existants en suggestions. */
@Composable
internal fun NomEtRayon(
    nom: String,
    onNom: (String) -> Unit,
    rayon: String,
    onRayon: (String) -> Unit,
    rayons: List<String>,
) {
    val couleurs = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = C.Rouge,
        unfocusedBorderColor = C.Bord,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = C.Rouge,
        focusedLabelColor = C.Rouge,
        unfocusedLabelColor = C.Discret,
    )
    OutlinedTextField(
        value = nom,
        onValueChange = { onNom(it.take(40)) },
        label = { Text("Nom") },
        singleLine = true,
        colors = couleurs,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = rayon,
        onValueChange = { onRayon(it.take(30)) },
        label = { Text("Rayon de la bibliothèque") },
        singleLine = true,
        colors = couleurs,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    RangeeTouches {
        (listOf(MesModelesStore.RAYON) + rayons.filter { it != MesModelesStore.RAYON }).forEach { r ->
            Touche(r, actif = r == rayon, petit = true) { onRayon(r) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Tes créations sont toutes dans « Mes modèles » ; un autre rayon les range en plus avec les modèles de ce rayon.",
        color = C.Discret, fontSize = 11.sp, lineHeight = 15.sp,
    )
}

/** Confirmation avant de perdre des modifications ou de supprimer. */
@Composable
internal fun Confirmer(
    titre: String,
    texte: String,
    oui: String,
    non: String = "Annuler",
    onOui: () -> Unit,
    onNon: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNon,
        containerColor = C.Panneau,
        titleContentColor = Color.White,
        textContentColor = C.Discret,
        title = { Text(titre) },
        text = { Text(texte) },
        confirmButton = { TextButton(onClick = onOui) { Text(oui, color = C.Rouge) } },
        dismissButton = { TextButton(onClick = onNon) { Text(non, color = Color.White) } },
    )
}

/** Petit compteur −/+ avec sa valeur. */
@Composable
internal fun Pas(titre: String, valeur: Int, min: Int, max: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titre, color = C.Discret, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Touche("−", petit = true, dispo = valeur > min) { onChange(valeur - 1) }
            Text(
                valeur.toString(), color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 28.dp),
            )
            Touche("+", petit = true, dispo = valeur < max) { onChange(valeur + 1) }
        }
    }
}

/** Pastille de couleur d'un niveau de luminosité (1-9). */
@Composable
internal fun Niveau(n: Int, actif: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val forme = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(forme)
            .background(C.Panneau)
            .border(if (actif) 2.dp else 1.dp, if (actif) C.Rouge else C.Bord, forme)
            .clickable(onClick = onTap)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.3f + 0.7f * n / 9f)),
            )
            Spacer(Modifier.height(2.dp))
            Text(n.toString(), color = if (actif) Color.White else C.Discret, fontSize = 10.sp)
        }
    }
}
