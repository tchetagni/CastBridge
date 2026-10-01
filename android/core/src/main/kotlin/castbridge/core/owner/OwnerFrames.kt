package castbridge.core.owner

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream

/**
 * Frames of the OWNER Bluetooth channel (docs/TRIAL-EDITION.md § Canal Bluetooth propriétaire): a service of its own ([SERVICE_UUID], the next one after the
 * API service 0003), so old TVs and phones never see it (additive, backward compatible). Frame = magic "CBTO" once at connection, then repeated
 * [type:1][length:2 big-endian][payload]. Nothing here is secret: every command inside is signed (see [OwnerCommand]).
 */
object OwnerFrames {
    const val SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000005"
    const val MAGIC = "CBTO"
    const val MAX_PAYLOAD = 4096

    const val CHALLENGE_REQUEST = 1      // console -> TV, empty
    const val CHALLENGE = 2              // TV -> console, 32 hex chars
    const val COMMAND = 3                // console -> TV, ASCII "cbo1.…" token
    const val RESULT = 4                 // TV -> console, [ok:1] + UTF-8 message
    const val DEVICE_INFO_REQUEST = 5    // console -> TV, empty
    const val DEVICE_INFO = 6            // TV -> console, device code + fingerprint lines (the "device request")
    const val PAIR = 7                   // console -> TV, the 6-digit pairing code shown on the panel
    const val ACTIVATION = 8             // console -> TV, ASCII "cbx1.…" activation (envelope, docs/ACTIVATION-FORMAT.md) (offline phase)

    class Frame(val type: Int, val payload: ByteArray) {
        val text: String get() = String(payload, Charsets.UTF_8)
    }

    fun encode(type: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(type in 1..255 && payload.size <= MAX_PAYLOAD)
        return ByteArray(3 + payload.size).also { it[0] = type.toByte(); it[1] = (payload.size shr 8).toByte(); it[2] = payload.size.toByte(); System.arraycopy(payload, 0, it, 3, payload.size) }
    }
    fun encode(type: Int, text: String) = encode(type, text.toByteArray(Charsets.UTF_8))
    fun hello(): ByteArray = MAGIC.toByteArray(Charsets.US_ASCII)

    /** Reads the magic; false if the peer does not speak this channel. */
    fun readHello(input: InputStream): Boolean = runCatching { ByteArray(4).also { DataInputStream(input).readFully(it) }.toString(Charsets.US_ASCII) == MAGIC }.getOrDefault(false)

    /** The next frame, or null at end of stream / on a frame that is too big or truncated (the caller closes the link). */
    fun read(input: InputStream): Frame? = runCatching {
        val d = DataInputStream(input)
        val type = d.readUnsignedByte(); val len = d.readUnsignedShort()
        if (type == 0 || len > MAX_PAYLOAD) return null
        Frame(type, ByteArray(len).also { d.readFully(it) })
    }.getOrNull()

    /** The text of the "device request" the TV gives for activation: its code and the full fingerprint set (the console needs them to build an activation and the lot keys). */
    fun deviceInfo(code: String, fp: Fingerprints): String =
        (listOf("code=$code", "k=${DeviceIdentity.kFor(fp.n)}") + fp.byKind.map { "factor=${it.key.name}|${it.value}" }).joinToString("\n")

    fun parseDeviceInfo(text: String): Triple<String, Int, Fingerprints>? = runCatching {
        val lines = text.split('\n')
        val code = DeviceCode.parse(lines[0].removePrefix("code=")) ?: return null
        val k = lines[1].removePrefix("k=").toInt()
        Triple(code, k, Fingerprints(lines.drop(2).associate { l -> l.removePrefix("factor=").split('|').let { FactorKind.valueOf(it[0]) to it[1] } }))
    }.getOrNull()
}
