package castbridge.core.lots

import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.Hkdf
import castbridge.core.owner.LotKeys
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto-shredding of rented content (docs/RENTAL-LOTS.md § 3). A rental has ONE random-looking key per (licence, seat, product, period), derived by the issuer from its secret master
 * ([rentalKey]); the key of one lot is derived from it ([lotKey]) and encrypts the lot file ([seal]/[open], AES-256-GCM, id and version authenticated). The rental key travels INSIDE the rental
 * right as a [makeBox]: wrapped once per subset of k of the TV's factors (same tolerance as the other lots, [LotKeys.kek]). The TV opens the box once, keeps the key in its vault, and at the end of
 * the rental DESTROYS it (rewrite, then delete): a copied or restored lot file is then noise. Deterministic (same inputs, same bytes) so the common vectors can pin it.
 */
object RentalKeys {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun hkdf(ikm: ByteArray, salt: String, info: String, n: Int) = Hkdf.expand(Hkdf.extract(salt.toByteArray(), ikm), info.toByteArray(), n)

    /**
     * The default master of a tool: the hash of its own deterministic signature of a fixed public text. Only the holder of the signing key can compute it, nothing new to store or back up
     * (it is as safe as the key itself and changes with it). A shared master between the desk, the owner phone and the server is the owner's decision (docs/RENTAL-LOTS.md § 3).
     */
    fun masterFrom(signer: castbridge.core.owner.Signer): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(signer.sign("castbridge-rental-master-v1".toByteArray()))

    /** [master] = the issuer's secret (never in the repository, never on a TV). Seat, not hardware set, so a replaced Wi-Fi module keeps the same key. */
    fun rentalKey(master: ByteArray, license: String, seat: String, productId: String, period: Long): ByteArray {
        require(master.size >= 16)
        return hkdf(master, "castbridge-rental-v1", "key|$license|$seat|$productId|$period", 32)
    }

    fun lotKey(rentalKey: ByteArray, lot: LotId, version: Int): ByteArray = hkdf(rentalKey, "castbridge-rental-lot-v1", "lot|${LotNames.key(lot)}|$version", 32)

    private fun aad(lot: LotId, version: Int) = "castbridge-rental-lot-v1|${LotNames.key(lot)}|$version".toByteArray()

    /** nonce(12) + ciphertext + tag. The nonce is derived (the lot key is unique per lot version, and so is the plaintext): the vectors stay deterministic. */
    fun seal(rentalKey: ByteArray, lot: LotId, version: Int, plain: ByteArray): ByteArray {
        val key = lotKey(rentalKey, lot, version)
        val nonce = hkdf(key, "castbridge-rental-nonce-v1", "nonce", 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); c.updateAAD(aad(lot, version))
        return nonce + c.doFinal(plain)
    }

    /** The plaintext, or null (wrong or destroyed key, other lot or version, altered file). */
    fun open(rentalKey: ByteArray?, lot: LotId, version: Int, blob: ByteArray): ByteArray? {
        if (rentalKey == null || rentalKey.size != 32 || blob.size < 12 + 16) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(lotKey(rentalKey, lot, version), "AES"), GCMParameterSpec(128, blob.copyOf(12)))
            c.updateAAD(aad(lot, version)); c.doFinal(blob, 12, blob.size - 12)
        }.getOrNull()
    }

    private fun subsets(items: List<FactorKind>, k: Int): List<List<FactorKind>> {
        if (k <= 0) return listOf(emptyList())
        if (items.size < k) return emptyList()
        if (items.size == k) return listOf(items)
        return subsets(items.drop(1), k - 1).map { listOf(items.first()) + it } + subsets(items.drop(1), k)
    }

    private fun boxAad(productId: String, period: Long, names: String) = "castbridge-rentalbox-v1|$productId|$period|$names".toByteArray()

    private fun gcm(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray, data: ByteArray, off: Int = 0): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); c.updateAAD(aad)
        return c.doFinal(data, off, data.size - off)
    }

    /** `KIND+KIND:<base64url>;…` text for the right line: [rentalKey] wrapped once per subset of [k] of the device's factors (the factor VALUES stay secret to whoever lacks the TV). */
    fun makeBox(fp: Fingerprints, k: Int, rentalKey: ByteArray, productId: String, period: Long): String {
        require(rentalKey.size == 32 && k in 1..fp.n)
        return subsets(fp.byKind.keys.toList(), k).map { s -> s.sortedBy { it.name } }.sortedBy { it.joinToString("+") { f -> f.name } }.joinToString(";") { s ->
            val names = s.joinToString("+") { it.name }
            val kek = LotKeys.kek(s.associateWith { fp.byKind.getValue(it) })
            val nonce = hkdf(kek, "castbridge-rentalbox-nonce-v1", "$productId|$period|$names", 12)
            names + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + gcm(Cipher.ENCRYPT_MODE, kek, nonce, boxAad(productId, period, names), rentalKey))
        }
    }

    /** The rental key if at least k of the CURRENT factors match one wrap; null otherwise (other device, altered box). */
    fun openBox(box: String, current: Fingerprints, productId: String, period: Long): ByteArray? {
        for (part in box.split(';').filter { it.isNotEmpty() }) {
            val names = part.substringBefore(':'); val blob = runCatching { Base64.getUrlDecoder().decode(part.substringAfter(':')) }.getOrNull() ?: continue
            val kinds = names.split('+').map { n -> FactorKind.values().firstOrNull { it.name == n } ?: return@map null }
            if (kinds.any { it == null } || kinds.any { it !in current.byKind } || blob.size < 12 + 16) continue
            val kek = LotKeys.kek(kinds.filterNotNull().associateWith { current.byKind.getValue(it) })
            runCatching { gcm(Cipher.DECRYPT_MODE, kek, blob.copyOf(12), boxAad(productId, period, names), blob, 12) }.getOrNull()?.takeIf { it.size == 32 }?.let { return it }
        }
        return null
    }

    fun fingerprintOf(key: ByteArray) = hex(java.security.MessageDigest.getInstance("SHA-256").digest(key)).take(16)
}
