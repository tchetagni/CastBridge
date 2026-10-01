package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.TvClient
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.*

class ResilientCallTest {
    private val clock = FakeClock()
    private val TOKEN_A = "cbk_" + "a".repeat(64)
    private val TOKEN_B = "cbk_" + "b".repeat(64)

    private class Gate(val clock: FakeClock, var cred: String?) : LinkGate {
        var suspects = 0; val rejected = ArrayList<String?>(); var onAwait: (Long) -> Unit = {}
        override fun credential() = cred
        override fun suspect(rejectedToken: String?) { suspects++; if (rejectedToken != null) rejected += rejectedToken }
        override fun awaitLink(timeoutMs: Long): Boolean { clock.advance(timeoutMs); onAwait(timeoutMs); return true }
    }

    private fun call(g: Gate, policy: CallPolicy = CallPolicy(maxWaitMs = 120_000)) = ResilientCall(g, policy, clock::now, { 0.5 })

    @Test fun successFirstTimeCostsNothing() {
        val g = Gate(clock, TOKEN_A)
        val r = call(g).run { it }
        assertEquals(CallResult.Ok(TOKEN_A), r); assertEquals(0, g.suspects)
    }

    @Test fun transientFailuresAreRetriedWithWaitingStateThenSucceed() {
        val g = Gate(clock, TOKEN_A); var n = 0; val waits = ArrayList<CallWaiting>()
        val r = call(g).run(onWaiting = { waits += it }) { if (++n < 4) throw if (n % 2 == 0) SocketTimeoutException("Read timed out") else IOException("Connection reset") else "ok" }
        assertEquals(CallResult.Ok("ok"), r); assertEquals(4, n)
        assertEquals(3, waits.size); assertTrue(waits.all { it.message == "En attente de la TV…" })
        assertTrue(g.suspects >= 3, "the link layer is asked to check itself")
    }

    @Test fun wrongPinIsNeverRetried_theLockoutRule() {
        val g = Gate(clock, "123456"); var n = 0
        val r = call(g).run<String> { n++; throw TvClient.HttpError(401, """{"error":"bad pin"}""") }
        assertIs<CallResult.CredentialRefused>(r); assertEquals(1, n, "one request, not a loop")
        assertEquals("Code de la TV incorrect.", (r as CallResult.CredentialRefused).message)
        var m = 0
        assertIs<CallResult.CredentialRefused>(call(g).run<String> { m++; throw TvClient.HttpError(401, """{"error":"locked","retryAfter":60}""") })
        assertEquals(1, m)
        assertIs<CallResult.CredentialRefused>(call(g).run<String> { m++; throw TvCredential.Missing() }); assertEquals(2, m)
        assertIs<CallResult.CredentialRefused>(call(g).run<String> { m++; throw BtProtocol.Refused(BtProtocol.ERR_PIN) }); assertEquals(3, m)
    }

    @Test fun expiredTokenIsReportedThenWaitsForADifferentOneAndNeverResendsTheOldOne() {
        val g = Gate(clock, TOKEN_A); val used = ArrayList<String?>()
        g.onAwait = { if (g.rejected.isNotEmpty() && used.size >= 1) g.cred = TOKEN_B }     // the link layer renewed it
        val r = call(g).run { c -> used += c; if (c == TOKEN_A) throw TvClient.HttpError(401, """{"error":"bad token"}""") else "done" }
        assertEquals(CallResult.Ok("done"), r)
        assertEquals(listOf<String?>(TOKEN_A, TOKEN_B), used, "the refused token was sent once")
        assertEquals(listOf<String?>(TOKEN_A), g.rejected)
    }

    @Test fun tokenThatStaysRefusedStopsWithoutHammeringTheTv() {
        val g = Gate(clock, TOKEN_A); var sent = 0
        val r = call(g, CallPolicy(maxWaitMs = 30_000)).run<String> { sent++; throw TvClient.HttpError(401, """{"error":"bad token"}""") }
        assertIs<CallResult.CredentialRefused>(r); assertEquals(1, sent)
    }

