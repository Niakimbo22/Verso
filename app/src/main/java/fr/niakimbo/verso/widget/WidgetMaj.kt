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
        val persos = app.widgetsPerso.poses(app)
        if (parFormat.values.all { it.isEmpty() } && persos.isEmpty()) {
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

        // Widgets de la galerie (une taille par fournisseur).
        var horlogeEpinglee = false
        if (persos.isNotEmpty()) {
            val etat = etatPerso(app, modele)
            val epingles = HashMap<String, Modele?>()
            for ((w, taille) in persos) {
                val reglage = app.widgetsPerso.pour(w, taille)
                // Modèle épinglé : ce widget montre toujours le même, quel que soit celui au dos.
                val m = reglage.modele?.let { e -> epingles.getOrPut(e) { app.catalogue.resoudre(e) } } ?: modele
                if (m?.dynamique != null && reglage.modele != null) horlogeEpinglee = true
                val min = if (m?.dynamique != null) LocalTime.now().truncatedTo(ChronoUnit.MINUTES).toString() else ""
                val signature = "galerie|${reglage.versJson()}|$id|$revision|$min|${etat.batterie}|${etat.enCharge}|${etat.regleAuto}|${etat.glyphEteint}|${etat.toyLie}"
                if (!forcer && signatures[w] == signature) continue
                if (envoyerPerso(app, manager, w, reglage.plan(), etat.copy(modele = m))) signatures[w] = signature
            }
        }

        if (modele?.dynamique != null || horlogeEpinglee) programmerMinute(app) else annulerMinute(app)
    }

    /** Ce que les widgets perso affichent en ce moment. */
    fun etatPerso(app: VersoApp, modele: Modele?): fr.niakimbo.verso.widget.perso.RenduPerso.Etat {
        val b = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val niveau = b?.let {
            val n = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val max = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (n < 0 || max <= 0) null else n * 100 / max
        } ?: 100
        val enCharge = (b?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val regles = app.moteurRegles.store
        val regle = if (regles.actif.value) regles.regles.value.firstOrNull { it.id == app.moteurRegles.enCours.value }?.nom else null
        return fr.niakimbo.verso.widget.perso.RenduPerso.Etat(
            modele = modele, batterie = niveau, enCharge = enCharge, regleAuto = regle,
            glyphEteint = fr.niakimbo.verso.glyph.BoutonGlyph.allume.value == false,
            toyLie = app.toyLie.value,
        )
    }

    /**
     * Aperçus de la liste des widgets du lanceur (Android 15+, `setWidgetPreview`) : le vrai rendu
     * de chaque taille, au lieu d'une image fixe. API limitée par le système (~2 appels par heure) :
     * une fois par version de Verso, et au plus une fois par jour.
     */
    suspend fun publierApercus(app: VersoApp) {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val prefs = app.getSharedPreferences("verso_widgets_galerie", Context.MODE_PRIVATE)
        val cle = "${fr.niakimbo.verso.BuildConfig.VERSION_CODE}"
        val maintenant = System.currentTimeMillis()
        if (prefs.getString("apercus_version", null) == cle && maintenant - prefs.getLong("apercus_quand", 0) < 24 * 3600_000L) return
        val modele = app.store.modeleActif?.let { app.catalogue.resoudre(it) }
            ?: app.catalogue.contenu().entrees.firstOrNull { !it.cache }?.let(app.catalogue::resoudre)
        val etat = etatPerso(app, modele)
        val manager = AppWidgetManager.getInstance(app)
        var ok = 0
        for (t in fr.niakimbo.verso.widget.perso.TailleW.entries) {
            try {
                val plan = fr.niakimbo.verso.widget.perso.WidgetVerso.parDefaut(t).copy(genre = fr.niakimbo.verso.widget.perso.Genre.MATRICE).plan().copy(anime = false)
                val rv = fr.niakimbo.verso.widget.perso.RenduPerso.construire(app, plan, etat, cliquable = false, budget = 60_000)
                if (manager.setWidgetPreview(ComponentName(app, t.classe), android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, rv)) ok++
            } catch (e: Exception) {
                app.journalToy.ajouter("Aperçu du widget ${t.label} pour le lanceur impossible : ${e.javaClass.simpleName}")
            }
        }
        prefs.edit().putString("apercus_version", cle).putLong("apercus_quand", maintenant).apply()
        app.journalToy.ajouter("Aperçus des widgets envoyés au lanceur : $ok / ${fr.niakimbo.verso.widget.perso.TailleW.entries.size}")
    }

    private fun envoyerPerso(
        app: VersoApp,
        manager: AppWidgetManager,
        id: Int,
        design: fr.niakimbo.verso.widget.perso.WidgetPerso,
        etat: fr.niakimbo.verso.widget.perso.RenduPerso.Etat,
    ): Boolean {
        var budget = RenduWidget.BUDGET_OCTETS
        while (true) {
            try {
                manager.updateAppWidget(id, fr.niakimbo.verso.widget.perso.RenduPerso.construire(app, design, etat, budget = budget))
                return true
            } catch (e: RuntimeException) {
                if (budget < 40_000) {
                    app.journalToy.ajouter("Widget « ${design.nom} » : envoi refusé par le lanceur (${e.javaClass.simpleName})")
                    return false
                }
                budget /= 2
            }
        }
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
