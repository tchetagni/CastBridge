package castbridge.core.tv

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/** Per-file locks shared by every transport (HTTP, Bluetooth, USB import) writing into the same folder. */
object FileLocks {
    private val locks = ConcurrentHashMap<String, Any>()
    fun of(dir: File, name: String): Any = locks.getOrPut(File(dir, name).absolutePath) { Any() }
}

/**
 * Transport-independent file transfer used over Bluetooth RFCOMM (any byte stream works, so it is
 * unit-tested with in-memory pipes).
 *
 * Client -> TV : "CBT1" | PIN (6 ASCII) | u16 nameLen | name (UTF-8) | i64 total
 * TV -> client : status byte (0 = READY, else an ERR_* code) and, if READY, i64 offset
 *                (size of the existing .part: resume point)
 * Client -> TV : bytes [offset, total)
 * TV -> client : status byte (0 = OK, else ERR_*)
 *
 * The TV writes into "<name>.part" as bytes arrive and renames it to "<name>" once complete,
 * exactly like the HTTP upload, so both transports can resume each other's partial files.
 */
object BtProtocol {
    const val MAGIC = "CBT1"
    /**
     * "Which faster link can we use?": the phone asks over Bluetooth (PIN checked like CBT1), the TV answers its addresses and,
     * if asked and possible, its Wi-Fi Direct group; the data then goes over Wi-Fi (HTTP, resumable) and Bluetooth is only the
     * fallback. An older TV answers ERR_MAGIC and the phone simply sends with CBT1.
     *
     * Client -> TV : "CBTN" | PIN (6 ASCII) | u8 flags (bit 0 = start Wi-Fi Direct if it is off)
     * TV -> client : status byte; if OK: u16 length | UTF-8 lines "key=value" ([LinkInfo])
     */
    const val NEGOTIATE = "CBTN"
    const val WANT_WIFI_DIRECT = 1
    /**
     * "Who is this phone to you?": the control message of the plug-and-play link. Only over Android's paired (authenticated and
     * encrypted) RFCOMM link, the peer being identified by the Bluetooth address of the socket, never by anything it writes.
     *
     * Client -> TV : "CBTH" | u8 flags (bit 0 = "I am new: ask the owner to trust me", honoured only while the TV shows « Ajouter un téléphone »)
     * TV -> client : status byte (OK or ERR_UNTRUSTED/ERR_DENIED/ERR_TIMEOUT/ERR_NOT_OPEN/ERR_BUSY); if OK: u16 length | UTF-8 lines [HelloInfo]
     *
     * A peer that is not trusted never receives an address, a name, a version or a token. A trusted phone receives the TV's name,
     * its current Wi-Fi addresses and a fresh per-phone token (never the PIN). The TV ignores the PIN field of the other messages
     * for a trusted peer (the phone then sends [castbridge.core.trust.TvAuth.NO_PIN]).
     */
    const val HELLO = "CBTH"
    const val HELLO_REQUEST_TRUST = 1
    /**
     * "Do you have reports for me?": the parent's phone pulls the reports of the parental control that wait in the TV's outbox
     * (docs/PARENTAL.md). Only over the paired RFCOMM link; the peer is the socket's device. An older TV answers ERR_MAGIC and an
     * older phone never sends it. See [castbridge.core.parental.ParentalSyncProtocol] for the frames.
     */
    const val PARENTAL = castbridge.core.parental.ParentalSyncProtocol.MAGIC
    /**
     * Additive (older phones never set it, older TVs ignore it): "after the flags comes `u8 length | install id` of the TV I remember". A TV that
     * answers an error then adds ONE hint byte ([HINT_NONE]/[HINT_OTHER_INSTALL]/[HINT_SAME_INSTALL]) so the phone can tell "the TV was reset or
     * reinstalled" from "this phone was removed from the TV"; only for a peer Android says is paired, and it only compares two random ids.
     */
    const val HELLO_HAS_INSTALL_ID = 2
    const val HINT_NONE = 0
    const val HINT_OTHER_INSTALL = 1
    const val HINT_SAME_INSTALL = 2
    /** RFCOMM service UUID shared by the TV and the phone app. */
    const val SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000001"
    /** Second RFCOMM service: a plain byte tunnel to the TV's SSH server (see castbridge.core.ssh.SshTunnel). */
    const val SSH_SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000002"
    /** Third RFCOMM service: a byte tunnel to the TV's own HTTP API (127.0.0.1:8765), see castbridge.core.tunnel.TcpTunnel and docs/ADMIN.md. */
    const val API_SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000003"
    /** Fourth RFCOMM service: the same HTTP API over ONE shared link per phone (frames, see castbridge.core.tunnel.MuxSession). The third service stays for old phones/TVs. */
    const val API_MUX_SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000004"
    const val OK = 0
    const val ERR_MAGIC = 1
    const val ERR_PIN = 2
    const val ERR_NAME = 3
    const val ERR_SPACE = 4
    const val ERR_LOCKED = 5
    const val ERR_IO = 6
    const val ERR_SIZE = 7
    /** HELLO: this phone is not trusted (and did not ask, or the link is not an authenticated pairing). */
    const val ERR_UNTRUSTED = 8
    /** HELLO: the owner pressed "Refuser" on the TV. */
    const val ERR_DENIED = 9
    /** HELLO: nobody answered on the TV in time. */
    const val ERR_TIMEOUT = 10
    /** HELLO: the TV is not in "Ajouter un téléphone" mode. */
    const val ERR_NOT_OPEN = 11
    /** HELLO: another phone is waiting for the owner's answer, or too many refusals. */
    const val ERR_BUSY = 12
    private const val MAX_NAME = 400
    private const val MAX_INSTALL_ID = 64

