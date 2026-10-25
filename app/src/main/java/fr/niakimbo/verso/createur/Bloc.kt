package fr.niakimbo.verso.createur

import fr.niakimbo.verso.matrix.MatrixFrame
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un bloc d'un modèle dynamique : un rectangle de la matrice ([x], [y], [l] × [h] LEDs)
 * qui affiche une information calculée en direct (heure, date, batterie…).
 *
 * Les blocs sont dessinés dans l'ordre de la liste : un bloc plus bas passe par-dessus
 * (une LED garde la valeur la plus lumineuse des blocs qui la couvrent).
 */
data class Bloc(
    val type: TypeBloc,
    val x: Int,
    val y: Int,
    val l: Int,
    val h: Int,
    /** Luminosité 1-9, comme les 9 niveaux de l'éditeur dessin. */
    val niveau: Int = 9,
    val options: Map<String, String> = emptyMap(),
) {
    /** Valeur d'une option, ou son défaut. */
    fun option(o: OptionBloc): String = options[o.cle]?.takeIf { o.accepte(it) } ?: o.defaut

    fun avec(o: OptionBloc, valeur: String) = copy(options = options + (o.cle to valeur))

    /** Garde le bloc dans la grille (au moins une LED visible). */
    fun borne(): Bloc {
        val nl = l.coerceIn(1, MatrixFrame.SIDE)
        val nh = h.coerceIn(1, MatrixFrame.SIDE)
        return copy(
            l = nl, h = nh,
            x = x.coerceIn(0, MatrixFrame.SIDE - nl),
            y = y.coerceIn(0, MatrixFrame.SIDE - nh),
            niveau = niveau.coerceIn(1, 9),
        )
    }

    companion object {
        /** Nouveau bloc d'un type, avec une place et une taille qui lui conviennent. */
        fun nouveau(type: TypeBloc): Bloc = when (type) {
            TypeBloc.HEURE -> Bloc(type, 3, 1, 7, 11)
            TypeBloc.DATE -> Bloc(type, 3, 4, 7, 5)
            TypeBloc.BATTERIE -> Bloc(type, 0, 0, 13, 13, niveau = 5)
            TypeBloc.SECONDES -> Bloc(type, 0, 0, 13, 13, niveau = 6)
            TypeBloc.TEXTE -> Bloc(type, 0, 4, 13, 5)
            TypeBloc.DESSIN -> Bloc(type, 0, 0, 13, 13)
            TypeBloc.EFFET -> Bloc(type, 0, 0, 13, 13, niveau = 4)
            TypeBloc.MINUTEUR -> Bloc(type, 3, 4, 7, 5)
        }
    }
}

/** Une option d'un type de bloc, décrite une fois : l'éditeur génère ses contrôles à partir d'ici. */
sealed class OptionBloc(val cle: String, val titre: String, val description: String, val defaut: String) {
    abstract fun accepte(valeur: String): Boolean

    /** Choix parmi quelques valeurs : (valeur stockée, libellé). */
    class Choix(cle: String, titre: String, description: String, val choix: List<Pair<String, String>>, defaut: String) :
        OptionBloc(cle, titre, description, defaut) {
        override fun accepte(valeur: String) = choix.any { it.first == valeur }
        fun libelle(valeur: String) = choix.firstOrNull { it.first == valeur }?.second ?: valeur
    }

    /** Texte libre (texte défilant, heure du compte à rebours). */
    class Texte(cle: String, titre: String, description: String, defaut: String, val longueurMax: Int, val filtre: Regex? = null) :
        OptionBloc(cle, titre, description, defaut) {
        override fun accepte(valeur: String) = valeur.length <= longueurMax && (filtre?.matches(valeur) ?: true)
    }

    /** Un autre modèle de la bibliothèque (dessin fixe, effet animé). */
    class Modele(cle: String, titre: String, description: String, defaut: String, val animes: Boolean) :
        OptionBloc(cle, titre, description, defaut) {
        override fun accepte(valeur: String) = valeur.isNotBlank()
    }
}

private val OUI_NON = listOf("oui" to "Oui", "non" to "Non")

