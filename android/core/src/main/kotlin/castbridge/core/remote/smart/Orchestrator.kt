package castbridge.core.remote.smart

import castbridge.core.remote.RemoteKey
import java.io.IOException

/** What worked for one TV, and when (epoch ms). Only strategies the user confirmed (or that the TV itself acknowledged) are kept. */
data class Remembered(val strategyId: String, val at: Long)

interface StrategyMemory {
    fun recall(tvId: String): Remembered?
    fun put(tvId: String, r: Remembered)
    fun forget(tvId: String)
}

class MemoryStrategyMemory : StrategyMemory {
    private val m = java.util.concurrent.ConcurrentHashMap<String, Remembered>()
    override fun recall(tvId: String) = m[tvId]
    override fun put(tvId: String, r: Remembered) { m[tvId] = r }
    override fun forget(tvId: String) { m.remove(tvId) }
}

/** One line of the diagnostic: when and why, never a secret ([Redact] is applied on entry). */
class DiagLog(private val capacity: Int = 200, private val clock: () -> Long = System::currentTimeMillis) {
    private val lines = ArrayDeque<String>()
    @Synchronized fun add(msg: String) { lines.addLast("${clock()} ${Redact.clean(msg)}"); while (lines.size > capacity) lines.removeFirst() }
    @Synchronized fun lines(): List<String> = lines.toList()
}

/** At most [perSecond] keys per second: a stuck finger or a bug must not flood a TV. */
class RateLimiter(private val perSecond: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private val times = ArrayDeque<Long>()
    @Synchronized fun tryAcquire(): Boolean {
        val now = clock()
        while (times.isNotEmpty() && now - times.first() >= 1000) times.removeFirst()
        if (times.size >= perSecond) return false
        times.addLast(now); return true
    }
}

enum class AttemptOutcome { OK, FAILED, NEEDS_PAIRING, TO_CONFIRM, CONFIRMED, SKIPPED }
data class Attempt(val strategyId: String, val outcome: AttemptOutcome, val reason: String?, val at: Long)

sealed class SendResult {
    data class Sent(val strategyId: String) : SendResult()
    object RateLimited : SendResult()
    data class Unavailable(val reason: String) : SendResult()
    data class NeedsPairing(val strategyId: String, val message: String) : SendResult()
}

/**
 * Tries the strategies of ONE TV (the one the user picked), in order, with short timeouts:
 * remembered-and-confirmed first, then the fingerprint's candidates, then the rest; falls over on failure or disappearance,
 * retries the best one later, and keeps a secret-free journal. CastBridge-TV (native) stays first whenever it is detected.
 */
