package castbridge.core.wallet.ui

import castbridge.core.net.JsonLite
import castbridge.core.owner.InstallSigner
import castbridge.core.update.Ed25519
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Preuve de possession de la TV (BindProof côté serveur, `castbridge-wallet-bind-v1`). Le vecteur doré `tools/wallet/wallet-bind-vector.json` a été produit par une implémentation INDÉPENDANTE
 * (Python `cryptography`) et vérifié par `tools/wallet/BindProofCheck.java` (logique de `BindProof.valid`) : le Kotlin doit retrouver exactement les mêmes octets.
 */
class BindProofTest {
    private val file = File(System.getProperty("wallet.bind.vector") ?: "../../tools/wallet/wallet-bind-vector.json")
    private val root: Map<String, Any?> get() = JsonLite.obj(file.readText())
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun messageIsExactlyWhatTheServerVerifies() {
        val r = root
        assertEquals(r["message"], WalletBind.message(r["deviceCode"] as String, r["apiDeviceId"] as String, r["at"] as Long))
        assertEquals("castbridge-wallet-bind-v1\nAAAA\nBBBB\n42", WalletBind.message("AAAA", "BBBB", 42))
    }

    @Test fun goldenVectorIsReproducedByteForByte() {
        val r = root
        val signer = InstallSigner(hex(r["seedHex"] as String))
        val p = WalletBind.proof(signer, r["deviceCode"] as String, r["apiDeviceId"] as String, r["at"] as Long)
        assertEquals(r["keyBase64"], p["key"])
        assertEquals(r["at"], p["at"])
        assertEquals(r["sigBase64"], p["sig"])
        assertEquals(setOf("key", "at", "sig"), p.keys)
    }

    @Test fun theServerRulesAcceptTheProofAndRefuseTheFakes() {
        val r = root
        val key = Base64.getDecoder().decode(r["keyBase64"] as String)
        val sig = Base64.getDecoder().decode(r["sigBase64"] as String)
        fun valid(code: String, dev: String, at: Long, now: Long) =
            WalletBind.inWindow(at, now) && Ed25519.verify(key, WalletBind.message(code, dev, at).toByteArray(), sig)
        val code = r["deviceCode"] as String; val dev = r["apiDeviceId"] as String; val at = r["at"] as Long
        assertTrue(valid(code, dev, at, at + 299_000))
        assertFalse(valid("2B5D-8FGH-1JKM-NPQ5", dev, at, at + 1_000))
        assertFalse(valid(code, "00000000-0000-4000-8000-000000000000", at, at + 1_000))
        assertFalse(valid(code, dev, at, at + 300_001))
    }

    @Test fun windowIsPlusOrMinusFiveMinutesInclusive() {
        val t = 1_790_000_000_000L
        assertTrue(WalletBind.inWindow(t, t))
        assertTrue(WalletBind.inWindow(t, t + 300_000)); assertTrue(WalletBind.inWindow(t, t - 300_000))
        assertFalse(WalletBind.inWindow(t, t + 300_001)); assertFalse(WalletBind.inWindow(t, t - 300_001))
    }

    @Test fun domainIsSeparateFromTheOtherSignaturesOfTheInstallKey() {
        assertEquals("castbridge-wallet-bind-v1", WalletBind.DOMAIN)
        assertNotEquals("castbridge-millions-journal-v1", WalletBind.DOMAIN)
        // un message de preuve ne peut pas être pris pour un instantané : domaine différent
        assertNotEquals(castbridge.core.wallet.Snapshot.DOMAIN, WalletBind.DOMAIN)
    }

    @Test fun differentCodeDeviceOrTimeGiveDifferentProofs() {
        val s = InstallSigner(ByteArray(32) { 7 })
        val a = WalletBind.proof(s, "7K3M-9PQ2-XH4T-V8RM", "dev-1", 1000)["sig"]
        assertNotEquals(a, WalletBind.proof(s, "2B5D-8FGH-1JKM-NPQ5", "dev-1", 1000)["sig"])
        assertNotEquals(a, WalletBind.proof(s, "7K3M-9PQ2-XH4T-V8RM", "dev-2", 1000)["sig"])
        assertNotEquals(a, WalletBind.proof(s, "7K3M-9PQ2-XH4T-V8RM", "dev-1", 1001)["sig"])
    }
}
