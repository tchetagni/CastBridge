package castbridge.core.remote

import castbridge.core.quiz.Json
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import castbridge.core.tv.OpenTvReply
import castbridge.core.tv.OpenTvScreen
import castbridge.core.tv.ReceiverServer.Companion.q

/** Result of one remote action on the TV. [via]: "app" (a CastBridge screen), "system" (accessibility service), "audio" (AudioManager). */
data class Outcome(val ok: Boolean, val via: String? = null, val message: String? = null, val status: Int = if (ok) 200 else 409) {
    companion object {
        fun done(via: String) = Outcome(true, via)
        fun refused(message: String, status: Int = 409) = Outcome(false, null, message, status)
    }
}

/** What the TV app does with remote events (implemented on Android by the receiver's RemoteHub; fakes in tests). */
interface RemoteSink {
    fun key(k: RemoteKey, action: KeyAction, repeat: Int, target: RemoteTarget): Outcome
    fun text(value: String, mode: TextMode, target: RemoteTarget): Outcome
    fun global(g: RemoteGlobal): Outcome
    /** JSON object: front screen, accessibility mode, volume, focused text field… */
    fun stateJson(): String
    /** Shows the step-by-step help for the "whole TV" mode on the TV screen. */
    fun openSetup(): Outcome = Outcome.refused("indisponible", 501)
    /**
     * Brings CastBridge-TV in front of whatever is on the TV's screen, and says how it went (docs/REMOTE.md, « Ouvrir CastBridge-TV depuis le téléphone »).
     * May block a few seconds (it checks that the screen is really up). null = this sink cannot do it.
     */
    fun openTv(screen: OpenTvScreen?): OpenTvReply? = null
}

/**
 * The /api/remote/ routes (behind the PIN, like the rest of the admin API; see docs/REMOTE.md):
 *
 *  POST /api/remote/key?code=DPAD_UP[&action=press|down|up|long][&repeat=n][&target=auto|app|system][&sid=…&seq=n]
 *  POST /api/remote/text?value=…[&mode=insert|replace|clear][&target=…][&sid=…&seq=n]
 *  POST /api/remote/pointer?dx=…&dy=…[&tap=1][&target=…][&sid=…&seq=n]    (touchpad: slides become arrows)
 *  POST /api/remote/global?action=BACK|HOME|RECENTS|NOTIFICATIONS|QUICK_SETTINGS|POWER_DIALOG   (accessibility mode)
 *  POST /api/remote/ping            cheap keep-alive / latency probe
 *  GET  /api/remote/state           what the remote can do right now
 *  POST /api/remote/system/setup    opens the "whole TV" help on the TV
 *  POST /api/tv/open[?screen=home|library|player|games|quiz]   brings CastBridge-TV to the front of the other apps (the same action is `POST open` on the
 *                                   Bluetooth channel = /api/remote/open); the body is optional: {"screen":"library"}. Answer: {"opened","how","needs"}
 *
 * [sid]/[seq] make resends after a reconnection harmless ([SeqFilter]). A held key the phone never releases is released
 * by [releaseStale], which the app calls a few times a second while [holding] is true.
 */
class RemoteApi(private val sink: RemoteSink, private val now: () -> Long = System::currentTimeMillis) : ApiExtension {
    private val seqs = SeqFilter()
    private val holds = HoldTracker()
    private val holdTarget = java.util.concurrent.ConcurrentHashMap<RemoteKey, RemoteTarget>()

