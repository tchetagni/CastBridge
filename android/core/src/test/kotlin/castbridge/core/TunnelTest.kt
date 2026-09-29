package castbridge.core

import castbridge.core.ssh.ByteRelay
import castbridge.core.ssh.FailureTracker
import castbridge.core.ssh.PeerRegistry
import castbridge.core.ssh.SshTunnel
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

class ByteRelayTest {
    /** A pipe pair: (what the relay reads, what the test writes into it). */
    private fun pipe(): Pair<PipedInputStream, PipedOutputStream> { val i = PipedInputStream(1 shl 20); return i to PipedOutputStream(i) }

    @Test fun bytesFlowBothWaysUnchanged() {
        val (aIn, toA) = pipe(); val (fromBIn, bOut) = pipe()           // a -> relay -> b
        val (bIn, toB) = pipe(); val (fromAIn, aOut) = pipe()           // b -> relay -> a
        var closedA = 0; var closedB = 0
        val r = ByteRelay(aIn, aOut, bIn, bOut, { closedA++; toA.close() }, { closedB++; toB.close() }, bufferBytes = 1024).start()
        val up = Random(1).nextBytes(300_000); val down = Random(2).nextBytes(100_000)
        val readDone = CountDownLatch(1)       // piped streams fail once their writer thread is gone: keep the writers alive
        thread { toA.write(up); toA.flush(); readDone.await() }; thread { toB.write(down); toB.flush(); readDone.await() }
        val gotUp = ByteArray(up.size).also { java.io.DataInputStream(fromBIn).readFully(it) }
        val gotDown = ByteArray(down.size).also { java.io.DataInputStream(fromAIn).readFully(it) }
        assertContentEquals(up, gotUp); assertContentEquals(down, gotDown)
        assertEquals(300_000, r.bytesAtoB.get()); assertEquals(100_000, r.bytesBtoA.get())
        toA.close()                                                       // one side ends...
        readDone.countDown()
        r.join(3000)
        assertTrue(r.isClosed); assertEquals(1, closedA); assertEquals(1, closedB, "...both links are closed, once")
    }

    @Test fun peerKeys() {
        val reg = PeerRegistry()
        reg.register(40001, "bt:AA")
        val lo = InetAddress.getByName("127.0.0.1")
        assertEquals("bt:AA", reg.clientKey(InetSocketAddress(lo, 40001)))
        assertEquals("127.0.0.1", reg.clientKey(InetSocketAddress(lo, 40002)))
        assertEquals("192.168.1.5", reg.clientKey(InetSocketAddress(InetAddress.getByName("192.168.1.5"), 40001)), "only loopback is attributed")
        reg.unregister(40001); assertEquals("127.0.0.1", reg.clientKey(InetSocketAddress(lo, 40001)))
        assertEquals("", reg.clientKey(null))
    }

    @Test fun lockoutCountsPerPeerKey() {
        val f = FailureTracker(2, 60_000)
        repeat(2) { f.recordFailure("bt:AA") }
        assertTrue(f.isLocked("bt:AA")); assertFalse(f.isLocked("bt:BB")); assertFalse(f.isLocked("127.0.0.1"))
    }

    @Test fun tunnelToALocalServerRegistersThePeerAndRespectsTheLimit() {
        val reg = PeerRegistry()
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { echo ->
            val seen = java.util.concurrent.ConcurrentLinkedQueue<String>()
            thread(isDaemon = true) {
                while (true) {
                    val s = runCatching { echo.accept() }.getOrNull() ?: break
                    seen += reg.clientKey(s.remoteSocketAddress as InetSocketAddress)    // what the SSH server would count failures against
                    thread(isDaemon = true) { runCatching { s.getInputStream().copyTo(s.getOutputStream()) }; s.close() }
                }
            }
            val t = SshTunnel(reg, echo.localPort, maxConnections = 1)
            val (inA, toTunnel) = pipe(); val (fromTunnel, outA) = pipe()
            val done = CountDownLatch(1)
            thread { t.serve("AA:BB", "Pixel", inA, outA, { toTunnel.close(); outA.close() }); done.countDown() }
            toTunnel.write("hello".toByteArray()); toTunnel.flush()
            val back = ByteArray(5).also { java.io.DataInputStream(fromTunnel).readFully(it) }
            assertEquals("hello", String(back)); assertEquals(listOf("bt:AA:BB"), seen.toList())
            assertEquals(1, t.active().size)
            var refusedClosed = false
            val (i2, _) = pipe(); val (_, o2) = pipe()
            assertFalse(t.serve("CC", "Other", i2, o2, { refusedClosed = true }), "over the limit")
            assertTrue(refusedClosed)
            toTunnel.close()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(0, t.active().size); assertEquals(0, reg.size())
        }
    }

    @Test fun noServerMeansRefused() {
        val free = ServerSocket(0).use { it.localPort }
        var closed = false
        val (i, _) = pipe(); val (_, o) = pipe()
        assertFalse(SshTunnel(PeerRegistry(), free).serve("AA", "x", i, o, { closed = true }))
        assertTrue(closed)
    }
}
