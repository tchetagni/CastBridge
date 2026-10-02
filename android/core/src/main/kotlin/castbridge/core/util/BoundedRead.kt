package castbridge.core.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Bounded read of a stream, usable on every Android level (replaces `InputStream.readNBytes(int)` / `readAllBytes()`, JDK 11 / Android API 33 only: the reference
 * TV is Android 9, API 28). Reads in a loop and throws as soon as more than [max] bytes would be needed, so a hostile or broken source can never fill the memory.
 */
object BoundedRead {
    class TooLarge(val max: Long) : IOException("flux plus gros que la limite de $max octets")

    /** All the bytes of [input], at most [max]; [TooLarge] (an IOException) as soon as the stream holds more. The stream is not closed. */
    fun readAll(input: InputStream, max: Int): ByteArray {
        require(max >= 0) { "limite négative" }
        val out = ByteArrayOutputStream(minOf(max, 8192).coerceAtLeast(16))
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw TooLarge(max.toLong())
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** The first [max] bytes of [input] at most (what `readNBytes(max)` returns): reads until [max] bytes or the end, and silently leaves the rest unread. */
    fun readUpTo(input: InputStream, max: Int): ByteArray {
        require(max >= 0) { "limite négative" }
        val out = ByteArrayOutputStream(minOf(max, 8192).coerceAtLeast(16))
        val buf = ByteArray(8192)
        var left = max
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n < 0) break
            out.write(buf, 0, n); left -= n
        }
        return out.toByteArray()
    }
}
