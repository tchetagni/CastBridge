package castbridge.core.remote.smart

import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteKey.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Android TV / Google TV « Remote v2 »: two TLS ports (6467 pairing, 6466 remote), both with a client certificate, protobuf messages
 * each prefixed by their varint length. Pairing: the TV shows a 6-character hexadecimal code; the secret sent back is
 * SHA-256(client modulus ‖ client exponent ‖ server modulus ‖ server exponent ‖ last two bytes of the code), whose first byte must
 * equal the first byte of the code. Message layouts are from the community's reverse-engineering notes (not verified on hardware).
 */
object AndroidTvMessages {
    const val PAIRING_PORT = 6467
    const val REMOTE_PORT = 6466

    fun frame(msg: ByteArray): ByteArray = Pb.Writer().varint(msg.size.toLong()).toByteArray() + msg

    /** Reads one length-prefixed message. */
    fun readFrame(i: InputStream): ByteArray {
        var len = 0; var shift = 0
        while (true) { val b = i.read(); if (b < 0) throw IOException("connexion fermée"); len = len or ((b and 0x7F) shl shift); if (b and 0x80 == 0) break; shift += 7; if (shift > 28) throw IOException("longueur invalide") }
        if (len < 0 || len > 1 shl 16) throw IOException("message trop grand")
        val out = ByteArray(len); var o = 0
        while (o < len) { val r = i.read(out, o, len - o); if (r < 0) throw IOException("connexion fermée"); o += r }
        return out
    }

    private fun envelope(inner: Pb.Writer.() -> Unit, field: Int? = null, status: Int = 200): ByteArray {
        val w = Pb.Writer().int(1, 2).int(2, status)         // protocol_version = 2, status = OK
        if (field != null) w.message(field, Pb.Writer().apply(inner))
        return w.toByteArray()
    }

    fun pairingRequest(client: String = "CastBridge") = envelope({ string(1, "androidtvremote2"); string(2, client) }, 10)
    /** Hexadecimal input, 6 symbols, we are the input device. */
    fun pairingOptions() = envelope({ message(1, Pb.Writer().int(1, 3).int(2, 6)); int(3, 1) }, 20)
    fun pairingConfiguration() = envelope({ message(1, Pb.Writer().int(1, 3).int(2, 6)); int(2, 1) }, 30)
    fun pairingSecret(secret: ByteArray) = envelope({ bytes(1, secret) }, 40)

    /** [code] = the 6 hexadecimal characters shown on the TV; null when it is malformed or its check byte does not match. */
    fun secret(clientModulus: ByteArray, clientExponent: ByteArray, serverModulus: ByteArray, serverExponent: ByteArray, code: String): ByteArray? {
        val c = code.trim().uppercase()
        if (c.length != 6 || !c.all { it in "0123456789ABCDEF" }) return null
        val nonce = ByteArray(2) { c.substring(2 + it * 2, 4 + it * 2).toInt(16).toByte() }
        val h = MessageDigest.getInstance("SHA-256").apply { update(clientModulus); update(clientExponent); update(serverModulus); update(serverExponent); update(nonce) }.digest()
        return if (h[0] == c.substring(0, 2).toInt(16).toByte()) h else null
    }

    /** direction: 1 = start of a long press, 2 = end, 3 = short press. */
    fun keyInject(androidKeyCode: Int, direction: Int = 3): ByteArray = Pb.Writer().message(10, Pb.Writer().int(1, androidKeyCode).int(2, direction)).toByteArray()
    fun pingResponse(value: Int): ByteArray = Pb.Writer().message(9, Pb.Writer().int(1, value)).toByteArray()

    /** Field 8 of the remote message = ping request; answered by field 9 with the same value. */
    fun pingValue(msg: ByteArray): Int? = Pb.parse(msg).firstOrNull { it.number == 8 }?.bytes?.let { b -> Pb.parse(b).firstOrNull { it.number == 1 }?.long?.toInt() }
}

/** An open framed TLS channel to the TV, supplied by the platform (needs a client certificate, which plain JVM cannot create). */
interface AndroidTvChannel : java.io.Closeable {
    val input: InputStream
    val output: OutputStream
    val serverModulus: ByteArray
    val serverExponent: ByteArray
    val clientModulus: ByteArray
    val clientExponent: ByteArray
}

fun interface AndroidTvChannelFactory { fun open(host: String, port: Int): AndroidTvChannel }

