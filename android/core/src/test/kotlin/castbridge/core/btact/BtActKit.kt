package castbridge.core.btact

import castbridge.core.owner.DeviceCode
import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.OwnerFrames
import castbridge.core.tv.PinGuard
import castbridge.core.tv.activation.ActivationAttemptGate
import castbridge.core.tv.activation.LockedActivationApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One direction of a link in memory: what is written is recorded, and read in order; closing the writing end gives the reader an end of stream once everything is read. */
internal class Pipe {
    private val queue = LinkedBlockingQueue<Int>()
    val recorded = ByteArrayOutputStream()
    @Volatile private var closed = false

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) {
            if (closed) throw IOException("lien fermé")
            val v = b and 0xFF
            synchronized(recorded) { recorded.write(v) }
            queue.put(v)
        }
        override fun close() = closeWrite()
    }

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            val v = queue.poll(20, TimeUnit.SECONDS) ?: throw IOException("lien muet : aucun octet en 20 s (le test ne doit jamais attendre sans fin)")
            if (v == -1) { queue.put(-1); return -1 }
            return v
        }
    }

    fun closeWrite() { if (!closed) { closed = true; queue.put(-1) } }
    fun bytes(): ByteArray = synchronized(recorded) { recorded.toByteArray() }
}

/** Random bytes that are the same every run: a session can be replayed bit for bit. NOT random: tests only. */
internal class SeqEntropy(seed: Int) : BtActWire.Entropy {
    private var counter = seed
    override fun bytes(n: Int): ByteArray = ByteArray(n) { (counter++ * 37 + 11).toByte() }
}

/** A TV that serves the activation service, with everything it is given observable. */
internal class FakeTv(
    var code: String? = "482913",
    var terms: Boolean = true,
    val clock: () -> Long = { 1_000_000L },
    val guard: PinGuard = PinGuard("482913", now = clock),
    val gate: ActivationAttemptGate = ActivationAttemptGate(guard, clock),
    serverSeed: Int = 1000,
    var requestText: String? = fullRequest,
) {
    val installed = ArrayList<String>()
    val authorized = ArrayList<String>()
    var reads = 0
    val server = BtActServer(
        code = { code }, gate = gate, termsAccepted = { terms },
        deviceRequest = { reads++; requestText },
        install = { key -> installed += key; if (key == GOOD_KEY) LockedActivationApi.Install.Accepted("Licence 1") else LockedActivationApi.Install.Rejected("Cette clé n'est pas celle de cette TV") },
        tvName = { "CastBridge TV salon" }, tvVersion = "0.14.47-test", onAuthorized = { authorized += it }, entropy = SeqEntropy(serverSeed),
    )

    companion object {
        val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.WIFI to "fedcba9876543210fedcba9876543210"))
        val fullRequest: String = OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, ByteArray(32) { (it + 3).toByte() }, ByteArray(32) { (it + 7).toByte() })
        const val GOOD_KEY = "GOOD-KEY-CANARY-0123456789"
        const val PEER = "AA:BB:CC:DD:EE:01"
    }
}

/** What a live session left: both directions as the link carried them, and what each side concluded. */
internal class LiveRun(val connect: BtActClient.Connect, val end: BtActServer.End, val c2s: ByteArray, val s2c: ByteArray, val read: BtActClient.Session.Request?, val installed: BtActClient.Session.Installed?)

/** Runs the real client against the real server over two in-memory pipes (the server on its own thread). [key] null = only read the request. */
internal fun live(tv: FakeTv, code: String, peer: String = FakeTv.PEER, clientSeed: Int = 5000, read: Boolean = true, key: String? = FakeTv.GOOD_KEY): LiveRun {
    val c2s = Pipe(); val s2c = Pipe()
    var end: BtActServer.End? = null
    val t = Thread { end = tv.server.serve(c2s.input, s2c.output, peer); s2c.closeWrite() }.apply { isDaemon = true; start() }
    val connect = BtActClient(SeqEntropy(clientSeed)).connect(s2c.input, c2s.output, code)
    var request: BtActClient.Session.Request? = null; var installed: BtActClient.Session.Installed? = null
    if (connect is BtActClient.Connect.Ready) {
        if (read) request = connect.session.readRequest()
        if (key != null) installed = connect.session.install(key)
        connect.session.finish()
    }
    c2s.closeWrite()
    t.join(15_000)
    check(!t.isAlive) { "le serveur ne s'est pas arrêté" }
    return LiveRun(connect, end!!, c2s.bytes(), s2c.bytes(), request, installed)
}

/** The server on bytes the client wrote earlier (no thread): used to replay, tamper with and cut a recorded session. */
internal fun serveBytes(tv: FakeTv, bytes: ByteArray, peer: String = FakeTv.PEER): Pair<BtActServer.End, ByteArray> {
    val out = ByteArrayOutputStream()
    val end = tv.server.serve(ByteArrayInputStream(bytes), out, peer)
    return end to out.toByteArray()
}

/** The client on bytes the server wrote earlier (no thread); the same client seed gives the same session as the recorded one. */
internal fun clientOnBytes(bytes: ByteArray, code: String, clientSeed: Int = 5000, read: Boolean = true, key: String? = FakeTv.GOOD_KEY): Triple<BtActClient.Connect, BtActClient.Session.Request?, BtActClient.Session.Installed?> {
    val connect = BtActClient(SeqEntropy(clientSeed)).connect(ByteArrayInputStream(bytes), ByteArrayOutputStream(), code)
    var request: BtActClient.Session.Request? = null; var installed: BtActClient.Session.Installed? = null
    if (connect is BtActClient.Connect.Ready) {
        if (read) request = connect.session.readRequest()
        if (key != null) installed = connect.session.install(key)
    }
    return Triple(connect, request, installed)
}
