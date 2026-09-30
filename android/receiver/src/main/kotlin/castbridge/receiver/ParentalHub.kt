package castbridge.receiver

import android.content.Context
import android.content.SharedPreferences
import castbridge.core.parental.ActivityEntry
import castbridge.core.parental.ActivityLog
import castbridge.core.parental.DailyUsage
import castbridge.core.parental.Parental
import castbridge.core.parental.Restrictions
import java.io.File
import java.util.Calendar

/**
 * The TV's parental control: a local activity log (kept on the TV, never sent anywhere) and the restrictions.
 * Everything CastBridge does is recorded here, and the optional accessibility service adds which app is in the
 * foreground. The parent opens the "Parental" screen (protected by its own PIN) to read the log and set rules.
 */
object ParentalHub {
    private const val PREFS = "castbridge_parental"

    private lateinit var prefs: SharedPreferences
    private lateinit var log: ActivityLog

    @Synchronized fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        log = ActivityLog(File(ctx.filesDir, "activity.log"))
    }

    // ------------------------------------------------------------------ restrictions

    fun restrictions(): Restrictions = Restrictions(
        enabled = prefs.getBoolean("enabled", false),
        blockedAfterMin = prefs.getInt("blocked_after", -1),
        blockedBeforeMin = prefs.getInt("blocked_before", -1),
        requirePinForPlayback = prefs.getBoolean("require_pin", false),
        dailyLimitMin = prefs.getInt("daily_limit", 0),
    )

    fun setRestrictions(r: Restrictions) {
        prefs.edit().putBoolean("enabled", r.enabled)
            .putInt("blocked_after", r.blockedAfterMin)
            .putInt("blocked_before", r.blockedBeforeMin)
            .putBoolean("require_pin", r.requirePinForPlayback)
            .putInt("daily_limit", r.dailyLimitMin).apply()
        record(Parental.TYPE_SETTINGS, "Réglages parentaux mis à jour (${describe(r)})")
    }

    private fun describe(r: Restrictions): String = when {
        !r.enabled -> "désactivés"
        else -> buildList {
            add("actifs")
            if (r.blockedAfterMin >= 0 && r.blockedBeforeMin >= 0) add("heures calmes ${Restrictions.fmt(r.blockedAfterMin)}–${Restrictions.fmt(r.blockedBeforeMin)}")
            if (r.requirePinForPlayback) add("code parental pour lire")
            if (r.dailyLimitMin > 0) add("limite ${r.dailyLimitMin} min/jour")
        }.joinToString(", ")
    }

    // ------------------------------------------------------------------ parental PIN

    /** "4444" = default PIN. */
    fun pin(): String = prefs.getString("pin", null) ?: "4444"

    fun hasPin(): Boolean = pin().isNotEmpty()

    fun setPin(v: String) { prefs.edit().putString("pin", v).apply() }

    /** Constant-time comparison so a wrong PIN is not guessable by timing. */
    fun checkPin(v: String): Boolean = java.security.MessageDigest.isEqual(pin().toByteArray(), v.toByteArray())

    /** true if [v] opens the parental administration (no PIN set = open). */
    fun pinOk(v: String): Boolean = !hasPin() || checkPin(v)

    /** When the parental PIN was last verified for playback (in-memory: resets on TV restart). */
    @Volatile private var verifiedAt = 0L

    /** Verifies the parental PIN and, when correct, unlocks playback for a while. */
    fun verifyPin(v: String): Boolean = if (checkPin(v)) { verifiedAt = System.currentTimeMillis(); true } else false

    // ------------------------------------------------------------------ recording

    val dailyUsage = DailyUsage()

    fun record(type: String, label: String) = log.record(type, label)

    fun onPlay(title: String) { dailyUsage.playStarted(); record(Parental.TYPE_PLAY, title) }

    fun onStop(title: String, watchedMs: Long) { dailyUsage.playStopped(); record(Parental.TYPE_STOP, "$title · ${fmtDur(watchedMs)}") }

    fun onApp(label: String) = record(Parental.TYPE_APP, label)

    fun onFile(label: String) = record(Parental.TYPE_FILE, label)

    fun onDownload(label: String) = record(Parental.TYPE_DOWNLOAD, label)

    fun onGame(label: String) = record(Parental.TYPE_GAME, label)

    fun list(): List<ActivityEntry> = log.list()

    fun clear() { log.clear(); record(Parental.TYPE_SETTINGS, "Historique effacé") }

    // ------------------------------------------------------------------ enforcement

    /** Minutes since midnight, local time. */
    private fun nowMinutes(): Int { val c = Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) }

    /** null = allowed, else why playback is blocked right now. */
    fun playbackBlocked(): String? {
        val r = restrictions()
        return r.playbackBlocked(nowMinutes()) ?: r.dailyLimitReached(dailyUsage.usedMinutes())
    }

    /** true when a parental PIN is asked before playing and it has not been verified recently. */
    fun needsPinForPlayback(): Boolean =
        restrictions().enabled && restrictions().requirePinForPlayback && System.currentTimeMillis() - verifiedAt > 2 * 3_600_000L

    /** null = allowed, else why playback must be refused (night window, daily limit, then the parental PIN). */
    fun gated(): String? = playbackBlocked() ?: if (needsPinForPlayback()) "Un code parental est demandé pour lire une vidéo." else null

    fun fmtDur(ms: Long): String = when {
        ms < 0 -> ""
        ms < 60_000 -> "${ms / 1000} s"
        else -> "${ms / 60_000} min"
    }

    // ------------------------------------------------------------------ phone admin (HTTP API, behind the parental PIN)

    private fun q(s: String) = castbridge.core.tv.ReceiverServer.q(s)

    fun statusJson(): String {
        val r = restrictions()
        return buildString {
            append("{\"pinSet\":").append(hasPin())
            append(",\"enabled\":").append(r.enabled)
            append(",\"blockedAfter\":").append(if (r.blockedAfterMin >= 0) q(Restrictions.fmt(r.blockedAfterMin)) else "null")
            append(",\"blockedBefore\":").append(if (r.blockedBeforeMin >= 0) q(Restrictions.fmt(r.blockedBeforeMin)) else "null")
            append(",\"requirePin\":").append(r.requirePinForPlayback)
            append(",\"dailyLimit\":").append(r.dailyLimitMin)
            append(",\"usedMinutes\":").append(dailyUsage.usedMinutes())
            append(",\"logCount\":").append(log.size())
            append('}')
        }
    }

    fun logJson(): String = "{\"entries\":[" + list().joinToString(",") { e ->
        "{\"ts\":${e.ts},\"type\":${q(e.type)},\"label\":${q(e.label)}}"
    } + "]}"
}
