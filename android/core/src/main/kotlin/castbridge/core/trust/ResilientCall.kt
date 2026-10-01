package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.TvClient
import java.io.IOException
import java.io.InterruptedIOException

/** What a call needs to know about the link, one place for every screen and service (the real one asks [LinkDriver]; tests use a fake). */
interface LinkGate {
    /** The credential to present now (token or PIN) or null. Asked again before every attempt, so a renewed token is picked up. */
    fun credential(): String?
    /** The call failed because the link looks dead: check it now. [rejectedToken]: the TV refused this token (expired or revoked). */
    fun suspect(rejectedToken: String? = null)
    /** Waits until the link is (re)established or [timeoutMs] passes; true if it is up. May return early on a link event. */
    fun awaitLink(timeoutMs: Long): Boolean
}

/** How long and how often [ResilientCall] insists. */
data class CallPolicy(
    /** Longest total wait for the TV before giving up with an actionable message. */
    val maxWaitMs: Long = 5 * 60_000,
    val steps: LongArray = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000),
    val spread: Double = 0.25,
)

/** What the screen shows while a call waits: a calm "En attente de la TV", never an error. */
data class CallWaiting(val attempt: Int, val waitedMs: Long, val message: String = "En attente de la TV…")

sealed class CallResult<out T> {
    data class Ok<T>(val value: T) : CallResult<T>()
    /** The TV refused the credential (wrong PIN, locked, no credential, token still refused after a new one): never retried, nothing was sent again. */
    data class CredentialRefused(val message: String) : CallResult<Nothing>()
    /** A permanent answer (not found, no room, needs the PIN...) or the time ran out: [message] says what to do. */
    data class GaveUp(val message: String, val waitedMs: Long, val maybeApplied: Boolean = false) : CallResult<Nothing>()
    object Cancelled : CallResult<Nothing>()
}

/**
 * The one reconnect-aware way to call a TV (HTTP route, library listing, parental, quiz relay, remote key...), instead of ad-hoc retries per screen:
 *
 * - transient failures (IOException, timeouts, 5xx) are retried with a jittered backoff while telling [onWaiting] ("En attente de la TV"),
 *   and the link layer is asked to check itself ([LinkGate.suspect]); a long outage ends with an actionable message after [CallPolicy.maxWaitMs];
 * - a token the TV refused (HTTP 401 "bad token") is reported, then the call waits for a DIFFERENT credential; the same one is never sent twice;
 * - a wrong PIN, a locked TV, a missing credential: no retry, ever (a loop here would lock the TV for a minute);
 * - a call that is not [idempotent] is not repeated once it may have been applied: it gives up with `maybeApplied` so the screen can check.
 */
class ResilientCall(
    private val gate: LinkGate,
    private val policy: CallPolicy = CallPolicy(),
    private val now: () -> Long = System::currentTimeMillis,
    private val random: () -> Double = Math::random,
    private val cancelled: () -> Boolean = { false },
) {
    fun <T> run(idempotent: Boolean = true, onWaiting: (CallWaiting) -> Unit = {}, op: (credential: String?) -> T): CallResult<T> {
        val start = now()
        var attempt = 0
        var refusedToken: String? = null
        var hasRefused = false
        while (true) {
            if (cancelled()) return CallResult.Cancelled
            val cred = gate.credential()
            if (hasRefused && (cred == null || cred == refusedToken)) {
                // wait for the link layer to produce a new token, without bothering the TV
                if (now() - start >= policy.maxWaitMs) return CallResult.CredentialRefused(LinkText.http(401, "bad token"))
                onWaiting(CallWaiting(attempt, now() - start, "Autorisation de la TV à renouveler…"))
                gate.awaitLink(2_000)
                continue
            }
            try {
                return CallResult.Ok(op(cred))
            } catch (e: Throwable) {
                if (e is InterruptedException || (e is InterruptedIOException && cancelled())) return CallResult.Cancelled
                when (val kind = classify(e)) {
                    Kind.CREDENTIAL -> return CallResult.CredentialRefused(LinkText.failure(e))
                    Kind.PERMANENT -> return CallResult.GaveUp(LinkText.failure(e), now() - start)
                    Kind.TOKEN -> { refusedToken = cred; hasRefused = true; gate.suspect(cred); attempt++; continue }
                    Kind.TRANSIENT -> {
                        if (!idempotent) return CallResult.GaveUp("La liaison avec la TV s'est interrompue pendant l'opération : vérifiez son résultat avant de la refaire.", now() - start, maybeApplied = true)
                        attempt++
                        val waited = now() - start
                        val pause = Jitter.around(policy.steps[(attempt - 1).coerceIn(0, policy.steps.size - 1)], policy.spread, random)
                        if (waited + pause > policy.maxWaitMs)
                            return CallResult.GaveUp("La TV ne répond plus depuis ${(waited / 60_000).coerceAtLeast(1)} min. Vérifiez qu'elle est allumée et que CastBridge-TV est ouvert, puis réessayez.", waited)
                        onWaiting(CallWaiting(attempt, waited))
                        gate.suspect()
                        gate.awaitLink(pause)
                    }
                }
            }
        }
    }

    enum class Kind { TRANSIENT, PERMANENT, CREDENTIAL, TOKEN }

    companion object {
        /** Which family a failure belongs to: the whole retry policy hangs on this table (and it is tested). */
        fun classify(e: Throwable): Kind = when (e) {
            is TvCredential.Missing -> Kind.CREDENTIAL
            is BtProtocol.Refused -> when (e.code) {
                BtProtocol.ERR_PIN, BtProtocol.ERR_LOCKED, BtProtocol.ERR_UNTRUSTED, BtProtocol.ERR_DENIED -> Kind.CREDENTIAL
                BtProtocol.ERR_BUSY, BtProtocol.ERR_TIMEOUT, BtProtocol.ERR_IO -> Kind.TRANSIENT
                else -> Kind.PERMANENT
            }
            is TvClient.HttpError -> when {
                e.code == 401 && TvClient.isBadToken(e) -> Kind.TOKEN
                e.code == 401 -> Kind.CREDENTIAL
                e.code == 507 || e.code == 501 -> Kind.PERMANENT
                e.code == 502 || e.code == 503 || e.code == 504 || e.code == 429 -> Kind.TRANSIENT
                e.code >= 500 -> Kind.TRANSIENT
                else -> Kind.PERMANENT
            }
            is BtUnavailable -> Kind.TRANSIENT
            is TvClient.Conflict -> Kind.TRANSIENT
            is IOException -> Kind.TRANSIENT
            else -> Kind.PERMANENT
        }
    }
}
