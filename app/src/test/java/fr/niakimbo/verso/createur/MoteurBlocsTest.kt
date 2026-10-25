package fr.niakimbo.verso.createur

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Vérifie le rendu des blocs et les outils de l'éditeur, sans téléphone.
 * `./gradlew testReleaseUnitTest` ; les images sont aussi imprimées en ASCII pour les regarder.
 */
class MoteurBlocsTest {

    private val aucune = MoteurBlocs.Ressources { null }

    private fun instant(h: Int, m: Int, s: Int = 0, batterie: Int = 75, ms: Long = 0) =
        MoteurBlocs.Instant(LocalTime.of(h, m, s), LocalDate.of(2026, 10, 25), batterie, ms)

    private fun ascii(f: MatrixFrame): String = buildString {
        for (y in 0 until MatrixFrame.SIDE) {
            for (x in 0 until MatrixFrame.SIDE) {
                val v = f[x, y]
                append(
                    when {
                        !MatrixFrame.isOnMatrix(x, y) -> ' '
                        v == 0 -> '.'
                        else -> ('0' + ((v * 9 + 127) / 255).coerceIn(1, 9))
                    },
                )
            }
            append('\n')
        }
    }

    private fun montrer(titre: String, f: MatrixFrame) = println("── $titre\n${ascii(f)}")

    @Test
    fun heureChiffres() {
        val f = MoteurBlocs.rendre(listOf(Bloc.nouveau(TypeBloc.HEURE)), instant(14, 5), aucune)
        montrer("Heure 14:05", f)
        // « 1 » de 14 : colonne du milieu du premier chiffre, ligne 1
        assertTrue(f[4, 1] > 0)
        // ligne vide entre heures et minutes
        assertEquals(0, (3..9).sumOf { f[it, 6] })
    }

    @Test
    fun aiguillesEtSecondes() {
        val blocs = listOf(
            Bloc(TypeBloc.SECONDES, 0, 0, 13, 13, niveau = 4),
            Bloc(TypeBloc.HEURE, 1, 1, 11, 11, options = mapOf("style" to "aiguilles", "cadran" to "non")),
        )
        val f = MoteurBlocs.rendre(blocs, instant(3, 0, 30), aucune)
        montrer("Aiguilles 3:00:30 + secondes", f)
        assertTrue("centre allumé", f[6, 6] == 255)
        assertTrue("aiguille des minutes vers midi", f[6, 2] == 255)
        assertTrue("aiguille des heures vers 3 h", f[8, 6] == 255)
        assertEquals(1000L, MoteurBlocs.periode(blocs, aucune))
    }

    @Test
    fun batterie() {
        val anneau = MoteurBlocs.rendre(listOf(Bloc.nouveau(TypeBloc.BATTERIE).copy(niveau = 9)), instant(10, 0, batterie = 50), aucune)
        montrer("Batterie 50 % anneau", anneau)
        assertEquals(255, anneau[12, 6]) // 3 h : rempli
        assertTrue(anneau[0, 6] in 1..60) // 9 h : piste seulement
        val barre = MoteurBlocs.rendre(listOf(Bloc(TypeBloc.BATTERIE, 2, 10, 9, 2, options = mapOf("style" to "barre"))), instant(10, 0, batterie = 34), aucune)
        montrer("Batterie 34 % barre", barre)
        val chiffre = MoteurBlocs.rendre(listOf(Bloc(TypeBloc.BATTERIE, 0, 4, 13, 5, options = mapOf("style" to "chiffre"))), instant(10, 0, batterie = 100), aucune)
        montrer("Batterie 100 chiffres", chiffre)
    }

    @Test
    fun dateEtMinuteur() {
        val blocs = listOf(
            Bloc(TypeBloc.DATE, 3, 1, 7, 5, options = mapOf("format" to "semaine")),
            Bloc(TypeBloc.DATE, 3, 7, 7, 5),
        )
        montrer("DI 25", MoteurBlocs.rendre(blocs, instant(10, 0), aucune))
        val m = MoteurBlocs.rendre(listOf(Bloc(TypeBloc.MINUTEUR, 3, 1, 7, 11, options = mapOf("cible" to "18:00"))), instant(16, 15), aucune)
        montrer("Compte à rebours 1:45", m)
    }

