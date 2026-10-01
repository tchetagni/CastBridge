package castbridge.core.parental

import castbridge.core.net.JsonLite
import castbridge.core.trust.TrustRegistry
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * What the TV answers to a phone that comes for its reports (CBTP). [status] is [BtProtocol.OK] or an ERR_*.
 */
class SyncReply(val status: Int, val json: String = "")

/**
 * TV side of CBTP. The peer is the paired device of the RFCOMM socket (proven by Android), never something it writes.
 *
 * - A peer that is not a trusted phone gets ERR_UNTRUSTED and nothing else.
 * - A trusted phone learns its opaque id and whether it is a designated recipient (so that it can ask the parent to designate it).
 * - Only a designated recipient gets reports and, until it confirmed having stored it, the key that signs them.
 */
class ReportSyncHost(
    private val recipients: ReportRecipients,
    private val outbox: ReportOutbox,
    private val isTrusted: (String) -> Boolean,
    private val tvName: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun fetch(peer: String): SyncReply {
        if (!TrustRegistry.isAddress(peer) || !isTrusted(peer)) return SyncReply(BtProtocol.ERR_UNTRUSTED)
        val r = recipients.get(peer)
        val reply = linkedMapOf<String, Any?>("v" to 1, "tv" to tvName(), "now" to now(), "you" to linkedMapOf("id" to recipients.phoneId(peer), "designated" to (r != null)))
        if (r != null) {
            if (!r.keyDelivered) reply["key"] = r.key
            reply["reports"] = outbox.pending(peer).map { it.envelope() }
        }
        return SyncReply(BtProtocol.OK, JsonLite.write(reply))
    }

    /** The phone stored [ids] (and the key if [keyStored]): they are forgotten. A phone cannot acknowledge anybody else's reports. */
    fun ack(peer: String, ids: List<String>, keyStored: Boolean) {
        if (!TrustRegistry.isAddress(peer) || !isTrusted(peer) || recipients.get(peer) == null) return
        outbox.ack(peer, ids)
        if (keyStored) recipients.keyDelivered(peer)
    }
}

/**
 * The wire format of CBTP, over any byte stream (so it is tested with pipes):
 *
 *   phone -> TV : "CBTP" | u16 length | UTF-8 JSON request ({"v":1})
 *   TV -> phone : status byte; if OK: u32 length | UTF-8 JSON reply (see [ReportSyncHost.fetch])
 *   phone -> TV : u16 length | UTF-8 JSON {"ack":[ids], "key":true|false}   (what it stored)
 *   TV -> phone : status byte (OK)
 *
 * An older TV answers ERR_MAGIC to "CBTP" and an older phone never sends it: nothing else changes on the link. The acknowledgement
 * comes AFTER the phone stored the reports, so a link that breaks in between only causes a second delivery (the phone drops duplicates).
 */
object ParentalSyncProtocol {
    const val MAGIC = "CBTP"
    const val MAX_REPLY = 512 * 1024
    private const val MAX_REQUEST = 4096

    /** TV side, after the 4 magic bytes were read. */
    fun serve(din: DataInputStream, dout: DataOutputStream, peer: String, host: ReportSyncHost): Int {
        val reqLen = din.readUnsignedShort()
        if (reqLen > MAX_REQUEST) { dout.writeByte(BtProtocol.ERR_SIZE); dout.flush(); return BtProtocol.ERR_SIZE }
        din.readFully(ByteArray(reqLen))                       // the request only carries a version for now
        val reply = host.fetch(peer)
        if (reply.status != BtProtocol.OK) { dout.writeByte(reply.status); dout.flush(); return reply.status }
        val bytes = reply.json.toByteArray(Charsets.UTF_8)
        dout.writeByte(BtProtocol.OK); dout.writeInt(bytes.size); dout.write(bytes); dout.flush()
        val ackLen = din.readUnsignedShort()
        if (ackLen > MAX_REQUEST * 8) { dout.writeByte(BtProtocol.ERR_SIZE); dout.flush(); return BtProtocol.ERR_SIZE }
        val ack = runCatching { JsonLite.obj(String(ByteArray(ackLen).also { din.readFully(it) }, Charsets.UTF_8)) }.getOrNull()
        @Suppress("UNCHECKED_CAST") val ids = (ack?.get("ack") as? List<Any?>).orEmpty().mapNotNull { it as? String }.take(100)
        host.ack(peer, ids, ack?.get("key") == true)
        dout.writeByte(BtProtocol.OK); dout.flush()
        return BtProtocol.OK
    }

