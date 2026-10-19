package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * "Modest TV" profile: every tunable that trades memory / storage for comfort, in one place.
 * Defaults target a 32-bit ARMv7 box with little RAM and little flash.
 */
data class TvProfile(
    /** Never let the device drop below this much free space because of us. */
    val minFreeBytes: Long = 100L shl 20,
    /** Explicit quota for the videos folder; 0 = automatic (see [Storage.quota]). */
    val quotaBytes: Long = 0,
    /** Automatic quota: this fraction of (used + free)... */
    val quotaFraction: Double = 0.5,
    /** ...but never more than this. */
    val quotaCapBytes: Long = 8L shl 30,
    /** Buffer for every file copy (HTTP upload, Bluetooth, USB import). Streaming only, nothing is held in RAM. */
    val ioBufferBytes: Int = 64 * 1024,
    /** Concurrent HTTP connections served (each keep-alive connection holds a thread). */
    val maxHttpThreads: Int = 8,
    /** /api/info recomputes its file listing at most this often (polled every second by clients). */
    val infoCacheMs: Long = 1000,
    /** Orphan ".part" files older than this are deleted at start-up. */
    val orphanPartMaxAgeMs: Long = 24L * 3600_000,
    /** Delete a file once it has played to the end. */
    val deleteAfterPlay: Boolean = false,
    /** When the quota is full, delete the oldest already-played files (never the one playing) to make room. */
    val evictPlayed: Boolean = false,
    /** Where new uploads go: "auto" (removable drive when usable, else internal), "internal", or a volume id. */
    val target: String = "auto",
    /** Quota of a removable volume: this fraction of (used + free), no cap (the drive is there to hold a lot). */
    val removableQuotaFraction: Double = 0.95,
    /**
     * Phone <-> TV transfers run at full speed only if, once the file is complete, the destination volume keeps at least this
     * much free space; otherwise another volume is tried (target auto) or the transfer is refused before the first byte.
     * Replaces [minFreeBytes] for these transfers (moves, Bluetooth and USB import keep [minFreeBytes]). 0 = off.
     */
    val minFreeAfterTransfer: Long = 1L shl 30,
    /** Write buffer of an HTTP upload (the TV writes the disk in blocks this big: fewer, larger writes on a FUSE/USB volume). */
    val uploadBufferBytes: Int = 256 * 1024,
    /** On a removable drive, flush an upload to the medium every this many bytes (and at the end), never after every block. */
    val removableSyncBytes: Long = 64L shl 20,
    /** « Contenus lourds sur la clé USB » : with a drive present, videos go to `<clé>/Download/CastBridge/` (survives the uninstall). */
    val heavyOnUsb: Boolean = true,
    /** Drive chosen by the user when several are plugged in ("" = automatic: the emptiest). */
    val heavyDriveId: String = "",
    /** Flux de réception simultanés (profil de ressources) ; 0 = automatique (fils HTTP - 2, comme avant). */
    val maxTransferStreams: Int = 0,
    /** Empreintes de l'index de contenu gardées en mémoire (profil de ressources ; 100 000 = valeur d'avant). */
    val indexEntries: Int = 100_000,
    /** « low » / « normal » annoncé au téléphone par `/api/transfer/caps` (champ optionnel `profile`) ; "" = non annoncé. */
    val resourceProfile: String = "",
)

/** The "keep 1 GB free after the transfer" rule and its human explanation. Pure. */
object TransferRule {
    /** Free space to keep on the destination after a phone <-> TV transfer. */
    fun minFree(p: TvProfile): Long = maxOf(p.minFreeBytes, p.minFreeAfterTransfer)

    /** Free bytes left on a volume with [free] bytes once [remaining] more bytes are written. */
    fun freeAfter(free: Long, remaining: Long): Long = free - remaining

    fun ok(free: Long, remaining: Long, minFree: Long): Boolean = free < 0 || freeAfter(free, remaining) >= minFree

    /** "1 Go", "640 Mo", "1.5 Go" (rounded down; [up] rounds up: for what must be freed). */
    fun size(b: Long, up: Boolean = false): String = when {
        b >= 1L shl 30 && b % (1L shl 30) == 0L -> "${b shr 30} Go"
        b >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.1f Go", (if (up) Math.ceil(b * 10.0 / (1L shl 30)) else Math.floor(b * 10.0 / (1L shl 30))) / 10)
        b >= 0 -> "${(if (up) b + (1L shl 20) - 1 else b) shr 20} Mo"
        else -> "-" + size(-b, !up)
    }

