package fr.niakimbo.verso.reglage

import kotlin.math.roundToInt

/**
 * Un réglage d'un modèle, décrit une fois : l'écran de réglages est généré à partir de cette description.
 *
 * Chaque réglage a un titre court et une [description] en français simple qui dit ce qu'il change
 * concrètement sur la matrice.
 */
sealed interface Reglage {
    val cle: String
    val titre: String
    val description: String

    /** Valeur numérique réglée avec un curseur. [format] transforme la valeur en texte lisible. */
    data class Curseur(
        override val cle: String,
        override val titre: String,
        override val description: String,
        val min: Float,
        val max: Float,
        val defaut: Float,
        /** Nombre de crans entre min et max (0 = continu). */
        val crans: Int = 0,
        val format: (Float) -> String = { it.roundToInt().toString() },
    ) : Reglage

    data class Interrupteur(
        override val cle: String,
        override val titre: String,
        override val description: String,
        val defaut: Boolean,
    ) : Reglage

    /** Choix parmi quelques options : (valeur stockée, libellé affiché). */
    data class Choix(
        override val cle: String,
        override val titre: String,
        override val description: String,
        val options: List<Pair<String, String>>,
        val defaut: String,
    ) : Reglage
}

/**
 * Valeurs d'un jeu de réglages. Une valeur absente (jamais touchée) prend le défaut du [Reglage].
 */
class Valeurs(private val map: Map<String, Any> = emptyMap()) {
    fun f(r: Reglage.Curseur): Float = (map[r.cle] as? Number)?.toFloat()?.coerceIn(r.min, r.max) ?: r.defaut
    fun i(r: Reglage.Curseur): Int = f(r).roundToInt()
    fun b(r: Reglage.Interrupteur): Boolean = map[r.cle] as? Boolean ?: r.defaut
    fun s(r: Reglage.Choix): String = (map[r.cle] as? String)?.takeIf { v -> r.options.any { it.first == v } } ?: r.defaut

    fun avec(cle: String, valeur: Any): Valeurs = Valeurs(map + (cle to valeur))
    fun sans(cles: Collection<String>): Valeurs = Valeurs(map - cles.toSet())
    fun brut(): Map<String, Any> = map

    override fun equals(other: Any?) = other is Valeurs && other.map == map
    override fun hashCode() = map.hashCode()
}

/** Formats d'affichage partagés, pour que les valeurs soient toujours lisibles de la même façon. */
object Formats {
    val pourcent: (Float) -> String = { "${it.roundToInt()} %" }
    val fois: (Float) -> String = { "×" + "%.2f".format(it).trimEnd('0').trimEnd('.', ',') }
    fun unite(u: String): (Float) -> String = { "${it.roundToInt()} $u" }
    fun decimal(u: String): (Float) -> String = { "%.1f".format(it) + " $u" }
}