    /** Errors that retrying cannot fix. */
    fun isFatal(code: Int) = code in setOf(ERR_MAGIC, ERR_PIN, ERR_NAME, ERR_SPACE, ERR_LOCKED, ERR_SIZE, ERR_UNTRUSTED, ERR_DENIED, ERR_NOT_OPEN)

    fun describe(code: Int) = when (code) {
        OK -> "ok"
        ERR_MAGIC -> "protocole inconnu"
        ERR_PIN -> "PIN incorrect"
        ERR_NAME -> "nom de fichier refusé"
        ERR_SPACE -> "espace insuffisant sur la TV"
        ERR_LOCKED -> "trop d'essais, TV verrouillée"
        ERR_SIZE -> "taille invalide"
        ERR_UNTRUSTED -> "téléphone non autorisé par la TV"
        ERR_DENIED -> "refusé sur la TV"
        ERR_TIMEOUT -> "pas de réponse sur la TV"
        ERR_NOT_OPEN -> "la TV n'attend pas de nouveau téléphone"
        ERR_BUSY -> "la TV traite déjà une demande"
        else -> "erreur TV ($code)"
    }

    class Refused(val code: Int, val hint: Int = HINT_NONE) : IOException(describe(code))

    // ---------------------------------------------------------------- receiver (TV)

