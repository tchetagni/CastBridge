package castbridge.core.xfer

import java.io.IOException
import java.net.ServerSocket
import java.net.SocketException
import kotlin.test.*

/** R-17 : « broken pipe » sans fin. La décision (pure) et la lecture de secours de la réponse de la TV. */
class WriteFailureClassifierTest {
    private val peerTexts = listOf("Broken pipe", "Connection reset", "Connection reset by peer", "Software caused connection abort", "Broken pipe (Write failed)", "an established connection was aborted by the software in your host machine")

    @Test fun everyPeerCloseTextIsRecognised() {
        for (t in peerTexts) assertTrue(WriteFailureClassifier.isPeerClose(SocketException(t)), t)
        assertTrue(WriteFailureClassifier.isPeerClose(java.nio.channels.ClosedChannelException()))
    }

    @Test fun otherErrorsAreNotPeerClose() {
        for (e in listOf(IOException("cancelled"), IOException("source ended early"), java.net.SocketTimeoutException("Read timed out"), IOException("No space left on device")))
            assertFalse(WriteFailureClassifier.isPeerClose(e), e.message)
    }

    @Test fun aReplyReadAfterTheFailureIsClassifiedWithTheSameTableAsTheLanes() {
        val e = SocketException("Broken pipe")
        fun fatal(o: Outcome) = (o as Outcome.Failed).also { assertTrue(it.fatal, it.reason) }.reason
        assertTrue(fatal(WriteFailureClassifier.classify(e, 401)).contains("autorisation refusée par la TV (401)"))
        assertTrue(fatal(WriteFailureClassifier.classify(e, 403)).contains("autorisation refusée par la TV (403)"))
        assertTrue(fatal(WriteFailureClassifier.classify(e, 413)).contains("la TV n'a plus de place (413)"))
        assertTrue(fatal(WriteFailureClassifier.classify(e, 507)).contains("la TV n'a plus de place (507)"))
        assertTrue(fatal(WriteFailureClassifier.classify(e, 400, "{\"error\":\"bad hash\"}")).contains("bad hash"))
        assertTrue(WriteFailureClassifier.classify(e, 404) === Outcome.SessionLost)
        assertTrue(WriteFailureClassifier.classify(e, 422) is Outcome.Corrupt)
        val busy = WriteFailureClassifier.classify(e, 429, "{\"retryMs\":750}")
        assertEquals(750L, (busy as Outcome.Busy).retryMs)
        assertTrue(WriteFailureClassifier.classify(e, 200, "{\"already\":true}") === Outcome.Already)
        assertEquals(5L, (WriteFailureClassifier.classify(e, 200, "{}", 5) as Outcome.Ok).bytes)
        val other = WriteFailureClassifier.classify(e, 503) as Outcome.Failed
        assertFalse(other.fatal)
    }

    @Test fun noReplyStaysTransientAndKeepsTheExactExceptionClass() {
        for (t in peerTexts) {
            val o = WriteFailureClassifier.classify(SocketException(t), null) as Outcome.Failed
            assertFalse(o.fatal, t)
            assertTrue(o.reason.contains(t) && o.reason.contains("SocketException"), o.reason)
        }
        val o = WriteFailureClassifier.classify(IOException(), null) as Outcome.Failed
        assertTrue(o.reason.contains("IOException") && !o.fatal, o.reason)
    }
}

class StuckDetectorTest {
    private val min = 60_000L
    @Test fun sameReasonSixTimesOverThreeMinutesTrips() {
        val d = StuckDetector()
        var tripped = false
        for (i in 0 until 6) tripped = d.onFailure("wifi", "Broken pipe (SocketException)", i * 25_000L) || tripped
        assertFalse(tripped, "6 fois mais seulement 125 s : pas encore")
        assertFalse(d.onFailure("wifi", "Broken pipe (SocketException)", 179_000))
        assertTrue(d.onFailure("wifi", "Broken pipe (SocketException)", 181_000))
    }

    @Test fun fewerThanSixNeverTripsHoweverLong() {
        val d = StuckDetector()
        for (i in 0 until 5) assertFalse(d.onFailure("wifi", "x", i * 10 * min))
    }

    @Test fun progressResetsTheCounter() {
        val d = StuckDetector()
        for (i in 0 until 5) d.onFailure("wifi", "x", i * 40_000L)
        d.onProgress()
        assertFalse(d.onFailure("wifi", "x", 400_000))
        for (i in 1 until 5) assertFalse(d.onFailure("wifi", "x", 400_000 + i * 40_000L))
        assertTrue(d.onFailure("wifi", "x", 400_000 + 5 * 40_000L))
    }

