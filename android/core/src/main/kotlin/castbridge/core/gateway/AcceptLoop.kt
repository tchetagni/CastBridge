package castbridge.core.gateway

import java.io.IOException

/** A listening Bluetooth service: what the accept loop needs from a server socket (a scripted fake in tests). */
interface Acceptor<L : Any> {
    /** Blocks until a link arrives. [IOException] = this listener is dead: closed on purpose, Bluetooth switched off, or the stack restarted. */
    @Throws(IOException::class) fun accept(): L
    fun close()
}

/**
 * The accept loop of one Bluetooth service of the TV's Internet gateway, which SURVIVES a failed accept (R-42, audit anti-régression 2026-10-07 b, I-12).
 *
 * Before: the loop left on the first failed `accept()` (Bluetooth switched off and on, stack restarted) but the host kept `running = true` and the dead server socket in its table:
 * `start()` did nothing any more, the screen said « prête » and the phone's knocks went nowhere until the app was restarted. relay-R1 made it critical (the TV now asks a phone for a
 * pipe by itself). Now, as `BtTunnelBridge.acceptLoop` does for the SSH and API tunnels, the loop listens AGAIN, a bounded number of times ([maxFailures] consecutive failures, pauses of
 * [pauseMs] × the number of failures: 2 + 4 + 6 + 8 + 10 = 30 s), then tells the host it [gaveUp] (`running = false`: a later `start()` works, and the TV's Bluetooth `STATE_ON` hook
 * restarts it, [BtAdapterWatch]). A link accepted resets the count. A listener closed on purpose ([wanted] false: `stop()`, the SSH took the old UUID) ends the loop quietly, and so does a
 * `stop()` during a pause.
 *
 * Pure: the sockets, the pauses and the host's table are injected.
 */
class AcceptLoop<L : Any>(
    /** Is [Acceptor] still the listener the host wants open? False after a deliberate close or `stop()`. */
    private val wanted: (Acceptor<L>) -> Boolean,
    /** Opens a NEW listener on the same service; null (or an exception) when the adapter cannot now (Bluetooth off, permission): counts as a failure. */
    private val reopen: () -> Acceptor<L>?,
    /** The host swaps its reference [old] for [new]; false = the host stopped meanwhile (the new listener is closed here and the loop ends). */
    private val replace: (old: Acceptor<L>, new: Acceptor<L>) -> Boolean,
    /** Hands an accepted link over (the host serves it on its own thread). */
    private val serve: (L) -> Unit,
    /** The loop gave up: the host marks the gateway as stopped. */
    private val gaveUp: () -> Unit,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val log: (String) -> Unit = {},
    private val maxFailures: Int = MAX_FAILURES,
    private val pauseMs: Long = PAUSE_MS,
) {
    fun run(first: Acceptor<L>) {
        var cur = first
        var failures = 0
        while (wanted(cur)) {
            var failure: IOException? = null
            val link = try { cur.accept() } catch (e: IOException) { failure = e; null }
            if (link != null) {
                failures = 0                                                    // une liaison acceptée : l'écoute marche, le compte repart de zéro
                try { serve(link) } catch (e: Exception) { log("serve: ${e.javaClass.simpleName}") }
                continue
            }
            if (!wanted(cur)) return                                            // fermée exprès (stop(), l'ancien UUID rendu au SSH) : ce n'est pas une panne
            log("accept failed (${failure?.message}); listening again")
            runCatching { cur.close() }
            var reopened: Acceptor<L>? = null
            while (reopened == null) {
                if (++failures > maxFailures) { gaveUp(); return }
                sleep(pauseMs * failures)
                if (!wanted(cur)) return                                        // stop() pendant la pause : on ne rouvre rien qui reviendrait après lui
                val n = try { reopen() } catch (e: Exception) { null }          // Bluetooth encore éteint, permission retirée : un échec de plus
                if (n == null) continue
                if (!replace(cur, n)) { runCatching { n.close() }; return }
                reopened = n
            }
            cur = reopened ?: return
        }
    }

    companion object {
        /** Consecutive failed accepts (and failed re-openings) before the loop gives up. */
        const val MAX_FAILURES = 5
        const val PAUSE_MS = 2_000L
    }
}
