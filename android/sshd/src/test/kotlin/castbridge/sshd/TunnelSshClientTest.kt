package castbridge.sshd

import castbridge.core.tunnel.Enrollment
import castbridge.core.tunnel.HostKeyPins
import castbridge.core.tunnel.TunnelException
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.forward.AcceptAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** The tunnel's SSH client against an in-process OpenSSH look-alike (Apache MINA) standing for the editor's server, and the tunnel-only sshd of the TV: no network, no real server. */
class TunnelSshClientTest {
    private val dir = kotlin.io.path.createTempDirectory("tunssh").toFile()
    private fun freePort() = ServerSocket(0).use { it.localPort }
    private val editorPort = freePort(); private val remotePort = freePort(); private val localPort = freePort()
    private val expert: KeyPair = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()
    private val expertB64 = PublicKeyEntry.toString(expert.public).substringAfter(' ')
    private val logins = ArrayList<String>()
    private var editor: SshServer? = null
    private val tvClient = TunnelSshClient(File(dir, "tv"))
    private val pins = HostKeyPins(File(dir, "pins"))
    private val tunnelSshd = TvSshServer(File(dir, "tunnel-sshd"), File(dir, "root"), port = localPort, shell = "/bin/sh", bindHost = "127.0.0.1",
        keyAuthority = { b -> if (b == expertB64) "alice" else null }, onLogin = { logins += it }, idleStop = false)
    private val sshClient = SshClient.setUpDefaultClient().apply { start() }

    @AfterTest fun tearDown() { sshClient.stop(); tunnelSshd.stop(); editor?.stop(true); dir.deleteRecursively() }

