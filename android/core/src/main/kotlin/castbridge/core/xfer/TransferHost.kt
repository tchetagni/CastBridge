package castbridge.core.xfer

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Where the TV puts a new transfer: a real folder of a volume. null from the allocator = refused (see [Allocation.Refused]). */
sealed class Allocation {
    /** [preallocate]: size the data file at once (free on ext4/f2fs; on FAT/exFAT the kernel would write zeros over the whole size first, see [PartAssembler.preallocates]). */
    class At(val dir: File, val diskName: String, val volumeId: String, val preallocate: Boolean = true) : Allocation()
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
        private val fin = java.util.concurrent.atomic.AtomicBoolean(false)
        /** A `finish` is verifying right now (a second one is answered « verifying » at once instead of waiting for the lock). */
        var finishing: Boolean
            get() = fin.get()
            set(v) = fin.set(v)
        /** True for the one caller that may verify now; the others must not wait. */
        fun tryBeginFinish(): Boolean = fin.compareAndSet(false, true)
    }
    private val sessions = ConcurrentHashMap<String, Session>()
    val stats = WriteStats()
    /** Size of the finished file the phone means by (name, size of the manifest): the TV may have filed and renamed it, so it is asked with the size too. */
    @Volatile var finalSizeOf: (String, Long) -> Long? = { _, _ -> null }

    sealed class Begin {
        class Ok(val s: Session, val resumed: Boolean) : Begin()
        /** The TV already holds the finished file. */
        object AlreadyThere : Begin()
        class Refused(val http: Int, val message: String) : Begin()
    }

    /** "Network alone" session for the bench: nothing is stored, no volume is touched. */
    fun beginDiscard(m: Manifest): Begin {
        sessions[m.id]?.let { return Begin.Ok(it, true) }
        if (sessions.size >= maxSessions) return Begin.Refused(429, "too many transfers in progress")
        val s = Session(m, PartAssembler.open(File("."), m, stats, now, discard = true), "", "", File("."))
        return Begin.Ok(sessions.putIfAbsent(m.id, s) ?: s, false)
    }

    fun begin(m: Manifest, allocate: (Manifest) -> Allocation): Begin {
        sessions[m.id]?.let { if (!it.assembler.manifest.equals(m)) discard(m.id) else return Begin.Ok(it, true) }
        if (finalSizeOf(m.name, m.size) == m.size) return Begin.AlreadyThere
        if (sessions.size >= maxSessions) return Begin.Refused(429, "too many transfers in progress")
        val a = allocate(m)
        if (a is Allocation.Refused) return Begin.Refused(a.http, a.message)
        a as Allocation.At
        return try {
            val asm = PartAssembler.open(a.dir, m, stats, now, preallocate = a.preallocate, persistEveryMs = { persistEveryMs() }, syncEveryBytes = { syncEveryBytes() })
            val s = Session(m, asm, a.diskName, a.volumeId, a.dir)
            val prev = sessions.putIfAbsent(m.id, s)
            if (prev != null) { asm.close(); Begin.Ok(prev, true) } else Begin.Ok(s, asm.map.count() > 0)
        } catch (e: IOException) { Begin.Refused(507, e.message ?: "disk error") }
    }

    /** Concurrent chunk requests the TV accepts right now ([PlaybackGovernor]: fewer while a video plays); clamped to 1..[maxStreams]. */
    @Volatile var streamLimit: () -> Int = { maxStreams }
    fun allowedStreams(): Int = runCatching(streamLimit).getOrDefault(maxStreams).coerceIn(1, maxStreams)
    /** Interval between two saves of a block map ([PlaybackGovernor]: spaced out while a video plays). */
    @Volatile var persistEveryMs: () -> Long = { 1000L }
    /** R-20: bytes between two periodic fsyncs of a copy's data file ([PlaybackPriority]: 4x while a video plays); 0 = none (the final force alone). */
    @Volatile var syncEveryBytes: () -> Long = { 0L }

    fun session(id: String): Session? = sessions[id]

    /** The sessions that write to the volume [volumeId] (« Préparer le retrait de la clé USB »: which copies hold the key, which to close). */
    fun sessionsOn(volumeId: String): List<Session> = sessions.values.filter { it.volumeId == volumeId }

    /**
     * Head first: on a volume where the data file is NOT preallocated, a block far beyond the contiguous prefix would make the kernel zero-fill the gap
     * synchronously (the phone's 20 s watchdog trips, the copy stalls): it is refused (429 busy, the phone retries) until the prefix catches up.
     * Always true when the file was sized at once (ext4/f2fs: sparse, free) and for a block behind the prefix (a resend).
     */
    fun admitAhead(s: Session, idx: Int): Boolean {
        if (s.assembler.preallocated || s.assembler.discard) return true
        val m = s.manifest
        val contiguous = minOf(s.assembler.map.leading().toLong() * m.blockSize, m.size)
        return PlaybackPriority.admitAhead(m.offset(idx), contiguous, PlaybackPriority.headWindow(m.blockSize, maxStreams))
    }
    /** A `finish` of [name] is reading the file back right now, whatever session object (block size) started it. */
    fun finishingName(name: String): Boolean = sessions.values.any { it.finishing && it.manifest.name == name }
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

    /**
     * R-21 : une session sans activité depuis [maxIdleMs] (10 min) est FERMÉE (fichier et fil rendus) mais son état reste sur disque : au retour du
     * téléphone, `begin` la rouvre et reprend où elle en était. Différent de [sweep], qui efface les fichiers d'une session abandonnée depuis des jours.
     */
    fun closeIdle(maxIdleMs: Long = IDLE_SESSION_MS): Int {
        var n = 0
        for ((id, s) in sessions) if (!s.finishing && now() - s.assembler.touched > maxIdleMs) { if (sessions.remove(id, s)) { s.assembler.close(); n++ } }
        return n
    }
    /** Des octets attendent le disque depuis ce nombre de ms sans qu'une écriture aboutisse (0 = rien de bloqué). */
    fun diskStalledMs(): Long = stats.stalledMs()

    fun active(): Int = sessions.size
    /** Bytes of [s] already on the TV (whole blocks; at most one block too many when the short last block is among them). */
    fun receivedBytes(s: Session): Long = minOf(s.assembler.map.count().toLong() * s.manifest.blockSize, s.manifest.size)
    fun inflightIds(): Set<String> = sessions.keys.toSet()

    // ---- JSON (hand-written like the rest of the API) ----
    /** French hint when the disk (not the Wi-Fi) is what limits the copy; null while unknown or fast enough. */
    /** R-15: « Copie ralentie pour ne pas gêner la lecture » while the TV really holds the copy back for its player (set by the server). */
    @Volatile var slowedNote: () -> String? = { null }

    fun note(s: Session): String? {
        slowedNote()?.let { return it }
        val bps = stats.bytesPerSec()
        if (s.assembler.discard || bps <= 0 || s.assembler.map.count() < 2 || bps >= SLOW_DISK_BPS) return null
        return "Le disque de la TV écrit à ${String.format(java.util.Locale.ROOT, "%.1f", bps / 1e6).replace('.', ',')} Mo/s : c'est lui qui limite la copie, pas le Wi-Fi. " +
            "Un support plus rapide (clé USB plus rapide, autre port) accélérera la copie."
    }

    fun stateJson(s: Session, withHashes: Boolean = false): String {
        val m = s.manifest; val a = s.assembler
        return "{\"id\":\"${m.id}\",\"name\":${q(m.name)},\"size\":${m.size},\"blockSize\":${m.blockSize},\"blocks\":${m.blocks}," +
            "\"done\":${a.map.count()},\"map\":\"${a.map.toHex()}\",\"volume\":${q(s.volumeId)}," +
            "\"contiguous\":${minOf(a.map.leading().toLong() * m.blockSize, m.size)},\"writeBps\":${stats.bytesPerSec()},\"queued\":${stats.queued()},\"maxStreams\":${allowedStreams()},\"ordered\":${!a.preallocated},\"ready\":${a.map.complete()}" + (note(s)?.let { ",\"note\":${q(it)}" } ?: "") +
            (if (withHashes) ",\"hashes\":\"${a.hashesJoined()}\"" else "") + "}"
    }

    companion object {
        const val API_VERSION = 1
        const val SLOW_DISK_BPS = 3_000_000L
        const val IDLE_SESSION_MS = 10 * 60_000L
        /** Une écriture qui n'aboutit plus depuis ce temps : le disque est bloqué, la TV le dit (cause `stalled`) au lieu de faire renvoyer les blocs. */
        const val DISK_STALL_MS = 15_000L
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    }
}