class Orchestrator(
    val tvId: String,
    val fingerprint: TvFingerprint,
    strategies: List<RemoteStrategy>,
    private val memory: StrategyMemory,
    private val clock: () -> Long = System::currentTimeMillis,
    val diag: DiagLog = DiagLog(),
    private val limiter: RateLimiter = RateLimiter(15),
    private val retryBestAfterMs: Long = 60_000,
    private val testPauseMs: Long = 600,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val all = strategies
    @Volatile var allowExperimental = false
    /** The strategy the user picked by hand (overrides the order and the experimental switch), or null for automatic. */
    @Volatile var forced: String? = null
    @Volatile var active: RemoteStrategy? = null; private set
    private val attempts = LinkedHashMap<String, Attempt>()
    private var lastBestTry = 0L
    private val unconfirmed = HashSet<String>()

    val capabilities: Capabilities get() = active?.capabilities ?: Capabilities()
    fun attempts(): List<Attempt> = synchronized(this) { attempts.values.toList() }
    fun strategy(id: String) = all.firstOrNull { it.id == id }

    private fun note(id: String, o: AttemptOutcome, reason: String? = null) {
        synchronized(this) { attempts[id] = Attempt(id, o, reason, clock()) }
        diag.add("$id: $o${reason?.let { " — $it" } ?: ""}")
    }

    /** The order in which strategies are tried (applicable and enabled only). */
    fun plan(): List<RemoteStrategy> {
        val byId = all.associateBy { it.id }
        val ids = LinkedHashSet<String>()
        forced?.let { ids += it }
        memory.recall(tvId)?.let { ids += it.strategyId }
        if (fingerprint.vendor == Vendor.CASTBRIDGE || StrategyIds.CASTBRIDGE in fingerprint.candidates) ids += StrategyIds.CASTBRIDGE
        ids += fingerprint.candidates
        ids += StrategyIds.DEFAULT_ORDER
        ids += all.map { it.id }
        // The forced strategy rules alone when it exists: the user asked for exactly that one.
        if (forced != null && byId[forced] != null) return listOf(byId.getValue(forced!!))
        return ids.mapNotNull { byId[it] }.filter { (allowExperimental || it.status == StrategyStatus.STABLE) && it.applicable(fingerprint) }
    }

    /** Tries the plan in order until one is ready. Returns the active strategy or null (the reasons are in [attempts]). */
    @Synchronized fun connect(): RemoteStrategy? {
        val candidates = plan()
        diag.add("plan: ${candidates.joinToString(",") { it.id }}")
        active?.let { runCatching { it.close() } }; active = null
        for (s in candidates) {
            if (tryConnect(s)) { active = s; lastBestTry = clock(); return s }
        }
        return null
    }

    private fun tryConnect(s: RemoteStrategy): Boolean {
        if (!s.probe().reachable) { note(s.id, AttemptOutcome.FAILED, "injoignable (sondage)"); return false }
        return try {
            s.connect()
            val confirmed = memory.recall(tvId)?.strategyId == s.id
            note(s.id, if (confirmed) AttemptOutcome.CONFIRMED else AttemptOutcome.TO_CONFIRM)
            if (!confirmed && !s.verifiesDelivery) synchronized(unconfirmed) { unconfirmed += s.id }
            true
        } catch (e: StrategyException) {
            note(s.id, if (e.needsPairing) AttemptOutcome.NEEDS_PAIRING else AttemptOutcome.FAILED, e.message); false
        } catch (e: IOException) { note(s.id, AttemptOutcome.FAILED, e.message); runCatching { s.close() }; false }
    }

    /** Pairs [strategyId] with [code] (Android TV, Vizio, Sony PSK), then reconnects. */
    @Synchronized fun pair(strategyId: String, code: String): Boolean {
        val s = strategy(strategyId) as? Pairable ?: return false
        return try { s.pair(code); note(strategyId, AttemptOutcome.OK, "appairage terminé"); tryConnect(strategy(strategyId)!!).also { if (it) active = strategy(strategyId) } }
        catch (e: IOException) { note(strategyId, AttemptOutcome.NEEDS_PAIRING, e.message); false }
    }

    /** Sends [key]; connects on demand; falls over to the next strategy when the active one is lost. */
    fun send(key: RemoteKey): SendResult {
        if (!limiter.tryAcquire()) return SendResult.RateLimited
        return synchronized(this) {
            if (active == null) connect() ?: return@synchronized unavailable()
            maybeRetryBest()
            var s = active!!
            if (key !in s.capabilities.keys) return@synchronized SendResult.Unavailable("touche ${key.label} indisponible avec ${s.label}")
            try { deliver(s, key) } catch (e: KeyUnsupported) { SendResult.Unavailable(e.message ?: "touche indisponible") }
            catch (e: IOException) {
                note(s.id, AttemptOutcome.FAILED, "envoi : ${e.message}")
                // 1) the same strategy once (a dropped idle link), 2) the next ones.
                if (e is StrategyException && e.needsPairing) return@synchronized SendResult.NeedsPairing(s.id, e.message ?: "")
                if (tryConnect(s)) { try { return@synchronized deliver(s, key) } catch (e2: IOException) { note(s.id, AttemptOutcome.FAILED, "envoi : ${e2.message}") } }
                runCatching { s.close() }; active = null
                for (n in plan().filter { it.id != s.id && it.capabilities.has(key) && failedRecently(it).not() }) {
                    if (tryConnect(n)) { active = n; return@synchronized try { deliver(n, key) } catch (e3: IOException) { note(n.id, AttemptOutcome.FAILED, "envoi : ${e3.message}"); active = null; continue } }
                }
                unavailable()
            }
        }
    }

    private fun failedRecently(s: RemoteStrategy) = attempts[s.id]?.let { it.outcome == AttemptOutcome.FAILED && clock() - it.at < FAILOVER_COOLDOWN_MS } == true

    private fun deliver(s: RemoteStrategy, key: RemoteKey): SendResult {
        s.send(key)
        if (s.verifiesDelivery && memory.recall(tvId)?.strategyId != s.id) { memory.put(tvId, Remembered(s.id, clock())); note(s.id, AttemptOutcome.CONFIRMED, "la TV a accusé réception") }
        return SendResult.Sent(s.id)
    }

    private fun unavailable() = SendResult.Unavailable("Aucune stratégie ne répond pour cette TV (voir « Ma TV » › diagnostic)")

    /** On a fallback, tries the best strategy again from time to time (it may have come back). */
    private fun maybeRetryBest() {
        val cur = active ?: return
        if (clock() - lastBestTry < retryBestAfterMs) return
        lastBestTry = clock()
        val best = plan().firstOrNull() ?: return
        if (best.id == cur.id) return
        if (tryConnect(best)) { diag.add("retour à ${best.id}"); runCatching { cur.close() }; active = best }
    }

    fun sendText(text: String): Boolean = synchronized(this) { try { active?.sendText(text) ?: false } catch (e: IOException) { note(active!!.id, AttemptOutcome.FAILED, "texte : ${e.message}"); false } }

    /** The reversible check of « Ma TV »: volume + then volume −. The user then says whether the volume changed ([confirmTest]). */
    fun test(): Boolean {
        val s = synchronized(this) { active ?: connect() } ?: return false
        if (!s.capabilities.volume) return false
        if (send(RemoteKey.VOLUME_UP) !is SendResult.Sent) return false
        sleep(testPauseMs)
        var back = send(RemoteKey.VOLUME_DOWN)
        if (back !is SendResult.Sent) { sleep(testPauseMs); back = send(RemoteKey.VOLUME_DOWN) }   // never leave the volume one step higher
        return back is SendResult.Sent
    }

    /** The user's answer to « Avez-vous vu le volume changer ? ». Only a « oui » is remembered. */
    @Synchronized fun confirmTest(saw: Boolean) {
        val s = active ?: return
        if (saw) { memory.put(tvId, Remembered(s.id, clock())); note(s.id, AttemptOutcome.CONFIRMED, "confirmé par l'utilisateur"); synchronized(unconfirmed) { unconfirmed -= s.id } }
        else {
            note(s.id, AttemptOutcome.FAILED, "l'utilisateur n'a rien vu changer")
            if (memory.recall(tvId)?.strategyId == s.id) memory.forget(tvId)
            runCatching { s.close() }; active = null
        }
    }

    /** Selects a strategy by hand (null = automatic) and reconnects. */
    @Synchronized fun choose(strategyId: String?): RemoteStrategy? { forced = strategyId; diag.add("choix manuel : ${strategyId ?: "automatique"}"); return connect() }

    /** Risks to tell the owner about (e.g. a TV that obeys anyone on the Wi-Fi). */
    fun warnings(): List<String> = active?.warnings.orEmpty()

    fun close() { synchronized(this) { all.forEach { runCatching { it.close() } }; active = null } }

    /** Copyable diagnostic: no token, PIN, PSK, MAC or full address. */
    fun diagnostic(): String = Redact.clean(buildString {
        appendLine("CastBridge — diagnostic télécommande")
        appendLine("TV : ${fingerprint.vendor.label}${fingerprint.family?.let { " / $it" } ?: ""}${fingerprint.model?.let { " / $it" } ?: ""} (confiance ${(fingerprint.confidence * 100).toInt()} %)")
        appendLine("Indices : ${fingerprint.evidence.joinToString("; ").ifEmpty { "aucun" }}")
        appendLine("Stratégie active : ${active?.id ?: "aucune"} — essais expérimentaux ${if (allowExperimental) "activés" else "désactivés"}${forced?.let { " — choix manuel $it" } ?: ""}")
        appendLine("Ordre : ${plan().joinToString(" > ") { it.id }}")
        for (a in attempts()) appendLine("- ${a.strategyId}: ${a.outcome}${a.reason?.let { " (${it})" } ?: ""}")
        warnings().forEach { appendLine("Risque : $it") }
        appendLine("Journal :"); diag.lines().takeLast(60).forEach { appendLine("  $it") }
    })

    companion object { const val FAILOVER_COOLDOWN_MS = 30_000L }
}