/** EXPERIMENTAL: the message flow is implemented and tested on fake channels; the TLS identity comes from the platform factory. */
class AndroidTvStrategy(tv: TvTarget, private val factory: AndroidTvChannelFactory?, log: (String) -> Unit = {}) : BaseStrategy(tv, log), Pairable {
    override val id = StrategyIds.ANDROID_TV
    override val label = "Android TV / Google TV (Remote v2)"
    override val status = StrategyStatus.EXPERIMENTAL
    override val capabilities = Capabilities(KEYS)
    override val limits = "Appairage par code à 6 caractères affiché sur la TV. Expérimental : jamais essayé sur une vraie TV."

    private var pairing: AndroidTvChannel? = null
    private var remote: AndroidTvChannel? = null

    override fun applicable(fp: TvFingerprint) = (fp.vendor == Vendor.ANDROID_TV || StrategyIds.ANDROID_TV in fp.candidates || fp.family == "Android TV") && factory != null
    override fun probe() = ProbeResult(tcpOpen(AndroidTvMessages.REMOTE_PORT) || tcpOpen(AndroidTvMessages.PAIRING_PORT), "ports 6466/6467")

    @Synchronized override fun connect() {
        val f = factory ?: failed("identité TLS indisponible dans cette version")
        state = StrategyState(StrategyState.Kind.CONNECTING)
        try { remote = f.open(tv.host, AndroidTvMessages.REMOTE_PORT).also { handshake(it) } } catch (e: IOException) {
            // Not paired yet: the remote port closes the link; start pairing on the other port.
            runCatching { remote?.close() }; remote = null
            val p = try { f.open(tv.host, AndroidTvMessages.PAIRING_PORT) } catch (e2: IOException) { failed("TV injoignable : ${e2.message}") }
            for (m in listOf(AndroidTvMessages.pairingRequest(), AndroidTvMessages.pairingOptions(), AndroidTvMessages.pairingConfiguration())) {
                p.output.write(AndroidTvMessages.frame(m)); p.output.flush(); AndroidTvMessages.readFrame(p.input)
            }
            pairing = p
            needsPairing("Entrez le code à 6 caractères affiché sur la TV.")
        }
        ready()
    }

    /** Answers the TV's configure / set-active messages until it is ready (a closed link = not paired). */
    private fun handshake(c: AndroidTvChannel) {
        repeat(3) {
            val m = AndroidTvMessages.readFrame(c.input)
            val f = Pb.parse(m).firstOrNull()?.number
            when (f) {
                1 -> c.output.write(AndroidTvMessages.frame(Pb.Writer().message(1, Pb.Writer().int(1, 622).message(2, Pb.Writer().string(1, "CastBridge").string(2, "CastBridge").int(3, 1).string(4, "1").string(5, "castbridge").string(6, "1"))).toByteArray()))
                2 -> c.output.write(AndroidTvMessages.frame(Pb.Writer().message(2, Pb.Writer().int(1, 622)).toByteArray()))
                8 -> AndroidTvMessages.pingValue(m)?.let { c.output.write(AndroidTvMessages.frame(AndroidTvMessages.pingResponse(it))) }
            }
            c.output.flush()
        }
    }

    override fun pair(code: String) {
        val p = pairing ?: throw IOException("appairage non démarré")
        val s = AndroidTvMessages.secret(p.clientModulus, p.clientExponent, p.serverModulus, p.serverExponent, code) ?: throw IOException("code invalide")
        p.output.write(AndroidTvMessages.frame(AndroidTvMessages.pairingSecret(s))); p.output.flush()
        AndroidTvMessages.readFrame(p.input)
        runCatching { p.close() }; pairing = null
    }

    override fun send(key: RemoteKey) {
        if (key !in KEYS) throw KeyUnsupported(key, label)
        val c = remote ?: throw IOException("non connectée")
        try { c.output.write(AndroidTvMessages.frame(AndroidTvMessages.keyInject(key.code))); c.output.flush() }
        catch (e: IOException) { state = StrategyState(StrategyState.Kind.FAILED, "liaison perdue"); throw e }
    }

    override fun close() { runCatching { pairing?.close() }; runCatching { remote?.close() }; pairing = null; remote = null; super.close() }

    companion object { val KEYS: Set<RemoteKey> = RemoteKey.values().toSet() }
}
