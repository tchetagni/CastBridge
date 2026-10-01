package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.*
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.*

/** (c) one helper for every authenticated request, (d) no loop ever replays a refused credential, tokens never count as PIN failures. */
class CredentialTest {
    private val TOKEN = "cbk_" + "0123456789abcdef".repeat(4)

    @Test fun theHelperPicksTheRightHeaderAndRefusesUnusableCredentials() {
        assertEquals("X-CB-Pin" to "482913", TvCredential.header("482913"))
        assertEquals("X-CB-Token" to TOKEN, TvCredential.header(TOKEN))
        assertEquals(emptyMap(), TvCredential.headers(null), "no credential on purpose (TV without PIN)")
        for (bad in listOf("", "   ", "12345", "1234567", "abcdef", "cbk_", "cbk_" + "a".repeat(63), "cbk_" + "a".repeat(65), "------"))
            assertFailsWith<TvCredential.Missing>(bad) { TvCredential.header(bad) }
        assertEquals("X-CB-Token: $TOKEN", TvCredential.headerLine(TOKEN)); assertNull(TvCredential.headerLine(null))
        assertEquals("482913", TvCredential.btPin("482913")); assertEquals(TvAuth.NO_PIN, TvCredential.btPin(TOKEN)); assertEquals(TvAuth.NO_PIN, TvCredential.btPin(""))
        assertTrue(TvCredential.isUsable(TOKEN) && TvCredential.isUsable("000000") && !TvCredential.isUsable("") && !TvCredential.isUsable(null))
    }

    @Test fun anEmptyCredentialNeverReachesTheNetwork() {
        // 127.0.0.1:1 would refuse the connection; the failure must be local and explicit, not an IOException from the socket
        val tv = TvClient("http://127.0.0.1:1", "")
        assertFailsWith<TvCredential.Missing> { tv.info() }
        assertFailsWith<TvCredential.Missing> { castbridge.core.dl.DownloadsClient("http://127.0.0.1:1", "").state() }
    }

    @Test fun anExpiredTokenOfATrustedPhoneIsNotConvertedIntoAWrongPinThatLocksTheTv() {
        val dir = kotlin.io.path.createTempDirectory("cred").toFile()
        val clock = FakeClock()
        val reg = TrustRegistry(MemoryTrustPersistence(), clock::now)
        reg.trust("AA:BB:CC:DD:EE:01", "Galaxy")
        val tok = reg.issueToken("AA:BB:CC:DD:EE:01")!!.token
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "482913", tokenAuth = reg::verifyToken).apply { start(5000, false) }
        try {
            val base = "http://127.0.0.1:$port"
            clock.advance(13 * 3600_000L)            // the token expired
            repeat(30) { // a loop that insists with the expired token: always "bad token", never a lockout
                val e = assertFailsWith<TvClient.HttpError> { TvClient(base, tok).info() }
                assertEquals(401, e.code); assertTrue(TvClient.isBadToken(e), e.message)
            }
            assertEquals("482913".length, TvClient(base, "482913").info().let { 6 }, "the PIN still works at once: the tokens did not lock the TV")
            // and an unusable credential never counts either: it is not sent
            repeat(30) { assertFailsWith<TvCredential.Missing> { TvClient(base, "").info() } }
            TvClient(base, "482913").info()
            // a wrong PIN, on the other hand, is counted (5 tries lock), which is exactly why no loop may send it twice
            repeat(5) { assertFailsWith<TvClient.HttpError> { TvClient(base, "000000").info() } }
            assertTrue(assertFailsWith<TvClient.HttpError> { TvClient(base, "482913").info() }.message!!.contains("locked"))
        } finally { server.stop(); dir.deleteRecursively() }
    }

    @Test fun credentialGateNeverOffersARefusedCredentialAgain() {
        val g = CredentialGate()
        assertTrue(g.allows("tv", TOKEN)); g.refuse("tv", TOKEN)
        assertFalse(g.allows("tv", TOKEN)); assertTrue(g.allows("tv", "cbk_" + "f".repeat(64))); assertTrue(g.allows("other", TOKEN))
        assertFalse(g.allows("tv", null))
        g.clear("tv"); assertTrue(g.allows("tv", TOKEN))
    }

    /** The audit as a test: no screen or client may hand-roll a PIN/token header. */
    @Test fun noOneButTheHelperBuildsAuthenticationHeaders() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android/core").isDirectory }.resolve("android")
        val allowed = setOf("TrustRegistry.kt", "Credentials.kt", "ReceiverServer.kt", "ParentalApi.kt", "ParentalClient.kt")
        val offenders = ArrayList<String>()
        for (module in listOf("core", "sender", "receiver")) File(root, "$module/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in allowed }.forEach { f ->
            f.readLines().forEachIndexed { i, l ->
                val t = l.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                if (Regex("\"X-CB-(Pin|Token)\"").containsMatchIn(l) || Regex("TvAuth\\s*\\.\\s*(header|PIN_HEADER|TOKEN_HEADER)").containsMatchIn(l)) offenders += "${f.name}:${i + 1}: $t"
            }
        }
        assertTrue(offenders.isEmpty(), "use TvCredential instead:\n" + offenders.joinToString("\n"))
    }

    /** A 6-digit check on a credential would shut trusted phones (token) out of a screen. */
    @Test fun noScreenAssumesThatTheCredentialIsASixDigitPin() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "android/core").isDirectory }.resolve("android")
        val offenders = ArrayList<String>()
        for (module in listOf("sender", "receiver")) File(root, "$module/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PinStore.kt" }.forEach { f ->   // PinStore validates the PIN being typed
            f.readLines().forEachIndexed { i, l ->
                if (Regex("Pin\\.isValidFormat\\((pin|tvPin)\\)").containsMatchIn(l)) offenders += "${f.name}:${i + 1}: ${l.trim()}"
            }
        }
        assertTrue(offenders.isEmpty(), "use TvCredential.isUsable:\n" + offenders.joinToString("\n"))
    }
}
