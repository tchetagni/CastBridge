package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.owner.SafeFile
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import java.io.File
import java.io.IOException

enum class DeliveryState {
    /** Waiting for the TV to be reachable (or for its retry delay). */
    PENDING,
    /** Bytes are going to the TV; [Delivery.offset] = what the TV confirmed. */
    SENDING,
    /** All bytes reached the TV, its install is not confirmed yet (Bluetooth: the TV answers at the next manifest). */
    SENT,
    /** The TV's own manifest shows the lot installed. */
    CONFIRMED,
    /** The TV refused it ([Delivery.lastError]): no retry until a newer version or an explicit [DeliveryQueue.retry]. */
    REFUSED,
    CANCELLED,
}

data class Delivery(
    val tvId: String,
    val lot: LotMeta,
    val state: DeliveryState,
    val offset: Long = 0,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val priority: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val active get() = state == DeliveryState.PENDING || state == DeliveryState.SENDING
}

/** Persistence of the queue (a file on the phone, memory in tests). */
interface QueueStore {
    fun load(): String?
    @Throws(IOException::class) fun save(json: String)
}

class FileQueueStore(private val file: File) : QueueStore {
    private fun valid(text: String) = runCatching { JsonLite.obj(text) }.isSuccess
    override fun load() = SafeFile.read(file, ::valid)?.text
    override fun save(json: String) {
        try { SafeFile.write(file, json, ::valid) } catch (e: IOException) { throw IOException("file d'attente non écrite", e) }
    }
}

class MemoryQueueStore(var text: String? = null) : QueueStore {
    override fun load() = text
    override fun save(json: String) { text = json }
}

/** What the phone last learnt about a TV (shown when the TV is away: "dernier contact il y a 3 jours, 2,1 Mo libres"). */
data class TvSeen(val at: Long, val manifest: TvManifest)

/**
 * The persistent, DEFERRED, per-TV delivery queue of the phone: what each TV should hold (the [LotPlanner] plan) against what
 * it reported holding (its [TvManifest]). Nothing here needs the TV to be present: the queue is filled when the phone has
 * downloaded a lot or the plan changes, and drained whenever a link to that TV exists, at any later time, resuming from the
 * last offset the TV confirmed. It is a pure state machine ([now] and [store] injected), saved after every change so it survives
 * an app kill or a phone reboot (a SENDING entry found at load goes back to PENDING, keeping its offset as a hint: the TV's own
 * ".part" size is what really decides where to resume).
 *
 *   PENDING -> SENDING(offset) -> SENT -> CONFIRMED
 *        ^----- link drop / retry later (backoff) -----'
 *   REFUSED (the TV said no, reason kept) and CANCELLED (no longer wanted / by the user) end it.
 *
 * One entry per (TV, lot): a newer version SUPERSEDES the older pending one (a TV away for weeks catches up straight to the
 * latest version of each lot, nothing intermediate is ever sent). The TV manifest is the truth: a lot CONFIRMED/SENT that the
 * TV no longer shows (reset, reinstall, eviction) is queued again.
 */
