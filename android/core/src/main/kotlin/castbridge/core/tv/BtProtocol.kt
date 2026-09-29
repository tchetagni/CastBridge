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
    /** RFCOMM service UUID shared by the TV and the phone app. */
    const val SERVICE_UUID = "7c5e3b9a-4d2f-4c61-9b0e-cb0000000001"
    const val OK = 0
    const val ERR_MAGIC = 1
    const val ERR_PIN = 2
    const val ERR_NAME = 3
    const val ERR_SPACE = 4
    const val ERR_LOCKED = 5
    const val ERR_IO = 6
    const val ERR_SIZE = 7
    private const val MAX_NAME = 400

    /** Errors that retrying cannot fix. */
    fun isFatal(code: Int) = code in setOf(ERR_MAGIC, ERR_PIN, ERR_NAME, ERR_SPACE, ERR_LOCKED, ERR_SIZE)

    fun describe(code: Int) = when (code) {
        OK -> "ok"
        ERR_MAGIC -> "protocole inconnu"
        ERR_PIN -> "PIN incorrect"
        ERR_NAME -> "nom de fichier refusé"
        ERR_SPACE -> "espace insuffisant sur la TV"
        ERR_LOCKED -> "trop d'essais, TV verrouillée"
        ERR_SIZE -> "taille invalide"
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
    ): Int {
        dir.mkdirs()
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        fun fail(code: Int): Int { dout.writeByte(code); dout.flush(); return code }

        val magic = ByteArray(4).also { din.readFully(it) }
        if (String(magic, Charsets.US_ASCII) != MAGIC) return fail(ERR_MAGIC)
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
            FileOutputStream(pf, true).use { out ->
                val buf = ByteArray(64 * 1024)
                while (done < total) {
                    val r = input.read(buf, 0, minOf(buf.size.toLong(), total - done).toInt())
                    if (r < 0) throw IOException("link closed at $done/$total")
                    out.write(buf, 0, r)
                    done += r
                    onProgress(name, done, total)
                }
            }
            if (final.exists()) final.delete()
            if (!pf.renameTo(final)) return fail(ERR_IO)
            dout.writeByte(OK); dout.flush()
            return OK
        }
    }

    // ---------------------------------------------------------------- sender (phone)

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
