package castbridge.core.owner

import castbridge.core.lots.Right
import java.util.Base64

/**
 * The claim `ik|<64 hex>` of a PRODUCTION activation (second audit w23-05, MEDIUM-C): the installation key of the TV the owner's tool signed the activation for. The device request is not signed,
 * so a man in the middle can swap its `install_sig=` line; the issuer cannot know. The TV can: [check] refuses an activation whose `ik` is not ITS OWN installation key, with a clear French
 * reason and both fingerprints (the owner compares them with the activation screen of the TV). Pure: no clock, no storage, no Android.
 *
 * An activation WITHOUT `ik` (every one issued before the correction) and a TV that cannot read its own key ([check] with `null`) are never refused here: nothing changes for them.
 */
object ActivationBinding {
    const val REFUSAL = "Cette activation a été préparée pour une autre clé d'installation"
    private const val PREFIX = "ik|"
    private val HEX = Regex("[0-9a-f]{64}")

    /** The 32-byte key signed in the activation, or null when it carries none (or an unreadable claim). */
    fun installKeyOf(a: Activation): ByteArray? {
        for (r in a.rights) {
            if (r !is Right.Unknown || !r.raw.startsWith(PREFIX)) continue
            val h = r.raw.removePrefix(PREFIX)
            if (!HEX.matches(h)) return null
            return h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        return null
    }

    /** Null when the activation may be installed on the TV whose installation key is [ownKey]; otherwise the refusal to show (not `suspect`: no manual unlock is offered). */
    fun check(a: Activation, ownKey: ByteArray?): ActivationResult.Rejected? {
        val ik = installKeyOf(a) ?: return null
        if (ownKey == null || ik.contentEquals(ownKey)) return null
        return ActivationResult.Rejected(Rejection.WRONG_DEVICE,
            "$REFUSAL : empreinte attendue ${fingerprint(ik)}, empreinte de cette TV ${fingerprint(ownKey)}. Demandez une nouvelle activation en comparant l'empreinte de l'écran de la TV avec celle de l'outil d'émission")
    }

    /** 8 groups of 4 lowercase hex characters: the first 16 bytes of SHA-256 of the raw key (the same text as the TV screen and the issuers). */
    fun fingerprint(raw: ByteArray): String = InstallSigner.fingerprintOf(Base64.getEncoder().encodeToString(raw))

    /** A fingerprint typed or pasted (any case, with or without `-` and spaces) as its canonical 32 lowercase hex characters, or null. */
    fun normalizeFingerprint(typed: String): String? = typed.filter { !it.isWhitespace() && it != '-' }.lowercase().takeIf { Regex("[0-9a-f]{32}").matches(it) }
}
