package castbridge.core.gateway

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** relay-R1 § 4 : le SOCKS local de la TV est réservé au processus CastBridge-TV (jeton local aléatoire par session) ; le tuyau mesure sa liaison par PING. */
class SocksAuthTest {
    // ------------------------------------------------------------------ négociation, octet par octet

    private fun negotiate(token: String?, vararg client: Int): Triple<Boolean, ByteArray, Int> {
        val inp = ByteArrayInputStream(ByteArray(client.size) { client[it].toByte() })   // la version 5 est déjà lue par l'appelant : on part du nombre de méthodes
        val out = ByteArrayOutputStream()
        val ok = SocksAuth.negotiate(DataInputStream(inp), out, token)
        return Triple(ok, out.toByteArray(), inp.available())
    }

    private fun userPass(user: String, pass: String): IntArray = intArrayOf(1, user.length) + user.map { it.code }.toIntArray() + intArrayOf(pass.length) + pass.map { it.code }.toIntArray()

    @Test fun withoutATokenTheOldBehaviourIsKept() {
        val (ok, reply, _) = negotiate(null, 1, 0)
        assertTrue(ok); assertContentEquals(byteArrayOf(5, 0), reply)
        val (ok2, reply2, _) = negotiate(null, 2, 0, 2)
        assertTrue(ok2); assertContentEquals(byteArrayOf(5, 0), reply2, "sans jeton configuré : aucune authentification (les tests et les TV d'avant)")
    }

    @Test fun withATokenOnlyTheRightPasswordPasses() {
        val token = "a1b2c3d4e5f60718"
        val (ok, reply, left) = negotiate(token, *(intArrayOf(2, 0, 2) + userPass("cb", token)))
        assertTrue(ok); assertContentEquals(byteArrayOf(5, 2, 1, 0), reply); assertEquals(0, left)
        val (bad, reply2, _) = negotiate(token, *(intArrayOf(2, 0, 2) + userPass("cb", "autre")))
        assertFalse(bad); assertContentEquals(byteArrayOf(5, 2, 1, 1), reply2)
    }

    @Test fun aClientThatOffersNoAuthenticationIsRefused() {
        val (ok, reply, _) = negotiate("secret0123456789", 1, 0)
        assertFalse(ok); assertContentEquals(byteArrayOf(5, 0xFF.toByte()), reply, "« aucune méthode acceptable » : un autre processus de la TV ne passe pas")
        val (ok2, reply2, _) = negotiate("secret0123456789", 0)
        assertFalse(ok2); assertContentEquals(byteArrayOf(5, 0xFF.toByte()), reply2)
    }

    @Test fun aDamagedSubnegotiationIsRefused() {
        val token = "secret0123456789"
        assertFalse(negotiate(token, *(intArrayOf(1, 2) + intArrayOf(9, 2, 'c'.code, 'b'.code, token.length) + token.map { it.code })).first, "version de sous-négociation inconnue")
        assertFalse(negotiate(token, 1, 2, 1, 2, 'c'.code).first, "tronquée")
        assertFalse(negotiate(token, *(intArrayOf(1, 2) + userPass("cb", ""))).first, "mot de passe vide")
        assertFalse(negotiate(token, *(intArrayOf(1, 2) + userPass("cb", token + "x"))).first, "plus long")
        assertFalse(negotiate(token, *(intArrayOf(1, 2) + userPass("cb", token.dropLast(1)))).first, "plus court")
    }

    @Test fun theComparisonDoesNotStopAtTheFirstDifferentByteButStillMatchesExactly() {
        assertTrue(SocksAuth.sameToken("abcdef", "abcdef"))
        assertFalse(SocksAuth.sameToken("abcdef", "abcdeg"))
        assertFalse(SocksAuth.sameToken("abcdef", "abcde"))
        assertFalse(SocksAuth.sameToken("", "x"))
        assertFalse(SocksAuth.sameToken("x", ""))
        assertTrue(SocksAuth.sameToken("", ""), "deux chaînes vides sont égales : c'est l'appelant qui refuse un jeton vide")
    }

    @Test fun theSessionTokenIsRandomLongAndDifferentEachTime() {
        val a = SocksAuth.newToken(); val b = SocksAuth.newToken()
        assertEquals(32, a.length); assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(a != b)
    }

    // ------------------------------------------------------------------ l'authentificateur du processus

    @Test fun theAuthenticatorAnswersOnlyForOurLoopbackSocksPort() {
        val auth = SocksTokenAuthenticator(port = 1080, token = { "jeton-de-session" })
        Authenticator.setDefault(auth)
        try {
            val lo = java.net.InetAddress.getByName("127.0.0.1")
            val pw = Authenticator.requestPasswordAuthentication("127.0.0.1", lo, 1080, "SOCKS5", "SOCKS authentication", null)
            assertNotNull(pw); assertEquals("cb", pw.userName); assertEquals("jeton-de-session", String(pw.password))
            assertNull(Authenticator.requestPasswordAuthentication("127.0.0.1", lo, 1081, "SOCKS5", "x", null), "un autre port")
            assertNull(Authenticator.requestPasswordAuthentication("127.0.0.1", lo, 1080, "http", "x", null), "un autre protocole (une vraie demande HTTP d'un proxy ne reçoit pas notre jeton)")
            assertNull(Authenticator.requestPasswordAuthentication("example.com", java.net.InetAddress.getByName("93.184.216.34"), 1080, "SOCKS5", "x", null), "jamais un proxy qui n'est pas le nôtre")
        } finally { Authenticator.setDefault(null) }
    }