    /**
     * Handles one connection. Returns the final status (OK or ERR_*); an [IOException] means the link
     * broke, in which case the bytes received so far stay in the .part file for a later resume.
     */
    fun serve(
        dir: File, input: InputStream, output: OutputStream,
        guard: PinGuard?, peer: String, minFreeBytes: Long = 100L shl 20,
        onProgress: (name: String, done: Long, total: Long) -> Unit = { _, _, _ -> },
        /** Answers a CBTN request (null = this TV does not offer a faster link: ERR_MAGIC, as an old TV would). */
        negotiate: ((wantWifiDirect: Boolean) -> LinkInfo)? = null,
        /** Phone remote over Bluetooth (CBTR, castbridge.core.remote.RemoteBt): runs until the phone closes the link. */
        remote: ((InputStream, OutputStream) -> Unit)? = null,
        /** Plug-and-play HELLO (CBTH); null = this TV does not offer it (ERR_MAGIC). Gets (peer address, phone asks to be trusted). */
        hello: ((peer: String, requestTrust: Boolean) -> HelloReply)? = null,
        /** Is this peer (address proven by the paired link) a trusted phone? Then the PIN field is not checked. */
        trusted: ((String) -> Boolean)? = null,
        /** Reports of the parental control for a designated phone (CBTP); null = this TV does not offer it (ERR_MAGIC). */
        parental: castbridge.core.parental.ReportSyncHost? = null,
    ): Int {
        dir.mkdirs()
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        fun fail(code: Int): Int { dout.writeByte(code); dout.flush(); return code }
        /** null = allowed. A trusted phone needs no PIN; everybody else is checked (and counted) like before. */
        fun pinProblem(pin: String): Int? {
            if (trusted?.invoke(peer) == true) return null
            if (guard == null) return null
            return when (guard.check(peer, pin)) { PinGuard.Result.OK -> null; PinGuard.Result.BAD -> ERR_PIN; PinGuard.Result.LOCKED -> ERR_LOCKED }
        }

        val magic = ByteArray(4).also { din.readFully(it) }
        val m = String(magic, Charsets.US_ASCII)
        if (m == HELLO && hello != null) {
            val flags = din.readUnsignedByte()
            val claimed = if (flags and HELLO_HAS_INSTALL_ID != 0) String(ByteArray(din.readUnsignedByte().coerceAtMost(MAX_INSTALL_ID)).also { din.readFully(it) }, Charsets.US_ASCII) else null
            return when (val r = hello(peer, flags and HELLO_REQUEST_TRUST != 0)) {
                is HelloReply.Err -> {
                    dout.writeByte(r.code)
                    if (claimed != null) dout.writeByte(r.hintFor?.invoke(claimed) ?: HINT_NONE)
                    dout.flush(); r.code
                }
                is HelloReply.Ok -> {
                    val text = r.info.encode().toByteArray(Charsets.UTF_8)
                    dout.writeByte(OK); dout.writeShort(text.size); dout.write(text); dout.flush(); OK
                }
            }
        }
        if (m == PARENTAL && parental != null) return castbridge.core.parental.ParentalSyncProtocol.serve(din, dout, peer, parental)
        if (m == NEGOTIATE && negotiate != null) {
            val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
            val flags = din.readUnsignedByte()
            pinProblem(pin)?.let { return fail(it) }
            val text = negotiate(flags and WANT_WIFI_DIRECT != 0).encode().toByteArray(Charsets.UTF_8)
            dout.writeByte(OK); dout.writeShort(text.size); dout.write(text); dout.flush()
            return OK
        }
        if (m == castbridge.core.remote.RemoteBt.MAGIC && remote != null) {
            val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
            pinProblem(pin)?.let { return fail(it) }
            dout.writeByte(OK); dout.flush()
            remote(din, dout)
            return OK
        }
        if (m != MAGIC) return fail(ERR_MAGIC)
        val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
        val nameLen = din.readUnsignedShort()
        if (nameLen == 0 || nameLen > MAX_NAME) return fail(ERR_NAME)
        val rawName = String(ByteArray(nameLen).also { din.readFully(it) }, Charsets.UTF_8)
        val total = din.readLong()

        pinProblem(pin)?.let { return fail(it) }
        val name = ReceiverServer.safeName(rawName) ?: return fail(ERR_NAME)
        if (total <= 0) return fail(ERR_SIZE)

        val final = File(dir, name)
        val pf = File(dir, "$name.part")
        synchronized(FileLocks.of(dir, name)) {
            if (final.isFile && final.length() == total) {
                dout.writeByte(OK); dout.writeLong(total); dout.writeByte(OK); dout.flush(); return OK
            }
            var cur = pf.length()
            if (cur > total) { pf.delete(); cur = 0 }
            if (dir.usableSpace - (total - cur) < minFreeBytes) return fail(ERR_SPACE)
            dout.writeByte(OK); dout.writeLong(cur); dout.flush()

            var done = cur
            onProgress(name, done, total)
            // RFCOMM hands over small pieces (about one baseband packet each): gather them in a 256 kB buffer so the disk sees
            // few large writes, and report progress every 256 kB only. Closing (also on a broken link) flushes what arrived.
            java.io.BufferedOutputStream(FileOutputStream(pf, true), 256 * 1024).use { out ->
                val buf = ByteArray(64 * 1024)
                var reported = done
                while (done < total) {
                    val r = input.read(buf, 0, minOf(buf.size.toLong(), total - done).toInt())
                    if (r < 0) throw IOException("link closed at $done/$total")
                    out.write(buf, 0, r)
                    done += r
                    if (done - reported >= PROGRESS_STEP || done == total) { reported = done; onProgress(name, done, total) }
                }
            }
            if (final.exists()) final.delete()
            if (!pf.renameTo(final)) return fail(ERR_IO)
            dout.writeByte(OK); dout.flush()
            return OK
        }
    }

