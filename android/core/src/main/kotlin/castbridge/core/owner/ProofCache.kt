package castbridge.core.owner

import castbridge.core.net.JsonLite
import castbridge.core.crypto.SecretWrapper
import java.io.File
import java.util.Base64

/** Where [ProofCache] keeps its text (one blob). */
interface ProofCacheStore {
    fun load(): String?
    /** Persists [text]; throws when the write fails. */
    fun save(text: String)
}

/** [ProofCacheStore] in one file, written atomically with [SafeFile] (the receiver passes `files/proof/cache.json`). */
class FileProofCacheStore(private val file: File) : ProofCacheStore {
    private fun valid(text: String) = runCatching { JsonLite.obj(text) }.isSuccess
    override fun load(): String? = SafeFile.read(file, ::valid)?.text
    override fun save(text: String) = SafeFile.write(file, text, ::valid)
}

/**
 * What a phone remembers of its TVs: per TV code the PINNED installation key (TOFU), the last accepted [Proof] and the highest `seq` seen. A proof is valid for [validityMs] (14 days)
 * from its `verifiedAt`, never beyond the activation's `endsAt`, and never again once [ProofSummary.validUntil] has passed. Time comes from [clock] (the phone's [TvClock]: high-water mark +
 * monotonic time): a wall clock wound back lengthens nothing, and `validUntil` of a TV never moves back. The caller persists [clock] itself and passes the wall time to each call.
 * The activation token (also the server access, `X-CB-TV-Proof`) is stored sealed by [wrapper] (`activationSealed`); a cache written in clear by an earlier build is still read, and sealed at the next write.
 * Re-pinning a TV under ANOTHER key (reinstalled TV, whose `seq` restarts) clears its `seq` and its proof. The newest activation seen per TV ([activationMark]) survives, it belongs to the TV, not to the key.
 * A failed [ProofCacheStore.save] throws after the memory was updated (the caller logs and retries). Not thread-safe by itself: methods are synchronized.
 */
class ProofCache(private val store: ProofCacheStore, private val clock: TvClock, private val wrapper: SecretWrapper, val validityMs: Long = DEFAULT_VALIDITY_MS) {
    companion object {
        const val DEFAULT_VALIDITY_MS = 14L * 24 * 3600 * 1000
        const val FORMAT = "castbridge-proof-cache-v1"
    }

    private class Entry(var pin: TrustedKey? = null, var proof: Proof? = null, var validUntil: Long = 0L, var lastSeq: Long = 0L, var mark: ActivationMark? = null)

    private val tvs = LinkedHashMap<String, Entry>()

    init {
        runCatching {
            val root = JsonLite.obj(store.load() ?: return@runCatching)
            require(root["format"] == FORMAT)
            (root["tvs"] as Map<*, *>).forEach { (code, v) ->
                @Suppress("UNCHECKED_CAST") val m = v as Map<String, Any?>
                val e = Entry(lastSeq = (m["lastSeq"] as Number).toLong(), validUntil = (m["validUntil"] as? Number)?.toLong() ?: 0L)
                (m["actKid"] as? String)?.let { k -> e.mark = ActivationMark(k, (m["actSeq"] as Number).toLong()) }
                (m["pin"] as? Map<*, *>)?.let { e.pin = TrustedKey(it["kid"] as String, it["pub"] as String) }
                (m["proof"] as? Map<*, *>)?.let {
                    val token = (it["activationSealed"] as? String)?.let { b -> wrapper.unwrap(Base64.getDecoder().decode(b))?.toString(Charsets.UTF_8) } ?: it["activation"] as? String   // clear = old format
                    if (token == null) { e.validUntil = 0L; return@let }                                                       // sealed under a lost wrapper key: the proof is gone, the pin stays
                    e.proof = Proof(it["tvCode"] as String, it["tvName"] as String, it["installKeyId"] as String, (it["endsAt"] as? Number)?.toLong(), it["super"] == true,
                        (it["verifiedAt"] as Number).toLong(), (it["seq"] as Number).toLong(), token)
                }
                tvs[code as String] = e
            }
        }.onFailure { tvs.clear() }
    }

