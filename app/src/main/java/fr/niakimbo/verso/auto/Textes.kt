package fr.niakimbo.verso.auto

import java.time.DayOfWeek

/** Les six familles de déclencheurs proposées dans l'éditeur, avec leur picto dot-matrix. */
enum class TypeQuand(val cle: String, val titre: String, val sousTitre: String, val picto: String) {
    HORAIRE(
        Declencheur.PLAGE, "Horaire", "Une plage d'heures",
        """
        ..XXXXX..
        .X.....X.
        X...X...X
        X...X...X
        X...XXX.X
        X.......X
        X.......X
        .X.....X.
        ..XXXXX..
        """,
    ),
    REVEIL(
        Declencheur.REVEIL, "Au réveil", "1er déverrouillage du matin",
        """
        ....X....
        .X.....X.
        ...XXX...
        ..XXXXX..
        XXXXXXXXX
        .........
        .XXXXXXX.
        .........
        ...XXX...
        """,
    ),
    CHARGE(
        Declencheur.CHARGE, "En charge", "Câble ou socle branché",
        """
        .....XX..
        ....XX...
        ...XX....
        ..XXXXXX.
        .XXXXXX..
        ....XX...
        ...XX....
        ..XX.....
        .........
        """,
    ),
    BATTERIE(
        Declencheur.BATTERIE, "Batterie faible", "Sous un seuil, hors charge",
        """
        .........
        .........
        XXXXXXXX.
        X......XX
        XX.....XX
        XX.....XX
        X......X.
        XXXXXXXX.
        .........
        """,
    ),
    CASQUE(
        Declencheur.CASQUE, "Casque", "Écouteurs Bluetooth ou filaires",
        """
        ..XXXXX..
        .X.....X.
        X.......X
        X.......X
        XX.....XX
        XXX...XXX
        XXX...XXX
        XX.....XX
        .........
        """,
    ),
    DND(
        Declencheur.DND, "Ne pas déranger", "Mode Ne pas déranger activé",
        """
        ..XXXXX..
        .XXXXXXX.
        XXXXXXXXX
        XXXXXXXXX
        X.......X
        XXXXXXXXX
        XXXXXXXXX
        .XXXXXXX.
        ..XXXXX..
        """,
    ),
    ;

    /** Points allumés du picto, ligne par ligne. */
    val points: List<String> = picto.trimIndent().lines().map { it.trim() }

    /** Déclencheur par défaut quand on choisit cette famille. */
    fun parDefaut(): Declencheur = when (this) {
        HORAIRE -> Declencheur.Plage(22 * 60, 7 * 60)
        REVEIL -> Declencheur.Reveil()
        CHARGE -> Declencheur.Charge
        BATTERIE -> Declencheur.BatterieFaible()
        CASQUE -> Declencheur.Casque()
        DND -> Declencheur.NePasDeranger
    }

    companion object {
        fun de(d: Declencheur): TypeQuand = entries.first { it.cle == d.type }
    }
}

/** Phrases affichées à Nico : toujours en français simple. */
object Textes {
    fun heure(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    private val abrev = mapOf(
        DayOfWeek.MONDAY to "lun", DayOfWeek.TUESDAY to "mar", DayOfWeek.WEDNESDAY to "mer",
        DayOfWeek.THURSDAY to "jeu", DayOfWeek.FRIDAY to "ven", DayOfWeek.SATURDAY to "sam", DayOfWeek.SUNDAY to "dim",
    )
    val lettres = listOf(
        DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "M", DayOfWeek.THURSDAY to "J",
        DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D",
    )
    private val semaine = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val weekEnd = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun jours(j: Set<DayOfWeek>): String = when (j) {
        Declencheur.TOUS_LES_JOURS -> "tous les jours"
        semaine -> "en semaine"
        weekEnd -> "le week-end"
        else -> j.sorted().joinToString(", ") { abrev.getValue(it) }
    }

    /** « De 22:00 à 07:00 · tous les jours », « En charge »… */
    fun quand(d: Declencheur): String = when (d) {
        is Declencheur.Plage ->
            (if (d.debut == d.fin) "Toute la journée" else "De ${heure(d.debut)} à ${heure(d.fin)}") + " · " + jours(d.jours)
        is Declencheur.Reveil -> "Au réveil (entre ${heure(d.debut)} et ${heure(d.fin)})"
        Declencheur.Charge -> "En charge"
        is Declencheur.BatterieFaible -> "Batterie à ${d.seuil} % ou moins"
        is Declencheur.Casque -> d.appareil?.let { "« $it » connecté" } ?: "Casque ou écouteurs connectés"
        Declencheur.NePasDeranger -> "Ne pas déranger activé"
    }
}

/** Règles toutes faites, ajoutées d'un tap depuis la liste. */
data class Recette(val titre: String, val nom: String, val quand: Declencheur, val modele: String)

object Recettes {
    val toutes = listOf(
        Recette("La nuit, une lune", "Nuit", Declencheur.Plage(22 * 60, 7 * 60), "lune"),
        Recette("Au réveil, un soleil", "Réveil", Declencheur.Reveil(), "soleil"),
        Recette("En charge, la barre de chargement", "En charge", Declencheur.Charge, "chargement"),
        Recette("Batterie faible, l'icône batterie", "Batterie faible", Declencheur.BatterieFaible(20), "batterie"),
        Recette("Casque branché, l'égaliseur", "Casque", Declencheur.Casque(), "egaliseur"),
        Recette("Ne pas déranger, dodo", "Ne pas déranger", Declencheur.NePasDeranger, "dodo"),
    )

    /** Déjà ajoutée : une règle avec le même déclencheur et le même modèle existe. */
    fun dejaLa(r: Recette, regles: List<Regle>) = regles.any { it.quand == r.quand && it.modele == r.modele }
}
