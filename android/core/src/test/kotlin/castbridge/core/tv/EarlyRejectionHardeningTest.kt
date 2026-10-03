package castbridge.core.tv

import castbridge.core.FakePlayer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import kotlin.test.*

/** R-17 (audit) : bornes du vidage, anneau des refus, keep-alive après « already », 503 pour un bloc interrompu. */
class EarlyRejectionHardeningTest {
    private val dir = kotlin.io.path.createTempDirectory("rejh").toFile()
    private val logs = java.util.Collections.synchronizedList(ArrayList<String>())
    private val server = ReceiverServer(VolumeRegistry.single(File(dir, "tv")), FakePlayer(), 0, pin = "123456", hostCheck = true,
        tokenAuth = { null }, onLog = { logs += it }).apply { start(5000, false) }
    private val port = server.listeningPort
    private val pin = "X-CB-Pin: 123456"
    private val chunk = "/api/transfer/chunk?id=abc&idx=0"

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private class Conn(port: Int, host: String = "127.0.0.1") : AutoCloseable {
        val s = Socket("127.0.0.1", port).apply { soTimeout = 8000 }
        private val inp = s.getInputStream()
        private val hostName = host
        fun send(method: String, path: String, headers: List<String>, body: ByteArray = ByteArray(0), declared: Long = body.size.toLong()) {
            val head = "$method $path HTTP/1.1\r\nHost: $hostName\r\nConnection: keep-alive\r\nContent-Length: $declared\r\n" + headers.joinToString("") { "$it\r\n" } + "\r\n"
            s.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1)); s.getOutputStream().write(body); s.getOutputStream().flush()
        }
        private fun line(): String? {
            val sb = StringBuilder()
            while (true) { val b = inp.read(); if (b < 0) return if (sb.isEmpty()) null else sb.toString(); if (b == '\n'.code) return sb.toString().trimEnd('\r'); sb.append(b.toChar()) }
        }
        fun reply(): Pair<Int, String>? {
            val st = line()?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: return null
            var len = 0
            while (true) { val l = line() ?: break; if (l.isEmpty()) break; if (l.startsWith("content-length:", true)) len = l.substringAfter(':').trim().toInt() }
            val b = ByteArray(len); var n = 0
            while (n < len) { val r = inp.read(b, n, len - n); if (r < 0) break; n += r }
            return st to String(b, 0, n)
        }
        fun drip(everyMs: Long, times: Int) = Thread {
            try { repeat(times) { s.getOutputStream().write(1); Thread.sleep(everyMs) } } catch (e: IOException) { } catch (e: InterruptedException) { }
        }.apply { isDaemon = true; start() }
        fun waitClosed(): Long { val t0 = System.nanoTime(); try { while (inp.read() >= 0) { } } catch (e: IOException) { }; return (System.nanoTime() - t0) / 1_000_000 }
        override fun close() { runCatching { s.close() } }
    }

    private fun info(): String {
        val c = URL("http://127.0.0.1:$port/api/info").openConnection() as HttpURLConnection
        c.setRequestProperty("X-CB-Pin", "123456")
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun begin(c: Conn): String {
        c.send("POST", "/api/transfer/begin?name=k.bin&size=1048576&blockSize=1048576", listOf(pin))
        val (st, body) = c.reply()!!
        assertEquals(200, st, body)
        return Regex("\"id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun sha(b: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test fun loggedRouteIsSanitisedAndOnlyBodyRequestsFillTheRing() {
        repeat(12) { i -> Conn(port, "evil.example.com").use { c -> c.send("GET", "/zz$i", emptyList()); c.reply() } }
        assertTrue(info().contains("\"rejections\":[]"), "des GET de scanner ne remplissent pas l'anneau")
        Conn(port).use { c ->
            c.send("PUT", "/api/transfer/ch%0Aunk%20x.bin?id=1", listOf(pin), ByteArray(100)); c.reply()
        }
        val line = logs.single()
        assertFalse(line.contains('\n') || line.contains('\r'), "no line injection: $line")
        assertTrue(line.startsWith("refus PUT /api/transfer/ch_unk_x_bin "), line)
        val arr = Regex("\"rejections\":\\[(.*?)]").find(info())!!.groupValues[1]
        assertFalse(arr.contains("\\n") || arr.contains("x.bin"), arr)
    }

    @Test fun theConnectionStaysUsableAfterAnAlreadyAnswer() {
        val data = ByteArray(1 shl 20) { (it * 7).toByte() }
        Conn(port).use { c ->
            val id = begin(c)
            val h = listOf(pin, "X-CB-Sha256: ${sha(data)}", "Content-Type: application/octet-stream")
            c.send("PUT", "/api/transfer/chunk?id=$id&idx=0", h, data)
            assertEquals(200, c.reply()!!.first)
            c.send("PUT", "/api/transfer/chunk?id=$id&idx=0", h, data)
            val (st, body) = c.reply()!!
            assertEquals(200, st); assertTrue("already" in body, body)
            c.send("GET", "/api/hello", emptyList())
            assertEquals(200, c.reply()?.first, "the next request on the same socket works: the unread body was read")
        }
    }

    @Test fun peersAreNotHeldBeyondTheDrainDeadline() {
        Conn(port).use { c ->       // unauthenticated refusal: 500 ms at most, even if the peer drips its body
            c.send("PUT", chunk, listOf("X-CB-Pin: 000000"), ByteArray(10), declared = 8L shl 20)
            assertEquals(401, c.reply()!!.first)
            val t = c.drip(50, 100)
            assertTrue(c.waitClosed() < 1500, "closed within the unauthenticated deadline")
            t.interrupt()
        }
        Conn(port).use { c ->       // authenticated refusal (unknown session): 2 s
            c.send("PUT", chunk, listOf(pin), ByteArray(10), declared = 8L shl 20)
            assertEquals(404, c.reply()!!.first)
            val t = c.drip(50, 200)
            val ms = c.waitClosed()
            assertTrue(ms < 3500, "closed within ~2 s ($ms)")
            t.interrupt()
        }
    }

    @Test fun aLockedPeerIsNeverRead() {
        repeat(5) { i -> Conn(port).use { c -> c.send("PUT", chunk, listOf("X-CB-Pin: 00000$i"), ByteArray(10)); c.reply() } }      // the 5th failure locks this address
        Conn(port).use { c ->
            c.send("PUT", chunk, listOf("X-CB-Pin: 000000"), ByteArray(10), declared = 8L shl 20)
            val (st, body) = c.reply()!!
            assertEquals(401, st); assertTrue("locked" in body, body)
            val t = c.drip(50, 100)
            assertTrue(c.waitClosed() < 300, "no read at all for a locked peer")
            t.interrupt()
        }
    }

    @Test fun onlyOneDrainAtATimePerAddress() {
        val a = Conn(port); val b = Conn(port)
        try {
            a.send("PUT", chunk, listOf("X-CB-Pin: 000000"), ByteArray(10), declared = 8L shl 20)
            assertEquals(401, a.reply()!!.first)
            val ta = a.drip(40, 100)
            Thread.sleep(100)
            b.send("PUT", chunk, listOf("X-CB-Pin: 000001"), ByteArray(10), declared = 8L shl 20)
            assertEquals(401, b.reply()!!.first)
            val tb = b.drip(40, 100)
            assertTrue(b.waitClosed() < 350, "the second connection of the same address is not drained while the first is")
            ta.interrupt(); tb.interrupt()
        } finally { a.close(); b.close() }
    }
}

/** A TV whose read timeout is short: a stalled phone makes the block « interrupted », which must be a retry (503), never a fatal 400. */
class InterruptedBlockServerTest {
    private val dir = kotlin.io.path.createTempDirectory("intr").toFile()
    private val server = ReceiverServer(VolumeRegistry.single(File(dir, "tv")), FakePlayer(), 0, pin = "123456", hostCheck = false).apply { start(700, false) }

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun aStalledBlockIsAnswered503WithRetryNever400() {
        val s = Socket("127.0.0.1", server.listeningPort).apply { soTimeout = 8000 }
        fun send(m: String, path: String, declared: Long, body: ByteArray = ByteArray(0)) {
            s.getOutputStream().write(("$m $path HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: $declared\r\nX-CB-Pin: 123456\r\nX-CB-Sha256: ${"a".repeat(64)}\r\n\r\n").toByteArray())
            s.getOutputStream().write(body); s.getOutputStream().flush()
        }
        val inp = s.getInputStream()
        fun read(): Pair<Int, String> {
            val sb = StringBuilder(); val buf = ByteArray(4096)
            while (true) {
                val n = inp.read(buf); if (n < 0) break
                sb.append(String(buf, 0, n))
                val i = sb.indexOf("\r\n\r\n")
                if (i >= 0) {
                    val len = Regex("content-length: *(\\d+)", RegexOption.IGNORE_CASE).find(sb)?.groupValues?.get(1)?.toInt() ?: 0
                    if (sb.length >= i + 4 + len) break
                }
            }
            val t = sb.toString()
            return t.substringAfter(' ').substringBefore(' ').toInt() to t
        }
        send("POST", "/api/transfer/begin?name=k.bin&size=1048576&blockSize=1048576", 0)
        val (st, b) = read()
        assertEquals(200, st, b)
        val id = Regex("\"id\":\"([^\"]+)\"").find(b)!!.groupValues[1]
        send("PUT", "/api/transfer/chunk?id=$id&idx=0", 1L shl 20, ByteArray(100 * 1024))      // then silence
        val (st2, b2) = read()
        assertEquals(503, st2, b2)
        assertTrue("interrupted" in b2 && "\"retry\":true" in b2 && "retryMs" in b2, b2)
        s.close()
    }
}
