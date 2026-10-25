package fr.niakimbo.verso.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.modele.Horloge
import fr.niakimbo.verso.modele.Modele
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Met à jour tous les widgets posés : modèle actif, réglages, style de chaque widget.
 *
 * Appelé quand le modèle ou ses réglages changent (VersoApp), après un tap sur le widget,
 * et chaque minute tant que l'horloge est affichée. Un widget dont rien n'a changé n'est pas renvoyé.
 */
object WidgetMaj {
    private val verrou = Mutex()
    private val signatures = HashMap<Int, String>()

    suspend fun majTous(app: VersoApp, forcer: Boolean = false) = verrou.withLock {
        val manager = AppWidgetManager.getInstance(app)
        val parFormat = Format.entries.associateWith { manager.getAppWidgetIds(ComponentName(app, it.classe)) }
        if (parFormat.values.all { it.isEmpty() }) {
            annulerMinute(app)
            return@withLock
        }

        val id = app.store.modeleActif
        val modele = id?.let { app.catalogue.resoudre(it) }
        val minute = if (modele?.dynamique != null) LocalTime.now().truncatedTo(ChronoUnit.MINUTES).toString() else ""
        val revision = "${app.reglages.revision.value}.${app.mesModeles.revision.value}"

        for ((format, ids) in parFormat) {
            for (w in ids) {
                val style = app.stylesWidget.lire(w, format)
                val signature = "$id|$revision|$minute|${style.versJson()}"
                if (!forcer && signatures[w] == signature) continue
                if (envoyer(app, manager, w, format, style, modele)) signatures[w] = signature
            }
        }

        if (modele?.dynamique != null) programmerMinute(app) else annulerMinute(app)
    }

    fun oublier(id: Int) {
        signatures.remove(id)
    }

    /**
     * Envoie le widget au lanceur. Si le paquet est trop gros pour l'échange entre apps,
     * on réessaie avec moitié moins d'images (l'animation est alors allégée, et on le note).
     */
    private fun envoyer(
        app: VersoApp,
        manager: AppWidgetManager,
        id: Int,
        format: Format,
        style: StyleWidget,
        modele: Modele?,
    ): Boolean {
        var budget = RenduWidget.BUDGET_OCTETS
        while (true) {
            try {
                manager.updateAppWidget(id, RenduWidget.construire(app, format, style, modele, budget = budget))
                if (budget < RenduWidget.BUDGET_OCTETS) {
                    app.journalToy.ajouter("Widget ${format.label} : animation « ${modele?.nom} » allégée pour le lanceur")
                }
                return true
            } catch (e: RuntimeException) {
                if (budget < 40_000) {
                    app.journalToy.ajouter("Widget ${format.label} : envoi refusé par le lanceur (${e.javaClass.simpleName})")
                    return false
                }
                budget /= 2
            }
        }
    }

    /**
     * Prochaine minute pile, sans réveiller le téléphone (RTC) : écran éteint, l'alarme attend
     * le prochain réveil et l'horloge du widget se remet à l'heure dès que l'écran s'allume.
     */
    private fun programmerMinute(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val quand = System.currentTimeMillis() + Horloge.msAvantProchaineMinute() + 300
        val pi = WidgetActions.intention(context, WidgetActions.MINUTE)
        try {
            am.setExact(AlarmManager.RTC, quand, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC, quand, pi)
        }
    }

    fun annulerMinute(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(WidgetActions.intention(context, WidgetActions.MINUTE))
    }
}
