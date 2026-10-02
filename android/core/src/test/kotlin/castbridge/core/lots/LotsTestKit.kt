package castbridge.core.lots

import castbridge.core.tv.ApiReply
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.random.Random

/** Fakes shared by the lot tests: a server key, a fake server, fake features, an in-process TV link. */
object Kit {
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val pub: String = Base64.getEncoder().encodeToString(pair.public.encoded.copyOfRange(12, 44))
    val otherPub: String = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.copyOfRange(12, 44))

    fun sign(lots: List<LotMeta>, channel: String = "stable", feature: String? = null, at: String = "2026-10-01T08:00:00Z"): LotManifest {
        val unsigned = LotManifest(channel, feature, at, lots, null, "")
        val sig = Signature.getInstance("Ed25519").run { initSign(pair.private); update(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8)); sign() }
        return unsigned.copy(signature = Base64.getEncoder().encodeToString(sig))
    }

    fun bytes(seed: Int, size: Int) = Random(seed).nextBytes(size)
    fun meta(feature: String, scope: String, version: Int, data: ByteArray, title: String = "$feature $scope", minApp: Int = 0) =
        LotMeta(LotId(feature, scope), version, data.size.toLong(), LotHash.sha256Hex(data), title, minApp)

    fun tmp(): File = kotlin.io.path.createTempDirectory("lots").toFile()
}

/** A published lot: its meta and bytes. */
class Published(val meta: LotMeta, val data: ByteArray)

class FakeRemote(var published: List<Published> = emptyList(), var signWith: (List<LotMeta>) -> LotManifest = { Kit.sign(it) }) : LotRemote {
    var down = false
    var catalogCalls = 0
    val downloads = ArrayList<String>()
    /** Cuts the next download after this many bytes (of the data it was about to send), once. */
    var cutAfter: Int? = null
    var gone = false
    /** Thrown by the next catalog / download request, whatever it is (a TLS failure for instance). */
    var failWith: IOException? = null
    /** Runs when a download starts (a phone pushing a lot in the meantime, for instance). */
    var onOpen: (() -> Unit)? = null

    override fun catalogJson(channel: String): String {
        catalogCalls++
        failWith?.let { throw it }
        if (down) throw IOException("hors ligne")
        return signWith(published.map { it.meta }).toJson()
    }

    override fun open(m: LotMeta, offset: Long): LotRemote.Stream {
        onOpen?.invoke()
        failWith?.let { throw it }
        if (down) throw IOException("hors ligne")
        if (gone) throw LotRemote.Gone("retiré")
        val p = published.first { it.meta.id == m.id && it.meta.version == m.version }
        downloads += "${m.id.feature}:${m.id.scope}@${offset}"
        val from = offset.toInt()
        val cut = cutAfter
        val body = p.data.copyOfRange(from, p.data.size)
        cutAfter = null
        val input: InputStream = if (cut != null && cut < body.size) object : InputStream() {
            var i = 0
            override fun read(): Int { if (i >= cut) throw IOException("lien coupé"); return body[i++].toInt() and 0xff }
        } else ByteArrayInputStream(body)
        return LotRemote.Stream(input, offset)
    }
}

class FakeConsumer(override val feature: String) : LotConsumer {
    val held = LinkedHashMap<LotId, LotMeta>()
    var failInstall = false
    val installs = ArrayList<LotMeta>()
    override fun install(meta: LotMeta, data: File): Boolean {
        if (failInstall) return false
        if (!data.isFile) return false
        held[meta.id] = meta; installs += meta; return true
    }
    override fun remove(id: LotId) { held.remove(id) }
    override fun installed() = held.values.toList()
}

/** A TV in a temp folder with fake features learn + quiz. */
class FakeTv(val starter: Long = 0, val max: Long = LotBudget.TV_MAX_BYTES, now: () -> Long = System::currentTimeMillis, app: Int = 10, keys: List<String> = listOf(Kit.pub)) {
    val dir = Kit.tmp()
    val learn = FakeConsumer("learn")
    val quiz = FakeConsumer("quiz")
    val store = TvLotStore(dir, mapOf("learn" to learn, "quiz" to quiz), keys, app, { starter }, max, now)
    val api = TvLotApi(store)
    fun reopen(now: () -> Long = System::currentTimeMillis) = TvLotStore(dir, mapOf("learn" to learn, "quiz" to quiz), listOf(Kit.pub), 10, { starter }, max, now)
}

/** The phone's view of a TV through its HTTP API handler, in-process; can drop the link after N bytes and go out of range. */
class DirectTransport(private val tv: FakeTv, private val chunk: Int = 64 * 1024, var reachable: Boolean = true, override val label: String = "test") : LotTransport {
    override val canReadManifest = true
    var dropAfterBytes: Long? = null            // total bytes this transport may still push before the link drops
    var sentBytes = 0L
    val sends = ArrayList<String>()

    private fun reply(r: ApiReply?) = r ?: ApiReply(404, "{}")
    override fun manifest(): TvManifest? = if (!reachable) null else TvManifest.parse(tv.api.handle("/api/lots", "GET", emptyMap())!!.json)
    override fun setPriority(ids: List<LotId>) = reachable && tv.api.handle("/api/lots/priority", "POST", mapOf("ids" to ids.joinToString(",") { LotNames.key(it) }))!!.status == 200
    override fun remove(id: LotId) = reachable && tv.api.handle("/api/lots/remove", "POST", mapOf("id" to LotNames.key(id)))!!.status == 200

    override fun send(meta: LotMeta, file: File, proofJson: String, onConfirmed: (Long) -> Unit, cancelled: () -> Boolean): SendResult {
        sends += "${meta.id.feature}:${meta.id.scope}@${meta.version}"
        if (!reachable) return SendResult.LinkDown(0, "TV hors de portée")
        val name = LotNames.fileName(meta)
        var off = castbridge.core.net.JsonLite.obj(tv.api.handle("/api/lots/part", "GET", mapOf("name" to name))!!.json)["received"].let { (it as Number).toLong() }
        val data = file.readBytes()
        while (off < meta.bytes) {
            val n = minOf(chunk.toLong(), meta.bytes - off).toInt()
            val limit = dropAfterBytes
            if (limit != null && sentBytes + n > limit) {
                // the link drops in the middle of this chunk: nothing of it counts (chunks are atomic), so send the part that fits as a shorter chunk
                val fit = (limit - sentBytes).toInt()
                if (fit > 0) { tv.api.handleBody("/api/lots/upload", "POST", mapOf("name" to name, "offset" to "$off", "total" to "${meta.bytes}"), data.copyOfRange(off.toInt(), off.toInt() + fit)); sentBytes += fit; off += fit }
                dropAfterBytes = null
                return SendResult.LinkDown(off, "lien coupé")
            }
            val r = reply(tv.api.handleBody("/api/lots/upload", "POST", mapOf("name" to name, "offset" to "$off", "total" to "${meta.bytes}"), data.copyOfRange(off.toInt(), off.toInt() + n)))
            sentBytes += n
            when (r.status) { 200, 409 -> { off = (castbridge.core.net.JsonLite.obj(r.json)["received"] as Number).toLong(); onConfirmed(off) }; else -> return SendResult.Refused(r.json) }
        }
        val r = tv.api.handleBody("/api/lots/install", "POST", mapOf("name" to name), proofJson.toByteArray())!!
        return if (r.status == 200) SendResult.Installed else SendResult.Refused(castbridge.core.net.JsonLite.obj(r.json)["error"] as String)
    }
}
