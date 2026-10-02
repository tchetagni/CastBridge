package castbridge.core.lots

import castbridge.core.lots.InstallKeyPolicy.KeyState
import castbridge.core.lots.InstallKeyPolicy.UnwrapFailure
import castbridge.core.lots.InstallKeyPolicy.Verdict
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import kotlin.test.*

class InstallKeyPolicyTest {
    @Test fun classificationTableIncludingTheMissingAliasDoubleConfirmation() {
        val table = listOf(
            Triple(UnwrapFailure.BAD_TAG, false, Verdict.LOST), Triple(UnwrapFailure.BAD_TAG, true, Verdict.LOST),
            Triple(UnwrapFailure.INVALIDATED, false, Verdict.LOST), Triple(UnwrapFailure.INVALIDATED, true, Verdict.LOST),
            Triple(UnwrapFailure.MISSING_ALIAS, false, Verdict.TRANSIENT), Triple(UnwrapFailure.MISSING_ALIAS, true, Verdict.LOST),     // audit (1): one `false` is not a loss
            Triple(UnwrapFailure.OTHER, false, Verdict.TRANSIENT), Triple(UnwrapFailure.OTHER, true, Verdict.TRANSIENT),
        )
        assertEquals(UnwrapFailure.values().toSet(), table.map { it.first }.toSet())
        for ((f, confirmed, verdict) in table) assertEquals(verdict, InstallKeyPolicy.classify(f, confirmed), "$f/$confirmed")
    }

    @Test fun aMissingAliasIsConfirmedOnlyWhenAFreshlyReloadedKeystoreAgreesTwice() {
        val bools = listOf(true, false, null)
        for (a in bools) for (b in bools) for (c in bools)
            assertEquals(a == false && b == false && c == false, InstallKeyPolicy.aliasConfirmedAbsent(a, b, c), "$a/$b/$c")
        assertFalse(InstallKeyPolicy.aliasConfirmedAbsent(false, false, true), "the reloaded store returns a key: the alias exists")
    }

    @Test fun protectionLabelsOfTheFourStates() {
        assertEquals(listOf("pending", "keystore", "unavailable", "unreadable"), KeyState.values().map { it.protection })
    }

    @Test fun backoffScheduleWithAnInjectedClock() {
        var t = 1_000L; val g = InstallKeyGate { t }
        assertTrue(g.mayAttempt()); assertEquals(0, g.failures)
        assertTrue(g.failed(), "first failure of the process: logged"); assertFalse(g.mayAttempt())
        t += InstallKeyPolicy.SHORT_DELAY_MS - 1; assertFalse(g.mayAttempt(), "59.999 s: still waiting")
        t += 1; assertTrue(g.mayAttempt(), "1 min: another attempt")
        assertFalse(g.failed(), "no second log line"); t += 60_000; assertTrue(g.mayAttempt())
        assertFalse(g.failed()); t += 60_000; assertTrue(g.mayAttempt())                                   // 3 failures, 1 min each
        assertFalse(g.failed()); t += InstallKeyPolicy.SHORT_DELAY_MS; assertFalse(g.mayAttempt(), "from the 4th failure: 1 h")
        t += InstallKeyPolicy.LONG_DELAY_MS - InstallKeyPolicy.SHORT_DELAY_MS - 1; assertFalse(g.mayAttempt())
        t += 1; assertTrue(g.mayAttempt())
        t -= 10_000_000; g.failed(); t -= 1; assertTrue(g.mayAttempt(), "a clock that went back never blocks forever")
        g.succeeded(); assertEquals(0, g.failures); assertTrue(g.mayAttempt())
        assertEquals(listOf(0L, 60_000L, 60_000L, 60_000L, 3_600_000L, 3_600_000L), (0..5).map { InstallKeyPolicy.delayAfter(it) })
    }

    @Test fun lazyKeyedApiBuildsNothingAtSetupAndAnswers503WhenBuildFails() {
        var builds = 0; var fail = true
        val inner = object : ApiExtension {
            override fun handle(path: String, method: String, params: Map<String, String>) = if (method == "GET") ApiReply(200, "{}") else null
            override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray) = ApiReply(201, "{}")
        }
        val api = LazyKeyedApi(setOf("/api/rental", "/api/rental/install"), setOf("/api/rental/install")) {
            builds++; if (fail) throw InstallKeyUnavailableException("coffre") else inner
        }
        assertEquals(0, builds, "nothing built when the server is set up")
        assertTrue(api.wantsBody("/api/rental/install")); assertFalse(api.wantsBody("/api/rental")); assertEquals(0, builds)
        assertNull(api.handle("/api/other", "GET", emptyMap())); assertEquals(0, builds, "other routes never build")
        val r = api.handle("/api/rental", "GET", emptyMap())!!
        assertEquals(503, r.status); assertTrue("coffre de clés indisponible" in r.json)
        assertEquals(503, api.handleBody("/api/rental/install", "POST", emptyMap(), ByteArray(0))!!.status)
        fail = false
        assertEquals(200, api.handle("/api/rental", "GET", emptyMap())!!.status)
        assertNull(api.handle("/api/rental", "DELETE", emptyMap()), "a route the inner api refuses stays refused")
        assertEquals(201, api.handleBody("/api/rental/install", "POST", emptyMap(), ByteArray(0))!!.status)
    }
}
