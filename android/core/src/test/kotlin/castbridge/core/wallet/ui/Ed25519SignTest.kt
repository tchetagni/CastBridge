package castbridge.core.wallet.ui

import castbridge.core.owner.Ed25519Signer
import castbridge.core.update.Ed25519
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** Signature Ed25519 en Kotlin pur : la TV (Android < 13) n'a pas Ed25519 dans java.security. Doit donner EXACTEMENT les mêmes octets que le JDK (Ed25519 est déterministe). */
class Ed25519SignTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun rfc8032Vector1() {
        val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val sig = Ed25519.sign(seed, ByteArray(0))
        assertContentEquals(hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"), sig)
    }

    @Test fun rfc8032Vector2() {
        val seed = hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
        val sig = Ed25519.sign(seed, hex("72"))
        assertContentEquals(hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"), sig)
    }

    @Test fun sameBytesAsTheJdkAndVerifies() {
        for (b in listOf(1, 0x55, 0x99, 0xfe)) {
            val seed = ByteArray(32) { (b + it).toByte() }
            val jdk = Ed25519Signer(seed)
            for (m in listOf(ByteArray(0), "abc".toByteArray(), ByteArray(1000) { (it * 7).toByte() })) {
                val mine = Ed25519.sign(seed, m)
                assertContentEquals(jdk.sign(m), mine)
                assertTrue(Ed25519.verify(Ed25519.publicKey(seed), m, mine))
            }
        }
    }
}
