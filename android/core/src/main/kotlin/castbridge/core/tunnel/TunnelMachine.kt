package castbridge.core.tunnel

enum class TunnelState { IDLE, NEEDS_TERMS, ENROLLING, CONNECTING, UP, BACKOFF, REVOKED }

/** Everything the machine reads from the TV (faked in tests). */
interface TunnelEnv {
    /** Wall clock, epoch ms (the 24 h pause after a revocation and the experts schedule use it). */
    fun now(): Long
    /** The user accepted the CURRENT terms on this installation ([TermsStore.accepted]). Without it nothing starts. */
    fun termsAccepted(): Boolean
    /** The TV is locked (no counting activation): no tunnel, trial or production. */
    fun locked(): Boolean
    /** Newest counting activation (token text "cbx1...."), or null. */
    fun activation(): String?
    fun connectivity(): TunnelPath
    /** Identifier of the tunnel's own SSH key (generated at the first call, never the activation key). Only called once every gate above is open. */
    fun keyId(): String
}

interface TunnelSession {
    fun alive(): Boolean
    fun close()
}

/** A failed connection. [kind] decides what the machine forgets: AUTH / FORWARD = the enrollment (re-enroll next time), HOSTKEY = nothing (a wrong host is not cured by enrolling again). */
class TunnelException(message: String, val kind: Kind = Kind.NETWORK) : Exception(message) {
    enum class Kind { NETWORK, AUTH, HOSTKEY, FORWARD }
}

/** The TV's I/O (HTTP and SSH, over the chosen [TunnelPath]). */
interface TunnelTransport {
    /** POSTs the enrollment. Network errors are returned as [EnrollOutcome.Retry], never thrown. */
    fun enroll(activation: String, path: TunnelPath): EnrollOutcome
    /** Opens the SSH session and the remote forward `127.0.0.1:<port>`; [onClosed] is called when it ends by itself. */
    @Throws(TunnelException::class)
    fun open(e: Enrollment, path: TunnelPath, onClosed: () -> Unit): TunnelSession
    /** Fetches and applies the experts list; returns a short message for the journal only when it is a refusal (else null). */
    fun refreshExperts(path: TunnelPath): ExpertsSync.Result
}

/**
 * Remote-administration tunnel state machine (docs/REMOTE-TUNNEL-TV.md). Pure logic: no thread, no socket. The TV's single worker thread calls [step] and sleeps for the returned time
 * (or until [wake] was called); a fake clock, transport and environment drive it in tests.
 *
 *   NEEDS_TERMS  terms not accepted on this TV: nothing starts, nothing is sent
 *   IDLE         locked, no counting activation, or no Internet (offline TVs stay offline: no attempt, no queue)
 *   ENROLLING / CONNECTING / UP / BACKOFF (exponential 5 s -> 10 min with jitter) / REVOKED (403: 24 h pause)
 */
