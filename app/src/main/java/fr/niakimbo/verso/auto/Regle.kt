package fr.niakimbo.verso.auto

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Une règle automatique : « Si [conditions] (toutes, ou au moins une), alors [action] ».
 *
 * [revenir] (règles « pendant ») : à la fin, on revient au modèle d'avant ; sinon le modèle reste.
 */
data class Regle(
    val id: String,
    val nom: String,
    val conditions: List<Declencheur>,
    val action: Action,
    val toutes: Boolean = true,
    val active: Boolean = true,
    val revenir: Boolean = true,
) {
    constructor(id: String, nom: String, quand: Declencheur, modele: String?) :
        this(id, nom, listOf(quand), if (modele == null) Action.Eteindre else Action.Afficher(modele))

    /** Une condition « moment » (réveil, heure précise) fait de toute la règle un moment. */
    val estMoment: Boolean get() = conditions.any { it.estMoment }

    /** Change quand ce qui s'affiche change : une règle en cours modifiée est réappliquée. */
    val signature: String get() = "$id|${action.signature}|$revenir"
}

/** Ce que fait une règle quand elle s'applique. */
sealed class Action {
    abstract val signature: String

    data class Afficher(val modele: String) : Action() {
        override val signature get() = "m:$modele"
    }

    data object Eteindre : Action() {
        override val signature get() = "off"
    }

    /** Un favori tiré au hasard à chaque déclenchement. */
    data object Hasard : Action() {
        override val signature get() = "hasard"
    }

    /** Les favoris les uns après les autres, toutes les [minutes]. */
    data class Defiler(val minutes: Int = 15) : Action() {
        override val signature get() = "defiler:$minutes"
    }
}

/**
 * Ce qui déclenche une règle. Deux familles :
 * - « pendant » ([estMoment] faux) : vrai tant que la situation dure (plage horaire, en charge…) ;
 * - « moment » : un instant (le réveil, une heure précise), le modèle reste ensuite jusqu'au prochain changement.
 */
sealed class Declencheur {
    abstract val type: String
    open val estMoment: Boolean get() = false

    // ---- Temps ----

    /** De [debut] à [fin] (minutes depuis minuit) ; [fin] < [debut] = passe minuit ; égales = toute la journée. */
    data class Plage(val debut: Int, val fin: Int, val jours: Set<DayOfWeek> = TOUS_LES_JOURS) : Declencheur() {
        override val type get() = PLAGE
    }

    /** À [minute] pile (minutes depuis minuit), les [jours] choisis. */
    data class AHeure(val minute: Int = 18 * 60, val jours: Set<DayOfWeek> = TOUS_LES_JOURS) : Declencheur() {
        override val type get() = A_HEURE
        override val estMoment get() = true
    }

    /** Premier déverrouillage du jour entre [debut] et [fin] (minutes depuis minuit). */
    data class Reveil(val debut: Int = 5 * 60, val fin: Int = 12 * 60) : Declencheur() {
        override val type get() = REVEIL
        override val estMoment get() = true
    }

    /** La nuit (coucher → lever du soleil) ou le jour, avec un décalage en minutes. Ville réglée dans Automatique. */
    data class Soleil(val nuit: Boolean = true, val decalage: Int = 0) : Declencheur() {
        override val type get() = SOLEIL
    }

    /** Un jour précis de l'année (anniversaire, fête), toute la journée. */
    data class Date(val jour: Int = 31, val mois: Int = 10) : Declencheur() {
        override val type get() = DATE
    }

    /** Les [minutes] avant la prochaine alarme de l'app Horloge. */
    data class AvantAlarme(val minutes: Int = 30) : Declencheur() {
        override val type get() = ALARME
    }

    // ---- Batterie ----

    /** En charge ; [source] : null = n'importe laquelle, sinon [CABLE] ou [SANS_FIL]. */
    data class Charge(val source: String? = null) : Declencheur() {
        override val type get() = CHARGE
    }

    /** Batterie à [seuil] % ou moins, hors charge. */
    data class BatterieFaible(val seuil: Int = 20) : Declencheur() {
        override val type get() = BATTERIE
    }

    /** En charge et à [seuil] % ou plus. */
    data class BatteriePleine(val seuil: Int = 100) : Declencheur() {
        override val type get() = PLEINE
    }

    data object Economie : Declencheur() {
        override val type get() = ECONOMIE
    }

    // ---- Téléphone ----

    /** Téléphone verrouillé ([verrouille]) ou en cours d'utilisation. */
    data class Ecran(val verrouille: Boolean = true) : Declencheur() {
        override val type get() = ECRAN
    }

