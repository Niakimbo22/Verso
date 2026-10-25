package fr.niakimbo.verso.auto

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Une règle automatique : « Si [quand], afficher [modele] ».
 *
 * [modele] null = éteindre la matrice. [revenir] (règles « pendant ») : à la fin, on revient
 * au modèle d'avant ; sinon le modèle de la règle reste affiché.
 */
data class Regle(
    val id: String,
    val nom: String,
    val quand: Declencheur,
    val modele: String?,
    val active: Boolean = true,
    val revenir: Boolean = true,
) {
    /** Change quand ce qui s'affiche change : une règle en cours modifiée est réappliquée. */
    val signature: String get() = "$id|$modele|$revenir"
}

/**
 * Ce qui déclenche une règle. Deux familles :
 * - « pendant » ([estMoment] faux) : vrai tant que la situation dure (plage horaire, en charge…) ;
 * - « moment » : un instant (le réveil), le modèle reste ensuite jusqu'au prochain changement.
 */
sealed class Declencheur {
    abstract val type: String
    open val estMoment: Boolean get() = false

    /** De [debut] à [fin] (minutes depuis minuit) ; [fin] < [debut] = passe minuit ; égales = toute la journée. */
    data class Plage(val debut: Int, val fin: Int, val jours: Set<DayOfWeek> = TOUS_LES_JOURS) : Declencheur() {
        override val type get() = PLAGE
    }

    /** Premier déverrouillage du jour entre [debut] et [fin] (minutes depuis minuit). */
    data class Reveil(val debut: Int = 5 * 60, val fin: Int = 12 * 60) : Declencheur() {
        override val type get() = REVEIL
        override val estMoment get() = true
    }

    data object Charge : Declencheur() {
        override val type get() = CHARGE
    }

    /** Batterie à [seuil] % ou moins, hors charge. */
    data class BatterieFaible(val seuil: Int = 20) : Declencheur() {
        override val type get() = BATTERIE
    }

    /** Casque ou écouteurs connectés (Bluetooth, filaire, USB) ; [appareil] = seulement celui-là. */
    data class Casque(val appareil: String? = null) : Declencheur() {
        override val type get() = CASQUE
    }

    data object NePasDeranger : Declencheur() {
        override val type get() = DND
    }

