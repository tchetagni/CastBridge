package castbridge.core.tunnel

import java.util.Random
import kotlin.test.*

class TunnelMachineTest {
    private class Env : TunnelEnv {
        var t = 1_800_000_000_000L
        var terms = true; var lockedNow = false; var act: String? = "cbx1.AAA.BBB"; var path = TunnelPath.DIRECT; var key = "key1"
        var keyCalls = 0
        override fun now() = t
        override fun termsAccepted() = terms
        override fun locked() = lockedNow
        override fun activation() = act
        override fun connectivity() = path
        override fun keyId(): String { keyCalls++; return key }
    }

    private class Session(val onClosed: () -> Unit) : TunnelSession {
        var up = true; var closed = 0
        override fun alive() = up
        override fun close() { up = false; closed++ }
    }

    private class Transport : TunnelTransport {
        var enrollAnswer: EnrollOutcome? = null
        var openError: TunnelException? = null
        val enrolls = ArrayList<Pair<String, TunnelPath>>(); val opens = ArrayList<TunnelPath>(); var experts = 0
        var expertsResult = ExpertsSync.Result(true, "ok", 1)
        var session: Session? = null
        override fun enroll(activation: String, path: TunnelPath): EnrollOutcome {
            enrolls += activation to path
            return enrollAnswer ?: EnrollOutcome.Ok(Enrollment("bridge.sti-cm.com", 2200, "cbtunnel", 22100, null, "", ""))
        }
        override fun open(e: Enrollment, path: TunnelPath, onClosed: () -> Unit): TunnelSession {
            opens += path; openError?.let { throw it }
            return Session(onClosed).also { session = it }
        }
        override fun refreshExperts(path: TunnelPath): ExpertsSync.Result { experts++; return expertsResult }
    }

    private val env = Env(); private val tr = Transport(); private val log = ArrayList<String>()
    private fun machine() = TunnelMachine(env, tr, null, TunnelBackoff(Random(1)), { log += it })

    @Test fun nothingStartsWithoutTerms() {
        val m = machine(); env.terms = false
        m.step()
        assertEquals(TunnelState.NEEDS_TERMS, m.state)
        assertTrue(tr.enrolls.isEmpty() && tr.opens.isEmpty() && env.keyCalls == 0, "no key generated, no request before acceptance")
        assertEquals(TunnelText.NEEDS_TERMS, TunnelText.line(m.state))
    }

    @Test fun lockedTvOrNoActivationGetsNoTunnel() {
        val m = machine(); env.lockedNow = true; m.step()
        assertEquals(TunnelState.IDLE, m.state); assertTrue(tr.enrolls.isEmpty())
        env.lockedNow = false; env.act = null; m.step()
        assertEquals(TunnelState.IDLE, m.state); assertTrue(tr.enrolls.isEmpty())
    }

    @Test fun offlineMakesNoAttempt() {
        val m = machine(); env.path = TunnelPath.OFFLINE
        repeat(5) { m.step(); env.t += 30_000 }
        assertEquals(TunnelState.IDLE, m.state); assertTrue(tr.enrolls.isEmpty() && tr.opens.isEmpty())
        assertEquals(TunnelText.OFFLINE, TunnelText.line(m.state))
    }

    @Test fun enrollThenConnectThenUp() {
        val m = machine()
        assertEquals(0L, m.step())
        assertEquals(TunnelState.UP, m.state)
        assertEquals(listOf("cbx1.AAA.BBB" to TunnelPath.DIRECT), tr.enrolls); assertEquals(listOf(TunnelPath.DIRECT), tr.opens)
        assertEquals(22100, m.enrollment!!.port); assertEquals(TunnelEnroll.activationId("cbx1.AAA.BBB"), m.enrollment!!.activationId)
        assertEquals(TunnelText.CONNECTED, TunnelText.line(m.state))
        assertTrue(log.any { it.startsWith("connectée") })
        m.step()                                        // UP: experts at tunnel up, no new enrollment
        assertEquals(1, tr.experts); assertEquals(1, tr.enrolls.size)
    }

    @Test fun viaPhoneGatewayUsesThatPathForEnrollAndSsh() {
        val m = machine(); env.path = TunnelPath.GATEWAY
        m.step()
        assertEquals(TunnelPath.GATEWAY, tr.enrolls.single().second); assertEquals(listOf(TunnelPath.GATEWAY), tr.opens)
        assertTrue(log.any { "par le téléphone" in it })
    }

    @Test fun expertsEveryFifteenMinutes() {
        val m = machine(); m.step(); m.step(); assertEquals(1, tr.experts)
        env.t += 14 * 60_000; m.step(); assertEquals(1, tr.experts)
        env.t += 61_000; m.step(); assertEquals(2, tr.experts)
        tr.expertsResult = ExpertsSync.Result(false, "refusée")
        env.t += 15 * 60_000; m.step(); assertEquals(3, tr.experts); assertTrue(log.contains("refusée"))
        env.t += 4 * 60_000; m.step(); assertEquals(3, tr.experts)
        env.t += 61_000; m.step(); assertEquals(4, tr.experts)         // a failed fetch is retried after 5 min
    }

