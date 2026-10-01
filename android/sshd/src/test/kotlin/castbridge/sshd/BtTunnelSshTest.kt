package castbridge.sshd

import castbridge.core.ssh.FailureTracker
import castbridge.core.ssh.SshTunnel
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.common.config.keys.PublicKeyEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

/**
 * "SSH over Bluetooth" end to end, with TCP listeners standing in for the RFCOMM service: each listener is one Bluetooth
 * device (its address is the peer id). SSH itself is unchanged: keys, lockout and exec behave as on the LAN.
 */
class BtTunnelSshTest {
    private val dir = kotlin.io.path.createTempDirectory("btssh").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    private val server = TvSshServer(File(dir, "data"), File(dir, "files").apply { mkdirs() }, port = port, shell = "/bin/sh",
        failures = FailureTracker(3, 60_000))
    private val tunnel = SshTunnel(server.peers, port, maxConnections = 2)
    private val client = SshClient.setUpDefaultClient().apply { start() }
    private val listeners = ArrayList<ServerSocket>()

    @AfterTest fun tearDown() { client.stop(); server.stop(); listeners.forEach { runCatching { it.close() } }; dir.deleteRecursively() }

    private fun edKey(): KeyPair = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()

    /** A fake "Bluetooth device" [addr]: every TCP connection to the returned port is served by the tunnel as that device. */
    private fun device(addr: String): Int {
        val ss = ServerSocket(0).also { listeners += it }
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { tunnel.serve(addr, "Téléphone $addr", s.getInputStream(), s.getOutputStream(), { s.close() }) }
            }
        }
        return ss.localPort
    }

    private fun login(p: Int, kp: KeyPair) = client.connect("tv", "127.0.0.1", p).verify(5, TimeUnit.SECONDS).session.also { it.addPublicKeyIdentity(kp) }

    @Test fun execWorksThroughTheTunnel() {
        val kp = edKey(); server.addKey(PublicKeyEntry.toString(kp.public)); server.start()
        val a = device("AA:AA:AA:AA:AA:01")
        login(a, kp).use { s ->
            s.auth().verify(5, TimeUnit.SECONDS)
            assertEquals(1, server.policy.openSessions, "a session through the tunnel counts for the TV's SSH badge (status 4-ssh-n)")
            val out = ByteArrayOutputStream()
            val ch = s.createExecChannel("head -c 200000 /dev/zero | wc -c"); ch.setOut(out); ch.open().verify(5, TimeUnit.SECONDS)
            ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            assertEquals("200000", out.toString("UTF-8").trim())
            assertEquals(1, tunnel.active().size)
        }
        Thread.sleep(300)
        assertEquals(0, server.policy.openSessions)
        assertEquals(0, tunnel.active().size, "closing the SSH session frees the tunnel slot")
        assertEquals(0, server.peers.size())
    }

    @Test fun lockoutIsPerBluetoothDevice() {
        val good = edKey(); server.addKey(PublicKeyEntry.toString(good.public)); server.start()
        val a = device("AA:AA:AA:AA:AA:01"); val b = device("BB:BB:BB:BB:BB:02")
        repeat(3) { login(a, edKey()).use { s -> assertFails { s.auth().verify(5, TimeUnit.SECONDS) } } }
        assertFails("device A is locked, even with the right key") { login(a, good).use { s -> s.auth().verify(5, TimeUnit.SECONDS) } }
        login(b, good).use { s -> s.auth().verify(5, TimeUnit.SECONDS) }          // device B is not
        login(port, good).use { s -> s.auth().verify(5, TimeUnit.SECONDS) }       // nor a direct local client
    }

    @Test fun connectionLimit() {
        server.start()
        val a = device("AA:AA:AA:AA:AA:01")
        val socks = (1..3).map { Socket("127.0.0.1", a).apply { soTimeout = 3000 } }
        try {
            // Every accepted link gets the SSH banner; the one over the limit is closed at once (EOF, no banner).
            val results = socks.map { s -> runCatching { val b = ByteArray(4); val n = s.getInputStream().read(b); if (n > 0) String(b, 0, n) else "EOF" }.getOrDefault("EOF") }
            assertEquals(2, results.count { it.startsWith("SSH") }, results.toString())
            assertEquals(1, results.count { it == "EOF" }, results.toString())
        } finally { socks.forEach { it.close() } }
    }
}

class BtTunnelSshFailureTest {
    @Test fun sshServerOffIsExplainedAndNothingIsLeft() {
        val log = java.util.concurrent.CopyOnWriteArrayList<String>()
        val free = ServerSocket(0).use { it.localPort }
        val peers = castbridge.core.ssh.PeerRegistry()
        val tunnel = SshTunnel(peers, free, maxConnections = 1, log = { log += it })
        repeat(3) {       // never a stuck slot, however many times it fails
            val s = ServerSocket(0)
            thread(isDaemon = true) { val c = s.accept(); tunnel.serve("AA:BB", "x", c.getInputStream(), c.getOutputStream(), { c.close() }) }
            Socket("127.0.0.1", s.localPort).use { assertEquals(-1, it.getInputStream().read()) }
            s.close()
        }
        Thread.sleep(200)
        assertTrue(log.any { "refused" in it && "SSH" in it }, log.toString())
        assertTrue(tunnel.lastError!!.contains("SSH"))
        assertEquals(0, tunnel.active().size); assertEquals(0, peers.size())
    }
}
