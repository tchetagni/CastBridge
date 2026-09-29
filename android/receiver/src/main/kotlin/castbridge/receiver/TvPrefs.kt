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

    /** Storage/memory profile: defaults for a modest TV, overridable through /api/storage. */
    fun profile(): TvProfile = TvProfile(
        quotaBytes = getLong("quota_bytes", 0),
        deleteAfterPlay = getBool("delete_after_play", false),
        evictPlayed = getBool("evict_played", false),
    )

    fun saveProfile(p: TvProfile) {
        putLong("quota_bytes", p.quotaBytes); putBool("delete_after_play", p.deleteAfterPlay); putBool("evict_played", p.evictPlayed)
    }

    fun getString(key: String, def: String? = null): String? = sp.getString(key, def)
    fun putString(key: String, v: String?) = sp.edit().putString(key, v).apply()
    fun getLong(key: String, def: Long) = sp.getLong(key, def)
    fun putLong(key: String, v: Long) = sp.edit().putLong(key, v).apply()
    fun getBool(key: String, def: Boolean) = sp.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
}