    companion object {
        const val PLAGE = "plage"
        const val REVEIL = "reveil"
        const val CHARGE = "charge"
        const val BATTERIE = "batterie"
        const val CASQUE = "casque"
        const val DND = "dnd"
        val TOUS_LES_JOURS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

/** Ce que le téléphone vit en ce moment, lu par le moteur à chaque évènement. */
data class Contexte(
    val maintenant: LocalDateTime,
    val enCharge: Boolean,
    val batterie: Int,
    /** Noms des casques / écouteurs connectés (vide = aucun). */
    val casques: List<String>,
    val nePasDeranger: Boolean,
)

/** Évaluation des règles : fonctions pures, testées sans téléphone. */
object Evaluation {

    /** La règle « pendant » qui gagne maintenant : la première de la liste (ordre = priorité). */
    fun gagnante(regles: List<Regle>, ctx: Contexte): Regle? =
        regles.firstOrNull { it.active && !it.quand.estMoment && vraie(it.quand, ctx) }

    fun vraie(d: Declencheur, ctx: Contexte): Boolean = when (d) {
        is Declencheur.Plage -> dansPlage(d, ctx.maintenant)
        is Declencheur.Reveil -> false // un moment, pas un état
        Declencheur.Charge -> ctx.enCharge
        is Declencheur.BatterieFaible -> !ctx.enCharge && ctx.batterie <= d.seuil
        is Declencheur.Casque -> d.appareil?.let { nom -> ctx.casques.any { it.equals(nom, ignoreCase = true) } }
            ?: ctx.casques.isNotEmpty()
        Declencheur.NePasDeranger -> ctx.nePasDeranger
    }

    fun dansPlage(p: Declencheur.Plage, t: LocalDateTime): Boolean {
        val m = t.hour * 60 + t.minute
        val jour = t.dayOfWeek
        return when {
            p.debut == p.fin -> jour in p.jours
            p.debut < p.fin -> jour in p.jours && m >= p.debut && m < p.fin
            // Passe minuit : le soir compte pour ce jour, la fin de nuit pour la veille.
            else -> (m >= p.debut && jour in p.jours) || (m < p.fin && jour.minus(1) in p.jours)
        }
    }

    /** Fenêtre du réveil : le déverrouillage [t] tombe-t-il dedans ? */
    fun dansFenetreReveil(r: Declencheur.Reveil, t: LocalDateTime): Boolean {
        val m = t.hour * 60 + t.minute
        return if (r.debut <= r.fin) m >= r.debut && m < r.fin else m >= r.debut || m < r.fin
    }

    /**
     * Prochaine minute où une plage commence ou finit (pour programmer une alarme),
     * null s'il n'y a aucune plage active.
     */
    fun prochaineFrontiere(regles: List<Regle>, t: LocalDateTime): LocalDateTime? {
        val bornes = regles.filter { it.active }.mapNotNull { it.quand as? Declencheur.Plage }
            .filter { it.debut != it.fin }
            .flatMap { listOf(it.debut, it.fin) }
            .distinct()
        if (bornes.isEmpty()) {
            // Une plage « toute la journée » limitée à certains jours change à minuit.
            val aMinuit = regles.any { it.active && (it.quand as? Declencheur.Plage)?.let { p -> p.debut == p.fin && p.jours.size < 7 } == true }
            return if (aMinuit) t.toLocalDate().plusDays(1).atStartOfDay() else null
        }
        val base = t.withSecond(0).withNano(0)
        val minute = t.hour * 60 + t.minute
        val prochaine = bornes.minOf { b -> if (b > minute) b - minute else b - minute + 24 * 60 }
        return base.plusMinutes(prochaine.toLong())
    }
}

/**
 * Ce qui doit s'afficher, selon l'option A choisie par Nico : une règle s'applique quand elle
 * commence ; un choix à la main tient jusqu'au prochain changement automatique.
 *
 * [enCours] : règle « pendant » appliquée en ce moment. [base] : le modèle à remettre quand plus
 * aucune règle ne s'applique (celui d'avant, ou le dernier choix fait à la main pendant la règle).
 */
object Decision {
    data class EnCours(val signature: String, val modele: String?, val revenir: Boolean)
    data class Memoire(val enCours: EnCours? = null, val base: String? = null)

    /** [afficher] non nul = mettre ce modèle (sa valeur interne null = éteindre). */
    data class Resultat(val memoire: Memoire, val afficher: Cible?)
    data class Cible(val modele: String?)

    /**
     * La règle gagnante vaut maintenant [gagnante] (null = aucune) ; [affiche] est le modèle actuellement à l'écran.
     * Rien ne bouge tant que la gagnante ne change pas : c'est ce qui laisse tenir un choix fait à la main.
     */
    fun evaluer(m: Memoire, gagnante: Regle?, affiche: String?): Resultat {
        val nouvelle = gagnante?.let { EnCours(it.signature, it.modele, it.revenir) }
        if (nouvelle?.signature == m.enCours?.signature) return Resultat(m, null)
        if (nouvelle != null) {
            // Aucune règle avant : ce qui était affiché devient le modèle à remettre ensuite.
            val base = if (m.enCours == null) affiche else m.base
            val afficher = if (nouvelle.modele == affiche) null else Cible(nouvelle.modele)
            return Resultat(Memoire(nouvelle, base), afficher)
        }
        val fini = m.enCours!!
        if (!fini.revenir) return Resultat(Memoire(null, affiche), null)
        val afficher = if (m.base == affiche) null else Cible(m.base)
        return Resultat(Memoire(null, m.base), afficher)
    }

    /** Choix fait à la main ([modele] null = éteint) : pendant une règle, c'est lui qu'on remettra à la fin. */
    fun choixManuel(m: Memoire, modele: String?): Memoire =
        if (m.enCours != null) m.copy(base = modele) else m

    /** Une règle « moment » (réveil) se déclenche : on affiche, et c'est le nouveau modèle de base. */
    fun moment(m: Memoire, modele: String?, affiche: String?): Resultat {
        val memoire = if (m.enCours != null) m.copy(base = modele) else m
        return Resultat(memoire, if (modele == affiche) null else Cible(modele))
    }
}