class TunnelMachine(
    private val env: TunnelEnv,
    private val transport: TunnelTransport,
    private val store: EnrollmentStore?,
    private val backoff: TunnelBackoff = TunnelBackoff(),
    private val journal: (String) -> Unit = {},
) {
    @Volatile var state = TunnelState.IDLE; private set
    @Volatile var path = TunnelPath.OFFLINE; private set
    @Volatile var lastError: String? = null; private set
    @Volatile var retryAt = 0L; private set
    @Volatile var enrollment: Enrollment? = null; private set
    @Volatile var upSince = 0L; private set
    private var session: TunnelSession? = null
    private var revokedUntil = 0L
    private var expertsDue = 0L
    @Volatile private var woken = false
    private var wakeHook: () -> Unit = {}

    init { store?.load()?.let { (e, r) -> enrollment = e; revokedUntil = r } }

    /** Called by the worker thread: something changed (network, terms, activation, session closed), run [step] again soon. */
    fun onWake(hook: () -> Unit) { wakeHook = hook }
    fun wake() { woken = true; wakeHook() }

    /** Milliseconds the worker may sleep before the next [step]. */
    fun step(): Long {
        woken = false
        val now = env.now()
        if (!env.termsAccepted()) return idle(TunnelState.NEEDS_TERMS, null, "conditions d'usage non acceptées")
        if (env.locked()) return idle(TunnelState.IDLE, "TV verrouillée (aucune clé valide)", "TV verrouillée")
        val activation = env.activation() ?: return idle(TunnelState.IDLE, "aucune clé d'activation valide", "aucune activation")
        val p = env.connectivity()
        if (p == TunnelPath.OFFLINE) return idle(TunnelState.IDLE, "hors ligne", "hors ligne")
        path = p

        session?.let { s ->
            if (!s.alive()) { lose("session fermée"); return backoffDelay(now) }
            if (now >= expertsDue) {
                val r = runCatching { transport.refreshExperts(p) }.getOrElse { ExpertsSync.Result(false, "Liste des experts : erreur ${it.javaClass.simpleName}") }
                if (!r.ok) journal(r.message)
                expertsDue = now + if (r.ok) EXPERTS_EVERY_MS else EXPERTS_RETRY_MS
            }
            return minOf(KEEPALIVE_CHECK_MS, (expertsDue - now).coerceAtLeast(1_000L))
        }

        if (revokedUntil > 0) {
            val until = minOf(revokedUntil, now + TunnelEnroll.REVOKED_MS)       // a clock wound back cannot lengthen the pause beyond 24 h
            if (now < until) { state = TunnelState.REVOKED; retryAt = until; return minOf(until - now, REVOKED_RECHECK_MS) }
            revokedUntil = 0L; save()
        }
        if (state == TunnelState.BACKOFF && now < retryAt) return retryAt - now

        val keyId = env.keyId()
        val actId = TunnelEnroll.activationId(activation)
        var e = enrollment?.takeIf { it.activationId == actId && it.keyId == keyId }
        if (e == null) {
            state = TunnelState.ENROLLING
            when (val o = transport.enroll(activation, p)) {
                is EnrollOutcome.Ok -> { e = o.enrollment.copy(activationId = actId, keyId = keyId); enrollment = e; save(); journal("enrôlée (port ${e.port})") }
                is EnrollOutcome.Revoked -> {
                    enrollment = null; revokedUntil = now + TunnelEnroll.REVOKED_MS; save()
                    state = TunnelState.REVOKED; retryAt = revokedUntil; lastError = o.message; journal("assistance désactivée pour cette TV par le serveur : pause de 24 h")
                    return REVOKED_RECHECK_MS
                }
                is EnrollOutcome.Retry -> { lastError = o.message; return backoffDelay(now, o.minDelayMs) }
            }
        }

        state = TunnelState.CONNECTING
        return try {
            session = transport.open(e!!, p) { wake() }
            state = TunnelState.UP; upSince = now; lastError = null; expertsDue = now
            journal("connectée (${if (p == TunnelPath.GATEWAY) "par le téléphone" else "réseau de la TV"}, port ${e.port})")
            0L
        } catch (x: TunnelException) {
            lastError = x.message
            if (x.kind == TunnelException.Kind.AUTH || x.kind == TunnelException.Kind.FORWARD) { enrollment = null; save() }
            journal("connexion impossible : ${x.message}")
            backoffDelay(now)
        }
    }

    /** Stops everything (service stopped): the session is closed, nothing is sent. */
    fun shutdown() { session?.let { runCatching { it.close() }; journal("déconnectée (arrêt de l'application)") }; session = null; upSince = 0L; state = TunnelState.IDLE }

    private fun idle(s: TunnelState, error: String?, journalWhy: String): Long {
        if (session != null) { drop("déconnectée ($journalWhy)") }
        state = s; lastError = error; if (error == "hors ligne") path = TunnelPath.OFFLINE
        return if (s == TunnelState.NEEDS_TERMS) NEEDS_TERMS_POLL_MS else IDLE_POLL_MS
    }

    private fun drop(why: String) { session?.let { runCatching { it.close() } }; session = null; upSince = 0L; journal(why) }

    private fun lose(why: String) {
        val stable = upSince > 0 && env.now() - upSince >= STABLE_MS
        drop("déconnectée ($why)")
        if (stable) backoff.reset()
    }

    private fun backoffDelay(now: Long, min: Long = 0L): Long {
        val d = backoff.next(min); retryAt = now + d; state = TunnelState.BACKOFF
        return d
    }

    private fun save() { runCatching { store?.save(enrollment, revokedUntil) } }

    companion object {
        const val KEEPALIVE_CHECK_MS = 30_000L
        const val EXPERTS_EVERY_MS = 15 * 60_000L
        const val EXPERTS_RETRY_MS = 5 * 60_000L
        const val IDLE_POLL_MS = 30_000L
        const val NEEDS_TERMS_POLL_MS = 60_000L
        const val REVOKED_RECHECK_MS = 60 * 60_000L
        /** A session that lasted this long was a real one: the next failure starts the backoff from 5 s again. */
        const val STABLE_MS = 60_000L
    }
}
