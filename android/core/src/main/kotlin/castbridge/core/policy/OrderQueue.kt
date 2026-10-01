package castbridge.core.policy

import castbridge.core.lots.QueueStore
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.Envelope

/** An order waiting on the phone for one TV. The phone keeps the SIGNED TOKEN as an opaque string: it neither edits nor invents one (any change breaks the signature). */
data class QueuedOrder(val tv: String, val token: String, val kid: String, val seq: Long, val expiresAt: Long, val addedAt: Long)

/** An acknowledgement received from a TV, waiting to be sent to the server at the next Internet connection. Technical only: device code, key, sequence, result. */
data class PendingAck(val tv: String, val ack: OrderAck)

/**
 * The phone's DEFERRED queue of orders, same mechanics as the lot delivery queue (`DeliveryQueue`): filled when the phone has Internet, drained whenever a link to that TV exists,
 * persisted after every change (an app kill or a reboot loses nothing), one entry per (TV, key, sequence). The phone is only a messenger and has NO user interface for it.
 * Delivery order = ascending sequence number per key (the TV demands strictly increasing numbers).
 */
class OrderQueue(private val store: QueueStore, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val items = LinkedHashMap<String, QueuedOrder>()      // tv|kid:seq
    private val acks = ArrayList<PendingAck>()
    private val haveAck = HashMap<String, Long>()                  // tv|kid -> highest acknowledged sequence
    var cursor: Long = 0; private set

    init { synchronized(lock) { load() } }

    private fun key(tv: String, kid: String, seq: Long) = "$tv|$kid:$seq"

    fun pending(tv: String): List<QueuedOrder> = synchronized(lock) { items.values.filter { it.tv == tv }.sortedWith(compareBy({ it.kid }, { it.seq })) }
    fun hasWork(tv: String) = pending(tv).isNotEmpty()
    fun pendingAcks(): List<PendingAck> = synchronized(lock) { acks.toList() }
    /** What the phone already holds as acknowledgements for [tv] (sent in ORDER_HELLO so the TV only re-sends what is missing). */
    fun have(tv: String): Map<String, Long> = synchronized(lock) { haveAck.filterKeys { it.startsWith("$tv|") }.mapKeys { it.key.substringAfter('|') } }

    /** Adds an order the server gave for [tv]. False if it is not a well-formed order token, already queued, already acknowledged, or long expired (the TV is the judge of the rest). */
    fun add(tv: String, token: String): Boolean = synchronized(lock) {
        val e = Envelope.decode(token) ?: return false
        if (e.type != "order") return false
        val k = key(tv, e.keyId, e.seq)
        if (k in items || e.seq <= (haveAck["$tv|${e.keyId}"] ?: -1L)) return false
        if (now() > e.expiresAt + 7L * 24 * 3600 * 1000) return false
        items[k] = QueuedOrder(tv, token, e.keyId, e.seq, e.expiresAt, now()); persist(); true
    }

    /** A TV was just paired: orders released earlier for it must be fetched too. */
    fun resetCursor() = synchronized(lock) { cursor = 0; persist() }

    fun setCursor(c: Long) = synchronized(lock) { if (c > cursor) { cursor = c; persist() } }

    /** The TV judged an order: it leaves the queue and its acknowledgement waits for the server. */
    fun acked(tv: String, ack: OrderAck) = synchronized(lock) {
        items.remove(key(tv, ack.kid, ack.seq))
        val hk = "$tv|${ack.kid}"; if (ack.seq > (haveAck[hk] ?: -1L)) haveAck[hk] = ack.seq
        if (acks.none { it.tv == tv && it.ack.kid == ack.kid && it.ack.seq == ack.seq }) acks += PendingAck(tv, ack)
        persist()
    }

    /** The TV is already past this sequence number and has nothing to say about it: stop carrying it. */
    fun skip(tv: String, kid: String, seq: Long) = synchronized(lock) { if (items.remove(key(tv, kid, seq)) != null) persist() }

    /** The server confirmed it received these acknowledgements. */
    fun acksSent(sent: List<PendingAck>) = synchronized(lock) { acks.removeAll(sent.toSet()); persist() }

    /** Drops what cannot be useful any more (long expired). Call from the background task. */
    fun purge() = synchronized(lock) { val t = now() - 7L * 24 * 3600 * 1000; if (items.values.removeAll { it.expiresAt < t }) persist() }

    private fun persist() {
        val j = mapOf(
            "cursor" to cursor,
            "items" to items.values.map { mapOf("tv" to it.tv, "token" to it.token, "kid" to it.kid, "seq" to it.seq, "exp" to it.expiresAt, "at" to it.addedAt) },
            "acks" to acks.map { mapOf("tv" to it.tv, "ack" to it.ack.toText()) }, "have" to haveAck,
        )
        runCatching { store.save(JsonLite.write(j)) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun load() {
        val text = store.load() ?: return
        runCatching {
            val m = JsonLite.obj(text)
            cursor = m.long("cursor") ?: 0
            (m["items"] as? List<Map<String, Any?>>)?.forEach { i -> val q = QueuedOrder(i.str("tv")!!, i.str("token")!!, i.str("kid")!!, i.long("seq")!!, i.long("exp") ?: 0, i.long("at") ?: 0); items[key(q.tv, q.kid, q.seq)] = q }
            (m["acks"] as? List<Map<String, Any?>>)?.forEach { a -> OrderAck.parse(a.str("ack")!!)?.let { acks += PendingAck(a.str("tv")!!, it) } }
            (m["have"] as? Map<String, Any?>)?.forEach { (k, v) -> (v as? Number)?.let { haveAck[k] = it.toLong() } }
        }
    }
}
