package fr.niakimbo.verso.rapide

import android.content.Context
import android.os.SystemClock
import fr.niakimbo.verso.VersoApp
import fr.niakimbo.verso.widget.WidgetMaj
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Chronomètre Glyph (28/10, Nico : « Nothing le propose déjà mais c'est sous-coté ») :
 * ▶ démarre et affiche le modèle Chrono au dos ; ⏸ met en pause (le temps reste affiché) ;
 * ↺ remet à zéro et rend le dos au modèle d'avant.
 *
 * Le temps est gardé en heure murale : il survit à l'arrêt du processus et au redémarrage.
 */
object Chrono {
    const val ID = "chrono"

    data class Etat(val enMarche: Boolean, val cumulMs: Long, val debutMs: Long) {
        /** Temps écoulé maintenant. */
        fun ms(maintenant: Long = System.currentTimeMillis()): Long =
            cumulMs + if (enMarche) (maintenant - debutMs).coerceAtLeast(0) else 0

        val aZero: Boolean get() = !enMarche && cumulMs == 0L
    }

    private val _etat = MutableStateFlow(Etat(false, 0, 0))
    val etat: StateFlow<Etat> = _etat

    @Volatile
    private var lu = false

    fun lire(c: Context): Etat {
        if (!lu) {
            val p = prefs(c)
            _etat.value = Etat(p.getBoolean("marche", false), p.getLong("cumul", 0), p.getLong("debut", 0))
            lu = true
        }
        return _etat.value
    }

    /** ▶ / ⏸. Au démarrage, le chrono passe au dos (le modèle d'avant est retenu pour ↺). */
    suspend fun basculer(app: VersoApp) {
        val e = lire(app)
        val maintenant = System.currentTimeMillis()
        val nouveau = if (e.enMarche) Etat(false, e.ms(maintenant), 0) else Etat(true, e.cumulMs, maintenant)
        ecrire(app, nouveau)
        if (nouveau.enMarche && app.store.modeleActif != ID) {
            prefs(app).edit().putString("avant", app.store.modeleActif).apply()
            app.catalogue.resoudre(ID)?.let {
                app.matrix.connect()
                app.player.jouer(it)
            }
        }
        WidgetMaj.majTous(app, forcer = true)
    }

    /** ↺ : zéro, et le dos revient au modèle d'avant le chrono. */
    suspend fun remettre(app: VersoApp) {
        ecrire(app, Etat(false, 0, 0))
        if (app.store.modeleActif == ID) {
            prefs(app).getString("avant", null)?.let { app.catalogue.resoudre(it) }?.let {
                app.matrix.connect()
                app.player.jouer(it)
            }
        }
        WidgetMaj.majTous(app, forcer = true)
    }

    /** Base pour un Chronometer de widget (horloge « depuis le démarrage » du téléphone). */
    fun baseWidget(e: Etat): Long = SystemClock.elapsedRealtime() - e.ms()

    fun signature(c: Context): String = lire(c).let { "${it.enMarche}.${it.cumulMs}.${it.debutMs}" }

    private fun ecrire(c: Context, e: Etat) {
        prefs(c).edit().putBoolean("marche", e.enMarche).putLong("cumul", e.cumulMs).putLong("debut", e.debutMs).apply()
        _etat.value = e
        lu = true
    }

    private fun prefs(c: Context) = c.getSharedPreferences("verso_chrono", Context.MODE_PRIVATE)
}
