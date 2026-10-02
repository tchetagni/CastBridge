package castbridge.core.free

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.test.*

class FreeDownloadTest {
    private val data = ByteArray(300_000) { (it * 31 % 251).toByteArray0() }
    private fun Int.toByteArray0() = toByte()
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun info(sha: String = sha(data), size: Long = data.size.toLong(), av: Boolean = true) = FreeContentInfo(av, size, sha, "2026-10-01", "CC BY-SA 4.0", "a.zip")
    private fun tmp(): File = File.createTempFile("free", ".part").also { it.delete(); it.deleteOnExit() }

    private class Fake(val payload: ByteArray, val honourRange: Boolean = true, val cutAfter: Int = -1, val status416: Boolean = false) : FreeTransport {
        val ranges = ArrayList<Long>(); var calls = 0
        override fun info() = ""
        override fun archive(rangeStart: Long): FreeResponse {
            calls++; ranges += rangeStart
            if (status416 && rangeStart > 0) return FreeResponse(416, null)
            val from = if (honourRange) rangeStart.toInt() else 0
            val bytes = payload.copyOfRange(from, payload.size)
            val body: InputStream = if (cutAfter >= 0 && calls == 1) object : InputStream() {
                var i = 0
                override fun read(): Int { if (i >= cutAfter) throw IOException("reset"); return bytes[i++].toInt() and 255 }
            } else ByteArrayInputStream(bytes)
            return FreeResponse(if (honourRange && rangeStart > 0) 206 else 200, body)
        }
    }

    @Test fun parsesInfo() {
        val i = FreeContentInfo.parse("""{"available":true,"sizeBytes":1234,"sha256":"${"a".repeat(64)}","generatedAt":"2026-10-01T10:00:00Z","licence":"CC BY-SA 4.0","fileName":"../x y.zip"}""")
        assertTrue(i.downloadable); assertEquals(1234, i.sizeBytes); assertEquals("x_y.zip", i.fileName)
    }
    @Test fun infoNotPublished() {
        val i = FreeContentInfo.parse("""{"available":false}""")
        assertFalse(i.downloadable); assertEquals("castbridge-contenus-libres.zip", i.fileName)
        assertTrue(DownloadPlan.run(Fake(data), i, tmp()) is FreeOutcome.Failed)
        assertFailsWith<IllegalArgumentException> { FreeContentInfo.parse("<html>") }
        assertFailsWith<IllegalArgumentException> { FreeContentInfo.parse("""{"available":true,"sizeBytes":5,"sha256":"zz"}""") }
    }
    @Test fun sizes() {
        assertEquals("1,5 Mo", FreeSizes.format(1_572_864)); assertEquals("2 Ko", FreeSizes.format(2048)); assertEquals(50, FreeSizes.percent(5, 10))
        assertTrue(FreeSizes.askBeforeMobile(21L * 1024 * 1024, true)); assertFalse(FreeSizes.askBeforeMobile(21L * 1024 * 1024, false)); assertFalse(FreeSizes.askBeforeMobile(1000, true))
    }
    @Test fun fullDownload() {
        val p = tmp(); var last = 0L
        val r = DownloadPlan.run(Fake(data), info(), p, { d, _ -> last = d })
        assertTrue(r is FreeOutcome.Done); assertEquals(data.size.toLong(), last); assertContentEquals(data, p.readBytes())
    }
    @Test fun resumesFromPartial() {
        val p = tmp(); p.writeBytes(data.copyOf(100_000)); val f = Fake(data)
        assertTrue(DownloadPlan.run(f, info(), p) is FreeOutcome.Done)
        assertEquals(listOf(100_000L), f.ranges); assertContentEquals(data, p.readBytes())
    }
    @Test fun interruptionKeepsPartThenResumes() {
        val p = tmp(); val f = Fake(data, cutAfter = 50_000)
        val r = DownloadPlan.run(f, info(), p)
        assertTrue(r is FreeOutcome.Interrupted); assertEquals(50_000L, p.length())
        assertTrue(DownloadPlan.run(f, info(), p) is FreeOutcome.Done); assertEquals(listOf(0L, 50_000L), f.ranges); assertContentEquals(data, p.readBytes())
    }
    @Test fun serverIgnoringRangeRestarts() {
        val p = tmp(); p.writeBytes(data.copyOf(10_000))
        assertTrue(DownloadPlan.run(Fake(data, honourRange = false), info(), p) is FreeOutcome.Done); assertContentEquals(data, p.readBytes())
    }
    @Test fun range416Restarts() {
        val p = tmp(); p.writeBytes(data.copyOf(10_000)); val f = Fake(data, status416 = true)
        assertTrue(DownloadPlan.run(f, info(), p) is FreeOutcome.Done); assertEquals(listOf(10_000L, 0L), f.ranges)
    }
    @Test fun completePartOnlyVerified() {
        val p = tmp(); p.writeBytes(data); val f = Fake(data)
        assertTrue(DownloadPlan.run(f, info(), p) is FreeOutcome.Done); assertEquals(0, f.calls)
    }
    @Test fun oversizedPartRestarts() {
        val p = tmp(); p.writeBytes(ByteArray(data.size + 5))
        assertTrue(DownloadPlan.run(Fake(data), info(), p) is FreeOutcome.Done); assertContentEquals(data, p.readBytes())
    }
    @Test fun checksumMismatchRetriesOnceThenFails() {
        val p = tmp(); val f = Fake(data)
        val r = DownloadPlan.run(f, info(sha = "0".repeat(64)), p)
        assertTrue(r is FreeOutcome.Failed); assertEquals(DownloadPlan.MISMATCH, r.message); assertEquals(2, f.calls); assertFalse(p.exists())
    }
    @Test fun mismatchThenGoodSucceeds() {
        val p = tmp(); p.writeBytes(ByteArray(100_000) { 7 }) // corrupt partial: resumed, mismatch, fresh download ok
        val f = Fake(data); val r = DownloadPlan.run(f, info(), p)
        assertTrue(r is FreeOutcome.Done); assertEquals(listOf(100_000L, 0L), f.ranges)
    }
    @Test fun cancellationKeepsPart() {
        val p = tmp(); var n = 0
        val r = DownloadPlan.run(Fake(data), info(), p, cancelled = { ++n > 3 })
        assertTrue(r is FreeOutcome.Cancelled); assertTrue(p.exists() && p.length() in 1 until data.size)
    }
    @Test fun unreachableIsInterrupted() {
        val t = object : FreeTransport { override fun info() = ""; override fun archive(rangeStart: Long): FreeResponse = throw IOException("no route") }
        assertTrue(DownloadPlan.run(t, info(), tmp()) is FreeOutcome.Interrupted)
    }
}
