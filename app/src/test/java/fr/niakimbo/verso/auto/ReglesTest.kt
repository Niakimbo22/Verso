package fr.niakimbo.verso.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Moteur des règles automatiques, sans téléphone : `./gradlew testReleaseUnitTest`. */
class ReglesTest {

    // Dimanche 25/10/2026.
    private fun t(h: Int, m: Int = 0, jour: Int = 25) = LocalDateTime.of(2026, 10, jour, h, m)

    private fun ctx(
        h: Int, m: Int = 0, jour: Int = 25,
        charge: Boolean = false, batterie: Int = 80,
        casques: List<String> = emptyList(), dnd: Boolean = false,
    ) = Contexte(t(h, m, jour), charge, batterie, casques, dnd)

    private val nuit = Regle("nuit", "Nuit", Declencheur.Plage(22 * 60, 7 * 60), "lune")
    private val charge = Regle("charge", "Charge", Declencheur.Charge(), "chargement")

    @Test
    fun plageSimple() {
        val p = Declencheur.Plage(9 * 60, 17 * 60)
        assertTrue(Evaluation.dansPlage(p, t(9)))
        assertTrue(Evaluation.dansPlage(p, t(16, 59)))
        assertFalse(Evaluation.dansPlage(p, t(17)))
        assertFalse(Evaluation.dansPlage(p, t(8, 59)))
    }

    @Test
    fun plagePasseMinuit() {
        val p = nuit.conditions[0] as Declencheur.Plage
        assertTrue(Evaluation.dansPlage(p, t(23)))
        assertTrue(Evaluation.dansPlage(p, t(2)))
        assertTrue(Evaluation.dansPlage(p, t(6, 59)))
        assertFalse(Evaluation.dansPlage(p, t(7)))
        assertFalse(Evaluation.dansPlage(p, t(12)))
    }