    private const val PROGRESS_STEP = 256L * 1024

    // ---------------------------------------------------------------- sender (phone)

    /**
     * Asks the TV for a faster link over an open Bluetooth connection. Throws [Refused] (ERR_MAGIC from a TV that does not
     * know CBTN, ERR_PIN...) or [IOException].
     */
    fun negotiate(input: InputStream, output: OutputStream, pin: String, wantWifiDirect: Boolean): LinkInfo {
        require(Pin.isValidFormat(pin) || pin == castbridge.core.trust.TvAuth.NO_PIN) { "PIN must be ${Pin.LENGTH} digits" }
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        dout.write(NEGOTIATE.toByteArray(Charsets.US_ASCII))
        dout.write(pin.toByteArray(Charsets.US_ASCII))
        dout.writeByte(if (wantWifiDirect) WANT_WIFI_DIRECT else 0)
        dout.flush()
        val st = din.readUnsignedByte()
        if (st != OK) throw Refused(st)
        val len = din.readUnsignedShort()
        return LinkInfo.decode(String(ByteArray(len).also { din.readFully(it) }, Charsets.UTF_8))
    }

    /**
     * Plug-and-play HELLO over an open (paired) link. Throws [Refused] (ERR_UNTRUSTED, ERR_DENIED, ERR_TIMEOUT, ERR_NOT_OPEN,
     * ERR_BUSY, or ERR_MAGIC from a TV that predates it) or [IOException]. With [requestTrust] the TV asks its owner; the call then
     * waits for the answer (up to a minute).
     */
    fun hello(input: InputStream, output: OutputStream, requestTrust: Boolean, installId: String? = null): HelloInfo {
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        val id = installId?.takeIf { it.isNotEmpty() && it.length <= MAX_INSTALL_ID && it.all { c -> c.code in 33..126 } }
        dout.write(HELLO.toByteArray(Charsets.US_ASCII))
        dout.writeByte((if (requestTrust) HELLO_REQUEST_TRUST else 0) or (if (id != null) HELLO_HAS_INSTALL_ID else 0))
        if (id != null) { dout.writeByte(id.length); dout.write(id.toByteArray(Charsets.US_ASCII)) }
        dout.flush()
        val st = din.readUnsignedByte()
        if (st != OK) {
            // an older TV closes right after the status byte: no hint then
            val hint = if (id != null) try { din.read().takeIf { it in HINT_NONE..HINT_SAME_INSTALL } ?: HINT_NONE } catch (_: IOException) { HINT_NONE } else HINT_NONE
            throw Refused(st, hint)
        }
        val len = din.readUnsignedShort()
        return HelloInfo.decode(String(ByteArray(len).also { din.readFully(it) }, Charsets.UTF_8))
            ?: throw IOException("answer of the TV not understood")
    }

    /**
     * One attempt on an open link. Returns normally when the TV confirmed the whole file; throws
     * [Refused] for an ERR_* answer and [IOException] for a broken link.
     */
    fun send(
        input: InputStream, output: OutputStream, name: String, total: Long, pin: String,
        openAt: (Long) -> InputStream, onOffset: (Long) -> Unit = {}, onBytes: (Long) -> Unit = {},
    ) {
        require(Pin.isValidFormat(pin) || pin == castbridge.core.trust.TvAuth.NO_PIN) { "PIN must be ${Pin.LENGTH} digits" }
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        dout.write(MAGIC.toByteArray(Charsets.US_ASCII))
        dout.write(pin.toByteArray(Charsets.US_ASCII))
        dout.writeShort(nameBytes.size)
        dout.write(nameBytes)
        dout.writeLong(total)
        dout.flush()

        val st = din.readUnsignedByte()
        if (st != OK) throw Refused(st)
        val offset = din.readLong()
        onOffset(offset)
        if (offset < total) openAt(offset).use { src ->
            val buf = ByteArray(64 * 1024)
            var left = total - offset
            while (left > 0) {
                val r = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (r < 0) throw IOException("source ended early")
                dout.write(buf, 0, r)
                left -= r
                onBytes(r.toLong())
            }
            dout.flush()
        }
        val end = din.readUnsignedByte()
        if (end != OK) throw Refused(end)
    }
}