    @Test fun permanentAnswersGiveUpAtOnceWithAFrenchSentence() {
        val g = Gate(clock, TOKEN_A)
        for ((code, text) in listOf(404 to "mettez CastBridge-TV à jour", 507 to "Plus de place", 403 to "code de la TV")) {
            var n = 0
            val r = call(g).run<String> { n++; throw TvClient.HttpError(code, "{}") }
            assertIs<CallResult.GaveUp>(r); assertEquals(1, n); assertTrue((r as CallResult.GaveUp).message.contains(text, ignoreCase = true), r.message)
        }
    }

    @Test fun longOutageGivesUpGracefullyWithAnActionableMessage() {
        val g = Gate(clock, TOKEN_A); var n = 0
        val r = call(g, CallPolicy(maxWaitMs = 90_000)).run<String> { n++; throw IOException("Connection refused") }
        assertIs<CallResult.GaveUp>(r)
        assertTrue(r.message.contains("allumée") && r.message.contains("CastBridge-TV"), r.message)
        assertTrue(n in 5..12, "attempts: $n")
        assertTrue(clock.now() - 1_000_000 <= 91_000)
    }

    @Test fun nonIdempotentCallIsNotRepeatedAfterALinkDrop() {
        val g = Gate(clock, TOKEN_A); var n = 0
        val r = call(g).run<String>(idempotent = false) { n++; throw IOException("broken pipe") }
        assertIs<CallResult.GaveUp>(r); assertTrue(r.maybeApplied); assertEquals(1, n)
    }

    @Test fun cancellationWins() {
        val g = Gate(clock, TOKEN_A)
        val r = ResilientCall(g, cancelled = { true }).run { "x" }
        assertEquals(CallResult.Cancelled, r)
    }

    @Test fun everyFailureFamilyIsClassified() {
        val table = mapOf<Throwable, ResilientCall.Kind>(
            TvClient.HttpError(401, "bad pin") to ResilientCall.Kind.CREDENTIAL, TvClient.HttpError(401, "locked") to ResilientCall.Kind.CREDENTIAL,
            TvClient.HttpError(401, """{"error":"bad token"}""") to ResilientCall.Kind.TOKEN,
            TvClient.HttpError(403, "") to ResilientCall.Kind.PERMANENT, TvClient.HttpError(404, "") to ResilientCall.Kind.PERMANENT, TvClient.HttpError(400, "") to ResilientCall.Kind.PERMANENT,
            TvClient.HttpError(503, "") to ResilientCall.Kind.TRANSIENT, TvClient.HttpError(500, "") to ResilientCall.Kind.TRANSIENT, TvClient.HttpError(429, "") to ResilientCall.Kind.TRANSIENT,
            TvClient.Conflict(5) to ResilientCall.Kind.TRANSIENT, IOException("x") to ResilientCall.Kind.TRANSIENT, SocketTimeoutException() to ResilientCall.Kind.TRANSIENT,
            BtUnavailable(BtUnavailable.Reason.OFF) to ResilientCall.Kind.TRANSIENT, TvCredential.Missing() to ResilientCall.Kind.CREDENTIAL,
            BtProtocol.Refused(BtProtocol.ERR_PIN) to ResilientCall.Kind.CREDENTIAL, BtProtocol.Refused(BtProtocol.ERR_UNTRUSTED) to ResilientCall.Kind.CREDENTIAL,
            BtProtocol.Refused(BtProtocol.ERR_BUSY) to ResilientCall.Kind.TRANSIENT, BtProtocol.Refused(BtProtocol.ERR_SPACE) to ResilientCall.Kind.PERMANENT,
            IllegalStateException("bug") to ResilientCall.Kind.PERMANENT)
        for ((e, k) in table) assertEquals(k, ResilientCall.classify(e), e.toString())
    }
}