    /**
     * Phone side. [store] receives the TV's reply and returns what it stored (ids to acknowledge, whether the key was stored). Throws
     * [BtProtocol.Refused] (ERR_UNTRUSTED, ERR_MAGIC from an older TV...) or [IOException].
     */
    fun fetch(input: InputStream, output: OutputStream, store: (Map<String, Any?>) -> Pair<List<String>, Boolean>) {
        val din = DataInputStream(input); val dout = DataOutputStream(output)
        val req = "{\"v\":1}".toByteArray(Charsets.UTF_8)
        dout.write(MAGIC.toByteArray(Charsets.US_ASCII)); dout.writeShort(req.size); dout.write(req); dout.flush()
        val st = din.readUnsignedByte()
        if (st != BtProtocol.OK) throw BtProtocol.Refused(st)
        val len = din.readInt()
        if (len < 0 || len > MAX_REPLY) throw IOException("answer of the TV too big")
        val reply = runCatching { JsonLite.obj(String(ByteArray(len).also { din.readFully(it) }, Charsets.UTF_8)) }.getOrElse { throw IOException("answer of the TV not understood") }
        val (ids, keyStored) = store(reply)
        val ack = JsonLite.write(linkedMapOf("ack" to ids, "key" to keyStored)).toByteArray(Charsets.UTF_8)
        dout.writeShort(ack.size.coerceAtMost(65535)); dout.write(ack, 0, ack.size.coerceAtMost(65535)); dout.flush()
        val end = din.readUnsignedByte()
        if (end != BtProtocol.OK) throw BtProtocol.Refused(end)
    }
}

/** A report kept on the phone. [read] is local. */
/** [ts] is the TV's clock (display only); [at] is when the phone received it (what the retention uses: a TV with a wrong clock must not make reports vanish). */
data class StoredReport(val id: String, val tv: String, val ts: Long, val kind: String, val body: Map<String, Any?>, val read: Boolean = false, val at: Long = 0)

/** Where the phone keeps its reports: a private file, excluded from backups (Android), memory in tests. */
interface InboxPersistence {
    fun load(): String?
    fun save(text: String)
}

class MemoryInboxPersistence(var text: String? = null) : InboxPersistence {
    override fun load() = text
    override fun save(text: String) { this.text = text }
}

/**
 * The phone's inbox of reports: the key of each TV, the history (bounded in number, age and size), what is unread.
 * A report is accepted only if its signature matches the key the TV gave over the secure link; anything else is dropped.
 */
