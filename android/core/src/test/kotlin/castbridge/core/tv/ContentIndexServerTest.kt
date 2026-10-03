package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.owner.TrialPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.test.*

/** R-12 : GET /api/have sur la VRAIE ReceiverServer (boucle locale) : index du contenu, invalidation par mtime, partiels ignorés, code exigé, jamais pendant la lecture. */
class ContentIndexServerTest {
    private val dir = kotlin.io.path.createTempDirectory("have").toFile()
    private val player = FakePlayer()
    private var trial = false
    private val pin = "482915"
    private fun newServer() = ReceiverServer(VolumeRegistry.single(dir), player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), pin = pin,
        routeGuard = { path -> if (trial && TrialPolicy.routeBlocked(path)) TrialPolicy.MESSAGE else null }, hostCheck = false).apply { start(5000, false) }
    private var server = newServer()
    private val base get() = "http://127.0.0.1:${server.listeningPort}"
    private val tv get() = TvClient(base, pin)
    private val data = Random(12).nextBytes(300_000)
    private fun sha(b: ByteArray) = ContentHash.hex(java.security.MessageDigest.getInstance("SHA-256").digest(b))

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun get(path: String, withPin: String? = pin): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.setRequestProperty("Connection", "close")
        withPin?.let { c.setRequestProperty("X-CB-Pin", it) }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: "")
    }
    private fun send(name: String, b: ByteArray = data) = tv.upload(name, 0, b.size.toLong(), ByteArrayInputStream(b)) {}
    private fun have(size: Long, h: String? = null) = DedupDecision.parse(tv.have(size, h))

    @Test fun anIndexedFileIsFoundByItsContentUnderAnyName() {
        send("Avatar.2009.mkv")
        // size only: a candidate, not hashed yet, and never a name
        val sizeOnly = tv.have(data.size.toLong())
        assertEquals(DedupDecision.Tv.Candidates(1, true), DedupDecision.parse(sizeOnly))
        assertFalse(sizeOnly.contains("Avatar"), "une question par taille ne donne jamais de nom")
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data)), "pas encore haché : « indexing », jamais « absent »")
        assertTrue(server.indexStep())
        val p = have(data.size.toLong(), sha(data))
        assertIs<DedupDecision.Tv.Present>(p)
        assertEquals("Avatar.2009.mkv", p.name); assertEquals(sha(data), p.sha256); assertTrue(p.complete)
        assertEquals(DedupDecision.Tv.Absent, have(data.size.toLong(), "c".repeat(64)), "même taille, autre contenu")
        assertEquals(DedupDecision.Tv.Absent, have(data.size + 1L))
        assertEquals(DedupDecision.Tv.Candidates(1, false), have(data.size.toLong()))
        assertFalse(server.indexStep(), "rien de plus à hacher")
    }

    @Test fun aChangedModificationTimeOrContentInvalidatesTheHash() {
        send("clip.mp4")
        assertTrue(server.indexStep())
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(data)))
        val f = File(dir, "clip.mp4")
        assertTrue(f.setLastModified(f.lastModified() - 120_000))
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data)), "mtime changé : l'ancien hash ne vaut plus")
        assertTrue(server.indexStep())
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(data)))
        // same size, other bytes: the old hash never answers « present »
        val changed = data.copyOf().also { it[10] = (it[10] + 1).toByte() }
        f.writeBytes(changed); f.setLastModified(f.lastModified() + 5_000)
        assertTrue(server.indexStep())
        assertEquals(DedupDecision.Tv.Absent, have(data.size.toLong(), sha(data)))
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(changed)))
    }

    @Test fun aPartialCopyNeverCountsAsPresent() {
        val c = URL("$base/upload/${TvClient.enc("long.mkv")}?offset=0&total=${data.size * 2}").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(data.size); c.setRequestProperty("X-CB-Pin", pin); c.setRequestProperty("Connection", "close")
        c.outputStream.use { it.write(data) }
        assertEquals(200, c.responseCode)
        assertTrue(File(dir, "long.mkv.part").isFile)
        assertEquals(DedupDecision.Tv.Absent, have(data.size.toLong()), "le .part (taille courante) n'est pas un fichier tenu")
        assertEquals(DedupDecision.Tv.Absent, have(data.size * 2L))
        assertFalse(server.indexStep(), "un partiel n'est jamais haché")
    }

    @Test fun theRouteNeedsTheCodeAndIsClosedOnATrialTv() {
        send("a.mkv")
        assertEquals(401, get("/api/have?size=${data.size}", withPin = null).first)
        assertEquals(401, get("/api/have?size=${data.size}", withPin = "000000").first)
        assertEquals(200, get("/api/have?size=${data.size}").first)
        assertEquals(400, get("/api/have").first)
        assertEquals(400, get("/api/have?size=${data.size}&sha256=zz").first)
        trial = true
        assertEquals(403, get("/api/have?size=${data.size}").first)
        assertFalse(TrialPolicy.routeAllowed("/api/have"))
    }

    @Test fun nothingIsHashedWhileAVideoPlays() {
        send("film.mkv")
        player.st = PlayerState("playing", "film.mkv", 0, 1000)
        assertFalse(server.indexStep(), "lecture en cours : aucune lecture de disque pour l'index")
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data)))
        player.st = PlayerState("buffering", "film.mkv", 0, 1000)
        assertFalse(server.indexStep())
        player.st = PlayerState()
        assertTrue(server.indexStep())
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(data)))
    }

    @Test fun theIndexSurvivesARestartAndStaysOutOfTheLibrary() {
        send("serie.S01E01.mkv")
        assertTrue(server.indexStep())
        server.stop()
        assertTrue(File(dir, ContentIndex.FILE).isFile, "le cache est écrit sur le volume (reprise après redémarrage)")
        server = newServer()
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(data)), "relu sans relire le fichier")
        assertFalse(server.indexStep())
        assertFalse(tv.info().contains(ContentIndex.FILE)); assertFalse(tv.library().contains(ContentIndex.FILE))
    }
}
