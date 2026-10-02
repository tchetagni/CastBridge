package castbridge.core.owner

import kotlin.test.*

/** RFC 7748: § 5.2 (two vectors and the iterated ones), § 6.1 (Alice and Bob). */
class X25519Test {
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun rfc7748Section52Vectors() {
        assertEquals("c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552",
            hex(X25519.scalarMult(unhex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4"), unhex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c"))))
        assertEquals("95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957",
            hex(X25519.scalarMult(unhex("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d"), unhex("e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493"))))
    }

    @Test fun rfc7748IteratedOnceAndAThousandTimes() {
        val nine = ByteArray(32).also { it[0] = 9 }
        var k = nine.copyOf(); var u = nine.copyOf()
        val started = System.nanoTime(); var firstDone = false
        for (i in 1..1000) {
            val out = X25519.scalarMult(k, u); u = k; k = out
            if (i == 1) { assertEquals("422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079", hex(k)); firstDone = true }
        }
        assertTrue(firstDone)
        assertEquals("684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51", hex(k))
        println("X25519 1000 iterations: ${(System.nanoTime() - started) / 1_000_000} ms")
    }

    @Test fun rfc7748Section61AliceAndBob() {
        val a = unhex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"); val b = unhex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val aPub = X25519.publicKey(a); val bPub = X25519.publicKey(b)
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", hex(aPub))
        assertEquals("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", hex(bPub))
        val expected = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
        assertEquals(expected, hex(assertNotNull(X25519.sharedSecret(a, bPub)))); assertEquals(expected, hex(assertNotNull(X25519.sharedSecret(b, aPub))))
    }

    @Test fun clampingAndInputsAreNotModified() {
        val k = ByteArray(32) { 0xFF.toByte() }; val c = X25519.clamp(k)
        assertEquals(0xF8, c[0].toInt() and 0xFF); assertEquals(0x7F, c[31].toInt() and 0xFF); assertEquals(0xFF, k[0].toInt() and 0xFF, "the input array is left alone")
        assertFailsWith<IllegalArgumentException> { X25519.clamp(ByteArray(31)) }
        assertNull(X25519.sharedSecret(ByteArray(31), ByteArray(32)))
    }

    @Test fun smallOrderPointsGiveNoSharedSecret() {
        val priv = ByteArray(32) { (it + 1).toByte() }
        assertNull(X25519.sharedSecret(priv, ByteArray(32)), "u = 0")
        assertNull(X25519.sharedSecret(priv, ByteArray(32).also { it[0] = 1 }), "u = 1 (order 4)")
        assertNull(X25519.sharedSecret(priv, unhex("e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800")), "order 8 point from the libsodium small-order list (not the RFC 7748 list)")
    }
}