class ReportInbox(
    private val persistence: InboxPersistence,
    private val now: () -> Long = System::currentTimeMillis,
    val maxReports: Int = 150,
    val maxAgeMs: Long = 90L * 24 * 3600_000,
) {
    enum class Outcome { STORED, DUPLICATE, REJECTED }

    private val keys = LinkedHashMap<String, String>()      // tv address -> key
    private val reports = ArrayList<StoredReport>()          // oldest first

    init { synchronized(this) { load() } }

    @Synchronized fun hasKey(tv: String) = keys.containsKey(TrustRegistry.norm(tv))
    @Synchronized fun setKey(tv: String, key: String) { if (key.matches(Regex("[0-9a-f]{64}"))) { keys[TrustRegistry.norm(tv)] = key; save() } }
    @Synchronized fun forgetTv(tv: String) { val a = TrustRegistry.norm(tv); keys.remove(a); save() }

    /** Verifies and stores one envelope of [tv]. */
    @Synchronized fun accept(tv: String, env: Map<String, Any?>): Outcome {
        val key = keys[TrustRegistry.norm(tv)] ?: return Outcome.REJECTED
        val id = env["id"] as? String ?: return Outcome.REJECTED
        val ts = (env["ts"] as? Number)?.toLong() ?: return Outcome.REJECTED
        val kind = env["kind"] as? String ?: return Outcome.REJECTED
        val body = env["body"] as? String ?: return Outcome.REJECTED
        if (!ReportMac.verify(key, id, ts, kind, body, env["mac"] as? String)) return Outcome.REJECTED
        if (reports.any { it.id == id }) return Outcome.DUPLICATE
        val parsed = runCatching { JsonLite.obj(body) }.getOrNull() ?: return Outcome.REJECTED
        reports += StoredReport(id, (parsed["tv"] as? String).orEmpty().take(60), ts, kind, parsed, false, now())
        prune(); save()
        return Outcome.STORED
    }

    @Synchronized fun history(): List<StoredReport> { prune(); return reports.sortedByDescending { it.ts } }
    @Synchronized fun unread(): Int = reports.count { !it.read }
    @Synchronized fun markAllRead() { for (i in reports.indices) reports[i] = reports[i].copy(read = true); save() }
    @Synchronized fun clear() { reports.clear(); save() }

    private fun prune() {
        val t = now()
        reports.removeAll { t - it.at > maxAgeMs }
        while (reports.size > maxReports) reports.removeAt(0)
        while (reports.size > 1 && reports.sumOf { it.body.toString().length + 100 } > MAX_CHARS) reports.removeAt(0)
    }

    private fun save() {
        val o = linkedMapOf("keys" to keys.map { (a, k) -> linkedMapOf("a" to a, "k" to k) },
            "reports" to reports.map { linkedMapOf("id" to it.id, "tv" to it.tv, "ts" to it.ts, "kind" to it.kind, "read" to it.read, "at" to it.at, "body" to it.body) })
        runCatching { persistence.save(JsonLite.write(o)) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun load() {
        val o = runCatching { persistence.load()?.let { JsonLite.obj(it) } }.getOrNull() ?: return
        for (m in (o["keys"] as? List<Map<String, Any?>>).orEmpty()) { val a = m["a"] as? String; val k = m["k"] as? String; if (a != null && k != null) keys[a] = k }
        for (m in (o["reports"] as? List<Map<String, Any?>>).orEmpty()) runCatching {
            reports += StoredReport(m["id"] as String, m["tv"] as? String ?: "", (m["ts"] as Number).toLong(), m["kind"] as String, m["body"] as Map<String, Any?>, m["read"] as? Boolean ?: false, (m["at"] as? Number)?.toLong() ?: now())
        }
    }

    companion object { private const val MAX_CHARS = 400_000 }
}

/** What one visit to the TV brought. [designated] null = unknown (the TV did not answer). [phoneId] is what the parent designates in the TV's list. */
data class SyncResult(val stored: List<StoredReport>, val rejected: Int, val designated: Boolean?, val phoneId: String?, val tvName: String?)

/**
 * Phone side of the delivery: connects (secure RFCOMM, see [castbridge.core.trust.BtTransport]), takes the reports, stores them in the
 * [inbox] and acknowledges. Newest-first display and the notification are the caller's job. No network, no server.
 */
object ReportSync {
    fun run(link: Link, tvAddress: String, inbox: ReportInbox): SyncResult {
        val stored = ArrayList<StoredReport>(); var rejected = 0
        var designated: Boolean? = null; var phoneId: String? = null; var tvName: String? = null
        ParentalSyncProtocol.fetch(link.input, link.output) { reply ->
            @Suppress("UNCHECKED_CAST") val you = reply["you"] as? Map<String, Any?>
            designated = you?.get("designated") as? Boolean; phoneId = you?.get("id") as? String; tvName = (reply["tv"] as? String)?.take(60)
            var keyStored = false
            (reply["key"] as? String)?.let { if (designated == true) { inbox.setKey(tvAddress, it); keyStored = true } }
            val ids = ArrayList<String>()
            @Suppress("UNCHECKED_CAST")
            for (env in (reply["reports"] as? List<Any?>).orEmpty().take(50)) {
                val m = env as? Map<String, Any?> ?: continue
                val id = m["id"] as? String ?: continue
                when (inbox.accept(tvAddress, m)) {
                    ReportInbox.Outcome.STORED -> { ids += id; inbox.history().firstOrNull { it.id == id }?.let(stored::add) }
                    ReportInbox.Outcome.DUPLICATE -> ids += id
                    ReportInbox.Outcome.REJECTED -> { rejected++; ids += id }      // forged or wrong key: dropped, and not asked again
                }
            }
            ids to keyStored
        }
        return SyncResult(stored, rejected, designated, phoneId, tvName)
    }
}

/** French sentences for the notification and the list of reports on the phone (pure, so that they are tested). */
object ReportText {
    fun minutes(m: Long): String = if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"

    private fun name(r: StoredReport) = ((r.body["profile"] as? Map<*, *>)?.get("name") as? String) ?: "Enfant"

    fun title(r: StoredReport): String = when (r.kind) {
        "daily" -> "Rapport du jour : ${name(r)}"
        "weekly" -> "Rapport de la semaine : ${name(r)}"
        else -> when (r.body["alert"]) {
            "limit" -> "Temps d'écran atteint"; "blocked" -> "Application bloquée"
            "tamper" -> "Surveillance de la TV affaiblie"; "newapp" -> "Nouvelle application sur la TV"
            else -> "Contrôle parental"
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun text(r: StoredReport): String = when (r.kind) {
        "daily", "weekly" -> {
            val total = (r.body["totalMin"] as? Number)?.toLong() ?: 0L
            val apps = (r.body[if (r.kind == "daily") "apps" else "topApps"] as? List<Map<String, Any?>>).orEmpty().take(3)
            val warn = (r.body["supervision"] as? Map<String, Any?>)?.takeIf { it["state"] != "active" && it["state"] != "off" }?.let { " ⚠ " + it["label"] }
            val tamper = (r.body["tamper"] as? List<*>)?.size ?: (r.body["tamperCount"] as? Number)?.toInt() ?: 0
            buildString {
                append("${minutes(total)} d'écran")
                if (apps.isNotEmpty()) append(" : " + apps.joinToString(", ") { "${it["label"]} ${minutes((it["min"] as? Number)?.toLong() ?: 0)}" })
                (r.body["blocked"] as? List<*>)?.size?.takeIf { it > 0 }?.let { append(" · $it blocage(s)") }
                (r.body["blockedCount"] as? Number)?.toInt()?.takeIf { it > 0 }?.let { append(" · $it blocage(s)") }
                if (tamper > 0) append(" · surveillance affaiblie")
                warn?.let { append(it) }
            }
        }
        else -> (r.body["text"] as? String) ?: ""
    }

    /** One notification for a batch: the report itself when there is one, else a count. */
    fun notification(batch: List<StoredReport>): Pair<String, String>? = when {
        batch.isEmpty() -> null
        batch.size == 1 -> title(batch[0]) to text(batch[0])
        else -> "${batch.size} nouveaux rapports parentaux" to batch.sortedByDescending { it.ts }.take(3).joinToString(" · ") { title(it) }
    }
}
