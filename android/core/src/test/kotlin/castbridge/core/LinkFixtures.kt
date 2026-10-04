package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.*
import java.io.*
import kotlin.concurrent.thread

/** A clock the tests move by hand: nothing here ever sleeps. */
open class FakeClock(var t: Long = 1_000_000L) { open fun now() = t; open fun advance(ms: Long) { t += ms } }

/**
 * A TV (real registry, pairing, HELLO and CBTH protocol over in-memory pipes) with every way of misbehaving the field showed:
 * off (page timeout), app not running (no SDP record), stale bond (refused at once), reinstalled (registry gone), restarted (registry reloaded),
 * and links that drop at a chosen byte. Time only moves when the test, or a simulated connect, says so.
 */
class FakeTv(val clock: FakeClock, val phone: String = "AA:BB:CC:DD:EE:01", val tvAddress: String = "11:22:33:44:55:66", val ttlMs: Long = 12 * 3600_000L) : BtTransport {
    var persistence = MemoryTrustPersistence()
    var reg = TrustRegistry(persistence, clock::now, tokenTtlMs = ttlMs)
    var pairing = PairingSession(reg, clock::now)
    val bonded = mutableSetOf(phone)
    var lan = listOf("192.168.1.20")
    var name = "TV du salon"
    var power = true
    var appRunning = true
    var staleBond = false
    var btRadioOn = true
    var wifiUp = true
    var connects = 0
    var hellos = 0
    /** Drop the link once this many bytes were read from / written to it (null = never). */
    var dropReadAfter: Int? = null
    var dropWriteAfter: Int? = null
    val limiter get() = limiterRef
    private var limiterRef: AttemptLimiter? = null
    /** The 8-phone cap flow of this TV (null = a TV that predates it, or a test that does not use it). */
    var capacity: PairCapacityFlow? = null
    var handler = newHandler()
    private fun newHandler() = HelloHandler(reg, pairing, { it in bonded }, { name }, "0.13", { "CastBridge TV Test" }, { LinkInfo(8765, lan) }, {}, limiterRef, capacity = capacity)

    fun useLimiter(l: AttemptLimiter) { limiterRef = l; handler = newHandler() }
    /** Turns the 8-phone flow on (the registry's own cap is always on). */
    fun useCapacity(timeoutMs: Long = 120_000): PairCapacityFlow = PairCapacityFlow(reg, clock::now, timeoutMs, onDenied = { pairing.recordDenial(it) }).also { capacity = it; handler = newHandler() }
    /** The app is killed and started again: the registry file survives. */
    fun restartApp() { reg = TrustRegistry(persistence, clock::now, tokenTtlMs = ttlMs); pairing = PairingSession(reg, clock::now); capacity = null; handler = newHandler() }
    /** Uninstalled and installed again: no registry, a new install id. */
    fun reinstall() { persistence = MemoryTrustPersistence(); restartApp() }

    override fun connect(address: String): Link {
        connects++
        if (!btRadioOn) { clock.advance(10_000); throw IOException("read failed, socket might closed or timeout, read ret: -1") }
        if (!power) { clock.advance(10_000); throw IOException("read failed, socket might closed or timeout, read ret: -1") }
        if (!appRunning) { clock.advance(300); throw IOException("Service discovery failed") }
        if (staleBond) { clock.advance(400); throw IOException("read failed, socket might closed or timeout, read ret: -1") }
        clock.advance(400)
        return flaky(serve(phone))
    }

    fun serve(peer: String): Link {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val dir = kotlin.io.path.createTempDirectory("faketv").toFile().also { it.deleteOnExit() }
        val h = handler; val r = reg; val b = bonded
        thread(isDaemon = true) {
            try { BtProtocol.serve(dir, tvIn, s2c, PinGuard("482913"), peer, 0, hello = { p, req -> hellos++; h.handle(p, "Galaxy de test", req) }, trusted = { r.isTrusted(it) && it in b }) }
            catch (_: Exception) {} finally { runCatching { s2c.close() } }
        }
        return object : Link { override val input = clIn; override val output = c2s; override fun close() { runCatching { c2s.close() }; runCatching { clIn.close() } } }
    }

    private fun flaky(l: Link): Link {
        val dr = dropReadAfter; val dw = dropWriteAfter
        if (dr == null && dw == null) return l
        return object : Link {
            var rd = 0; var wr = 0
            override val input = object : InputStream() {
                override fun read(): Int { if (dr != null && rd >= dr) { l.close(); throw IOException("read failed, socket might closed or timeout, read ret: -1") }; rd++; return l.input.read() }
            }
            override val output = object : OutputStream() {
                override fun write(b: Int) { if (dw != null && wr >= dw) { l.close(); throw IOException("write failed: broken pipe") }; wr++; l.output.write(b) }
                override fun flush() = l.output.flush()
            }
            override fun close() = l.close()
        }
    }

    /** Wi-Fi API probe: true while the TV runs and the Wi-Fi is up (the fake Wi-Fi has one address). */
    fun apiAnswers() = power && appRunning && wifiUp
}

/** The phone: Bluetooth, bond, network, foreground, all switchable. */
class FakeEnv(val clock: FakeClock, val tv: FakeTv) : LinkEnv {
    var bt: BtUnavailable.Reason? = null
    val bonds = HashMap<String, BondState>()
    var network = true
    var fg = true
    var probes = 0
    var candidates: List<TvCandidate> = emptyList()
    override fun now() = clock.now()
    override fun btProblem() = bt
    override fun bond(address: String) = bonds[TrustRegistry.norm(address)] ?: BondState.BONDED
    override fun networkUp() = network
    override fun probe(base: String): Boolean { probes++; return network && tv.apiAnswers() && base.contains(tv.lan.first()) }
    override fun check(base: String, token: String) =
        if (!probe(base)) TokenCheck.UNREACHABLE else if (tv.reg.verifyToken(token) == tv.phone) TokenCheck.OK else TokenCheck.TOKEN_REJECTED
    override fun foreground() = fg
    override fun bondedTvs() = candidates
}

/** A phone ready to talk to [tv]: saved TV, driver, loop helper. */
class Phone(val clock: FakeClock, val tv: FakeTv, val machine: LinkMachine = LinkMachine(), seed: Long = 7) {
    val env = FakeEnv(clock, tv)
    val saved = SavedTvs(MemoryTrustPersistence())
    val store = MemoryLinkStore()
    val rnd = java.util.Random(seed)
    val link = PhoneLink(tv, { env.probe(it) }, now = clock::now)
    val driver = LinkDriver(link, env, saved, store, machine, { rnd.nextDouble() })
    val shownHistory = ArrayList<String>()

    init { saved.upsert(SavedTv(tv.tvAddress, "TV du salon", addedAt = 1), makeDefault = true) }

    fun step(t: Trigger = Trigger.TIMER): LinkDriver.Step = driver.step(t).also { s -> val k = s.view.state.key; if (shownHistory.lastOrNull() != k) shownHistory += k }

    /** What the Android loop does: step, then sleep for what the driver asked (a null = waits for an event: here a 60 s timer tick). */
    fun run(ms: Long, trigger: Trigger = Trigger.TIMER): LinkDriver.Step {
        val end = clock.now() + ms
        var s = step(trigger)
        while (clock.now() < end) { clock.advance(minOf(s.nextInMs ?: 60_000L, end - clock.now()).coerceAtLeast(1)); if (clock.now() < end) s = step(trigger) }
        return s
    }

    /** Pairs the phone for real: the owner opened the window and approves. */
    fun trustOnTv() { tv.reg.trust(tv.phone, "Galaxy de test") }
}
