package fr.niakimbo.verso.glyph

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import fr.niakimbo.verso.VersoApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Le VRAI bouton Glyph (Nico, 26/10) : Nothing ne donne aux apps aucun moyen d'allumer / éteindre
 * la Glyph Interface (rien dans le SDK, réglage système protégé qui demanderait l'ADB). Ce service
 * d'accessibilité fait donc ce que ferait le doigt : il ouvre le menu déroulant, trouve la tuile
 * « Glyph », lit son état, appuie dessus si besoin, puis referme le menu.
 *
 * Il ne réagit à aucun évènement de lui-même : il n'agit que quand Verso le lui demande (⏻).
 */
class BoutonGlyph : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _disponible.value = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        _disponible.value = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /** Ouvre le menu, cherche la tuile (en tournant les pages), lit l'état, appuie si besoin. */
    private suspend fun regler(app: VersoApp, voulu: Boolean?): Resultat {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        try {
            var tuile: AccessibilityNodeInfo? = null
            var tours = 0
            // Jusqu'à ~2,5 s : le menu s'ouvre, puis on fait défiler les pages de tuiles.
            while (tuile == null && tours < 25) {
                delay(100)
                tuile = chercherTuile()
                if (tuile == null && tours in setOf(8, 13, 18)) tournerPage(avant = tours != 18)
                tours++
            }
            if (tuile == null) {
                app.journalToy.ajouter("Bouton Glyph : tuile « Glyph » introuvable dans le menu déroulant")
                return Resultat.Introuvable
            }
            val avant = etat(tuile)
            if (voulu != null && avant == voulu) {
                memoriser(avant)
                return Resultat.Fait(avant)
            }
            cliquer(tuile)
            delay(450)
            val apres = chercherTuile()?.let(::etat) ?: avant?.not()
            memoriser(apres)
            app.journalToy.ajouter("Bouton Glyph : tuile touchée (${texte(avant)} → ${texte(apres)})")
            return Resultat.Fait(apres)
        } finally {
            delay(150)
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
    }

    private fun texte(b: Boolean?) = when (b) { true -> "allumé"; false -> "éteint"; null -> "?" }

    /** La tuile Glyph : un élément du menu dont le nom est « Glyph » (pas la lampe Glyph, ni la tuile Verso). */
    private fun chercherTuile(): AccessibilityNodeInfo? {
        val racines = windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow)
        val candidats = mutableListOf<AccessibilityNodeInfo>()
        for (r in racines) parcourir(r) { n ->
            val nom = (n.contentDescription ?: n.text)?.toString()?.trim()?.lowercase() ?: return@parcourir
            if (!nom.startsWith("glyph")) return@parcourir
            if (EXCLUS.any { nom.contains(it) }) return@parcourir
            candidats += n
        }
        // Le plus précis d'abord : exactement « Glyph », puis « Glyph, activé »…
        return candidats.sortedBy { n ->
            val nom = (n.contentDescription ?: n.text).toString().trim().lowercase()
            if (nom == "glyph") 0 else 1
        }.firstOrNull()
    }

    private fun parcourir(n: AccessibilityNodeInfo, f: (AccessibilityNodeInfo) -> Unit) {
        f(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { parcourir(it, f) }
    }

    /** Allumée ? (case cochée, ou description « Activé » / « Désactivé ») ; null = inconnu. */
    private fun etat(n: AccessibilityNodeInfo): Boolean? {
        val noeuds = generateSequence(n) { it.parent }.take(4).toList()
        noeuds.firstOrNull { it.isCheckable }?.let { return it.isChecked }
        val textes = noeuds.flatMap { listOfNotNull(it.stateDescription, it.contentDescription, it.text) }
            .joinToString(" ").lowercase()
        return when {
            listOf("désactiv", "desactiv", "off", "inactive").any { textes.contains(it) } -> false
            listOf("activé", "active", " on").any { textes.contains(it) } -> true
            else -> null
        }
    }

    private fun cliquer(n: AccessibilityNodeInfo) {
        val cible = generateSequence(n) { it.parent }.take(5).firstOrNull { it.isClickable } ?: n
        cible.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Fait défiler les pages de tuiles (la tuile Glyph peut être sur la 2ᵉ page). */
    private fun tournerPage(avant: Boolean) {
        val racines = windows.mapNotNull { it.root } + listOfNotNull(rootInActiveWindow)
        for (r in racines) {
            var fait = false
            parcourir(r) { n ->
                if (!fait && n.isScrollable && n.packageName?.toString() == SYSTEMUI) {
                    fait = n.performAction(if (avant) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }
            }
            if (fait) return
        }
    }

    private fun memoriser(allume: Boolean?) {
        if (allume != null) _allume.value = allume
    }

    sealed class Resultat {
        /** [allume] : état de la tuile après l'action (null = pas lisible). */
        data class Fait(val allume: Boolean?) : Resultat()
        data object Introuvable : Resultat()
        data object Indisponible : Resultat()
    }

    companion object {
        private const val SYSTEMUI = "com.android.systemui"
        private val EXCLUS = listOf("lampe", "torch", "toy", "jeu", "verso")

        @Volatile
        private var instance: BoutonGlyph? = null
        private val verrou = Mutex()

        private val _disponible = MutableStateFlow(false)

        /** Le service est-il activé dans Réglages › Accessibilité ? */
        val disponible: StateFlow<Boolean> = _disponible

        private val _allume = MutableStateFlow<Boolean?>(null)

        /** Dernier état connu de la Glyph Interface (null = jamais lu). */
        val allume: StateFlow<Boolean?> = _allume

        /** Relit dans les réglages si le service est activé (après un retour de Réglages, par exemple). */
        fun actualiser(context: Context) {
            val nom = ComponentName(context, BoutonGlyph::class.java).flattenToString()
            val actifs = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            _disponible.value = instance != null && actifs.split(':').any { it.equals(nom, ignoreCase = true) }
        }

        /** Le Glyph est allumé d'après ce qu'on sait (le Glyph Toy de Verso lié prouve qu'il l'est). */
        fun noterAllume() {
            _allume.value = true
        }

        /** Toy délié : le Glyph a peut-être été coupé à la main, on ne sait plus (sauf s'il était déjà éteint). */
        fun oublier() {
            if (_allume.value == true) _allume.value = null
        }

        /** Allume ([voulu] vrai), éteint (faux) ou bascule (null) la Glyph Interface. */
        suspend fun regler(app: VersoApp, voulu: Boolean?): Resultat {
            val s = instance ?: return Resultat.Indisponible
            return verrou.withLock { s.regler(app, voulu) }
        }
    }
}