/** Everything the platform provides to build the strategies of one TV. */
class StrategyEnv(
    val secrets: SecretStore,
    val log: (String) -> Unit = {},
    val native: (() -> castbridge.core.remote.RemoteTransport)? = null,
    val hid: HidPort? = null,
    val ir: IrEmitter? = null,
    val apps: AppLauncher? = null,
    val androidTv: AndroidTvChannelFactory? = null,
)

object StrategyCatalog {
    /** All strategies for [tv], in [StrategyIds.DEFAULT_ORDER]. They are only objects: nothing is sent before the orchestrator connects. */
    fun build(tv: TvTarget, fp: TvFingerprint, env: StrategyEnv): List<RemoteStrategy> {
        val l = env.log
        val descUrl = fp.upnp?.controlUrls?.values?.firstOrNull()
        return listOfNotNull(
            env.native?.let { NativeStrategy(tv, it, l) },
            CvteStrategy(tv, log = l), RokuStrategy(tv, log = l), SamsungStrategy(tv, env.secrets, log = l), LgStrategy(tv, env.secrets, log = l),
            SonyStrategy(tv, env.secrets, log = l), AndroidTvStrategy(tv, env.androidTv, l), PhilipsStrategy(tv, log = l), VizioStrategy(tv, env.secrets, log = l),
            DlnaStrategy(tv, fp.upnp, descUrl, l),
            BluetoothHidStrategy(tv, env.hid, l),
            InfraredStrategy(tv, env.ir, fp.vendor, l),
            VendorAppStrategy(tv, env.apps, fp.vendor, l),
        )
    }
}
