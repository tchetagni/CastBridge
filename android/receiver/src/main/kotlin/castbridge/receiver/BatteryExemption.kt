package castbridge.receiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import castbridge.core.tv.BatteryExemptionPolicy

/**
 * Exemption d'optimisation de batterie (docs/TV-SERVICE-DEMARRAGE.md) : une ligne du MENU, proposée seulement si l'app n'est pas déjà
 * exemptée ET si l'écran système existe sur ce boîtier. Une fois choisie (accord ou refus), elle ne revient plus : jamais de boucle.
 */
object BatteryExemption {
    private const val ASKED = "battery_exemption_asked"

    /** L'intent à lancer si la ligne doit être proposée, sinon null. */
    fun offerIntent(ctx: Context, prefs: TvPrefs): Intent? {
        if (Build.VERSION.SDK_INT < 23) return null
        val pm = ctx.getSystemService(PowerManager::class.java)
        val ignoring = runCatching { pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true }.getOrDefault(true)
        @Suppress("BatteryLife")
        val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
        val exists = runCatching { i.resolveActivity(ctx.packageManager) != null }.getOrDefault(false)
        return i.takeIf { BatteryExemptionPolicy.shouldOffer(ignoring, exists, prefs.getBool(ASKED, false), Build.VERSION.SDK_INT) }
    }

    fun markAsked(prefs: TvPrefs) = prefs.putBool(ASKED, true)
}
