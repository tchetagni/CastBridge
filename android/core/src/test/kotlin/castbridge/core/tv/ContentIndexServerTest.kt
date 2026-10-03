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
    private var child = false
    private val pin = "482915"
    /** This TV's own key (the TV app keeps it in its private storage). */
    private val tvKey = ByteArray(32) { (it * 7 + 3).toByte() }
    private fun newServer(key: ByteArray? = tvKey) = ReceiverServer(VolumeRegistry.single(dir), player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), pin = pin,
        routeGuard = { path -> if (trial && TrialPolicy.routeBlocked(path)) TrialPolicy.MESSAGE else null }, hostCheck = false,
        contentFlags = object : ContentFlags {
            override fun childActive() = child
            override fun protectedNames(items: List<LibraryItem>) = emptySet<String>()
        }, contentIndexKey = key).apply { start(5000, false) }
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
    private fun have(size: Long, h: String? = null, fresh: Boolean = false) = DedupDecision.parse(tv.have(size, h, fresh))

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
        // the index re-reads the list only on an event (a reception, a volume, a question): the phone's question puts it first
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data)))
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

    @Test fun theSignedIndexSurvivesARestartButItsHashesAreNeverFresh() {
        send("serie.S01E01.mkv")
        assertTrue(server.indexStep())
        val p0 = have(data.size.toLong(), sha(data))
        assertIs<DedupDecision.Tv.Present>(p0); assertTrue(p0.fresh, "calculé par ce processus")
        server.stop()
        assertTrue(File(dir, ContentIndex.FILE).isFile, "le cache est écrit sur le volume (reprise après redémarrage)")
        server = newServer()
        val p1 = have(data.size.toLong(), sha(data))
        assertIs<DedupDecision.Tv.Present>(p1, "copie : le cache signé suffit, sans relire le fichier")
        assertFalse(p1.fresh, "une ligne du cache n'est JAMAIS fraîche")
        // a MOVE asks for a fresh hash: re-read first, « indexing » until then
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data), fresh = true))
        assertTrue(server.indexStep())
        val p2 = have(data.size.toLong(), sha(data), fresh = true)
        assertIs<DedupDecision.Tv.Present>(p2); assertTrue(p2.fresh)
        assertFalse(tv.info().contains(ContentIndex.FILE)); assertFalse(tv.library().contains(ContentIndex.FILE))
    }

    // audit Opus, mutation « faire confiance à un .cbhash forgé »
    @Test fun aForgedOrForeignCacheIsIgnored() {
        send("film.mkv")
        val f = File(dir, "film.mkv")
        val forged = "e".repeat(64)
        // unsigned (old format) and signed with ANOTHER TV's key: both ignored
        File(dir, ContentIndex.FILE).writeText("v1\nfilm.mkv\t${data.size}\t${f.lastModified()}\t$forged\n")
        server.stop(); server = newServer()
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), forged), "ligne non signée : ignorée")
        File(dir, ContentIndex.FILE).writeText("v2\nfilm.mkv\t${data.size}\t${f.lastModified()}\t$forged\t${"0".repeat(64)}\n")
        server.stop(); server = newServer()
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), forged), "signature fausse : ignorée")
        val other = ContentIndex({ listOf(HeldFile("internal", dir, "film.mkv", "film.mkv", "", data.size.toLong())) }, { true }, ByteArray(32) { 9 })
        File(dir, ContentIndex.FILE).delete()
        assertTrue(other.step()); other.flush(force = true)                     // a cache written by another TV (its own key)
        server.stop(); server = newServer()
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data)), "cache d'une autre TV : ignoré, recalculé")
        assertTrue(server.indexStep())
        assertEquals(DedupDecision.Tv.Absent, have(data.size.toLong(), forged), "jamais « présent » sur l'empreinte forgée")
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(data)))
    }

    // audit Opus, mutation « contenu changé à date et taille identiques »
    @Test fun contentChangedWithTheSameDateAndSizeIsCaughtByTheFreshHash() {
        send("chanson.mp3")
        assertTrue(server.indexStep())
        server.stop()
        val f = File(dir, "chanson.mp3"); val mt = f.lastModified()
        val retouched = data.copyOf().also { it[100] = (it[100] + 1).toByte() }
        f.writeBytes(retouched); assertTrue(f.setLastModified(mt))           // tags edited, date and size kept
        server = newServer()
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(data)), "le cache ne peut pas le voir (même taille, même date)")
        assertEquals(DedupDecision.Tv.Indexing(1), have(data.size.toLong(), sha(data), fresh = true))
        assertTrue(server.indexStep())
        assertEquals(DedupDecision.Tv.Absent, have(data.size.toLong(), sha(data), fresh = true), "le hash frais voit le vrai contenu : aucun « Déplacer » sans copie")
        assertIs<DedupDecision.Tv.Present>(have(data.size.toLong(), sha(retouched), fresh = true))
    }

    @Test fun underAChildProfileTheAnswerGivesNoNameNorFolder() {
        send("Secret.2001.mkv")
        assertTrue(server.indexStep())
        child = true
        val raw = tv.have(data.size.toLong(), sha(data))
        assertFalse(raw.contains("Secret"), raw)
        val p = DedupDecision.parse(raw)
        assertIs<DedupDecision.Tv.Present>(p); assertTrue(p.masked)
    }

    @Test fun withoutAKeyOfThisTvNoCacheIsReadNorWritten() {
        server.stop(); server = newServer(key = null)
        send("a.mkv")
        assertTrue(server.indexStep())
        server.stop()
        assertFalse(File(dir, ContentIndex.FILE).exists())
        server = newServer(key = null)
    }
}
