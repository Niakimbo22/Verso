package fr.niakimbo.verso.widget.perso

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.widget.WidgetMaj
import fr.niakimbo.verso.widget.perso.WidgetPerso.Bloc
import fr.niakimbo.verso.widget.perso.WidgetPerso.Couleur
import fr.niakimbo.verso.widget.perso.WidgetPerso.Disposition
import fr.niakimbo.verso.widget.perso.WidgetPerso.Taille
import fr.niakimbo.verso.widget.perso.WidgetPerso.Type
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Les widgets Verso, façon galerie de l'iPhone (refait le 26/10 à la demande de Nico : « un truc qu'on a
 * déjà tous vu », sans blocs à placer). On choisit un genre, on fait défiler ses tailles, on règle
 * le style, puis « Ajouter le widget ». Chaque taille est un widget à part pour le lanceur :
 * un 4 × 2 se pose vraiment en 4 × 2.
 */
data class WidgetVerso(
    val genre: Genre,
    val taille: TailleW,
    val style: Style = Style.VERRE,
    /** Ancien réglage « Rouge Nothing » (relu pour les widgets déjà posés) : voir [couleur]. */
    val rouge: Boolean = false,
    /** Texte du genre « Message ». */
    val texte: String = "",
    /** Couleur des LEDs allumées et du texte principal. */
    val couleur: CouleurW = if (rouge) CouleurW.ROUGE else CouleurW.BLANC,
    /** Modèle épinglé sur ce widget (null = il suit la matrice au dos). */
    val modele: String? = null,
    val points: Points = Points.RONDS,
    /** LEDs éteintes visibles (comme la vraie matrice) ou cachées (seules les LEDs allumées se voient). */
    val ledsEteintes: Boolean = true,
    val anime: Boolean = true,
    val tap: TapW = TapW.CHOISIR,
    /** Ce que le widget montre en plus de la matrice (quand la taille le permet). */
    val nom: Boolean = true,
    val boutons: Boolean = true,
    val regle: Boolean = true,
    /** Pastille d'état : Glyph allumé / éteint, Toy au dos. */
    val etatGlyph: Boolean = false,
) {
    enum class Style(val label: String) { VERRE("Verre"), SOMBRE("Sombre"), CLAIR("Clair"), AUCUN("Sans fond") }

    /** [argb] null = blanc (noir sur fond clair). */
    enum class CouleurW(val label: String, val argb: Int?) {
        BLANC("Blanc", null),
        ROUGE("Rouge Nothing", 0xFFD71921.toInt()),
        AMBRE("Ambre", 0xFFFFB300.toInt()),
        GLACE("Bleu glace", 0xFF7FD4FF.toInt()),
        VERT("Vert", 0xFF3DDC84.toInt()),
        ROSE("Rose", 0xFFFF6FAE.toInt()),
    }

    enum class Points(val label: String) { RONDS("Ronds"), CARRES("Carrés") }

    enum class TapW(val label: String, val tap: WidgetPerso.Tap) {
        CHOISIR("Fenêtre de choix", WidgetPerso.Tap.CHOISIR),
        SUIVANT("Modèle suivant", WidgetPerso.Tap.SUIVANT),
        ONOFF("⏻ On / Off", WidgetPerso.Tap.ONOFF),
        VERSO("Ouvrir Verso", WidgetPerso.Tap.VERSO),
        AFFICHER("Afficher ce modèle au dos", WidgetPerso.Tap.AFFICHER),
    }

    /** Ce que la taille choisie montre, avant les réglages « afficher » (pour ne proposer que ce qui existe). */
    fun blocsDeBase(): List<Bloc> = genre.contenu(taille, Couleur.PRINCIPALE, texte.ifBlank { "Bonne journée" }).second

    /** Le plan de dessin pour [RenduPerso]. */
    fun plan(): WidgetPerso {
        val (disposition, base) = genre.contenu(taille, Couleur.PRINCIPALE, texte.ifBlank { "Bonne journée" })
        val blocs = base.filter {
            when (it.type) {
                Type.NOM -> nom
                Type.BOUTONS -> boutons
                Type.AUTO -> regle
                else -> true
            }
        }.toMutableList()
        if (etatGlyph) {
            // Après le nom (ou en fin), avant les boutons.
            val i = blocs.indexOfFirst { it.type == Type.BOUTONS }.takeIf { it >= 0 } ?: blocs.size
            blocs.add(i, Bloc(Type.GLYPH, Taille.S))
        }
        return WidgetPerso(
            nom = genre.titre,
            blocs = blocs,
            disposition = disposition,
            fond = when (style) {
                Style.VERRE -> WidgetPerso.Fond.VERRE
                Style.SOMBRE, Style.CLAIR -> WidgetPerso.Fond.PLEIN
                Style.AUCUN -> WidgetPerso.Fond.TRANSPARENT
            },
            theme = if (style == Style.CLAIR) WidgetPerso.Theme.CLAIR else WidgetPerso.Theme.SOMBRE,
            alignement = if (disposition == Disposition.LIGNE || (taille.l >= 4 && taille.h == 1)) WidgetPerso.Alignement.GAUCHE else WidgetPerso.Alignement.CENTRE,
            ledsRouges = couleur == CouleurW.ROUGE,
            pointsCarres = points == Points.CARRES,
            accent = couleur.argb,
            ledsEteintes = ledsEteintes,
            anime = anime,
            tap = if (tap == TapW.AFFICHER && modele == null) WidgetPerso.Tap.CHOISIR else tap.tap,
            modeleFixe = modele,
        )
    }

    /** Résumé lisible (liste « Sur ton écran d'accueil »). */
    fun resume(): String = buildList {
        add(style.label)
        if (couleur != CouleurW.BLANC) add(couleur.label.lowercase())
        if (modele != null) add("modèle épinglé")
        if (points == Points.CARRES) add("points carrés")
        if (!anime) add("fixe")
    }.joinToString(" · ")

    fun versJson(): String = JSONObject()
        .put("genre", genre.name).put("taille", taille.name).put("style", style.name).put("rouge", couleur == CouleurW.ROUGE).put("texte", texte)
        .put("couleur", couleur.name).put("modele", modele ?: JSONObject.NULL).put("points", points.name)
        .put("eteintes", ledsEteintes).put("anime", anime).put("tap", tap.name)
        .put("nom", nom).put("boutons", boutons).put("regle", regle).put("glyph", etatGlyph)
        .toString()

    companion object {
        fun depuisJson(s: String?): WidgetVerso? = try {
            val o = JSONObject(s ?: return null)
            val rouge = o.optBoolean("rouge", false)
            WidgetVerso(
                genre = Genre.valueOf(o.getString("genre")),
                taille = TailleW.valueOf(o.getString("taille")),
                style = Style.entries.firstOrNull { it.name == o.optString("style") } ?: Style.VERRE,
                rouge = rouge,
                texte = o.optString("texte").take(40),
                couleur = CouleurW.entries.firstOrNull { it.name == o.optString("couleur") } ?: if (rouge) CouleurW.ROUGE else CouleurW.BLANC,
                modele = if (o.isNull("modele")) null else o.optString("modele").ifBlank { null },
                points = Points.entries.firstOrNull { it.name == o.optString("points") } ?: Points.RONDS,
                ledsEteintes = o.optBoolean("eteintes", true),
                anime = o.optBoolean("anime", true),
                tap = TapW.entries.firstOrNull { it.name == o.optString("tap") } ?: TapW.CHOISIR,
                nom = o.optBoolean("nom", true),
                boutons = o.optBoolean("boutons", true),
                regle = o.optBoolean("regle", true),
                etatGlyph = o.optBoolean("glyph", false),
            )
        } catch (_: Exception) {
            null
        }

        /** Widget posé depuis la liste du lanceur, jamais réglé : l'horloge (ou la matrice) de sa taille. */
        fun parDefaut(taille: TailleW) = WidgetVerso(
            genre = Genre.entries.firstOrNull { taille in it.tailles } ?: Genre.MATRICE,
            taille = taille,
        )
    }
}

