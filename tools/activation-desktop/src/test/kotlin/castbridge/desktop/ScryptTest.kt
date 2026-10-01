package castbridge.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ScryptTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun rfc7914Vector2() {
        // RFC 7914 § 12, vector 2: scrypt("password", "NaCl", N=1024, r=8, p=16, dkLen=64)
        val dk = ScryptKdf.scrypt("password".toCharArray(), "NaCl".toByteArray(), 1024, 8, 16, 64)
        assertEquals("fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b3731622eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640", hex(dk))
    }

    @Test fun differentPassphraseOrSaltChangesTheKey() {
        val a = ScryptKdf(16, 1, 1).derive("un code assez long".toCharArray(), ByteArray(16), 32)
        assertNotEquals(hex(a), hex(ScryptKdf(16, 1, 1).derive("un code assez lonG".toCharArray(), ByteArray(16), 32)))
        assertNotEquals(hex(a), hex(ScryptKdf(16, 1, 1).derive("un code assez long".toCharArray(), ByteArray(16) { 1 }, 32)))
        assertEquals(hex(a), hex(ScryptKdf(16, 1, 1).derive("un code assez long".toCharArray(), ByteArray(16), 32)))
    }
}