    private fun persist() {
        val tvMap = tvs.mapValues { (_, e) ->
            linkedMapOf<String, Any?>("lastSeq" to e.lastSeq, "validUntil" to e.validUntil, "actKid" to e.mark?.keyId, "actSeq" to e.mark?.seq,
                "pin" to e.pin?.let { linkedMapOf("kid" to it.keyId, "pub" to it.publicKeyBase64) },
                "proof" to e.proof?.let { linkedMapOf("tvCode" to it.tvCode, "tvName" to it.tvName, "installKeyId" to it.installKeyId, "endsAt" to it.endsAt, "super" to it.superUnlimited,
                    "verifiedAt" to it.verifiedAt, "seq" to it.seq, "activationSealed" to Base64.getEncoder().encodeToString(wrapper.wrap(it.activationToken.toByteArray(Charsets.UTF_8)))) })
        }
        store.save(JsonLite.write(linkedMapOf("format" to FORMAT, "tvs" to tvMap)))
    }

    /** Pins [key] as the installation key of [tvCode] (after the user compared the fingerprints; replaces a previous pin only when the caller confirmed an identity change). */
    @Synchronized fun pin(tvCode: String, key: TrustedKey) {
        val e = tvs.getOrPut(tvCode) { Entry() }
        val old = e.pin
        if (old != null && (old.keyId != key.keyId || old.publicKeyBase64 != key.publicKeyBase64)) { e.lastSeq = 0L; e.proof = null; e.validUntil = 0L }   // a reinstalled TV restarts its seq at 0
        e.pin = TrustedKey(key.keyId, key.publicKeyBase64)
        persist()
    }

    @Synchronized fun pinned(tvCode: String): TrustedKey? = tvs[tvCode]?.pin

    /** Highest `seq` accepted for [tvCode] (feed it to [TvProof.verify] as `lastSeq`), or null. */
    @Synchronized fun lastSeq(tvCode: String): Long? = tvs[tvCode]?.takeIf { it.proof != null || it.lastSeq > 0 }?.lastSeq

    /**
     * Stores an accepted [proof]: `verifiedAt` becomes the cache clock reading at [wallNowMs] (monotone), `validUntil` = `verifiedAt + validityMs` and never moves back for this TV.
     * Returns false (nothing changes) when its `seq` is older than the one already stored.
     */
    @Synchronized fun put(proof: Proof, wallNowMs: Long): Boolean {
        val e = tvs.getOrPut(proof.tvCode) { Entry() }
        if (proof.seq < e.lastSeq) return false
        clock.observe(wallNowMs)
        val verifiedAt = clock.now(wallNowMs)
        e.proof = proof.copy(verifiedAt = verifiedAt)
        e.validUntil = maxOf(e.validUntil, verifiedAt + validityMs)
        e.lastSeq = proof.seq
        Activation.decode(proof.activationToken)?.let { a -> val m = e.mark; if (m == null || m.keyId != a.keyId || a.seq > m.seq) e.mark = ActivationMark(a.keyId, a.seq) }
        persist()
        return true
    }

    /** Newest activation seen for [tvCode] (feed it to [TvProof.verify] as `lastActivation`), or null. */
    @Synchronized fun activationMark(tvCode: String): ActivationMark? = tvs[tvCode]?.mark

    private fun summary(e: Entry): ProofSummary? = e.proof?.let { ProofSummary(it.tvCode, it.tvName, it.verifiedAt, e.validUntil, it.endsAt) }

    /** Proofs still valid now (one per TV): the phone is `Linked` when this is not empty. */
    @Synchronized fun validProofs(wallNowMs: Long): List<ProofSummary> {
        clock.observe(wallNowMs)
        val now = clock.now(wallNowMs)
        return tvs.values.mapNotNull(::summary).filter { it.validAt(now) }
    }

    /** The accepted proof of [tvCode] (valid or not), e.g. to reuse its activation token. */
    @Synchronized fun proofOf(tvCode: String): Proof? = tvs[tvCode]?.proof

    /** Removes the proof of [tvCode] but keeps its pin and `seq` (the TV reported a reduced mode or a lost key: the phone learns the end before the 14 days). */
    @Synchronized fun dropProof(tvCode: String) { tvs[tvCode]?.let { it.proof = null; it.validUntil = 0L; persist() } }

    /** Removes the proof when the activation's `endsAt` has passed at [wallNowMs]; true when it was removed. */
    @Synchronized fun dropIfEnded(tvCode: String, wallNowMs: Long): Boolean {
        val e = tvs[tvCode] ?: return false
        val ends = e.proof?.endsAt ?: return false
        clock.observe(wallNowMs)
        if (clock.now(wallNowMs) < ends) return false
        e.proof = null; e.validUntil = 0L; persist(); return true
    }

    /** The TV was removed from the app: its proof, its pin and its `seq` go. */
    @Synchronized fun forget(tvCode: String) { if (tvs.remove(tvCode) != null) persist() }
}
