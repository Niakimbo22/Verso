package fr.niakimbo.verso.auto

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.glyph.BoutonGlyph
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * L'app au premier plan, pour la condition « App ouverte » (étape 9, partie 3).
 *
 * Deux sources, la meilleure d'abord :
 * 1. le service d'accessibilité de Verso (celui du bouton Glyph) : il reçoit chaque changement
 *    de fenêtre, c'est instantané et sans autre autorisation ;
 * 2. à défaut, « Accès aux données d'utilisation » : Verso regarde toutes les secondes, seulement
 *    écran allumé et seulement si une règle « App ouverte » existe.
 */
object AppPremierPlan {
    @Volatile
    var pkg: String? = null
        private set

    @Volatile
    private var app: VersoApp? = null
    private var sondage: Job? = null
    private var claviers: Set<String> = emptySet()

    /** Paquets qui ne sont jamais « l'app ouverte » (menu déroulant, barre système). */
    private val IGNORES = setOf("com.android.systemui", "android")

    fun init(a: VersoApp) {
        app = a
        claviers = try {
            a.getSystemService(InputMethodManager::class.java)?.inputMethodList?.map { it.packageName }?.toSet().orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Évènement du service d'accessibilité : une nouvelle fenêtre est au premier plan. */
    fun surEvenement(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        val classe = e.className?.toString() ?: return
        if (p in IGNORES || p in claviers) return
        // Seulement une vraie activité (pas une boîte de dialogue ou un menu qui s'ouvre dans l'app).
        val a = app ?: return
        val estActivite = try {
            a.packageManager.getActivityInfo(ComponentName(p, classe), 0)
            true
        } catch (_: Exception) {
            false
        }
        if (!estActivite) return
        changer(p, "app ouverte")
    }

    private fun changer(p: String, raison: String) {
        if (p == pkg) return
        pkg = p
        app?.moteurRegles?.evaluer(raison)
    }

    /** « Accès aux données d'utilisation » accordé à Verso ? */
    fun usageAutorise(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun ouvrirUsage(c: Context) {
        val detail = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, android.net.Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(detail)
        } catch (_: Exception) {
            c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun ouvrirAccessibilite(c: Context) =
        c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** Une des deux sources marche ? */
    fun disponible(c: Context): Boolean {
        BoutonGlyph.actualiser(c)
        return BoutonGlyph.disponible.value || usageAutorise(c)
    }

    /**
     * Appelé par le moteur à chaque évaluation : lance (ou arrête) la lecture toutes les secondes
     * quand l'accessibilité n'est pas là. [besoin] : une règle « App ouverte » active existe.
     */
    @Synchronized
    fun actualiserSondage(besoin: Boolean) {
        val a = app ?: return
        val faut = besoin && !BoutonGlyph.disponible.value && usageAutorise(a) &&
            a.getSystemService(PowerManager::class.java).isInteractive
        if (!faut) {
            sondage?.cancel()
            sondage = null
            return
        }
        if (sondage?.isActive == true) return
        sondage = a.appScope.launch {
            val usage = a.getSystemService(UsageStatsManager::class.java)
            val power = a.getSystemService(PowerManager::class.java)
            while (isActive && power.isInteractive && !BoutonGlyph.disponible.value) {
                derniereApp(usage)?.let { changer(it, "app ouverte (usage)") }
                delay(1_000)
            }
        }
    }

    /** Dernière app passée au premier plan dans les 10 dernières secondes. */
    private fun derniereApp(usage: UsageStatsManager): String? {
        val maintenant = System.currentTimeMillis()
        val evts = usage.queryEvents(maintenant - 10_000, maintenant)
        val e = UsageEvents.Event()
        var dernier: String? = null
        while (evts.hasNextEvent()) {
            evts.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.packageName !in IGNORES && e.packageName !in claviers) {
                dernier = e.packageName
            }
        }
        return dernier
    }
}
