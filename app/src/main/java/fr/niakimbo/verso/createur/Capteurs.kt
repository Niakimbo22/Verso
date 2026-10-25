package fr.niakimbo.verso.createur

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Informations du téléphone lues par les blocs (batterie). Lecture du dernier état diffusé
 * par le système, sans abonnement : rien ne tourne quand aucun bloc ne la demande.
 */
object Capteurs {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cache: Pair<Long, Int>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Niveau de batterie 0-100 (relu au plus toutes les 20 s). 100 si inconnu. */
    fun batterie(): Int {
        val maintenant = System.currentTimeMillis()
        cache?.let { (quand, niveau) -> if (maintenant - quand < 20_000) return niveau }
        val ctx = appContext ?: return 100
        val niveau = try {
            val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val n = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val max = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (n < 0 || max <= 0) 100 else (n * 100 / max).coerceIn(0, 100)
        } catch (_: Exception) {
            100
        }
        cache = maintenant to niveau
        return niveau
    }
}
