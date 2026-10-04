package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.Limits
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.ClientIp
import castbridge.play.Cidr
import castbridge.play.ConnectionLimits
import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.TestKeys
import castbridge.play.TestRights
import java.io.File
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Limites de w20-07 câblées : débit de connexions (HTTP 429 + Retry-After), plafond relevé des adresses partagées, appareil, salle, codes faux, PLAY_BUSY structuré. */
class LimitFilterTest {
    private val servers = ArrayList<PlayServer>()
    @AfterTest fun stop() { servers.forEach { it.close() }; servers.clear() }
    private fun server(cfg: PlayConfig) = PlayServer(cfg).also { it.start(); servers += it }
    private fun cfg(connPerMinute: Int = 60, maxPerIp: Int = 8, shared: Int = 64) = PlayConfig(webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), connPerMinute = connPerMinute, maxPerIp = maxPerIp, maxPerIpShared = shared)

    /** Poignée de main brute : (statut, en-têtes en minuscules). Aucune trame n'est lue après. */
    private fun handshake(port: Int, xff: String): Pair<Int, Map<String, String>> {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 5_000
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
            s.getOutputStream().write(("GET /play/ws HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $key\r\nOrigin: https://bridge.sti-cm.com\r\nX-Forwarded-For: $xff\r\n\r\n").toByteArray())
            s.getOutputStream().flush()
            val head = StringBuilder()
            val input = s.getInputStream()
            while (!head.endsWith("\r\n\r\n")) { val b = input.read(); if (b < 0) break; head.append(b.toChar()) }
            val lines = head.toString().split("\r\n")
            return lines[0].substring(9, 12).toInt() to lines.drop(1).filter { ": " in it }.associate { it.substringBefore(": ").lowercase() to it.substringAfter(": ") }
        }
    }

    @Test fun newConnectionRateIsLimitedPerAddressWith429AndRetryAfterBeforeTheUpgrade() {
        val srv = server(cfg(connPerMinute = 5))
        repeat(5) { assertEquals(101, handshake(srv.port, "203.0.113.50").first, "poignée de main ${it + 1}") }
        val (status, headers) = handshake(srv.port, "203.0.113.50")
        assertEquals(429, status)
        assertTrue((headers["retry-after"]?.toLongOrNull() ?: 0) in 1..60, "Retry-After en secondes : $headers")
        assertEquals(101, handshake(srv.port, "203.0.113.51").first, "une autre adresse passe")
    }

    @Test fun cgnatFriendlyDefaultsAllowAClassroomOfFortyConnectionsInAMinute() {
        val limits = Limits({ 1_000L })
        assertEquals(60, limits.config.connPerMinute); assertEquals(600, limits.config.connPerHour); assertEquals(8, limits.config.maxOpenPerIp); assertEquals(64, limits.config.maxOpenPerIpShared)
        repeat(40) { assertTrue(limits.admitConnection("school").allowed) }
    }

    @Test fun sharedAddressCeilingRisesOnlyForPlayersReallySeatedForThirtySeconds() {
        var now = 1_000L
        val l = Limits({ now })
        val gate = ConnectionLimits(8, 1_000, 64, gate = l, maxPerIpShared = 64)
        val ip = "203.0.113.77"
        repeat(8) { assertEquals(ConnectionLimits.Verdict.OK, gate.acquire(ip)) }
        assertEquals(ConnectionLimits.Verdict.IP_FULL, gate.acquire(ip), "9e connexion d'une adresse ordinaire")
        repeat(8) { l.noteSeat(ip, "room-A", "device-$it") }
        assertEquals(ConnectionLimits.Verdict.IP_FULL, gate.acquire(ip), "des sièges tout neufs ne relèvent rien (identifiants inventés en rafale)")
        now += Limits.SEAT_MIN_AGE_MS
        repeat(8) { assertEquals(ConnectionLimits.Verdict.OK, gate.acquire(ip), "8 joueurs assis depuis 30 s : plafond 16 (n°${it + 9})") }
        assertEquals(ConnectionLimits.Verdict.IP_FULL, gate.acquire(ip), "17e : au-delà de 8 + appareils assis")
        // un autre appareil qui ne partage rien n'en profite pas
        repeat(8) { assertEquals(ConnectionLimits.Verdict.OK, gate.acquire("198.51.100.1")) }
        assertEquals(ConnectionLimits.Verdict.IP_FULL, gate.acquire("198.51.100.1"))
        assertEquals(ConnectionLimits.Verdict.RATE, ConnectionLimits(8, 1_000, 64, gate = Limits({ 1_000L }, Limits.Config(connPerMinute = 1)), 64).let { g -> g.acquire("x"); g.acquire("x") })
    }

    @Test fun deviceEntryRateIsLimitedWithStructuredRetryAfter() {
        val hub = GuardHarness.hub()
        val (_, code) = GuardHarness.host(hub)
        val device = GuardHarness.dev()
        var busy: String? = null
        for (i in 0 until 25) {
            val c = GuardHarness.join(hub, code, "Awa", "d$i", device = device, spectate = true)
            c.errors().firstOrNull { it.contains("PLAY_BUSY") }?.let { busy = it }
        }
        val e = busy ?: error("le 21e essai d'entrée du même appareil devait être refusé")
        assertTrue(Regex("\"retryAfterMs\":\\d+").containsMatchIn(e), "retryAfterMs structuré : $e")
        assertTrue(e.contains("\"retryable\":true"))
    }

    @Test fun roomMessageRateIsLimitedPerRoomNotPerPlayer() {
        val hub = GuardHarness.hub()
        hub.guard = PlayGuard(Limits(config = Limits.Config(roomMessagesPerSecond = 1, roomBurst = 20)), LogRedactor.silent())
        val (tv, code) = GuardHarness.host(hub)
        val a = GuardHarness.join(hub, code, "Awa", "a"); val b = GuardHarness.join(hub, code, "Bello", "b")
        var refused = 0
        repeat(60) { val c = if (it % 2 == 0) a else b; hub.onText(c, PlayCodec.encode(ClientMsg.Pong("p$it"))) }
        for (c in listOf(a, b, tv)) refused += c.errors().count { it.contains("PLAY_BUSY") }
        assertTrue(refused in 25..45, "au-delà de la rafale de 20, les messages de la salle (tous joueurs confondus) sont refusés : $refused")
        assertFalse(a.closedWith != null || b.closedWith != null, "un refus de débit ne ferme rien")
    }

    @Test fun busyServiceAnswersPlayBusyWithRetryAfterMs() {
        val hub = castbridge.play.PlayHub(PlayConfig(webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, maxRooms = 1, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 100_000), { 1_000L }, GuardHarness.bank,
            castbridge.play.entitlement.TicketVerifier(listOf(TestKeys.pub)), limits = ConnectionLimits(1_000, 1_000_000))
        GuardHarness.host(hub)
        val tv2 = FakeConn("tv2", "198.51.100.10").also { it.ticket = TestKeys.ticket(); hub.register(it) }
        hub.onText(tv2, PlayCodec.encode(TestRights.create()))
        val e = tv2.errors().single()
        assertTrue(e.contains("\"reason\":\"PLAY_BUSY\"") && e.contains("\"retryAfterMs\":15000"), e)
    }

    @Test fun blockedAddressGetsTheStructuredWaitButTheRightCodeStillEnters() {
        var now = 1_000L
        val hub = GuardHarness.hub(clock = { now })
        hub.guard = PlayGuard(Limits(config = Limits.Config(joinPerMinutePerDevice = 1_000_000)), LogRedactor.silent())
        val (_, code) = GuardHarness.host(hub)
        repeat(30) { GuardHarness.join(hub, "ZZZZZZ%02d".format(it), "Xa", "bad$it", ip = "203.0.113.90", device = "mal-0001") }
        val blocked = GuardHarness.join(hub, "ZZZZZZ99", "Xa", "again", ip = "203.0.113.90", device = "mal-0001").errors().single()
        assertTrue(blocked.contains("PLAY_BAD_CODE") && Regex("\"retryAfterMs\":\\d{5,}").containsMatchIn(blocked), "attente structurée : $blocked")
        assertTrue(GuardHarness.join(hub, code, "Awa", "late", ip = "203.0.113.90").welcomed(), "le BON code entre toujours, même d'une adresse bloquée pour de mauvais codes")
        assertTrue(GuardHarness.join(hub, code, "Bello", "other", ip = "203.0.113.91").welcomed())
    }

    @Test fun nearMissCodesRotateTheRoomCodeOnlyFromJoinAndAtMostOncePerMinute() {
        var now = 1_000L
        val hub = GuardHarness.hub(clock = { now })
        hub.guard = PlayGuard(Limits(config = Limits.Config(joinPerMinutePerDevice = 1_000_000)), LogRedactor.silent())
        val (_, code) = GuardHarness.host(hub)
        val room = hub.rooms().single()
        // 50 frappes à un symbole du code vivant, depuis des adresses différentes (le plafond par adresse ne s'en mêle pas)
        fun near(): String { val c = room.code.toCharArray(); c[7] = if (c[7] == 'A') 'B' else 'A'; return String(c).also { check(RoomCode.nearMiss(it, room.code)) } }
        repeat(49) { GuardHarness.join(hub, near(), "Xa", "n$it", ip = "198.51.100.${it + 1}") }
        assertEquals(code, room.code, "49 : pas encore")
        GuardHarness.join(hub, near(), "Xa", "n49", ip = "198.51.100.200")
        assertNotEquals(code, room.code, "50 frappes proches : le code a changé")
        val second = room.code
        repeat(60) { GuardHarness.join(hub, near(), "Xa", "m$it", ip = "192.0.2.${it + 1}") }
        assertEquals(second, room.code, "pas deux rotations en moins d'une minute")
        now += 61_000
        repeat(50) { GuardHarness.join(hub, near(), "Xa", "p$it", ip = "192.0.2.${it + 100}") }
        assertNotEquals(second, room.code, "une minute plus tard, une nouvelle rotation est possible")
        // un code faux ordinaire (aucun lien avec une salle) ne fait jamais tourner un code
        val calm = GuardHarness.hub(); val (_, c2) = GuardHarness.host(calm)
        repeat(200) { GuardHarness.join(calm, "ZZZZZ%03d".format(it % 1000), "Xa", "o$it", ip = "203.0.113.${it % 250 + 1}") }
        assertEquals(c2, calm.rooms().single().code)
    }

    @Test fun codeRotationIsReachableOnlyFromTheJoinPath() {
        val hubSrc = File("src/main/kotlin/castbridge/play/RoomRegistry.kt").readText()
        val callers = Regex("noteNearMiss").findAll(hubSrc).count()
        assertEquals(1, callers, "un seul appel à noteNearMiss dans le registre")
        val fn = hubSrc.substringAfter("private fun nearMiss(").substringBefore("\n    private fun codeFailed")
        assertTrue("noteNearMiss" in fn)
        val joinFn = hubSrc.substringAfter("private fun join(").substringBefore("private fun resume(")
        assertTrue("nearMiss(" in joinFn, "appelée depuis join")
        val others = hubSrc.replace(fn, "").replace(joinFn, "")
        assertFalse("nearMiss(" in others.replace("private fun nearMiss(", ""), "nulle part ailleurs (ni resume, ni create, ni tick)")
        val all = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" && it.name != "RoomRegistry.kt" }.joinToString { it.readText() }
        assertFalse("noteNearMiss" in all, "ni les contrôleurs de repli ni le serveur n'y touchent")
    }
}