    @Test
    fun texteDefilant() {
        val b = Bloc(TypeBloc.TEXTE, 0, 4, 13, 5, options = mapOf("texte" to "BONJOUR"))
        assertEquals(100L, MoteurBlocs.periode(listOf(b), aucune))
        val debut = MoteurBlocs.rendre(listOf(b), instant(10, 0, ms = 0), aucune)
        assertEquals("rien au tout début (le texte entre par la droite)", 0, debut.litCount)
        val apres = MoteurBlocs.rendre(listOf(b), instant(10, 0, ms = 1300), aucune)
        montrer("Texte après 13 pas", apres)
        assertTrue(apres.litCount > 0)
        val court = Bloc(TypeBloc.TEXTE, 0, 4, 13, 5, options = mapOf("texte" to "OK"))
        assertNull("un texte qui tient ne défile pas", MoteurBlocs.periode(listOf(court), aucune))
    }

    @Test
    fun effetReduitEtDecoupe() {
        val plein = MatrixFrame(IntArray(MatrixFrame.SIZE) { if (it % 2 == 0) 255 else 0 })
        val vide = MatrixFrame.blank()
        val res = MoteurBlocs.Ressources { id ->
            if (id == "anim") listOf(Modele.Image(plein, 100), Modele.Image(vide, 100)) else null
        }
        val b = Bloc(TypeBloc.EFFET, 0, 0, 6, 6, options = mapOf("modele" to "anim"))
        assertEquals(100L, MoteurBlocs.periode(listOf(b), res))
        assertTrue(MoteurBlocs.rendre(listOf(b), instant(1, 0, ms = 50), res).litCount > 0)
        assertEquals(0, MoteurBlocs.rendre(listOf(b), instant(1, 0, ms = 150), res).litCount)
        // Un bloc ne dessine jamais hors de son rectangle.
        val f = MoteurBlocs.rendre(listOf(b.copy(x = 3, y = 3)), instant(1, 0, ms = 50), res)
        for (y in 0 until 13) for (x in 0 until 13) if (x !in 3..8 || y !in 3..8) assertEquals(0, f[x, y])
    }

    @Test
    fun blocAbsentNeCassePasLesAutres() {
        val blocs = listOf(Bloc(TypeBloc.DESSIN, 0, 0, 13, 13, options = mapOf("modele" to "inconnu")), Bloc.nouveau(TypeBloc.HEURE))
        assertTrue(MoteurBlocs.rendre(blocs, instant(12, 0), aucune).litCount > 0)
    }

    @Test
    fun police() {
        assertEquals(3, Police.largeur("A"))
        assertEquals(Police.glyphe('E'), Police.glyphe('é'))
        assertEquals(7, Police.largeur("12"))
        assertEquals(Police.glyphe('?'), Police.glyphe('§'))
    }

    @Test
    fun outilsDessin() {
        val sym = OutilsDessin.Symetrie(horizontale = true, verticale = true)
        val px = OutilsDessin.poser(OutilsDessin.vide(), listOf(2 to 3), 255, sym)
        assertEquals(4, px.count { it > 0 })
        assertEquals(255, px[3 * 13 + 10])
        assertEquals(255, px[9 * 13 + 2])
        // Le coin n'existe pas : rien n'y est dessiné.
        assertEquals(0, OutilsDessin.poser(OutilsDessin.vide(), listOf(0 to 0), 255, OutilsDessin.Symetrie())[0])
        // Remplir un disque vide = toutes les LEDs.
        val rempli = OutilsDessin.remplir(OutilsDessin.vide(), 6, 6, 100, OutilsDessin.Symetrie())
        assertEquals((0 until 169).count { MatrixFrame.isOnMatrix(it % 13, it / 13) }, rempli.count { it > 0 })
        // 4 quarts de tour = identité ; décaler 13 fois = identité.
        val motif = IntArray(169) { if (it % 7 == 0) 200 else 0 }
        var t = motif
        repeat(4) { t = OutilsDessin.tourner(t, true) }
        assertTrue(t.contentEquals(motif))
        var d = motif
        repeat(13) { d = OutilsDessin.decaler(d, 1, 0) }
        assertTrue(d.contentEquals(motif))
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), OutilsDessin.ligne(0, 0, 2, 2))
        assertEquals(9, OutilsDessin.niveau(OutilsDessin.luminosite(9)))
        assertEquals(1, OutilsDessin.niveau(OutilsDessin.luminosite(1)))
        assertEquals(5, OutilsDessin.niveau(OutilsDessin.luminosite(5)))
    }
}
