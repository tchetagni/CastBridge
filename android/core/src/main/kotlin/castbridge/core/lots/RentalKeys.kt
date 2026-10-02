package castbridge.core.lots

import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.Hkdf
import castbridge.core.owner.LotKeys
import castbridge.core.owner.X25519
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

    /**
     * v1 box, `KIND+KIND:<base64url>;…`: [rentalKey] wrapped once per subset of [k] of the device's factors. WEAK, kept only to read old activations until [V1_BOX_SUNSET_MS]: the
     * fingerprints are PUBLIC (they are in the activation target and in the device request), so whoever holds the activation can open this box without the TV. New boxes are [makeBoxV2].
     */
    fun makeBox(fp: Fingerprints, k: Int, rentalKey: ByteArray, productId: String, period: Long): String {
        require(rentalKey.size == 32 && k in 1..fp.n)
        return subsets(fp.byKind.keys.toList(), k).map { s -> s.sortedBy { it.name } }.sortedBy { it.joinToString("+") { f -> f.name } }.joinToString(";") { s ->
            val names = s.joinToString("+") { it.name }
            val kek = LotKeys.kek(s.associateWith { fp.byKind.getValue(it) })
            val nonce = hkdf(kek, "castbridge-rentalbox-nonce-v1", "$productId|$period|$names", 12)
            names + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + gcm(Cipher.ENCRYPT_MODE, kek, nonce, boxAad(productId, period, names), rentalKey))
        }
    }

    /** First instant (2027-01-01T00:00Z) from which an activation carrying a v1 box is no longer accepted: `issuedAt` before it is read, at or after it is refused. */
    const val V1_BOX_SUNSET_MS = 1798761600000L

    fun isV1Accepted(issuedAt: Long) = issuedAt < V1_BOX_SUNSET_MS

    private const val V2 = "v2:"
    private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun boxAadV2(productId: String, period: Long, installPub: ByteArray) = "castbridge-rentalbox-v2|$productId|$period|${hex(installPub)}".toByteArray()
    private fun kekV2(shared: ByteArray, installPub: ByteArray, ephPub: ByteArray, productId: String, period: Long) =
        hkdf(shared, "castbridge-rentalbox-v2", "kek|${hex(installPub)}|${hex(ephPub)}|$productId|$period", 32)

    /**
     * v2 box, `v2:<b64url ephemeral public key>:<b64url nonce ‖ AES-256-GCM(rentalKey)>`: ECDH between a one-shot X25519 key (from [ephSeed]) and the TV's [installPub], then HKDF, then
     * AES-GCM bound to product, period and installation. Only the holder of the installation's private key opens it. [ephSeed] (32 bytes) must be fresh random in production
     * (`SecureRandom`); the vectors give a fixed one so that the same inputs give the same bytes. Throws [IllegalArgumentException] for a wrong size or an installation key of small order.
     */
    fun makeBoxV2(installPub: ByteArray, rentalKey: ByteArray, productId: String, period: Long, ephSeed: ByteArray): String {
        require(rentalKey.size == 32 && installPub.size == 32 && ephSeed.size == 32) { "tailles de clé invalides" }
        val ephPub = X25519.publicKey(ephSeed)
        val shared = X25519.sharedSecret(ephSeed, installPub) ?: throw IllegalArgumentException("clé d'installation invalide")
        val kek = kekV2(shared, installPub, ephPub, productId, period)
        val nonce = hkdf(kek, "castbridge-rentalbox-nonce-v2", "nonce", 12)
        return V2 + b64(ephPub) + ":" + b64(nonce + gcm(Cipher.ENCRYPT_MODE, kek, nonce, boxAadV2(productId, period, installPub), rentalKey))
    }

    /**
     * Opens a box of either generation. A v2 box needs [install]; its failure to open means another installation (the AAD binds the public key) or an altered box. A v1 box is opened with
     * the factors, and refused with [BoxResult.V1Expired] when [issuedAt] (the activation's, null = not checked) is at or after [V1_BOX_SUNSET_MS]. A box is v1 OR v2, never both:
     * a `v2:` box with another part beside it is [BoxResult.Unreadable].
     */
    fun openBox(box: String, current: Fingerprints, productId: String, period: Long, install: InstallKey? = null, issuedAt: Long? = null): BoxResult {
        if (!box.startsWith(V2)) {
            if (issuedAt != null && !isV1Accepted(issuedAt)) return BoxResult.V1Expired
            return openV1(box, current, productId, period)?.let { BoxResult.Key(it) } ?: BoxResult.Unreadable
        }
        if (';' in box) return BoxResult.Unreadable
        val parts = box.split(':')
        if (parts.size != 3) return BoxResult.Unreadable
        val dec = Base64.getUrlDecoder()
        val ephPub = runCatching { dec.decode(parts[1]) }.getOrNull()?.takeIf { it.size == 32 } ?: return BoxResult.Unreadable
        val blob = runCatching { dec.decode(parts[2]) }.getOrNull()?.takeIf { it.size == 12 + 32 + 16 } ?: return BoxResult.Unreadable
        if (install == null) return BoxResult.NeedsInstallKey
        val shared = X25519.sharedSecret(install.priv, ephPub) ?: return BoxResult.Unreadable
        val kek = kekV2(shared, install.pub, ephPub, productId, period)
        val key = runCatching { gcm(Cipher.DECRYPT_MODE, kek, blob.copyOf(12), boxAadV2(productId, period, install.pub), blob, 12) }.getOrNull()?.takeIf { it.size == 32 }
        return if (key != null) BoxResult.Key(key) else BoxResult.OtherInstall
    }

    /** v1 only (kept for the old callers): the rental key if at least k of the CURRENT factors match one wrap; null otherwise (other device, altered box). */
    @Deprecated("v1 seulement : utiliser openBox(box, current, productId, period, install, issuedAt) qui lit aussi la v2")
    fun openBox(box: String, current: Fingerprints, productId: String, period: Long): ByteArray? = if (box.startsWith(V2)) null else openV1(box, current, productId, period)

    private fun openV1(box: String, current: Fingerprints, productId: String, period: Long): ByteArray? {
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

/** What [RentalKeys.openBox] found. */
sealed class BoxResult {
    /** The 32-byte rental key. */
    class Key(val bytes: ByteArray) : BoxResult()
    /** A v2 box that this installation's key does not open: boxed for another installation of the TV (ask for a reissue), or altered. */
    object OtherInstall : BoxResult()
    /** A v2 box but no installation key was given. */
    object NeedsInstallKey : BoxResult()
    /** A v1 box in an activation issued at or after [RentalKeys.V1_BOX_SUNSET_MS]. */
    object V1Expired : BoxResult()
    /** Malformed, mixed v1/v2, small-order point, wrong factors or altered v1 box. */
    object Unreadable : BoxResult()
}
