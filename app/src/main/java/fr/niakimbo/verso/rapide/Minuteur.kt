package fr.niakimbo.verso.rapide

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.widget.WidgetMaj
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Minuteur Glyph (28/10, Nico : « je voulais à la base un minuteur ») : durée choisie à la roulette
 * façon iPhone ([MinuteurActivity]), sablier au dos ([fr.niakimbo.verso.generateur.MinuteurLive]),
 * et à la fin une vraie sonnerie Verso, forte, même app fermée et téléphone en veille ([Sonnerie]).
 *
 * Ce n'est PAS une alarme de l'app Horloge : alarme exacte Android à Verso (USE_EXACT_ALARM),
 * qui ne compte donc pas comme « prochaine alarme » pour les règles automatiques.
 * Le temps est gardé en heure murale : il survit à l'arrêt du processus (et le redémarrage reprogramme l'alarme).
 */
object Minuteur {
    const val ID = "minuteur"

    /**
     * [dureeMs] : durée choisie. En marche : fin à [finMs] (heure murale). En pause : [resteMs] restant.
     * [sonne] : terminé, la sonnerie retentit.
     */
    data class Etat(
        val enMarche: Boolean = false,
        val dureeMs: Long = 0,
        val finMs: Long = 0,
        val resteMs: Long = 0,
        val sonne: Boolean = false,
    ) {
        /** Temps restant maintenant. */
        fun reste(maintenant: Long = System.currentTimeMillis()): Long = when {
            sonne -> 0
            enMarche -> (finMs - maintenant).coerceAtLeast(0)
            else -> resteMs
        }

        /** Rien en cours (ni décompte, ni pause, ni sonnerie). */
        val inactif: Boolean get() = !enMarche && !sonne && resteMs == 0L

        val enPause: Boolean get() = !enMarche && !sonne && resteMs > 0

        /** 0 = plein (vient de commencer), 1 = fini. */
        fun ecoule(maintenant: Long = System.currentTimeMillis()): Float =
            if (inactif || dureeMs <= 0) 0f else (1f - reste(maintenant).toFloat() / dureeMs).coerceIn(0f, 1f)
    }

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat

    @Volatile
    private var lu = false

    fun lire(c: Context): Etat {
        if (!lu) {
            val p = prefs(c)
            _etat.value = Etat(
                p.getBoolean("marche", false), p.getLong("duree", 0), p.getLong("fin", 0),
                p.getLong("reste", 0), p.getBoolean("sonne", false),
            )
            lu = true
        }
        return _etat.value
    }

    /** Dernière durée choisie (la roulette s'ouvre dessus, ▶ du widget la relance). 5 min par défaut. */
    fun derniereDuree(c: Context): Long = prefs(c).getLong("derniere", 5 * 60_000L)

