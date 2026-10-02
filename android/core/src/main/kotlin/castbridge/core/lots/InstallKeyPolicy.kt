package castbridge.core.lots

import java.io.File

/**
 * Pure decisions about the installation key's envelope (the Android code only maps its exceptions onto these inputs), so that a TRANSIENT Keystore failure can never destroy the alias
 * or the key that protects `install.key` (audits w4-03: every v2 rental would be lost for good), and so that a Keystore that stays broken never freezes the TV nor floods it.
 */
object InstallKeyPolicy {
    enum class Step { USE, GENERATE, RECREATE, FAIL }

    /** What to do with the alias: [present] = `containsAlias` answer, null if the lookup itself failed (never generate then: the alias may well exist). */
    fun forLookup(present: Boolean?): Step = when (present) { true -> Step.USE; false -> Step.GENERATE; null -> Step.FAIL }

    /** What to do when encrypting with the existing key failed: only a permanently invalidated key is replaced; anything else is rethrown and retried later. */
    fun forEncryptFailure(permanentlyInvalidated: Boolean): Step = if (permanentlyInvalidated) Step.RECREATE else Step.FAIL

    /** Why an unwrap failed, as the Android code sees it. */
    enum class UnwrapFailure { BAD_TAG, MISSING_ALIAS, INVALIDATED, OTHER }

    /**
     * Is this unwrap failure a LOST key (unwrap answers null, the store may regenerate) rather than a transient one (unwrap rethrows, the store keeps the file and the caller retries)?
     * Only a wrong tag (another or a recreated key, an altered blob), an alias that the Keystore says is absent, or a permanently invalidated key are losses.
     */
    fun unwrapIsLoss(f: UnwrapFailure): Boolean = f != UnwrapFailure.OTHER

    /**
     * The wrapper that must READ a stored key: always the one named by its `wrap=` label (a plain blob is read by the plain wrapper even when the Keystore works now: reading it through the
     * Keystore would fail and the key would be declared lost), never the one the process would choose for a new key.
     */
    fun readWith(stored: String): String = stored

    /** Must a key read under [stored] be rewritten (same seed) under [chosen]? Only to go from no envelope to an envelope: a plain key moves into the Keystore as soon as it works. */
    fun migrateTo(stored: String, chosen: String): Boolean = stored == PLAIN && chosen != PLAIN

    // ---- the retry schedule when the key cannot be read for now (Keystore unavailable) ----

    const val SHORT_DELAY_MS = 60_000L                  // 1 min between the first attempts …
    const val LONG_DELAY_MS = 3_600_000L                // … then 1 h
    const val SHORT_TRIES = 3
    /** An assumed regeneration needs this many failures (all process starts together) … */
    const val GIVE_UP_FAILURES = 8
    /** … seen in at least this many process starts (a single bad boot never costs the key). */
    const val GIVE_UP_STARTS = 3

    /** The wait before the next attempt after [failuresThisProcess] failures in this process (0 = none yet: try now). */
    fun delayAfter(failuresThisProcess: Int): Long = when {
        failuresThisProcess <= 0 -> 0L
        failuresThisProcess <= SHORT_TRIES -> SHORT_DELAY_MS
        else -> LONG_DELAY_MS
    }

    /** May a new attempt start at [nowMs] (monotonic clock)? [lastFailureAtMs] = monotonic time of the last failure in this process, null if none. */
    fun mayAttempt(nowMs: Long, lastFailureAtMs: Long?, failuresThisProcess: Int): Boolean =
        lastFailureAtMs == null || nowMs - lastFailureAtMs >= delayAfter(failuresThisProcess) || nowMs < lastFailureAtMs   // a clock that went back never blocks forever

    /** Failures of the install key, persisted across process starts (`install.key.retry`). */
    data class RetryState(val failures: Int = 0, val starts: Int = 0)

    /** The state after one more failure; [firstInThisProcess] counts one more process start. */
    fun afterFailure(s: RetryState, firstInThisProcess: Boolean) = RetryState(s.failures + 1, s.starts + if (firstInThisProcess) 1 else 0)

    /** Give up waiting for the Keystore and regenerate the key (stated: « illisible : demandez la réémission »)? */
    fun mustRegenerate(s: RetryState): Boolean = s.failures >= GIVE_UP_FAILURES && s.starts >= GIVE_UP_STARTS

