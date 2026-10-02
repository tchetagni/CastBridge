package castbridge.receiver

import android.content.Context
import castbridge.core.lots.FileQueueStore
import castbridge.core.owner.DeviceContext
import castbridge.core.owner.KeyRing
import castbridge.core.policy.*
import java.io.File

/**
 * The TV's policy engine (docs/ORDRES.md § Téléviseur): one [PolicyEngine] over a small JSON file, and the receiver of the order frames of the owner Bluetooth channel.
 * NOT COMPILED in the cloud. Wiring left to the coordinator, in this order:
 *  1. `PolicyHub.init(ctx, keyRing, ::deviceContext)` at start (the key ring and the device fingerprints come from the owner/activation code of `claude/trial-edition`);
 *  2. in the owner-channel server (service `…0004`, handshake `CBTO`), for every frame: `PolicyHub.onOwnerFrame(type, payload)?.forEach(write)`; a null result means "not mine, let the other handlers have it";
 *  3. `PolicyHub.observeClock()` at start; since `init` it is then repeated by itself every 5 minutes of running ([CLOCK_PERIOD_MS]), so a reboot loses at most 5 minutes;
 *  4. the gate: `PolicyGate.effective(...)` where the app computes its `GateState`; `PolicyHub.state` for flags, minimum version, budgets, retired lots, messages;
 *  5. the read-only screen « À propos > Politiques appliquées » listing [journalLines] (no button, nothing to edit), and the first-launch usage notice (text to be validated by the owner).
 */
object PolicyHub {
    private lateinit var engine: PolicyEngine
    private lateinit var rx: TvOrderReceiver

    @Synchronized fun init(ctx: Context, keys: KeyRing, device: () -> DeviceContext) {
        if (::engine.isInitialized) return
        engine = PolicyEngine(keys, PolicyStorage(FileQueueStore(File(ctx.filesDir, "policy/state.json"))), device)
        rx = TvOrderReceiver(engine)
        java.util.Timer("castbridge-policy-clock", true).schedule(object : java.util.TimerTask() { override fun run() { runCatching { engine.observeClock() } } }, CLOCK_PERIOD_MS, CLOCK_PERIOD_MS)
    }

    /** Period of the clock observation of the policy engine (was one hour: a reboot every less than an hour could freeze the clock). */
    const val CLOCK_PERIOD_MS = 5 * 60_000L

    val state: PolicyState get() = engine.current
    fun onOwnerFrame(type: Int, payload: ByteArray): List<ByteArray>? = rx.onFrame(type, payload)
    fun observeClock() = engine.observeClock()
    /** Read-only lines for the « Politiques appliquées » screen, newest first. */
    fun journalLines(): List<String> = engine.journal().map { it.line() }
}
