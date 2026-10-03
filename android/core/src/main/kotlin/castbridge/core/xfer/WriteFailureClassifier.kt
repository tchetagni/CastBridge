package castbridge.core.xfer

import java.io.IOException

/**
 * R-17. A write to the TV failed half-way (the TV answered and closed before the phone finished sending the block). Pure decisions only:
 * - [isPeerClose]: is it the TV closing on us (EPIPE, ECONNRESET, ...), where the TV's reply may still be readable?
 * - [classify]: the [Outcome] of the failed write, from the reply read afterwards (same table as [statusOutcome]) or, with no reply, a transient
 *   failure that keeps the exact exception class (a log line « Broken pipe » alone hid what really happened for 30 minutes).
 */
object WriteFailureClassifier {
    /** The salvage read of the reply is bounded: at most this long, at most this many bytes of body. */
    const val SALVAGE_MAX_MS = 1000L
    const val SALVAGE_MAX_BYTES = 4096

    private val PEER_TEXTS = listOf("broken pipe", "connection reset", "software caused connection abort", "connection abort", "reset by peer", "established connection was aborted")

    fun isPeerClose(e: Throwable): Boolean {
        if (e is java.nio.channels.ClosedChannelException) return true
        val m = e.message?.lowercase() ?: return false
        return PEER_TEXTS.any { it in m }
    }

    /** [status] = the status line read after the failure (null = nothing could be read); [body] = its (truncated) body. */
    fun classify(e: IOException, status: Int?, body: String = "", bytes: Long = 0): Outcome =
        if (status != null) statusOutcome(status, body, bytes) else Outcome.Failed(describe(e))

    /** « Broken pipe (SocketException) »: the message and the exact class, so two different failures never look alike. */
    fun describe(e: Throwable): String = "${e.message ?: "liaison coupée"} (${e.javaClass.simpleName})"
}

/**
 * Many identical transient failures in a row with no byte of progress anywhere is not a transient failure: after [maxRepeats] times the same
 * reason on a lane, over at least [minSpanMs], the transfer fails visibly (the user can retry) instead of looping forever. Any progress resets
 * every counter. Pure: the scheduler feeds it the clock.
 */
class StuckDetector(val maxRepeats: Int = DEFAULT_REPEATS, val minSpanMs: Long = DEFAULT_SPAN_MS) {
    private class Streak(val reason: String, var count: Int, val firstMs: Long)
    private val streaks = HashMap<String, Streak>()

    /** Books one failure of [lane]; true = stuck, fail the transfer. */
    @Synchronized fun onFailure(lane: String, reason: String, nowMs: Long): Boolean {
        val cur = streaks[lane]?.takeIf { it.reason == reason } ?: Streak(reason, 0, nowMs).also { streaks[lane] = it }
        cur.count++
        return cur.count >= maxRepeats && nowMs - cur.firstMs >= minSpanMs
    }

    /** A block was confirmed (on any lane). */
    @Synchronized fun onProgress() { streaks.clear() }

    companion object {
        const val DEFAULT_REPEATS = 6
        const val DEFAULT_SPAN_MS = 180_000L
        private const val SUFFIX = "vérifiez la TV puis relancez"

        fun message(reason: String): String =
            if (reason.contains("Broken pipe", true) || reason.contains("reset", true) || reason.contains("abort", true) || reason.contains("connection closed", true))
                "La TV ferme la connexion pendant l'envoi (cause inconnue) : $SUFFIX"
            else "La TV n'accepte pas l'envoi ($reason) : $SUFFIX"
    }
}
