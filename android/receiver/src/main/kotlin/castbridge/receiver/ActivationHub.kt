package castbridge.receiver

import android.content.Context
import android.content.SharedPreferences
import castbridge.core.activation.Activation
import castbridge.core.activation.ActivationKeys
import java.net.NetworkInterface

/**
 * Offline activation of the TV: a "machine code" (from the MAC address when readable, else a stored UUID) is shown on the
 * TV; the seller computes the token (server /admin/activation) and the user types it. The state lives in preferences only.
 * When [ActivationKeys.SECRET] is empty, activation is disabled and the TV is always "activated".
 */
object ActivationHub {
    private const val PREFS = "castbridge_activation"
    private lateinit var prefs: SharedPreferences

    @Synchronized fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun enabled(): Boolean = ActivationKeys.SECRET.isNotBlank()

    /** Stable per device; stored so it never changes (a randomized Wi-Fi MAC would otherwise break the token). */
    fun fingerprint(): String {
        prefs.getString("fingerprint", null)?.let { return it }
        val mac = macAddress()
        val fp = if (mac != null) "mac:$mac" else "uuid:${java.util.UUID.randomUUID()}"
        prefs.edit().putString("fingerprint", fp).apply()
        return fp
    }

    fun requestCode(): String = Activation.requestCode(fingerprint())

    /** What the TV shows on screen: the machine code Caesar-shifted (the seller reverses it with the same shift). */
    fun displayedCode(): String = Activation.caesar(requestCode(), Activation.CAESAR_SHIFT)

    fun activated(): Boolean = prefs.getBoolean("activated", false) || !enabled()

    /** Applies a token; true = unlocked (also true when activation is disabled). */
    fun activate(token: String): Boolean {
        if (!enabled()) return true
        val ok = Activation.verify(requestCode(), token, ActivationKeys.SECRET) ||
                 (macAddress()?.let { Activation.verify(it, token, ActivationKeys.SECRET) } == true)
        if (ok) prefs.edit().putBoolean("activated", true).apply()
        return ok
    }

    fun statusJson(): String {
        val q = castbridge.core.tv.ReceiverServer::q
        return """{"enabled":${enabled()},"activated":${activated()},"requestCode":${q(if (enabled()) displayedCode() else "")}}"""
    }

    private fun macAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { !it.isLoopback && it.hardwareAddress != null && it.hardwareAddress!!.isNotEmpty() }
            .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 }
            .firstOrNull()
            ?.hardwareAddress?.joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
