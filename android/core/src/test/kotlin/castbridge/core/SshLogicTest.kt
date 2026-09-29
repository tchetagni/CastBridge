package castbridge.core

import castbridge.core.ssh.*
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.*

class AuthorizedKeysTest {
    // Real-format keys generated for the test (ed25519 and a 2048-bit RSA key blob).
    private val ed = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKxHNTyU9rlj2qOXuDJKr7Uxm agent@laptop"

    private fun blob(type: String, vararg parts: ByteArray): String {
        val o = ByteArrayOutputStream()
        fun put(b: ByteArray) { o.write(java.nio.ByteBuffer.allocate(4).putInt(b.size).array()); o.write(b) }
        put(type.toByteArray()); parts.forEach(::put)
        return Base64.getEncoder().encodeToString(o.toByteArray())
    }

    @Test fun parsesEd25519AndComputesFingerprint() {
        val k = AuthorizedKeys.parseLine(ed)
        assertEquals("ssh-ed25519", k.type); assertEquals("agent@laptop", k.comment)
        assertTrue(k.fingerprint.startsWith("SHA256:") && !k.fingerprint.endsWith("="))
        assertEquals(43, k.fingerprint.removePrefix("SHA256:").length)
    }

    @Test fun rejectsOptionsUnknownTypesMismatchAndGarbage() {
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine("command=\"rm -rf /\" $ed") }
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine("ssh-dss AAAA x") }
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine("ssh-ed25519 AAAAB3NzaC1yc2E= x") }   // wrong inner type
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine("ssh-ed25519 !!!notbase64 x") }
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine("ssh-ed25519") }
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine("") }
    }

    @Test fun rsaMinimumSize() {
        val e = byteArrayOf(1, 0, 1)
        val small = "ssh-rsa " + blob("ssh-rsa", e, ByteArray(129).also { it[0] = 0; it[1] = 0x80.toByte() }.copyOf(65)) // 512 bits
        assertFailsWith<AuthorizedKeys.Invalid> { AuthorizedKeys.parseLine(small) }
        val n = ByteArray(257).also { it[1] = 0x80.toByte() }                                                          // 2048 bits
        assertEquals("ssh-rsa", AuthorizedKeys.parseLine("ssh-rsa " + blob("ssh-rsa", e, n) + " me").type)
    }

    @Test fun fileParsingDedupesAddRemoveAndLimit() {
        val text = "# comment\n\n$ed\n$ed\nbad line\n"
        val keys = AuthorizedKeys.parse(text)
        assertEquals(1, keys.size)
        assertEquals(keys, AuthorizedKeys.add(keys, AuthorizedKeys.parseLine(ed)))
        assertEquals(text.lines().filter { it.startsWith("ssh-") }.first() + "\n", AuthorizedKeys.render(keys))
        assertTrue(AuthorizedKeys.remove(keys, keys[0].fingerprint).isEmpty())
        var many = emptyList<SshKey>()
        for (i in 0 until AuthorizedKeys.MAX_KEYS) many = AuthorizedKeys.add(many,
            SshKey("ssh-ed25519", blob("ssh-ed25519", ByteArray(32) { i.toByte() }), "k$i"))
        assertFailsWith<AuthorizedKeys.Invalid> {
            AuthorizedKeys.add(many, SshKey("ssh-ed25519", blob("ssh-ed25519", ByteArray(32) { 99 }), "x"))
        }
    }

    @Test fun commentIsSanitized() {
        val k = AuthorizedKeys.parseLine(ed.substringBeforeLast(" ") + "  hello world\tand more")
        assertEquals("hello worldand more", k.comment)
    }
}

class SshPolicyTest {
    @Test fun offByDefaultEnableTimeoutAndSessions() {
        var t = 0L
        val p = SshPolicy(idleMinutes = 10, now = { t })
        assertFalse(p.enabled); assertFalse(p.shouldStop())
        p.enable()
        t = 9 * 60_000; assertFalse(p.shouldStop()); assertEquals(60, p.secondsLeft())
        p.onSessionOpened()
        t = 60 * 60_000; assertFalse(p.shouldStop(), "an open session keeps it up (it has its own idle timeout)")
        p.onSessionClosed()
        assertFalse(p.shouldStop())
        t += 10 * 60_000; assertTrue(p.shouldStop())
        p.disable(); assertFalse(p.enabled); assertFalse(p.shouldStop())
    }

