package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.update.Ed25519
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/** Hashing helpers shared by the whole lot framework. */
object LotHash {
    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    fun sha256Hex(b: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(b))
    fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return hex(md.digest())
    }
}

/** Names of lots: ids are safe path segments, file names are the same on the server, the phone and the TV. */
object LotNames {
    private val SEGMENT = Regex("^[a-z0-9][a-z0-9-]{0,31}$")
    /** A feature is a single word (no hyphen), so that "castbridge-lot-learn-cm2-trial-v1.lot" parses without ambiguity: scopes may contain hyphens ("droit-l1", "cm2-trial"). */
    private val FEATURE = Regex("^[a-z0-9]{1,32}$")
    private val FILE = Regex("^castbridge-lot-([a-z0-9]{1,32})-([a-z0-9][a-z0-9-]{0,31})-v(\\d{1,9})\\.lot$")
    const val PROOF_SUFFIX = ".json"

    fun valid(id: LotId) = FEATURE.matches(id.feature) && SEGMENT.matches(id.scope)
    /** "learn:cm2" (stable key for maps, query strings and files). */
    fun key(id: LotId) = "${id.feature}:${id.scope}"
    fun parseKey(k: String): LotId? = k.split(':').takeIf { it.size == 2 }?.let { LotId(it[0], it[1]) }?.takeIf(::valid)
    /** The lot file as it travels (over Wi-Fi or Bluetooth): "castbridge-lot-learn-cm2-v3.lot"; its signed proof is that name + ".json". */
    fun fileName(id: LotId, version: Int) = "castbridge-lot-${id.feature}-${id.scope}-v$version.lot"
    fun fileName(m: LotMeta) = fileName(m.id, m.version)
    fun parseFileName(name: String): Pair<LotId, Int>? = FILE.matchEntire(name)?.let { m ->
        val v = m.groupValues[3].toIntOrNull() ?: return null
        LotId(m.groupValues[1], m.groupValues[2]) to v
    }
}

fun LotMeta.toMap(): Map<String, Any?> = linkedMapOf<String, Any?>("feature" to id.feature, "scope" to id.scope, "version" to version, "bytes" to bytes,
    "sha256" to sha256, "title" to title, "minAppVersion" to minAppVersion).also { if (edition == Edition.TRIAL) it["edition"] = "trial" }

fun parseLotMeta(m: Map<String, Any?>): LotMeta? = runCatching {
    val id = LotId(m.str("feature") ?: error("feature"), m.str("scope") ?: error("scope"))
    LotMeta(id, m.int("version") ?: error("version"), m.long("bytes") ?: error("bytes"), (m.str("sha256") ?: error("sha256")).lowercase(),
        m.str("title") ?: "", m.int("minAppVersion") ?: 0,
        when (m.str("edition")) { null, "full" -> Edition.FULL; "trial" -> Edition.TRIAL; else -> error("edition") })
}.getOrNull()?.takeIf { LotNames.valid(it.id) && it.version > 0 && it.bytes >= 0 && it.sha256.length == 64 && LotEditions.consistent(it) }

/**
 * The signed catalog of the server (GET /api/v1/lots/catalog?feature=&channel=, backend LotManifest.java).
 *
 * Choice: ONE signed catalog PER ANSWER (the whole list of lots published on [channel], optionally filtered to one [feature]),
 * not one file per feature: a single request gives the phone the full picture (one signature to check, one ETag), and the
 * signature binds the filter, so a "learn" catalog cannot be replayed as a "quiz" one. Same Ed25519 key as the app updates
 * ([castbridge.core.update.UpdateKeys]) and same style as [castbridge.core.update.UpdateManifest]: the signature covers a
 * line-based [canonicalPayload] rebuilt here from the parsed fields, so changing any size, hash or version breaks it.
 *
 * The phone keeps the catalog that vouched for each installed lot (its "proof") and sends it to the TV with the lot: the
 * TV, which never talks to the server, verifies the signature itself and refuses a lot the catalog does not vouch for.
 */
data class LotManifest(
    val channel: String,
    /** null = every feature. */
    val feature: String?,
    val generatedAt: String,
    val lots: List<LotMeta>,
    val keyId: String?,
    val signature: String,
) {
    fun canonicalPayload(): String = buildList {
        add(FORMAT)
        add("channel=$channel")
        add("feature=${feature ?: "*"}")
        add("generatedAt=$generatedAt")
        lots.sortedWith(compareBy({ it.id.feature }, { it.id.scope }, { it.version })).forEach {
            add("lot=${it.id.feature}|${it.id.scope}|${it.version}|${it.bytes}|${it.sha256}|${it.minAppVersion}|${LotHash.sha256Hex(it.title.toByteArray(Charsets.UTF_8))}" +
                // FULL lines are unchanged (old signatures stay valid); a TRIAL lot is bound by its signature so nobody can pass one off as FULL
                if (it.edition == Edition.TRIAL) "|trial" else "")
        }
    }.joinToString("\n")

    fun signatureValid(publicKeyBase64: String): Boolean = try {
        Ed25519.verify(Base64.getDecoder().decode(publicKeyBase64.trim()), canonicalPayload().toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(signature))
    } catch (e: IllegalArgumentException) { false }

    fun signedByAny(keys: List<String>) = keys.any { signatureValid(it) }

    /** Does this catalog announce exactly [m] (same version, size, hash, title)? */
    fun vouches(m: LotMeta) = lots.any { it == m }
    fun find(id: LotId): LotMeta? = lots.filter { it.id == id }.maxByOrNull { it.version }

    fun toJson(): String = JsonLite.write(linkedMapOf("channel" to channel, "feature" to feature, "generatedAt" to generatedAt, "keyId" to keyId,
        "signature" to signature, "lots" to lots.map { it.toMap() }))

    companion object {
        const val FORMAT = "castbridge-lot-catalog-v1"

        /** Throws [JsonLite.ParseError] if the text is not a catalog; an entry that is invalid makes the whole catalog invalid. */
        fun parse(json: String): LotManifest {
            val m = JsonLite.obj(json)
            val arr = m["lots"] as? List<*> ?: throw JsonLite.ParseError("missing \"lots\"")
            val lots = arr.map { e ->
                @Suppress("UNCHECKED_CAST") parseLotMeta(e as? Map<String, Any?> ?: throw JsonLite.ParseError("bad lot")) ?: throw JsonLite.ParseError("bad lot entry")
            }
            return LotManifest(m.str("channel") ?: throw JsonLite.ParseError("missing \"channel\""), m.str("feature"),
                m.str("generatedAt") ?: throw JsonLite.ParseError("missing \"generatedAt\""), lots, m.str("keyId"),
                m.str("signature") ?: throw JsonLite.ParseError("missing \"signature\""))
        }
    }
}
