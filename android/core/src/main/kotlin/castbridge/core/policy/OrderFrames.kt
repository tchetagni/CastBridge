package castbridge.core.policy

import castbridge.core.owner.OwnerFrames
import java.io.IOException

/**
 * Frames of the order transfer, carried by the OWNER Bluetooth channel (service `…0005` = [OwnerFrames.SERVICE_UUID], handshake `CBTO`, frame layout of [OwnerFrames]) after the
 * existing trust link / carrier mode: NEW frame types 16..21. A TV whose channel does not handle them (an old TV; today the current one too, the orders are not wired, inventory M7)
 * does NOT ignore them: its [castbridge.core.owner.OwnerChannelServer] answers any type it does not know with RESULT(0) « Non pris en charge par cette TV » (the sender must read that
 * as « TV not able »); an old phone never sends them. Nothing new is opened.
 *
 *   phone → TV  16 ORDER_HELLO   `have=<kid>:<seq>` lines: for each key, the highest acknowledgement the phone already holds
 *   TV → phone  17 ORDER_STATE   `v=1` · `policyVersion=n` · `seq=<kid>:<last accepted>` lines   (then the missing acknowledgements, one ORDER_ACK each)
 *   phone → TV  18 ORDER_BEGIN   `id=<8 hex>` `total=<chars>`      start (or resume) one order
 *   phone → TV  19 ORDER_CHUNK   `id|offset|` + the next ≤ 1024 ASCII chars of the token
 *   TV → phone  20 ORDER_ACK     [OrderAck.toText]  once the whole token arrived and was judged
 *   TV → phone  21 ORDER_NEED    `id|offset`   where the TV is (after BEGIN: resume point; after a chunk at the wrong offset: the offset it wants)
 */
object OrderFrames {
    const val HELLO = 16
    const val STATE = 17
    const val BEGIN = 18
    const val CHUNK = 19
    const val ACK = 20
    const val NEED = 21
    const val CHUNK_SIZE = 1024
    val TYPES = setOf(HELLO, STATE, BEGIN, CHUNK, ACK, NEED)

    /** Short stable identifier of a token (not secret). */
    fun idOf(token: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).take(4).joinToString("") { "%02x".format(it) }

    fun hello(have: Map<String, Long>) = OwnerFrames.encode(HELLO, have.toSortedMap().entries.joinToString("\n") { "have=${it.key}:${it.value}" })
    fun begin(id: String, total: Int) = OwnerFrames.encode(BEGIN, "id=$id\ntotal=$total")
    fun chunk(id: String, offset: Int, data: String) = OwnerFrames.encode(CHUNK, "$id|$offset|$data")
    fun need(id: String, offset: Int) = OwnerFrames.encode(NEED, "$id|$offset")
    fun ack(a: OrderAck) = OwnerFrames.encode(ACK, a.toText())
    fun state(version: Long, seqs: Map<String, Long>) = OwnerFrames.encode(STATE, (listOf("v=1", "policyVersion=$version") + seqs.toSortedMap().map { "seq=${it.key}:${it.value}" }).joinToString("\n"))

    fun parseHave(text: String): Map<String, Long> = text.split('\n').mapNotNull { l -> l.takeIf { it.startsWith("have=") }?.removePrefix("have=")?.split(':')?.takeIf { it.size == 2 }?.let { runCatching { it[0] to it[1].toLong() }.getOrNull() } }.toMap()
    fun parseSeqs(text: String): Map<String, Long> = text.split('\n').mapNotNull { l -> l.takeIf { it.startsWith("seq=") }?.removePrefix("seq=")?.split(':')?.takeIf { it.size == 2 }?.let { runCatching { it[0] to it[1].toLong() }.getOrNull() } }.toMap()
}

/**
 * TV side of the transfer: feed it every frame of the owner channel; it answers the frames of its type with the frames to send back and returns null for any other type (the caller
 * then lets the other handlers have it: an unknown frame is never an error). Partial orders are kept in memory per id; after a drop the phone reconnects, sends BEGIN again and is
 * told where to resume. A token is judged only when complete, by [PolicyEngine.receive]; the TV never trusts the transfer, only the signature.
 */
class TvOrderReceiver(private val engine: PolicyEngine, private val maxPartials: Int = 4) {
    private class Partial(val total: Int) { val data = StringBuilder() }
    private val partial = LinkedHashMap<String, Partial>()

    fun onFrame(type: Int, payload: ByteArray): List<ByteArray>? {
        if (type !in OrderFrames.TYPES) return null
        val text = String(payload, Charsets.UTF_8)
        return when (type) {
            OrderFrames.HELLO -> {
                val have = OrderFrames.parseHave(text)
                listOf(OrderFrames.state(engine.current.version, engine.seqSnapshot())) + engine.acksAfter(have).map { OrderFrames.ack(it) }
            }
            OrderFrames.BEGIN -> {
                val f = text.split('\n').associate { it.substringBefore('=') to it.substringAfter('=') }
                val id = f["id"]?.takeIf { it.matches(Regex("^[0-9a-f]{8}$")) } ?: return emptyList()
                val total = f["total"]?.toIntOrNull()?.takeIf { it in 1..PolicyEngine.MAX_TOKEN } ?: return emptyList()
                val p = partial[id]?.takeIf { it.total == total } ?: Partial(total).also { partial[id] = it; while (partial.size > maxPartials) partial.remove(partial.keys.first()) }
                listOf(OrderFrames.need(id, p.data.length))
            }
            OrderFrames.CHUNK -> {
                val a = text.indexOf('|'); val b = text.indexOf('|', a + 1)
                if (a < 0 || b < 0) return emptyList()
                val id = text.substring(0, a); val off = text.substring(a + 1, b).toIntOrNull() ?: return emptyList(); val data = text.substring(b + 1)
                val p = partial[id] ?: return emptyList()
                if (off != p.data.length) return listOf(OrderFrames.need(id, p.data.length))
                if (p.data.length + data.length > p.total) { partial.remove(id); return emptyList() }
                p.data.append(data)
                if (p.data.length < p.total) return emptyList()
                partial.remove(id)
                val token = p.data.toString()
                if (OrderFrames.idOf(token) != id) emptyList() else listOf(OrderFrames.ack(engine.receive(token)))
            }
            else -> emptyList()      // STATE / ACK / NEED are TV → phone frames: a phone never sends them to us, ignore
        }
    }
}

/** A byte pipe to one TV for the order transfer (Bluetooth owner channel, or the tunnel). [write] false / [IOException] = link down; [read] null = nothing more now. */
interface OrderLink {
    @Throws(IOException::class) fun write(frame: ByteArray)
    @Throws(IOException::class) fun read(): OwnerFrames.Frame?
}
