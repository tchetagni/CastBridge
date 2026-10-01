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
 *  3. `PolicyHub.observeClock()` at start and every hour (the TV's high-water clock);
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
    }

    val state: PolicyState get() = engine.current
    fun onOwnerFrame(type: Int, payload: ByteArray): List<ByteArray>? = rx.onFrame(type, payload)
    fun observeClock() = engine.observeClock()
    /** Read-only lines for the « Politiques appliquées » screen, newest first. */
    fun journalLines(): List<String> = engine.journal().map { it.line() }
}
