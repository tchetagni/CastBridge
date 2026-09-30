package castbridge.core.update

/**
 * Public key of the update server, embedded in the apps: a manifest it does not verify is ignored.
 *
 * TO FILL AT DEPLOYMENT (see backend/README.md, "Clés Ed25519"): the value of
 * `curl https://<server>/api/v1/updates/public-key` → "publicKey" (raw 32 bytes in base64), or
 * `openssl pkey -in castbridge-signing.pem -pubout -outform DER | tail -c 32 | base64`.
 * Empty = automatic updates disabled (nothing can be verified).
 *
 * Changing the key means the apps already installed will refuse the new manifests: publish first an update signed
 * with the old key that embeds the new one (or keep both in [PUBLIC_KEYS] during the transition).
 */
object UpdateKeys {
    /** Production key of https://bridge.sti-cm.com (keyId 6102414005737206), created 2026-09-30. */
    const val PUBLIC_KEY: String = "SItJOVQ7KfItg1kksDbdqId9fWnkMDhU7MD5YU46rd0="

    /** Every accepted key (the current one, plus the next one during a key change). */
    val PUBLIC_KEYS: List<String> = listOf(PUBLIC_KEY).filter { it.isNotBlank() }
}
