package fr.niakimbo.verso.auto

import java.time.DayOfWeek
import java.time.ZoneId

/** Les quatre familles du catalogue « Si… ». */
enum class Famille(val titre: String) { TEMPS("Temps"), BATTERIE("Batterie"), TELEPHONE("Téléphone"), CONNEXIONS("Connexions") }

/** Chaque déclencheur proposé dans le catalogue, avec son picto dot-matrix 9×9. */
enum class TypeQuand(val cle: String, val famille: Famille, val titre: String, val sousTitre: String, val picto: String) {
    HORAIRE(
        Declencheur.PLAGE, Famille.TEMPS, "Horaire", "De telle heure à telle heure",
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
    A_HEURE(
        Declencheur.A_HEURE, Famille.TEMPS, "À une heure", "Pile à l'heure choisie",
        """
        ....X....
        ...XXX...
        ..XXXXX..
        ..XXXXX..
        ..XXXXX..
        .XXXXXXX.
        XXXXXXXXX
        .........
        ....X....
        """,
    ),
    REVEIL(
        Declencheur.REVEIL, Famille.TEMPS, "Au réveil", "1er déverrouillage du matin",
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
    SOLEIL(
        Declencheur.SOLEIL, Famille.TEMPS, "Nuit / jour", "Suit le coucher du soleil",
        """
        ..XXXX...
        .XXX.....
        XXX....X.
        XXX......
        XXX...X..
        XXX......
        .XXX.....
        ..XXXX...
        .........
        """,
    ),
    DATE(
        Declencheur.DATE, Famille.TEMPS, "Une date", "Anniversaire, fête…",
        """
        .X.....X.
        XXXXXXXXX
        X.......X
        XXXXXXXXX
        X.X.X.X.X
        X.......X
        X.X.X.X.X
        X.......X
        XXXXXXXXX
        """,
    ),
    ALARME(
        Declencheur.ALARME, Famille.TEMPS, "Avant l'alarme", "Juste avant ton réveil",
        """
        X.......X
        .X.XXX.X.
        ..X...X..
        .X..X..X.
        .X..X..X.
        .X...X.X.
        ..X...X..
        ...XXX...
        .X.....X.
        """,
    ),
    CHARGE(
        Declencheur.CHARGE, Famille.BATTERIE, "En charge", "Câble ou sans fil",
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
        Declencheur.BATTERIE, Famille.BATTERIE, "Batterie faible", "Sous un seuil",
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
    PLEINE(
        Declencheur.PLEINE, Famille.BATTERIE, "Batterie pleine", "En charge, presque à 100 %",
        """
        .........
        .........
        XXXXXXXX.
        XXXXXXXXX
        XXXXXXXXX
        XXXXXXXXX
        XXXXXXXX.
        .........
        .........
        """,
    ),
    ECONOMIE(
        Declencheur.ECONOMIE, Famille.BATTERIE, "Économie", "Économiseur de batterie",
        """
        ......XXX
        ....XXXXX
        ...XXXX.X
        ..XXX..XX
        ..XX..XX.
        ..X..XXX.
        .X.XXXX..
        X........
        .........
        """,
    ),
    ECRAN(
        Declencheur.ECRAN, Famille.TELEPHONE, "Verrouillé", "Ou déverrouillé",
        """
        ...XXX...
        ..X...X..
        ..X...X..
        .XXXXXXX.
        .XXXXXXX.
        .XXX.XXX.
        .XXX.XXX.
        .XXXXXXX.
        .........
        """,
    ),
    DND(
        Declencheur.DND, Famille.TELEPHONE, "Ne pas déranger", "Mode activé",
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
    SONNERIE(
        Declencheur.SONNERIE, Famille.TELEPHONE, "Silencieux", "Ou vibreur",
        """
        X...X....
        .X.XXX...
        ..XXXXX..
        ..XXXXX..
        ..XXXXX..
        .XXXXXXX.
        XXXXXXXXX
        .......X.
        ....X...X
        """,
    ),
    AVION(
        Declencheur.AVION, Famille.TELEPHONE, "Mode avion", "Mode avion activé",
        """
        ....X....
        ....X....
        ...XXX...
        .XXXXXXX.
        XXXXXXXXX
        ....X....
        ....X....
        ...XXX...
        ..XXXXX..
        """,
    ),
    LAMPE(
        Declencheur.LAMPE, Famille.TELEPHONE, "Lampe", "Lampe torche allumée",
        """
        XXXXXXXXX
        XXXXXXXXX
        .XXXXXXX.
        ..XXXXX..
        ..XX.XX..
        ..XXXXX..
        ..XXXXX..
        ..XXXXX..
        ..XXXXX..
        """,
    ),
    CASQUE(
        Declencheur.CASQUE, Famille.CONNEXIONS, "Casque", "Écouteurs connectés",
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
    BLUETOOTH(
        Declencheur.BLUETOOTH, Famille.CONNEXIONS, "Bluetooth", "Montre, voiture, enceinte…",
        """
        ....X....
        ....XX...
        .X..X.X..
        ..X.X.X..
        ...XXX...
        ..X.X.X..
        .X..X.X..
        ....XX...
        ....X....
        """,
    ),
    RESEAU(
        Declencheur.RESEAU, Famille.CONNEXIONS, "Réseau", "Wi-Fi, données, hors ligne",
        """
        .........
        .XXXXXXX.
        X.......X
        ..XXXXX..
        .X.....X.
        ...XXX...
        ..X...X..
        .........
        ....X....
        """,
    ),
    ;

    /** Points allumés du picto, ligne par ligne. */
    val points: List<String> = picto.trimIndent().lines().map { it.trim() }

    /** Déclencheur par défaut quand on choisit ce type. */
    fun parDefaut(): Declencheur = when (this) {
        HORAIRE -> Declencheur.Plage(22 * 60, 7 * 60)
        A_HEURE -> Declencheur.AHeure()
        REVEIL -> Declencheur.Reveil()
        SOLEIL -> Declencheur.Soleil()
        DATE -> java.time.LocalDate.now().let { Declencheur.Date(it.dayOfMonth, it.monthValue) }
        ALARME -> Declencheur.AvantAlarme()
        CHARGE -> Declencheur.Charge()
        BATTERIE -> Declencheur.BatterieFaible()
        PLEINE -> Declencheur.BatteriePleine()
        ECONOMIE -> Declencheur.Economie
        ECRAN -> Declencheur.Ecran()
        DND -> Declencheur.NePasDeranger
        SONNERIE -> Declencheur.Sonnerie()
        AVION -> Declencheur.Avion
        LAMPE -> Declencheur.Lampe
        CASQUE -> Declencheur.Casque()
        BLUETOOTH -> Declencheur.Bluetooth()
        RESEAU -> Declencheur.Reseau()
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
    val semaine = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val weekEnd = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val mois = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    fun nomMois(m: Int) = mois[(m - 1).coerceIn(0, 11)]

    fun jours(j: Set<DayOfWeek>): String = when (j) {
        Declencheur.TOUS_LES_JOURS -> "tous les jours"
        semaine -> "en semaine"
        weekEnd -> "le week-end"
        else -> j.sorted().joinToString(", ") { abrev.getValue(it) }
    }

    private fun decalage(min: Int) = when {
        min == 0 -> ""
        min > 0 -> " (+$min min)"
        else -> " ($min min)"
    }

    /** Une condition en quelques mots, pour la phrase résumé et les cartes. */
    fun quand(d: Declencheur): String = when (d) {
        is Declencheur.Plage ->
            (if (d.debut == d.fin) "toute la journée" else "de ${heure(d.debut)} à ${heure(d.fin)}") +
                if (d.jours != Declencheur.TOUS_LES_JOURS) " (${jours(d.jours)})" else ""
        is Declencheur.AHeure -> "à ${heure(d.minute)}" + if (d.jours != Declencheur.TOUS_LES_JOURS) " (${jours(d.jours)})" else ""
        is Declencheur.Reveil -> "au réveil"
        is Declencheur.Soleil -> (if (d.nuit) "la nuit" else "le jour") + decalage(d.decalage)
        is Declencheur.Date -> "le ${d.jour} ${nomMois(d.mois)}"
        is Declencheur.AvantAlarme -> "${d.minutes} min avant l'alarme"
        is Declencheur.Charge -> when (d.source) {
            Declencheur.CABLE -> "en charge par câble"
            Declencheur.SANS_FIL -> "en charge sans fil"
            else -> "en charge"
        }
        is Declencheur.BatterieFaible -> "batterie ≤ ${d.seuil} %"
        is Declencheur.BatteriePleine -> "batterie ≥ ${d.seuil} %"
        Declencheur.Economie -> "en économie de batterie"
        is Declencheur.Ecran -> if (d.verrouille) "verrouillé" else "déverrouillé"
        Declencheur.NePasDeranger -> "en Ne pas déranger"
        is Declencheur.Sonnerie -> when (d.mode) {
            Declencheur.SILENCIEUX -> "en silencieux"
            Declencheur.VIBREUR -> "en vibreur"
            else -> "en silencieux ou vibreur"
        }
        Declencheur.Avion -> "en mode avion"
        Declencheur.Lampe -> "lampe allumée"
        is Declencheur.Casque -> d.appareil?.let { "« $it » branché" } ?: "casque branché"
        is Declencheur.Bluetooth -> d.appareil?.let { "« $it » connecté" } ?: "un appareil Bluetooth connecté"
        is Declencheur.Reseau -> when (d.type_) {
            Declencheur.DONNEES -> "sur données mobiles"
            Declencheur.AUCUN -> "hors ligne"
            else -> "sur Wi-Fi"
        }
    }

    /** Toutes les conditions d'une règle, reliées par « et » / « ou ». */
    fun conditions(r: Regle): String {
        val mots = r.conditions.map(::quand)
        if (mots.size <= 1) return mots.firstOrNull().orEmpty()
        val lien = if (r.toutes) " et " else " ou "
        return mots.dropLast(1).joinToString(", ") + lien + mots.last()
    }

    /** Ce que fait la règle. [nom] donne le nom lisible d'un modèle. */
    fun alors(a: Action, nom: (String) -> String): String = when (a) {
        is Action.Afficher -> nom(a.modele)
        Action.Eteindre -> "matrice éteinte"
        Action.Hasard -> "un favori au hasard"
        is Action.Defiler -> "les favoris toutes les ${a.minutes} min"
    }

    /** « Si la nuit et en charge, afficher Lune. » */
    fun phrase(r: Regle, nom: (String) -> String): String {
        val si = conditions(r).replaceFirstChar { it.uppercase() }
        val alors = when (r.action) {
            Action.Eteindre -> "éteindre la matrice"
            is Action.Defiler -> "faire défiler les favoris (toutes les ${(r.action as Action.Defiler).minutes} min)"
            else -> "afficher " + alors(r.action, nom)
        }
        return "Si $si, $alors."
    }

    /** Lever et coucher du soleil aujourd'hui, pour la ville choisie. */
    fun soleil(lieu: Lieu?): String {
        lieu ?: return "Choisis ta ville pour connaître le coucher du soleil."
        val h = Soleil.heures(java.time.LocalDate.now(), lieu, ZoneId.systemDefault()) ?: return lieu.nom
        return "${lieu.nom} · lever ${"%02d:%02d".format(h.first.hour, h.first.minute)} · coucher ${"%02d:%02d".format(h.second.hour, h.second.minute)}"
    }
}

/** Règles toutes faites, ajoutées d'un tap depuis la liste. */
data class Recette(val titre: String, val nom: String, val regle: (String) -> Regle)

object Recettes {
    private fun r(titre: String, nom: String, vararg si: Declencheur, action: Action, toutes: Boolean = true) =
        Recette(titre, nom) { id -> Regle(id, nom, si.toList(), action, toutes) }

    val toutes = listOf(
        r("La nuit, une lune", "Nuit", Declencheur.Soleil(nuit = true), action = Action.Afficher("lune")),
        r("Au réveil, un soleil", "Réveil", Declencheur.Reveil(), action = Action.Afficher("soleil")),
        r("En charge, la barre de chargement", "En charge", Declencheur.Charge(), action = Action.Afficher("chargement")),
        r("Batterie faible, l'icône batterie", "Batterie faible", Declencheur.BatterieFaible(20), action = Action.Afficher("batterie")),
        r("Casque branché, l'égaliseur", "Casque", Declencheur.Casque(), action = Action.Afficher("egaliseur")),
        r("Ne pas déranger, dodo", "Ne pas déranger", Declencheur.NePasDeranger, action = Action.Afficher("dodo")),
        r("La nuit en charge, matrice éteinte", "Dodo en charge", Declencheur.Plage(23 * 60, 7 * 60), Declencheur.Charge(), action = Action.Eteindre),
        r("Le week-end, les favoris défilent", "Week-end", Declencheur.Plage(0, 0, Textes.weekEnd), action = Action.Defiler(20)),
        r("Halloween, une citrouille", "Halloween", Declencheur.Date(31, 10), action = Action.Afficher("citrouille")),
        r("Noël, un sapin", "Noël", Declencheur.Date(25, 12), action = Action.Afficher("sapin")),
        r("Hors ligne, l'icône Wi-Fi", "Hors ligne", Declencheur.Reseau(Declencheur.AUCUN), action = Action.Afficher("wifi")),
        r("Lampe allumée, matrice éteinte", "Lampe", Declencheur.Lampe, action = Action.Eteindre),
    )

    /** Déjà ajoutée : une règle avec les mêmes conditions et la même action existe. */
    fun dejaLa(r: Recette, regles: List<Regle>): Boolean {
        val modele = r.regle("x")
        return regles.any { it.conditions == modele.conditions && it.action == modele.action }
    }
}
