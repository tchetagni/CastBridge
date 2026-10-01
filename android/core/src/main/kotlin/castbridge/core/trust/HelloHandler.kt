package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.HelloInfo
import castbridge.core.tv.HelloReply
import castbridge.core.tv.LinkInfo

/**
 * TV side of the HELLO message: decides who gets the TV's Wi-Fi address and a token.
 *
 * - [isBonded] must be true: Android itself says this address has a pairing (link key) with the TV. A secure RFCOMM socket
 *   already guarantees it; the check stays as a second lock (a phone unpaired from the TV's Bluetooth settings loses access at once).
 * - Trusted phone -> answer with a fresh token.
 * - Unknown phone that asked for trust -> [PairingSession.ask] (the owner must press OK on the TV, in his own window).
 * - Anybody else -> ERR_UNTRUSTED and nothing else: no name, no address, no token, not even the TV's version.
 */
class HelloHandler(
    private val registry: TrustRegistry,
    private val pairing: PairingSession,
    private val isBonded: (String) -> Boolean,
    private val tvName: () -> String,
    private val version: String,
    private val mdnsName: () -> String?,
    private val link: () -> LinkInfo,
    /** A trusted phone just connected (for the « téléphone connecté » banner). */
    private val onConnected: (TrustedPhone) -> Unit = {},
    /** Storm control: a phone (or all of them) asking far too often is told "busy, later" (ERR_BUSY, which the phone treats as transient). */
    private val limiter: AttemptLimiter? = null,
) {
    /** What an unknown phone is told: nothing but "no", plus (only when it is paired and gave the install id it remembers) whether the TV is another installation. */
    private fun untrusted(paired: Boolean) = HelloReply.Err(BtProtocol.ERR_UNTRUSTED,
        if (!paired) null else { claimed -> if (claimed == registry.installId) BtProtocol.HINT_SAME_INSTALL else BtProtocol.HINT_OTHER_INSTALL })

    fun handle(peer: String, peerName: String?, requestTrust: Boolean): HelloReply {
        if (!TrustRegistry.isAddress(peer) || !isBonded(peer)) return untrusted(false)
        if (limiter != null && limiter.tryAcquire(TrustRegistry.norm(peer)) > 0) return HelloReply.Err(BtProtocol.ERR_BUSY)
        if (!registry.isTrusted(peer)) {
            if (!requestTrust) return untrusted(true)
            when (pairing.ask(peer, peerName.orEmpty())) {
                PairingSession.Decision.APPROVED -> {}
                PairingSession.Decision.DENIED -> return HelloReply.Err(BtProtocol.ERR_DENIED)
                PairingSession.Decision.TIMEOUT -> return HelloReply.Err(BtProtocol.ERR_TIMEOUT)
                PairingSession.Decision.NOT_OPEN -> return HelloReply.Err(BtProtocol.ERR_NOT_OPEN)
                PairingSession.Decision.BUSY, PairingSession.Decision.BLOCKED -> return HelloReply.Err(BtProtocol.ERR_BUSY)
            }
        }
        val t = registry.issueToken(peer) ?: return untrusted(true)   // revoked while we waited
        registry.get(peer)?.let(onConnected)
        return HelloReply.Ok(HelloInfo(tvName(), version, mdnsName(), t.token, registry.tokenTtlMs / 1000, link(), registry.installId))
    }
}
