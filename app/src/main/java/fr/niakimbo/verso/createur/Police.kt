package fr.niakimbo.verso.createur

import java.text.Normalizer

/**
 * Police 3×5 des blocs (chiffres, lettres, ponctuation) : la plus petite lisible sur la matrice 13×13.
 *
 * Les minuscules sont mises en majuscules et les accents retirés (é → E). Un caractère inconnu
 * s'affiche « ? ». Chaque glyphe fait 5 lignes ; la largeur varie (1 à 5 colonnes).
 */
object Police {
    const val HAUTEUR = 5

    private val GLYPHES: Map<Char, List<String>> = mapOf(
        '0' to "XXX X.X X.X X.X XXX", '1' to ".X. XX. .X. .X. XXX", '2' to "XXX ..X XXX X.. XXX",
        '3' to "XXX ..X .XX ..X XXX", '4' to "X.X X.X XXX ..X ..X", '5' to "XXX X.. XXX ..X XXX",
        '6' to "XXX X.. XXX X.X XXX", '7' to "XXX ..X ..X .X. .X.", '8' to "XXX X.X XXX X.X XXX",
        '9' to "XXX X.X XXX ..X XXX",
        'A' to ".X. X.X XXX X.X X.X", 'B' to "XX. X.X XX. X.X XX.", 'C' to ".XX X.. X.. X.. .XX",
        'D' to "XX. X.X X.X X.X XX.", 'E' to "XXX X.. XX. X.. XXX", 'F' to "XXX X.. XX. X.. X..",
        'G' to ".XX X.. X.X X.X .XX", 'H' to "X.X X.X XXX X.X X.X", 'I' to "XXX .X. .X. .X. XXX",
        'J' to "..X ..X ..X X.X .X.", 'K' to "X.X X.X XX. X.X X.X", 'L' to "X.. X.. X.. X.. XXX",
        'M' to "X.X XXX XXX X.X X.X", 'N' to "XX. X.X X.X X.X X.X", 'O' to ".X. X.X X.X X.X .X.",
        'P' to "XX. X.X XX. X.. X..", 'Q' to ".X. X.X X.X XX. .XX", 'R' to "XX. X.X XX. X.X X.X",
        'S' to ".XX X.. .X. ..X XX.", 'T' to "XXX .X. .X. .X. .X.", 'U' to "X.X X.X X.X X.X XXX",
        'V' to "X.X X.X X.X X.X .X.", 'W' to "X.X X.X XXX XXX X.X", 'X' to "X.X X.X .X. X.X X.X",
        'Y' to "X.X X.X .X. .X. .X.", 'Z' to "XXX ..X .X. X.. XXX",
        ' ' to ".. .. .. .. ..", '.' to ". . . . X", ',' to ". . . X X", ':' to ". X . X .",
        ';' to ". X . X X", '!' to "X X X . X", '?' to "XXX ..X .X. ... .X.", '-' to "... ... XXX ... ...",
        '+' to "... .X. XXX .X. ...", '=' to "... XXX ... XXX ...", '/' to "..X ..X .X. X.. X..",
        '\'' to "X X . . .", '"' to "X.X X.X ... ... ...", '%' to "X.X ..X .X. X.. X.X",
        '°' to "XX XX .. .. ..", '#' to "X.X XXX X.X XXX X.X", '(' to ".X X. X. X. .X",
        ')' to "X. .X .X .X X.", '*' to "X.X .X. X.X ... ...", '<' to "..X .X. X.. .X. ..X",
        '>' to "X.. .X. ..X .X. X..", '_' to "... ... ... ... XXX", '&' to ".X. X.X .X. X.X .XX",
        '♥' to ".X.X. XXXXX XXXXX .XXX. ..X..", '★' to "..X.. XXXXX .XXX. .X.X. X...X",
        '€' to ".XX X.. XX. X.. .XX", '@' to "XXX X.X X.X X.. .XX",
    ).mapValues { (_, v) -> v.split(' ') }

    /** Glyphe d'un caractère (5 lignes de même largeur, 'X' = allumé). */
    fun glyphe(c: Char): List<String> {
        GLYPHES[c]?.let { return it }
        val majuscule = c.uppercaseChar()
        GLYPHES[majuscule]?.let { return it }
        // é → e, ç → c… (on garde la lettre de base)
        val base = Normalizer.normalize(majuscule.toString(), Normalizer.Form.NFD).firstOrNull()
        return base?.let { GLYPHES[it] } ?: GLYPHES.getValue('?')
    }

    /**
     * Colonnes d'un texte, de gauche à droite (chaque colonne = 5 booléens, du haut vers le bas),
     * avec une colonne vide entre deux caractères.
     */
    fun colonnes(texte: String): List<BooleanArray> {
        val cols = ArrayList<BooleanArray>()
        texte.forEachIndexed { i, c ->
            if (i > 0) cols += BooleanArray(HAUTEUR)
            val g = glyphe(c)
            for (x in g[0].indices) cols += BooleanArray(HAUTEUR) { y -> g[y][x] == 'X' }
        }
        return cols
    }

    fun largeur(texte: String): Int = colonnes(texte).size
}
