package castbridge.core.lots

import castbridge.core.owner.Activation

/** The TV side of the lot store seen by the sweep: what is installed (the manifest) and how to remove one lot. Nothing else is reachable from the sweep. */
interface RentedLots {
    fun heldLots(): Set<LotId>
    fun lotEdition(id: LotId): Edition?
    fun removeLot(id: LotId)
}

class TvRentedLots(private val store: TvLotStore) : RentedLots {
    override fun heldLots() = store.manifest().lots.map { it.meta.id }.toSet()
    override fun lotEdition(id: LotId) = store.manifest().lots.firstOrNull { it.meta.id == id }?.meta?.edition
    override fun removeLot(id: LotId) = store.remove(id)
}

enum class SweepTrigger { APP_START, LEARN_SCREEN, PHONE_RECONNECT, PERIODIC, LESSON_END }

data class SweepReport(
    val trigger: SweepTrigger, val endedNow: List<String>, val keysDestroyed: List<String>, val lotsRemoved: List<LotId>, val deferred: List<String>,
    /** Lots left in place because a lasting right covers them: the delivery must install the normal copy. */
    val keptBecauseOwned: List<LotId>, val notices: List<String>,
    /** Contracts whose sweep failed (exception): logged, left at their last persisted step and retried at the next sweep; the other contracts were swept all the same. */
    val failures: List<String> = emptyList(),
)

/**
 * The autonomous deletion (docs/RENTAL-LOTS.md § 4). Runs at app start, when the Learn screen opens, at each phone reconnection and every [PERIOD_MS] while the app runs ([tick]).
 * Order, each step persisted and idempotent (a power cut resumes at the same step): 1. mark the ended contracts; 2. DESTROY THE KEY; 3. remove the rented lots (store entry, then encrypted
 * files); 4. done. It removes ONLY lots registered as rented under an ended contract AND present in the lot manifest AND not a trial lot, and only files inside the rental safe.
 * [onStep] is called before every step (the tests use it to cut the power).
 */
class RentalSweeper(
    private val ledger: RentalLedger, private val vault: RentalVault, private val lots: RentedLots, private val activations: () -> List<Activation>,
    /** Lots a purchase, a subscription or a grant allows right now. (An account activated by the super administrator code never reaches the deletion: its rentals are permanent, see [RentalEngine.superUnlimited].) */ private val owned: () -> Set<LotId> = { emptySet() },
    private val wall: () -> Long = System::currentTimeMillis, private val onStep: (String) -> Unit = {},
    /** Called with the contract key and the exception when one contract's sweep fails (the sweep goes on with the next contract). */
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {
    companion object { const val PERIOD_MS = 6L * 3600 * 1000 }

    private var lastPeriodic = Long.MIN_VALUE

    /** For the app's own timer (already ticking every hour for the clock): sweeps when 6 h have passed since the last periodic sweep. */
    fun tick(lessonActive: Boolean = false): SweepReport? {
        val t = wall()
        if (lastPeriodic != Long.MIN_VALUE && t - lastPeriodic < PERIOD_MS) return null
        lastPeriodic = t
        return sweep(SweepTrigger.PERIODIC, lessonActive)
    }

    fun sweep(trigger: SweepTrigger, lessonActive: Boolean = false): SweepReport {
        ledger.observe()
        val ended = ledger.markEnding(ledger.status(activations()))
        val keys = ArrayList<String>(); val removed = ArrayList<LotId>(); val deferred = ArrayList<String>(); val kept = ArrayList<LotId>(); val notices = ArrayList<String>()
        val failures = ArrayList<String>()
        for (key in ledger.keys()) {
            try { sweepContract(key, lessonActive, keys, removed, deferred, kept, notices) }
            catch (e: Throwable) { if (e is VirtualMachineError) throw e; failures += key; onError(key, e) }
        }
        // a doubtful ledger (see RentalLedger.degraded) leaves quarantine only when every contract was tombstoned without failure or delay
        if (ledger.degraded && failures.isEmpty() && deferred.isEmpty() && ledger.allEnded()) ledger.clearDegraded()
        return SweepReport(trigger, ended, keys, removed, deferred, kept, notices.distinct(), failures)
    }

    private fun sweepContract(key: String, lessonActive: Boolean, keys: MutableList<String>, removed: MutableList<LotId>, deferred: MutableList<String>, kept: MutableList<LotId>, notices: MutableList<String>) {
            val rec = ledger.rec(key) ?: return
            if (rec.phase == RentalPhase.LIVE || rec.phase == RentalPhase.DONE) return
            // a lesson in progress delays the end, by at most the configured time after the end
            if (lessonActive && ledger.nowMs() - rec.expiredAt < ledger.config.lessonDeferralMs) { deferred += key; return }
            val reason = (rec.reason ?: ExpiryReason.DATE).name.lowercase()
            if (rec.phase == RentalPhase.EXPIRING) {
                onStep("key:$key")
                check(vault.destroyKey(key)) { "clé non détruite" }
                keys += key
                ledger.advance(key, RentalPhase.KEY_GONE)
                ledger.addLog(RentalLogEntry(wall(), "-", reason, "cle"))
            }
            val held = lots.heldLots(); val own = owned()
            for (k in rec.lots.toList()) {
                if (k in rec.removed) continue
                val id = LotNames.parseKey(k) ?: continue
                onStep("lot:$k")
                if (id in own && id in held) { kept += id; ledger.addLog(RentalLogEntry(wall(), k, reason, "conserve-acquis")); ledger.lotDone(key, id); continue }
                if (id in held && lots.lotEdition(id) != Edition.TRIAL) { lots.removeLot(id); removed += id }
                vault.deleteLotFiles(id)
                ledger.lotDone(key, id)
                ledger.addLog(RentalLogEntry(wall(), k, reason, "fichiers"))
            }
            ledger.advance(key, RentalPhase.DONE)
            notices += RentalEngine.ENDED
    }
}