    data object NePasDeranger : Declencheur() {
        override val type get() = DND
    }

    /** Sonnerie coupée : [mode] = [SILENCIEUX], [VIBREUR], ou null pour les deux. */
    data class Sonnerie(val mode: String? = null) : Declencheur() {
        override val type get() = SONNERIE
    }

    data object Avion : Declencheur() {
        override val type get() = AVION
    }

    data object Lampe : Declencheur() {
        override val type get() = LAMPE
    }

    // ---- Connexions ----

    /** Casque ou écouteurs connectés (Bluetooth, filaire, USB) ; [appareil] = seulement celui-là. */
    data class Casque(val appareil: String? = null) : Declencheur() {
        override val type get() = CASQUE
    }

    /** Un appareil Bluetooth connecté (montre, voiture, enceinte…) ; [appareil] null = n'importe lequel. */
    data class Bluetooth(val appareil: String? = null) : Declencheur() {
        override val type get() = BLUETOOTH
    }

    /** Type de connexion Internet : [WIFI], [DONNEES] ou [AUCUN] (hors ligne). */
    data class Reseau(val type_: String = WIFI) : Declencheur() {
        override val type get() = RESEAU
    }

    companion object {
        const val PLAGE = "plage"
        const val A_HEURE = "aheure"
        const val REVEIL = "reveil"
        const val SOLEIL = "soleil"
        const val DATE = "date"
        const val ALARME = "alarme"
        const val CHARGE = "charge"
        const val BATTERIE = "batterie"
        const val PLEINE = "pleine"
        const val ECONOMIE = "economie"
        const val ECRAN = "ecran"
        const val DND = "dnd"
        const val SONNERIE = "sonnerie"
        const val AVION = "avion"
        const val LAMPE = "lampe"
        const val CASQUE = "casque"
        const val BLUETOOTH = "bluetooth"
        const val RESEAU = "reseau"

        const val CABLE = "cable"
        const val SANS_FIL = "sansfil"
        const val SILENCIEUX = "silencieux"
        const val VIBREUR = "vibreur"
        const val WIFI = "wifi"
        const val DONNEES = "donnees"
        const val AUCUN = "aucun"

        val TOUS_LES_JOURS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

/** Position de la ville choisie (lever et coucher du soleil). */
data class Lieu(val nom: String, val lat: Double, val lon: Double)

/** Ce que le téléphone vit en ce moment, lu par le moteur à chaque évènement. */
data class Contexte(
    val maintenant: LocalDateTime,
    val enCharge: Boolean = false,
    val batterie: Int = 80,
    /** Noms des casques / écouteurs connectés (vide = aucun). */
    val casques: List<String> = emptyList(),
    val nePasDeranger: Boolean = false,
    val sourceCharge: String? = null,
    val economie: Boolean = false,
    val verrouille: Boolean = false,
    /** [Declencheur.SILENCIEUX], [Declencheur.VIBREUR] ou null (sonnerie normale). */
    val sonnerie: String? = null,
    val avion: Boolean = false,
    val lampe: Boolean = false,
    val bluetooth: List<String> = emptyList(),
    /** [Declencheur.WIFI], [Declencheur.DONNEES] ou [Declencheur.AUCUN]. */
    val reseau: String = Declencheur.WIFI,
    val prochaineAlarme: LocalDateTime? = null,
    val lieu: Lieu? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** Évaluation des règles : fonctions pures, testées sans téléphone. */
object Evaluation {

    /** La règle « pendant » qui gagne maintenant : la première de la liste (ordre = priorité). */
    fun gagnante(regles: List<Regle>, ctx: Contexte): Regle? =
        regles.firstOrNull { it.active && !it.estMoment && vraie(it, ctx) }

    /** Toutes les conditions (ou au moins une) sont vraies. */
    fun vraie(r: Regle, ctx: Contexte): Boolean {
        if (r.conditions.isEmpty()) return false
        return if (r.toutes) r.conditions.all { vraie(it, ctx) } else r.conditions.any { vraie(it, ctx) }
    }

    /**
     * Une règle « moment » se déclenche-t-elle maintenant ? Le moment ([estLeMoment]) doit arriver ;
     * avec « toutes », les autres conditions doivent aussi être vraies.
     */
    fun momentPret(r: Regle, ctx: Contexte, estLeMoment: (Declencheur) -> Boolean): Boolean {
        val moments = r.conditions.filter { it.estMoment }
        if (moments.none(estLeMoment)) return false
        val autres = r.conditions.filterNot { it.estMoment }
        return !r.toutes || autres.all { vraie(it, ctx) }
    }

    fun vraie(d: Declencheur, ctx: Contexte): Boolean = when (d) {
        is Declencheur.Plage -> dansPlage(d, ctx.maintenant)
        is Declencheur.AHeure, is Declencheur.Reveil -> false // des moments, pas des états
        is Declencheur.Soleil -> nuitOuJour(d, ctx)
        is Declencheur.Date -> ctx.maintenant.dayOfMonth == d.jour && ctx.maintenant.monthValue == d.mois
        is Declencheur.AvantAlarme -> ctx.prochaineAlarme?.let { a ->
            !ctx.maintenant.isBefore(a.minusMinutes(d.minutes.toLong())) && ctx.maintenant.isBefore(a)
        } ?: false
        is Declencheur.Charge -> ctx.enCharge && (d.source == null || d.source == ctx.sourceCharge)
        is Declencheur.BatterieFaible -> !ctx.enCharge && ctx.batterie <= d.seuil
        is Declencheur.BatteriePleine -> ctx.enCharge && ctx.batterie >= d.seuil
        Declencheur.Economie -> ctx.economie
        is Declencheur.Ecran -> ctx.verrouille == d.verrouille
        Declencheur.NePasDeranger -> ctx.nePasDeranger
        is Declencheur.Sonnerie -> if (d.mode == null) ctx.sonnerie != null else ctx.sonnerie == d.mode
        Declencheur.Avion -> ctx.avion
        Declencheur.Lampe -> ctx.lampe
        is Declencheur.Casque -> contient(ctx.casques, d.appareil)
        is Declencheur.Bluetooth -> contient(ctx.bluetooth, d.appareil)
        is Declencheur.Reseau -> ctx.reseau == d.type_
    }

    private fun contient(liste: List<String>, nom: String?) =
        nom?.let { n -> liste.any { it.equals(n, ignoreCase = true) } } ?: liste.isNotEmpty()

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

    private fun nuitOuJour(d: Declencheur.Soleil, ctx: Contexte): Boolean {
        val lieu = ctx.lieu ?: return false
        val (lever, coucher) = Soleil.leverCoucher(ctx.maintenant.toLocalDate(), lieu, ctx.zone) ?: return false
        val t = ctx.maintenant
        val dec = d.decalage.toLong()
        // Décalage : la nuit commence [dec] min après le coucher et finit [dec] min avant le lever (et l'inverse pour le jour).
        val jour = !t.isBefore(lever.plusMinutes(if (d.nuit) -dec else dec)) && t.isBefore(coucher.plusMinutes(if (d.nuit) dec else -dec))
        return if (d.nuit) !jour else jour
    }

    /**
     * Prochaine minute où une règle peut changer d'état (début ou fin de plage, heure précise,
     * lever / coucher du soleil, minuit, alarme), pour programmer une alarme. Null = aucune.
     */
    fun prochaineFrontiere(regles: List<Regle>, ctx: Contexte): LocalDateTime? {
        val t = ctx.maintenant.withSecond(0).withNano(0)
        val candidats = mutableListOf<LocalDateTime>()
        fun aLaMinute(m: Int) {
            val aujourdHui = t.toLocalDate().atStartOfDay().plusMinutes(m.toLong())
            candidats += if (aujourdHui.isAfter(t)) aujourdHui else aujourdHui.plusDays(1)
        }
        val minuit = t.toLocalDate().plusDays(1).atStartOfDay()
        for (r in regles.filter { it.active }) for (d in r.conditions) when (d) {
            is Declencheur.Plage -> if (d.debut == d.fin) candidats += minuit else { aLaMinute(d.debut); aLaMinute(d.fin) }
            is Declencheur.AHeure -> aLaMinute(d.minute)
            is Declencheur.Date -> candidats += minuit
            is Declencheur.Soleil -> ctx.lieu?.let { lieu ->
                for (jour in listOf(t.toLocalDate(), t.toLocalDate().plusDays(1))) {
                    Soleil.leverCoucher(jour, lieu, ctx.zone)?.let { (l, c) ->
                        val dec = d.decalage.toLong()
                        listOf(l.plusMinutes(dec), l.minusMinutes(dec), c.plusMinutes(dec), c.minusMinutes(dec))
                            .map { it.withSecond(0).withNano(0).plusMinutes(1) }
                            .filter { it.isAfter(t) }
                            .let { candidats += it }
                    }
                }
            }
            is Declencheur.AvantAlarme -> ctx.prochaineAlarme?.let { a ->
                listOf(a.minusMinutes(d.minutes.toLong()), a).filter { it.isAfter(t) }.let { candidats += it }
            }
            else -> Unit
        }
        return candidats.minOrNull()
    }
}

/**
 * Lever et coucher du soleil (algorithme de la NOAA simplifié, à ±2 min près), sans Internet.
 */
object Soleil {
    /** (lever, coucher) en heure locale, ou null s'il ne se lève / couche pas (pôles). */
    fun leverCoucher(date: LocalDate, lieu: Lieu, zone: ZoneId): Pair<LocalDateTime, LocalDateTime>? {
        val n = date.dayOfYear
        val gamma = 2 * Math.PI / 365.0 * (n - 1)
        val eqTemps = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val lat = Math.toRadians(lieu.lat)
        val cosH = (cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl))) - Math.tan(lat) * Math.tan(decl)
        if (cosH < -1 || cosH > 1) return null
        val h = Math.toDegrees(acos(cosH))
        val leverUtc = 720 - 4 * (lieu.lon + h) - eqTemps
        val coucherUtc = 720 - 4 * (lieu.lon - h) - eqTemps
        fun local(minUtc: Double): LocalDateTime {
            val m = floor(minUtc * 60).roundToLong()
            return date.atStartOfDay(ZoneId.of("UTC")).plusSeconds(m).withZoneSameInstant(zone).toLocalDateTime()
        }
        return local(leverUtc) to local(coucherUtc)
    }

    /** Heure seule, pour l'affichage. */
    fun heures(date: LocalDate, lieu: Lieu, zone: ZoneId): Pair<LocalTime, LocalTime>? =
        leverCoucher(date, lieu, zone)?.let { it.first.toLocalTime() to it.second.toLocalTime() }
}

/**
 * Ce qui doit s'afficher, selon l'option A choisie par Nico : une règle s'applique quand elle
 * commence ; un choix à la main tient jusqu'au prochain changement automatique.
 *
 * [Memoire.enCours] : règle « pendant » appliquée en ce moment. [Memoire.base] : le modèle à remettre
 * quand plus aucune règle ne s'applique (celui d'avant, ou le dernier choix fait à la main pendant la règle).
 */
object Decision {
    data class EnCours(val signature: String, val modele: String?, val revenir: Boolean)
    data class Memoire(val enCours: EnCours? = null, val base: String? = null)