    /**
     * Why a transfer cannot go to [label]: "il resterait 640 Mo sur Mémoire interne, il en faut 1 Go : libérez 384 Mo ou branchez la clé USB".
     * [driveAbsent] adds the advice to plug the drive.
     */
    fun message(label: String, free: Long, remaining: Long, minFree: Long, driveAbsent: Boolean): String {
        val after = freeAfter(free, remaining)
        val missing = minFree - after
        val head = if (after >= 0) "Espace insuffisant : il resterait ${size(after)} sur $label après le transfert, il en faut ${size(minFree)}"
                   else "Espace insuffisant : le fichier ne tient pas sur $label (il manque ${size(-after, up = true)}) et il faut garder ${size(minFree)} libres"
        return "$head : libérez ${size(missing, up = true)}" + if (driveAbsent) " ou branchez la clé USB." else " ou choisissez un autre volume."
    }
}

/**
 * R-55: what follows the « Espace insuffisant : … » headline ([TransferRule.message]) of a refused copy: one sentence per OTHER volume present, saying why it did not take
 * the copy. A key with 23 Go free that the TV leaves unused and never explains looks like a bug (2026-10-18, the owner: « l'erreur … n'est pas normale »). Pure.
 *
 * Why, most telling first: the write test failed (read-only), the file is too big for the file system, the destination was asked for by name so the key was not even
 * considered (the TV's « Où ranger les nouveaux fichiers » setting, or the destination chosen on the phone), no room, quota reached, a slow key kept for the last resort.
 */
object SpaceRefusalText {
    const val READ_ONLY = "lecture seule (test d'écriture raté)"
    const val SLOW = "jugée lente, pourtant retenue en dernier recours"
    const val UNKNOWN = "raison non relevée"
    /** The label of the TV's setting (MENU › Stockage) and of the phone's chooser (« Destination sur la TV »). */
    const val SETTING = "Où ranger les nouveaux fichiers"
    const val PHONE_CHOICE = "Destination sur la TV"

    /**
     * One sentence per volume of [volumes] other than [headId] (the one the headline speaks of), in the order given; none for a volume that needs no explanation.
     * [skipped] = what the policy set aside, plus the volumes that said no once asked (reason "not enough space" / "quota exceeded"); [target] = the destination asked for;
     * [byPhone] = that destination came with the request (the phone's choice) rather than from the TV's own setting.
     */
    fun sentences(volumes: List<StorageVolume>, skipped: List<StoragePolicy.Skipped>, target: String, size: Long, minFree: Long,
                  headId: String?, byPhone: Boolean = false): List<String> {
        val internalLabel = volumes.firstOrNull { it.kind == VolumeKind.INTERNAL }?.label ?: "Mémoire interne"
        return volumes.filter { it.id != headId }.mapNotNull { v ->
            reason(v, skipped.lastOrNull { it.volumeId == v.id }?.reason, target, size, minFree, internalLabel, byPhone)?.let { "${name(v)} non utilisée : $it." }
        }
    }

    /** The headline, then the sentences after a space; the headline alone when there is nothing to add (a single volume: the message is what it always was). */
    fun with(headline: String, sentences: List<String>): String = if (sentences.isEmpty()) headline else headline + " " + sentences.joinToString(" ")

    private fun name(v: StorageVolume) = if (v.free >= 0) "${v.label} (${TransferRule.size(v.free)} libres)" else v.label

    private fun reason(v: StorageVolume, why: String?, target: String, size: Long, minFree: Long, internalLabel: String, byPhone: Boolean): String? {
        val room = TransferRule.ok(v.free, size, minFree) && why != "not enough space"
        // not even considered: the destination was asked for by name
        val left = when (target) { StoragePolicy.AUTO -> false; StoragePolicy.INTERNAL -> v.kind != VolumeKind.INTERNAL; else -> v.id != target }
        return when {
            !v.writable -> READ_ONLY
            size > v.maxFileBytes -> "fichier trop gros pour ${v.fs.label}"
            left && room -> when {
                target != StoragePolicy.INTERNAL -> null          // a key was asked for by name: the other volumes are not judged (the headline already says « choisissez un autre volume »)
                byPhone -> "choix « $PHONE_CHOICE : $internalLabel » du téléphone ; choisissez Automatique ou la clé"
                else -> "réglage « $SETTING : $internalLabel » ; choisissez Automatique ou la clé dans MENU › Stockage"
            }
            !room -> noRoom(v, size, minFree)
            why == "quota exceeded" -> "quota de stockage atteint"
            v.kind == VolumeKind.REMOVABLE && v.writeBps in 1 until StoragePolicy.SLOW_BPS -> SLOW
            target == StoragePolicy.AUTO -> UNKNOWN
            else -> null
        }
    }

