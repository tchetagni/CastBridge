package castbridge.core.tv.activation

import castbridge.core.owner.GateState
import castbridge.core.tv.Pin
import castbridge.core.tv.PinGuard
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audit of the Wi-Fi activation of a LOCKED TV (commit 24c766e1, docs/TV-ACTIVATION-CLE-USB.md § Limites): M1 (global cap of wrong codes, terms before the code,
 * bounded address tables, code regenerated after the activation), L1 (non-local sockets closed before any header, 2 connections per address), L3 (feature gate).
 */
class LockedActivationHardeningTest {
    private val pin = "482915"
    private var clock = 1_000_000L
    private val installed = ArrayList<String>()
    private var terms = true
    private fun api(guard: PinGuard = PinGuard(pin, now = { clock })) = LockedActivationApi(guard, { key ->
        installed += key
        if (key == "GOOD") LockedActivationApi.Install.Accepted("Licence 1") else LockedActivationApi.Install.Rejected("Cette clé n'est pas celle de cette TV")
    }, { terms }, "1.0-test", now = { clock })

    private fun req(ip: String = "192.168.1.30", pin: String? = this.pin) =
        LockedActivationApi.Request("POST", LockedActivationApi.PATH, ip, "192.168.1.20:8765", pin, null, 4)
    private val good: (Int) -> ByteArray? = { n -> "GOOD".toByteArray().copyOf(n) }

    // ---- M1 (a): global cap of wrong codes, every address together ----

    @Test fun `twenty wrong codes from many addresses close the route for everyone for ten minutes`() {
        val a = api()
        repeat(LockedActivationApi.GLOBAL_MAX_WRONG - 1) { i -> assertEquals(401, a.handle(req(ip = "192.168.1.${100 + i}", pin = "000000"), good).status) }
        assertEquals(200, a.handle(req(ip = "192.168.1.50"), good).status, "19 wrong codes: still open")
        assertEquals(401, a.handle(req(ip = "192.168.1.200", pin = "000000"), good).status)       // the 20th
        installed.clear()
        val r = a.handle(req(ip = "192.168.1.51"), good)
        assertEquals(429, r.status, "the right code from a fresh address is refused too"); assertTrue("Bluetooth" in r.json, r.json)
        assertTrue(installed.isEmpty())
        clock += LockedActivationApi.WINDOW_MS + 1
        assertEquals(200, a.handle(req(ip = "192.168.1.51"), good).status, "open again after the window")
    }

    @Test fun `the global cap stops comparing codes, so a right guess is not revealed`() {
        val a = api()
        repeat(LockedActivationApi.GLOBAL_MAX_WRONG) { i -> a.handle(req(ip = "10.0.${i / 200}.${i % 200 + 1}", pin = "000000"), good) }
        assertEquals(429, a.handle(req(ip = "10.9.9.9", pin = "111111"), good).status)
        assertEquals(429, a.handle(req(ip = "10.9.9.8"), good).status)
    }

    // ---- M1 (b): the terms of use before the code ----

    @Test fun `terms not accepted answer 409 whatever the code, without comparing it`() {
        terms = false
        var compared = 0
        val counting = object : PinGuard(pin, now = { clock }) {
            override fun check(ip: String, given: String?): Result { compared++; return super.check(ip, given) }
        }
        val a = api(counting)
        assertEquals(409, a.handle(req(pin = "000000"), good).status, "a wrong code is not told")
        assertEquals(409, a.handle(req(), good).status, "a right code is not told")
        assertEquals(409, a.handle(req(pin = null), good).status)
        assertEquals(0, compared, "the code is never compared while the terms are not accepted")
        assertTrue(installed.isEmpty())
    }

    @Test fun `terms not accepted never lock an address out`() {
        terms = false
        val a = api()
        repeat(10) { assertEquals(409, a.handle(req(pin = "000000"), good).status) }
        terms = true
        assertEquals(200, a.handle(req(), good).status, "the refused-terms requests did not count as wrong codes")
    }

    // ---- M1 (c): bounded address tables ----

    @Test fun `the PinGuard keeps at most a thousand addresses, the oldest go first`() {
        val g = PinGuard(pin, now = { clock })
        repeat(PinGuard.MAX_ENTRIES + 500) { i -> g.check("fd00::${Integer.toHexString(i)}", "000000") }
        assertEquals(PinGuard.MAX_ENTRIES, g.trackedAddresses())
        val small = PinGuard(pin, now = { clock }, maxEntries = 3)
        repeat(4) { small.check("10.0.0.1", "000000") }                    // 4 failures for the first address
        small.check("10.0.0.2", "000000"); small.check("10.0.0.3", "000000")
        small.check("10.0.0.1", "000000")                                   // 5th failure: locked; and the most recently used
        small.check("10.0.0.4", "000000")                                   // evicts the least recently used (10.0.0.2), not the locked one
        assertEquals(3, small.trackedAddresses())
        assertEquals(PinGuard.Result.LOCKED, small.check("10.0.0.1", pin))
    }

