package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.Activation
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.TvClock
import java.io.File

/** Where a rental contract stands in its life. Each step is idempotent and persisted, so a power cut resumes where it stopped. */
enum class RentalPhase { LIVE, EXPIRING, KEY_GONE, DONE }

/** One line of the local journal: which lot, when, why, which step. NOTHING else: no device, licence, seat, factor or person. */
data class RentalLogEntry(val at: Long, val lot: String, val reason: String, val step: String)

/**
 * The TV's rental state, persisted in `<dir>/rentals.json` (atomic write): the clock high-water mark ([clock]), the minutes of use counted per contract, the phase and the
 * rented lots of each contract, and the journal. It also holds the TOMBSTONE of every ended contract: once a contract has left [RentalPhase.LIVE], its key is never opened again, even if the
 * activation file is read again from a USB stick or a backup (that is what makes a restored copy useless). Learner progress is NOT here and is never touched.
 */
class RentalLedger(private val dir: File, val clock: TvClock = TvClock(), val config: RentalConfig = RentalConfig(), private val wall: () -> Long = System::currentTimeMillis) {
    class Rec(var used: Long = 0, var phase: RentalPhase = RentalPhase.LIVE, var reason: ExpiryReason? = null, var expiredAt: Long = 0,
              val lots: LinkedHashSet<String> = LinkedHashSet(), val removed: LinkedHashSet<String> = LinkedHashSet())

    private val lock = Any()
    private val recs = LinkedHashMap<String, Rec>()
    private val log = ArrayList<RentalLogEntry>()
    private val file get() = File(dir, "rentals.json")

    init { synchronized(lock) { load() } }

    /** The time the rental rules use now (never earlier than the highest time seen). */
    fun nowMs(): Long = RentalEngine.judge(clock, wall(), config).now

    fun rec(key: String): Rec? = synchronized(lock) { recs[key] }
    fun phase(key: String) = rec(key)?.phase ?: RentalPhase.LIVE
    fun journal(): List<RentalLogEntry> = synchronized(lock) { log.toList() }
    fun rentedLots(key: String): Set<String> = synchronized(lock) { recs[key]?.lots?.toSet() ?: emptySet() }
    fun usedMinutes(key: String): Long = synchronized(lock) { recs[key]?.used ?: 0L }

    /** Records what the wall clock says (call at start and every hour while running) and what a signed message proves; never moves back. */
    fun observe(signedIssuedAt: Long = 0L) = synchronized(lock) {
        val doubt = RentalEngine.judge(clock, wall(), config).doubt
        // a clock in doubt is NOT recorded as time seen: a wrong far-ahead reading must not become the new high-water mark
        if (doubt == null) clock.observe(wall(), signedIssuedAt) else clock.observe(0L, signedIssuedAt)
        save()
    }

    /** The user says « l'heure est juste » after a suspension for a far-ahead clock: accepted up to [TvClock.MAX_JUMP_MS]; a clock BEHIND is never accepted (it would extend). */
    fun confirmClockAhead(): Boolean = synchronized(lock) {
        val w = wall(); val base = maxOf(clock.lastSeen, clock.floor)
        if (w < base || w > base + TvClock.MAX_JUMP_MS) return false
        clock.lastSeen = w; save(); true
    }

    private fun inputs(superUnlimited: Boolean = false): RentalInputs = synchronized(lock) {
        RentalInputs(RentalEngine.judge(clock, wall(), config), recs.mapValues { it.value.used }, recs.filterValues { it.phase != RentalPhase.LIVE }.mapValues { it.value.reason ?: ExpiryReason.DATE }, superUnlimited)
    }

    /** The state of every rental in [activations] right now. Tombstoned contracts stay EXPIRED. */
    fun status(activations: List<Activation>): List<RentalStatus> = RentalEngine.evaluate(RentalEngine.contracts(activations), inputs(RentalEngine.superUnlimited(activations)), config)

    /**
     * Installs the keys of the rentals of [activation] that are usable now: opens the box with the device's factors and puts the key in [vault]. A tombstoned contract is never reopened.
     * Returns, per contract, what happened (for the screen and the tests).
     */
    fun install(activation: Activation, all: List<Activation>, device: Fingerprints, vault: RentalVault): Map<String, String> = synchronized(lock) {
        observe(activation.issuedAt)
        val out = LinkedHashMap<String, String>()
        val statuses = status(all).associateBy { it.key }
        for (c in RentalEngine.contracts(all)) {
            if (activation.rights.none { it is Right.Rental && it.productId == c.productId && it.period == c.period }) continue
            val r = recs.getOrPut(c.key) { Rec() }
            val st = statuses[c.key]
            out[c.key] = when {
                r.phase != RentalPhase.LIVE -> "terminée"
                st == null || !st.usable -> if (st?.state == RentalState.EXPIRED) "terminée" else st?.state?.name?.lowercase() ?: "inconnue"
                vault.hasKey(c.key) -> "clé déjà en place"
                else -> {
                    val key = RentalKeys.openBox(c.box, device, c.productId, c.period)
                    if (key == null) "clé illisible (autre appareil ou enveloppe altérée)" else if (vault.putKey(c.key, key)) "clé installée" else "écriture impossible"
                }
            }
        }
        save(); out
    }