    @Test
    fun plageJours() {
        // Vendredi et samedi soir seulement.
        val p = Declencheur.Plage(22 * 60, 2 * 60, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
        assertTrue(Evaluation.dansPlage(p, t(23, jour = 24))) // samedi 23h
        assertTrue(Evaluation.dansPlage(p, t(1, jour = 25))) // dimanche 1h = nuit de samedi
        assertFalse(Evaluation.dansPlage(p, t(23, jour = 25))) // dimanche 23h
        assertFalse(Evaluation.dansPlage(p, t(1, jour = 24 - 4))) // mardi 1h
    }

    @Test
    fun priorite_premiereDeLaListe() {
        val regles = listOf(charge, nuit)
        assertEquals("charge", Evaluation.gagnante(regles, ctx(23, charge = true))?.id)
        assertEquals("nuit", Evaluation.gagnante(regles, ctx(23))?.id)
        assertNull(Evaluation.gagnante(regles, ctx(12)))
        assertNull(Evaluation.gagnante(listOf(nuit.copy(active = false)), ctx(23)))
    }

    @Test
    fun batterieEtCasque() {
        val faible = Declencheur.BatterieFaible(15)
        assertTrue(Evaluation.vraie(faible, ctx(12, batterie = 15)))
        assertFalse(Evaluation.vraie(faible, ctx(12, batterie = 16)))
        assertFalse(Evaluation.vraie(faible, ctx(12, batterie = 5, charge = true)))
        assertTrue(Evaluation.vraie(Declencheur.Casque(), ctx(12, casques = listOf("Nothing Ear"))))
        assertFalse(Evaluation.vraie(Declencheur.Casque("AirPods"), ctx(12, casques = listOf("Nothing Ear"))))
        assertTrue(Evaluation.vraie(Declencheur.Casque("nothing ear"), ctx(12, casques = listOf("Nothing Ear"))))
    }

    @Test
    fun prochaineFrontiere() {
        val regles = listOf(nuit)
        assertEquals(t(22), Evaluation.prochaineFrontiere(regles, ctx(12, 30)))
        assertEquals(t(7, jour = 26), Evaluation.prochaineFrontiere(regles, ctx(22)))
        assertNull(Evaluation.prochaineFrontiere(listOf(charge), ctx(12)))
    }

    @Test
    fun regleQuiCommencePuisFinit_revientAuModeleDavant() {
        var m = Decision.Memoire()
        val debut = Decision.evaluer(m, nuit, affiche = "pluie")
        assertEquals("lune", debut.afficher?.modele)
        m = debut.memoire
        // Toujours la nuit : rien ne bouge.
        assertNull(Decision.evaluer(m, nuit, "lune").afficher)
        val fin = Decision.evaluer(m, null, "lune")
        assertEquals("pluie", fin.afficher?.modele)
        assertNull(fin.memoire.enCours)
    }

    @Test
    fun optionA_choixManuelTientJusquauProchainChangement() {
        var m = Decision.evaluer(Decision.Memoire(), nuit, "pluie").memoire
        // 23h : Nico choisit Coeur à la main.
        m = Decision.choixManuel(m, "coeur")
        // La règle de nuit continue : aucune image imposée.
        assertNull(Decision.evaluer(m, nuit, "coeur").afficher)
        // On branche le téléphone : nouvelle règle, elle s'applique.
        val r = Decision.evaluer(m, charge, "coeur")
        assertEquals("chargement", r.afficher?.modele)
        // On débranche à 8h (plus de nuit) : retour au choix manuel, pas à Pluie.
        val fin = Decision.evaluer(r.memoire, null, "chargement")
        assertEquals("coeur", fin.afficher?.modele)
    }

    @Test
    fun choixManuelPuisFinDeRegle_garderLeChoix() {
        var m = Decision.evaluer(Decision.Memoire(), nuit, "pluie").memoire
        m = Decision.choixManuel(m, "coeur")
        val fin = Decision.evaluer(m, null, "coeur")
        assertNull(fin.afficher) // déjà Coeur : on ne touche à rien
    }

    @Test
    fun sansRevenir_leModeleDeLaRegleReste() {
        val r = nuit.copy(revenir = false)
        val m = Decision.evaluer(Decision.Memoire(), r, "pluie").memoire
        val fin = Decision.evaluer(m, null, "lune")
        assertNull(fin.afficher)
        assertEquals("lune", fin.memoire.base)
    }

    @Test
    fun regleModifieePendantQuelleSApplique_reappliquee() {
        val m = Decision.evaluer(Decision.Memoire(), nuit, "pluie").memoire
        val r = Decision.evaluer(m, nuit.copy(action = Action.Afficher("etoile")), "lune")
        assertEquals("etoile", r.afficher?.modele)
        assertEquals("pluie", r.memoire.base)
    }

    @Test
    fun eteindreEstUnModele() {
        val dodo = nuit.copy(action = Action.Eteindre)
        val r = Decision.evaluer(Decision.Memoire(), dodo, "pluie")
        assertTrue(r.afficher != null)
        assertNull(r.afficher!!.modele)
        assertEquals("pluie", Decision.evaluer(r.memoire, null, null).afficher?.modele)
    }

    @Test
    fun reveil() {
        val r = Declencheur.Reveil()
        assertTrue(Evaluation.dansFenetreReveil(r, t(7, 30)))
        assertFalse(Evaluation.dansFenetreReveil(r, t(2)))
        assertFalse(Evaluation.dansFenetreReveil(r, t(13)))
        // Réveil pendant une règle : affiché, et devient le modèle à remettre ensuite.
        val m = Decision.evaluer(Decision.Memoire(), charge, "pluie").memoire
        val res = Decision.moment(m, "soleil", "chargement")
        assertEquals("soleil", res.afficher?.modele)
        assertEquals("soleil", Decision.evaluer(res.memoire, null, "soleil").memoire.base)
    }

    // ---------- Partie 1 bis : nouveaux déclencheurs, ET / OU, actions ----------

    private val grenoble = Lieu("Grenoble", 45.188, 5.724)

    @Test
    fun soleil_grenoble() {
        val zone = java.time.ZoneId.of("Europe/Paris")
        val (lever, coucher) = Soleil.heures(java.time.LocalDate.of(2026, 10, 25), grenoble, zone)!!
        // Vérifié : lever ~07:10, coucher ~17:30 (heure d'hiver).
        assertTrue("lever $lever", lever.isAfter(java.time.LocalTime.of(6, 50)) && lever.isBefore(java.time.LocalTime.of(7, 30)))
        assertTrue("coucher $coucher", coucher.isAfter(java.time.LocalTime.of(17, 10)) && coucher.isBefore(java.time.LocalTime.of(17, 50)))
        val (eteLever, eteCoucher) = Soleil.heures(java.time.LocalDate.of(2026, 6, 21), grenoble, zone)!!
        assertTrue("été $eteLever", eteLever.isBefore(java.time.LocalTime.of(6, 0)))
        assertTrue("été $eteCoucher", eteCoucher.isAfter(java.time.LocalTime.of(21, 15)))
    }

    @Test
    fun soleil_nuitEtJour() {
        val zone = java.time.ZoneId.of("Europe/Paris")
        fun c(h: Int) = Contexte(t(h), lieu = grenoble, zone = zone)
        val nuit = Declencheur.Soleil(nuit = true)
        assertTrue(Evaluation.vraie(nuit, c(22)))
        assertTrue(Evaluation.vraie(nuit, c(5)))
        assertFalse(Evaluation.vraie(nuit, c(12)))
        assertTrue(Evaluation.vraie(Declencheur.Soleil(nuit = false), c(12)))
        // Sans ville : jamais vrai.
        assertFalse(Evaluation.vraie(nuit, Contexte(t(23))))
        // Prochaine frontière à midi : le coucher du jour.
        val f = Evaluation.prochaineFrontiere(listOf(Regle("s", "S", nuit, "lune")), c(12))!!
        assertTrue("frontière $f", f.hour == 17)
    }

    @Test
    fun combinaisons_etOu() {
        val etNuitCharge = Regle("r", "R", listOf(nuit.conditions[0], Declencheur.Charge()), Action.Afficher("lune"), toutes = true)
        assertFalse(Evaluation.vraie(etNuitCharge, ctx(23)))
        assertTrue(Evaluation.vraie(etNuitCharge, ctx(23, charge = true)))
        val ou = etNuitCharge.copy(toutes = false)
        assertTrue(Evaluation.vraie(ou, ctx(23)))
        assertTrue(Evaluation.vraie(ou, ctx(12, charge = true)))
        assertFalse(Evaluation.vraie(ou, ctx(12)))
    }

    @Test
    fun batterieTelephoneConnexions() {
        val c = Contexte(t(12), enCharge = true, batterie = 100, sourceCharge = Declencheur.SANS_FIL)
        assertTrue(Evaluation.vraie(Declencheur.BatteriePleine(), c))
        assertTrue(Evaluation.vraie(Declencheur.Charge(Declencheur.SANS_FIL), c))
        assertFalse(Evaluation.vraie(Declencheur.Charge(Declencheur.CABLE), c))
        assertTrue(Evaluation.vraie(Declencheur.Sonnerie(), Contexte(t(12), sonnerie = Declencheur.VIBREUR)))
        assertFalse(Evaluation.vraie(Declencheur.Sonnerie(Declencheur.SILENCIEUX), Contexte(t(12), sonnerie = Declencheur.VIBREUR)))
        assertTrue(Evaluation.vraie(Declencheur.Ecran(verrouille = true), Contexte(t(12), verrouille = true)))
        assertTrue(Evaluation.vraie(Declencheur.Reseau(Declencheur.AUCUN), Contexte(t(12), reseau = Declencheur.AUCUN)))
        assertTrue(Evaluation.vraie(Declencheur.Bluetooth("Nothing Watch"), Contexte(t(12), bluetooth = listOf("nothing watch"))))
        assertTrue(Evaluation.vraie(Declencheur.Date(25, 10), ctx(9)))
        assertFalse(Evaluation.vraie(Declencheur.Date(31, 10), ctx(9)))
    }

    @Test
    fun avantAlarme() {
        val alarme = t(7, jour = 26)
        val d = Declencheur.AvantAlarme(30)
        assertTrue(Evaluation.vraie(d, Contexte(t(6, 45, 26), prochaineAlarme = alarme)))
        assertFalse(Evaluation.vraie(d, Contexte(t(6, 20, 26), prochaineAlarme = alarme)))
        assertFalse(Evaluation.vraie(d, Contexte(t(7, 0, 26), prochaineAlarme = alarme)))
        val f = Evaluation.prochaineFrontiere(listOf(Regle("a", "A", d, "soleil")), Contexte(t(23), prochaineAlarme = alarme))
        assertEquals(t(6, 30, 26), f)
    }

    @Test
    fun momentAvecCondition() {
        val aDixHuit = Declencheur.AHeure(18 * 60)
        val r = Regle("m", "M", listOf(aDixHuit, Declencheur.Charge()), Action.Afficher("soleil"), toutes = true)
        assertTrue(r.estMoment)
        val c = ctx(18)
        assertFalse(Evaluation.momentPret(r, c) { it == aDixHuit })
        assertTrue(Evaluation.momentPret(r, ctx(18, charge = true)) { it == aDixHuit })
        assertTrue(Evaluation.momentPret(r.copy(toutes = false), c) { it == aDixHuit })
        assertFalse(Evaluation.momentPret(r, ctx(18, charge = true)) { false })
        // Un moment ne compte jamais comme règle « pendant ».
        assertNull(Evaluation.gagnante(listOf(r), ctx(18, charge = true)))
        assertEquals(t(18), Evaluation.prochaineFrontiere(listOf(r), ctx(12)))
    }

    @Test
    fun defilement() {
        val r = Regle("d", "D", listOf(Declencheur.Charge()), Action.Defiler(10))
        var m = Decision.evaluer(Decision.Memoire(), r, "coeur", "pluie").memoire
        assertEquals("pluie", m.base)
        val res = Decision.defiler(m, "etoile", "coeur")
        assertEquals("etoile", res.afficher?.modele)
        m = res.memoire
        // Toujours la même règle : pas de réapplication.
        assertNull(Decision.evaluer(m, r, "autre", "etoile").afficher)
        assertEquals("pluie", Decision.evaluer(m, null, null, "etoile").afficher?.modele)
    }

    // ---- Étape 9, partie 2 : notifications (flash) et musique ----

    private val whatsapp = AppChoisie("com.whatsapp", "WhatsApp")
    private val spotify = AppChoisie("com.spotify.music", "Spotify")

    @Test
    fun flash_jamaisPendant() {
        val r = Regle("n", "Notif", listOf(Declencheur.Notification(listOf(whatsapp), 5)), Action.Afficher("message"))
        assertTrue(r.estFlash)
        assertFalse(r.estMoment)
        assertFalse(r.estPendant)
        assertNull(Evaluation.gagnante(listOf(r), ctx(12)))
        assertNull(Evaluation.prochaineFrontiere(listOf(r), ctx(12)))
    }

    @Test
    fun flash_appEtConditions() {
        val wa = Regle("wa", "WA", listOf(Declencheur.Notification(listOf(whatsapp), 10), Declencheur.Plage(22 * 60, 7 * 60)), Action.Afficher("message"))
        val tout = Regle("tout", "Tout", listOf(Declencheur.Notification()), Action.Afficher("notification"))
        val regles = listOf(wa, tout)
        // WhatsApp la nuit : la règle WhatsApp (la plus haute) gagne.
        assertEquals("wa", Evaluation.flash(regles, "com.whatsapp", ctx(23))?.id)
        assertEquals(10, Evaluation.flash(regles, "com.whatsapp", ctx(23))?.secondesFlash)
        // WhatsApp le jour : la plage est fausse, c'est « toutes les apps ».
        assertEquals("tout", Evaluation.flash(regles, "com.whatsapp", ctx(12))?.id)
        // Une autre app : seulement « toutes les apps ».
        assertEquals("tout", Evaluation.flash(regles, "com.discord", ctx(23))?.id)
        // Règle coupée, ou OU : la plage n'est plus obligatoire.
        assertNull(Evaluation.flash(listOf(wa), "com.whatsapp", ctx(12)))
        assertEquals("wa", Evaluation.flash(listOf(wa.copy(toutes = false)), "com.whatsapp", ctx(12))?.id)
        assertNull(Evaluation.flash(listOf(tout.copy(active = false)), "com.discord", ctx(12)))
    }

    @Test
    fun musique() {
        val c = Contexte(t(12), musique = listOf("com.spotify.music"))
        assertTrue(Evaluation.vraie(Declencheur.Musique(), c))
        assertTrue(Evaluation.vraie(Declencheur.Musique(listOf(spotify)), c))
        assertFalse(Evaluation.vraie(Declencheur.Musique(listOf(whatsapp)), c))
        assertFalse(Evaluation.vraie(Declencheur.Musique(), ctx(12)))
        val r = Regle("m", "Musique", listOf(Declencheur.Musique()), Action.Afficher("egaliseur"))
        assertTrue(r.estPendant)
        assertEquals("m", Evaluation.gagnante(listOf(r), c)?.id)
    }

    @Test
    fun textes() {
        val r = Regle("n", "", listOf(Declencheur.Notification(listOf(whatsapp), 5)), Action.Afficher("message"))
        assertEquals("Si une notif de WhatsApp arrive, afficher Message 5 s.", Textes.phrase(r) { "Message" })
        assertEquals("Spotify ou WhatsApp joue", Textes.quand(Declencheur.Musique(listOf(spotify, whatsapp))))
        assertEquals("de la musique joue", Textes.quand(Declencheur.Musique()))
    }

    // ---- Étape 9, partie 3 : app ouverte et météo ----

    @Test
    fun appOuverte() {
        val meteo = AppChoisie("com.nothing.weather", "Météo")
        val d = Declencheur.AppOuverte(listOf(meteo))
        assertTrue(Evaluation.vraie(d, Contexte(t(12), app = "com.nothing.weather")))
        assertFalse(Evaluation.vraie(d, Contexte(t(12), app = "com.android.launcher")))
        assertFalse(Evaluation.vraie(d, Contexte(t(12), app = null)))
        assertFalse(Evaluation.vraie(Declencheur.AppOuverte(), Contexte(t(12), app = "com.nothing.weather")))
        // Plus haute dans la liste : elle passe devant la nuit.
        val r = Regle("a", "App météo", listOf(d), Action.Afficher("meteo-live"))
        assertEquals("a", Evaluation.gagnante(listOf(r, nuit), Contexte(t(23), app = "com.nothing.weather"))?.id)
        assertEquals("nuit", Evaluation.gagnante(listOf(r, nuit), Contexte(t(23), app = "autre"))?.id)
        assertEquals("Si Météo est ouverte, afficher Météo réelle.", Textes.phrase(r) { "Météo réelle" })
    }

    @Test
    fun meteo() {
        assertEquals(Meteo.SOLEIL, Meteo.ciel(0))
        assertEquals(Meteo.NUAGES, Meteo.ciel(3))
        assertEquals(Meteo.NUAGES, Meteo.ciel(45))
        assertEquals(Meteo.PLUIE, Meteo.ciel(61))
        assertEquals(Meteo.PLUIE, Meteo.ciel(81))
        assertEquals(Meteo.NEIGE, Meteo.ciel(73))
        assertEquals(Meteo.ORAGE, Meteo.ciel(95))
        val d = Declencheur.Meteo(setOf(Meteo.PLUIE, Meteo.ORAGE))
        assertTrue(Evaluation.vraie(d, Contexte(t(12), ciel = Meteo.PLUIE)))
        assertFalse(Evaluation.vraie(d, Contexte(t(12), ciel = Meteo.SOLEIL)))
        assertFalse(Evaluation.vraie(d, Contexte(t(12), ciel = null)))
        assertEquals("il pleut ou il y a de l'orage", Textes.quand(d))
    }

    @Test
    fun meteoLive() {
        fun e(code: Int, jour: Boolean = true) = EtatMeteo(code, 10, jour, "Meylan", 0)
        assertEquals("soleil", fr.niakimbo.verso.generateur.MeteoLive.icone(e(0)))
        assertEquals("lune", fr.niakimbo.verso.generateur.MeteoLive.icone(e(1, jour = false)))
        assertEquals("eclaircie", fr.niakimbo.verso.generateur.MeteoLive.icone(e(2)))
        assertEquals("pluie", fr.niakimbo.verso.generateur.MeteoLive.icone(e(63)))
        assertEquals("orage", fr.niakimbo.verso.generateur.MeteoLive.icone(e(96)))
        // Toutes les températures se dessinent (sans planter, et avec des LEDs allumées).
        for (t in -40..50) assertTrue(fr.niakimbo.verso.generateur.Live.temperature(t).frame().litCount > 0)
    }

    // ---- Le son en TOP 1 (28/10) ----

    @Test
    fun son_top1() {
        val deezer = AppChoisie("deezer.android.app", "Deezer")
        val ega = Regle("ega", "Égaliseur", listOf(Declencheur.AppOuverte(listOf(deezer))), Action.Afficher("egaliseur"))
        val c = Contexte(t(23), app = "fr.niakimbo.verso", musique = listOf("deezer.android.app"))
        // Deezer joue en arrière-plan : l'app compte comme ouverte.
        assertTrue(Evaluation.vraie(ega, c))
        // Règle de son en bas de la liste : elle gagne quand même sur la nuit.
        assertEquals("ega", Evaluation.gagnante(listOf(nuit, ega), c)?.id)
        // Plus de son : la nuit reprend.
        assertEquals("nuit", Evaluation.gagnante(listOf(nuit, ega), Contexte(t(23)))?.id)
        val musique = Regle("m", "M", listOf(Declencheur.Musique()), Action.Afficher("lune"))
        assertTrue(Evaluation.estSon(musique))
        assertFalse(Evaluation.estSon(nuit))
        assertEquals("m", Evaluation.gagnante(listOf(nuit, musique), Contexte(t(23), musique = listOf("?")))?.id)
    }

    @Test
    fun sonSansAccesNotifs() {
        val d = Declencheur.AppOuverte(listOf(AppChoisie("deezer.android.app", "Deezer"), AppChoisie("com.whatsapp", "WhatsApp")))
        assertTrue(Evaluation.vraie(d, Contexte(t(12), musique = listOf("?"))))
        assertFalse(Evaluation.vraie(Declencheur.AppOuverte(listOf(AppChoisie("com.whatsapp", "WhatsApp"))), Contexte(t(12), musique = listOf("?"))))
        assertFalse(Evaluation.vraie(d, Contexte(t(12))))
    }

    @org.junit.Test
    fun egaliseurWidget_fixeSansMusique_animeAvecMusique() {
        val v = fr.niakimbo.verso.reglage.Valeurs()
        val eg = fr.niakimbo.verso.generateur.EgaliseurLive
        org.junit.Assert.assertEquals(1, eg.pourWidget(v, false).images.size)
        org.junit.Assert.assertTrue(eg.pourWidget(v, true).images.size > 1)
        org.junit.Assert.assertTrue(eg.pourWidget(v.avec("mouvement", "toujours"), false).images.size > 1)
        org.junit.Assert.assertEquals(1, eg.pourWidget(v.avec("mouvement", "jamais"), true).images.size)
    }
}
