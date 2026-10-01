package castbridge.core.policy

import castbridge.core.lots.MemoryQueueStore
import castbridge.core.owner.*
import java.io.IOException

const val DAY = 24L * 3600 * 1000
const val NOW0 = 1_800_000_000_000L

/** Keys, a device and a TV engine for the order tests. Seeds are test-only. */
class PolicyKit(val wall: LongArrayHolder = LongArrayHolder(NOW0)) {
    class LongArrayHolder(var v: Long)

    private fun seed(n: String) = java.security.MessageDigest.getInstance("SHA-256").digest("castbridge-orders-test|$n".toByteArray())
    val server = Ed25519Signer(seed("server"))
    val desk = Ed25519Signer(seed("desk"))
    val rogue = Ed25519Signer(seed("rogue"))
    val weak = Ed25519Signer(seed("weak-policy-only"))
    val serverScopes = setOf(KeyScope.POLICY, KeyScope.ISSUE_TRIAL, KeyScope.ISSUE_PRODUCTION, KeyScope.REACTIVATE, KeyScope.REVOKE, KeyScope.REGISTRY)
    val ring = KeyRing(listOf(server.trusted(serverScopes), desk.trusted(KeyScope.ALL), weak.trusted(setOf(KeyScope.POLICY))))

    val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASH-A", flashCid = "cid-a", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS-A"))
    val otherFp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASH-B", flashCid = "cid-b", ethernetMac = "AA:BB:CC:00:11:99", systemSerial = "SYS-B"))
    val ctx = DeviceContext(fp, setOf("lic-1"), setOf("beta"))
    val myTarget = Envelope.Target.Device(DeviceIdentity.kFor(fp.n), fp.byKind)
    val otherTarget = Envelope.Target.Device(DeviceIdentity.kFor(otherFp.n), otherFp.byKind)

    val store = MemoryQueueStore()
    fun engine(store: MemoryQueueStore = this.store) = PolicyEngine(ring, PolicyStorage(store), { ctx }, { wall.v })
    var engine = engine()

    var nonceN = 0
    fun order(action: String, params: Map<String, String> = emptyMap(), seq: Long, signer: Signer = server, target: Envelope.Target = myTarget,
              notBefore: Long = NOW0 - DAY, expiresAt: Long = NOW0 + 30 * DAY, issuedAt: Long = NOW0, nonce: String = "%016x".format(++nonceN + seq * 1000)): String =
        Orders.issue(signer, seq, nonce, issuedAt, notBefore, expiresAt, target, action, params)
}

/** An in-memory owner channel: the phone writes frames, the TV receiver answers; can be cut after N written frames, and can drop the TV's answers. */
class LoopLink(private val tv: TvOrderReceiver, private val cutAfterWrites: Int = Int.MAX_VALUE, private val dropAnswersFrom: Int = Int.MAX_VALUE) : OrderLink {
    private val inbox = ArrayDeque<OwnerFrames.Frame>()
    var writes = 0; private set
    val log = ArrayList<Int>()
    /** Other handlers of the channel: they get the frames the order receiver does not own (an old phone/TV ignores them). */
    var others = 0
    override fun write(frame: ByteArray) {
        if (writes >= cutAfterWrites) throw IOException("liaison coupée")
        writes++
        val f = OwnerFrames.read(frame.inputStream())!!
        log += f.type
        val answers = tv.onFrame(f.type, f.payload)
        if (answers == null) { others++; return }
        if (writes < dropAnswersFrom) answers.forEach { inbox += OwnerFrames.read(it.inputStream())!! }
    }
    override fun read(): OwnerFrames.Frame? = inbox.removeFirstOrNull()
}
