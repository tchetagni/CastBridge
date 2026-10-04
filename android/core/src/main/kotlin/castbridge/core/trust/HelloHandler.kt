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
    /** Storm control (not applied to the owner-driven « Ajouter un téléphone » requests): a phone (or all of them) asking far too often is told "busy, later" (ERR_BUSY, which the phone treats as transient). */
    private val limiter: AttemptLimiter? = null,
    /** The owner's window refused a phone that asked (denied, timed out, blocked, busy...): for a clear message on the TV. */
    private val onRefused: (name: String, decision: PairingSession.Decision) -> Unit = { _, _ -> },
    /** The 8-phone cap (see [TrustRegistry.MAX_PHONES]): a ninth phone waits for the owner to choose which one to remove. */
    private val capacity: PairCapacityFlow? = null,
) {
    /** What an unknown phone is told: nothing but "no", plus (only when it is paired and gave the install id it remembers) whether the TV is another installation. */
    private fun untrusted(paired: Boolean) = HelloReply.Err(BtProtocol.ERR_UNTRUSTED,
        if (!paired) null else { claimed -> if (claimed == registry.installId) BtProtocol.HINT_SAME_INSTALL else BtProtocol.HINT_OTHER_INSTALL })

    /** The owner's window: null = approved (the phone is trusted now), else what the phone is told. */
    private fun askOwner(peer: String, peerName: String?): HelloReply.Err? {
        val decision = pairing.ask(peer, peerName.orEmpty())
        if (decision != PairingSession.Decision.APPROVED && decision != PairingSession.Decision.NOT_OPEN) runCatching { onRefused(PhoneName.sanitize(peerName), decision) }
        return when (decision) {
            PairingSession.Decision.APPROVED -> null
            PairingSession.Decision.DENIED -> HelloReply.Err(BtProtocol.ERR_DENIED)
            PairingSession.Decision.TIMEOUT -> HelloReply.Err(BtProtocol.ERR_TIMEOUT)
            PairingSession.Decision.NOT_OPEN -> HelloReply.Err(BtProtocol.ERR_NOT_OPEN)
            PairingSession.Decision.BUSY, PairingSession.Decision.BLOCKED -> HelloReply.Err(BtProtocol.ERR_BUSY)
            PairingSession.Decision.WRITE_FAILED -> HelloReply.Err(BtProtocol.ERR_IO)
            PairingSession.Decision.FULL -> {   // approved at the same time as another phone: the cap won; the owner decides who goes
                capacity?.ask(peer, peerName.orEmpty(), windowOpen = true)
                HelloReply.Err(BtProtocol.ERR_FULL)
            }
        }
    }

    fun handle(peer: String, peerName: String?, requestTrust: Boolean): HelloReply {
        if (!TrustRegistry.isAddress(peer) || !isBonded(peer)) return untrusted(false)
        if (!requestTrust && limiter != null && limiter.tryAcquire(TrustRegistry.norm(peer)) > 0) return HelloReply.Err(BtProtocol.ERR_BUSY)
        if (!registry.isTrusted(peer)) {
            if (!requestTrust) return untrusted(true)
            // The 8-phone cap: a ninth phone never reaches the approval dialog; the owner first chooses which phone to remove (PairCapacityFlow).
            if (capacity != null && pairing.isBlocked(peer)) return HelloReply.Err(BtProtocol.ERR_BUSY)   // refused / cancelled too often: never reopens the owner's screen
            capacity?.let { c ->
                when (c.ask(peer, peerName.orEmpty(), pairing.isOpen)) {
                    PairCapacityFlow.Answer.Pending -> return HelloReply.Err(BtProtocol.ERR_FULL)
                    PairCapacityFlow.Answer.Busy -> return HelloReply.Err(BtProtocol.ERR_BUSY)
                    PairCapacityFlow.Answer.Cancelled -> return HelloReply.Err(BtProtocol.ERR_FULL_CANCELED)
                    PairCapacityFlow.Answer.TimedOut -> return HelloReply.Err(BtProtocol.ERR_FULL_TIMEOUT)
                    PairCapacityFlow.Answer.Room, PairCapacityFlow.Answer.Trusted -> {}
                }
            }
            if (!registry.isTrusted(peer)) askOwner(peer, peerName)?.let { return it }
        }
        val t = registry.issueToken(peer) ?: return untrusted(true)   // revoked while we waited
        registry.get(peer)?.let(onConnected)
        return HelloReply.Ok(HelloInfo(tvName(), version, mdnsName(), t.token, registry.tokenTtlMs / 1000, link(), registry.installId, TrustRegistry.MAX_PHONES))
    }
}
