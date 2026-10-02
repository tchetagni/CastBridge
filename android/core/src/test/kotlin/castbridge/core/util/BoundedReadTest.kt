package castbridge.core.util

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.*

/** The bounded read that replaces readNBytes / readAllBytes (not available below Android API 33). */
class BoundedReadTest {
    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun readsEverythingUpToTheLimitIncludingExactlyTheLimit() {
        assertContentEquals(ByteArray(0), BoundedRead.readAll(ByteArrayInputStream(ByteArray(0)), 10))
        assertContentEquals(bytes(5), BoundedRead.readAll(ByteArrayInputStream(bytes(5)), 10))
        assertContentEquals(bytes(10), BoundedRead.readAll(ByteArrayInputStream(bytes(10)), 10))
        assertContentEquals(bytes(20_000), BoundedRead.readAll(ByteArrayInputStream(bytes(20_000)), 20_000))   // several internal chunks
        assertContentEquals(ByteArray(0), BoundedRead.readAll(ByteArrayInputStream(ByteArray(0)), 0))
    }

    @Test fun throwsAsSoonAsTheLimitIsExceeded() {
        val e = assertFailsWith<BoundedRead.TooLarge> { BoundedRead.readAll(ByteArrayInputStream(bytes(11)), 10) }
        assertTrue(e is IOException)
        assertFailsWith<BoundedRead.TooLarge> { BoundedRead.readAll(ByteArrayInputStream(bytes(20_001)), 20_000) }
        assertFailsWith<BoundedRead.TooLarge> { BoundedRead.readAll(ByteArrayInputStream(bytes(1)), 0) }
        assertFailsWith<IllegalArgumentException> { BoundedRead.readAll(ByteArrayInputStream(bytes(1)), -1) }
    }

    @Test fun anEndlessStreamNeverFillsTheMemory() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int { served++; return 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; java.util.Arrays.fill(b, off, off + len, 1); return len }
        }
        assertFailsWith<BoundedRead.TooLarge> { BoundedRead.readAll(endless, 100_000) }
        assertTrue(served <= 100_000 + 8192, "stopped right after the limit: $served")
    }

    @Test fun handlesShortReadsAndDoesNotCloseTheStream() {
        val data = bytes(1000); var i = 0; var closed = false
        val slow = object : InputStream() {
            override fun read(): Int = if (i < data.size) data[i++].toInt() and 0xff else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int { if (i >= data.size) return -1; val n = minOf(len, 7, data.size - i); System.arraycopy(data, i, b, off, n); i += n; return n }
            override fun close() { closed = true }
        }
        assertContentEquals(data, BoundedRead.readAll(slow, 1000)); assertFalse(closed)
    }

    @Test fun readUpToTruncatesInsteadOfThrowing() {
        assertContentEquals(bytes(10), BoundedRead.readUpTo(ByteArrayInputStream(bytes(50)), 10))
        assertContentEquals(bytes(5), BoundedRead.readUpTo(ByteArrayInputStream(bytes(5)), 10))
        assertContentEquals(bytes(20_000), BoundedRead.readUpTo(ByteArrayInputStream(bytes(30_000)), 20_000))
        assertContentEquals(ByteArray(0), BoundedRead.readUpTo(ByteArrayInputStream(bytes(5)), 0))
        val s = ByteArrayInputStream(bytes(50)); BoundedRead.readUpTo(s, 10); assertEquals(40, s.available())   // never reads past the limit
    }
}