    /** `installKeyProtection` of `GET /api/activation`: the label of the key in use, else `unavailable` once an attempt failed, else `pending` (being prepared). */
    fun protection(ready: String?, failuresThisProcess: Int): String = ready ?: if (failuresThisProcess > 0) UNAVAILABLE else PENDING

    const val PLAIN = "plain"; const val UNAVAILABLE = "unavailable"; const val PENDING = "pending"

    /** The French answer of a route that needs the installation key while it cannot be read (HTTP 503). */
    const val UNAVAILABLE_MESSAGE = "coffre de clés indisponible : nouvel essai automatique, réessayez plus tard"
}

/**
 * Routes built LAZILY: [build] (which may need the installation key, hence the Keystore) runs at the first request of one of [paths], never when the server is set up, and its failure
 * answers 503 « coffre de clés indisponible » instead of reaching the caller (audit w4-03: a Keystore failure in `TvService.onCreate` brought the whole TV app down). Other paths are
 * left to the next extension, [bodyPaths] are announced without building anything.
 */
class LazyKeyedApi(private val paths: Set<String>, private val bodyPaths: Set<String>, private val build: () -> castbridge.core.tv.ApiExtension) : castbridge.core.tv.ApiExtension {
    private fun unavailable() = castbridge.core.tv.ApiReply(503, """{"error":${castbridge.core.tv.ReceiverServer.q(InstallKeyPolicy.UNAVAILABLE_MESSAGE)}}""")
    private inline fun guarded(path: String, call: (castbridge.core.tv.ApiExtension) -> castbridge.core.tv.ApiReply?): castbridge.core.tv.ApiReply? {
        if (path !in paths) return null
        val api = runCatching(build).getOrElse { return unavailable() }
        return call(api)
    }
    override fun wantsBody(path: String) = path in bodyPaths
    override fun handle(path: String, method: String, params: Map<String, String>) = guarded(path) { it.handle(path, method, params) }
    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray) = guarded(path) { it.handleBody(path, method, params, body) }
}

/**
 * The retry gate of the installation key: when may the next attempt start (backoff on a monotonic clock), and when to give up and regenerate (counters in `<dir>/install.key.retry`,
 * so that several process starts are counted). Decisions come from [InstallKeyPolicy]; this class only keeps the state. Thread-safe.
 */
class InstallKeyGate(dir: File, private val mono: () -> Long) {
    private val file = File(dir, InstallKeyStore.FILE + ".retry")
    private var lastFailureAt: Long? = null
    @Volatile var failuresThisProcess = 0; private set

    @Synchronized fun mayAttempt(): Boolean = InstallKeyPolicy.mayAttempt(mono(), lastFailureAt, failuresThisProcess)

    /** Records a failure; [countsTowardRegeneration] = the key could not be READ (not a disk or other error). Returns true for the first failure of this process (log it, once). */
    @Synchronized fun failed(countsTowardRegeneration: Boolean): Boolean {
        val first = failuresThisProcess == 0
        failuresThisProcess++; lastFailureAt = mono()
        if (countsTowardRegeneration) runCatching { save(InstallKeyPolicy.afterFailure(load(), !countedThisProcess)); countedThisProcess = true }
        return first
    }
    private var countedThisProcess = false      // this process start was already counted in `starts`

    @Synchronized fun mustRegenerate(): Boolean = InstallKeyPolicy.mustRegenerate(load())

    /** The key is ready: the counters start again from zero. */
    @Synchronized fun succeeded() { failuresThisProcess = 0; lastFailureAt = null; runCatching { file.delete() } }

    fun load(): InstallKeyPolicy.RetryState = runCatching {
        val kv = file.readLines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() } }.toMap()
        InstallKeyPolicy.RetryState(kv["failures"]?.toIntOrNull() ?: 0, kv["starts"]?.toIntOrNull() ?: 0)
    }.getOrDefault(InstallKeyPolicy.RetryState())

    private fun save(s: InstallKeyPolicy.RetryState) { file.parentFile?.mkdirs(); file.writeText("failures=${s.failures}\nstarts=${s.starts}\n") }
}