    private fun noRoom(v: StorageVolume, size: Long, minFree: Long): String {
        val after = TransferRule.freeAfter(v.free, size)
        return "pas assez de place" + when {
            after < 0 -> " (le fichier ne tient pas, il manque ${TransferRule.size(-after, up = true)})"
            after < minFree -> " (il resterait ${TransferRule.size(after)} après le transfert, il en faut ${TransferRule.size(minFree)})"
            else -> ""
        }
    }
}

/**
 * R-55: a removable volume is write-tested once, when it is mounted. A key that failed that one test (not ready yet, a hiccup) stays « lecture seule » until it is plugged in
 * again, and the TV used to say « pas assez de place » about the internal storage without a word about it. A copy refused for lack of room now tests such a key again,
 * at most once a minute per key (a key that really is write-protected must not be hammered by a phone that retries). Pure.
 */
object KeyRetest {
    const val MIN_INTERVAL_MS = 60_000L

    /** True when a key last tested at [lastMs] (null = never) may be tested again at [nowMs]; a clock set back allows it too. */
    fun allowed(lastMs: Long?, nowMs: Long): Boolean = lastMs == null || nowMs < lastMs || nowMs - lastMs >= MIN_INTERVAL_MS

    /**
     * Ids of the removable volumes to test again for a refusal: those the policy set aside as « not writable » ([skipped]) and not tested within the last minute ([last]).
     * Nothing when [target] is the internal storage: that setting left the keys out, they were not set aside for being read-only.
     */
    fun due(skipped: List<StoragePolicy.Skipped>, volumes: List<StorageVolume>, target: String, last: Map<String, Long>, nowMs: Long): List<String> =
        if (target == StoragePolicy.INTERNAL) emptyList()
        else skipped.filter { it.reason.startsWith("not writable") }
            .mapNotNull { s -> volumes.firstOrNull { it.id == s.volumeId && it.kind == VolumeKind.REMOVABLE } }
            .map { it.id }.distinct().filter { allowed(last[it], nowMs) }
}

/** Storage accounting for the videos folder. Pure file logic. */
object Storage {
    const val PART = ".part"
    private const val PLAYED = ".played"

    /** Every visible file under [dir], including the category folders of filed files (only the category folders of [Filing.ROOTS] are entered; hidden entries, the bin, the downloads and symbolic links never are). */
    private fun walk(dir: File, depth: Int = 0): List<File> = dir.listFiles().orEmpty().flatMap { f ->
        when {
            f.name.startsWith(".") -> emptyList()
            f.isFile -> listOf(f)
            f.isDirectory && depth < 5 && (depth > 0 || f.name in Filing.ROOTS) && !UsbPaths.isSymlink(f) -> walk(f, depth + 1)
            else -> emptyList()
        }
    }

    fun files(dir: File): List<File> = walk(dir).filter { !it.name.endsWith(PART) && !it.name.endsWith(Meta.SUFFIX) }

    /** Bytes used by finished files and partial uploads. */
    fun used(dir: File): Long = walk(dir).sumOf { it.length() }

    /**
     * Effective quota of one volume. Internal: explicit, else min(fraction of what we and the free space add up to, cap).
     * Removable drive: [TvProfile.removableQuotaFraction] of (used + free), uncapped; the explicit quota is meant for the
     * scarce internal flash and does not apply to a drive.
     */
    fun quota(dir: File, p: TvProfile, used: Long = used(dir), free: Long = dir.usableSpace, kind: VolumeKind = VolumeKind.INTERNAL): Long =
        if (kind == VolumeKind.REMOVABLE) ((used + free) * p.removableQuotaFraction).toLong()
        else if (p.quotaBytes > 0) p.quotaBytes
        else minOf(((used + free) * p.quotaFraction).toLong(), p.quotaCapBytes)

    /** Null if [incoming] more bytes fit, else why not. */
    fun refusal(dir: File, p: TvProfile, incoming: Long, free: Long = dir.usableSpace, kind: VolumeKind = VolumeKind.INTERNAL): String? {
        if (free - incoming < p.minFreeBytes) return "not enough space"
        val used = used(dir)
        if (used + incoming > quota(dir, p, used, free, kind)) return "quota exceeded"
        return null
    }

