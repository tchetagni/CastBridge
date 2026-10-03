package castbridge.core.tv

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import java.io.File
import java.io.FileOutputStream

/**
 * What proves that a file sent to the TV is the very file the phone holds, so that the original may then be deleted (« Déplacer »).
 * Pure functions: the phone calls them, nothing here deletes. Whatever is not listed here is NOT a proof, and without proof the original stays.
 */
object MoveProof {
    /** Bytes compared at the head and at the tail of a file already on the TV (a Range read of `/stream`). */
    const val EDGE = 64 * 1024

    /**
     * A transfer by this job: every byte of the file was sent by it ([sentByThisJob] == [size]), and the TV answered taking the size into account
     * ([tvCheckedSize], see [TvClient.Part.sizeChecked]): an older TV answering « done » for any homonym proves nothing.
     */
    fun byUpload(sentByThisJob: Long, size: Long, tvCheckedSize: Boolean, tvDone: Boolean): Boolean =
        size > 0 && sentByThisJob == size && tvCheckedSize && tvDone

    /** A fast (multi-connection) transfer that ended `Done` with a verified Merkle/SHA root. */
    fun byFastTransfer(done: Boolean, rootVerified: Boolean): Boolean = done && rootVerified

    /**
     * « Already on the TV »: same size AND same head AND same tail (the [EDGE] first and last bytes, read through `/stream`). A null edge (not read) is no proof.
     * Size alone is never enough.
     */
    fun alreadyThere(localSize: Long, tvSize: Long, localHead: ByteArray?, tvHead: ByteArray?, localTail: ByteArray?, tvTail: ByteArray?): Boolean =
        localSize > 0 && localSize == tvSize && localHead != null && tvHead != null && localTail != null && tvTail != null &&
            localHead.contentEquals(tvHead) && localTail.contentEquals(tvTail)

    /**
     * « Déplacer » a file whose content the TV ALREADY holds (nothing is copied, R-12). FIRST proof, by hash — all of:
     *  1. the phone computed [localSha] itself, from the original's bytes, at the moment of the decision (never a hash kept from earlier, never a hash received);
     *  2. the TV answered GET /api/have with `fresh=1` for a FINISHED file ([tvComplete]) whose hash THIS run of the TV computed from the bytes on its disk
     *     ([tvFresh]; a line of the signed cache `.cbhash` is never fresh);
     *  3. both sizes are equal and > 0, both SHA-256 are equal (64 hex characters).
     */
    fun byContentHash(localSize: Long, localSha: String?, tvSize: Long, tvSha: String?, tvComplete: Boolean, tvFresh: Boolean): Boolean =
        localSize > 0 && localSize == tvSize && tvComplete && tvFresh && ContentHash.valid(localSha?.lowercase()) && ContentHash.valid(tvSha?.lowercase()) &&
            localSha!!.lowercase() == tvSha!!.lowercase()

    /** SECOND proof: the phone read the first and last [EDGE] bytes of the TV's file itself (/stream/ Range) and they equal its own ([alreadyThere]). */
    fun byEdges(localSize: Long, tvSize: Long, localHead: ByteArray?, tvHead: ByteArray?, localTail: ByteArray?, tvTail: ByteArray?): Boolean =
        alreadyThere(localSize, tvSize, localHead, tvHead, localTail, tvTail)

    /**
     * The original of a MOVE without copy may be offered for deletion only with BOTH proofs; then Android or the app asks the user (MoveHandler). A name, a size,
     * « indexing », a cached hash, a twin of the queue or an older TV are never proofs: the original stays.
     */
    fun mayDeleteWithoutCopy(hashProof: Boolean, edgesProof: Boolean): Boolean = hashProof && edgesProof

    /** Where to read the head and the tail of a file of [size] bytes: (offset, length) pairs. */
    fun edges(size: Long): Pair<Pair<Long, Int>, Pair<Long, Int>> {
        val n = minOf(size, EDGE.toLong()).toInt()
        return (0L to n) to (maxOf(0L, size - EDGE) to n)
    }

    const val OLD_TV_TEXT = "Mise à jour de la TV nécessaire pour Déplacer : l'original est conservé."
    const val ALREADY_THERE_TEXT = "Déjà sur la TV : l'original est conservé."
}

/**
 * Pending « Déplacer » requests (the phone deletes an original only after [MoveProof]), kept in a file, one JSON line per request, so that a restart of the
 * app loses none and handles them one at a time. [add] and [done] rewrite the file whole through a temporary file (atomic rename, synced); a damaged line is skipped,
 * never a reason to delete anything.
 */
class MoveRequests(private val file: File) {
    /** [uri] is the phone's own handle of the original (opaque here), [size] its size when queued. */
    data class Request(val id: String, val uri: String, val name: String, val size: Long)

    private val lock = Any()

    fun all(): List<Request> = synchronized(lock) { read() }

    /** The next request to handle, or null. Handled one at a time: the next one only after [done] of this one. */
    fun next(): Request? = synchronized(lock) { read().firstOrNull() }

    /** Queues a request (a second request for the same [Request.uri] is the same request). */
    fun add(r: Request) = synchronized(lock) {
        val cur = read()
        if (cur.none { it.uri == r.uri }) write(cur + r)
    }

    /** The request [id] was handled (deleted, or kept for lack of proof): it leaves the queue. */
    fun done(id: String) = synchronized(lock) { write(read().filter { it.id != id }) }

    fun size(): Int = all().size

    private fun read(): List<Request> {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        return lines.mapNotNull { l ->
            runCatching {
                val m = JsonLite.obj(l)
                Request(m.str("id") ?: return@runCatching null, m.str("uri") ?: return@runCatching null, m.str("name") ?: "", m.long("size") ?: 0L)
            }.getOrNull()
        }
    }

    private fun write(list: List<Request>) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.absolutePath + ".tmp")
        FileOutputStream(tmp).use { o ->
            o.write(list.joinToString("") { JsonLite.write(mapOf("id" to it.id, "uri" to it.uri, "name" to it.name, "size" to it.size)) + "\n" }.toByteArray())
            o.fd.sync()
        }
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "cannot write the move requests" } }
    }
}
