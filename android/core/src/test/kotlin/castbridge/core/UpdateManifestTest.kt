package castbridge.core

import castbridge.core.update.Ed25519
import castbridge.core.update.UpdateManifest
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.random.Random
import kotlin.test.*

fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

class Ed25519Test {
    @Test
    fun rfc8032Vectors() {
        // RFC 8032 section 7.1, tests 1 and 2
        assertTrue(Ed25519.verify(hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"), ByteArray(0),
            hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")))
        val pub2 = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val sig2 = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")
        assertTrue(Ed25519.verify(pub2, byteArrayOf(0x72), sig2))
        assertFalse(Ed25519.verify(pub2, byteArrayOf(0x73), sig2))
        assertFalse(Ed25519.verify(pub2, byteArrayOf(0x72), sig2.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }))
        assertFalse(Ed25519.verify(pub2, byteArrayOf(0x72), sig2.copyOf(63)))
        assertFalse(Ed25519.verify(ByteArray(31), byteArrayOf(0x72), sig2))
        // S >= L must be refused (malleability)
        val bigS = sig2.copyOf().also { for (i in 32 until 64) it[i] = 0xff.toByte() }
        assertFalse(Ed25519.verify(pub2, byteArrayOf(0x72), bigS))
    }

    @Test
    fun agreesWithTheJdkImplementation() {
        val gen = KeyPairGenerator.getInstance("Ed25519")
        val rnd = Random(1)
        repeat(12) {
            val kp = gen.generateKeyPair()
            val raw = kp.public.encoded.copyOfRange(12, 44) // X.509 SubjectPublicKeyInfo = 12-byte prefix + raw key
            val msg = rnd.nextBytes(rnd.nextInt(0, 300))
            val sig = Signature.getInstance("Ed25519").run { initSign(kp.private); update(msg); sign() }
            assertTrue(Ed25519.verify(raw, msg, sig))
            if (msg.isNotEmpty()) assertFalse(Ed25519.verify(raw, msg.copyOf().also { m -> m[0] = (m[0] + 1).toByte() }, sig))
        }
    }
}

class UpdateManifestTest {
    /** Built and signed by the server's test (backend SigningAndApkTest.FIXTURE, RFC 8032 test key 1). */
    private val fixture = """
        {"app":"tv","channel":"stable","abi":"armeabi-v7a","versionCode":42,"versionName":"0.6",
         "url":"https://cb.example/castbridge/dl/tv/castbridge-tv-0.6-42-armeabi-v7a-0123abcd.apk",
         "sha256":"0123abcd00000000000000000000000000000000000000000000000000000000","size":1234567,"minSdk":26,
         "notes":"Corrections « Wi-Fi Direct » et lecteur.\nDeuxième ligne.","mandatory":true,"minSupportedVersionCode":40,
         "publishedAt":"2026-09-30T10:00:00+01:00","keyId":null,
         "signature":"E6Q78QbwcZGsWRuYbrXcVERthAD5JEAxbUaszHBPr79+CUrcfmHuLeQ/+R0OXHLUsh8Q1qTqgAt4uJwgQDZDAA=="}
    """.trimIndent()
    private val serverTestKey = "11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo="

    @Test
    fun verifiesAManifestSignedByTheServer() {
        val m = UpdateManifest.parse(fixture)
        assertEquals(42, m.versionCode)
        assertEquals(26, m.minSdk)
        assertEquals("Corrections « Wi-Fi Direct » et lecteur.\nDeuxième ligne.", m.notes)
        assertTrue(m.signatureValid(serverTestKey))
        assertTrue(m.isMandatoryFor(41))
        // any change breaks it
        assertFalse(m.copy(url = "https://evil.example/x.apk").signatureValid(serverTestKey))
        assertFalse(m.copy(sha256 = "f" + m.sha256.drop(1)).signatureValid(serverTestKey))
        assertFalse(m.copy(size = m.size + 1).signatureValid(serverTestKey))
        assertFalse(m.copy(notes = m.notes + " ").signatureValid(serverTestKey))
        assertFalse(m.copy(mandatory = false).signatureValid(serverTestKey))
        assertFalse(m.copy(minSupportedVersionCode = 0).signatureValid(serverTestKey))
        // another key, garbage key
        assertFalse(m.signatureValid("3UAXw+hDiVqStwqnTRt+vJyYLM8uxJaMwM1V8Sr0Zgw="))
        assertFalse(m.signatureValid("pas du base64 !"))
    }

    @Test
    fun optionalFieldsAndErrors() {
        val m = UpdateManifest.parse(fixture.replace("\"minSdk\":26", "\"minSdk\":null").replace("\"mandatory\":true", "\"mandatory\":false"))
        assertNull(m.minSdk)
        assertTrue(m.canonicalPayload().contains("\nminSdk=\n"))
        assertFalse(m.isMandatoryFor(40))
        assertTrue(m.isMandatoryFor(39), "below the oldest supported version")
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse("""{"app":"tv"}""") }
        assertFailsWith<IllegalArgumentException> { UpdateManifest.parse("pas du json") }
    }

    @Test
    fun verifiesTheDownloadedFile() {
        val f = kotlin.io.path.createTempFile("apk").toFile()
        f.writeBytes("hello apk".toByteArray())
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        val m = UpdateManifest.parse(fixture).copy(sha256 = sha, size = f.length())
        assertNull(m.verifyFile(f))
        assertNotNull(m.copy(size = 3).verifyFile(f))
        f.appendBytes(byteArrayOf(1))
        assertNotNull(m.copy(size = f.length()).verifyFile(f))
        assertNotNull(m.verifyFile(java.io.File(f.path + ".missing")))
    }
}
