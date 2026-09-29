package castbridge.receiver

import android.content.Context
import castbridge.core.tv.Pin

/** TV-side persistent settings (private SharedPreferences). */
class TvPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_tv", Context.MODE_PRIVATE)

    /** 6-digit PIN, generated on first launch and kept. Never logged. */
    @Synchronized fun pin(): String {
        sp.getString("pin", null)?.takeIf { Pin.isValidFormat(it) }?.let { return it }
        return Pin.generate().also { sp.edit().putString("pin", it).apply() }
    }

    fun getString(key: String, def: String? = null): String? = sp.getString(key, def)
    fun putString(key: String, v: String?) = sp.edit().putString(key, v).apply()
    fun getLong(key: String, def: Long) = sp.getLong(key, def)
    fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    fun getBool(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
}