/** An open bidirectional link (a Bluetooth socket on Android, in-memory pipes in tests). */
interface Link : AutoCloseable {
    val input: InputStream
    val output: OutputStream
}

/**
 * Resumable Bluetooth upload: on a broken link it waits, reconnects, and continues from the size of
 * the .part file the TV reports in its READY answer.
 */
class ResumableBtUpload(
    private val name: String,
    private val total: Long,
    private val pin: String,
    private val connect: () -> Link,                 // throws IOException if the TV is unreachable
    private val openAt: (Long) -> InputStream,
    private val cancelled: () -> Boolean = { false },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val maxFailures: Int = 30,               // consecutive failures without progress
) {
    fun run(onState: (ResumableUpload.State) -> Unit): ResumableUpload.State {
        var sent = 0L
        var backoff = 500L
        var failures = 0
        while (!cancelled()) {
            try {
                connect().use { link ->
                    BtProtocol.send(link.input, link.output, name, total, pin, openAt,
                        onOffset = { sent = it; onState(ResumableUpload.State.Uploading(sent, total)) },
                        onBytes = { n ->
                            sent += n; backoff = 500; failures = 0
                            onState(ResumableUpload.State.Uploading(sent, total))
                            if (cancelled()) throw java.io.InterruptedIOException("cancelled")
                        })
                }
                return ResumableUpload.State.Done.also(onState)
            } catch (e: BtProtocol.Refused) {
                if (BtProtocol.isFatal(e.code)) return ResumableUpload.State.Failed(e.message ?: "refusé").also(onState)
                failures++
                onState(ResumableUpload.State.Waiting(sent, total, castbridge.core.trust.LinkText.failure(e)))
            } catch (e: IOException) {
                if (cancelled()) break
                failures++
                onState(ResumableUpload.State.Waiting(sent, total, castbridge.core.trust.LinkText.failure(e)))
            }
            if (failures >= maxFailures) return ResumableUpload.State.Failed("Bluetooth injoignable").also(onState)
            sleep(backoff); backoff = minOf(backoff * 2, 5000)
        }
        return ResumableUpload.State.Failed("annulé").also(onState)
    }
}

/** What the TV tells the phone over Bluetooth about faster links: its HTTP port and addresses, and its Wi-Fi Direct group if on. */
data class LinkInfo(val port: Int, val ips: List<String>, val wdSsid: String? = null, val wdPass: String? = null, val wdIp: String? = null) {
    fun encode(): String = buildString {
        append("port=").append(port).append('\n')
        ips.forEach { append("ip=").append(it).append('\n') }
        if (wdSsid != null && wdPass != null) {
            append("wd.ssid=").append(wdSsid.replace('\n', ' ')).append('\n')
            append("wd.pass=").append(wdPass.replace('\n', ' ')).append('\n')
            append("wd.ip=").append(wdIp ?: WifiDirect.GROUP_OWNER_IP).append('\n')
        }
    }

    companion object {
        private val IPV4 = Regex("^(\\d{1,3})(\\.\\d{1,3}){3}$")
        fun decode(s: String): LinkInfo {
            val kv = s.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toList()
            fun one(k: String) = kv.firstOrNull { it.first == k }?.second
            return LinkInfo(one("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: ReceiverServer.PORT,
                kv.filter { it.first == "ip" && IPV4.matches(it.second) }.map { it.second },   // literal IPv4 only: never a host name to resolve
                one("wd.ssid"), one("wd.pass"), one("wd.ip")?.takeIf { IPV4.matches(it) })
        }
    }
}

/**
 * Which way to send a file, fastest first, once the TV answered over Bluetooth: its address on a network the phone shares
 * (HTTP, several MB/s), else its Wi-Fi Direct group (HTTP, several MB/s, the phone joins it), else Bluetooth itself
 * (RFCOMM, ~100-250 kB/s). Pure: [reachable] tests an address (GET /api/hello with a short timeout).
 */
object LinkPlanner {
    sealed class Route {
        abstract val label: String
        data class Lan(val base: String) : Route() { override val label get() = "Wi-Fi (réseau commun)" }
        data class Direct(val ssid: String, val pass: String, val base: String) : Route() { override val label get() = "Wi-Fi Direct" }
        object Bluetooth : Route() { override val label get() = "Bluetooth" }
        /** The TV's HTTP API through the phone's Bluetooth tunnel: the same requests as on Wi-Fi, slower. */
        data class BluetoothTunnel(val base: String) : Route() { override val label get() = "Bluetooth (API)" }
    }