class DeliveryQueue(private val store: QueueStore, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val items = LinkedHashMap<String, Delivery>()          // key = tvId|feature:scope
    private val seen = LinkedHashMap<String, TvSeen>()

    init { synchronized(lock) { load() } }

    private fun key(tv: String, id: LotId) = "$tv|${LotNames.key(id)}"

    fun all(): List<Delivery> = synchronized(lock) { items.values.toList() }
    fun of(tv: String): List<Delivery> = all().filter { it.tvId == tv }
    fun get(tv: String, id: LotId): Delivery? = synchronized(lock) { items[key(tv, id)] }
    fun seen(tv: String): TvSeen? = synchronized(lock) { seen[tv] }
    fun pendingCount(tv: String) = of(tv).count { it.state == DeliveryState.PENDING || it.state == DeliveryState.SENDING || it.state == DeliveryState.SENT }
    fun hasWork(tv: String): Boolean = of(tv).any { it.active }

    /** Records a fresh TV manifest (the phone's knowledge of the TV, kept for when it is away). */
    fun recordSeen(tv: String, m: TvManifest) = synchronized(lock) { seen[tv] = TvSeen(now(), m); persist() }

    /**
     * Aligns the queue of [tv] with [wanted] (lots to deliver, with their priority) and, when known, the TV's own [tvManifest].
     * Never loses a delivery that is still wanted; cancels what no longer is.
     */
    fun reconcile(tv: String, wanted: List<Pair<LotMeta, Int>>, tvManifest: TvManifest?) = synchronized(lock) {
        val t = now()
        tvManifest?.let { seen[tv] = TvSeen(t, it) }
        val wantedIds = wanted.map { it.first.id }.toSet()
        for ((lot, prio) in wanted) {
            val k = key(tv, lot.id)
            val cur = items[k]
            val onTv = tvManifest?.lots?.firstOrNull { it.meta.id == lot.id }?.meta
            val rejection = tvManifest?.rejected?.firstOrNull { it.id == lot.id && it.version == lot.version }
            items[k] = when {
                // the TV holds this version (or a newer one): done
                onTv != null && (onTv.version > lot.version || onTv.version == lot.version && onTv.sha256 == lot.sha256) ->
                    (if (cur != null && cur.lot.version == lot.version) cur else Delivery(tv, lot, DeliveryState.CONFIRMED, createdAt = t)).copy(state = DeliveryState.CONFIRMED, offset = lot.bytes, lastError = null, priority = prio, updatedAt = t)
                cur == null -> Delivery(tv, lot, DeliveryState.PENDING, priority = prio, createdAt = t, updatedAt = t)
                // a newer version supersedes whatever was queued or done for an older one (and a changed hash of the same version)
                cur.lot.version != lot.version || cur.lot.sha256 != lot.sha256 -> Delivery(tv, lot, DeliveryState.PENDING, priority = prio, createdAt = t, updatedAt = t)
                cur.state == DeliveryState.REFUSED -> cur.copy(priority = prio)
                // we believed it was installed/sent but the (fresh) TV manifest does not show it: the TV was reset or refused it
                tvManifest != null && (cur.state == DeliveryState.CONFIRMED || cur.state == DeliveryState.SENT) ->
                    if (rejection != null) cur.copy(state = DeliveryState.REFUSED, lastError = rejection.reason, priority = prio, updatedAt = t)
                    else Delivery(tv, lot, DeliveryState.PENDING, priority = prio, createdAt = t, updatedAt = t)
                cur.state == DeliveryState.CANCELLED -> Delivery(tv, lot, DeliveryState.PENDING, priority = prio, createdAt = t, updatedAt = t)
                else -> cur.copy(priority = prio)
            }
        }
        for ((k, d) in items.entries.toList()) if (d.tvId == tv && d.lot.id !in wantedIds && (d.active || d.state == DeliveryState.SENT)) items[k] = d.copy(state = DeliveryState.CANCELLED, updatedAt = t)
        persist()
    }

    /** The next delivery to attempt for [tv]: ready ones, most important first, then smallest, then by name. */
    fun next(tv: String): Delivery? = synchronized(lock) {
        items.values.filter { it.tvId == tv && it.active && it.nextAttemptAt <= now() }
            .minWithOrNull(compareBy({ it.priority }, { it.lot.bytes }, { it.lot.id.feature }, { it.lot.id.scope }))
    }

    /** When will the earliest waiting delivery of [tv] be ready (null = none waiting)? For the next background attempt. */
    fun nextReadyAt(tv: String): Long? = synchronized(lock) { items.values.filter { it.tvId == tv && it.active }.minOfOrNull { it.nextAttemptAt } }

    fun begin(tv: String, id: LotId) = update(tv, id) { if (it.active) it.copy(state = DeliveryState.SENDING, updatedAt = now()) else it }
    fun progress(tv: String, id: LotId, offset: Long) = update(tv, id) { if (it.state == DeliveryState.SENDING) it.copy(offset = offset, updatedAt = now()) else it }
    fun sent(tv: String, id: LotId) = update(tv, id) { if (it.state == DeliveryState.SENDING || it.active) it.copy(state = DeliveryState.SENT, offset = it.lot.bytes, lastError = null, updatedAt = now()) else it }
    fun confirmed(tv: String, id: LotId) = update(tv, id) { it.copy(state = DeliveryState.CONFIRMED, offset = it.lot.bytes, lastError = null, updatedAt = now()) }

    /** The link dropped / the TV left: back to PENDING with a growing delay (5 s … 15 min), the offset kept as a hint. */
    fun failedTransient(tv: String, id: LotId, error: String, offset: Long? = null) = update(tv, id) {
        if (!it.active) it else {
            val attempts = it.attempts + 1
            it.copy(state = DeliveryState.PENDING, attempts = attempts, offset = offset ?: it.offset, lastError = error,
                nextAttemptAt = now() + backoffMs(attempts), updatedAt = now())
        }
    }

    /** The TV refused it for good. */
    fun refused(tv: String, id: LotId, reason: String) = update(tv, id) { it.copy(state = DeliveryState.REFUSED, lastError = reason, updatedAt = now()) }
    fun cancel(tv: String, id: LotId) = update(tv, id) { if (it.state == DeliveryState.CONFIRMED) it else it.copy(state = DeliveryState.CANCELLED, updatedAt = now()) }
    /** The user asks to try again (or to send first): PENDING now, from the TV's own offset. */
    fun retry(tv: String, id: LotId) = update(tv, id) { it.copy(state = DeliveryState.PENDING, attempts = 0, nextAttemptAt = 0, lastError = null, updatedAt = now()) }
    fun setPriority(tv: String, id: LotId, priority: Int) = update(tv, id) { it.copy(priority = priority) }
    fun forget(tv: String) = synchronized(lock) { items.keys.removeAll { it.startsWith("$tv|") }; seen.remove(tv); persist() }

    private fun update(tv: String, id: LotId, f: (Delivery) -> Delivery) = synchronized(lock) {
        val k = key(tv, id); items[k]?.let { items[k] = f(it); persist() }
        Unit
    }

    private fun persist() { try { store.save(toJson()) } catch (_: IOException) { /* kept in memory; the next change retries */ } }

    private fun toJson(): String = JsonLite.write(linkedMapOf(
        "deliveries" to items.values.map { d -> d.lot.toMap() + mapOf("tv" to d.tvId, "state" to d.state.name, "offset" to d.offset, "attempts" to d.attempts,
            "nextAttemptAt" to d.nextAttemptAt, "priority" to d.priority.toLong(), "lastError" to d.lastError, "createdAt" to d.createdAt, "updatedAt" to d.updatedAt) },
        "seen" to seen.map { (tv, s) -> linkedMapOf("tv" to tv, "at" to s.at, "manifest" to s.manifest.toJson()) }))

    private fun load() {
        val m = runCatching { JsonLite.obj(store.load() ?: return) }.getOrNull() ?: return
        (m["deliveries"] as? List<*>)?.forEach { e ->
            @Suppress("UNCHECKED_CAST") val d = e as? Map<String, Any?> ?: return@forEach
            val lot = parseLotMeta(d) ?: return@forEach
            val tv = d.str("tv") ?: return@forEach
            val st = runCatching { DeliveryState.valueOf(d.str("state") ?: "") }.getOrNull() ?: return@forEach
            // after a restart nothing is in flight: SENDING goes back to PENDING (offset kept as a hint, the TV's .part decides)
            val state = if (st == DeliveryState.SENDING) DeliveryState.PENDING else st
            items[key(tv, lot.id)] = Delivery(tv, lot, state, d.long("offset") ?: 0, d.long("attempts")?.toInt() ?: 0, d.long("nextAttemptAt") ?: 0,
                d.long("priority")?.toInt() ?: 0, d.str("lastError"), d.long("createdAt") ?: 0, d.long("updatedAt") ?: 0)
        }
        (m["seen"] as? List<*>)?.forEach { e ->
            @Suppress("UNCHECKED_CAST") val s = e as? Map<String, Any?> ?: return@forEach
            val tv = s.str("tv") ?: return@forEach
            val man = s.str("manifest")?.let { TvManifest.parse(it) } ?: return@forEach
            seen[tv] = TvSeen(s.long("at") ?: 0, man)
        }
    }

    companion object {
        fun backoffMs(attempts: Int): Long = minOf(15L * 60_000, 5_000L shl minOf(attempts - 1, 10))
    }
}

