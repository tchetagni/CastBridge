package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * The lots held by the PHONE (CastBridge): at most [maxBytes] ([LotBudget.PHONE_MAX_BYTES]) in total.
 *
 * Layout: `<dir>/<feature>/<scope>/v<version>.lot` (+ `.proof.json`, the signed catalog that vouched for it), `<dir>/index.json`,
 * `<dir>/.part/` for the downloads in progress (they resume). Only the NEWEST version of a lot is kept, once it is in place:
 * an install is atomic (verify, move, index, then delete the old version), so a failed update leaves the previous version
 * intact (rollback by construction). It is also the [LotSource] the TV deliveries read from, so they work with no Internet.
 *
 * Eviction: the lots [protect] says are the active profiles' classes are never evicted; the others go least-recently-used
 * first. If even that is not enough, nothing is evicted and [Install.Full] says (in French) how much is missing.
 */
class LotStore(
    private val dir: File,
    val maxBytes: Long = LotBudget.PHONE_MAX_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) : LotSource {
    data class Entry(val meta: LotMeta, val installedAt: Long, val lastUsed: Long)

    sealed class Fit {
        data class Yes(val evict: List<LotId>) : Fit()
        data class No(val reason: String, val missingBytes: Long) : Fit()
    }

    sealed class Install {
        data class Ok(val evicted: List<LotId>, val replacedVersion: Int?) : Install()
        /** Not installed, the file is not trustworthy / not valid (corrupted, wrong size…): [reason] in French. */
        data class Refused(val reason: String) : Install()
        /** The 100 Mo are full of protected lots. */
        data class Full(val reason: String, val missingBytes: Long) : Install()
    }

    private val lock = Any()
    private val entries = LinkedHashMap<LotId, Entry>()

    init { synchronized(lock) { load() } }

    private val indexFile get() = File(dir, "index.json")
    fun partFile(m: LotMeta) = File(File(dir, ".part"), LotNames.fileName(m))
    private fun lotFile(id: LotId, version: Int) = File(File(File(dir, id.feature), id.scope), "v$version.lot")
    private fun proofFile(id: LotId, version: Int) = File(lotFile(id, version).parentFile, "v$version.proof.json")

    fun list(): List<Entry> = synchronized(lock) { entries.values.sortedWith(compareBy({ it.meta.id.feature }, { it.meta.id.scope })) }
    fun get(id: LotId): Entry? = synchronized(lock) { entries[id] }
    fun usedBytes(): Long = synchronized(lock) { entries.values.sumOf { it.meta.bytes } }
    fun usedBytes(feature: String): Long = synchronized(lock) { entries.values.filter { it.meta.id.feature == feature }.sumOf { it.meta.bytes } }
    fun freeBytes(): Long = maxBytes - usedBytes()

    /** The lot file of [id] if exactly this [version] is held. */
    fun file(id: LotId, version: Int): File? = synchronized(lock) { entries[id]?.takeIf { it.meta.version == version }?.let { lotFile(id, version) }?.takeIf { it.isFile } }
    /** The signed catalog (JSON) that vouched for the held version of [id]: sent to the TV with the lot. */
    fun proof(id: LotId): String? = synchronized(lock) { entries[id]?.let { proofFile(id, it.meta.version) }?.takeIf { it.isFile }?.readText() }

    override fun catalog(): List<LotMeta> = list().map { it.meta }
    override fun open(id: LotId, version: Int): InputStream? = file(id, version)?.let { touch(id); FileInputStream(it) }

    fun touch(id: LotId) = synchronized(lock) { entries[id]?.let { entries[id] = it.copy(lastUsed = now()); runCatching { save() } }; Unit }

    /** Can [m] be installed, and which lots would be evicted for it? Used BEFORE downloading, so no bandwidth is wasted. */
    fun fit(m: LotMeta, protect: (LotId) -> Boolean = { false }): Fit = synchronized(lock) {
        val net = m.bytes - (entries[m.id]?.meta?.bytes ?: 0L)
        var free = maxBytes - usedBytes()
        if (m.bytes > maxBytes) return Fit.No("« ${m.title} » (${mo(m.bytes)}) dépasse à lui seul la place prévue (${mo(maxBytes)})", m.bytes - maxBytes)
        if (net <= free) return Fit.Yes(emptyList())
        val evict = ArrayList<LotId>()
        for (e in entries.values.filter { it.meta.id != m.id && !protect(it.meta.id) }.sortedWith(compareBy({ it.lastUsed }, { LotNames.key(it.meta.id) }))) {
            evict += e.meta.id; free += e.meta.bytes
            if (net <= free) return Fit.Yes(evict)
        }
        Fit.No("Stockage plein : il manque ${mo(net - free)} pour « ${m.title} » (les données des profils actifs sont conservées)", net - free)
    }

    /**
     * Installs the verified [file] of [m]. On success [file] is consumed (moved). On a refusal it is deleted if it is corrupted
     * and left alone otherwise. [proof] = the signed catalog JSON that vouches for [m].
     */
    fun install(m: LotMeta, file: File, proof: String? = null, protect: (LotId) -> Boolean = { false }): Install = synchronized(lock) {
        if (!LotNames.valid(m.id)) return Install.Refused("identifiant de lot invalide")
        if (!file.isFile) return Install.Refused("fichier absent")
        if (file.length() != m.bytes) { file.delete(); return Install.Refused("taille ${file.length()} au lieu de ${m.bytes} octets : téléchargement incomplet") }
        if (!LotHash.sha256Hex(file).equals(m.sha256, ignoreCase = true)) { file.delete(); return Install.Refused("empreinte SHA-256 différente : fichier corrompu ou modifié") }
        val old = entries[m.id]
        if (old != null && old.meta.version > m.version) return Install.Refused("une version plus récente (${old.meta.version}) est déjà installée")
        val evict = when (val f = fit(m, protect)) { is Fit.Yes -> f.evict; is Fit.No -> return Install.Full(f.reason, f.missingBytes) }
        val target = lotFile(m.id, m.version)
        try {
            target.parentFile.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.delete()
            if (!file.renameTo(tmp)) { file.copyTo(tmp, overwrite = true); file.delete() }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("renommage impossible")
            proof?.let { proofFile(m.id, m.version).writeText(it) }
        } catch (e: IOException) {
            target.delete()
            return Install.Refused("écriture impossible : ${e.message ?: e.javaClass.simpleName}")
        }
        val before = LinkedHashMap(entries)
        evict.forEach { dropFiles(it); entries.remove(it) }
        entries[m.id] = Entry(m, installedAt = now(), lastUsed = now())
        try { save() } catch (e: IOException) {
            // roll back: the index on disk is the truth, and the previous version is still there
            entries.clear(); entries.putAll(before); target.delete(); proofFile(m.id, m.version).delete()
            return Install.Refused("écriture impossible : ${e.message ?: e.javaClass.simpleName}")
        }
        if (old != null && old.meta.version != m.version) { lotFile(m.id, old.meta.version).delete(); proofFile(m.id, old.meta.version).delete() }
        Install.Ok(evict, old?.meta?.version)
    }

    fun remove(id: LotId) = synchronized(lock) {
        if (entries.remove(id) != null) { dropFiles(id); runCatching { save() } }
        Unit
    }

    /** Deletes stale partial downloads (older than [olderThanMs]). */
    fun purgeParts(olderThanMs: Long = 7L * 24 * 3600 * 1000) {
        File(dir, ".part").listFiles()?.filter { now() - it.lastModified() > olderThanMs }?.forEach { it.delete() }
    }

    private fun dropFiles(id: LotId) {
        File(File(dir, id.feature), id.scope).listFiles()?.forEach { it.delete() }
        File(File(dir, id.feature), id.scope).delete()
    }

    private fun load() {
        entries.clear()
        val text = runCatching { indexFile.readText() }.getOrNull() ?: return
        val arr = runCatching { JsonLite.obj(text)["lots"] as? List<*> }.getOrNull() ?: return
        for (e in arr) {
            @Suppress("UNCHECKED_CAST") val m = e as? Map<String, Any?> ?: continue
            val meta = parseLotMeta(m) ?: continue
            if (!lotFile(meta.id, meta.version).isFile) continue          // a lot whose file vanished is no longer held
            entries[meta.id] = Entry(meta, m.long("installedAt") ?: 0L, m.long("lastUsed") ?: 0L)
        }
    }

    private fun save() {
        dir.mkdirs()
        val json = JsonLite.write(linkedMapOf("lots" to entries.values.map { e -> e.meta.toMap() + mapOf("installedAt" to e.installedAt, "lastUsed" to e.lastUsed) }))
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(indexFile)) { indexFile.delete(); if (!tmp.renameTo(indexFile)) throw IOException("index non écrit") }
    }

    companion object {
        fun mo(bytes: Long): String = if (bytes >= 1L shl 20) "%.1f Mo".format(bytes / 1048576.0).replace('.', ',') else "${(bytes + 1023) / 1024} Ko"
    }
}
