package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.SafeFile
import java.io.File
import java.io.IOException

const val LOT_SCHEMA = 1

/** A lot refused by the TV (kept in its manifest so the phone can tell the user why). */
data class LotRejection(val id: LotId, val version: Int, val reason: String, val at: Long)

/** The "TV manifest": what the TV tells the phone at every contact (GET /api/lots), so the phone computes the exact difference. */
data class TvManifest(
    val schema: Int,
    val maxBytes: Long,
    val usedBytes: Long,
    /** Bundled starter data (in the APK), counted in [usedBytes]. */
    val starterBytes: Long,
    val lots: List<TvLot>,
    val priority: List<LotId>,
    val rejected: List<LotRejection>,
) {
    val remainingBytes get() = maxBytes - usedBytes
    fun holds(m: LotMeta) = lots.any { it.meta.id == m.id && (it.meta.version > m.version || it.meta.version == m.version && it.meta.sha256 == m.sha256) }

    fun toJson(): String = JsonLite.write(linkedMapOf("schema" to schema, "maxBytes" to maxBytes, "usedBytes" to usedBytes, "starterBytes" to starterBytes,
        "lots" to lots.map { it.meta.toMap() + mapOf("installedAt" to it.installedAt) }, "priority" to priority.map(LotNames::key),
        "rejected" to rejected.map { linkedMapOf("feature" to it.id.feature, "scope" to it.id.scope, "version" to it.version, "reason" to it.reason, "at" to it.at) }))

    companion object {
        fun parse(json: String): TvManifest? = runCatching {
            val m = JsonLite.obj(json)
            fun list(k: String) = (m[k] as? List<*>) ?: emptyList<Any?>()
            @Suppress("UNCHECKED_CAST") fun maps(k: String) = list(k).mapNotNull { it as? Map<String, Any?> }
            TvManifest(
                schema = m.long("schema")?.toInt() ?: return null, maxBytes = m.long("maxBytes") ?: return null,
                usedBytes = m.long("usedBytes") ?: return null, starterBytes = m.long("starterBytes") ?: 0L,
                lots = maps("lots").mapNotNull { e -> parseLotMeta(e)?.let { TvLot(it, e.long("installedAt") ?: 0L) } },
                priority = list("priority").mapNotNull { (it as? String)?.let(LotNames::parseKey) },
                rejected = maps("rejected").mapNotNull { e -> LotNames.parseKey("${e.str("feature")}:${e.str("scope")}")?.let { LotRejection(it, e.long("version")?.toInt() ?: 0, e.str("reason") ?: "", e.long("at") ?: 0L) } },
            )
        }.getOrNull()
    }
}

data class TvLot(val meta: LotMeta, val installedAt: Long)

/**
 * The lots held by the TV (CastBridge-TV), under a STRICT cap of [maxBytes] ([LotBudget.TV_MAX_BYTES]) for Apprendre + Quiz together,
 * the starter data bundled in the APK ([starterBytes]) included. The cap is enforced at install time and at startup; an install
 * that cannot fit is refused with a reason in French, never accepted over the cap.
 *
 * The TV NEVER downloads lots from the Internet: lots only arrive pushed by the phone (docs/LOTS.md). Every lot is checked here
 * (signed catalog that vouches for it, size, SHA-256, minAppVersion, no downgrade) before the matching [LotConsumer] installs it;
 * the phone is not trusted. Eviction frees room only among the lots the phone did not flag as priority, oldest first.
 *
 * The consumer's [LotConsumer.install] must be atomic (on failure the previous version stays usable): the TV then keeps it.
 */
