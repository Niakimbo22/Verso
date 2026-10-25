package fr.niakimbo.verso.createur

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Couleurs Nothing de l'éditeur. */
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

/** Bouton de l'éditeur : symbole + petit libellé, rouge quand il est actif. */
@Composable
internal fun Outil(
    symbole: String,
    libelle: String?,
    actif: Boolean = false,
    modifier: Modifier = Modifier,
    dispo: Boolean = true,
    onTap: () -> Unit,
) {
    val forme = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(forme)
            .background(if (actif) C.Rouge else C.Panneau)
            .border(1.dp, if (actif) C.Rouge else C.Bord, forme)
            .clickable(enabled = dispo, onClick = onTap)
            .alpha(if (dispo) 1f else 0.3f)
            .padding(horizontal = 8.dp, vertical = if (libelle == null) 12.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbole, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
            if (libelle != null) {
                Spacer(Modifier.height(2.dp))
                Text(libelle, color = if (actif) Color.White else C.Discret, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** Petite puce texte (options du menu ⋯). */
@Composable
internal fun Puce(texte: String, actif: Boolean = false, modifier: Modifier = Modifier, dispo: Boolean = true, onTap: () -> Unit) {
    val forme = RoundedCornerShape(50)
    Box(
        modifier
            .clip(forme)
            .background(if (actif) C.Rouge else C.Panneau)
            .border(1.dp, if (actif) C.Rouge else C.Bord, forme)
            .clickable(enabled = dispo, onClick = onTap)
            .alpha(if (dispo) 1f else 0.3f)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texte, color = Color.White, fontSize = 13.sp, maxLines = 1) }
}

/** Confirmation avant de perdre des modifications. */
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