/** Les tailles proposées (cases de l'écran d'accueil). Chacune a son propre widget pour le lanceur. */
enum class TailleW(val l: Int, val h: Int, val nom: String, val classe: Class<out WidgetVersoProvider>) {
    T1x1(1, 1, "Mini", Verso1x1::class.java),
    T2x1(2, 1, "Bandeau", Verso2x1::class.java),
    T2x2(2, 2, "Petit", WidgetPersoProvider::class.java),
    T3x2(3, 2, "Moyen", Verso3x2::class.java),
    T4x1(4, 1, "Barre", Verso4x1::class.java),
    T4x2(4, 2, "Large", Verso4x2::class.java),
    T4x3(4, 3, "Haut", Verso4x3::class.java),
    T4x4(4, 4, "Grand", Verso4x4::class.java),
    ;

    val label get() = "$l × $h"

    companion object {
        fun de(classe: String?): TailleW? = entries.firstOrNull { it.classe.name == classe }
    }
}

/** Ce que montre le widget. Chaque genre propose ses tailles, avec un contenu pensé pour chacune. */
enum class Genre(val titre: String, val description: String, val tailles: List<TailleW>) {
    MATRICE("Matrice", "Le modèle au dos, animé, avec ses commandes dans les grandes tailles.", TailleW.entries),
    HORLOGE("Horloge", "L'heure et la date, à jour toute seule. La matrice dans les grandes tailles.", TailleW.entries),
    INFOS("Tout-en-un", "Heure, date, batterie, règle auto et matrice.", listOf(TailleW.T2x2, TailleW.T3x2, TailleW.T4x2, TailleW.T4x3, TailleW.T4x4)),
    BATTERIE("Batterie", "Le niveau de batterie en grand, ⚡ en charge.", listOf(TailleW.T1x1, TailleW.T2x1, TailleW.T2x2, TailleW.T4x1, TailleW.T4x2)),
    MESSAGE("Message", "Ton texte à toi, avec la date.", listOf(TailleW.T2x1, TailleW.T2x2, TailleW.T4x1, TailleW.T4x2, TailleW.T4x4)),
    ;