    /** Dernières durées lancées (3 au plus, la plus récente d'abord). */
    fun recentes(c: Context): List<Long> =
        prefs(c).getString("recentes", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { it > 0 }

    // ----- Réglages de la sonnerie -----

    enum class Son(val label: String) { PULSE("Verso Pulse"), SIRENE("Verso Sirène"), TELEPHONE("Alarme du téléphone") }

    fun son(c: Context): Son = Son.entries.firstOrNull { it.name == prefs(c).getString("son", null) } ?: Son.PULSE
    fun setSon(c: Context, s: Son) = prefs(c).edit().putString("son", s.name).apply()
    /** Volume de la sonnerie, en % du volume « alarme » du téléphone (réglé pendant qu'elle sonne, remis après). */
    fun volume(c: Context) = prefs(c).getInt("volume", 60)
    fun setVolume(c: Context, v: Int) = prefs(c).edit().putInt("volume", v.coerceIn(5, 100)).apply()

    /** Progressif : le son monte doucement jusqu'au volume choisi (en 30 s). */
    fun progressif(c: Context) = prefs(c).getBoolean("progressif", true)
    fun setProgressif(c: Context, v: Boolean) = prefs(c).edit().putBoolean("progressif", v).apply()
    fun vibre(c: Context) = prefs(c).getBoolean("vibre", true)
    fun setVibre(c: Context, v: Boolean) = prefs(c).edit().putBoolean("vibre", v).apply()

    /** Onglet affiché (Minuteur ou Chrono) : c'est aussi ce que montre le widget. */
    fun modeChrono(c: Context) = prefs(c).getBoolean("mode_chrono", false)
    fun setModeChrono(app: VersoApp, v: Boolean) {
        prefs(app).edit().putBoolean("mode_chrono", v).apply()
        app.appScope.launch { WidgetMaj.majTous(app, forcer = true) }
    }

    // ----- Commandes -----

    /** Lance le décompte de [dureeMs] ; le sablier passe au dos (le modèle d'avant est retenu). */
    suspend fun demarrer(app: VersoApp, dureeMs: Long) {
        if (dureeMs <= 0) return
        Sonnerie.arreter(app)
        val p = prefs(app)
        val rec = (listOf(dureeMs) + recentes(app).filter { it != dureeMs }).take(3)
        p.edit().putLong("derniere", dureeMs).putString("recentes", rec.joinToString(",")).putBoolean("mode_chrono", false).apply()
        ecrire(app, Etat(enMarche = true, dureeMs = dureeMs, finMs = System.currentTimeMillis() + dureeMs))
        programmer(app)
        afficherAuDos(app)
        app.journalToy.ajouter("Minuteur : ${texte(dureeMs)}")
        WidgetMaj.majTous(app, forcer = true)
    }

    /** ⏸ / ▶ : pause, reprise, ou relance de la dernière durée si rien n'est en cours. */
    suspend fun basculer(app: VersoApp) {
        val e = lire(app)
        when {
            e.sonne -> arreterSonnerie(app)
            e.enMarche -> {
                ecrire(app, e.copy(enMarche = false, resteMs = e.reste().coerceAtLeast(1)))
                annulerAlarme(app)
                WidgetMaj.majTous(app, forcer = true)
            }
            e.enPause -> {
                ecrire(app, e.copy(enMarche = true, finMs = System.currentTimeMillis() + e.resteMs, resteMs = 0))
                programmer(app)
                afficherAuDos(app)
                WidgetMaj.majTous(app, forcer = true)
            }
            else -> demarrer(app, derniereDuree(app))
        }
    }

    /** +1 min (en marche, en pause, ou pendant la sonnerie : ça relance 1 min). */
    suspend fun ajouter(app: VersoApp, ms: Long = 60_000L) {
        val e = lire(app)
        when {
            e.sonne -> {
                Sonnerie.arreter(app)
                ecrire(app, Etat(enMarche = true, dureeMs = ms, finMs = System.currentTimeMillis() + ms))
                programmer(app)
                afficherAuDos(app)
            }
            e.enMarche -> {
                ecrire(app, e.copy(dureeMs = e.dureeMs + ms, finMs = e.finMs + ms))
                programmer(app)
            }
            e.enPause -> ecrire(app, e.copy(dureeMs = e.dureeMs + ms, resteMs = e.resteMs + ms))
            else -> return demarrer(app, ms)
        }
        WidgetMaj.majTous(app, forcer = true)
    }

    /** ↺ / Annuler : tout s'arrête et le dos revient au modèle d'avant. */
    suspend fun annuler(app: VersoApp) {
        annulerAlarme(app)
        Sonnerie.arreter(app)
        ecrire(app, Etat(dureeMs = lire(app).dureeMs))
        rendreLeDos(app)
        WidgetMaj.majTous(app, forcer = true)
    }

    /** Arrêter la sonnerie (bouton Arrêter de l'écran, de la notification ou du widget). */
    suspend fun arreterSonnerie(app: VersoApp) = annuler(app)

    /** L'alarme est arrivée : c'est fini, ça sonne. */
    internal suspend fun fin(app: VersoApp) {
        val e = lire(app)
        if (!e.enMarche) return
        // Alarme arrivée un peu tôt (rare) : on reprogramme.
        if (e.finMs - System.currentTimeMillis() > 1_500) {
            programmer(app)
            return
        }
        ecrire(app, e.copy(enMarche = false, resteMs = 0, sonne = true))
        app.journalToy.ajouter("Minuteur terminé : sonnerie")
        afficherAuDos(app)
        Sonnerie.demarrer(app)
        WidgetMaj.majTous(app, forcer = true)
    }

    /** Téléphone redémarré ou Verso mis à jour : on reprogramme (ou on oublie un minuteur fini depuis longtemps). */
    suspend fun reprendre(app: VersoApp) {
        val e = lire(app)
        when {
            e.sonne -> annuler(app)
            !e.enMarche -> Unit
            e.finMs <= System.currentTimeMillis() - 60_000 -> annuler(app)
            else -> programmer(app)
        }
    }

    private suspend fun afficherAuDos(app: VersoApp) {
        if (app.store.modeleActif != ID && app.store.modeleActif != Chrono.ID) {
            prefs(app).edit().putString("avant", app.store.modeleActif).apply()
        }
        if (app.store.modeleActif == ID) return
        app.catalogue.resoudre(ID)?.let {
            app.matrix.connect()
            app.player.jouer(it)
        }
    }

    private suspend fun rendreLeDos(app: VersoApp) {
        if (app.store.modeleActif != ID) return
        prefs(app).getString("avant", null)?.let { app.catalogue.resoudre(it) }?.let {
            app.matrix.connect()
            app.player.jouer(it)
        }
    }

    private fun intention(c: Context) = PendingIntent.getBroadcast(
        c, 7, Intent(c, AlarmeMinuteur::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun programmer(app: VersoApp) {
        val e = lire(app)
        val am = app.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.finMs, intention(app))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.finMs, intention(app))
    }

    private fun annulerAlarme(c: Context) = c.getSystemService(AlarmManager::class.java).cancel(intention(c))

    /** Base d'un Chronometer à rebours de widget (horloge « depuis le démarrage » du téléphone). */
    fun baseWidget(c: Context, e: Etat): Long = SystemClock.elapsedRealtime() + if (e.inactif) derniereDuree(c) else e.reste()

    fun signature(c: Context): String = lire(c).let { "${it.enMarche}.${it.finMs}.${it.resteMs}.${it.sonne}.${modeChrono(c)}.${derniereDuree(c)}" }

    /** « 1 h 05 min », « 5 min », « 45 s ». */
    fun texte(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s / 60) % 60
        val sec = s % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add(if (h > 0) "%02d min".format(m) else "$m min")
            if (sec > 0) add("$sec s")
        }.ifEmpty { listOf("0 s") }.joinToString(" ")
    }

    private fun ecrire(c: Context, e: Etat) {
        prefs(c).edit()
            .putBoolean("marche", e.enMarche).putLong("duree", e.dureeMs).putLong("fin", e.finMs)
            .putLong("reste", e.resteMs).putBoolean("sonne", e.sonne)
            .apply()
        _etat.value = e
        lu = true
    }

    internal fun prefs(c: Context) = c.getSharedPreferences("verso_minuteur", Context.MODE_PRIVATE)
}

/** Fin du minuteur (alarme exacte) : même app fermée et téléphone en veille. */
class AlarmeMinuteur : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VersoApp
        val fin = goAsync()
        app.appScope.launch {
            try {
                Minuteur.fin(app)
            } finally {
                fin.finish()
            }
        }
    }
}
