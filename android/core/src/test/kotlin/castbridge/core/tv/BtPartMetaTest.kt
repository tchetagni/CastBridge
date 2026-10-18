package castbridge.core.tv

import castbridge.core.Rig
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

/**
 * R-54 (suite, côté TV) : la réception Bluetooth écrit le même `<nom>.meta` que l'envoi HTTP (la taille totale, avant le premier octet), et le retire quand le fichier est complet.
 * Sans lui, `GET /api/part?size=` répond PART_OTHER pour une copie COMMENCÉE en Bluetooth ([FixW1505ServerTest.aPartWithoutMetaIsNeverResumedForAKnownSize]) : le téléphone ne pouvait
 * pas la reprendre en Wi-Fi quand le Wi-Fi revenait. Ici, la vraie `ReceiverServer` (Rig : mémoire interne et clé USB) et la vraie `BtProtocol.serve` sur des flux en mémoire, dans le
 * même dossier (le Bluetooth écrit dans la mémoire interne, `TvService.videosDir`).
 */
class BtPartMetaTest {
    private val r = Rig()
    private val dir = r.internalDir
    @AfterTest fun tearDown() = r.close()

    private val pin = "482913"
    private val total = 12_000
    private val data = Random(54).nextBytes(total)

    /** Une liaison Bluetooth en mémoire servie par un fil de la TV ; rend (entrée du téléphone, sortie du téléphone, résultat de la TV). */
    private fun link(): Triple<InputStream, OutputStream, LinkedBlockingQueue<Any>> {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val res = LinkedBlockingQueue<Any>()
        thread(isDaemon = true) {
            try { res.put(BtProtocol.serve(dir, tvIn, s2c, null, "AA:BB", 0)) } catch (e: Exception) { res.put(e) }
            finally { runCatching { s2c.close() } }
        }
        return Triple(clIn, c2s, res)
    }

