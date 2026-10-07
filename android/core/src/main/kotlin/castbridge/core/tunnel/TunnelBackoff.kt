package castbridge.core.tunnel

import castbridge.core.owner.SafeFile
import java.io.File
import java.util.Random

/** Reconnection delays: 5 s, 10 s, 20 s ... capped at 10 min, each with +-25 % jitter (never above the cap), so that a fleet of TVs does not come back all at once after a server restart. */
class TunnelBackoff(private val random: Random = Random(), private val baseMs: Long = BASE_MS, private val capMs: Long = CAP_MS) {
    private var failures = 0

    val attempts get() = failures

    /** The delay to wait after one more failure. */
    fun next(minDelayMs: Long = 0L): Long {
        val exp = baseMs shl failures.coerceAtMost(20)
        if (failures < 20) failures++
        val raw = exp.coerceIn(baseMs, capMs)
        val jittered = (raw * (0.75 + 0.5 * random.nextDouble())).toLong().coerceIn(baseMs / 2, capMs)
        return maxOf(jittered, minDelayMs)
    }

    fun reset() { failures = 0 }

    companion object {
        const val BASE_MS = 5_000L
        const val CAP_MS = 10 * 60_000L
    }
}

/** Host-key checking for the tunnel's SSH client: the fingerprint the server announced at enrollment, else trust on first use, pinned and enforced afterwards. */
object HostKeyPin {
    enum class Decision { ACCEPT, ACCEPT_AND_PIN, REJECT }
    data class Verdict(val decision: Decision, val reason: String)

    /** [announced] = `hostKeyFingerprint` of the enrollment reply (null when the server gave none); [pinned] = what this TV saw and kept before; [presented] = the key now offered ("SHA256:..."). */
    fun decide(announced: String?, pinned: String?, presented: String): Verdict = when {
        announced != null && announced == presented -> Verdict(if (pinned == presented) Decision.ACCEPT else Decision.ACCEPT_AND_PIN, "empreinte annoncée par le serveur")
        announced != null -> Verdict(Decision.REJECT, "empreinte de l'hôte différente de celle annoncée par le serveur")
        pinned == null -> Verdict(Decision.ACCEPT_AND_PIN, "première connexion : empreinte retenue")
        pinned == presented -> Verdict(Decision.ACCEPT, "empreinte retenue")
        else -> Verdict(Decision.REJECT, "empreinte de l'hôte différente de celle retenue à la première connexion")
    }
}

/** The pinned host-key fingerprints, one line `host:port<TAB>fingerprint`, written through [SafeFile]. */
class HostKeyPins(private val file: File) {
    @Synchronized fun get(host: String, port: Int): String? = read()["$host:$port"]

    @Synchronized fun pin(host: String, port: Int, fingerprint: String) {
        val m = read().toMutableMap(); if (m["$host:$port"] == fingerprint) return
        m["$host:$port"] = fingerprint
        SafeFile.write(file, m.entries.joinToString("") { "${it.key}\t${it.value}\n" })
    }

    private fun read(): Map<String, String> = SafeFile.read(file)?.text?.lineSequence()?.mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap().orEmpty()
}

/** Which way the tunnel's TCP connection may leave the TV. */
enum class TunnelPath { DIRECT, GATEWAY, OFFLINE }

object TunnelConnectivity {
    /**
     * The TV's own network (Wi-Fi / Ethernet) when it reaches the Internet; else the phone's Internet shared over Bluetooth (CastBridge-TV's local SOCKS5 proxy), when a phone is attached AND
     * the end-to-end check through it worked AND the pipe is for the tunnel ([assistPipe]); else offline: the tunnel makes no attempt and queues nothing.
     * relay-R1: no definition of its own any more, the path is read from the single truth [castbridge.core.connect.NetState].
     *
     * R-45 (audit anti-régression 2026-10-07 b, I-7): the tunnel used to ride ANY open pipe and keep its SSH session on it for ever (a permanent reverse tunnel waiting for an expert); the
     * phone's pipe stops 10 minutes after its last open connection, and that session counts as one: the pipe, its notification and its keep-alive on mobile data never ended, the 5 MB
     * a day went, then the game was refused. Now the pipe is the tunnel's ONLY while an assistance session was asked for or the pipe is the phone owner's own manual sharing
     * ([assistPipe], [AssistPipePolicy]); otherwise [TunnelPath.OFFLINE]: the machine closes the session, the pipe's open-connection count falls to zero and its idle stop can run.
     */
    fun path(net: castbridge.core.connect.NetState, assistPipe: Boolean = false): TunnelPath = when (net) {
        castbridge.core.connect.NetState.DIRECT -> TunnelPath.DIRECT
        castbridge.core.connect.NetState.VIA_RELAY -> if (assistPipe) TunnelPath.GATEWAY else TunnelPath.OFFLINE
        castbridge.core.connect.NetState.NONE -> TunnelPath.OFFLINE
    }

    fun choose(directOk: Boolean, gatewayConnected: Boolean, gatewayOk: Boolean, assistPipe: Boolean = false): TunnelPath =
        path(castbridge.core.connect.NetStates.of(directOk, gatewayConnected, gatewayOk), assistPipe)
}

/**
 * Who may use the phone's pipe among the TV's tasks of assistance (R-45): the tunnel, ONLY during
 *  - an assistance session the user asked for ([requested]: « Se connecter maintenant » on the TV), for [windowMs] from the request (asking again renews it; a clock wound back never
 *    stretches it beyond the window);
 *  - or while the attached pipe is the phone owner's MANUAL sharing (the TV had not asked for it: [attached] with `tvAskedForIt` false), which the owner controls himself.
 * A pipe the TV asked for to play, to sync the wallet or to update is never the tunnel's. Pure: the time and the identity of the current pipe ([TvNet.attachId]) come from the caller.
 */
class AssistPipePolicy(private val windowMs: Long = ASSIST_WINDOW_MS) {
    @Volatile private var until = 0L
    @Volatile private var manualAttach = NONE

    /** The user asked for assistance now. */
    fun requested(now: Long) { until = now + windowMs }

    /** The assistance ended (or was withdrawn): the pipe is no longer the tunnel's. */
    fun released() { until = 0L }

    /** A pipe attached (identity [attachId]); [tvAskedForIt] = a live TV need (game, wallet, update, assistance) had asked for a pipe when it attached. */
    fun attached(attachId: Int, tvAskedForIt: Boolean) { manualAttach = if (tvAskedForIt) NONE else attachId }

    /** The phone's link ended. */
    fun detached() { manualAttach = NONE }

    fun allowed(now: Long, attachId: Int): Boolean = (until - now) in 1..windowMs || (attachId != NONE && attachId == manualAttach)

    companion object {
        /** How long an assistance session asked for with « Se connecter maintenant » may use the pipe: time for the expert to come, not for ever. */
        const val ASSIST_WINDOW_MS = 30 * 60_000L
        private const val NONE = -1
    }
}
