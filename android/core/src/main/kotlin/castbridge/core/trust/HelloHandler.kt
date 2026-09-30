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
) {
    fun handle(peer: String, peerName: String?, requestTrust: Boolean): HelloReply {
        if (!TrustRegistry.isAddress(peer) || !isBonded(peer)) return HelloReply.Err(BtProtocol.ERR_UNTRUSTED)
        if (!registry.isTrusted(peer)) {
            if (!requestTrust) return HelloReply.Err(BtProtocol.ERR_UNTRUSTED)
            when (pairing.ask(peer, peerName.orEmpty())) {
                PairingSession.Decision.APPROVED -> {}
                PairingSession.Decision.DENIED -> return HelloReply.Err(BtProtocol.ERR_DENIED)
                PairingSession.Decision.TIMEOUT -> return HelloReply.Err(BtProtocol.ERR_TIMEOUT)
                PairingSession.Decision.NOT_OPEN -> return HelloReply.Err(BtProtocol.ERR_NOT_OPEN)
                PairingSession.Decision.BUSY, PairingSession.Decision.BLOCKED -> return HelloReply.Err(BtProtocol.ERR_BUSY)
            }
        }
        val t = registry.issueToken(peer) ?: return HelloReply.Err(BtProtocol.ERR_UNTRUSTED)   // revoked while we waited
        registry.get(peer)?.let(onConnected)
        return HelloReply.Ok(HelloInfo(tvName(), version, mdnsName(), t.token, registry.tokenTtlMs / 1000, link()))
    }
}
