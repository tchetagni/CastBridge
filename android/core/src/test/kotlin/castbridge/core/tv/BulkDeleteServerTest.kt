package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.net.JsonLite
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** `POST /api/delete/batch` et `GET /api/delete/batch?job=` sur la vraie ReceiverServer (dossiers temporaires). */
class BulkDeleteServerTest {
    private val root = kotlin.io.path.createTempDirectory("bulk").toFile()
    private val internalDir = File(root, "internal").apply { mkdirs() }
    private val usbDir = File(root, "usb").apply { mkdirs() }
    private val lists = AtomicInteger()
    @Volatile private var gate: CountDownLatch? = null
    private var childActive = false
    private val flags = object : ContentFlags {
        override fun childActive() = childActive
        override fun protectedNames(items: List<LibraryItem>) = emptySet<String>()
    }
    private val vols = listOf(
        StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false),
        StorageVolume("usb-1", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true))
    private val provider = object : VolumeProvider {
        override fun scan(remeasure: Boolean) = vols
        override fun storeFor(volume: StorageVolume): VolumeStore = object : FileStore(volume) {
            override fun list(): List<StoreEntry> { lists.incrementAndGet(); return super.list() }
            override fun deleteFinal(name: String): Boolean { gate?.await(10, TimeUnit.SECONDS); return super.deleteFinal(name) }
        }
    }
    private val player = FakePlayer()
    private val server = ReceiverServer(VolumeRegistry(provider).also { it.refresh() }, player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0, target = "internal"),
        contentFlags = flags, hostCheck = false).apply { start(5000, false) }
    private val tv = TvClient("http://127.0.0.1:${server.listeningPort}")

    @AfterTest fun tearDown() { gate?.countDown(); server.stop(); root.deleteRecursively() }

    private fun put(dir: File, n: String, size: Int) { File(dir, n).writeBytes(ByteArray(size)) }
    private fun finish(job: String): Map<String, Any?> {
        val t = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < t) {
            val s = JsonLite.obj(tv.deleteBatchStatus(job))
            if (s["finished"] == true) return s
            Thread.sleep(10)
        }
        fail("le lot ne finit pas")
    }
    private fun start(vararg t: BulkDelete.Target) = JsonLite.obj(tv.deleteBatch(t.toList()))

    @Test fun `supprime, libere les octets et ne relit la liste qu une fois apres`() {
        put(internalDir, "a.mp4", 1000); put(internalDir, "b.mp4", 2000); put(usbDir, "c.mkv", 4000); put(internalDir, "reste.mp4", 7)
        assertEquals(4, server.libraryItems().size)
        val before = lists.get()
        val r = start(BulkDelete.Target("a.mp4", "internal"), BulkDelete.Target("b.mp4"), BulkDelete.Target("c.mkv", "usb-1"))
        assertEquals(3L, r["count"])
        val s = finish(r["job"] as String)
        assertEquals(3L, s["done"]); assertEquals(3L, s["total"]); assertEquals(7000L, s["freed"]); assertEquals(emptyList<Any>(), s["skipped"])
        assertFalse(File(internalDir, "a.mp4").exists()); assertFalse(File(internalDir, "b.mp4").exists()); assertFalse(File(usbDir, "c.mkv").exists())
        assertTrue(File(internalDir, "reste.mp4").exists())
        assertEquals(before, lists.get(), "le lot ne relit pas la liste fichier par fichier")
        assertEquals(listOf("reste.mp4"), server.libraryItems().map { it.name })
        assertEquals(before + vols.size, lists.get(), "une seule relecture (une par volume) après l'invalidation finale")
    }

    @Test fun `une copie sur un seul volume laisse l autre`() {
        put(internalDir, "x.mp4", 10); put(usbDir, "x.mp4", 30)
        val s = finish(start(BulkDelete.Target("x.mp4", "usb-1"))["job"] as String)
        assertEquals(30L, s["freed"])
        assertTrue(File(internalDir, "x.mp4").exists()); assertFalse(File(usbDir, "x.mp4").exists())
    }

    @Test fun `un fichier absent ou en deplacement est laisse avec sa raison, le reste est supprime`() {
        put(internalDir, "ok.mp4", 100); put(internalDir, "move.mp4", 200)
        val f = ReceiverServer::class.java.getDeclaredField("moveJob").apply { isAccessible = true }
        f.set(server, MoveJob("move.mp4", vols[0], vols[1], "move.mp4", 200))
        val s = finish(start(BulkDelete.Target("absent.mp4"), BulkDelete.Target("move.mp4"), BulkDelete.Target("ok.mp4"))["job"] as String)
        assertEquals(3L, s["done"]); assertEquals(100L, s["freed"])
        val sk = (s["skipped"] as List<*>).associate { m -> (m as Map<*, *>).let { it["name"] to it["reason"] } }
        assertEquals<Map<Any?, Any?>>(mapOf("absent.mp4" to "absent", "move.mp4" to "moving"), sk)
        assertTrue(File(internalDir, "move.mp4").exists()); assertFalse(File(internalDir, "ok.mp4").exists())
    }

    @Test fun `le fichier en lecture est arrete puis supprime`() {
        put(internalDir, "lu.mp4", 50)
        tv.play("lu.mp4"); assertEquals("playing", player.state().state)
        finish(start(BulkDelete.Target("lu.mp4"))["job"] as String)
        assertEquals("idle", player.state().state); assertFalse(File(internalDir, "lu.mp4").exists())
    }

    @Test fun `un second lot pendant le premier recoit 409`() {
        put(internalDir, "a.mp4", 1); put(internalDir, "b.mp4", 1)
        gate = CountDownLatch(1)
        val r = start(BulkDelete.Target("a.mp4"))
        val e = assertFailsWith<TvClient.HttpError> { tv.deleteBatch(listOf(BulkDelete.Target("b.mp4"))) }
        assertEquals(409, e.code)
        assertTrue(File(internalDir, "b.mp4").exists())
        gate!!.countDown()
        finish(r["job"] as String)
        finish(start(BulkDelete.Target("b.mp4"))["job"] as String)          // le premier fini, un autre peut partir
        assertFalse(File(internalDir, "b.mp4").exists())
    }

    @Test fun `refus d un profil enfant, d un nom invalide, d un corps vide et d un lot inconnu`() {
        put(internalDir, "a.mp4", 1)
        childActive = true
        assertEquals(403, assertFailsWith<TvClient.HttpError> { tv.deleteBatch(listOf(BulkDelete.Target("a.mp4"))) }.code)
        childActive = false
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.deleteBatch(listOf(BulkDelete.Target("../a.mp4"))) }.code)
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.deleteBatch(emptyList()) }.code)
        assertEquals(404, assertFailsWith<TvClient.HttpError> { tv.deleteBatchStatus("nope") }.code)
        assertTrue(File(internalDir, "a.mp4").exists())
    }

    @Test fun `sans le code PIN la route est refusee comme les autres`() {
        val s = ReceiverServer(VolumeRegistry(provider).also { it.refresh() }, FakePlayer(), 0, pin = "123456", hostCheck = false).apply { start(5000, false) }
        try {
            val c = URL("http://127.0.0.1:${s.listeningPort}/api/delete/batch").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write("""{"files":[{"name":"a.mp4"}]}""".toByteArray()) }
            assertEquals(401, c.responseCode)
        } finally { s.stop() }
    }
}
