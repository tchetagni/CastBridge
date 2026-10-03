package castbridge.play.entitlement

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `tools/play/gen-ticket-keypair.sh` : clé privée en 600, jamais affichée, jamais écrasée ; la clé publique produite est celle que le service accepte. */
class KeypairScriptTest {
    private val script = File("../tools/play/gen-ticket-keypair.sh")

    private fun run(dir: File): Pair<Int, String> {
        val p = ProcessBuilder("bash", script.path, dir.path).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue(p.waitFor(60, TimeUnit.SECONDS)); return p.exitValue() to out
    }

    @Test fun generatesAPairThatSignsAndVerifiesAndNeverOverwrites() {
        org.junit.Assume.assumeTrue("openssl absent", runCatching { ProcessBuilder("openssl", "version").start().waitFor() == 0 }.getOrDefault(false))
        assertTrue(script.isFile, "script : ${script.absolutePath}")
        val dir = File(Files.createTempDirectory("kp").toFile(), "keys")
        val (code, out) = run(dir)
        assertEquals(0, code, out)
        val priv = File(dir, "play-ticket.key"); val pub = File(dir, "play-ticket.pub.b64")
        assertTrue(priv.isFile && pub.isFile)
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(priv.toPath()), "clé privée en 600")
        assertTrue(out.contains(pub.path) && Regex("[0-9a-f]{64}").containsMatchIn(out), "affiche le chemin de la clé publique et l'empreinte : $out")
        val pem = priv.readText()
        assertFalse(out.contains("PRIVATE") || out.contains(pem.lines()[1]), "la clé privée n'est jamais affichée")
        assertFalse(out.contains(priv.path), "pas même son chemin")

        // la paire tient : un ticket signé par la privée est accepté par un vérificateur qui n'a que la publique (format de CASTBRIDGE_PLAY_TICKET_PUBKEY)
        val der = Base64.getMimeDecoder().decode(pem.lines().filter { !it.startsWith("-----") }.joinToString(""))
        val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(der))
        val now = System.currentTimeMillis(); val b64 = Base64.getUrlEncoder().withoutPadding()
        val payload = b64.encodeToString("""{"aud":"castbridge-play","deviceId":"d1","blocked":false,"country":"CM","iat":$now,"exp":${now + 600_000},"jti":"${"ab".repeat(16)}"}""".toByteArray())
        val sig = Signature.getInstance("Ed25519").run { initSign(key); update("castbridge-play-ticket-v1\ncbp1.$payload".toByteArray()); sign() }
        assertTrue(TicketVerifier(listOf(pub.readText().trim())).verify("cbp1.$payload." + b64.encodeToString(sig), now))

        // ne dit rien de plus au second passage et n'écrase RIEN
        val before = priv.readBytes()
        val (code2, out2) = run(dir)
        assertEquals(1, code2); assertTrue("existe déjà" in out2)
        assertTrue(before.contentEquals(priv.readBytes()), "la clé existante n'a pas bougé")
        assertEquals(setOf("play-ticket.key", "play-ticket.pub", "play-ticket.pub.b64"), dir.list()!!.toSet(), "aucun fichier temporaire ne reste")
    }

    @Test fun withoutADirectoryItPrintsTheUsageAndWritesNothing() {
        val p = ProcessBuilder("bash", script.path).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(2, p.waitFor()); assertTrue("Usage" in out)
    }

    @Test fun anExistingDirectoryKeepsItsPermissionsAndAHostileNameIsNotParsedAsAnOption() {
        org.junit.Assume.assumeTrue("openssl absent", runCatching { ProcessBuilder("openssl", "version").start().waitFor() == 0 }.getOrDefault(false))
        val existing = Files.createTempDirectory("kp-existing").toFile()
        Files.setPosixFilePermissions(existing.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE))
        val (code, out) = run(existing); assertEquals(0, code, out)
        assertTrue(PosixFilePermission.GROUP_READ in Files.getPosixFilePermissions(existing.toPath()), "un dossier existant n'est pas chmodé")
        val created = File(Files.createTempDirectory("kp-new").toFile(), "neuf")
        run(created)
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE), Files.getPosixFilePermissions(created.toPath()), "un dossier créé par le script est en 0700")
    }
}
