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
     * the probe through it worked; else offline: the tunnel makes no attempt and queues nothing.
     */
    fun choose(directOk: Boolean, gatewayConnected: Boolean, gatewayOk: Boolean): TunnelPath = when {
        directOk -> TunnelPath.DIRECT
        gatewayConnected && gatewayOk -> TunnelPath.GATEWAY
        else -> TunnelPath.OFFLINE
    }
}
