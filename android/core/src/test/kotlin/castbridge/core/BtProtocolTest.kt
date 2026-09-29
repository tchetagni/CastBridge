package castbridge.core

import castbridge.core.tv.*
import java.io.*
import kotlin.random.Random
import kotlin.test.*

class BtProtocolTest {
    private val dir = kotlin.io.path.createTempDirectory("bt").toFile()
    private val data = Random(7).nextBytes(1_000_000)
    private val guard = PinGuard("482913")
    private val results = java.util.concurrent.LinkedBlockingQueue<Any>()

    @AfterTest fun tearDown() { dir.deleteRecursively() }

    private class PipeLink(
        override val input: InputStream, override val output: OutputStream,
        private val onClose: () -> Unit,
    ) : Link { override fun close() = onClose() }

    /** Opens an in-memory "Bluetooth" link served by a TV thread. Client writes fail after [cutAfter] bytes. */
    private fun connect(cutAfter: Long = Long.MAX_VALUE, minFree: Long = 0, peer: String = "AA:BB"): Link {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val tv = Thread {
            try { results.put(BtProtocol.serve(dir, tvIn, s2c, guard, peer, minFree)) }
            catch (e: Exception) { results.put(e) }
            finally { runCatching { s2c.close() }; runCatching { tvIn.close() } }
        }.apply { isDaemon = true; start() }
        var written = 0L
        val out = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (written + len > cutAfter) {
                    val ok = (cutAfter - written).toInt().coerceAtLeast(0)
                    if (ok > 0) c2s.write(b, off, ok)
                    written = cutAfter
                    c2s.close()
                    throw IOException("bluetooth link lost")
                }
                c2s.write(b, off, len); written += len
            }
            override fun flush() = c2s.flush()
        }
        return PipeLink(clIn, out) { runCatching { c2s.close() }; runCatching { clIn.close() }; tv.join(3000) }
    }

    private fun src(off: Long): InputStream = ByteArrayInputStream(data, off.toInt(), data.size - off.toInt())

    @Test fun transfersWholeFileAndRenamesPart() {
        val st = ResumableBtUpload("clip é.mp4", data.size.toLong(), "482913", { connect() }, ::src, sleep = {}).run {}
        assertEquals(ResumableUpload.State.Done, st)
        assertContentEquals(data, File(dir, "clip é.mp4").readBytes())
        assertFalse(File(dir, "clip é.mp4.part").exists())
    }

    @Test fun resumesFromPartAfterLinkLoss() {
        var n = 0
        val states = mutableListOf<ResumableUpload.State>()
        val st = ResumableBtUpload("a.mp4", data.size.toLong(), "482913",
            { if (n++ == 0) connect(cutAfter = 300_000) else connect() }, ::src, sleep = {}).run { states += it }
        assertEquals(ResumableUpload.State.Done, st)
        assertEquals(2, n)
        assertTrue(states.any { it is ResumableUpload.State.Waiting })
        // the 2nd attempt started at the .part size, not at 0
        assertTrue(states.filterIsInstance<ResumableUpload.State.Uploading>().any { it.sent in 1..data.size.toLong() - 1 && it.sent > 0 })
        assertContentEquals(data, File(dir, "a.mp4").readBytes())
    }

    @Test fun resumesAPartFileLeftByAnotherTransport() {
        File(dir, "h.mp4.part").writeBytes(data.copyOf(400_000))   // e.g. started over HTTP
        var start = -1L
        val st = ResumableBtUpload("h.mp4", data.size.toLong(), "482913", { connect() },
            { off -> start = off; src(off) }, sleep = {}).run {}
        assertEquals(ResumableUpload.State.Done, st)
        assertEquals(400_000, start)
        assertContentEquals(data, File(dir, "h.mp4").readBytes())
    }

    @Test fun wrongPinIsFatalAndLocksAfterFiveTries() {
        repeat(4) {
            val st = ResumableBtUpload("x.mp4", 10, "000000", { connect() }, { ByteArrayInputStream(ByteArray(10)) }, sleep = {}).run {}
            assertTrue(st is ResumableUpload.State.Failed && st.reason.contains("PIN"), "$st")
        }
        val locked = ResumableBtUpload("x.mp4", 10, "000000", { connect() }, { ByteArrayInputStream(ByteArray(10)) }, sleep = {}).run {}
        assertTrue(locked is ResumableUpload.State.Failed && locked.reason.contains("verrouill"), "$locked")
        assertFalse(File(dir, "x.mp4").exists())
    }

    @Test fun badNameAndNoSpaceAreFatal() {
        val bad = ResumableBtUpload("../evil", 10, "482913", { connect() }, { ByteArrayInputStream(ByteArray(10)) }, sleep = {}).run {}
        assertTrue(bad is ResumableUpload.State.Failed, "$bad")
        val full = ResumableBtUpload("big.mp4", 10, "482913", { connect(minFree = Long.MAX_VALUE / 2) },
            { ByteArrayInputStream(ByteArray(10)) }, sleep = {}).run {}
        assertTrue(full is ResumableUpload.State.Failed && full.reason.contains("espace"), "$full")
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".mp4") })
    }

    @Test fun alreadyCompleteFileIsAccepted() {
        File(dir, "d.mp4").writeBytes(data)
        val st = ResumableBtUpload("d.mp4", data.size.toLong(), "482913", { connect() }, { error("must not read") }, sleep = {}).run {}
        assertEquals(ResumableUpload.State.Done, st)
    }

    @Test fun garbageIsRejectedWithoutTouchingDisk() {
        val l = connect()
        l.output.write("HELLOHELLOHELLO".toByteArray()); l.output.flush()
        assertEquals(BtProtocol.ERR_MAGIC, l.input.read())
        l.close()
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
}
