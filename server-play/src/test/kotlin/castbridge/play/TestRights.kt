package castbridge.play

import castbridge.core.lots.Right
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationIssuer
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.KeyScope
import castbridge.core.owner.RawFactors
import castbridge.core.quiz.online.ClientMsg
import java.security.MessageDigest

/**
 * Émetteur d'activations `cbx1` DE TEST (clés dérivées de chaînes publiques : elles ne valent rien) et appareils de test. Le service vérifie VRAIMENT les signatures :
 * ces clés sont les « clés de confiance » ([trustedKeys], format `CASTBRIDGE_PLAY_TRUSTED_KEYS`) des tests.
 */
object TestRights {
    const val DAY = 24L * 3600 * 1000
    private fun seed(name: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("castbridge-play-test-issuer|$name".toByteArray())

    val issuer = Ed25519Signer(seed("desk"))
    val rogue = Ed25519Signer(seed("rogue"))
    private val scopes = "ISSUE_TRIAL+ISSUE_PRODUCTION+REVOKE"
    /** Valeur de `CASTBRIDGE_PLAY_TRUSTED_KEYS` pour les tests. */
    val trustedSpec: String = "desk:${issuer.publicKeyBase64}:$scopes"
    val trustedKeys: List<String> = listOf(trustedSpec)

    class Tv(val name: String) {
        val fp: Fingerprints = DeviceIdentity.fingerprints(RawFactors("FLASH-$name", "cid-$name", "AA:BB:CC:00:11:${"%02x".format(name.hashCode() and 0xff)}", null, null, "SYS-$name", null))
        val code: String = DeviceCode.of(fp)
    }

    val tv = Tv("A")
    val CODE: String = tv.code
    val otherTv = Tv("B")

    fun activation(kind: ActivationKind = ActivationKind.PRODUCTION, device: Tv = tv, now: Long = System.currentTimeMillis(), rights: List<Right> = emptyList(),
                   signer: Ed25519Signer = issuer, license: String = if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else "lic-0001", seq: Long? = null,
                   issuedAt: Long = now - 3_600_000L): String =
        ActivationIssuer(signer, KeyScope.ALL).issue(ActivationIssuer.Request(kind, device.code, device.fp, issuedAt, rights = rights, license = license, seq = seq)).token

    /** Une location d'un bouquet [bundle] (identifiant de lot = identifiant de bouquet pour le Quiz), [days] jours à partir de [startsAt]. */
    fun rental(bundle: String, startsAt: Long, days: Int = 30, product: String = "loc-$bundle") =
        Right.Rental(product, listOf(bundle), startsAt, startsAt, days)

    fun trialUsage(now: Long = System.currentTimeMillis()) = listOf(Right.Usage(now - 3_600_000L, now + 29 * DAY))

    /** Production sans location (jeton frais à chaque appel de `lazy` : le service juge à l'heure de son horloge). */
    val PROD: String by lazy { activation() }
    val TRIAL: String by lazy { activation(ActivationKind.TRIAL, rights = trialUsage()) }

    fun create(activation: String? = PROD, rentals: List<String> = emptyList(), name: String? = null, mode: String = "DUEL") = ClientMsg.Create(name, mode, activation, rentals)
}