    /**
     * May the TV create its Wi-Fi Direct group because a phone asked over Bluetooth? A group can disturb the TV's own Wi-Fi, so
     * only if the owner switched Wi-Fi Direct on, or if the TV has no network at all (nothing to disturb).
     */
    fun mayStartWifiDirect(requested: Boolean, enabledByOwner: Boolean, tvHasNetwork: Boolean) = requested && (enabledByOwner || !tvHasNetwork)

    /**
     * [tunnelBase]: the phone's local end of the API tunnel (http://127.0.0.1:18765) when its Bluetooth gateway runs. It is a route
     * of last resort for everything that speaks HTTP (library, remote, parental, install...); simple file sends keep CBT1.
     */
    fun plan(info: LinkInfo?, reachable: (String) -> Boolean, canJoinWifiDirect: Boolean, tunnelBase: String? = null): List<Route> = buildList {
        if (info != null) {
            info.ips.map { "http://$it:${info.port}" }.firstOrNull(reachable)?.let { add(Route.Lan(it)) }
            if (canJoinWifiDirect && info.wdSsid != null && info.wdPass != null)
                add(Route.Direct(info.wdSsid, info.wdPass, "http://${info.wdIp ?: WifiDirect.GROUP_OWNER_IP}:${info.port}"))
        }
        if (tunnelBase != null) add(Route.BluetoothTunnel(tunnelBase))
        add(Route.Bluetooth)
    }
}

/** What a TV's answer to a trusted phone's HELLO holds. [token] is that phone's credential for the Wi-Fi API ([ttlSec] seconds). */
data class HelloInfo(val tvName: String, val version: String, val mdns: String?, val token: String, val ttlSec: Long, val link: LinkInfo,
    /** Random id of this installation of CastBridge-TV (changes when the TV forgets its phones); null = a TV that predates it. */
    val installId: String? = null) {
    fun encode(): String = buildString {
        append("tv=").append(line(tvName)).append('\n')
        append("v=").append(line(version)).append('\n')
        if (mdns != null) append("mdns=").append(line(mdns)).append('\n')
        append("ttl=").append(ttlSec).append('\n')
        append("token=").append(token).append('\n')
        if (installId != null) append("id=").append(installId).append('\n')
        append(link.encode())
    }

    companion object {
        private fun line(s: String) = s.replace(Regex("[\\r\\n\\t]"), " ").take(120)
        private val TOKEN = Regex("^cbk_[0-9a-f]{64}$")
        private val INSTALL_ID = Regex("^[0-9a-f]{8,64}$")

        /** null when the token is missing or malformed (nothing is usable without it). */
        fun decode(s: String): HelloInfo? {
            val kv = s.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            val token = kv["token"]?.takeIf { TOKEN.matches(it) } ?: return null
            val name = if (kv["tv"].isNullOrBlank()) "TV" else castbridge.core.trust.PhoneName.sanitize(kv["tv"], 60)
            return HelloInfo(name, kv["v"].orEmpty().take(40), kv["mdns"]?.take(120), token,
                kv["ttl"]?.toLongOrNull()?.coerceIn(60, 7 * 24 * 3600L) ?: 3600, LinkInfo.decode(s),
                kv["id"]?.takeIf { INSTALL_ID.matches(it) })
        }
    }
}

sealed class HelloReply {
    class Ok(val info: HelloInfo) : HelloReply()
    /** [hintFor]: given the install id the phone remembers, [BtProtocol.HINT_OTHER_INSTALL] / [BtProtocol.HINT_SAME_INSTALL] (only set for a paired peer). */
    class Err(val code: Int, val hintFor: ((String) -> Int)? = null) : HelloReply()
}
