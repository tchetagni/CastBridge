package castbridge.core.wallet.ui

import castbridge.core.owner.InstallSigner
import kotlin.math.abs

/**
 * Preuve de possession de la TV pour `POST /api/v1/wallet/sync` (côté serveur : `BindProof`, audit M5) : une `cbx1` copiée ne suffit pas à lier une identité à un appareil.
 * La TV signe, avec sa clé d'installation Ed25519 ([InstallSigner]) et SON domaine `castbridge-wallet-bind-v1`, le message `domaine \n code \n identifiant public de l'appareil API \n heure(ms)` ;
 * la preuve est liée à CE code, à CET appareil API et à l'heure du serveur (± 5 minutes). Corps : `"bind": {"key": <clé publique brute, base64>, "at": <ms>, "sig": <base64>}`.
 * Le vecteur doré `tools/wallet/wallet-bind-vector.json` (implémentation indépendante) fixe les octets ; `tools/wallet/BindProofCheck.java` y applique la logique du serveur.
 */
object WalletBind {
    const val DOMAIN = "castbridge-wallet-bind-v1"
    const val WINDOW_MS = 5 * 60_000L

    fun message(code: String, apiDeviceId: String, atMs: Long): String = "$DOMAIN\n$code\n$apiDeviceId\n$atMs"

    fun proof(signer: InstallSigner, code: String, apiDeviceId: String, atMs: Long): Map<String, Any?> =
        linkedMapOf("key" to signer.publicKeyBase64, "at" to atMs, "sig" to signer.sign(message(code, apiDeviceId, atMs)))

    /** La fenêtre du serveur : l'heure de la preuve à ± 5 minutes de l'heure du serveur, bornes comprises. */
    fun inWindow(atMs: Long, serverNowMs: Long): Boolean = abs(serverNowMs - atMs) <= WINDOW_MS
}
