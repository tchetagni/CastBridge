package castbridge.core.xfer

import castbridge.core.Rig
import castbridge.core.lots.MemoryQueueStore
import castbridge.core.tv.QueueStatus
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TransferQueueModel
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvInfo
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.*

/**
 * R-09 au niveau du VRAI `ReceiverServer` sur boucle locale : la file du téléphone (règles de `TransferQueueModel`) envoie trois fichiers à la suite
 * par le chemin ordonné (celui de « Copier et lire », R-08), chacun démarrant dès que le précédent est fini (verrou de fin de la TV relâché) ;
 * le fichier dont le nom est déjà pris sur la TV par un autre contenu échoue avec sa cause (NAME_TAKEN), sans rien écraser, et ne bloque pas le suivant.
 */
class CopyQueueServerTest {
    private val rigs = ArrayList<Rig>()
    @AfterTest fun tearDown() { rigs.forEach { it.close() } }

    private fun sha(b: ByteArray) = Hash.hex(MessageDigest.getInstance("SHA-256").digest(b))
    private fun body(r: Rig, name: String): ByteArray {
        val c = URL(r.base + "/stream/" + TvClient.enc(name)).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000; c.readTimeout = 20_000
        assertEquals(200, c.responseCode, name)
        return c.inputStream.use { it.readBytes() }
    }

    @Test fun threeFilesInARowOneNameTakenOneCopyAndPlay() {
        val r = Rig().also { it.unplug(); rigs += it }
        val data = mapOf("a.mkv" to Random(1).nextBytes(3 shl 20), "b.mkv" to Random(2).nextBytes(2 shl 20), "c.mkv" to Random(3).nextBytes(3 shl 20))
        // another content of another size already holds the name b.mkv on the TV
        val other = Random(9).nextBytes(1 shl 20)
        assertEquals(200, r.put("b.mkv", 0, other.size.toLong(), other).first)

        val m = TransferQueueModel(store = MemoryQueueStore())
        val order = ArrayList<String>()
        fun send(item: castbridge.core.tv.QueueItem) {
            val bytes = data.getValue(item.name)
            val res = ResumableUpload(item.name, bytes.size.toLong(), { r.base }, { off -> ByteArrayInputStream(bytes, off.toInt(), bytes.size - off.toInt()) },
                sleep = { Thread.sleep(minOf(it, 100)) }).run {}
            if (res == ResumableUpload.State.Done) m.finish(item.id, true) else m.finish(item.id, false, (res as? ResumableUpload.State.Failed)?.reason)
        }
        val a = m.enqueue("content://a", "a.mkv", data.getValue("a.mkv").size.toLong(), false)
        assertEquals(a.id, m.next()!!.id); m.start(a.id); order += a.name          // a runs while the others are added
        val b = m.enqueue("content://b", "b.mkv", data.getValue("b.mkv").size.toLong(), false)
        val c = m.enqueue("content://c", "c.mkv", data.getValue("c.mkv").size.toLong(), false, playOnTv = true, ordered = true)
        assertTrue(m.admitted(c.id).startsWith("Ajouté à la file : n° 2"), m.admitted(c.id))
        send(m.item(a.id)!!)
        val t0 = System.currentTimeMillis()
        while (true) {
            assertTrue(System.currentTimeMillis() - t0 < 45_000, "la file avance (pas de blocage)")
            val item = m.next() ?: break                     // starts at once after the previous end: the TV's finish lock is released
            m.start(item.id); order += item.name
            send(item)
        }
        assertEquals(listOf("a.mkv", "c.mkv", "b.mkv"), order, "a en cours, puis le « Copier et lire » c, puis la copie b")
        assertEquals(QueueStatus.DONE, m.item(a.id)!!.status); assertEquals(QueueStatus.DONE, m.item(c.id)!!.status)
        assertEquals(QueueStatus.FAILED, m.item(b.id)!!.status)
        assertEquals(ResumableUpload.NAME_TAKEN_TEXT, m.item(b.id)!!.error, "la cause est dite, en français")
        assertFalse(m.busy())

        val info = TvInfo.parse(r.tv.info())
        for (n in listOf("a.mkv", "c.mkv")) {
            assertEquals(true, info.file(n)?.complete, n)
            assertEquals(sha(data.getValue(n)), sha(body(r, n)), "$n identique à l'original")
        }
        assertEquals(sha(other), sha(body(r, "b.mkv")), "rien n'est écrasé sur la TV")
    }
}
