package fr.niakimbo.verso.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.createur.CreateurActivity
import fr.niakimbo.verso.createur.MesModelesStore
import fr.niakimbo.verso.createur.MoteurBlocs
import fr.niakimbo.verso.matrix.MatrixFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

private val Rouge = Color(0xFFD71921)
private val Discret = Color(0xFF8A8A8A)
private val Panneau = Color(0xFF111111)
private val Bord = Color(0xFF2A2A2A)

/** Trois boutons au-dessus de la bibliothèque : nouveau dessin, nouveau modèle dynamique, importer. */
@Composable
fun BarreCreer(onImporte: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as VersoApp
    val scope = rememberCoroutineScope()
    var menuImport by remember { mutableStateOf(false) }

    fun importer(texte: String?) {
        if (texte.isNullOrBlank()) {
            Toast.makeText(context, "Rien à importer.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val message = try {
                val ajoutes = app.mesModeles.importer(texte) { p -> apercuBlocs(app, p) }
                onImporte()
                if (ajoutes.size == 1) "« ${ajoutes[0].nom} » ajouté à Mes modèles" else "${ajoutes.size} modèles ajoutés à Mes modèles"
            } catch (e: Exception) {
                "Import impossible : ${e.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val fichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val texte = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            importer(texte)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BoutonCreer("✎", "Dessin", Modifier.weight(1f)) { CreateurActivity.ouvrir(context, CreateurActivity.DESSIN) }
        BoutonCreer("◷", "Dynamique", Modifier.weight(1f)) { CreateurActivity.ouvrir(context, CreateurActivity.BLOCS) }
        BoutonCreer("⇩", "Importer", Modifier.weight(1f)) { menuImport = true }
    }

    if (menuImport) {
        AlertDialog(
            onDismissRequest = { menuImport = false },
            containerColor = Panneau,
            titleContentColor = Color.White,
            textContentColor = Discret,
            title = { Text("Importer un modèle") },
            text = { Text("Un fichier .json exporté depuis Verso (ou écrit à la main au format de VERSO_SPEC), ou le texte JSON copié dans le presse-papiers.") },
            confirmButton = {
                TextButton(onClick = {
                    menuImport = false
                    fichier.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }) { Text("Choisir un fichier", color = Rouge) }
            },
            dismissButton = {
                TextButton(onClick = {
                    menuImport = false
                    val presse = context.getSystemService(ClipboardManager::class.java)
                    importer(presse?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString())
                }) { Text("Coller", color = Color.White) }
            },
        )
    }
}

@Composable
private fun BoutonCreer(symbole: String, texte: String, modifier: Modifier, onTap: () -> Unit) {
    val forme = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(forme)
            .background(Panneau)
            .border(1.dp, Bord, forme)
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbole, color = Rouge, fontSize = 18.sp)
            Text("+ $texte", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Aperçu d'un modèle par blocs (écrit dans son fichier comme image de secours). */
internal fun apercuBlocs(app: VersoApp, p: MesModelesStore.Perso): MatrixFrame? =
    (p as? MesModelesStore.Perso.Blocs)?.let {
        MoteurBlocs.rendre(it.def.blocs, MoteurBlocs.instant(LocalTime.of(10, 10)), app.catalogue.ressources())
    }

/** Partage le JSON d'un modèle créé (messagerie, Drive, notes…). */
internal suspend fun partagerJson(context: Context, app: VersoApp, id: String) {
    val p = app.mesModeles.parId(id) ?: return
    val json = app.mesModeles.exporter(id, apercuBlocs(app, p)) ?: return
    val envoi = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_TEXT, json)
        .putExtra(Intent.EXTRA_SUBJECT, "Modèle Verso « ${p.nom} »")
        .putExtra(Intent.EXTRA_TITLE, "${p.nom}.json")
    context.startActivity(Intent.createChooser(envoi, "Partager « ${p.nom} »"))
}

/** Écrit le JSON d'un modèle créé dans un fichier choisi par l'utilisateur. */
internal suspend fun ecrireJson(context: Context, app: VersoApp, id: String, uri: Uri): Boolean {
    val p = app.mesModeles.parId(id) ?: return false
    val json = app.mesModeles.exporter(id, apercuBlocs(app, p)) ?: return false
    return withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null }.getOrDefault(false)
    }
}

/** Supprime un modèle créé : il quitte aussi les favoris, et la matrice s'éteint s'il y était affiché. */
internal suspend fun supprimerPerso(app: VersoApp, id: String) {
    if (app.store.modeleActif == id) app.player.eteindre()
    if (app.store.dernierModele == id) app.store.dernierModele = null
    if (app.favoris.estFavori(id)) app.favoris.basculer(id)
    app.mesModeles.supprimer(id)
}

internal fun nomFichier(nom: String) = nom.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "modele" } + ".json"