class TvLotStore(
    private val dir: File,
    private val consumers: Map<String, LotConsumer>,
    private val publicKeys: List<String>,
    private val appVersion: Int,
    /** Bytes of the Learn + Quiz data bundled in the APK. */
    private val starterBytes: () -> Long = { 0L },
    val maxBytes: Long = LotBudget.TV_MAX_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
    /** Called (French text) when the index was unreadable and the `.bak` copy was used instead. */
    private val onWarning: (String) -> Unit = {},
) {
    sealed class Result {
        data class Ok(val evicted: List<LotId>) : Result()
        data class Refused(val reason: String) : Result()
    }

    sealed class Chunk {
        data class Received(val bytes: Long) : Chunk()
        /** The offset sent is not where the TV stands: resume from [bytes]. */
        data class Conflict(val bytes: Long) : Chunk()
        data class Refused(val reason: String) : Chunk()
    }

    private val lock = Any()
    private val installedAt = HashMap<LotId, Long>()
    private var priority: List<LotId> = emptyList()
    private val rejected = ArrayList<LotRejection>()
    private val inbox = File(dir, ".in")

    init { synchronized(lock) { load() } }

    private val indexFile get() = File(dir, "lots.json")
    fun partFile(name: String) = File(inbox, "$name.part")

    private fun held(): List<LotMeta> = consumers.values.flatMap { c -> runCatching { c.installed() }.getOrDefault(emptyList()) }
    fun usedBytes(): Long = starterBytes() + held().sumOf { it.bytes }
    fun remainingBytes(): Long = maxBytes - usedBytes()

    fun manifest(): TvManifest = synchronized(lock) {
        val h = held()
        TvManifest(LOT_SCHEMA, maxBytes, starterBytes() + h.sumOf { it.bytes }, starterBytes(), h.map { TvLot(it, installedAt[it.id] ?: 0L) }
            .sortedWith(compareBy({ it.meta.id.feature }, { it.meta.id.scope })), priority, rejected.toList())
    }

    /** The phone says which lots matter (its plan): they are never evicted to make room for others. */
    fun setPriority(ids: List<LotId>) = synchronized(lock) { priority = ids.filter(LotNames::valid).distinct().take(200); runCatching { save() }; Unit }

    /** Startup: back under the cap if the starter data grew (APK update) or something drifted; forgets orphans and stale parts. */
    fun startup(): List<LotId> = synchronized(lock) {
        val h = held().map { it.id }.toSet()
        installedAt.keys.retainAll(h)
        inbox.listFiles()?.filter { now() - it.lastModified() > 7L * 24 * 3600 * 1000 }?.forEach { it.delete() }
        val evicted = ArrayList<LotId>()
        while (usedBytes() > maxBytes) {
            val victim = evictionOrder(except = null).firstOrNull() ?: break
            consumers[victim.feature]?.remove(victim); installedAt.remove(victim); evicted += victim
        }
        runCatching { save() }
        evicted
    }

    /** True if the TV is over its cap and nothing can be evicted (the starter data alone is too big): the settings screen says so. */
    fun overBudget(): Boolean = usedBytes() > maxBytes

    private fun evictionOrder(except: LotId?): List<LotId> =
        held().map { it.id }.filter { it != except && it !in priority }.sortedWith(compareBy({ installedAt[it] ?: 0L }, { LotNames.key(it) }))

    // ---- resumable reception (HTTP chunks; Bluetooth files land in the same inbox) ----

    fun received(name: String): Long = partFile(name).takeIf { it.isFile }?.length() ?: 0L

    /** Appends [bytes] at [offset] of the lot file [name] (the one [LotNames.fileName] builds). */
    fun receive(name: String, offset: Long, total: Long, bytes: ByteArray): Chunk = synchronized(lock) {
        val (id, _) = LotNames.parseFileName(name) ?: return Chunk.Refused("nom de lot invalide")
        if (!LotNames.valid(id)) return Chunk.Refused("nom de lot invalide")
        if (total <= 0) return Chunk.Refused("taille invalide")
        if (total > maxBytes) return Chunk.Refused("lot trop gros pour la TV (${LotStore.mo(total)} > ${LotStore.mo(maxBytes)})")
            .also { rejected.removeAll { r -> r.id == id }; rejected += LotRejection(id, LotNames.parseFileName(name)!!.second, it.reason, now()); runCatching { save() } }
        val part = partFile(name)
        val cur = if (part.isFile) part.length() else 0L
        if (offset != cur || cur + bytes.size > total) return Chunk.Conflict(cur)
        if (cur == 0L) clearRejections(id)                                // a new attempt: the old refusal is history
        inbox.mkdirs()
        if (inbox.usableSpace < total - cur + (1L shl 20)) return Chunk.Refused("espace disque insuffisant sur la TV")
        try { java.io.FileOutputStream(part, true).use { it.write(bytes) } }
        catch (e: IOException) { return Chunk.Refused("écriture impossible sur la TV") }
        Chunk.Received(cur + bytes.size)
    }

    /** Verifies and installs the fully received [name]; [proofJson] = the signed catalog that vouches for it. */
    fun installReceived(name: String, proofJson: String): Result = synchronized(lock) {
        val (id, version) = LotNames.parseFileName(name) ?: return Result.Refused("nom de lot invalide")
        val part = partFile(name)
        if (!part.isFile) return Result.Refused("lot non reçu")
        val cat = try { LotManifest.parse(proofJson) } catch (e: IllegalArgumentException) { return reject(id, version, part, "catalogue illisible") }
        if (!cat.signedByAny(publicKeys)) return reject(id, version, part, "catalogue non signé par le serveur CastBridge : lot refusé")
        val meta = cat.lots.firstOrNull { it.id == id && it.version == version } ?: return reject(id, version, part, "le catalogue signé ne contient pas ce lot")
        install(meta, part)
    }

    /** Verifies and installs [file] (consumed) as [meta]. [meta] must already be vouched for by a verified signed catalog. */
    private fun install(meta: LotMeta, file: File): Result {
        val id = meta.id
        fun no(why: String): Result = reject(id, meta.version, file, why)
        val consumer = consumers[id.feature] ?: return no("cette TV ne gère pas les données « ${id.feature} »")
        if (meta.minAppVersion > appVersion) return no("« ${meta.title} » demande une version plus récente de CastBridge-TV (mise à jour par clé USB)")
        if (file.length() != meta.bytes) return no("taille ${file.length()} au lieu de ${meta.bytes} octets")
        if (!LotHash.sha256Hex(file).equals(meta.sha256, ignoreCase = true)) return no("empreinte SHA-256 différente : fichier corrompu")
        val have = held().firstOrNull { it.id == id }
        if (have != null && have.version > meta.version) { file.delete(); return Result.Refused("version plus récente déjà installée (${have.version})") }
        if (have != null && have.version == meta.version && have.sha256 == meta.sha256) { file.delete(); clearRejections(id); return Result.Ok(emptyList()) }
        // editions: a full lot replaces its trial twin (never both on the TV); a trial lot is refused once the full one is there
        if (meta.edition == Edition.TRIAL && held().any { it.id == LotEditions.fullOf(id) }) { file.delete(); return Result.Refused("la version complète est déjà installée") }
        val twin = if (meta.edition == Edition.FULL) held().firstOrNull { it.id == LotEditions.trialOf(id) } else null
        // the budget: strict, evicting only non-priority lots, and only if that is enough
        val free = maxBytes - usedBytes()
        val net = meta.bytes - (have?.bytes ?: 0L) - (twin?.bytes ?: 0L)
        val evict = ArrayList<LotId>()
        if (net > free) {
            var f = free
            for (v in evictionOrder(except = id).filter { it != twin?.id }) { evict += v; f += held().first { it.id == v }.bytes; if (net <= f) break }
            if (net > f) return no("Pas assez de place sur la TV : « ${meta.title} » (${LotStore.mo(meta.bytes)}) dépasse les ${LotStore.mo(maxBytes)} prévus " +
                "(il manque ${LotStore.mo(net - f)} ; les données prioritaires sont conservées)")
        }
        val ok = runCatching { consumer.install(meta, file) }.getOrDefault(false)
        if (!ok) return no("installation refusée par « ${id.feature} » : la version précédente reste en place")
        evict.forEach { consumers[it.feature]?.remove(it); installedAt.remove(it) }
        if (twin != null) { consumers[twin.id.feature]?.remove(twin.id); installedAt.remove(twin.id) }
        // hard guarantee: whatever the consumer reported, never stay over the cap
        if (usedBytes() > maxBytes) {
            consumer.remove(id)
            return Result.Refused("Pas assez de place sur la TV : « ${meta.title} » retiré pour respecter les ${LotStore.mo(maxBytes)}")
                .also { rejected += LotRejection(id, meta.version, "dépasse la place disponible", now()); runCatching { save() } }
        }
        installedAt[id] = now()
        clearRejections(id)
        file.delete()
        runCatching { save() }
        return Result.Ok(evict)
    }

    fun remove(id: LotId) = synchronized(lock) { consumers[id.feature]?.remove(id); installedAt.remove(id); runCatching { save() }; Unit }

    private fun reject(id: LotId, version: Int, file: File, why: String): Result.Refused {
        file.delete()
        rejected.removeAll { it.id == id }
        rejected += LotRejection(id, version, why, now())
        while (rejected.size > 20) rejected.removeAt(0)
        runCatching { save() }
        return Result.Refused(why)
    }

    private fun clearRejections(id: LotId) { rejected.removeAll { it.id == id } }

    /**
     * Bluetooth deliveries (CBT1) land as plain files in [receivedDir] (the TV's reception folder): the lot and, last, its signed
     * proof ("<lot file>.json"). This adopts the complete pairs (installs them, deletes both files). Call after each Bluetooth
     * transfer and at startup.
     */
    fun adoptFrom(receivedDir: File): List<String> = synchronized(lock) {
        val done = ArrayList<String>()
        receivedDir.listFiles()?.filter { it.isFile && it.name.endsWith(LotNames.PROOF_SUFFIX) && LotNames.parseFileName(it.name.removeSuffix(LotNames.PROOF_SUFFIX)) != null }?.forEach { proof ->
            val name = proof.name.removeSuffix(LotNames.PROOF_SUFFIX)
            val lot = File(receivedDir, name)
            if (!lot.isFile) return@forEach
            inbox.mkdirs()
            clearRejections(LotNames.parseFileName(name)!!.first)
            val part = partFile(name)
            part.delete()
            if (!lot.renameTo(part)) { lot.copyTo(part, overwrite = true); lot.delete() }
            installReceived(name, proof.readText())
            proof.delete()
            done += name
        }
        done
    }

    private fun load() {
        val r = SafeFile.read(indexFile, ::validIndex) ?: return
        if (r.fromBackup) onWarning("Index des lots illisible : copie de secours (lots.json.bak) relue.")
        val m = runCatching { JsonLite.obj(r.text) }.getOrNull() ?: return
        (m["installedAt"] as? Map<*, *>)?.forEach { (k, v) -> LotNames.parseKey(k.toString())?.let { id -> (v as? Number)?.let { installedAt[id] = it.toLong() } } }
        priority = (m["priority"] as? List<*>)?.mapNotNull { (it as? String)?.let(LotNames::parseKey) } ?: emptyList()
        (m["rejected"] as? List<*>)?.forEach { e ->
            @Suppress("UNCHECKED_CAST") val r = e as? Map<String, Any?> ?: return@forEach
            LotNames.parseKey("${r.str("feature")}:${r.str("scope")}")?.let { rejected += LotRejection(it, r.long("version")?.toInt() ?: 0, r.str("reason") ?: "", r.long("at") ?: 0L) }
        }
    }

    private fun save() {
        dir.mkdirs()
        val json = JsonLite.write(linkedMapOf("installedAt" to installedAt.mapKeys { LotNames.key(it.key) }, "priority" to priority.map(LotNames::key),
            "rejected" to rejected.map { linkedMapOf("feature" to it.id.feature, "scope" to it.id.scope, "version" to it.version, "reason" to it.reason, "at" to it.at) }))
        try { SafeFile.write(indexFile, json, ::validIndex) } catch (_: IOException) { /* kept in memory; the next change retries */ }
    }

    private fun validIndex(text: String) = runCatching { JsonLite.obj(text) }.isSuccess
}