    @Test fun noTokenMeansNoAnswer() {
        Authenticator.setDefault(SocksTokenAuthenticator(1080) { null })
        try { assertNull(Authenticator.requestPasswordAuthentication("127.0.0.1", java.net.InetAddress.getByName("127.0.0.1"), 1080, "SOCKS5", "x", null)) }
        finally { Authenticator.setDefault(null) }
    }

    // ------------------------------------------------------------------ de bout en bout avec le vrai client SOCKS du JDK

    private fun link(): Pair<Mux, Mux> {
        ServerSocket(0).use { ss ->
            val a = Socket("127.0.0.1", ss.localPort); val b = ss.accept()
            return Mux(a.getInputStream(), a.getOutputStream()) to Mux(b.getInputStream(), b.getOutputStream())
        }
    }

    private val internet = ServerSocket(0).also { ss ->
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val c = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { c.use { it.getOutputStream().apply { write("bonjour".toByteArray()); flush() } } }
            }
        }
    }

    @Volatile private var token: String? = "0123456789abcdef"
    private val entry = Entry({ it == "123456" }, port = 0, socksToken = { token })
    private var socksPort = 0

    @BeforeTest fun setUp() { socksPort = entry.startSocks() }
    @AfterTest fun tearDown() { entry.stop(); internet.close(); Authenticator.setDefault(null) }

    private fun attach() {
        val (tv, phone) = link()
        thread(isDaemon = true) { entry.attach(tv, "phone") }
        thread(isDaemon = true) { runCatching { Exit(phone, "123456").run() } }
        repeat(100) { if (entry.connected) return; Thread.sleep(20) }
        fail("gateway never attached")
    }

    private fun viaTv(): Socket = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))).apply {
        soTimeout = 20_000; connect(InetSocketAddress("127.0.0.1", internet.localPort), 20_000)
    }

    @Test fun ourOwnProcessWithTheTokenGetsThrough() {
        attach()
        Authenticator.setDefault(SocksTokenAuthenticator(socksPort) { token })
        viaTv().use { assertEquals("bonjour", String(it.getInputStream().readBytes())) }
    }

    @Test fun anotherProcessWithoutTheTokenIsRefused() {
        attach()
        // ni authentificateur ni jeton : ce que fait n'importe quelle autre application de la TV qui trouve le port
        assertFailsWith<java.io.IOException> { viaTv().use { it.getInputStream().read() } }
    }

    @Test fun aWrongTokenIsRefused() {
        attach()
        Authenticator.setDefault(SocksTokenAuthenticator(socksPort) { "pas-le-bon-jeton" })
        assertFailsWith<java.io.IOException> { viaTv().use { it.getInputStream().read() } }
    }

    @Test fun aNewSessionTokenCutsTheOldOneAtOnce() {
        attach()
        Authenticator.setDefault(SocksTokenAuthenticator(socksPort) { "0123456789abcdef" })
        viaTv().use { assertEquals("bonjour", String(it.getInputStream().readBytes())) }
        token = SocksAuth.newToken()                       // nouvelle session du tuyau : l'ancien jeton ne vaut plus rien
        assertFailsWith<java.io.IOException> { viaTv().use { it.getInputStream().read() } }
    }

    @Test fun withoutAConfiguredTokenTheProxyStaysOpenLikeBefore() {
        token = null
        attach()
        viaTv().use { assertEquals("bonjour", String(it.getInputStream().readBytes())) }
    }

    // ------------------------------------------------------------------ PING : la mesure de la liaison

    @Test fun pingMeasuresTheLinkRoundTripAndNeedsAPhone() {
        assertNull(entry.ping(500), "pas de téléphone : pas de mesure")
        attach()
        val rtt = entry.ping(5_000)
        assertNotNull(rtt); assertTrue(rtt in 0..5_000, "rtt=$rtt")
        repeat(3) { assertNotNull(entry.ping(5_000), "plusieurs mesures de suite") }
    }

    @Test fun aPingNobodyAnswersTimesOutWithoutBlockingTheLink() {
        val (tv, phone) = link()
        thread(isDaemon = true) { entry.attach(tv, "phone") }
        // un « téléphone » qui dit bonjour (HELLO) puis se tait : le PING reste sans réponse
        thread(isDaemon = true) { runCatching { phone.write(Frame(Gw.HELLO, 0, (Gw.MAGIC + "123456").toByteArray())); phone.read(); Thread.sleep(3_000) } }
        repeat(100) { if (entry.connected) return@repeat; Thread.sleep(20) }
        val t0 = System.currentTimeMillis()
        assertNull(entry.ping(300))
        assertTrue(System.currentTimeMillis() - t0 < 2_000)
    }
}
