package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkPlanner

enum class BondState { NONE, BONDING, BONDED }

enum class TokenCheck { OK, UNREACHABLE, TOKEN_REJECTED }

/** Why [LinkDriver.step] runs. Only [USER] and [BOND_STATE] may contact a TV that said "I do not know you" or "denied". */
enum class Trigger { TIMER, ACL_CONNECTED, ACL_DISCONNECTED, BLUETOOTH_STATE, BOND_STATE, NETWORK, SCREEN_ON, APP_OPENED, WORKER, USER }

/** Everything the driver needs from the phone, as small questions (Android in the app, a fake in the tests). */
interface LinkEnv {
    fun now(): Long
    /** Bluetooth unusable right now (off, no permission, no adapter), or null. */
    fun btProblem(): BtUnavailable.Reason?
    fun bond(address: String): BondState
    /** The phone has a usable network (Wi-Fi or Wi-Fi Direct) at all. */
    fun networkUp(): Boolean
    /** GET /api/hello on this base URL answers like a CastBridge TV (public: used to plan routes). */
    fun probe(base: String): Boolean
    /** An authenticated request (GET /api/info with the token): tells "the TV is there" from "the TV is there but refuses this token" (reinstalled, phone removed, revoked). */
    fun check(base: String, token: String): TokenCheck
    fun foreground(): Boolean
    /** Paired devices that offer the CastBridge service (to follow a TV whose Bluetooth address changed). */
    fun bondedTvs(): List<TvCandidate> = emptyList()
}

/** A token as the phone keeps it (private storage). [issuedAt]/[expiresAt] are phone-clock times: no dependence on the TV's clock. */
data class StoredCredential(val token: String, val issuedAt: Long, val expiresAt: Long)

/** What survives the process dying or the phone rebooting. Never holds a PIN. */
interface LinkStore {
    fun loadModel(): String?
    fun saveModel(text: String)
    fun loadCredential(address: String): StoredCredential?
    fun saveCredential(address: String, c: StoredCredential)
    fun clearCredential(address: String)
}

class MemoryLinkStore : LinkStore {
    var model: String? = null
    val creds = HashMap<String, StoredCredential>()
    override fun loadModel() = model
    override fun saveModel(text: String) { model = text }
    override fun loadCredential(address: String) = creds[TrustRegistry.norm(address)]
    override fun saveCredential(address: String, c: StoredCredential) { creds[TrustRegistry.norm(address)] = c }
    override fun clearCredential(address: String) { creds.remove(TrustRegistry.norm(address)) }
}

/**
 * The brain of the phone's link loop: ONE call to [step] = observe, maybe talk to the TV, update the [LinkMachine], say when to come back.
 * The Android loop only sleeps for [Step.nextInMs] (or until a broadcast) and calls it again; everything that matters is decided here and
 * tested with a fake clock and a fake TV.
 *
 * - Connected: a keep-alive (two probes, never a single timeout) on the Wi-Fi route, a HELLO on the Bluetooth route; a dead Wi-Fi route falls back to
 *   the next one (Wi-Fi Direct, Bluetooth only) with the SAME token, and the faster route is taken again when it answers.
 * - The token is renewed at half of its life; if the renewal fails while the token is still valid, nothing is dropped and the renewal is retried sooner.
 * - A token the TV refused is never sent again ([CredentialGate]); no PIN is ever used here. A TV that says "unknown phone" or "denied" is not asked again
 *   by itself (only [Trigger.USER] or a new Bluetooth pairing).
 * - Storm control: [AttemptLimiter] bounds the HELLOs of this phone; the delays are jittered and the backoff only resets after a stable connection.
 */
