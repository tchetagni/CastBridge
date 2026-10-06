package castbridge.receiver

import android.content.Context
import castbridge.core.tv.Pin
import castbridge.core.tv.TvProfile

/** TV-side persistent settings (private SharedPreferences). */
class TvPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_tv", Context.MODE_PRIVATE)

    /** 6-digit PIN, generated on first launch and kept. Never logged. */
    @Synchronized fun pin(): String {
        sp.getString("pin", null)?.takeIf { Pin.isValidFormat(it) }?.let { return it }
        return Pin.generate().also { sp.edit().putString("pin", it).apply() }
    }

    /**
     * Stockage de la régénération du code (castbridge.core.tv.pin.PinRegenerator) : le nouveau code, les empreintes (SHA-256 tronquées, jamais le code)
     * des deux anciens et les heures des dernières régénérations s'écrivent en UNE seule opération synchrone (commit) : si elle échoue,
     * l'ancien code reste en place et la TV n'est jamais sans code valide.
     */
    fun pinStore(): castbridge.core.tv.pin.PinStore = object : castbridge.core.tv.pin.PinStore {
        override fun current() = pin()
        override fun fingerprints() = (sp.getString("pin_prev", "") ?: "").split(',').filter { it.isNotEmpty() }
        override fun times() = (sp.getString("pin_regen_times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
        override fun commit(pin: String, fingerprints: List<String>, times: List<Long>): Boolean {
            if (pin.length != Pin.LENGTH || !pin.all { it in '0'..'9' }) return false      // never write an invalid code
            return runCatching {
                sp.edit().putString("pin", pin).putString("pin_prev", fingerprints.joinToString(",")).putString("pin_regen_times", times.joinToString(",")).commit()
            }.getOrDefault(false)
        }
    }

    /**
     * Audit M1 d (docs/TV-ACTIVATION-CLE-USB.md): the code shown while the TV was LOCKED (its Wi-Fi activation route) is marked « exposé »; at the activation it is
     * replaced once ([castbridge.core.tv.activation.LockedPinRotation]). The new code and the cleared mark are written in ONE synchronous commit. Never logged.
     */
    fun lockedPinStore(): castbridge.core.tv.activation.LockedPinRotation.Store = object : castbridge.core.tv.activation.LockedPinRotation.Store {
        override fun exposed() = sp.getBoolean("pin_exposed_locked", false)
        override fun markExposed() { runCatching { sp.edit().putBoolean("pin_exposed_locked", true).commit() } }
        override fun replace(newPin: String): Boolean = synchronized(this@TvPrefs) {
            if (!Pin.isValidFormat(newPin)) return false
            runCatching { sp.edit().putString("pin", newPin).putBoolean("pin_exposed_locked", false).commit() }.getOrDefault(false)
        }
    }

    /** Storage/memory profile: defaults for a modest TV, overridable through /api/storage. */
    fun profile(): TvProfile = TvProfile(
        quotaBytes = getLong("quota_bytes", 0),
        deleteAfterPlay = getBool("delete_after_play", false),
        evictPlayed = getBool("evict_played", false),
        target = getString("storage_target", "auto") ?: "auto",
        minFreeAfterTransfer = getLong("min_free_after", 1L shl 30),
        heavyOnUsb = getBool("heavy_on_usb", true),
        heavyDriveId = getString("heavy_drive", "") ?: "",
    )

    fun saveProfile(p: TvProfile) {
        putLong("quota_bytes", p.quotaBytes); putBool("delete_after_play", p.deleteAfterPlay); putBool("evict_played", p.evictPlayed)
        putString("storage_target", p.target); putLong("min_free_after", p.minFreeAfterTransfer)
        putBool("heavy_on_usb", p.heavyOnUsb); putString("heavy_drive", p.heavyDriveId)
    }

    /** Periodic Internet probe (a 204 request to a third party): off by default, the TV stays quiet; the manual test and the features that need Internet still probe. */
    var netProbe: Boolean
        get() = getBool("net_probe", false)
        set(v) = putBool("net_probe", v)

    fun getString(key: String, def: String? = null): String? = sp.getString(key, def)
    fun putString(key: String, v: String?) = sp.edit().putString(key, v).apply()
    fun getLong(key: String, def: Long) = sp.getLong(key, def)
    fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    fun getBool(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
}
