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
    /** Phone -> TV: the challenge of a TV proof, 64 lowercase hex chars (32 random bytes). The TV answers with [PROOF]; both are additive, but an old TV does NOT ignore the type: OwnerChannel answers RESULT(0) « Non pris en charge par cette TV ». w6-12 (phone side) must map that answer to TV_OLD_VERSION. */
    const val PROOF_REQUEST = 9
    /** TV -> phone: ASCII "cbx1.…" envelope of type `proof` signed by the installation key over the challenge (see [TvProof]; docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md § 3.3). */
    const val PROOF = 10

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
    fun deviceInfo(code: String, fp: Fingerprints, installPub: ByteArray? = null, installSig: ByteArray? = null): String =
        (listOf("code=$code", "k=${DeviceIdentity.kFor(fp.n)}") + fp.byKind.map { "factor=${it.key.name}|${it.value}" } +
            listOfNotNull(installPub?.also { require(it.size == 32) { "clé d'installation de 32 octets attendue" } }?.let { "install=x25519|" + it.joinToString("") { b -> "%02x".format(b) } }) +
            listOfNotNull(installSig?.also { require(it.size == 32) { "clé de signature de 32 octets attendue" } }?.let { "install_sig=ed25519|" + it.joinToString("") { b -> "%02x".format(b) } })).joinToString("\n")

    /**
     * The device request: [code], [k], the factors [fp], the installation's public key [installPub] (null for a request from an old TV) and the `key=value` lines this version does not
     * know ([unknown], kept verbatim, ignored: a newer TV may add lines). The first three components keep the shape of the old `Triple` for destructuring.
     */
    data class DeviceInfo(val code: String, val k: Int, val fp: Fingerprints, val installPub: ByteArray? = null, val unknown: List<String> = emptyList(), val installSig: ByteArray? = null) {
        /** Old `Triple` accessors, so callers written before the installation key still compile; new code uses the names. */
        val first: String get() = code
        val second: Int get() = k
        val third: Fingerprints get() = fp
    }

    /**
     * Reads a device request. Null if the code or k is missing or unreadable, a `factor=` or `install=x25519|` line is malformed, or a line has no `=`. Blank lines and CRLF are
     * tolerated, unknown `key=value` lines are ignored (listed in [DeviceInfo.unknown]), an `install=` with another algorithm is unknown too.
     */
    fun parseDeviceInfo(text: String): DeviceInfo? = runCatching {
        val lines = text.replace("\r", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        val code = DeviceCode.parse(lines[0].removePrefix("code=").takeIf { lines[0].startsWith("code=") } ?: return null) ?: return null
        if (!lines[1].startsWith("k=")) return null
        val k = lines[1].removePrefix("k=").toInt()
        val factors = LinkedHashMap<FactorKind, String>(); var installPub: ByteArray? = null; var installSig: ByteArray? = null; val unknown = ArrayList<String>()
        for (l in lines.drop(2)) {
            val eq = l.indexOf('='); if (eq <= 0) return null
            val key = l.substring(0, eq); val value = l.substring(eq + 1)
            when {
                key == "factor" -> value.split('|').let { if (it.size != 2 || it[1].isEmpty()) return null; factors[FactorKind.valueOf(it[0])] = it[1] }
                key == "install" && value.startsWith("x25519|") -> {
                    val h = value.removePrefix("x25519|")
                    if (installPub != null || h.length != 64 || !h.all { it in '0'..'9' || it in 'a'..'f' }) return null
                    installPub = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }
                key == "install_sig" && value.startsWith("ed25519|") -> {
                    // the Ed25519 key the TV signs its `bind` proofs with (W23-05 audit HIGH-1): the issuer SIGNS it into the activation (right `ik`). Additive: an older parser lists the line as unknown.
                    val h = value.removePrefix("ed25519|")
                    if (installSig != null || h.length != 64 || !h.all { it in '0'..'9' || it in 'a'..'f' }) return null
                    installSig = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }
                else -> unknown += l
            }
        }
        DeviceInfo(code, k, Fingerprints(factors), installPub, unknown, installSig)
    }.getOrNull()

    /** The old shape (code, k, fingerprints): tolerates an `install=` line, refuses any other unknown line as before. */
    @Deprecated("utiliser parseDeviceInfo (DeviceInfo)")
    fun parseDeviceInfoLegacy(text: String): Triple<String, Int, Fingerprints>? = parseDeviceInfo(text)?.takeIf { it.unknown.isEmpty() }?.let { Triple(it.code, it.k, it.fp) }
}