enum class TypeBloc(val json: String, val nom: String, val symbole: String, val description: String) {
    HEURE("heure", "Heure", "◷", "L'heure réelle, en chiffres ou à aiguilles."),
    DATE("date", "Date", "▦", "Le jour du mois, avec le mois, ou le jour de la semaine."),
    BATTERIE("batterie", "Batterie", "▮", "Le niveau de batterie : anneau, barre ou pourcentage."),
    SECONDES("secondes", "Secondes", "◔", "Un anneau qui fait le tour en une minute."),
    TEXTE("texte", "Texte", "Aa", "Un texte qui défile."),
    DESSIN("dessin", "Dessin", "✎", "La première image d'un modèle (un de tes dessins, par exemple)."),
    EFFET("effet", "Effet", "≈", "Une animation reprise d'un modèle (pluie, onde…)."),
    MINUTEUR("minuteur", "Compte à rebours", "⧗", "Les minutes qui restent avant une heure donnée.");

    val options: List<OptionBloc>
        get() = when (this) {
            HEURE -> listOf(Options.STYLE_HEURE, Options.AFFICHER, Options.FORMAT, Options.CADRAN)
            DATE -> listOf(Options.FORMAT_DATE)
            BATTERIE -> listOf(Options.STYLE_BATTERIE, Options.PISTE)
            SECONDES -> listOf(Options.STYLE_SECONDES, Options.PISTE)
            TEXTE -> listOf(Options.TEXTE, Options.VITESSE_TEXTE, Options.TAILLE)
            DESSIN -> listOf(Options.MODELE_DESSIN, Options.AJUSTER)
            EFFET -> listOf(Options.MODELE_EFFET, Options.AJUSTER, Options.VITESSE_EFFET)
            MINUTEUR -> listOf(Options.CIBLE)
        }

    /** Options sans effet avec les autres réglages du bloc (ex. format 24 h pour des aiguilles) : masquées. */
    fun optionsUtiles(b: Bloc): List<OptionBloc> = options.filter { o ->
        when (o) {
            Options.AFFICHER, Options.FORMAT -> b.option(Options.STYLE_HEURE) == "chiffres"
            Options.CADRAN -> b.option(Options.STYLE_HEURE) == "aiguilles"
            Options.PISTE -> this != BATTERIE || b.option(Options.STYLE_BATTERIE) != "chiffre"
            else -> true
        }
    }

    companion object {
        fun fromJson(v: String): TypeBloc =
            entries.firstOrNull { it.json == v } ?: throw IllegalArgumentException("type de bloc inconnu « $v »")
    }
}

/** Toutes les options des blocs (clés stables : elles sont écrites dans les fichiers JSON). */
object Options {
    val STYLE_HEURE = OptionBloc.Choix(
        "style", "Style", "Chiffres (3×5) ou horloge à aiguilles dessinée dans le bloc.",
        listOf("chiffres" to "Chiffres", "aiguilles" to "Aiguilles"), "chiffres",
    )
    val AFFICHER = OptionBloc.Choix(
        "afficher", "Afficher", "Heures et minutes (sur deux lignes, 11 LEDs de haut), ou seulement l'un des deux (5 de haut).",
        listOf("hhmm" to "HH + MM", "hh" to "Heures", "mm" to "Minutes"), "hhmm",
    )
    val FORMAT = OptionBloc.Choix(
        "format", "Format", "24 h (14) ou 12 h (02).",
        listOf("24" to "24 h", "12" to "12 h"), "24",
    )
    val CADRAN = OptionBloc.Choix(
        "cadran", "Cadran", "Cercle discret et repères 12-3-6-9 autour des aiguilles.",
        OUI_NON, "oui",
    )
    val FORMAT_DATE = OptionBloc.Choix(
        "format", "Format", "Jour du mois (25), jour + mois sur deux lignes, ou jour de la semaine (DI).",
        listOf("jour" to "Jour", "jourmois" to "Jour + mois", "semaine" to "Semaine"), "jour",
    )
    val STYLE_BATTERIE = OptionBloc.Choix(
        "style", "Style", "Anneau qui se remplit, barre horizontale, ou pourcentage en chiffres.",
        listOf("anneau" to "Anneau", "barre" to "Barre", "chiffre" to "Chiffres"), "anneau",
    )
    val STYLE_SECONDES = OptionBloc.Choix(
        "style", "Style", "Anneau qui se remplit en une minute, ou un seul point qui tourne.",
        listOf("anneau" to "Anneau", "point" to "Point"), "anneau",
    )
    val PISTE = OptionBloc.Choix(
        "piste", "Piste", "Montre aussi la partie vide, très faiblement.",
        OUI_NON, "oui",
    )
    val TEXTE = OptionBloc.Texte(
        "texte", "Texte", "Lettres sans accents, chiffres et ponctuation simple (♥ et ★ marchent aussi).",
        "VERSO", longueurMax = 60,
    )
    val VITESSE_TEXTE = OptionBloc.Choix(
        "vitesse", "Vitesse", "Vitesse de défilement.",
        listOf("lent" to "Lent", "normal" to "Normal", "rapide" to "Rapide"), "normal",
    )
    val TAILLE = OptionBloc.Choix(
        "taille", "Taille", "Petite (5 LEDs de haut) ou grande (lettres doublées, 10 de haut).",
        listOf("petite" to "Petite", "grande" to "Grande"), "petite",
    )
    val MODELE_DESSIN = OptionBloc.Modele(
        "modele", "Modèle", "La première image de ce modèle est affichée dans le bloc.",
        "smiley", animes = false,
    )
    val MODELE_EFFET = OptionBloc.Modele(
        "modele", "Modèle", "Cette animation tourne dans le bloc.",
        "pluie", animes = true,
    )
    val AJUSTER = OptionBloc.Choix(
        "ajuster", "Ajuster", "Réduire : tout le dessin tient dans le bloc. Découper : le dessin reste à sa place et seul ce qui est dans le bloc se voit.",
        listOf("reduire" to "Réduire", "decouper" to "Découper"), "reduire",
    )
    val VITESSE_EFFET = OptionBloc.Choix(
        "vitesse", "Vitesse", "Vitesse de l'animation.",
        listOf("0.5" to "×0,5", "1" to "×1", "2" to "×2"), "1",
    )
    val CIBLE = OptionBloc.Texte(
        "cible", "Heure visée", "Format HH:MM (ex. 18:00). Le bloc montre les minutes qui restent, ou H:MM sur deux lignes au-delà d'une heure.",
        "18:00", longueurMax = 5, filtre = Regex("([01]?\\d|2[0-3]):[0-5]\\d"),
    )
}

