package castbridge.core.xfer

import castbridge.core.Rig
import castbridge.core.phone.Handoff
import castbridge.core.tv.*
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.*

private const val MiB = 1 shl 20

/**
 * R-08 « Copier et lire ne lance pas la lecture sur la TV avant la fin de la copie », au niveau du VRAI `ReceiverServer` sur boucle locale :
 *  - chemin ordonné (celui que « Copier sur la TV et lire » prend désormais, `CopyRoute`) : pendant l'envoi, `/api/info` montre `received` qui
 *    grandit, le relais du téléphone (`Handoff.copyReady`) devient vrai, `/api/play` démarre le fichier en cours et `/stream/` sert son début ;
 *    la copie qui nourrit la lecture n'est pas bridée ; à la fin, le fichier est complet et identique ;
 *  - chemin rapide (preuve du diagnostic A/B) : pendant l'envoi par blocs, la TV ne montre aucun fichier et refuse `/api/play` et `/stream/`.
 * Toutes les attentes sont bornées (TestWatchdogGuardTest).
 */
class CopyAndPlayHandoffServerTest {
    private val rigs = ArrayList<Rig>()
    private val closers = ArrayList<() -> Unit>()
    private fun rig(): Rig = Rig().also { it.unplug(); rigs += it }
    @AfterTest fun tearDown() { closers.forEach { runCatching { it() } }; rigs.forEach { it.close() } }

    private fun waitFor(ms: Long = 15_000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { assertTrue(System.currentTimeMillis() < end, "délai dépassé : $what"); Thread.sleep(20) }
    }

