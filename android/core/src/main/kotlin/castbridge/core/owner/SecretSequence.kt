package castbridge.core.owner

import java.security.SecureRandom

enum class RemoteKey(val navigation: Boolean) { UP(true), DOWN(true), LEFT(true), RIGHT(true), OK(true), BACK(true), MENU(true), OTHER(false) }

/**
 * Detector of the key combination that reveals the hidden owner panel on the TV start screen (docs/TRIAL-EDITION.md § Console propriétaire).
 * HONEST: the combination protects nothing, it only hides an interface; every owner command still needs a signed, device-bound, single-use order.
 *
 * Rules: the attempt starts with the first key and must finish within [windowMs] (default 8 s); the FIRST wrong navigation key resets the attempt
 * (if that key is the first of the sequence it starts a new attempt, so a typo followed by a restart still works); keys that are not navigation keys
 * (volume, digits…) are ignored; no sound, no message, no return value other than "completed". The sequence is a constant, change it without touching the protocol.
 */
class SecretSequence(private val now: () -> Long, private val sequence: List<RemoteKey> = DEFAULT, private val windowMs: Long = 8_000L) {
    companion object {
        val DEFAULT = listOf(RemoteKey.UP, RemoteKey.UP, RemoteKey.DOWN, RemoteKey.DOWN, RemoteKey.LEFT, RemoteKey.RIGHT, RemoteKey.LEFT, RemoteKey.RIGHT, RemoteKey.OK)
    }
    private var index = 0
    private var startedAt = 0L

    /** True exactly when the last key completes the sequence in time. */
    fun onKey(key: RemoteKey): Boolean {
        if (!key.navigation) return false
        val t = now()
        if (index > 0 && t - startedAt >= windowMs) index = 0                // too slow: a new attempt starts with this key
        if (key == sequence[index]) {
            if (index == 0) startedAt = t
            index++
            if (index == sequence.size) { index = 0; return true }
        } else {
            index = 0
            if (key == sequence[0]) { startedAt = t; index = 1 }
        }
        return false
    }
}

/**
 * The hidden panel: opened by the sequence on the start screen (or later MENU > À propos), closed by Back or by itself after [openMs] (60 s), and rate limited
 * ([maxReveals] per rolling minute: not a toy). Shows the Bluetooth name, the address, whether the owner service is listening and a short single-use pairing code.
 */
class OwnerPanelGate(private val now: () -> Long, private val random: SecureRandom = SecureRandom(), private val sequence: SecretSequence = SecretSequence(now),
                     private val openMs: Long = 60_000L, private val maxReveals: Int = 5) {
    private val reveals = ArrayDeque<Long>()
    private var closeAt = 0L
    var pairingCode: String? = null
        private set

    val visible: Boolean get() = pairingCode != null && now() < closeAt

    /** Feed every key of the screen; returns true when the panel has just been revealed (the UI shows it; on false it shows nothing at all). */
    fun onKey(key: RemoteKey): Boolean {
        val t = now()
        if (visible && key == RemoteKey.BACK) { close(); return false }
        tick()
        if (!sequence.onKey(key)) return false
        while (reveals.isNotEmpty() && t - reveals.first() > 60_000L) reveals.removeFirst()
        if (reveals.size >= maxReveals) return false                       // silently ignored
        reveals.addLast(t)
        closeAt = t + openMs
        pairingCode = "%06d".format(random.nextInt(1_000_000))
        return true
    }

    /** Call from the UI timer: closes the panel after the delay. */
    fun tick() { if (pairingCode != null && now() >= closeAt) close() }

    fun close() { pairingCode = null; closeAt = 0L }

    /** The pairing code is single use and only valid while the panel is open. */
    fun consumePairing(code: String): Boolean {
        tick()
        val c = pairingCode ?: return false
        if (c != code) return false
        pairingCode = null
        return true
    }
}
