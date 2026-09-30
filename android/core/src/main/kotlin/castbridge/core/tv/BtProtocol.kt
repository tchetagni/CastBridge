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
    const val DOWNLOAD = "CBTD"
    const val WANT_WIFI_DIRECT = 1
    /** RFCOMM service UUID shared by the TV and the phone app. */
    const val SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000001"
    /** Second RFCOMM service: a plain byte tunnel to the TV's SSH server (see castbridge.core.ssh.SshTunnel). */
    const val SSH_SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000002"
    const val OK = 0
    const val ERR_MAGIC = 1
    const val ERR_PIN = 2
    const val ERR_NAME = 3
    const val ERR_SPACE = 4
    const val ERR_LOCKED = 5
    const val ERR_IO = 6
    const val ERR_SIZE = 7
    const val ERR_NOT_FOUND = 8
    private const val MAX_NAME = 400

    /** Errors that retrying cannot fix. */
    fun isFatal(code: Int) = code in setOf(ERR_MAGIC, ERR_PIN, ERR_NAME, ERR_SPACE, ERR_LOCKED, ERR_SIZE, ERR_NOT_FOUND)

    fun describe(code: Int) = when (code) {
        OK -> "ok"
        ERR_MAGIC -> "protocole inconnu"
        ERR_PIN -> "PIN incorrect"
        ERR_NAME -> "nom de fichier refusé"
        ERR_SPACE -> "espace insuffisant sur la TV"
        ERR_LOCKED -> "trop d'essais, TV verrouillée"
        ERR_SIZE -> "taille invalide"
        ERR_NOT_FOUND -> "fichier introuvable sur la TV"
        else -> "erreur TV ($code)"
    }

    class Refused(val code: Int) : IOException(describe(code))

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
        /** Download TV -> phone (CBTD): resolves a stored file by name and returns it, or null if not found. */
        download: ((String) -> File?)? = null,
    ): Int {
        dir.mkdirs()
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        fun fail(code: Int): Int { dout.writeByte(code); dout.flush(); return code }

        val magic = ByteArray(4).also { din.readFully(it) }
        val m = String(magic, Charsets.US_ASCII)
        if (m == NEGOTIATE && negotiate != null) {
            val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
            val flags = din.readUnsignedByte()
            if (guard != null) when (guard.check(peer, pin)) {
                PinGuard.Result.OK -> {}
                PinGuard.Result.BAD -> return fail(ERR_PIN)
                PinGuard.Result.LOCKED -> return fail(ERR_LOCKED)
            }
            val text = negotiate(flags and WANT_WIFI_DIRECT != 0).encode().toByteArray(Charsets.UTF_8)
            dout.writeByte(OK); dout.writeShort(text.size); dout.write(text); dout.flush()
            return OK
        }
        if (m == castbridge.core.remote.RemoteBt.MAGIC && remote != null) {
            val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
            if (guard != null) when (guard.check(peer, pin)) {
                PinGuard.Result.OK -> {}
                PinGuard.Result.BAD -> return fail(ERR_PIN)
                PinGuard.Result.LOCKED -> return fail(ERR_LOCKED)
            }
            dout.writeByte(OK); dout.flush()
            remote(din, dout)
            return OK
        }
        if (m == DOWNLOAD && download != null) {
            val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
            val nameLen = din.readUnsignedShort()
            if (nameLen == 0 || nameLen > MAX_NAME) return fail(ERR_NAME)
            val name = String(ByteArray(nameLen).also { din.readFully(it) }, Charsets.UTF_8)
            if (guard != null) when (guard.check(peer, pin)) {
                PinGuard.Result.OK -> {}
                PinGuard.Result.BAD -> return fail(ERR_PIN)
                PinGuard.Result.LOCKED -> return fail(ERR_LOCKED)
            }
            val f = download(ReceiverServer.safeName(name) ?: return fail(ERR_NAME)) ?: return fail(ERR_NOT_FOUND)
            if (!f.isFile) return fail(ERR_NOT_FOUND)
            dout.writeByte(OK); dout.writeLong(f.length()); dout.flush()
            f.inputStream().use { inp ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val r = inp.read(buf)
                    if (r < 0) break
                    dout.write(buf, 0, r)
                    done += r
                }
                if (done != f.length()) throw IOException("fichier tronqué")
                dout.flush()
            }
            return OK
        }
        if (m != MAGIC) return fail(ERR_MAGIC)
        val pin = String(ByteArray(Pin.LENGTH).also { din.readFully(it) }, Charsets.US_ASCII)
        val nameLen = din.readUnsignedShort()
        if (nameLen == 0 || nameLen > MAX_NAME) return fail(ERR_NAME)
        val rawName = String(ByteArray(nameLen).also { din.readFully(it) }, Charsets.UTF_8)
        val total = din.readLong()

        if (guard != null) when (guard.check(peer, pin)) {
            PinGuard.Result.OK -> {}
            PinGuard.Result.BAD -> return fail(ERR_PIN)
            PinGuard.Result.LOCKED -> return fail(ERR_LOCKED)
        }
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
        require(Pin.isValidFormat(pin)) { "PIN must be ${Pin.LENGTH} digits" }
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
     * One attempt on an open link. Returns normally when the TV confirmed the whole file; throws
     * [Refused] for an ERR_* answer and [IOException] for a broken link.
     */
    fun send(
        input: InputStream, output: OutputStream, name: String, total: Long, pin: String,
        openAt: (Long) -> InputStream, onOffset: (Long) -> Unit = {}, onBytes: (Long) -> Unit = {},
    ) {
        require(Pin.isValidFormat(pin)) { "PIN must be ${Pin.LENGTH} digits" }
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

    /**
     * Download TV -> phone (CBTD): asks the TV for [name] and streams it into [openAt](0). Returns the file size;
     * throws [Refused] for an ERR_* answer and [IOException] for a broken link.
     */
    fun download(
        input: InputStream, output: OutputStream, name: String, pin: String,
        openAt: () -> java.io.OutputStream, onBytes: (Long) -> Unit = {},
    ): Long {
        require(Pin.isValidFormat(pin)) { "PIN must be ${Pin.LENGTH} digits" }
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        dout.write(DOWNLOAD.toByteArray(Charsets.US_ASCII))
        dout.write(pin.toByteArray(Charsets.US_ASCII))
        dout.writeShort(nameBytes.size)
        dout.write(nameBytes)
        dout.flush()

        val st = din.readUnsignedByte()
        if (st != OK) throw Refused(st)
        val size = din.readLong()
        openAt().use { out ->
            val buf = ByteArray(64 * 1024)
            var done = 0L
            while (done < size) {
                val r = din.read(buf, 0, minOf(buf.size.toLong(), size - done).toInt())
                if (r < 0) throw IOException("lien fermé à $done/$size")
                out.write(buf, 0, r)
                done += r
                onBytes(r.toLong())
            }
        }
        return size
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
                onState(ResumableUpload.State.Waiting(sent, total, e.message ?: "erreur"))
            } catch (e: IOException) {
                if (cancelled()) break
                failures++
                onState(ResumableUpload.State.Waiting(sent, total, e.message ?: e.javaClass.simpleName))
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
    }

    /**
     * May the TV create its Wi-Fi Direct group because a phone asked over Bluetooth? A group can disturb the TV's own Wi-Fi, so
     * only if the owner switched Wi-Fi Direct on, or if the TV has no network at all (nothing to disturb).
     */
    fun mayStartWifiDirect(requested: Boolean, enabledByOwner: Boolean, tvHasNetwork: Boolean) = requested && (enabledByOwner || !tvHasNetwork)

    fun plan(info: LinkInfo?, reachable: (String) -> Boolean, canJoinWifiDirect: Boolean): List<Route> = buildList {
        if (info != null) {
            info.ips.map { "http://$it:${info.port}" }.firstOrNull(reachable)?.let { add(Route.Lan(it)) }
            if (canJoinWifiDirect && info.wdSsid != null && info.wdPass != null)
                add(Route.Direct(info.wdSsid, info.wdPass, "http://${info.wdIp ?: WifiDirect.GROUP_OWNER_IP}:${info.port}"))
        }
        add(Route.Bluetooth)
    }
}
