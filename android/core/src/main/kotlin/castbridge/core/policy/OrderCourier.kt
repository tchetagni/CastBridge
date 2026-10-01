package castbridge.core.policy

import castbridge.core.owner.OwnerFrames
import java.io.IOException

/** One order the server hands to this phone for a TV it is paired with. */
data class ServerOrder(val tv: String, val token: String)
data class Fetched(val cursor: Long, val orders: List<ServerOrder>)

/** The phone's two calls to the server (authenticated by the device; `GET /api/v1/orders?since=` and `POST /api/v1/orders/acks`, docs/ORDRES.md § Serveur). Null / false = no Internet. */
interface OrderServerApi {
    fun fetch(since: Long): Fetched?
    fun postAcks(acks: List<PendingAck>): Boolean
}

sealed class CourierResult {
    /** Every queued order for this TV was judged (or skipped); [acked] acknowledgements collected. */
    data class Done(val acked: Int, val skipped: Int) : CourierResult()
    /** The link dropped: what was judged is saved, the rest waits for the next link (the TV keeps its partial order and tells where to resume). */
    data class LinkDown(val acked: Int, val reason: String) : CourierResult()
    /** The TV does not speak the order frames (old TV): nothing is lost, the orders stay queued. */
    object NotSupported : CourierResult()
}

/**
 * The phone as messenger (docs/ORDRES.md § Téléphone). Two independent steps, run by a background job (WorkManager on Android, battery respected, no UI):
 *  - [syncServer]: with Internet, fetch the orders of the paired TVs into the [OrderQueue] and upload the TV acknowledgements collected earlier;
 *  - [deliver]: as soon as ANY link to a TV exists (Bluetooth owner channel or Wi-Fi tunnel), hand over its queued orders in order, in chunks with resume, collect the acknowledgements.
 * The phone never opens a channel for the user, never decodes an order for display and cannot make one: it only carries signed tokens.
 */
class OrderCourier(private val queue: OrderQueue, private val server: OrderServerApi) {
    /** [pairedTvs] = device codes of the TVs paired with this phone (the server filters too; this is the second lock). Returns false when offline. */
    fun syncServer(pairedTvs: Set<String>): Boolean {
        val acks = queue.pendingAcks()
        if (acks.isNotEmpty() && server.postAcks(acks)) queue.acksSent(acks)
        val f = server.fetch(queue.cursor) ?: return false
        f.orders.filter { it.tv in pairedTvs }.forEach { queue.add(it.tv, it.token) }
        queue.setCursor(f.cursor)
        queue.purge()
        val again = queue.pendingAcks()
        if (again.isNotEmpty() && server.postAcks(again)) queue.acksSent(again)
        return true
    }

    fun deliver(tv: String, link: OrderLink, maxReads: Int = 64): CourierResult {
        var acked = 0; var skipped = 0
        try {
            link.write(OrderFrames.hello(queue.have(tv)))
            var tvSeqs: Map<String, Long>? = null
            // answers to HELLO: STATE then the missing acknowledgements
            var n = 0
            while (n++ < maxReads) {
                val f = link.read() ?: break
                when (f.type) {
                    OrderFrames.STATE -> tvSeqs = OrderFrames.parseSeqs(f.text)
                    OrderFrames.ACK -> OrderAck.parse(f.text)?.let { queue.acked(tv, it); acked++ }
                }
            }
            if (tvSeqs == null) return CourierResult.NotSupported
            for (o in queue.pending(tv)) {
                if (o.seq <= (tvSeqs[o.kid] ?: 0L)) { queue.skip(tv, o.kid, o.seq); skipped++; continue }   // the TV is already past it (its ack, if any, came with the backlog)
                if (send(tv, o, link) == null) return CourierResult.LinkDown(acked, "pas d'accusé de la TV")
                acked++
            }
        } catch (e: IOException) {
            return CourierResult.LinkDown(acked, e.message ?: "liaison coupée")
        }
        return CourierResult.Done(acked, skipped)
    }

    /** Sends one order with resume (BEGIN → the TV says where it is → chunks from there); the TV's acknowledgement, or null if the link went quiet before it came. */
    private fun send(tv: String, o: QueuedOrder, link: OrderLink): OrderAck? {
        val id = OrderFrames.idOf(o.token)
        link.write(OrderFrames.begin(id, o.token.length))
        var offset = -1
        var rounds = 0
        while (rounds++ < 400) {
            while (true) {
                val f = link.read() ?: break
                when (f.type) {
                    OrderFrames.NEED -> f.text.split('|').takeIf { it.size == 2 && it[0] == id }?.let { offset = it[1].toIntOrNull() ?: offset }
                    OrderFrames.ACK -> OrderAck.parse(f.text)?.let { a -> queue.acked(tv, a); if (a.kid == o.kid && a.seq == o.seq) return a }
                }
            }
            if (offset < 0 || offset >= o.token.length) return null
            val end = minOf(offset + OrderFrames.CHUNK_SIZE, o.token.length)
            link.write(OrderFrames.chunk(id, offset, o.token.substring(offset, end)))
            offset = end                                  // optimistic; a NEED from the TV puts it back where the TV really is
        }
        return null
    }
}
