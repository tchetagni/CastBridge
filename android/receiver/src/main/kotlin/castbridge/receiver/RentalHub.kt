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
    private val warming = java.util.concurrent.atomic.AtomicBoolean(false)
    private val noteTaken = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lock = java.util.concurrent.locks.ReentrantLock()
    @Volatile private var gate: InstallKeyGate? = null
    private const val TAG = "RentalHub"

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "rental")

    /** The retry gate (backoff 1 min then 1 h, counters across process starts in `install.key.retry`): see [InstallKeyGate] / [InstallKeyPolicy]. */
    private fun gate(ctx: Context): InstallKeyGate = gate ?: synchronized(this) { gate ?: InstallKeyGate(dir(ctx), android.os.SystemClock::elapsedRealtime).also { gate = it } }

    /**
     * The safe, the ledger and the installation key. Never waits more than [waitMs] for another thread that is preparing them, and never retries the Keystore before the backoff of
     * [InstallKeyGate] allows it: it throws instead (callers answer 503 / an empty list). A Keystore failure never reaches `TvService.onCreate` (nothing there calls this).
     */
    private fun ensure(ctx: Context, waitMs: Long = 2_000): Parts {
        parts?.let { return it }
        val g = gate(ctx)
        if (!g.mayAttempt()) throw InstallKeyUnavailableException(InstallKeyPolicy.UNAVAILABLE_MESSAGE)
        if (!lock.tryLock(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS)) throw InstallKeyUnavailableException("clé d'installation en préparation")
        try {
            parts?.let { return it }
            if (!g.mayAttempt()) throw InstallKeyUnavailableException(InstallKeyPolicy.UNAVAILABLE_MESSAGE)
            return try { build(ctx.applicationContext, g).also { parts = it; g.succeeded() } } catch (e: Exception) {
                // logged ONCE per process (no flooding: the gate spaces the attempts 1 min, then 1 h)
                if (g.failed(countsTowardRegeneration = e is InstallKeyUnavailableException)) Log.w(TAG, "clé d'installation indisponible : nouvel essai dans ${InstallKeyPolicy.delayAfter(1) / 1000} s puis toutes les heures", e)
                throw e
            }
        } finally { lock.unlock() }
    }

    private fun build(app: Context, g: InstallKeyGate): Parts {
        val dir = dir(app)
        val vault = RentalVault(dir)
        // The installation key (X25519): a NEW key goes under the Android Keystore when its probe works, else (stated) in the clear; a STORED key is always read with the envelope its file
        // names (KeystoreWrapper.install() for `keystore`, never the plain fallback) and a plain key moves into the Keystore as soon as it works (InstallKeyStore, InstallKeyPolicy).
        val probeLogged = java.util.concurrent.atomic.AtomicBoolean(false)
        val wrapper = KeystoreWrapper.orPlain { m, t -> if (g.failuresThisProcess == 0 && probeLogged.compareAndSet(false, true)) Log.w(TAG, m, t) }
        val keys = InstallKeyStore(dir, wrapper, readers = { label ->
            when (label) { "keystore" -> KeystoreWrapper.install(); InstallKeyPolicy.PLAIN -> castbridge.core.crypto.PlainWrapper(); else -> null }
        })
        try { keys.loadOrCreate() } catch (e: InstallKeyUnavailableException) {
            // the Keystore has failed over several process starts: give the key up (stated « illisible : demandez la réémission »), the old file is kept aside and retried at every start
            if (!g.mustRegenerate()) throw e
            Log.e(TAG, "coffre de clés indisponible depuis plusieurs démarrages : clé d'installation régénérée", e)
            keys.regenerateAssumingLost()
        }
        keys.loadNote?.let { Log.w(TAG, it) }
        val ledger = RentalLedger(dir, TvClock(mono = android.os.SystemClock::elapsedRealtime), RentalConfig(), System::currentTimeMillis)
        ledger.loadNote?.let { Log.e(TAG, it) }
        val rented = TvRentedLots(LotsHub.store(app))
        // Lots a lasting right covers (a lot bought during its rental is kept at expiry). The TV has NO bundle catalogue: OwnedLots resolves only what needs none (owner grants, `tout`), see its doc.
        val owned = { OwnedLots.of(ActivationCenter.allActivations(), ActivationCenter.now(), rented.heldLots()) }
        val sweeper = RentalSweeper(ledger, vault, rented, { ActivationCenter.allActivations() }, owned, System::currentTimeMillis, {}, { k, e -> Log.e(TAG, "balayage de la location $k en échec (nouvel essai au prochain balayage)", e) })
        return Parts(vault, ledger, sweeper, keys)
    }

    /**
     * Prepares the safe and the installation key (Keystore) on a background thread, so no caller on the main thread ever waits for it. Safe to call often: one thread at a time
     * ([warming]), and none while the backoff of the gate forbids a new attempt.
     */
    fun warm(ctx: Context) {
        if (parts != null) return
        val app = ctx.applicationContext
        if (!gate(app).mayAttempt() || !warming.compareAndSet(false, true)) return
        Thread { try { runCatching { ensure(app, waitMs = 30_000) } } finally { warming.set(false) } }.apply { isDaemon = true; name = "rental-warm" }.start()
    }

    private fun onMainThread() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    /** The installation's public key if it is ready, without ever blocking the main thread (null there while it is being prepared, the warm-up is started). For the device request. */
    fun installPubOrNull(ctx: Context): ByteArray? {
        parts?.let { p -> return runCatching { p.keys.loadOrCreate().pub }.getOrNull() }
        if (onMainThread()) { warm(ctx); return null }
        return runCatching { ensure(ctx).keys.loadOrCreate().pub }.getOrNull()
    }

    /** `installKeyProtection` of `GET /api/activation`: `keystore` / `plain` once ready, `unavailable` after a failed attempt, `pending` while being prepared. Never blocks. */
    fun protectionStatus(ctx: Context): String {
        val p = parts
        if (p == null) warm(ctx)
        return InstallKeyPolicy.protection(p?.keys?.protection, gate(ctx).failuresThisProcess)
    }

    /** The installation id once the key is ready, "" otherwise. Never blocks. */
    fun installIdOrEmpty(): String = parts?.let { p -> runCatching { p.keys.loadOrCreate().installId }.getOrNull() }.orEmpty()

    /** This installation's key pair (works locked or trial: nothing here needs the core to be started). A key lost for good gives a new one; a key that cannot be read FOR NOW (Keystore) throws, may block up to 2 s: never on the main thread. */
    fun installKey(ctx: Context): InstallKey = ensure(ctx).keys.loadOrCreate()

    /** The rental safe, for the sealed lots of the other hubs. */
    fun vault(ctx: Context): RentalVault = ensure(ctx).vault

    /**
     * An activation was accepted: the keys of its usable rentals go into the safe (a swept or ended contract is never reopened). The installation key IS passed to the ledger, which
     * enforces the v1 box sunset from then on ([installKeyed]). Returns the French notes worth showing (a box for another installation, a refused v1 box, a regenerated key).
     */
    fun onActivation(ctx: Context, a: Activation): List<String> {
        val p = ensure(ctx)
        val result = p.ledger.installKeyed(a, ActivationCenter.allActivations(), ActivationCenter.fingerprints(), p.vault, p.keys.loadOrCreate())
        // the key's load note is cached for the whole process: said once, not at every activation
        val keyNote = p.keys.loadNote?.takeIf { "illisible" in it && noteTaken.compareAndSet(false, true) }?.let { "$it : demandez la réémission des locations" }
        val notes = RentalNotes.of(result) + listOfNotNull(keyNote)
        notes.forEach { Log.w(TAG, it) }
        return notes
    }

    /** The state of every rental, for the access computation and the screens. */
    fun statuses(ctx: Context): List<RentalStatus> = runCatching {
        // never block the main thread on the first generation of the Keystore key: nothing yet, and the background warm-up is started
        if (parts == null && onMainThread()) { warm(ctx); return@runCatching emptyList<RentalStatus>() }
        ensure(ctx).ledger.status(ActivationCenter.allActivations())
    }.getOrDefault(emptyList())

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

    /**
     * Routes: GET /api/rental, POST /api/rental/install, POST /api/rental/sweep, POST /api/activation/install (PIN only: the body is an activation, rentals included). Built LAZILY: nothing
     * here touches the Keystore when the server is set up (`TvService.onCreate`); a rental route whose key cannot be read answers 503 « coffre de clés indisponible » ([LazyKeyedApi]).
     */
    fun api(ctx: Context): ApiExtension {
        val app = ctx.applicationContext
        return LazyKeyedApi(setOf("/api/rental", "/api/rental/install", "/api/rental/sweep"), setOf("/api/rental/install")) {
            val p = ensure(app)
            RentalApi(LotsHub.store(app), p.ledger, p.vault, { ActivationCenter.allActivations() }, p.sweeper)
        }.then(ActivationInstallApi(app))
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
            is ActivationResult.Accepted -> ApiReply(200, """{"installed":true,"label":${castbridge.core.tv.ReceiverServer.q(ActivationCenter.label())},"notes":[${ActivationCenter.lastRentalNotes.joinToString(",") { castbridge.core.tv.ReceiverServer.q(it) }}]}""")
            is ActivationResult.Rejected -> ApiReply(422, """{"error":${castbridge.core.tv.ReceiverServer.q(r.message)}}""")
        }
    }
}
