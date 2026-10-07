package castbridge.play.chess

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.quiz.Json
import castbridge.play.StakeServices
import castbridge.play.stake.EscrowGate
import castbridge.play.stake.ResultSpool
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Le matériel de test des parties misées : clés DE TEST (dérivées de chaînes publiques : elles ne valent rien) et fabrique de blocages `cbe1` signés comme le fait l'API (domaine
 * `castbridge-wallet-escrow-v1`). Le service vérifie VRAIMENT les signatures ; les résultats `cbr1` qu'il produit sont relus avec la clé publique de [resultSigner], comme le fait l'API.
 */
object StakeKit {
    private fun seed(name: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-play-chess-test|$name".toByteArray())

    val walletSigner = Ed25519Signer(seed("wallet"))
    val otherWalletSigner = Ed25519Signer(seed("rogue-wallet"))
    val resultSigner = Ed25519Signer(seed("result"))
    /** Clé publique « portefeuille » de test, au format de `CASTBRIDGE_PLAY_WALLET_PUBKEY` (32 octets bruts en Base64). */
    val walletPub: String = walletSigner.publicKeyBase64
    val resultRing = KeyRing(listOf(resultSigner.trusted()))

    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val rnd = SecureRandom()

    fun eid(): String = b64.encodeToString(ByteArray(16).also { rnd.nextBytes(it) })

    /** Un blocage `cbe1` : par défaut valable 30 min, signé par la clé « portefeuille » de test. */
    fun escrow(id: String, cur: String = "NDEM", per: Long = 20, k: Int = 1, eid: String = eid(), now: Long = System.currentTimeMillis(), lifeMs: Long = 30 * 60_000L,
               aud: String = "castbridge-play", signer: Ed25519Signer = walletSigner, iat: Long = now, amt: Long = per * k): String {
        val payload = linkedMapOf<String, Any?>("aud" to aud, "kid" to signer.keyId, "eid" to eid, "id" to id, "cur" to cur, "per" to per, "k" to k.toLong(), "amt" to amt, "iat" to iat, "exp" to iat + lifeMs)
        val body = b64.encodeToString(Json.write(payload).toByteArray(Charsets.UTF_8))
        val sig = signer.sign("castbridge-wallet-escrow-v1\ncbe1.$body".toByteArray(Charsets.US_ASCII))
        return "cbe1.$body." + b64.encodeToString(sig)
    }

    /** L'identifiant de blocage (`eid`) lu dans la charge d'un `cbe1` (sans rien vérifier : pour les assertions). */
    fun eidOf(token: String): String = (Json.parse(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)) as Map<*, *>)["eid"] as String

    fun tempDir(): File = Files.createTempDirectory("castbridge-results-").toFile().also { it.deleteOnExit() }

    /** Un fichier de clé « résultat » comme celui que le service monte en secret : la graine de [resultSigner] en Base64 (format lu par `ResultKey`). Pour les tests qui démarrent un VRAI [castbridge.play.PlayServer]. */
    fun resultKeyFile(): File = Files.createTempFile("castbridge-result-key-", ".b64").toFile().also {
        it.deleteOnExit(); it.writeText(Base64.getEncoder().encodeToString(seed("result")) + "\n")
    }

    /** Les services de mise d'un hub de test : porte (clé publique de test), clé « résultat » de test, dépôt dans [dir]. */
    fun services(dir: File = tempDir(), maxNdem: Long = 1_000, maxMboko: Long = 100): StakeServices = StakeServices(EscrowGate(listOf(walletPub), maxNdem, maxMboko), resultSigner, ResultSpool(dir))
}
