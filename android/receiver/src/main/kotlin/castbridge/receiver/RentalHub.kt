package castbridge.receiver

import android.content.Context
import android.util.Log
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
    private class Parts(val vault: RentalVault, val ledger: RentalLedger, val sweeper: RentalSweeper, val keys: InstallKeyStore)
    @Volatile private var parts: Parts? = null
    private const val TAG = "RentalHub"

    @Synchronized private fun ensure(ctx: Context): Parts = parts ?: run {
        val app = ctx.applicationContext
        val dir = File(app.filesDir, "rental")
        val vault = RentalVault(dir)
        // The installation key (X25519, wrapped by the Android Keystore or, stated, in the clear): made here, not in `ActivationCenter.init`, and without the core started (works on the locked screen).
        val keys = InstallKeyStore(dir, KeystoreWrapper.orPlain())
        keys.loadOrCreate()
        keys.loadNote?.let { Log.w(TAG, it) }
        val ledger = RentalLedger(dir, TvClock(mono = android.os.SystemClock::elapsedRealtime), RentalConfig(), System::currentTimeMillis)
        ledger.loadNote?.let { Log.e(TAG, it) }
        val rented = TvRentedLots(LotsHub.store(app))
        // Lots a lasting right covers (a lot bought during its rental is kept at expiry). The TV has NO bundle catalogue: OwnedLots resolves only what needs none (owner grants, `tout`), see its doc.
        val owned = { OwnedLots.of(ActivationCenter.allActivations(), ActivationCenter.now(), rented.heldLots()) }
        val sweeper = RentalSweeper(ledger, vault, rented, { ActivationCenter.allActivations() }, owned, System::currentTimeMillis, {}, { k, e -> Log.e(TAG, "balayage de la location $k en échec (nouvel essai au prochain balayage)", e) })
        Parts(vault, ledger, sweeper, keys).also { parts = it }
    }

    /** This installation's key pair (works locked or trial: nothing here needs the core to be started). Never throws for a missing or unreadable file: a new key is made. */
    fun installKey(ctx: Context): InstallKey = ensure(ctx).keys.loadOrCreate()

    /** `keystore` (wrapped by the Android Keystore) or `plain` (stated fallback): for `GET /api/activation` and the admin page. */
    fun installProtection(ctx: Context): String = ensure(ctx).keys.protection

    /** The rental safe, for the sealed lots of the other hubs. */
    fun vault(ctx: Context): RentalVault = ensure(ctx).vault

    /**
     * An activation was accepted: the keys of its usable rentals go into the safe (a swept or ended contract is never reopened). The installation key IS passed to the ledger, which
     * enforces the v1 box sunset from then on ([installKeyed]). Returns the French notes worth showing (a box for another installation, a refused v1 box, a regenerated key).
     */
    fun onActivation(ctx: Context, a: Activation): List<String> {
        val p = ensure(ctx)
        val result = p.ledger.installKeyed(a, ActivationCenter.allActivations(), ActivationCenter.fingerprints(), p.vault, p.keys.loadOrCreate())
        val notes = RentalNotes.of(result) + listOfNotNull(p.keys.loadNote?.takeIf { "illisible" in it }?.let { "$it : demandez la réémission des locations" })
        notes.forEach { Log.w(TAG, it) }
        return notes
    }

    /** The state of every rental, for the access computation and the screens. */
    fun statuses(ctx: Context): List<RentalStatus> = runCatching { ensure(ctx).ledger.status(ActivationCenter.allActivations()) }.getOrDefault(emptyList())

    /** The deletion at the end of a rental: the French notices to show, if any. Safe to call often (idempotent). */
    /**
     * One minute of USE: called every minute while « Apprendre » is open. The minute is counted on a rental that has a usage ceiling (the trial key's 12 h window) and holds a lot, so the
     * window runs out by reading, not by the calendar. Past the ceiling the next sweep deletes the lots. Returns the statuses after counting.
     */
    fun meterOneMinute(ctx: Context): List<RentalStatus> = runCatching {
        val p = ensure(ctx); val acts = ActivationCenter.allActivations()
        val lot = p.ledger.status(acts).filter { it.contract.maxUsageMinutes > 0 && it.usable }
            .firstNotNullOfOrNull { s -> p.ledger.rentedLots(s.key).firstNotNullOfOrNull { castbridge.core.lots.LotNames.parseKey(it) } } ?: return@runCatching emptyList()
        p.ledger.recordUsage(lot, 1, acts).also { if (it.any { s -> s.state == castbridge.core.lots.RentalState.EXPIRED }) sweep(ctx, SweepTrigger.LEARN_SCREEN, lessonActive = true) }
    }.getOrDefault(emptyList())

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
        // A key sent from the phone over the Wi-Fi opens a locked or trial TV only after the terms of use were accepted ON the TV (activation screen), like a key typed or read from a USB drive.
        if ((ActivationCenter.locked() || ActivationCenter.trial()) && !TunnelHub.termsAccepted(ctx))
            return ApiReply(409, """{"error":${castbridge.core.tv.ReceiverServer.q(castbridge.core.tunnel.TunnelTerms.MUST_ACCEPT_ON_TV)}}""")
        return when (val r = ActivationCenter.accept(Channel.MANUAL, text.toByteArray(Charsets.UTF_8))) {
            is ActivationResult.Accepted -> ApiReply(200, """{"installed":true,"label":${castbridge.core.tv.ReceiverServer.q(ActivationCenter.label())}}""")
            is ActivationResult.Rejected -> ApiReply(422, """{"error":${castbridge.core.tv.ReceiverServer.q(r.message)}}""")
        }
    }
}
