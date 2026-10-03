package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.owner.TrialPolicy
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import kotlin.test.*

/**
 * R-17 : une requête de transfert refusée AVANT d'avoir lu son corps (hôte, essai, jeton, code, session inconnue...) doit laisser le téléphone lire
 * son statut : la TV lit et jette le corps (borné) au lieu de fermer avec des octets non lus (la connexion est alors réinitialisée et le
 * téléphone ne voit que « Broken pipe »).
 */
class EarlyRejectionServerTest {
    private val dir = kotlin.io.path.createTempDirectory("rej").toFile()
    private val logs = java.util.Collections.synchronizedList(ArrayList<String>())
    private var trial = false
    private val server = ReceiverServer(VolumeRegistry.single(File(dir, "tv")), FakePlayer(), 0, pin = "123456", hostCheck = true,
        routeGuard = { p -> if (trial && TrialPolicy.routeBlocked(p)) TrialPolicy.MESSAGE else null },
        tokenAuth = { null }, onLog = { logs += it }).apply { start(5000, false) }
    private val port = server.listeningPort

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    /** Writes [bodyLen] bytes to a PUT like a phone does (whole body before reading), then reads the status line. Null = nothing readable. */
    private fun rawPut(path: String, headers: List<String>, bodyLen: Int, host: String = "127.0.0.1", strictClose: Boolean = false): Int? {
        Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 8000
            val out = s.getOutputStream()
            val head = "PUT $path HTTP/1.1\r\nHost: $host\r\nConnection: keep-alive\r\nContent-Length: $bodyLen\r\n" + headers.joinToString("") { "$it\r\n" } + "\r\n"
            try {
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                val chunk = ByteArray(64 * 1024); var left = bodyLen
                while (left > 0) { val n = minOf(chunk.size, left); out.write(chunk, 0, n); left -= n }
                out.flush()
            } catch (e: IOException) { /* the TV may have answered and closed already: the status is still to be read */ }
            return try {
                val line = StringBuilder()
                val inp = s.getInputStream()
                while (true) { val b = inp.read(); if (b < 0 || b == '\n'.code) break; line.append(b.toChar()) }
                val status = line.toString().split(' ').getOrNull(1)?.toIntOrNull()
                // the TV then closes: a graceful end (FIN) means it read the body; a reset means it closed with bytes unread
                if (status != null && strictClose) { val buf = ByteArray(4096); while (inp.read(buf) >= 0) { } }
                status
            } catch (e: IOException) { null }
        }
    }

    private val pin = "X-CB-Pin: 123456"
    private val chunk = "/api/transfer/chunk?id=abc&idx=0"
    private val body4 = 4 shl 20

    @Test fun trialEditionRefusalIsReadableAfterA4MiBBody() {
        trial = true
        assertEquals(403, rawPut(chunk, listOf(pin, "X-CB-Sha256: ${"0".repeat(64)}"), body4))
        assertEquals(403, rawPut(chunk, listOf(pin), 900_000, strictClose = true), "under the drain cap the TV also closes gracefully")
        assertTrue(File(dir, "tv").walkTopDown().none { it.isFile && it.length() > 0 }, "a refused request writes nothing")
    }

    @Test fun badPinIsReadableAfterA4MiBBody() {
        assertEquals(401, rawPut(chunk, listOf("X-CB-Pin: 000000"), 900_000, strictClose = true))
    }

    @Test fun badTokenIsReadableAfterA4MiBBody() {
        assertEquals(401, rawPut(chunk, listOf("X-CB-Token: nope"), body4))
    }

    @Test fun hostNotAllowedIsReadableAfterA4MiBBody() {
        assertEquals(403, rawPut(chunk, listOf(pin), body4, host = "evil.example.com"))
    }

    @Test fun unknownSessionIsAn404TheClientCanRead() {
        assertEquals(404, rawPut(chunk, listOf(pin, "X-CB-Sha256: ${"0".repeat(64)}"), 900_000, strictClose = true))
    }

    @Test fun everyEarlyRejectionIsLoggedWithoutSecretsAndTheLastFiveAreInInfo() {
        trial = true
        repeat(7) { rawPut("/api/transfer/chunk?id=abc&idx=$it", listOf(pin), 1000) }
        rawPut(chunk, listOf("X-CB-Pin: 654321"), 1000)
        val lines = logs.toList()
        assertTrue(lines.size >= 8, lines.toString())
        assertTrue(lines.all { it.contains("/api/transfer/chunk") && Regex(" 40[0-9]\\b").containsMatchIn(it) }, lines.toString())
        assertTrue(lines.none { it.contains("123456") || it.contains("654321") || it.contains("X-CB") }, "no PIN, token or body in the log")
        trial = false
        val c = URL("http://127.0.0.1:$port/api/info").openConnection() as HttpURLConnection
        c.setRequestProperty("X-CB-Pin", "123456")
        val json = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        val arr = Regex("\"rejections\":\\[(.*?)]").find(json)?.groupValues?.get(1) ?: fail("rejections missing from /api/info: ${json.takeLast(300)}")
        assertEquals(5, Regex("\"route\"").findAll(arr).count(), arr)
        assertFalse(arr.contains("123456") || arr.contains("654321"))
    }
}