    /**
     * Le téléphone envoie [content] sous [name] avec la taille annoncée [announce]. [cutAfter] : la liaison casse après ce nombre d'octets DU FICHIER (la TV garde ce qui est arrivé).
     * [atReady] reçoit le décalage annoncé par la TV (juste avant le premier octet). Rend le décalage annoncé.
     */
    private fun send(name: String, content: ByteArray = data, announce: Long = content.size.toLong(), cutAfter: Int? = null, atReady: (Long) -> Unit = {}): Long {
        val (i, o, res) = link()
        val limit = 4 + 6 + 2 + name.toByteArray().size + 8 + (cutAfter ?: Int.MAX_VALUE / 2)       // MAGIC + PIN + longueur du nom + nom + taille, puis le fichier
        val out = object : OutputStream() {
            var n = 0
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (n + len > limit) { val k = (limit - n).coerceAtLeast(0); o.write(b, off, k); n += k; o.close(); throw IOException("lost") }
                o.write(b, off, len); n += len
            }
            override fun flush() = o.flush()
        }
        var offset = -1L
        val attempt = runCatching {
            BtProtocol.send(i, out, name, announce, pin, { off -> ByteArrayInputStream(content, off.toInt(), content.size - off.toInt()) }, onOffset = { offset = it; atReady(it) })
        }
        val tv = res.poll(10, TimeUnit.SECONDS)
        if (cutAfter != null) { assertTrue(attempt.exceptionOrNull() is IOException, "la liaison a cassé"); assertTrue(tv is IOException, "la TV a vu la liaison casser : $tv") }
        else { attempt.getOrThrow(); assertEquals(BtProtocol.OK, tv) }
        return offset
    }

    private fun leftovers(name: String) = dir.walkTopDown().filter { it.isFile && (it.name == "$name.part" || it.name == "$name.meta") }.map { it.name }.toList()

    @Test fun aCopyStartedOverBluetoothGoesOnOverWifiFromTheByteReceived() {
        send("bt.mp4", cutAfter = 5_000)
        assertEquals(5_000, File(dir, "bt.mp4.part").length(), "ce qui est arrivé est gardé")
        assertEquals(total.toLong(), Meta.read(dir, "bt.mp4"), "le même .meta que l'envoi HTTP : la taille totale")
        // ce que le téléphone demande avant son premier octet Wi-Fi
        val p = r.tv.part("bt.mp4", total.toLong())
        assertNull(p.code, "reprise possible : plus de PART_OTHER pour une copie commencée en Bluetooth")
        assertEquals(5_000, p.length, "le décalage de reprise = les octets reçus par Bluetooth")
        assertFalse(p.done)
        // et la TV la montre comme n'importe quelle copie reçue en partie (R-51)
        r.server.changed()
        assertEquals(listOf("bt.mp4" to 5_000L), r.server.partials().map { it.name to it.received }, "visible dans la liste des copies reçues en partie")
        // le Wi-Fi finit la copie, à l'octet reçu
        val (code, body) = r.put("bt.mp4", 5_000, total.toLong(), data.copyOfRange(5_000, total))
        assertEquals(200, code, body)
        val done = r.tv.part("bt.mp4", total.toLong())
        assertTrue(done.done && done.length == total.toLong(), "fichier complet sur la TV : $done")
        assertContentEquals(data, dir.walkTopDown().first { it.isFile && it.name == "bt.mp4" }.readBytes())
        assertEquals(emptyList(), leftovers("bt.mp4"), "ni .part ni .meta une fois le fichier complet")
    }

    @Test fun aCopyOfAnotherSizeIsRefusedAsBefore() {
        send("bt.mp4", cutAfter = 5_000)
        val other = 20_000L
        assertEquals("PART_OTHER", r.tv.part("bt.mp4", other).code, "une autre taille = un autre contenu : jamais repris")
        val (code, body) = r.put("bt.mp4", 5_000, other, ByteArray(100))
        assertEquals(409, code, body); assertTrue(body.contains("PART_OTHER"), body)
        assertEquals(5_000, File(dir, "bt.mp4.part").length(), "rien n'a été ajouté à la copie")
        assertEquals(total.toLong(), Meta.read(dir, "bt.mp4"))
    }

    @Test fun theSizeSidecarIsWrittenBeforeTheFirstByteAndRemovedWhenTheFileIsWhole() {
        var atReady: Long? = null
        val offset = send("whole.mp4", atReady = { atReady = Meta.read(dir, "whole.mp4") })
        assertEquals(0L, offset)
        assertEquals(total.toLong(), atReady, "le .meta existe déjà quand la TV dit « prêt » (avant le premier octet)")
        assertContentEquals(data, File(dir, "whole.mp4").readBytes())
        assertEquals(emptyList(), leftovers("whole.mp4"), "fichier complet : plus de .part ni de .meta")
    }

    @Test fun aSidecarOfAnotherTotalMeansAnotherContentAndRestartsFromZero() {
        File(dir, "other.mp4.part").writeBytes(ByteArray(3_000) { 9 }); Meta.write(dir, "other.mp4", 9_000)      // la copie d'un autre contenu, de 9 000 octets
        val offset = send("other.mp4")
        assertEquals(0L, offset, "jamais d'octets ajoutés à la copie d'un autre contenu (comme le 409 PART_OTHER du HTTP)")
        assertContentEquals(data, File(dir, "other.mp4").readBytes())
        assertEquals(emptyList(), leftovers("other.mp4"))
    }

    @Test fun theSameSizeResumesAndAPartWithoutSidecarIsAdopted() {
        File(dir, "same.mp4.part").writeBytes(data.copyOf(3_000)); Meta.write(dir, "same.mp4", total.toLong())
        assertEquals(3_000L, send("same.mp4", cutAfter = 2_000), "même taille : la reprise continue où elle en était")
        assertEquals(5_000, File(dir, "same.mp4.part").length())
        // une copie Bluetooth plus ancienne, sans .meta (avant ce correctif) : le Bluetooth la reprend comme avant ET lui donne son .meta, donc le Wi-Fi peut la reprendre
        File(dir, "old.mp4.part").writeBytes(data.copyOf(4_000))
        assertNull(Meta.read(dir, "old.mp4"))
        assertEquals(4_000L, send("old.mp4", cutAfter = 1_000))
        assertEquals(total.toLong(), Meta.read(dir, "old.mp4"))
        assertNull(r.tv.part("old.mp4", total.toLong()).code, "reprenable en Wi-Fi")
        assertEquals(5_000, r.tv.part("old.mp4", total.toLong()).length)
    }
}
