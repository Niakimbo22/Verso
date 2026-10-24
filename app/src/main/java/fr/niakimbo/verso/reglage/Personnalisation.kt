package fr.niakimbo.verso.reglage

import fr.niakimbo.verso.matrix.MatrixFrame
import fr.niakimbo.verso.modele.Modele
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Réglages communs à tous les modèles, appliqués après le dessin du modèle :
 * luminosité, vitesse, rotation, miroir, inversion.
 */
object Personnalisation {
    val LUMINOSITE = Reglage.Curseur(
        cle = "commun.luminosite",
        titre = "Luminosité",
        description = "Intensité de toutes les LEDs allumées. Baisse-la pour un rendu plus discret, la nuit par exemple.",
        min = 10f, max = 100f, defaut = 100f, crans = 17, format = Formats.pourcent,
    )
    val VITESSE = Reglage.Curseur(
        cle = "commun.vitesse",
        titre = "Vitesse de l'animation",
        description = "Accélère ou ralentit toute l'animation. ×1 = vitesse d'origine, ×2 = deux fois plus vite.",
        min = 0.25f, max = 3f, defaut = 1f, crans = 10, format = Formats.fois,
    )
    val ROTATION = Reglage.Choix(
        cle = "commun.rotation",
        titre = "Rotation",
        description = "Tourne le dessin sur la matrice, pratique selon le sens où tu poses le téléphone.",
        options = listOf("0" to "Aucune", "90" to "90°", "180" to "180°", "270" to "270°"),
        defaut = "0",
    )
    val MIROIR = Reglage.Interrupteur(
        cle = "commun.miroir",
        titre = "Miroir",
        description = "Retourne le dessin de gauche à droite.",
        defaut = false,
    )
    val INVERSER = Reglage.Interrupteur(
        cle = "commun.inverser",
        titre = "Inverser",
        description = "Allume ce qui était éteint et éteint ce qui était allumé (effet négatif).",
        defaut = false,
    )

    /** Réglages communs proposés pour ce modèle (la vitesse n'a de sens que s'il est animé). */
    fun reglages(anime: Boolean): List<Reglage> =
        listOfNotNull(LUMINOSITE, VITESSE.takeIf { anime }, ROTATION, MIROIR, INVERSER)

    /** Applique les réglages communs à un modèle déjà dessiné. */
    fun appliquer(modele: Modele, v: Valeurs): Modele {
        val t = transformation(v)
        val vitesse = v.f(VITESSE)
        val images = modele.images.map { image ->
            val duree = if (modele.estAnime) (image.dureeMs / vitesse).roundToLong() else image.dureeMs
            Modele.Image(t(image.frame), duree.coerceAtLeast(Modele.MIN_DUREE_MS))
        }
        val rendu = modele.dynamique?.let { { heure: java.time.LocalTime -> t(modele.frameDynamique(heure)) } }
        return modele.copy(images = images, rendu = rendu)
    }

    private fun transformation(v: Valeurs): (MatrixFrame) -> MatrixFrame {
        val lum = v.f(LUMINOSITE) / 100f
        val quarts = (v.s(ROTATION).toInt() / 90) % 4
        val miroir = v.b(MIROIR)
        val inverser = v.b(INVERSER)
        if (lum >= 0.999f && quarts == 0 && !miroir && !inverser) return { it }
        return { frame ->
            val n = MatrixFrame.SIDE
            val px = IntArray(MatrixFrame.SIZE)
            for (y in 0 until n) {
                for (x in 0 until n) {
                    // Position d'origine du pixel qui atterrit en (x, y).
                    var sx = x
                    var sy = y
                    repeat(quarts) { val ox = sx; sx = sy; sy = n - 1 - ox }
                    if (miroir) sx = n - 1 - sx
                    var valeur = frame[sx, sy]
                    if (inverser && MatrixFrame.isOnMatrix(x, y)) valeur = MatrixFrame.MAX - valeur
                    px[y * n + x] = if (valeur > 0) (valeur * lum).roundToInt().coerceAtLeast(1) else 0
                }
            }
            MatrixFrame(px)
        }
    }
}