    @Test fun lostSessionBacksOffThenReconnects() {
        val m = machine(); m.step(); m.step()
        tr.session!!.up = false
        val d = m.step()
        assertEquals(TunnelState.BACKOFF, m.state); assertTrue(d in 2_500..7_000, "first delay near 5 s: $d")
        assertEquals(1, tr.session!!.closed)
        assertEquals(d, m.step().also { }, "woken early: waits the rest")
        env.t += d; assertEquals(0L, m.step()); assertEquals(TunnelState.UP, m.state)
        assertEquals(1, tr.enrolls.size, "enrollment kept")
    }

    @Test fun backoffGrowsAndNeverExceedsTenMinutes() {
        val m = machine(); tr.openError = TunnelException("réseau coupé")
        var last = 0L; val seen = ArrayList<Long>()
        repeat(14) { val d = m.step(); seen += d; env.t += d; last = d }
        assertTrue(seen.all { it <= 600_000 }); assertTrue(seen.first() < 8_000); assertTrue(seen.takeLast(4).all { it >= 300_000 }, "$seen")
        assertEquals(TunnelState.BACKOFF, m.state); assertEquals("réseau coupé", m.lastError)
    }

    @Test fun stableSessionResetsBackoff() {
        val m = machine(); m.step()
        tr.session!!.up = false; var d = m.step(); env.t += d
        tr.openError = TunnelException("x"); repeat(4) { d = m.step(); env.t += d }          // ramp up the failures
        assertTrue(d > 30_000)
        tr.openError = null; m.step(); assertEquals(TunnelState.UP, m.state)
        env.t += 5 * 60_000; tr.session!!.up = false
        d = m.step(); assertTrue(d < 8_000, "a long session restarts at 5 s: $d")
    }

    @Test fun revokedStopsAndWaits24Hours() {
        val m = machine(); tr.enrollAnswer = EnrollOutcome.Revoked("révoqué")
        m.step(); assertEquals(TunnelState.REVOKED, m.state); assertEquals(1, tr.enrolls.size)
        env.t += 23 * 3_600_000L; m.step(); assertEquals(1, tr.enrolls.size, "no retry before 24 h")
        env.t += 2 * 3_600_000L; tr.enrollAnswer = null; m.step()
        assertEquals(2, tr.enrolls.size); assertEquals(TunnelState.UP, m.state)
    }

    @Test fun rateLimitedWaitsAtLeastTenMinutes() {
        val m = machine(); tr.enrollAnswer = EnrollOutcome.Retry("trop", TunnelEnroll.RATE_LIMITED_MIN_MS)
        val d = m.step(); assertTrue(d >= 600_000); assertEquals(TunnelState.BACKOFF, m.state)
    }

    @Test fun newActivationOrKeyReEnrolls() {
        val m = machine(); m.step(); m.step()
        tr.session!!.up = false; var d = m.step(); env.t += d
        env.act = "cbx1.NEW.SIG"; m.step()
        assertEquals(2, tr.enrolls.size); assertEquals("cbx1.NEW.SIG", tr.enrolls.last().first)
        tr.session!!.up = false; d = m.step(); env.t += d; env.key = "key2"; m.step()
        assertEquals(3, tr.enrolls.size)
    }

    @Test fun authRefusalForgetsEnrollmentButHostKeyDoesNot() {
        val m = machine(); tr.openError = TunnelException("clé refusée", TunnelException.Kind.AUTH)
        var d = m.step(); env.t += d; assertNull(m.enrollment)
        tr.openError = null; m.step(); assertEquals(2, tr.enrolls.size, "enrolled again")
        tr.session!!.up = false; d = m.step(); env.t += d
        tr.openError = TunnelException("empreinte", TunnelException.Kind.HOSTKEY); d = m.step(); env.t += d
        assertNotNull(m.enrollment); assertEquals(2, tr.enrolls.size)
    }

    @Test fun termsWithdrawnOrTvLockedOrOfflineClosesTheTunnel() {
        val m = machine(); m.step(); val s = tr.session!!
        env.path = TunnelPath.OFFLINE; m.step(); assertEquals(1, s.closed); assertEquals(TunnelState.IDLE, m.state)
        assertTrue(log.any { it.contains("hors ligne") })
        env.path = TunnelPath.DIRECT; m.step(); val s2 = tr.session!!; assertNotSame(s, s2)
        env.terms = false; m.step(); assertEquals(1, s2.closed); assertEquals(TunnelState.NEEDS_TERMS, m.state)
    }

    @Test fun enrollmentIsPersistedAndPauseSurvivesRestart() {
        val dir = kotlin.io.path.createTempDirectory("tun").toFile()
        try {
            val store = EnrollmentStore(java.io.File(dir, "e.json"))
            val m1 = TunnelMachine(env, tr, store, TunnelBackoff(Random(1))); m1.step()
            val m2 = TunnelMachine(env, tr, store, TunnelBackoff(Random(1)))
            assertEquals(22100, m2.enrollment!!.port)
            tr.session!!.up = false; m1.step()
            val tr2 = Transport().apply { enrollAnswer = EnrollOutcome.Revoked("non") }
            val m3 = TunnelMachine(env, tr2, store, TunnelBackoff(Random(1))); env.act = "cbx1.OTHER.X"; m3.step()
            val m4 = TunnelMachine(env, Transport(), store, TunnelBackoff(Random(1))); m4.step()
            assertEquals(TunnelState.REVOKED, m4.state)
        } finally { dir.deleteRecursively() }
    }

    @Test fun shutdownClosesTheSession() {
        val m = machine(); m.step(); val s = tr.session!!; m.shutdown(); assertEquals(1, s.closed)
    }
}