    private fun startEditor(hostKeyFile: File = File(dir, "editor_host_key"), acceptKey: ((java.security.PublicKey) -> Boolean) = { true }): SshServer {
        val s = SshServer.setUpDefaultServer()
        s.host = "127.0.0.1"; s.port = editorPort
        s.keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath()).apply { algorithm = "EC"; keySize = 256 }
        s.publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { _, k, _ -> acceptKey(k) }
        s.forwardingFilter = AcceptAllForwardingFilter.INSTANCE
        s.start(); editor = s
        return s
    }

    private fun enrollment(fp: String? = null) = Enrollment("127.0.0.1", editorPort, "cbtunnel", remotePort, fp, "aid", tvClient.keyId())

    private fun expertExec(cmd: String): String {
        val s = sshClient.connect("tv", "127.0.0.1", remotePort).verify(10, TimeUnit.SECONDS).session
        s.addPublicKeyIdentity(expert); s.auth().verify(10, TimeUnit.SECONDS)
        val out = ByteArrayOutputStream(); val ch = s.createExecChannel(cmd); ch.setOut(out); ch.open().verify(10, TimeUnit.SECONDS)
        ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000); s.close(true)
        return out.toString("UTF-8")
    }

    @Test fun keyIsCreatedOnceStoredPrivatelyAndIsEd25519() {
        val line = tvClient.publicKeyLine()
        assertTrue(line.startsWith("ssh-ed25519 ") && line.endsWith(" castbridge-tv"), line)
        assertEquals(line, TunnelSshClient(File(dir, "tv")).publicKeyLine(), "the same key after a restart")
        assertNotEquals(line, TunnelSshClient(File(dir, "tv2")).publicKeyLine())
        val f = File(dir, "tv/tunnel_key")
        assertTrue(f.isFile && f.readText().trim().matches(Regex("[0-9a-f]{64}")))
        assertFalse(java.nio.file.Files.getPosixFilePermissions(f.toPath()).any { it.name.startsWith("GROUP") || it.name.startsWith("OTHERS") })
        f.writeText("damaged"); File(dir, "tv/tunnel_key.bak").delete()
        assertNotEquals(line, TunnelSshClient(File(dir, "tv")).publicKeyLine(), "a damaged file is replaced")
    }

    @Test fun reverseForwardReachesTheTunnelOnlySshdAndExpertsLogIn() {
        startEditor(); tunnelSshd.start()
        var closed = 0
        val sess = tvClient.open(enrollment(), pins, localPort, null) { closed++ }
        try {
            assertTrue(sess.alive())
            assertEquals("hello\n", expertExec("echo hello"))
            assertEquals(listOf("alice"), logins)
            // an unknown key is refused by the tunnel sshd (and never gets a shell)
            val bad = sshClient.connect("tv", "127.0.0.1", remotePort).verify(10, TimeUnit.SECONDS).session
            bad.addPublicKeyIdentity(net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair())
            assertFails { bad.auth().verify(10, TimeUnit.SECONDS) }; bad.close(true)
            assertEquals(listOf("alice"), logins)
        } finally { sess.close() }
        assertFalse(sess.alive())
        assertFailsWith<ConnectException> { Socket("127.0.0.1", remotePort).close() }
    }

    @Test fun passwordLoginIsNeverOfferedByTheTunnelSshd() {
        tunnelSshd.start()
        val s = sshClient.connect("tv", "127.0.0.1", localPort).verify(10, TimeUnit.SECONDS).session
        s.addPasswordIdentity("anything"); assertFails { s.auth().verify(10, TimeUnit.SECONDS) }; s.close(true)
    }

    @Test fun tunnelSshdListensOnLoopbackOnly() {
        tunnelSshd.start()
        val other = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
        if (other != null) assertFailsWith<java.io.IOException> { Socket().use { it.connect(java.net.InetSocketAddress(other, localPort), 2000) } }
    }

    @Test fun throughASocketDialerLikeThePhoneGateway() {
        startEditor(); tunnelSshd.start()
        var dialed = 0
        val sess = tvClient.open(enrollment(), pins, localPort, { h, p -> dialed++; assertEquals("127.0.0.1", h); assertEquals(editorPort, p); Socket("127.0.0.1", editorPort) }) { }
        try { assertEquals(1, dialed); assertEquals("via\n", expertExec("echo via")) } finally { sess.close() }
    }

    @Test fun hostKeyIsPinnedOnFirstUseThenEnforced() {
        startEditor(); tunnelSshd.start()
        tvClient.open(enrollment(), pins, localPort, null) { }.close()
        assertNotNull(pins.get("127.0.0.1", editorPort))
        editor!!.stop(true); Thread.sleep(300)
        startEditor(File(dir, "other_host_key"))                                 // the server's key changed (or a man in the middle)
        val x = assertFailsWith<TunnelException> { tvClient.open(enrollment(), pins, localPort, null) { } }
        assertEquals(TunnelException.Kind.HOSTKEY, x.kind, x.message)
    }

    @Test fun announcedFingerprintMustMatch() {
        val s = startEditor(); tunnelSshd.start()
        val real = KeyUtils.getFingerPrint(s.keyPairProvider.loadKeys(null).first().public)
        tvClient.open(enrollment(real), pins, localPort, null) { }.close()
        val x = assertFailsWith<TunnelException> { tvClient.open(enrollment("SHA256:" + "A".repeat(43)), HostKeyPins(File(dir, "pins2")), localPort, null) { } }
        assertEquals(TunnelException.Kind.HOSTKEY, x.kind)
    }

    @Test fun serverRefusingTheKeyIsAnAuthFailure() {
        startEditor(acceptKey = { false })
        val x = assertFailsWith<TunnelException> { tvClient.open(enrollment(), pins, localPort, null) { } }
        assertEquals(TunnelException.Kind.AUTH, x.kind, x.message)
    }

    @Test fun unreachableServerIsANetworkFailure() {
        val x = assertFailsWith<TunnelException> { tvClient.open(enrollment(), pins, localPort, null) { } }
        assertEquals(TunnelException.Kind.NETWORK, x.kind, x.message)
    }

    @Test fun lostConnectionIsReportedAndWakesTheMachine() {
        startEditor(); tunnelSshd.start()
        val latch = java.util.concurrent.CountDownLatch(1)
        val sess = tvClient.open(enrollment(), pins, localPort, null) { latch.countDown() }
        editor!!.stop(true)
        assertTrue(latch.await(10, TimeUnit.SECONDS)); assertFalse(sess.alive()); sess.close()
    }
}