    /** The delivery installed [lot] under this rental: from now on the sweep may remove it at the end. A lot that is free, a trial or already allowed by another right is refused. */
    fun markRented(contractKey: String, lot: LotId, meta: LotMeta, families: LotFamilies, otherwiseAllowed: Set<LotId> = emptySet()): String? = synchronized(lock) {
        RentalPolicy.refusal(meta, families)?.let { return it }
        if (lot in otherwiseAllowed) return "« ${meta.title} » est déjà couvert par un achat ou un abonnement : pas de double location"
        val r = recs[contractKey] ?: return "location inconnue"
        if (r.phase != RentalPhase.LIVE) return RentalEngine.ENDED
        r.lots += LotNames.key(lot); save(); null
    }

    /** A lasting right now covers [lot] (a purchase arrived): the lot leaves the rental, so the sweep never touches it. The delivery must install the normal copy first. */
    fun release(contractKey: String, lot: LotId) = synchronized(lock) { recs[contractKey]?.lots?.remove(LotNames.key(lot)); recs[contractKey]?.removed?.remove(LotNames.key(lot)); save() }

    /**
     * Counts [minutes] of use of [lot] (the app calls this while rented content is open, say every minute). The minutes go to the usable rental covering the lot that ends first; a clock in
     * doubt does not stop the count (usage is the second ceiling exactly for that). Returns the statuses after the update.
     */
    fun recordUsage(lot: LotId, minutes: Int, all: List<Activation>): List<RentalStatus> = synchronized(lock) {
        require(minutes in 0..24 * 60)
        val before = status(all)
        val target = before.filter { RentalLogic.covers(it, lot, recs) && (it.usable || it.state == RentalState.SUSPENDED) }.minByOrNull { it.contract.endsAt }
        if (target != null && minutes > 0) {
            val r = recs.getOrPut(target.key) { Rec() }
            r.used += minutes
            if (target.contract.maxUsageMinutes > 0 && r.used >= target.contract.maxUsageMinutes && r.phase == RentalPhase.LIVE && r.expiredAt == 0L) r.expiredAt = clock.now(wall())
            save()
        }
        status(all)
    }

    /** Marks as ending every contract that is EXPIRED now and still LIVE (called by the sweep). Returns the keys newly marked. */
    fun markEnding(statuses: List<RentalStatus>): List<String> = synchronized(lock) {
        val out = ArrayList<String>()
        for (s in statuses.filter { it.state == RentalState.EXPIRED }) {
            val r = recs.getOrPut(s.key) { Rec() }
            if (r.phase != RentalPhase.LIVE) continue
            r.phase = RentalPhase.EXPIRING; r.reason = s.reason ?: ExpiryReason.DATE
            if (r.expiredAt == 0L) r.expiredAt = if (r.reason == ExpiryReason.DATE) s.contract.graceEndsAt else clock.now(wall())
            out += s.key
        }
        if (out.isNotEmpty()) save(); out
    }

    internal fun advance(key: String, phase: RentalPhase) = synchronized(lock) { recs[key]?.phase = phase; save() }
    internal fun lotDone(key: String, lot: LotId) = synchronized(lock) { recs[key]?.removed?.add(LotNames.key(lot)); save() }
    internal fun addLog(e: RentalLogEntry) = synchronized(lock) { log += e; while (log.size > 200) log.removeAt(0); save() }
    internal fun keys(): List<String> = synchronized(lock) { recs.keys.toList() }

    private fun load() {
        val m = runCatching { JsonLite.obj(file.readText()) }.getOrNull() ?: return
        (m["clock"] as? Map<*, *>)?.let { c -> (c["lastSeen"] as? Number)?.let { clock.lastSeen = it.toLong() }; (c["floor"] as? Number)?.let { clock.floor = it.toLong() } }
        (m["contracts"] as? Map<*, *>)?.forEach { (k, v) ->
            @Suppress("UNCHECKED_CAST") val e = v as? Map<String, Any?> ?: return@forEach
            val r = Rec(e.long("used") ?: 0, runCatching { RentalPhase.valueOf(e.str("phase") ?: "LIVE") }.getOrDefault(RentalPhase.LIVE),
                e.str("reason")?.let { x -> runCatching { ExpiryReason.valueOf(x) }.getOrNull() }, e.long("expiredAt") ?: 0)
            (e["lots"] as? List<*>)?.forEach { r.lots += it.toString() }; (e["removed"] as? List<*>)?.forEach { r.removed += it.toString() }
            recs[k.toString()] = r
        }
        (m["log"] as? List<*>)?.forEach { x ->
            @Suppress("UNCHECKED_CAST") val e = x as? Map<String, Any?> ?: return@forEach
            log += RentalLogEntry(e.long("at") ?: 0, e.str("lot") ?: "", e.str("reason") ?: "", e.str("step") ?: "")
        }
    }

    private fun save() {
        dir.mkdirs()
        val json = JsonLite.write(linkedMapOf(
            "clock" to linkedMapOf("lastSeen" to clock.lastSeen, "floor" to clock.floor),
            "contracts" to recs.mapValues { (_, r) -> linkedMapOf("used" to r.used, "phase" to r.phase.name, "reason" to r.reason?.name, "expiredAt" to r.expiredAt, "lots" to r.lots.toList(), "removed" to r.removed.toList()) },
            "log" to log.map { linkedMapOf("at" to it.at, "lot" to it.lot, "reason" to it.reason, "step" to it.step) }))
        val tmp = File(dir, "rentals.json.tmp"); tmp.writeText(json)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

internal object RentalLogic {
    /** Does this rental cover [lot]? Only through the lots registered as rented under it (the delivery's record), never by guessing from bundle names. */
    fun covers(s: RentalStatus, lot: LotId, recs: Map<String, RentalLedger.Rec>) = recs[s.key]?.lots?.contains(LotNames.key(lot)) == true
}