class LinkDriver(
    private val link: PhoneLink,
    private val env: LinkEnv,
    private val saved: SavedTvs,
    private val store: LinkStore,
    private val machine: LinkMachine = LinkMachine(),
    private val random: () -> Double = Math::random,
    private val canJoinWifiDirect: () -> Boolean = { false },
    private val limiter: AttemptLimiter = AttemptLimiter(global = 12, perPeer = 12, now = env::now),
    /** The token is treated as expired this long before its time (clocks differ a little). */
    private val skewMs: Long = 60_000,
    private val btKeepAliveMs: Long = 60_000,
    private val minGapMs: Long = 1_000,
) {
    class Step(val view: LinkView, /** null = wait for the user or a system event */ val nextInMs: Long?, val session: LinkSession?)

    val gate = CredentialGate()
    private var model: LinkMachine.Model? = null
    private var session: LinkSession? = null
    private var issuedAt = 0L
    private var lastHelloAt = 0L
    private var lastStepAt = Long.MIN_VALUE / 2
    private var last: Step? = null
    private var rejected = false
    private var forceCheck = false

    val currentModel: LinkMachine.Model? @Synchronized get() = model

    /** The token to present to the TV right now, or null: kept while valid even if the link is lost, never one the TV refused. */
    @Synchronized fun credential(): String? {
        val tv = saved.default() ?: return null
        val now = env.now()
        session?.takeIf { it.tv.address == tv.address && now < it.expiresAt - skewMs && gate.allows(tv.address, it.credential) }?.let { return it.credential }
        return store.loadCredential(tv.address)?.takeIf { now < it.expiresAt - skewMs && gate.allows(tv.address, it.token) }?.token
    }

    /** The TV's API said this token is expired or revoked (HTTP 401 "bad token"): it is dropped and a new HELLO follows at the next [step]. */
    @Synchronized fun reportTokenRejected(token: String) {
        val tv = saved.default() ?: return
        dropToken(tv, token)
        rejected = true
    }

    private fun dropToken(tv: SavedTv, token: String) {
        gate.refuse(tv.address, token)
        if (session?.credential == token) session = null
        store.loadCredential(tv.address)?.let { if (it.token == token) store.clearCredential(tv.address) }
    }

    /** A session obtained by the pairing flow becomes the current one (credential stored, state "connected" at once). */
    @Synchronized fun adopt(s: LinkSession) {
        val now = env.now()
        saved.upsert(s.tv, makeDefault = true)
        session = s; issuedAt = now; lastHelloAt = now; gate.clear(s.tv.address); rejected = false
        store.saveCredential(s.tv.address, StoredCredential(s.credential, now, s.expiresAt))
        model = machine.reduce((model ?: machine.initial(true, s.tv.name)).copy(shown = LinkState.Connecting, since = 0), connected(s), now)
        last = null
    }

    /** Forgets the TV locally (the user chose « Oublier » or « Réassocier »). */
    @Synchronized fun forget(address: String) {
        saved.remove(address); store.clearCredential(address); gate.clear(TrustRegistry.norm(address))
        session = null; model = null; last = null
    }

    @Synchronized fun step(trigger: Trigger = Trigger.TIMER): Step {
        val now = env.now()
        last?.let { l -> if (trigger != Trigger.USER && now - lastStepAt < minGapMs) return Step(l.view, (minGapMs - (now - lastStepAt)).coerceAtLeast(100), l.session) }   // a burst of broadcasts is one check
        lastStepAt = now
        forceCheck = trigger == Trigger.ACL_DISCONNECTED || trigger == Trigger.USER || trigger == Trigger.APP_OPENED || trigger == Trigger.SCREEN_ON || trigger == Trigger.BLUETOOTH_STATE   // a hint that the link may be dead: look now
        val tv0 = saved.default()
        var m = model ?: restore(tv0, now)
        if (tv0 == null) { session = null; return done(m, machine.reduce(m, Outcome.NoTv, now), now, trigger) }
        var tv: SavedTv = tv0
        m = m.copy(tvName = tv.name)
        if (rejected) { rejected = false; m = machine.reduce(m, Outcome.TokenRejected, now) }

        env.btProblem()?.let { return done(m, machine.reduce(m, Outcome.Bluetooth(it), now), now, trigger) }   // the session and its token are kept: Wi-Fi may still work

        var bond = env.bond(tv.address)
        if (bond == BondState.NONE) follow(tv)?.let { tv = it; bond = env.bond(tv.address) }
        if (bond == BondState.NONE) return done(m, machine.reduce(m, Outcome.NotBonded, now), now, trigger)
        if (bond == BondState.BONDING) return done(m, machine.reduce(m, Outcome.Bonding, now), now, trigger)

        // a TV that does not know us (or denied us) is not bothered again by timers, broadcasts or the worker
        if (trigger != Trigger.USER && trigger != Trigger.BOND_STATE && machine.nextAttempt(m, env.foreground(), random) is Retry.Never && m.shown !is LinkState.NotBonded && m.shown !is LinkState.BtBlocked)
            return finish(m, now, null)

        val out = observe(tv, now).map { o -> if (o == Outcome.Alive && !m.shown.isGood) session?.let(::connected) ?: o else o }   // "all is well" after a problem is a plain "connected"
        return done(m, out.fold(m) { acc, o -> machine.reduce(acc, o, now) }, now, trigger)
    }

    // ---------------------------------------------------------------------------------------------------- observation

    private fun observe(tv: SavedTv, now: Long): List<Outcome> {
        val cur = session?.takeIf { it.tv.address == tv.address }
        if (cur != null && now < cur.expiresAt - skewMs) {
            if (now >= ReconnectPolicy.renewAt(issuedAt, cur.expiresAt)) return hello(tv, now, cur)
            return keepAlive(tv, cur, now) ?: hello(tv, now, session ?: cur)
        }
        if (cur != null) { session = null; store.clearCredential(tv.address) }   // the token itself ran out
        return hello(tv, now, null)
    }

    /** Null = the link needs a HELLO (the Bluetooth route, or every route failed). */
    private fun keepAlive(tv: SavedTv, cur: LinkSession, now: Long): List<Outcome>? {
        val info = cur.info.link
        when (val r = cur.route) {
            is LinkPlanner.Route.Lan, is LinkPlanner.Route.Direct -> {
                val base = cur.base!!
                var c = env.check(base, cur.credential)
                if (c == TokenCheck.UNREACHABLE) c = env.check(base, cur.credential)      // never rely on one timeout
                if (c == TokenCheck.OK) return listOf(Outcome.Alive)
                if (c == TokenCheck.TOKEN_REJECTED) {
                    // the TV is there and does not accept this token any more (reinstalled, phone removed, revoked): a HELLO tells which
                    dropToken(tv, cur.credential)
                    return listOf<Outcome>(Outcome.TokenRejected) + hello(tv, now, null)
                }
                // the Wi-Fi route is dead: the next best route with the same token, then check the TV is still there
                val next = LinkPlanner.plan(info, env::probe, canJoinWifiDirect()).first()
                if (next !is LinkPlanner.Route.Bluetooth) { session = cur.copy(route = next); return listOf(connected(session!!)) }
                session = cur.copy(route = next)
                return null
            }
            else -> {
                // Bluetooth only: look for the faster route again (the Wi-Fi may be back), and check the Bluetooth link from time to time
                if (info.ips.isNotEmpty() || info.wdSsid != null) {
                    val next = LinkPlanner.plan(info, env::probe, canJoinWifiDirect()).first()
                    if (next !is LinkPlanner.Route.Bluetooth) { session = cur.copy(route = next); return listOf(connected(session!!)) }
                }
                return if (forceCheck || now - lastHelloAt >= btKeepAliveMs) null else listOf(Outcome.Alive)
            }
        }
    }

    private fun hello(tv: SavedTv, now: Long, held: LinkSession?): List<Outcome> {
        if (limiter.tryAcquire(tv.address) > 0) return if (held != null) listOf(Outcome.Alive) else emptyList()   // storm control: skip this attempt
        val had = held ?: session?.takeIf { it.tv.address == tv.address }
        val r = link.connect(tv)
        val after = env.now()
        when (r) {
            is PhoneLink.Result.Connected -> {
                val s = r.session
                session = s; issuedAt = after; lastHelloAt = after
                saved.upsert(s.tv)
                store.saveCredential(s.tv.address, StoredCredential(s.credential, after, s.expiresAt))
                return listOf(connected(s))
            }
            is PhoneLink.Result.BluetoothProblem -> return listOf(Outcome.Bluetooth(r.reason))
            is PhoneLink.Result.Refused -> {
                // busy / not open / timeout on a plain HELLO: the TV is throttling or restarting, not refusing: transient
                if (r.code == BtProtocol.ERR_BUSY || r.code == BtProtocol.ERR_TIMEOUT || r.code == BtProtocol.ERR_NOT_OPEN) return lostOrAbsent(had, AbsentKind.NO_ANSWER, tv, after)
                session = null; store.clearCredential(tv.address)
                return listOf(Outcome.Refused(r.code, r.hint))
            }
            is PhoneLink.Result.TvAbsent -> return lostOrAbsent(had, r.kind, tv, after)
        }
    }

    /** The TV did not answer. A phone holding a valid token keeps it and says "lost, reconnecting"; the side that went away is named. */
    private fun lostOrAbsent(had: LinkSession?, kind: AbsentKind, tv: SavedTv, now: Long): List<Outcome> {
        if (had == null) return listOf(Outcome.Absent(kind))
        if (now >= had.expiresAt - skewMs) { session = null; store.clearCredential(tv.address); return listOf(Outcome.Absent(kind)) }
        session = had
        val base = had.base
        if (base != null && env.probe(base)) return listOf(Outcome.Alive)                   // Bluetooth hiccup but the Wi-Fi API answers: all is well
        return listOf(if (kind == AbsentKind.CLOSED_AT_ONCE) Outcome.Absent(kind) else Outcome.Lost(if (!env.networkUp() && (had.info.link.ips.isNotEmpty() || had.info.link.wdSsid != null)) LossSide.PHONE_NETWORK else LossSide.TV))
    }

    private fun connected(s: LinkSession) = Outcome.Connected(RouteKind.of(s.route), s.tv.name, wifiExpected = s.info.link.ips.isNotEmpty() || s.info.link.wdSsid != null)

    /** The saved TV whose name matches a paired CastBridge TV at another Bluetooth address (the TV's Bluetooth identity changed): followed. */
    private fun follow(tv: SavedTv): SavedTv? {
        val ch = saved.addressChanges(env.bondedTvs()).firstOrNull { it.old.address == tv.address } ?: return null
        saved.moveAddress(ch.old.address, ch.newAddress); store.clearCredential(ch.old.address); session = null
        return saved.get(ch.newAddress)
    }

    // ---------------------------------------------------------------------------------------------------- result

    private fun restore(tv: SavedTv?, now: Long): LinkMachine.Model =
        LinkMachine.Model.decode(store.loadModel(), now) ?: machine.initial(tv != null, tv?.name ?: "TV")

    private fun done(before: LinkMachine.Model, after: LinkMachine.Model, now: Long, trigger: Trigger): Step = finish(after, now, machine.nextAttempt(after, env.foreground(), random).let { (it as? Retry.After)?.ms })

    private fun finish(m: LinkMachine.Model, now: Long, delay: Long?): Step {
        model = m
        runCatching { store.saveModel(m.encode()) }
        var d = delay
        val s = session
        if (s != null && d != null) {
            // renew at mid-life; while the renewal keeps failing, come back at a third of what is left so a still-valid token is retried well before it dies
            val due = ReconnectPolicy.renewAt(issuedAt, s.expiresAt)
            val left = s.expiresAt - skewMs - now
            d = if (now >= due) minOf(d, (left / 3).coerceIn(5_000L, 5 * 60_000L)) else minOf(d, (due - now).coerceAtLeast(1_000))
        }
        return Step(machine.view(m), d, s).also { last = it }
    }
}