    /** Deletes partial uploads (and size sidecars without any data file) untouched for [maxAgeMs]; returns how many were removed. */
    fun cleanOrphans(dir: File, maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        // Partial uploads first: a size sidecar is an orphan once its .part is gone.
        val all = dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { !it.name.endsWith(PART) }
        var n = 0
        for (f in all) {
            if (now - f.lastModified() <= maxAgeMs) continue
            val orphan = when {
                f.name.endsWith(PART) -> true
                f.name.endsWith(Meta.SUFFIX) -> {
                    val base = f.name.removeSuffix(Meta.SUFFIX)
                    !File(dir, base).exists() && !File(dir, base + PART).exists()
                }
                else -> false
            }
            if (orphan && f.delete()) n++
        }
        n += castbridge.core.xfer.PartAssembler.sweep(dir, maxAgeMs, now)    // abandoned multi-connection transfers (.cbx)
        return n
    }

    // ---- "played" marks (persisted next to the videos, hidden from listings) ----

    fun playedNames(dir: File): Set<String> = runCatching { File(dir, PLAYED).readLines().filter { it.isNotEmpty() }.toSet() }.getOrDefault(emptySet())

    fun markPlayed(dir: File, name: String) {
        val set = playedNames(dir)
        if (name !in set) runCatching { AtomicFile.write(File(dir, PLAYED), ((set + name).joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)) }
    }

    fun forget(dir: File, name: String) {
        val set = playedNames(dir)
        if (name in set) runCatching { AtomicFile.write(File(dir, PLAYED), ((set - name).joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Oldest already-played finished files (never [protect]) whose deletion would free at least [needed]
     * bytes; null if even all of them are not enough.
     */
    fun evictionPlan(dir: File, needed: Long, protect: String?): List<File>? {
        val played = playedNames(dir)
        val candidates = files(dir).filter { it.name in played && it.name != protect }.sortedBy { it.lastModified() }
        val plan = ArrayList<File>(); var freed = 0L
        for (f in candidates) { if (freed >= needed) break; plan += f; freed += f.length() }
        return plan.takeIf { freed >= needed }
    }
}

/** Bounded HTTP worker pool for NanoHTTPD (its default spawns an unbounded thread per connection). */
class BoundedRunner(private val maxThreads: Int, queue: Int = 32) : NanoHTTPD.AsyncRunner {
    private val open = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<NanoHTTPD.ClientHandler, Boolean>())
    private val pool = ThreadPoolExecutor(maxThreads, maxThreads, 30, TimeUnit.SECONDS, LinkedBlockingQueue(queue)) { r ->
        Thread(r, "cb-http").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    override fun closeAll() { open.toList().forEach { it.close() }; pool.shutdownNow() }
    override fun closed(clientHandler: NanoHTTPD.ClientHandler) { open.remove(clientHandler) }
    override fun exec(clientHandler: NanoHTTPD.ClientHandler) {
        open.add(clientHandler)
        try { pool.execute(clientHandler) } catch (e: RejectedExecutionException) { open.remove(clientHandler); clientHandler.close() }
    }
}

/** RFC 7233 single-range parsing for file servers (206 / 416 decisions). */
object HttpRange {
    sealed class R {
        object Full : R()
        data class Part(val start: Long, val end: Long) : R() { val length get() = end - start + 1 }
        object Unsatisfiable : R()
    }

    /** Only the first range of a multi-range request is honoured (allowed by the RFC). An unparsable header is ignored. */
    fun parse(header: String?, total: Long): R {
        val m = header?.let { Regex("^\\s*bytes\\s*=\\s*(\\d*)\\s*-\\s*(\\d*)").find(it) } ?: return R.Full
        val s = m.groupValues[1]; val e = m.groupValues[2]
        if (s.isEmpty() && e.isEmpty()) return R.Full
        if (total <= 0) return R.Unsatisfiable
        val start: Long; var end = total - 1
        if (s.isEmpty()) {                                   // suffix: the last N bytes
            val n = e.toLongOrNull() ?: return R.Full
            if (n == 0L) return R.Unsatisfiable
            start = maxOf(0, total - n)
        } else {
            start = s.toLongOrNull() ?: return R.Full
            if (e.isNotEmpty()) end = minOf(e.toLongOrNull() ?: return R.Full, total - 1)
        }
        return if (start >= total || start > end) R.Unsatisfiable else R.Part(start, end)
    }
}
