package castbridge.core.owner

import castbridge.core.lots.LotId
import castbridge.core.lots.LotNames
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Hkdf {
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256")); doFinal(data) }
    fun extract(salt: ByteArray, ikm: ByteArray) = hmac(salt, ikm)
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); var t = ByteArray(0); var i = 1
        while (out.size() < length) { t = hmac(prk, t + info + byteArrayOf(i.toByte())); out.write(t); i++ }
        return out.toByteArray().copyOf(length)
    }
}

/**
 * Per-TV encryption of the paid lots (docs/TRIAL-EDITION.md § Lots chiffrés). The trial lots are NOT encrypted (public sample). Each paid lot has its
 * own random key; on a TV it is wrapped by a random per-TV data key [DeviceKeyBox], which is itself wrapped under key-encryption keys derived (HKDF) from
 * subsets of k of the TV's hardware factors, so that the k-of-n identity tolerance also holds for decryption (a replaced Wi-Fi module does not lose the content).
 * They survive an uninstall of the app because they depend on the hardware and on files, not on an Android Keystore key.
 * SECURITY EFFECT, stated plainly: whoever knows enough of a TV's factors can derive its KEK; this stops casual copying of the lot files to another TV, not a
 * determined attacker who has the TV's identifiers, and a TV that decrypted a lot keeps the plaintext (see the limits in the document).
 */
object LotKeys {
    private val random = SecureRandom()

    fun newKey(rnd: SecureRandom = random): ByteArray = ByteArray(32).also(rnd::nextBytes)

    private fun aad(vararg parts: String) = parts.joinToString("|").toByteArray(Charsets.UTF_8)

    private fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 32 && nonce.size == 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); c.updateAAD(aad)
        return nonce + c.doFinal(plain)
    }

    private fun openGcm(key: ByteArray, blob: ByteArray, aad: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOf(12))); c.updateAAD(aad)
        c.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()

    /** A lot key wrapped under the TV's data key; the id and version are authenticated, so a wrapped key cannot be moved to another lot. */
    fun wrapLotKey(dataKey: ByteArray, lotKey: ByteArray, lot: LotId, version: Int, rnd: SecureRandom = random) =
        seal(dataKey, lotKey, aad("castbridge-lotkey-v1", LotNames.key(lot), version.toString()), ByteArray(12).also(rnd::nextBytes))

    fun unwrapLotKey(dataKey: ByteArray, wrapped: ByteArray, lot: LotId, version: Int): ByteArray? = openGcm(dataKey, wrapped, aad("castbridge-lotkey-v1", LotNames.key(lot), version.toString()))

    fun kek(factors: Map<FactorKind, String>): ByteArray {
        val ikm = factors.toSortedMap().entries.joinToString("\n") { "${it.key.name}=${it.value}" }.toByteArray(Charsets.UTF_8)
        return Hkdf.expand(Hkdf.extract("castbridge-kek-v1".toByteArray(), ikm), "kek".toByteArray(), 32)
    }

    /** The data key of one TV, wrapped once per subset of [k] factors. Built by whoever knows the TV's fingerprints (server, or the console in the offline phase). */
    class DeviceKeyBox(val k: Int, val wraps: List<Pair<Set<FactorKind>, ByteArray>>) {
        companion object {
            fun create(fp: Fingerprints, k: Int, dataKey: ByteArray, rnd: SecureRandom = random): DeviceKeyBox {
                val kinds = fp.byKind.keys.toList()
                return DeviceKeyBox(k, subsets(kinds, k).map { s ->
                    s.toSet() to seal(kek(s.associateWith { fp.byKind.getValue(it) }), dataKey, aad("castbridge-datakey-v1", s.sortedBy { it.name }.joinToString(",") { it.name }), ByteArray(12).also(rnd::nextBytes))
                })
            }
        }

        /** The data key if at least [k] of the TV's CURRENT factors match one wrap; null otherwise. */
        fun open(current: Fingerprints): ByteArray? {
            for ((subset, blob) in wraps) {
                if (subset.any { it !in current.byKind }) continue
                val key = kek(subset.associateWith { current.byKind.getValue(it) })
                openGcm(key, blob, aad("castbridge-datakey-v1", subset.sortedBy { it.name }.joinToString(",") { it.name }))?.let { return it }
            }
            return null
        }
    }

    private fun <T> subsets(items: List<T>, k: Int): List<List<T>> {
        if (k <= 0) return listOf(emptyList())
        if (items.size < k) return emptyList()
        if (items.size == k) return listOf(items)
        return subsets(items.drop(1), k - 1).map { listOf(items.first()) + it } + subsets(items.drop(1), k)
    }
}
