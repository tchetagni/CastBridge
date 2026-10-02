package castbridge.core.lots

import castbridge.core.crypto.PlainWrapper
import castbridge.core.lots.InstallKeyPolicy.RetryState
import castbridge.core.lots.InstallKeyPolicy.Step
import castbridge.core.lots.InstallKeyPolicy.UnwrapFailure
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import java.io.File
import kotlin.test.*

class InstallKeyPolicyTest {
    @Test fun aliasIsGeneratedOnlyWhenTheLookupSaysItIsAbsent() {
        assertEquals(Step.USE, InstallKeyPolicy.forLookup(true))
        assertEquals(Step.GENERATE, InstallKeyPolicy.forLookup(false))
        assertEquals(Step.FAIL, InstallKeyPolicy.forLookup(null))
    }
    @Test fun onlyAPermanentlyInvalidatedKeyIsRecreated() {
        assertEquals(Step.RECREATE, InstallKeyPolicy.forEncryptFailure(true))
        assertEquals(Step.FAIL, InstallKeyPolicy.forEncryptFailure(false))
    }

    @Test fun onlyABadTagAMissingAliasOrAnInvalidatedKeyIsALoss() {
        val table = mapOf(UnwrapFailure.BAD_TAG to true, UnwrapFailure.MISSING_ALIAS to true, UnwrapFailure.INVALIDATED to true, UnwrapFailure.OTHER to false)
        assertEquals(UnwrapFailure.values().toSet(), table.keys)
        table.forEach { (f, loss) -> assertEquals(loss, InstallKeyPolicy.unwrapIsLoss(f), "$f") }
    }

    @Test fun aStoredKeyIsReadWithItsOwnEnvelopeAndOnlyPlainMigrates() {
        // stored, chosen for a new key -> reader, migrate?
        val table = listOf(
            Triple("plain", "keystore", "plain" to true),          // audit w4-03 (2): a plain key is read as plain, then moved into the Keystore
            Triple("plain", "plain", "plain" to false),
            Triple("keystore", "keystore", "keystore" to false),
            Triple("keystore", "plain", "keystore" to false),      // the probe failed: still read through the Keystore, never through plain, never rewritten as plain
            Triple("memory", "memory", "memory" to false),
            Triple("plain", "memory", "plain" to true),
        )
        for ((stored, chosen, expected) in table) {
            assertEquals(expected.first, InstallKeyPolicy.readWith(stored), "$stored/$chosen")
            assertEquals(expected.second, InstallKeyPolicy.migrateTo(stored, chosen), "$stored/$chosen")
        }
    }

    @Test fun backoffIsOneMinuteThenOneHour() {
        val table = listOf(0 to 0L, 1 to 60_000L, 2 to 60_000L, 3 to 60_000L, 4 to 3_600_000L, 10 to 3_600_000L, 1000 to 3_600_000L)
        table.forEach { (n, d) -> assertEquals(d, InstallKeyPolicy.delayAfter(n), "after $n failures") }
        // now, lastFailureAt, failures -> may attempt
        val attempts = listOf(
            Triple(5L, null, 0) to true,
            Triple(59_999L, 0L, 1) to false, Triple(60_000L, 0L, 1) to true,
            Triple(3_599_999L, 0L, 4) to false, Triple(3_600_000L, 0L, 4) to true,
            Triple(100L, 1_000L, 4) to true,                        // a monotonic clock that went back (cannot happen, but never blocks forever)
        )
        attempts.forEach { (a, ok) -> assertEquals(ok, InstallKeyPolicy.mayAttempt(a.first, a.second, a.third), "$a") }
    }

    @Test fun regenerationNeedsManyFailuresOverSeveralStarts() {
        val table = listOf(RetryState(0, 0) to false, RetryState(7, 3) to false, RetryState(8, 2) to false, RetryState(100, 1) to false, RetryState(8, 3) to true, RetryState(20, 5) to true)
        table.forEach { (s, r) -> assertEquals(r, InstallKeyPolicy.mustRegenerate(s), "$s") }
        assertEquals(RetryState(1, 1), InstallKeyPolicy.afterFailure(RetryState(), true))
        assertEquals(RetryState(2, 1), InstallKeyPolicy.afterFailure(RetryState(1, 1), false))
    }

    @Test fun protectionStatusForTheActivationApi() {
        assertEquals("keystore", InstallKeyPolicy.protection("keystore", 3))
        assertEquals("plain", InstallKeyPolicy.protection("plain", 0))
        assertEquals("unavailable", InstallKeyPolicy.protection(null, 1))
        assertEquals("pending", InstallKeyPolicy.protection(null, 0))
    }

    @Test fun gateSpacesTheAttemptsWithAnInjectedClockAndCountsStartsInAFile() {
        val dir = Kit.tmp(); var t = 1_000L
        val g1 = InstallKeyGate(dir, { t })
        assertTrue(g1.mayAttempt())
        assertTrue(g1.failed(true), "first failure of the process: log it")
        assertFalse(g1.mayAttempt()); t += 59_999; assertFalse(g1.mayAttempt()); t += 1; assertTrue(g1.mayAttempt())
        assertFalse(g1.failed(true), "logged once")
        repeat(2) { t += 60_000; assertTrue(g1.mayAttempt()); g1.failed(true) }
        assertEquals(4, g1.failuresThisProcess)
        t += 60_000; assertFalse(g1.mayAttempt(), "after 4 failures: 1 h"); t += 3_540_000; assertTrue(g1.mayAttempt())
        assertEquals(RetryState(4, 1), g1.load())
        g1.failed(false)                                                 // a failure that is not the key's: backoff, but not counted toward regeneration
        assertEquals(RetryState(4, 1), g1.load())
        // two more process starts (new gates on the same folder), 2 failures each
        repeat(2) { val g = InstallKeyGate(dir, { t }); assertTrue(g.mayAttempt(), "a new process tries at once"); g.failed(true); t += 60_000; g.failed(true) }
        assertEquals(RetryState(8, 3), InstallKeyGate(dir, { t }).load())
        assertTrue(InstallKeyGate(dir, { t }).mustRegenerate())
        val g4 = InstallKeyGate(dir, { t }); g4.succeeded()
        assertEquals(RetryState(), g4.load()); assertFalse(g4.mustRegenerate()); assertFalse(File(dir, "install.key.retry").exists())
    }

    @Test fun storedWrapReadsTheLabelOfTheKeyFile() {
        val dir = Kit.tmp()
        assertNull(InstallKeyStore.storedWrap(dir))
        InstallKeyStore(dir, PlainWrapper()).loadOrCreate()
        assertEquals("plain", InstallKeyStore.storedWrap(dir))
        File(dir, "install.key").writeText("wrap=keystore")                      // no header: not a key file
        assertNotEquals("keystore", InstallKeyStore.storedWrap(dir))
    }

    @Test fun lazyKeyedApiBuildsNothingAtSetupAndAnswers503WhenTheKeyIsUnavailable() {
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
