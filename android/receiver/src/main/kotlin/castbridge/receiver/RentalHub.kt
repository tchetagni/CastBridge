package castbridge.receiver

import android.content.Context
import castbridge.core.lots.*
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.Channel
import castbridge.core.owner.TvClock
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import castbridge.core.tv.then
import java.io.File

/**
 * Rented lots on the TV (docs/RENTAL-LOTS.md): the rental safe (keys, encrypted files), the ledger (contracts, usage, clock), the autonomous sweep, and the routes the phone / owner tools use.
 * Rented lots arrive sealed, are opened with the key that the rental's activation carried for THIS TV, installed like any lot, and DELETED by the sweep when the rental ends, except in an
 * account activated by the super administrator code (SUPER_UNLIMITED), where rentals are permanent. The TV stays offline: nothing here reaches the Internet.
 */
object RentalHub {
    private class Parts(val vault: RentalVault, val ledger: RentalLedger, val sweeper: RentalSweeper)
    @Volatile private var parts: Parts? = null

    @Synchronized private fun ensure(ctx: Context): Parts = parts ?: run {
        val app = ctx.applicationContext
        val dir = File(app.filesDir, "rental")
        val vault = RentalVault(dir)
        val ledger = RentalLedger(dir, TvClock(), RentalConfig(), System::currentTimeMillis)
        val sweeper = RentalSweeper(ledger, vault, TvRentedLots(LotsHub.store(app)), { ActivationCenter.allActivations() }, { emptySet() }, System::currentTimeMillis)
        Parts(vault, ledger, sweeper).also { parts = it }
    }

    /** An activation was accepted: the keys of its usable rentals go into the safe (a swept or ended contract is never reopened). */
    fun onActivation(ctx: Context, a: Activation) {
        val p = ensure(ctx)
        p.ledger.install(a, ActivationCenter.allActivations(), ActivationCenter.fingerprints(), p.vault)
    }

    /** The state of every rental, for the access computation and the screens. */
    fun statuses(ctx: Context): List<RentalStatus> = runCatching { ensure(ctx).ledger.status(ActivationCenter.allActivations()) }.getOrDefault(emptyList())

    /** The deletion at the end of a rental: the French notices to show, if any. Safe to call often (idempotent). */
    fun sweep(ctx: Context, trigger: SweepTrigger, lessonActive: Boolean = false): List<String> =
        runCatching { ensure(ctx).sweeper.sweep(trigger, lessonActive).notices }.getOrDefault(emptyList())

    /** Routes: GET /api/rental, POST /api/rental/install, POST /api/rental/sweep, POST /api/activation/install (PIN only: the body is an activation, rentals included). */
    fun api(ctx: Context): ApiExtension {
        val p = ensure(ctx)
        return RentalApi(LotsHub.store(ctx), p.ledger, p.vault, { ActivationCenter.allActivations() }, p.sweeper).then(ActivationInstallApi(ctx.applicationContext))
    }
}

/** POST /api/activation/install: the body is an activation (full token, grouped text or compact key). Verified exactly like a typed one, then installed (rentals' keys go into the safe). */
private class ActivationInstallApi(private val ctx: Context) : ApiExtension {
    override fun wantsBody(path: String) = path == "/api/activation/install"
    /** GET /api/activation/request: the device request (code + fingerprints, no raw value) that the owner's tool needs to build this TV's activation. */
    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != "/api/activation/request" || method != "GET") return null
        ActivationCenter.init(ctx)
        return ApiReply(200, """{"request":${castbridge.core.tv.ReceiverServer.q(ActivationCenter.requestText())}}""")
    }
    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != "/api/activation/install" || method != "POST") return null
        ActivationCenter.init(ctx)
        val text = String(body, Charsets.UTF_8).trim()
        return when (val r = ActivationCenter.accept(Channel.MANUAL, text.toByteArray(Charsets.UTF_8))) {
            is ActivationResult.Accepted -> ApiReply(200, """{"installed":true,"label":${castbridge.core.tv.ReceiverServer.q(ActivationCenter.label())}}""")
            is ActivationResult.Rejected -> ApiReply(422, """{"error":${castbridge.core.tv.ReceiverServer.q(r.message)}}""")
        }
    }
}