    @Test fun aDifferentReasonRestartsTheStreakAndLanesAreIndependent() {
        val d = StuckDetector()
        for (i in 0 until 5) d.onFailure("wifi", "a", i * 50_000L)
        assertFalse(d.onFailure("wifi", "b", 300_000), "autre raison : série recommencée")
        for (i in 0 until 10) assertFalse(d.onFailure("bluetooth", "z$i", 300_000L + i), "raisons toutes différentes : jamais")
        val e = StuckDetector(maxRepeats = 2, minSpanMs = 1000)
        assertFalse(e.onFailure("wifi", "a", 0)); assertFalse(e.onFailure("bluetooth", "a", 5000)); assertTrue(e.onFailure("wifi", "a", 6000))
    }

    @Test fun theFrenchMessage() {
        val unknown = "La TV ferme la connexion pendant l'envoi (cause inconnue) : vérifiez la TV puis relancez"
        assertEquals(unknown, StuckDetector.message("Broken pipe (SocketException)"))
        assertEquals(unknown, StuckDetector.message("Connection reset (SocketException)"))
        val m = StuckDetector.message("TV : 503 {}")
        assertTrue(m.startsWith("La TV n'accepte pas l'envoi") && m.contains("503") && m.endsWith("vérifiez la TV puis relancez"), m)
    }
}

/** La lecture de secours : la TV a répondu puis fermé avant la fin de l'écriture. */
class HttpConnSalvageTest {
    /** A one-shot server: reads the request head, writes [reply] (if any), then holds the socket for [holdMs] before closing it. */
    private fun serve(reply: ByteArray?, holdMs: Long = 1500, block: (Int) -> Unit) {
        val ss = ServerSocket(0)
        Thread {
            ss.use { srv ->
                val s = srv.accept()
                val inp = s.getInputStream()
                var tail = 0
                while (true) { val b = inp.read(); if (b < 0) break; tail = (tail shl 8) or b; if (tail == 0x0d0a0d0a) break }
                if (reply != null) { s.getOutputStream().write(reply); s.getOutputStream().flush() }
                if (holdMs > 0) Thread.sleep(holdMs)
                s.close()
            }
        }.apply { isDaemon = true; start() }
        block(ss.localPort)
    }

    private fun conn(port: Int) = HttpConn(HttpConn.tcp("127.0.0.1", port))
    private val brokenBody: (java.nio.channels.SocketChannel) -> Unit = { throw SocketException("Broken pipe") }

    @Test fun theReplyOfATvThatAnsweredBeforeTheBodyWasWrittenIsRead() {
        val reply = "HTTP/1.1 403 Forbidden\r\nContent-Length: 16\r\nContent-Type: application/json\r\n\r\n{\"error\":\"nope\"}".toByteArray()
        serve(reply) { port ->
            val r = conn(port).request("PUT", "/api/transfer/chunk?id=a&idx=0", "127.0.0.1", emptyList(), 100, brokenBody)
            assertEquals(403, r.status)
            assertFalse(r.keepAlive, "une écriture coupée : jamais de réutilisation de la connexion")
        }
    }

    @Test fun nothingToReadKeepsTheExceptionClassAndMessage() {
        serve(null, holdMs = 0) { port ->
            val e = assertFailsWith<SocketException> { conn(port).request("PUT", "/api/transfer/chunk?id=a&idx=0", "127.0.0.1", emptyList(), 100, brokenBody) }
            assertEquals("Broken pipe", e.message)
        }
    }

    @Test fun silenceIsBoundedByOneSecond() {
        serve(null) { port ->
            val t0 = System.nanoTime()
            assertFailsWith<SocketException> { conn(port).request("PUT", "/x", "127.0.0.1", emptyList(), 100, brokenBody) }
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 1400, "au plus ~1 s d'attente")
        }
    }

    @Test fun aCancelledWriteIsNotSalvaged() {
        val reply = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".toByteArray()
        serve(reply) { port ->
            assertFailsWith<IOException> { conn(port).request("PUT", "/x", "127.0.0.1", emptyList(), 100, { throw IOException("cancelled") }) }
        }
    }

    @Test fun theBodyOfASalvagedReplyIsCappedAt4KiB() {
        val big = "x".repeat(10_000)
        val reply = ("HTTP/1.1 400 Bad Request\r\nContent-Length: ${big.length}\r\n\r\n$big").toByteArray()
        serve(reply) { port ->
            val r = conn(port).request("PUT", "/x", "127.0.0.1", emptyList(), 100, brokenBody)
            assertEquals(400, r.status); assertTrue(r.body.length <= 4096)
        }
    }
}
