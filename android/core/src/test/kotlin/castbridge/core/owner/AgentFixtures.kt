package castbridge.core.owner

import castbridge.core.lots.Right
import java.security.MessageDigest

/** Test keys (derived from public strings, worth nothing), devices and builders shared by the delegation, ticket and vector tests. */
object AgentFixtures {
    const val T0 = 1_800_000_000_000L
    const val DAY = 24L * 3600 * 1000

    fun seedOf(name: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|$name".toByteArray())

    class KeyDef(val name: String, val signer: Ed25519Signer, val scopes: Set<KeyScope>) {
        fun trusted() = TrustedKey(signer.keyId, signer.publicKeyBase64, scopes)
    }

    /** `desk` holds DELEGATE (explicit); `phone` does not; `agent` and `agent2` are field-agent keys, never in a compiled ring. */
    val keys = listOf(
        KeyDef("desk", Ed25519Signer(seedOf("desk")), KeyScope.ALL + KeyScope.DELEGATE),
        KeyDef("phone", Ed25519Signer(seedOf("phone")), KeyScope.ALL - KeyScope.REGISTRY),
        KeyDef("server", Ed25519Signer(seedOf("server")), setOf(KeyScope.ISSUE_TRIAL, KeyScope.ISSUE_PRODUCTION, KeyScope.REACTIVATE, KeyScope.REVOKE, KeyScope.REGISTRY, KeyScope.POLICY)),
        KeyDef("rogue", Ed25519Signer(seedOf("rogue")), KeyScope.ALL),
        KeyDef("agent", Ed25519Signer(seedOf("agent-douala")), Delegation.ALLOWED_SCOPES),
        KeyDef("agent2", Ed25519Signer(seedOf("agent-bonamoussadi")), Delegation.ALLOWED_SCOPES),
    )
    fun key(n: String) = keys.first { it.name == n }
    fun kid(n: String) = key(n).signer.keyId

    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    class Dev(val name: String, val raw: RawFactors) { val fp = DeviceIdentity.fingerprints(raw); val code = DeviceCode.of(fp) }
    val devices = listOf(
        Dev("tvA", RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", soldered, "SYSA0001", "11:22:33:44:55:01")),
        Dev("tvN1", RawFactors("FLASHSERIAL-N1", "cid-n1", "AA:BB:CC:00:11:11", "10:20:30:40:50:11", soldered, "SYSN0001", "11:22:33:44:55:11")),
    )
    fun dev(n: String) = devices.first { it.name == n }

    /** The ring of a TV: the owner keys, no agent. */
    fun tvRing(revoked: Set<String> = emptySet()) = KeyRing(listOf("desk", "phone", "server").map { key(it).trusted() }, revoked)

    /** A mandate for [agent] signed by [owner] at [at] (90 days by default, production keys up to 365 days, no rental). */
    fun delegation(owner: String = "desk", agent: String = "agent", at: Long = T0, seq: Long = at, name: String = "douala-akwa-01", maxKeyDays: Int = 365, maxSales: Int = 200,
                   bundles: List<String> = listOf("tout"), validityDays: Int = 90, nonce: String = "a1a1a1a1a1a1a1a1", confirmOrders: Boolean = false, sellVouchers: Boolean = false, maxConfirm: Int? = null): String =
        Delegation.issue(key(owner).signer, at, seq, nonce, key(agent).signer.publicKeyBase64, name, maxKeyDays, maxSales, bundles, validityDays = validityDays,
            confirmOrders = confirmOrders, sellVouchers = sellVouchers, maxConfirmXafPerDay = maxConfirm)

    /** An activation signed by the agent key for [device]: production with a [days]-day usage ceiling by default. */
    fun activation(device: String = "tvA", agent: String = "agent", kind: ActivationKind = ActivationKind.PRODUCTION, rights: List<Right>? = null, at: Long = T0 + DAY, days: Int = 90,
                   nonce: String = "b2b2b2b2b2b2b2b2", seq: Long? = null,
                   /** The agent's own issuing scopes: widen them to forge what a mandate must refuse. */ issuerScopes: Set<KeyScope> = Delegation.ALLOWED_SCOPES): String {
        val d = dev(device)
        val r = rights ?: listOf(Right.Usage(at, at + days * DAY))
        val license = if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else "lic-0001"
        return ActivationIssuer(key(agent).signer, issuerScopes).issue(ActivationIssuer.Request(kind, d.code, d.fp, at, rights = r, license = license, nonce = nonce, seq = seq)).token
    }
}