    /** Contenu de ce genre pour la taille [t]. [p] = couleur du texte principal, [msg] = texte du genre Message. */
    fun contenu(t: TailleW, p: Couleur, msg: String): Pair<Disposition, List<Bloc>> {
        val col = Disposition.COLONNE
        val lig = Disposition.LIGNE
        val d = Couleur.DISCRETE
        return when (this) {
            MATRICE -> when (t) {
                TailleW.T1x1 -> col to listOf(Bloc(Type.MATRICE, Taille.S))
                TailleW.T2x1 -> lig to listOf(Bloc(Type.MATRICE, Taille.S), Bloc(Type.NOM, Taille.S, p))
                TailleW.T2x2 -> col to listOf(Bloc(Type.MATRICE, Taille.L))
                TailleW.T3x2 -> lig to listOf(Bloc(Type.MATRICE, Taille.L), Bloc(Type.NOM, Taille.M, p), Bloc(Type.BOUTONS))
                TailleW.T4x1 -> lig to listOf(Bloc(Type.MATRICE, Taille.S), Bloc(Type.NOM, Taille.M, p), Bloc(Type.BOUTONS))
                TailleW.T4x2 -> lig to listOf(Bloc(Type.MATRICE, Taille.L), Bloc(Type.NOM, Taille.L, p), Bloc(Type.AUTO, Taille.S, Couleur.ROUGE), Bloc(Type.BOUTONS))
                TailleW.T4x3 -> col to listOf(Bloc(Type.MATRICE, Taille.XL), Bloc(Type.NOM, Taille.M, p), Bloc(Type.BOUTONS))
                TailleW.T4x4 -> col to listOf(Bloc(Type.MATRICE, Taille.XL), Bloc(Type.ESPACE), Bloc(Type.NOM, Taille.L, p), Bloc(Type.AUTO, Taille.S, Couleur.ROUGE), Bloc(Type.BOUTONS))
            }
            HORLOGE -> when (t) {
                TailleW.T1x1 -> col to listOf(Bloc(Type.HEURE, Taille.S, p))
                TailleW.T2x1 -> col to listOf(Bloc(Type.HEURE, Taille.M, p))
                TailleW.T2x2 -> col to listOf(Bloc(Type.HEURE, Taille.L, p), Bloc(Type.DATE, Taille.S, d))
                TailleW.T3x2 -> col to listOf(Bloc(Type.HEURE, Taille.L, p), Bloc(Type.DATE, Taille.M, d))
                TailleW.T4x1 -> col to listOf(Bloc(Type.HEURE, Taille.M, p), Bloc(Type.DATE, Taille.S, d))
                TailleW.T4x2 -> lig to listOf(Bloc(Type.MATRICE, Taille.L), Bloc(Type.HEURE, Taille.L, p), Bloc(Type.DATE, Taille.M, d))
                TailleW.T4x3 -> col to listOf(Bloc(Type.HEURE, Taille.XL, p), Bloc(Type.DATE, Taille.M, d), Bloc(Type.ESPACE), Bloc(Type.MATRICE, Taille.M))
                TailleW.T4x4 -> col to listOf(Bloc(Type.HEURE, Taille.XL, p), Bloc(Type.DATE, Taille.M, d), Bloc(Type.ESPACE), Bloc(Type.MATRICE, Taille.XL))
            }
            INFOS -> when (t) {
                TailleW.T3x2 -> lig to listOf(Bloc(Type.MATRICE, Taille.M), Bloc(Type.HEURE, Taille.M, p), Bloc(Type.DATE, Taille.S, d), Bloc(Type.BATTERIE, Taille.S, d))
                TailleW.T4x2 -> lig to listOf(Bloc(Type.MATRICE, Taille.L), Bloc(Type.HEURE, Taille.L, p), Bloc(Type.DATE, Taille.S, d), Bloc(Type.BATTERIE, Taille.S, d), Bloc(Type.AUTO, Taille.S, Couleur.ROUGE))
                TailleW.T4x3, TailleW.T4x4 -> col to listOf(
                    Bloc(Type.HEURE, Taille.L, p), Bloc(Type.DATE, Taille.M, d), Bloc(Type.ESPACE),
                    Bloc(Type.MATRICE, if (t == TailleW.T4x4) Taille.XL else Taille.L), Bloc(Type.ESPACE),
                    Bloc(Type.BATTERIE, Taille.S, d), Bloc(Type.AUTO, Taille.S, Couleur.ROUGE),
                )
                else -> col to listOf(Bloc(Type.HEURE, Taille.M, p), Bloc(Type.DATE, Taille.S, d), Bloc(Type.BATTERIE, Taille.S, d), Bloc(Type.AUTO, Taille.S, Couleur.ROUGE))
            }
            BATTERIE -> when (t) {
                TailleW.T1x1 -> col to listOf(Bloc(Type.BATTERIE, Taille.M, p))
                TailleW.T2x1, TailleW.T4x1 -> col to listOf(Bloc(Type.BATTERIE, Taille.L, p))
                TailleW.T4x2 -> lig to listOf(Bloc(Type.MATRICE, Taille.L), Bloc(Type.BATTERIE, Taille.XL, p), Bloc(Type.TEXTE, Taille.S, d, "BATTERIE"))
                else -> col to listOf(Bloc(Type.BATTERIE, Taille.XL, p), Bloc(Type.TEXTE, Taille.S, d, "BATTERIE"))
            }
            MESSAGE -> when (t) {
                TailleW.T2x1, TailleW.T4x1 -> col to listOf(Bloc(Type.TEXTE, Taille.L, p, msg))
                TailleW.T4x4 -> col to listOf(Bloc(Type.TEXTE, Taille.XL, p, msg), Bloc(Type.DATE, Taille.M, d), Bloc(Type.ESPACE), Bloc(Type.MATRICE, Taille.L))
                else -> col to listOf(Bloc(Type.TEXTE, Taille.XL, p, msg), Bloc(Type.DATE, Taille.S, d))
            }
        }
    }
}