    @Test fun activityExtendsAndMinutesAreClamped() {
        var t = 0L
        val p = SshPolicy(now = { t })
        p.enable(0); assertEquals(1, p.idleMinutes)
        p.enable(100000); assertEquals(SshPolicy.MAX_IDLE_MINUTES, p.idleMinutes)
        p.enable(5)
        t = 4 * 60_000; p.onActivity()
        t = 8 * 60_000; assertFalse(p.shouldStop())
        t = 9 * 60_000; assertTrue(p.shouldStop())
    }

    @Test fun failureTrackerLocksAndExpires() {
        var t = 0L
        val f = FailureTracker(3, 60_000) { t }
        repeat(2) { f.recordFailure("a") }; assertFalse(f.isLocked("a"))
        f.recordFailure("a"); assertTrue(f.isLocked("a")); assertFalse(f.isLocked("b"))
        t = 59_999; assertTrue(f.isLocked("a"))
        t = 60_000; assertFalse(f.isLocked("a"))
        f.recordFailure("b"); f.recordSuccess("b"); repeat(2) { f.recordFailure("b") }; assertFalse(f.isLocked("b"))
    }

    @Test fun lanOnly() {
        for (ip in listOf("192.168.0.5", "10.1.2.3", "172.16.0.9", "172.31.255.1", "127.0.0.1", "169.254.1.1", "::1", "fe80::1%wlan0", "fd12:3456::1"))
            assertTrue(Lan.isLocal(ip), ip)
        for (ip in listOf("8.8.8.8", "172.32.0.1", "203.0.113.5", "2001:db8::1", "example.com", "", "999.1.1.1x"))
            assertFalse(Lan.isLocal(ip), ip)
    }
}

class LineDisciplineTest {
    private val toClient = ByteArrayOutputStream(); private val toShell = ByteArrayOutputStream()
    private var eof = 0; private var intr = 0
    private val ld = LineDiscipline({ toClient.write(it) }, { toShell.write(it) }, { eof++ }, { intr++ })
    private fun type(s: String) = ld.fromClient(s.toByteArray())
    private fun shell() = toShell.toString("UTF-8"); private fun client() = toClient.toString("UTF-8")

    @Test fun echoesAndSendsLineOnEnter() {
        type("ls -l"); assertEquals("", shell()); assertEquals("ls -l", client())
        type("\r"); assertEquals("ls -l\n", shell()); assertTrue(client().endsWith("\r\n"))
    }

    @Test fun crlfIsOneEnter() { type("a\r\nb\r"); assertEquals("a\nb\n", shell()) }

    @Test fun backspaceAndUtf8() {
        type("abc\u007f\u007fx"); type("é"); type("\u007f"); type("y\r")
        assertEquals("axy\n", shell())
        assertTrue(client().contains("\b \b"))
    }

    @Test fun ctrlUAndCtrlW() {
        type("hello world\u0017!\r"); assertEquals("hello !\n", shell())
        toShell.reset(); type("abc\u0015def\r"); assertEquals("def\n", shell())
    }

    @Test fun ctrlCAndCtrlD() {
        type("abc\u0003"); assertEquals(1, intr); assertEquals("", shell()); assertTrue(client().contains("^C"))
        type("\u0004"); assertEquals(1, eof)
        type("ab\u0004"); assertEquals("ab", shell()); assertEquals(1, eof, "Ctrl-D on a non-empty line just flushes")
    }

    @Test fun escapeSequencesAreSwallowed() {
        type("a\u001b[A\u001b[1;5Cb\u001bOPc\r"); assertEquals("abc\n", shell())
    }

    @Test fun outputGetsCarriageReturns() {
        assertEquals("a\r\nb\r\n", String(ld.fromShell("a\nb\r\n".toByteArray())))
        assertEquals("x\r", String(ld.fromShell("x\r".toByteArray())))
        assertEquals("\ny", String(ld.fromShell("\ny".toByteArray())), "LF right after a chunk-final CR is not doubled")
    }
}
