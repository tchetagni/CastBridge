package castbridge.core.xfer

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Where the TV puts a new transfer: a real folder of a volume. null from the allocator = refused (see [Allocation.Refused]). */
sealed class Allocation {
    class At(val dir: File, val diskName: String, val volumeId: String) : Allocation()
    class Refused(val http: Int, val message: String) : Allocation()
}

/**
 * The TV's multi-connection transfer service, independent of HTTP (ReceiverServer maps it to `/api/transfer/...`).
 * Back-pressure: the TV announces how fast its disk absorbs data ([State.writeBps]) and how much it holds in flight; a chunk that would push
 * the in-flight bytes beyond [maxInflight] is answered "busy" instead of being read, so the TV never fills its memory for nothing.
 */
class TransferHost(
    val maxStreams: Int = 6,
    private val maxSessions: Int = 3,
    private val now: () -> Long = System::currentTimeMillis,
) {
    class Session(val manifest: Manifest, val assembler: PartAssembler, val diskName: String, val volumeId: String, val dir: File) {
        @Volatile var finishing = false
    }
    private val sessions = ConcurrentHashMap<String, Session>()
    val stats = WriteStats()
    @Volatile var finalSizeOf: (String) -> Long? = { null }

    sealed class Begin {
        class Ok(val s: Session, val resumed: Boolean) : Begin()
        /** The TV already holds the finished file. */
        object AlreadyThere : Begin()
        class Refused(val http: Int, val message: String) : Begin()
    }

    fun begin(m: Manifest, allocate: (Manifest) -> Allocation): Begin {
        sessions[m.id]?.let { if (!it.assembler.manifest.equals(m)) discard(m.id) else return Begin.Ok(it, true) }
        if (finalSizeOf(m.name) == m.size) return Begin.AlreadyThere
        if (sessions.size >= maxSessions) return Begin.Refused(429, "too many transfers in progress")
        val a = allocate(m)
        if (a is Allocation.Refused) return Begin.Refused(a.http, a.message)
        a as Allocation.At
        return try {
            val asm = PartAssembler.open(a.dir, m, stats, now)
            val s = Session(m, asm, a.diskName, a.volumeId, a.dir)
            val prev = sessions.putIfAbsent(m.id, s)
            if (prev != null) { asm.close(); Begin.Ok(prev, true) } else Begin.Ok(s, asm.map.count() > 0)
        } catch (e: IOException) { Begin.Refused(507, e.message ?: "disk error") }
    }

    fun session(id: String): Session? = sessions[id]
    fun hasName(name: String): Boolean = sessions.values.any { it.manifest.name == name }

    /** True if one more block of [blockBytes] may be taken now (back-pressure). */
    fun mayAccept(blockBytes: Long): Boolean {
        val q = stats.queued()
        return q == 0L || q + blockBytes <= maxInflight(blockBytes)
    }
    /** What the disk can absorb in about 3 seconds, never less than 2 blocks, never more than 6 (the TV has little memory to spare). */
    fun maxInflight(blockBytes: Long): Long {
        val bps = stats.bytesPerSec()
        return if (bps <= 0) blockBytes * maxStreams else (bps * 3).coerceIn(blockBytes * 2, blockBytes * 6)
    }

    fun remove(id: String) { sessions.remove(id) }
    fun discard(id: String) { sessions.remove(id)?.assembler?.discard() }

    /** Drops sessions nobody touched for [maxAgeMs] and their files. */
    fun sweep(maxAgeMs: Long): Int {
        var n = 0
        for ((id, s) in sessions) if (!s.finishing && now() - s.assembler.touched > maxAgeMs) { discard(id); n++ }
        return n
    }

    fun active(): Int = sessions.size
    fun inflightIds(): Set<String> = sessions.keys.toSet()

    // ---- JSON (hand-written like the rest of the API) ----
    fun stateJson(s: Session, withHashes: Boolean = false): String {
        val m = s.manifest; val a = s.assembler
        return "{\"id\":\"${m.id}\",\"name\":${q(m.name)},\"size\":${m.size},\"blockSize\":${m.blockSize},\"blocks\":${m.blocks}," +
            "\"done\":${a.map.count()},\"map\":\"${a.map.toHex()}\",\"volume\":${q(s.volumeId)}," +
            "\"writeBps\":${stats.bytesPerSec()},\"queued\":${stats.queued()},\"maxStreams\":$maxStreams,\"ready\":${a.map.complete()}" +
            (if (withHashes) ",\"hashes\":\"${a.hashesJoined()}\"" else "") + "}"
    }

    companion object {
        const val API_VERSION = 1
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    }
}