/** Réglage de chaque widget posé, et du widget en attente de pose (« Ajouter le widget »). */
class WidgetsPersoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("verso_widgets_galerie", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)

    /** Change à chaque modification : les widgets posés se redessinent. */
    val revision: StateFlow<Int> = _revision

    /** Réglage du widget posé [appWidgetId], ou celui par défaut de sa taille. */
    fun pour(appWidgetId: Int, taille: TailleW): WidgetVerso =
        WidgetVerso.depuisJson(prefs.getString("pose_$appWidgetId", null))?.copy(taille = taille) ?: WidgetVerso.parDefaut(taille)

    fun regle(appWidgetId: Int): Boolean = prefs.contains("pose_$appWidgetId")

    fun enregistrer(appWidgetId: Int, w: WidgetVerso) {
        prefs.edit().putString("pose_$appWidgetId", w.versJson()).apply()
        _revision.value++
    }

    fun oublier(appWidgetId: Int) {
        prefs.edit().remove("pose_$appWidgetId").apply()
    }

    /** Garde le widget choisi le temps que le lanceur le pose ; [jeton] revient avec l'id du widget posé. */
    fun mettreEnAttente(jeton: String, w: WidgetVerso) = prefs.edit().putString("attente_$jeton", w.versJson()).apply()

    fun reprendreAttente(jeton: String): WidgetVerso? {
        val w = WidgetVerso.depuisJson(prefs.getString("attente_$jeton", null))
        prefs.edit().remove("attente_$jeton").apply()
        return w
    }

    /** Un widget posé affiche-t-il la batterie ? (pour ne le redessiner qu'en cas de besoin) */
    fun avecBatterie(context: Context): Boolean = poses(context).any { (id, t) ->
        pour(id, t).plan().blocs.any { it.type == Type.BATTERIE }
    }

    /** Tous les widgets Verso de la galerie posés sur l'écran d'accueil : (id, taille). */
    fun poses(context: Context): List<Pair<Int, TailleW>> {
        val m = AppWidgetManager.getInstance(context)
        return TailleW.entries.flatMap { t -> m.getAppWidgetIds(ComponentName(context, t.classe)).map { it to t } }
    }
}

/** Un fournisseur par taille (voir [TailleW]) ; tout le dessin est fait par [RenduPerso] via [WidgetMaj]. */
abstract class WidgetVersoProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as VersoApp
        val attente = goAsync()
        app.appScope.launch {
            try { WidgetMaj.majTous(app, forcer = true) } finally { attente.finish() }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val app = context.applicationContext as VersoApp
        ids.forEach {
            app.widgetsPerso.oublier(it)
            WidgetMaj.oublier(it)
        }
    }
}

/** 2 × 2 (garde le nom du premier widget perso : ceux déjà posés continuent de marcher). */
class WidgetPersoProvider : WidgetVersoProvider()
class Verso1x1 : WidgetVersoProvider()
class Verso2x1 : WidgetVersoProvider()
class Verso3x2 : WidgetVersoProvider()
class Verso4x1 : WidgetVersoProvider()
class Verso4x2 : WidgetVersoProvider()
class Verso4x3 : WidgetVersoProvider()
class Verso4x4 : WidgetVersoProvider()
