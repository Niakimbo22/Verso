package fr.niakimbo.verso.toy

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Ouvre l'écran où l'on choisit l'Always-on Glyph Toy, en essayant du plus direct au plus général.
 *
 * - AodToySelectActivity : l'écran de choix du Toy AOD (utilisé par des projets communautaires via ADB).
 * - ToysManagerActivity : l'écran « Gérer les Glyph Toys » documenté par Nothing (masqué sur le 4a Pro).
 * - Réglages Android : dernier recours, l'utilisateur suit le chemin affiché dans l'app.
 *
 * Renvoie ce qui a été ouvert, pour le journal du Toy.
 */
object ChoixToy {
    const val CHEMIN = "Réglages › Glyph Interface › Flip to Glyph › Always-on Glyph Toy"

    private const val PAQUET = "com.nothing.thirdparty"
    private val ECRANS = listOf(
        "$PAQUET.matrix.toys.manager.AodToySelectActivity" to "écran de choix du Toy AOD",
        "$PAQUET.matrix.toys.manager.ToysManagerActivity" to "gestionnaire de Glyph Toys",
    )

    fun ouvrir(context: Context): String {
        for ((classe, nom) in ECRANS) {
            val intent = Intent().setComponent(ComponentName(PAQUET, classe))
            try {
                context.startActivity(intent)
                return "Ouvert : $nom"
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        return try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
            "Écrans Glyph inaccessibles : Réglages ouverts, suis le chemin indiqué"
        } catch (e: ActivityNotFoundException) {
            "Impossible d'ouvrir les Réglages"
        }
    }
}