    @Test fun `the per-address limiter keeps at most a thousand addresses`() {
        val a = api(PinGuard(pin, now = { clock }))
        repeat(LockedActivationApi.MAX_ADDRESSES + 50) { i -> a.handle(req(ip = "fd00::${Integer.toHexString(i)}"), good) }
        assertEquals(LockedActivationApi.MAX_ADDRESSES, a.trackedAddresses())
    }

    // ---- M1 (d): the code shown on the locked screen is replaced after the activation ----

    private class FakeStore(var pin: String = "482915", var exposed: Boolean = false, var failWrite: Boolean = false) : LockedPinRotation.Store {
        var writes = 0
        override fun exposed() = exposed
        override fun markExposed() { exposed = true }
        override fun replace(newPin: String): Boolean { writes++; if (failWrite) return false; pin = newPin; exposed = false; return true }
    }

    @Test fun `the code exposed by the locked route is regenerated once at the activation`() {
        val s = FakeStore()
        LockedPinRotation.onLockedRouteOpened(s)
        assertTrue(s.exposed)
        val p = LockedPinRotation.onActivated(s) { "731406" }
        assertEquals("731406", p); assertEquals("731406", s.pin); assertFalse(s.exposed)
        assertNull(LockedPinRotation.onActivated(s) { "111111" }, "only once")
        assertEquals("731406", s.pin)
    }

    @Test fun `a TV whose locked route never opened keeps its code`() {
        val s = FakeStore()
        assertNull(LockedPinRotation.onActivated(s) { "731406" })
        assertEquals(0, s.writes); assertEquals("482915", s.pin)
    }

    @Test fun `a failed write or an invalid code keeps the old code and the mark`() {
        val s = FakeStore(exposed = true, failWrite = true)
        assertNull(LockedPinRotation.onActivated(s) { "731406" }); assertTrue(s.exposed); assertEquals("482915", s.pin)
        val t = FakeStore(exposed = true)
        assertNull(LockedPinRotation.onActivated(t) { "12ab" }); assertEquals(0, t.writes); assertTrue(t.exposed)
        assertTrue(Pin.isValidFormat(LockedPinRotation.onActivated(FakeStore(exposed = true))!!), "the default generator gives a valid code")
    }

    // ---- L1: non-local sockets closed before reading anything, 2 connections per address ----

    @Test fun `the connection gate admits local addresses only, two at a time each`() {
        val g = ConnectionGate()
        for (ip in listOf(null, "8.8.8.8", "100.64.1.2", "example.com", "")) assertFalse(g.acquire(ip), ip.toString())
        assertTrue(g.acquire("192.168.1.30")); assertTrue(g.acquire("192.168.1.30"))
        assertFalse(g.acquire("192.168.1.30"), "a third connection from the same address")
        assertTrue(g.acquire("192.168.1.31"), "another address is not blocked")
        g.release("192.168.1.30")
        assertTrue(g.acquire("192.168.1.30"))
        g.release("192.168.1.30"); g.release("192.168.1.30"); g.release("192.168.1.31")
        assertEquals(0, g.tracked(), "released addresses are forgotten (the table stays small)")
        g.release("192.168.1.30"); assertEquals(0, g.tracked())
    }

    @Test fun `the real server closes a third idle connection from the same address`() {
        val s = LockedActivationServer(api(), 0)
        s.start(5_000, true)
        val held = ArrayList<Socket>()
        try {
            val port = s.listeningPort
            repeat(2) { held += Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 2_000) } }
            Thread.sleep(200)
            val third = Socket().apply { connect(InetSocketAddress("127.0.0.1", port), 2_000); soTimeout = 3_000 }
            val r = try { third.getInputStream().read() } catch (e: SocketTimeoutException) { -2 } catch (e: java.io.IOException) { -1 }
            assertEquals(-1, r, "closed by the server before any byte was read")
            third.close()
            held.forEach { it.close() }
            Thread.sleep(300)
            // once released, the address is served again
            val c = java.net.URL("http://127.0.0.1:$port/api/hello").openConnection() as java.net.HttpURLConnection
            assertEquals(200, c.responseCode)
        } finally { held.forEach { runCatching { it.close() } }; s.stop() }
    }

    // ---- L3: the route opens only when the feature gate allows it on a locked TV ----

    @Test fun `the locked route opens only on a locked TV`() {
        assertTrue(LockedActivationApi.mayOpen(GateState.Locked))
        assertFalse(LockedActivationApi.mayOpen(GateState.NotRequired))
        assertFalse(LockedActivationApi.mayOpen(GateState.Grace(0)))
    }
}
