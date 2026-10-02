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
    /** The safe, the ledger and the sweeper: built WITHOUT the installation key (the Keystore can fail for good, the sweep and the 12 h meter must keep running). */
    private class Parts(val vault: RentalVault, val ledger: RentalLedger, val sweeper: RentalSweeper)
    @Volatile private var parts: Parts? = null
    @Volatile private var keys: InstallKeyStore? = null
    private val warming = java.util.concurrent.atomic.AtomicBoolean(false)
    /** TRUE at every process start: an activation accepted while the key was unavailable, then a restart, must still be installed once the key is ready (the ledger skips what is in place). */
    private val replayNeeded = java.util.concurrent.atomic.AtomicBoolean(true)
    private val unreadableLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lock = java.util.concurrent.locks.ReentrantLock()
    private val gate = InstallKeyGate(android.os.SystemClock::elapsedRealtime)
    private const val TAG = "RentalHub"
    private val READY = InstallKeyPolicy.KeyState.READY
    private val UNREADABLE = InstallKeyPolicy.KeyState.UNREADABLE

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "rental")

    private fun parts(ctx: Context): Parts = parts ?: synchronized(this) { parts ?: build(ctx.applicationContext).also { parts = it } }

    private fun build(app: Context): Parts {
        val dir = dir(app)
        val vault = RentalVault(dir)
        val ledger = RentalLedger(dir, TvClock(mono = android.os.SystemClock::elapsedRealtime), RentalConfig(), System::currentTimeMillis)
        ledger.loadNote?.let { Log.e(TAG, it) }
        val rented = TvRentedLots(LotsHub.store(app))
        // Lots a lasting right covers (a lot bought during its rental is kept at expiry). The TV has NO bundle catalogue: OwnedLots resolves only what needs none (owner grants, `tout`), see its doc.
        val owned = { OwnedLots.of(ActivationCenter.allActivations(), ActivationCenter.now(), rented.heldLots()) }
        val sweeper = RentalSweeper(ledger, vault, rented, { ActivationCenter.allActivations() }, owned, System::currentTimeMillis, {}, { k, e -> Log.e(TAG, "balayage de la location $k en échec (nouvel essai au prochain balayage)", e) })
        return Parts(vault, ledger, sweeper)
    }

    /** The installation key's store (nothing touches the Keystore until it is used): a stored key is read by `KeystoreWrapper.install()` (never creates), a new one is made by `creator()`. */
    private fun keyStore(ctx: Context): InstallKeyStore = keys ?: synchronized(this) {
        keys ?: InstallKeyStore(dir(ctx), KeystoreWrapper.creator(), readers = { label -> if (label == "keystore") KeystoreWrapper.install() else null }).also { keys = it }
    }

    private fun mayTry(ks: InstallKeyStore) = ks.state == READY || ks.state == UNREADABLE || gate.mayAttempt()      // READY: cached, UNREADABLE: throws at once, no attempt

    /**
     * The installation key. Never waits more than [waitMs] for another thread, never retries before the backoff of [InstallKeyGate] (1 min, then 1 h, forever) and never retries an
     * UNREADABLE key (until [resetInstallKey]): it throws instead ([InstallKeyUnavailableException] / [InstallKeyUnreadableException]); callers answer 503 / keep going without it.
     * Nothing here is called by `TvService.onCreate`.
     */
    private fun key(ctx: Context, waitMs: Long = 2_000): InstallKey {
        val ks = keyStore(ctx)
        if (ks.state == READY) return ks.loadOrCreate()
        if (!mayTry(ks)) throw InstallKeyUnavailableException(InstallKeyPolicy.UNAVAILABLE_MESSAGE)
        if (!lock.tryLock(waitMs, java.util.concurrent.TimeUnit.MILLISECONDS)) throw InstallKeyUnavailableException("clé d'installation en préparation")
        try {
            if (!mayTry(ks)) throw InstallKeyUnavailableException(InstallKeyPolicy.UNAVAILABLE_MESSAGE)
            val k = try { ks.loadOrCreate() } catch (e: InstallKeyUnavailableException) {
                if (gate.failed()) Log.w(TAG, "clé d'installation indisponible : nouvel essai dans ${InstallKeyPolicy.delayAfter(1) / 1000} s puis toutes les heures", e)    // once per process
                throw e
            } catch (e: InstallKeyUnreadableException) {
                if (unreadableLogged.compareAndSet(false, true)) Log.e(TAG, "clé d'installation illisible : réinitialisation par le propriétaire nécessaire", e)
                throw e
            }
            gate.succeeded(); ks.loadNote?.let { Log.w(TAG, it) }
            replayIfNeeded(ctx, k)
            return k
        } finally { lock.unlock() }
    }

    /** Activations accepted while the key was not ready are installed (their v2 boxes opened) as soon as it is: the ledger skips what is already in place. */
    private fun replayIfNeeded(ctx: Context, k: InstallKey) {
        if (!replayNeeded.compareAndSet(true, false)) return
        val p = parts(ctx); val all = ActivationCenter.allActivations()
        for (a in all) runCatching { p.ledger.installKeyed(a, all, ActivationCenter.fingerprints(), p.vault, k) }.onFailure { Log.w(TAG, "réinstallation différée d'une activation en échec", it) }
        Log.w(TAG, "activations enregistrées : locations réinstallées une fois la clé prête (déjà en place : ignorées)")
    }

    /** Prepares the key (Keystore) on a background thread, so no caller on the main thread ever waits for it. One thread at a time, none while the backoff forbids a new attempt or the key is unreadable. */
    fun warm(ctx: Context) {
        val app = ctx.applicationContext; val ks = keyStore(app)
        if (ks.state == READY || ks.state == UNREADABLE || !gate.mayAttempt() || !warming.compareAndSet(false, true)) return
        Thread { try { runCatching { key(app, waitMs = 30_000) } } finally { warming.set(false) } }.apply { isDaemon = true; name = "rental-warm" }.start()
    }

    private fun onMainThread() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    /** The installation's public key if it is ready, without ever blocking the main thread (null there while it is being prepared, the warm-up is started). For the device request. */
    fun installPubOrNull(ctx: Context): ByteArray? {
        val ks = keyStore(ctx)
        if (ks.state == READY) return runCatching { ks.loadOrCreate().pub }.getOrNull()
        if (onMainThread()) { warm(ctx); return null }
        return runCatching { key(ctx).pub }.getOrNull()
    }

    /** `installKeyProtection` of `GET /api/activation`: `keystore` / `pending` / `unavailable` / `unreadable`. Never blocks (starts the warm-up when useful). */
    fun protectionStatus(ctx: Context): String { warm(ctx); return keyStore(ctx).state.protection }

    /** The installation id once the key is ready, "" otherwise. Never blocks. */
    fun installIdOrEmpty(ctx: Context): String = keyStore(ctx).takeIf { it.state == READY }?.let { runCatching { it.loadOrCreate().installId }.getOrNull() }.orEmpty()

    /** The rental safe, for the sealed lots of the other hubs. */
    fun vault(ctx: Context): RentalVault = parts(ctx).vault

    /**
     * An activation was accepted (and stored): the keys of its usable rentals go into the safe (a swept or ended contract is never reopened), the installation key IS passed to the ledger, which
     * enforces the v1 box sunset ([installKeyed]). While the key is not ready the activation stays stored, [replayIfNeeded] installs it as soon as the key is, and the note says so.
     * Returns the French notes worth showing (a box for another installation, a refused v1 box, the key's state).
     */
    fun onActivation(ctx: Context, a: Activation): List<String> {
        val p = parts(ctx)
        val k = try { key(ctx) } catch (e: InstallKeyUnreadableException) { replayNeeded.set(true); return listOf(InstallKeyPolicy.UNREADABLE_MESSAGE) }
            catch (e: InstallKeyUnavailableException) { replayNeeded.set(true); warm(ctx); return listOf("coffre de clés indisponible : l'activation est enregistrée, ses locations seront installées dès que le coffre répondra") }
        val notes = RentalNotes.of(p.ledger.installKeyed(a, ActivationCenter.allActivations(), ActivationCenter.fingerprints(), p.vault, k))
        notes.forEach { Log.w(TAG, it) }
        return notes
    }

    /**
     * EXPLICIT owner reset of the installation key (route POST /api/activation/install-key/reset, behind the TV's authentication): the files are renamed `.reset-<ms>`, never deleted, a fresh
     * key is made and the activations stored meanwhile are installed again. Returns the note « clé d'installation réinitialisée : réémettez les locations ». Refused ([InstallKeyResetRefusedException]) unless the key is unreadable ([InstallKeyPolicy.resetAllowed]); throws when the Keystore is unavailable.
     */
    fun resetInstallKey(ctx: Context): String {
        val ks = keyStore(ctx)
        if (!lock.tryLock(30, java.util.concurrent.TimeUnit.SECONDS)) throw InstallKeyUnavailableException("clé d'installation en préparation")
        try {
            if (!InstallKeyPolicy.resetAllowed(ks.state, ks.state == InstallKeyPolicy.KeyState.PENDING && !ks.isAbsent())) throw InstallKeyResetRefusedException(InstallKeyPolicy.RESET_REFUSED)
            val note = ks.reset(); Log.w(TAG, note)
            gate.succeeded(); unreadableLogged.set(false); replayNeeded.set(true); replayIfNeeded(ctx, ks.loadOrCreate())
            return note
        } finally { lock.unlock() }
    }

    /** The state of every rental, for the access computation and the screens (works whatever the state of the installation key). */
    fun statuses(ctx: Context): List<RentalStatus> = runCatching { parts(ctx).ledger.status(ActivationCenter.allActivations()) }.getOrDefault(emptyList())

    /**
     * One minute of USE: called every minute while « Apprendre » is open. The minute is counted on a rental that has a usage ceiling (the trial key's 12 h window) and holds a lot, so the
     * window runs out by reading, not by the calendar. Past the ceiling the next sweep deletes the lots. Returns the statuses after counting.
     */
    fun meterOneMinute(ctx: Context): List<RentalStatus> = runCatching {
        val p = parts(ctx); val acts = ActivationCenter.allActivations()
        val lot = p.ledger.status(acts).filter { it.contract.maxUsageMinutes > 0 && it.usable }
            .firstNotNullOfOrNull { s -> p.ledger.rentedLots(s.key).firstNotNullOfOrNull { castbridge.core.lots.LotNames.parseKey(it) } } ?: return@runCatching emptyList()
        p.ledger.recordUsage(lot, 1, acts).also { if (it.any { s -> s.state == castbridge.core.lots.RentalState.EXPIRED }) sweep(ctx, SweepTrigger.LEARN_SCREEN, lessonActive = true) }
    }.getOrDefault(emptyList())

    /** The deletion at the end of a rental: the French notices to show, if any. Safe to call often (idempotent); also gives a pending key another chance (backoff permitting). */
    fun sweep(ctx: Context, trigger: SweepTrigger, lessonActive: Boolean = false): List<String> {
        if (replayNeeded.get()) warm(ctx)
        return runCatching { parts(ctx).sweeper.sweep(trigger, lessonActive).notices }.getOrDefault(emptyList())
    }

    /**
     * Routes: GET /api/rental, POST /api/rental/install, POST /api/rental/sweep, POST /api/activation/install (PIN only: the body is an activation, rentals included). Built LAZILY: nothing
     * here touches the Keystore (the rental routes do not need the installation key); a route whose parts cannot be built answers 503 « coffre de clés indisponible » ([LazyKeyedApi]).
     */
    fun api(ctx: Context): ApiExtension {
        val app = ctx.applicationContext
        return LazyKeyedApi(setOf("/api/rental", "/api/rental/install", "/api/rental/sweep"), setOf("/api/rental/install")) {
            val p = parts(app)
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