/**
 * Drains the queue of one TV over one [LotTransport], whenever the caller found a link (the app calls it when Bluetooth connects,
 * the TV shows up on the LAN, or the user presses « Envoyer »). It reads what the phone holds ([LotStore]) and never needs
 * the Internet, so it works while the server is unreachable. Never blocks the user: it is meant for a background worker.
 */
class LotDelivery(
    private val queue: DeliveryQueue,
    private val store: LotStore,
    /** Which lots matter to this TV (its profiles' classes), computed by the app from the profiles. */
    private val needs: (tv: String) -> List<Need>,
) {
    data class Report(
        val reachable: Boolean,
        val sent: List<LotId>,
        val confirmed: List<LotId>,
        val refused: List<Pair<LotId, String>>,
        val skipped: List<LotPlanner.Skipped>,
        /** Waiting for the next contact. */
        val pending: Int,
        val plan: LotPlanner.Plan?,
    )

    /**
     * Brings the queue of [tv] up to date with the phone's store, WITHOUT any contact: the plan uses the last manifest the TV
     * reported (or the full default budget for a TV never seen). Call it after every download so that "En attente d'envoi à la TV"
     * is shown at once, whether or not the TV is in range.
     */
    fun enqueue(tv: String): LotPlanner.Plan {
        val known = queue.seen(tv)?.manifest
        val plan = LotPlanner.plan(needs(tv), store.catalog(), known?.lots?.map { it.meta } ?: emptyList(), known?.starterBytes ?: 0L, known?.maxBytes ?: LotBudget.TV_MAX_BYTES)
        queue.reconcile(tv, plan.wanted.map { it to (plan.priority[it.id] ?: 0) }, null)
        return plan
    }

    fun deliver(tv: String, transport: LotTransport, cancelled: () -> Boolean = { false }, only: LotId? = null): Report {
        val live = transport.manifest()
        if (live == null && transport.canReadManifest) {
            val plan = enqueue(tv)                                       // not in range: the delivery waits, nothing is lost
            return Report(false, emptyList(), emptyList(), emptyList(), plan.skipped, queue.pendingCount(tv), plan)
        }
        val fresh = live != null
        val m = live ?: queue.seen(tv)?.manifest
        val plan = LotPlanner.plan(needs(tv), store.catalog(), m?.lots?.map { it.meta } ?: emptyList(), m?.starterBytes ?: 0L, m?.maxBytes ?: LotBudget.TV_MAX_BYTES)
        queue.reconcile(tv, plan.wanted.map { it to (plan.priority[it.id] ?: 0) }, live)
        if (fresh) transport.setPriority(plan.wanted.map { it.id })
        val sent = ArrayList<LotId>(); val refused = ArrayList<Pair<LotId, String>>(); val confirmed = ArrayList<LotId>()
        while (!cancelled()) {
            val d = (if (only == null) queue.next(tv) else queue.get(tv, only)?.takeIf { it.active }) ?: break   // only = « Envoyer » on one lot: the others stay queued
            val id = d.lot.id
            val file = store.file(id, d.lot.version)
            val proof = store.proof(id)
            if (file == null || proof == null) { queue.cancel(tv, id); continue }      // superseded or evicted on the phone meanwhile
            queue.begin(tv, id)
            when (val r = transport.send(d.lot, file, proof, { queue.progress(tv, id, it) }, cancelled)) {
                SendResult.Installed -> { queue.sent(tv, id); queue.confirmed(tv, id); sent += id; confirmed += id }
                SendResult.Delivered -> { queue.sent(tv, id); sent += id }
                is SendResult.Refused -> { queue.refused(tv, id, r.reason); refused += id to r.reason }
                is SendResult.LinkDown -> { queue.failedTransient(tv, id, r.reason, r.confirmed); break }   // the TV went away: stop, try at the next contact
            }
        }
        // a fresh manifest settles what is SENT (Bluetooth) and tells the TV's rejections
        if (fresh) transport.manifest()?.let { m2 -> queue.reconcile(tv, plan.wanted.map { it to (plan.priority[it.id] ?: 0) }, m2) }
        val after = queue.of(tv)
        refused += after.filter { it.state == DeliveryState.REFUSED && refused.none { r -> r.first == it.lot.id } }.map { it.lot.id to (it.lastError ?: "refusé par la TV") }
        return Report(true, sent, confirmed, refused, plan.skipped, queue.pendingCount(tv), plan)
    }
}