    /** [afficher] non nul = mettre ce modèle (sa valeur interne null = éteindre). */
    data class Resultat(val memoire: Memoire, val afficher: Cible?)
    data class Cible(val modele: String?)

    /**
     * La règle gagnante vaut maintenant [gagnante] (null = aucune), qui afficherait [cible] si elle commence ;
     * [affiche] est le modèle actuellement à l'écran. Rien ne bouge tant que la gagnante ne change pas :
     * c'est ce qui laisse tenir un choix fait à la main.
     */
    fun evaluer(m: Memoire, gagnante: Regle?, cible: String?, affiche: String?): Resultat {
        val signature = gagnante?.signature
        if (signature == m.enCours?.signature) return Resultat(m, null)
        if (gagnante != null) {
            // Aucune règle avant : ce qui était affiché devient le modèle à remettre ensuite.
            val base = if (m.enCours == null) affiche else m.base
            val afficher = if (cible == affiche) null else Cible(cible)
            return Resultat(Memoire(EnCours(signature!!, cible, gagnante.revenir), base), afficher)
        }
        val fini = m.enCours!!
        if (!fini.revenir) return Resultat(Memoire(null, affiche), null)
        val afficher = if (m.base == affiche) null else Cible(m.base)
        return Resultat(Memoire(null, m.base), afficher)
    }

    /** Raccourci pour une règle qui affiche un modèle fixe. */
    fun evaluer(m: Memoire, gagnante: Regle?, affiche: String?): Resultat =
        evaluer(m, gagnante, (gagnante?.action as? Action.Afficher)?.modele, affiche)

    /** Choix fait à la main ([modele] null = éteint) : pendant une règle, c'est lui qu'on remettra à la fin. */
    fun choixManuel(m: Memoire, modele: String?): Memoire =
        if (m.enCours != null) m.copy(base = modele) else m

    /** Une règle « moment » se déclenche : on affiche, et c'est le nouveau modèle de base. */
    fun moment(m: Memoire, modele: String?, affiche: String?): Resultat {
        val memoire = if (m.enCours != null) m.copy(base = modele) else m
        return Resultat(memoire, if (modele == affiche) null else Cible(modele))
    }

    /** Défilement des favoris pendant une règle : le modèle change, la règle reste la même. */
    fun defiler(m: Memoire, modele: String?, affiche: String?): Resultat {
        val enCours = m.enCours ?: return Resultat(m, null)
        return Resultat(m.copy(enCours = enCours.copy(modele = modele)), if (modele == affiche) null else Cible(modele))
    }
}