/** Un modèle dynamique fait de blocs, tel qu'enregistré (VERSO_SPEC §3.5). */
data class DefinitionBlocs(
    val id: String,
    val nom: String,
    val categorie: String,
    val blocs: List<Bloc>,
)

/** Lecture / écriture du format JSON des modèles par blocs (VERSO_SPEC §3.5). */
object BlocsJson {
    fun estBlocs(json: JSONObject) = json.optString("dynamique") == "blocs"

    fun parse(json: JSONObject): DefinitionBlocs {
        val id = json.getString("id").trim()
        require(id.isNotBlank()) { "id vide" }
        val tableau = json.optJSONArray("blocs") ?: throw IllegalArgumentException("Modèle « $id » : « blocs » manquant")
        val blocs = (0 until tableau.length()).map { i ->
            try {
                parseBloc(tableau.getJSONObject(i))
            } catch (e: Exception) {
                throw IllegalArgumentException("Modèle « $id », bloc ${i + 1} : ${e.message}", e)
            }
        }
        return DefinitionBlocs(
            id = id,
            nom = json.optString("nom", id).ifBlank { id },
            categorie = json.optString("categorie", "").ifBlank { MesModelesStore.RAYON },
            blocs = blocs,
        )
    }

    private fun parseBloc(o: JSONObject): Bloc {
        val type = TypeBloc.fromJson(o.getString("type"))
        val options = mutableMapOf<String, String>()
        o.optJSONObject("options")?.let { opts -> opts.keys().forEach { k -> options[k] = opts.get(k).toString() } }
        return Bloc(
            type = type,
            x = o.optInt("x", 0),
            y = o.optInt("y", 0),
            l = o.optInt("l", MatrixFrame.SIDE),
            h = o.optInt("h", MatrixFrame.SIDE),
            niveau = o.optInt("niveau", 9),
            options = options,
        ).borne()
    }

    /** [apercu] : image de secours (aperçus, apps qui ne connaissent pas les blocs). */
    fun toJson(def: DefinitionBlocs, apercu: MatrixFrame): JSONObject = JSONObject().apply {
        put("id", def.id)
        put("nom", def.nom)
        put("categorie", def.categorie)
        put("type", "statique")
        put("dynamique", "blocs")
        put("blocs", JSONArray().apply {
            def.blocs.forEach { b ->
                put(JSONObject().apply {
                    put("type", b.type.json)
                    put("x", b.x)
                    put("y", b.y)
                    put("l", b.l)
                    put("h", b.h)
                    put("niveau", b.niveau)
                    if (b.options.isNotEmpty()) put("options", JSONObject(b.options as Map<*, *>))
                })
            }
        })
        put("frames", JSONArray().put(JSONObject().apply {
            put("pixels", JSONArray().apply { apercu.pixels.forEach { put(it) } })
            put("dureeMs", 1000)
        }))
    }
}