    /** The phone's file as the classic upload reads it: bytes from [from], held at [gate] (bounded wait), paced while [paced] (a Wi-Fi of ~1.5 Mo/s). */
    private inner class Source(private val data: ByteArray) {
        @Volatile var gate = 0L
        @Volatile var paced = true
        fun at(from: Long): InputStream = object : InputStream() {
            var pos = from
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= data.size) return -1
                val end = System.currentTimeMillis() + 30_000
                while (pos >= gate) { if (System.currentTimeMillis() > end) throw IOException("gate never opened"); Thread.sleep(10) }
                val n = minOf(len.toLong(), gate - pos, data.size - pos, 64L * 1024).toInt()
                System.arraycopy(data, pos.toInt(), b, off, n); pos += n
                if (paced) Thread.sleep(n * 1000L / 1_500_000)
                return n
            }
        }
    }

    private fun file(r: Rig, name: String): TvFile? = TvInfo.parse(r.tv.info()).file(name)

    private fun get(r: Rig, path: String, range: String? = null): Pair<Int, ByteArray> {
        val c = URL(r.base + path).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000; c.readTimeout = 20_000
        range?.let { c.setRequestProperty("Range", it) }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0))
    }

    private fun sha(b: ByteArray) = Hash.hex(MessageDigest.getInstance("SHA-256").digest(b))

    @Test fun duringAnOrderedCopyTheTvSeesTheBytesAndCanStartPlayingBeforeTheEnd() {
        val r = rig()
        val name = "film.mkv"
        val data = Random(8).nextBytes(40 * MiB)
        val total = data.size.toLong()
        val durMs = 1_200_000L                                      // 20 min : ≈ 35 ko/s, 30 s ≈ 1 Mo
        val src = Source(data).apply { gate = 2L * MiB }
        val result = AtomicReference<ResumableUpload.State?>()
        val t = Thread({ result.set(ResumableUpload(name, total, { r.base }, src::at, sleep = { Thread.sleep(minOf(it, 200)) }).run {}) }, "phone-upload")
            .apply { isDaemon = true; start() }
        closers += { src.gate = total; src.paced = false; t.join(10_000) }

        // 1) received is visible and grows while the copy runs
        waitFor(what = "le début du fichier apparaît dans /api/info") { (file(r, name)?.received ?: 0) >= 2L * MiB - 512 * 1024 }
        val first = file(r, name)!!
        assertFalse(first.complete); assertEquals(total, first.size, "la TV connaît la taille finale (.meta)")
        src.gate = 6L * MiB
        waitFor(what = "received grandit") { (file(r, name)?.received ?: 0) >= 6L * MiB - 512 * 1024 }
        val now = file(r, name)!!
        assertTrue(now.received > first.received && now.received < total, "received ${first.received} → ${now.received} / $total")

        // 2) the phone's hand-off becomes true well before the end
        assertTrue(Handoff.copyReady(now.received, total, durMs, 0, false, 1_500_000), "le téléphone passerait la main à ${now.received} / $total octets")

        // 3) the TV starts the growing file and serves its prefix
        val (playCode, playBody) = r.call("POST", "/api/play?name=$name&pos=0")
        assertEquals(200, playCode, playBody)
        assertEquals("playing", r.player.st.state); assertEquals(name, r.player.st.name)
        assertTrue(r.player.lastUrl.orEmpty().contains("/stream/film.mkv"), "le lecteur lit le fichier en cours : ${r.player.lastUrl}")
        val (sc, head) = get(r, "/stream/$name", "bytes=0-${MiB - 1}")
        assertEquals(206, sc)
        assertContentEquals(data.copyOfRange(0, MiB), head, "/stream/ sert le début exact du fichier en cours")
        waitFor(what = "la politique voit une lecture qui grandit") { r.call("GET", "/api/info").second.contains("\"growing\"") }

        // 4) the copy that feeds this very playback is not throttled (a capped copy would take ≥ 5.8 s for these 34 Mio at 6 Mo/s at most)
        val t0 = System.nanoTime()
        src.paced = false; src.gate = total
        t.join(30_000)
        val secs = (System.nanoTime() - t0) / 1e9
        assertEquals(ResumableUpload.State.Done, result.get(), "la copie se termine")
        assertTrue(secs < 4.0, "la copie qui nourrit la lecture a été bridée : ${"%.1f".format(secs)} s pour ${(total - now.received) / MiB} Mio")

        // 5) complete and identical
        waitFor(what = "fichier complet") { file(r, name)?.complete == true }
        assertEquals(total, file(r, name)!!.size)
        val (fc, full) = get(r, "/stream/$name")
        assertEquals(200, fc)
        assertEquals(sha(data), sha(full), "le fichier reçu est identique à l'original")
    }

    @Test fun duringAFastBlockCopyTheTvSeesNothingAndCannotPlay() {
        // preuve du diagnostic : le transfert rapide écrit dans .cbx/<id>.data, invisible à /api/info, /api/play et /stream/ avant `finish`
        val r = rig()
        val name = "rapide.mkv"
        val bs = 4 * MiB
        val (code, body) = r.call("POST", "/api/transfer/begin?name=$name&size=${16L * MiB}&blockSize=$bs")
        assertEquals(200, code, body)
        val m = Manifest(name, 16L * MiB, bs)
        val block = Random(3).nextBytes(bs)
        val conn = HttpConn(HttpConn.tcp("127.0.0.1", r.port)).also { c -> closers += { c.close() } }
        for (idx in 0 until 2) {
            val reply = conn.request("PUT", "/api/transfer/chunk?id=${m.id}&idx=$idx", "127.0.0.1:${r.port}",
                listOf("Content-Type: application/octet-stream", "X-CB-Sha256: ${sha(block)}"), block.size.toLong()) { out ->
                val bb = ByteBuffer.wrap(block); while (bb.hasRemaining()) out.write(bb)
            }
            assertEquals(200, reply.status, reply.body)
        }
        Thread.sleep(1_100)                                           // past the /api/info listing cache
        assertNull(file(r, name), "A : le fichier en cours de transfert rapide n'apparaît pas dans /api/info")
        assertEquals(404, r.call("POST", "/api/play?name=$name&pos=0").first, "B : /api/play ne peut pas lire un fichier qui n'est que dans .cbx")
        assertEquals(404, get(r, "/stream/$name", "bytes=0-1023").first, "B : /stream/ non plus")
    }
}
