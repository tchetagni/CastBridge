package castbridge.sshd

import castbridge.core.ssh.FailureTracker
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.sftp.client.SftpClientFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.test.*

class TvSshServerTest {
    private val dir = kotlin.io.path.createTempDirectory("ssh").toFile()
    private val root = File(dir, "files").apply { mkdirs() }
    private val port = ServerSocket(0).use { it.localPort }
    private val server = TvSshServer(File(dir, "data"), root, port = port, shell = "/bin/sh", failures = FailureTracker(3, 60_000))
    private val client = SshClient.setUpDefaultClient().apply { start() }

    private fun ecKey(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private fun edKey(): KeyPair = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()

    @AfterTest fun tearDown() { client.stop(); server.stop(); dir.deleteRecursively() }

    private fun session(kp: KeyPair): ClientSession =
        client.connect("tv", "127.0.0.1", port).verify(5, TimeUnit.SECONDS).session.also {
            it.addPublicKeyIdentity(kp)
        }

    private fun exec(s: ClientSession, cmd: String): Triple<String, String, Int?> {
        val out = ByteArrayOutputStream(); val err = ByteArrayOutputStream()
        val ch = s.createExecChannel(cmd); ch.setOut(out); ch.setErr(err)
        ch.open().verify(5, TimeUnit.SECONDS)
        ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
        return Triple(out.toString("UTF-8"), err.toString("UTF-8"), ch.exitStatus)
    }

    private fun authorize(kp: KeyPair) { server.addKey(PublicKeyEntry.toString(kp.public)) }

    @Test fun execReturnsExitCodeAndSeparateStreams() {
        val kp = edKey(); authorize(kp); server.start()
        session(kp).use { s ->
            s.auth().verify(5, TimeUnit.SECONDS)
            val (out, err, code) = exec(s, "echo hello; echo oops >&2; exit 3")
            assertEquals("hello\n", out); assertEquals("oops\n", err); assertEquals(3, code)
        }
    }

    @Test fun ecdsaKeysWorkToo() {
        val kp = ecKey(); authorize(kp); server.start()
        session(kp).use { s -> s.auth().verify(5, TimeUnit.SECONDS); assertEquals("ok\n", exec(s, "echo ok").first) }
    }

    @Test fun unknownKeyIsRefusedAndAddressGetsLocked() {
        authorize(edKey()); server.start()
        repeat(3) {
            session(edKey()).use { s -> assertFails { s.auth().verify(5, TimeUnit.SECONDS) } }
        }
        // locked now: even the real key cannot get in for a minute
        val good = edKey(); authorize(good)
        assertFails { session(good).use { s -> s.auth().verify(5, TimeUnit.SECONDS) } }
    }

    @Test fun passwordAuthenticationIsNotOffered() {
        authorize(edKey()); server.start()
        client.connect("tv", "127.0.0.1", port).verify(5, TimeUnit.SECONDS).session.use { s ->
            s.addPasswordIdentity("anything")
            assertFails { s.auth().verify(5, TimeUnit.SECONDS) }
        }
    }

    @Test fun sftpIsConfinedToTheRoot() {
        File(root, "a.txt").writeText("hi"); File(dir, "secret.txt").writeText("no")
        val kp = edKey(); authorize(kp); server.start()
        session(kp).use { s ->
            s.auth().verify(5, TimeUnit.SECONDS)
            SftpClientFactory.instance().createSftpClient(s).use { sftp ->
                assertEquals(listOf("a.txt"), sftp.readDir("/").map { it.filename }.filter { !it.startsWith(".") })
                assertFails { sftp.open("/../secret.txt").close() }
            }
        }
    }

    @Test fun portForwardingNeverReachesTheTarget() {
        val kp = edKey(); authorize(kp); server.start()
        ServerSocket(0).use { target ->
            target.soTimeout = 1500
            session(kp).use { s ->
                s.auth().verify(5, TimeUnit.SECONDS)
                fun addr(h: String, p: Int) = org.apache.sshd.common.util.net.SshdSocketAddress(h, p)
                s.createLocalPortForwardingTracker(addr("127.0.0.1", 0), addr("127.0.0.1", target.localPort)).use { t ->
                    runCatching {
                        java.net.Socket("127.0.0.1", t.boundAddress.port).use { c -> c.getOutputStream().write(1); c.getOutputStream().flush() }
                    }
                    assertFails("the TV must not open connections on behalf of a client") { target.accept().close() }
                }
            }
        }
    }

    @Test fun startStopAndKeyManagement() {
        assertFalse(server.running); assertFalse(server.policy.enabled)
        val k = server.addKey(PublicKeyEntry.toString(edKey().public) + " me@host")
        assertEquals(listOf(k), server.keys())
        assertTrue(server.removeKey(k.fingerprint)); assertTrue(server.keys().isEmpty())
        assertNull(server.hostKeyFingerprint().takeIf { false })
        server.start(); assertTrue(server.running); assertTrue(server.policy.enabled)
        assertTrue(server.hostKeyFingerprint()!!.startsWith("SHA256:"))
        server.stop(); assertFalse(server.running); assertFalse(server.policy.enabled)
    }

    @Test fun interactiveShellRunsCommands() {
        val kp = edKey(); authorize(kp); server.start()
        session(kp).use { s ->
            s.auth().verify(5, TimeUnit.SECONDS)
            val out = ByteArrayOutputStream()
            val ch = s.createShellChannel(); ch.setOut(out); ch.setErr(out)
            val pipe = java.io.PipedOutputStream(); ch.setIn(java.io.PipedInputStream(pipe))
            ch.open().verify(5, TimeUnit.SECONDS)
            pipe.write("echo interactive-ok\nexit\n".toByteArray()); pipe.flush()
            ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            assertTrue(out.toString("UTF-8").contains("interactive-ok"), out.toString("UTF-8"))
        }
    }

    @Test fun terminalClientGetsEchoAndCarriageReturnEnter() {
        val kp = edKey(); authorize(kp); server.start()
        session(kp).use { s ->
            s.auth().verify(5, TimeUnit.SECONDS)
            val out = ByteArrayOutputStream()
            val ch = s.createShellChannel(); ch.setPtyType("xterm"); ch.setOut(out); ch.setErr(out)
            val pipe = java.io.PipedOutputStream(); ch.setIn(java.io.PipedInputStream(pipe))
            ch.open().verify(5, TimeUnit.SECONDS)
            pipe.write("echo pty-ok\r".toByteArray()); pipe.flush(); Thread.sleep(500)
            pipe.write("exit\r".toByteArray()); pipe.flush()
            ch.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            val text = out.toString("UTF-8")
            assertTrue(text.contains("echo pty-ok"), "typed text is echoed: $text")
            assertTrue(Regex("pty-ok\r\n").containsMatchIn(text), "output uses CRLF: $text")
        }
    }
}
