package castbridge.core.activation

/**
 * Shared secret of the offline activation tokens, embedded in the TV app (and held by the seller's generator, the
 * /admin/activation page of the server).
 *
 * TO FILL AT DEPLOYMENT: the same value as CASTBRIDGE_ACTIVATION_SECRET on the server. Empty = activation disabled
 * (every TV is considered activated). A determined attacker can extract a secret embedded in the APK; if that matters,
 * switch to an Ed25519 signature (public key in the app, private key held only by the seller).
 */
object ActivationKeys {
    const val SECRET: String = ""
}
