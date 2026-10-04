package castbridge.play

import castbridge.core.owner.RevocationNotice
import castbridge.core.owner.RevocationState
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizLotIndex
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayRedact
import castbridge.play.entitlement.RevocationsFeed
import castbridge.play.entitlement.TicketVerifier
import castbridge.play.entitlement.TrustedIssuers
import castbridge.play.guard.FakeConn
import castbridge.play.guard.GuardHarness
import castbridge.play.guard.LogRedactor
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ré-audit Opus final (R2, R3, R4 et mineurs) : valeurs par défaut, quotas non brûlés, lots réservés hors de LOTS_DIR, santé des révocations. */
class FinalFixesTest {
    private val servers = ArrayList<PlayServer>()
    @AfterTest fun stop() { servers.forEach { it.close() }; servers.clear() }

    private fun env(vararg kv: Pair<String, String>): (String) -> String? = { k -> (mapOf("CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "127.0.0.1/32", "CASTBRIDGE_PLAY_REVOCATIONS_URL" to "https://bridge.sti-cm.com/api/v1/revocations") + kv.toMap())[k] }
    private fun tmp(): File = kotlin.io.path.createTempDirectory("final").toFile().also { it.deleteOnExit() }

    // ---- R2 : valeurs par défaut ----

    @Test fun defaultsAreFriendlyToSchools() {
        assertEquals(24, PlayConfig().maxPerIp)
        assertTrue("CASTBRIDGE_PLAY_MAX_PER_48" in PlayConfig.ENV_NAMES)
        assertEquals(512, PlayConfig().maxPer48)
        assertEquals(100, PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_MAX_PER_48" to "100")).maxPer48)
        assertEquals(24, PlayConfig.fromEnv(env()).maxPerIp)
    }

    // ---- R2 : un refus APRÈS le contrôle des droits ne brûle ni le jti ni le quota d'adresse ----

    @Test fun aRefusalAfterTheRightsCheckBurnsNeitherTheTicketNorTheAddressQuota() {
        val cfg = PlayConfig(webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, maxRooms = 1, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 2, createsPerIdentityPerDay = 1_000)
        val hub = PlayHub(cfg, { 1_000L }, GuardHarness.bank, TicketVerifier(listOf(TestKeys.pub)), limits = ConnectionLimits(1_000, 1_000_000))
        GuardHarness.host(hub, id = "tvA", ip = "198.51.100.1")
        val before = hub.usedTicketCount()
        repeat(5) { i ->
            val tv = FakeConn("tvB$i", "198.51.100.2").also { it.ticket = TestKeys.ticket(); hub.register(it) }
            hub.onText(tv, PlayCodec.encode(TestRights.create()))
            val e = tv.errors().single()
            assertTrue(e.contains("\"reason\":\"PLAY_BUSY\"") && !e.contains("dans une heure"), "salle pleine, et rien d'autre (n°$i) : $e")
        }
        assertEquals(before, hub.usedTicketCount(), "aucun jti brûlé par ces refus de capacité")
    }

    // ---- R3 : les lots réservés ne se montent jamais sur LOTS_DIR ----

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun writeLot(dir: File, name: String) {
        val maps = (0 until 5).map { i -> linkedMapOf<String, Any?>("id" to "res-$i", "track" to "general", "level" to null, "field" to null, "region" to "CM", "category" to "Culture", "difficulty" to 1 + i % 5,
            "question" to "Question de culture réservée numéro $i ?", "choices" to listOf("a$i", "b$i", "c$i", "d$i"), "answer" to 0, "explanation" to "Explication $i.", "source" to "test", "status" to "approved", "verif" to "computed", "lang" to "fr") }
        val qbytes = ("{\"version\":2,\"questions\":[\n" + maps.joinToString(",\n") { Json.write(it) } + "\n]}\n").toByteArray()
        val hashes = maps.associate { it["id"] as String to QuizLotIndex.questionHash(it) }
        val ibytes = Json.write(linkedMapOf("v" to 1, "scope" to "culture-cm", "version" to 1, "count" to hashes.size, "contentHash" to QuizLotIndex.contentHash(hashes), "q" to hashes.toSortedMap())).toByteArray()
        val manifest = Json.write(linkedMapOf("format" to 1, "id" to "culture-cm", "track" to "general", "level" to null, "field" to null, "part" to 1, "parts" to 1, "version" to 1, "questions" to maps.size,
            "lot" to linkedMapOf("feature" to "quiz", "scope" to "culture-cm", "title" to "culture-cm"),
            "files" to linkedMapOf("questions.json" to linkedMapOf("size" to qbytes.size, "sha256" to sha(qbytes)), "index.json" to linkedMapOf("size" to ibytes.size, "sha256" to sha(ibytes)))))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, d) in listOf("manifest.json" to manifest.toByteArray(), "questions.json" to qbytes, "index.json" to ibytes)) { z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } }
        File(dir, name).writeBytes(out.toByteArray())
    }

    @Test fun aReservedZipInTheFreeLotsFolderIsNeverServed() {
        val free = EmbeddedQuestionSource(levels = null).bank().all.size
        val d = tmp()
        writeLot(d, "quiz-culture-cm-p1-v1.quiz.zip")
        assertTrue(PlayServer.loadBank(d).all.size > free, "un lot LIBRE du dossier est bien servi (le test de contrôle)")
        val r = tmp()
        writeLot(r, "quiz-culture-cm-reserved-p1-v1.quiz.zip")
        assertEquals(free, PlayServer.loadBank(r).all.size, "un lot `-reserved-` n'est jamais fusionné dans la banque libre, même posé dans LOTS_DIR")
    }

    @Test fun startingWithTheSameFolderForFreeAndReservedLotsIsRefused() {
        val d = tmp()
        val e = assertFailsWith<IllegalStateException> { PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_LOTS_DIR" to d.path, "CASTBRIDGE_PLAY_RESERVED_DIR" to d.path)) }
        assertTrue(e.message!!.contains("LOTS_DIR") && e.message!!.contains("RESERVED_DIR"), e.message)
        PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_LOTS_DIR" to d.path, "CASTBRIDGE_PLAY_RESERVED_DIR" to tmp().path))   // deux dossiers : accepté
    }

    // ---- R4 : la santé dit l'état RÉEL des révocations ----

    private val ring = TrustedIssuers.parse(TestRights.trustedSpec).ring

    @Test fun healthNeverSaysOkBeforeAListHasBeenAccepted() {
        val srv = PlayServer(PlayConfig(webPlay = true, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, revocationsUrl = "http://127.0.0.1:9/revocations")).also { it.start(); servers += it }
        val body = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI("http://127.0.0.1:${srv.port}/play/health")).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body()
        assertTrue(body.contains("\"revocations\":\"none\""), body)
    }

    @Test fun theFeedReportsOkNoneAndStale() {
        var clock = 1_000_000_000_000L
        var body: String? = null
        val feed = RevocationsFeed(ring, { body }, { clock })
        assertEquals("none", feed.status())
        body = RevocationNotice.issue(TestRights.issuer, clock, RevocationState()); assertTrue(feed.refresh())
        assertEquals("ok", feed.status())
        clock += 61 * 60_000L; assertEquals("stale", feed.status(), "plus d'une heure sans rafraîchissement")
        clock += 24 * 3_600_000L; assertEquals("stale", feed.status(), "plus de 24 h : le service refuse d'ouvrir des salles")
    }

    @Test fun aReloadedFileKeepsTheAgeOfTheListNotTheAgeOfTheFile() {
        val now = System.currentTimeMillis()
        val f = File(tmp(), "rev.txt")
        f.writeText(RevocationNotice.issue(TestRights.issuer, now - 30 * 3_600_000L, RevocationState()))   // liste vieille de 30 h, fichier tout neuf (volume recopié)
        val feed = RevocationsFeed(ring, { null }, { now }, file = f)
        assertEquals(now - 30 * 3_600_000L, feed.lastRefreshMs(), "lastOkMs = min(mtime, issuedAt)")
        assertFalse(feed.usable(), "une liste de 30 h n'est plus assez fraîche")
    }

    // ---- mineurs ----

    @Test fun anHttpRevocationsUrlToARemoteHostIsAStartupErrorButTheInternalApiIsAllowed() {
        val e = assertFailsWith<IllegalStateException> { PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_REVOCATIONS_URL" to "http://bridge.sti-cm.com/api/v1/revocations")) }
        assertTrue(e.message!!.contains("https"), e.message)
        PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_REVOCATIONS_URL" to "http://castbridge-api:8080/api/v1/revocations"))   // réseau interne : la liste est signée
        PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_REVOCATIONS_URL" to "https://bridge.sti-cm.com/api/v1/revocations"))
    }

    @Test fun ticketsAreScrubbedFromTheLog() {
        val s = PlayRedact.scrub("ticket reçu cbp1.eyJhdWQiOiJ4In0.c2lnbmF0dXJl fin")
        assertFalse("cbp1" in s || "eyJhdWQ" in s, s)
    }

    @Test fun theSampledLogDoesNotHideOtherRooms() {
        val lines = ArrayList<String>()
        val log = LogRedactor({ lines += it }, { 5_000_000L })
        log.event("play.game.unranked", "x", mapOf("roomId" to "a".repeat(32), "seats" to 1))
        log.event("play.game.unranked", "x", mapOf("roomId" to "b".repeat(32), "seats" to 1))
        assertEquals(2, lines.size, "une clé par salle : la salle b n'est pas masquée par la salle a")
        log.event("play.game.unranked", "x", mapOf("roomId" to "a".repeat(32), "seats" to 1))
        assertEquals(2, lines.size, "la même salle reste échantillonnée")
    }
}