    val holding: Boolean get() = holds.any()

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path == OPEN_TV_PATH) return openTv(method, params)
        if (!path.startsWith(PREFIX)) return null
        val route = path.removePrefix(PREFIX)
        if (route == "state") return if (method == "GET") ApiReply(200, sink.stateJson()) else err(405, "use GET")
        if (method != "POST") return err(405, "use POST")
        return when (route) {
            "ping" -> ApiReply(200, """{"ok":true,"t":${now()}}""")
            "key" -> withSeq(params) { key(params) }
            "text" -> withSeq(params) { text(params) }
            "pointer" -> withSeq(params) { pointer(params) }
            "global" -> withSeq(params) { global(params) }
            "system/setup" -> reply(sink.openSetup(), null)
            "open" -> openTv(method, params)
            else -> err(404, "not found")
        }
    }

    /** `POST /api/tv/open` may carry a small JSON body `{"screen":"library"}`, but needs none (query `?screen=` or nothing at all: [castbridge.core.tv.ReceiverServer.BODY_OPTIONAL]). */
    override fun wantsBody(path: String) = path == OPEN_TV_PATH

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != OPEN_TV_PATH) return null
        if (method != "POST") return err(405, "use POST")
        if (body.size > MAX_OPEN_BODY) return err(413, "body too large")
        val inBody = try {
            val text = String(body, Charsets.UTF_8).trim()
            if (text.isEmpty()) null else when (val raw = Json.obj(text)["screen"]) {
                null -> null
                is String -> if (raw.isBlank()) null else OpenTvScreen.parse(raw) ?: return err(400, "screen must be ${OpenTvScreen.LIST}")
                else -> return err(400, "screen must be a string")
            }
        } catch (e: Json.ParseError) { return err(400, "the body must be a JSON object like {\"screen\":\"library\"}") }
        return openTv(method, params, inBody)
    }

    /** The query wins over the body; no screen at all means « wherever the TV was ». */
    private fun openTv(method: String, params: Map<String, String>, fromBody: OpenTvScreen? = null): ApiReply {
        if (method != "POST") return err(405, "use POST")
        val raw = params["screen"]?.takeIf { it.isNotBlank() }
        val screen = if (raw == null) fromBody else OpenTvScreen.parse(raw) ?: return err(400, "screen must be ${OpenTvScreen.LIST}")
        val r = sink.openTv(screen) ?: return err(501, "indisponible")
        // 200 whatever the result: the TV did answer, the phone reads {"opened":false,…} and says what to do
        return ApiReply(200, r.toJson())
    }

    /** Releases keys held longer than the hold timeout without news from the phone (link lost). */
    fun releaseStale(): List<RemoteKey> = holds.expired(now()).onEach { k ->
        runCatching { sink.key(k, KeyAction.UP, 0, holdTarget.remove(k) ?: RemoteTarget.AUTO) }
    }

    private inline fun withSeq(p: Map<String, String>, block: () -> ApiReply): ApiReply {
        val sid = p["sid"]?.takeIf { it.isNotEmpty() }
        if (sid != null && !SID.matches(sid)) return err(400, "bad sid")
        val seq = p["seq"]?.let { it.toLongOrNull()?.takeIf { v -> v >= 0 } ?: return err(400, "bad seq") }
        if ((sid == null) != (seq == null)) return err(400, "sid and seq go together")
        // Checked before the action runs, and recorded only if the request is valid (a 400 must not burn the number).
        synchronized(seqs) {
            if (sid != null && seq != null && (seqs.lastSeq(sid) ?: -1) >= seq)
                return ApiReply(200, """{"ok":true,"dup":true,"seq":$seq}""")
            val r = block()
            if (r.status != 400 && sid != null) seqs.accept(sid, seq)
            return r
        }
    }

    private fun target(p: Map<String, String>): RemoteTarget? = RemoteTarget.parse(p["target"])

    private fun key(p: Map<String, String>): ApiReply {
        val k = RemoteKey.parse(p["code"]) ?: return err(400, "unknown key")
        val a = KeyAction.parse(p["action"]) ?: return err(400, "action must be press, down, up or long")
        var rep = p["repeat"]?.let { it.toIntOrNull()?.takeIf { v -> v in 0..MAX_REPEAT } ?: return err(400, "bad repeat") } ?: 0
        val t = target(p) ?: return err(400, "target must be auto, app or system")
        when (a) {
            KeyAction.DOWN -> {
                // A repeat of a key the TV does not hold (released by the watchdog during a cut): it starts a new press.
                if (rep > 0 && !holds.isHeld(k)) rep = 0
                holds.down(k, now()); holdTarget[k] = t
            }
            // Already released by the watchdog (link lost for a moment): a late "up" must not produce a second release.
            KeyAction.UP -> if (!holds.up(k)) return ApiReply(200, """{"ok":true,"ignored":true}""").also { holdTarget.remove(k) }
                else holdTarget.remove(k)
            else -> {}
        }
        return reply(sink.key(k, a, if (a == KeyAction.DOWN) rep else 0, t), k)
    }

    private fun text(p: Map<String, String>): ApiReply {
        val mode = TextMode.parse(p["mode"]) ?: return err(400, "mode must be insert, replace or clear")
        val v = p["value"].orEmpty()
        if (v.length > MAX_TEXT) return err(400, "text too long (max $MAX_TEXT)")
        if (v.any { it < ' ' || it == '\u007f' }) return err(400, "control characters are not allowed")
        if (v.isEmpty() && mode == TextMode.INSERT) return err(400, "value required")
        val t = target(p) ?: return err(400, "target must be auto, app or system")
        return reply(sink.text(v, mode, t), null)
    }

    private fun pointer(p: Map<String, String>): ApiReply {
        val t = target(p) ?: return err(400, "target must be auto, app or system")
        if (p["tap"] == "1") return reply(sink.key(RemoteKey.DPAD_CENTER, KeyAction.PRESS, 0, t), RemoteKey.DPAD_CENTER)
        val dx = p["dx"]?.toIntOrNull()?.takeIf { it in -MAX_MOVE..MAX_MOVE } ?: return err(400, "dx required (-$MAX_MOVE..$MAX_MOVE)")
        val dy = p["dy"]?.toIntOrNull()?.takeIf { it in -MAX_MOVE..MAX_MOVE } ?: return err(400, "dy required (-$MAX_MOVE..$MAX_MOVE)")
        val step = p["step"]?.toIntOrNull()?.coerceIn(10, 1000) ?: 60
        val keys = TouchpadMapper.steps(dx, dy, step)
        var last = Outcome.done("none")
        for (k in keys) { last = sink.key(k, KeyAction.PRESS, 0, t); if (!last.ok) break }
        val r = reply(last, null)
        return if (last.ok) ApiReply(200, """{"ok":true,"via":${q(last.via ?: "")},"keys":[${keys.joinToString(",") { q(it.wire) }}]}""") else r
    }

    private fun global(p: Map<String, String>): ApiReply {
        val g = RemoteGlobal.parse(p["action"]) ?: return err(400, "action must be one of ${RemoteGlobal.values().joinToString { it.wire }}")
        return reply(sink.global(g), null)
    }

    private fun reply(o: Outcome, k: RemoteKey?): ApiReply =
        if (o.ok) ApiReply(200, """{"ok":true,"via":${q(o.via ?: "")}${k?.let { ",\"key\":${q(it.wire)}" } ?: ""}}""")
        else ApiReply(o.status, """{"ok":false,"error":${q(o.message ?: "refusé")},"message":${q(o.message ?: "refusé")}}""")

    private fun err(status: Int, msg: String) = ApiReply(status, """{"ok":false,"error":${q(msg)}}""")

    companion object {
        const val PREFIX = "/api/remote/"
        /** « Ouvrir CastBridge-TV » : the same action as [PREFIX] + `open`, under the name the phone's shortcut and the owner's scripts use. */
        const val OPEN_TV_PATH = "/api/tv/open"
        private const val MAX_OPEN_BODY = 1024
        const val MAX_TEXT = 500
        const val MAX_REPEAT = 100_000
        const val MAX_MOVE = 4000
        private val SID = Regex("^[A-Za-z0-9_-]{1,32}$")
    }
}
