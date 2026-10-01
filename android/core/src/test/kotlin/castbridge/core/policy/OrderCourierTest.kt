package castbridge.core.policy

import castbridge.core.lots.MemoryQueueStore
import castbridge.core.owner.*
import kotlin.test.*

private class FakeServer(var online: Boolean = true) : OrderServerApi {
    val orders = ArrayList<ServerOrder>()
    val received = ArrayList<PendingAck>()
    var cursor = 0L
    override fun fetch(since: Long): Fetched? = if (online) Fetched(orders.size.toLong(), orders.drop(since.toInt())) else null
    override fun postAcks(acks: List<PendingAck>): Boolean { if (!online) return false; received += acks; return true }
}

class OrderCourierTest {
    private val k = PolicyKit()
    private val tvCode = DeviceCode.of(k.fp)
    private val server = FakeServer()
    private val qStore = MemoryQueueStore()
    private val queue = OrderQueue(qStore) { k.wall.v }
    private val courier = OrderCourier(queue, server)
    private val receiver get() = TvOrderReceiver(k.engine)

    private fun serverOrder(action: String, params: Map<String, String> = emptyMap(), seq: Long, tv: String = tvCode, signer: Signer = k.server) { server.orders += ServerOrder(tv, k.order(action, params, seq = seq, signer = signer)) }

    @Test fun phoneFetchesOnlyItsPairedTvsThenDeliversLaterAndReturnsTheAcks() {
        serverOrder(PolicyActions.FLAG_SET, mapOf("name" to "learn.beta", "value" to "1"), seq = 1)
        serverOrder(PolicyActions.APP_MIN_VERSION, mapOf("version" to "9"), seq = 2)
        serverOrder(PolicyActions.APP_MIN_VERSION, mapOf("version" to "1"), seq = 3, tv = "ZZZZ-ZZZZ-ZZZZ-ZZZZ")   // a TV this phone is not paired with
        assertTrue(courier.syncServer(setOf(tvCode)))
        assertEquals(2, queue.pending(tvCode).size); assertEquals(0, queue.pending("ZZZZ-ZZZZ-ZZZZ-ZZZZ").size)
        // the TV is absent for now; later the link exists
        val r = courier.deliver(tvCode, LoopLink(receiver))
        assertEquals(CourierResult.Done(2, 0), r)
        assertTrue(k.engine.current.flag("learn.beta")); assertEquals(9, k.engine.current.minVersion)
        assertTrue(queue.pending(tvCode).isEmpty()); assertEquals(2, queue.pendingAcks().size)
        // next Internet connection: acknowledgements go up, and only technical fields
        assertTrue(courier.syncServer(setOf(tvCode)))
        assertEquals(2, server.received.size); assertTrue(queue.pendingAcks().isEmpty())
        assertTrue(server.received.all { it.ack.applied && it.tv == tvCode })
        assertTrue(server.received.none { it.ack.toText().contains("learn") || it.ack.toText().contains("flag") }, "no content of the order goes back")
    }

    @Test fun offlinePhoneKeepsAcksUntilTheNetworkReturns() {
        serverOrder(PolicyActions.RIGHTS_REFRESH, seq = 1); courier.syncServer(setOf(tvCode)); courier.deliver(tvCode, LoopLink(receiver))
        server.online = false
        assertFalse(courier.syncServer(setOf(tvCode))); assertEquals(1, queue.pendingAcks().size)
        // the phone restarts: nothing lost
        val q2 = OrderQueue(qStore) { k.wall.v }; assertEquals(1, q2.pendingAcks().size)
        server.online = true; assertTrue(OrderCourier(q2, server).syncServer(setOf(tvCode))); assertEquals(1, server.received.size)
    }

    @Test fun fetchIsIncrementalAndDuplicatesAreIgnored() {
        serverOrder(PolicyActions.RIGHTS_REFRESH, seq = 1); courier.syncServer(setOf(tvCode)); courier.syncServer(setOf(tvCode))
        assertEquals(1, queue.pending(tvCode).size)
        assertFalse(queue.add(tvCode, server.orders[0].token), "same order twice")
        assertFalse(queue.add(tvCode, "garbage")); assertEquals(1L, queue.cursor)
    }

    @Test fun anOrderRefusedByTheTvIsReportedAndTheFollowingOnesApply() {
        serverOrder("format.disk", seq = 1); serverOrder(PolicyActions.RIGHTS_REFRESH, seq = 2, signer = k.rogue); serverOrder(PolicyActions.APP_MIN_VERSION, mapOf("version" to "4"), seq = 3)
        courier.syncServer(setOf(tvCode))
        // the phone cannot tell which is good: it carries them all
        val r = courier.deliver(tvCode, LoopLink(receiver)) as CourierResult.Done
        assertEquals(3, r.acked); assertEquals(4, k.engine.current.minVersion)
        courier.syncServer(setOf(tvCode))
        assertEquals(listOf(AckReason.UNKNOWN_ACTION, AckReason.UNKNOWN_KEY, AckReason.APPLIED), server.received.sortedBy { it.ack.seq }.map { it.ack.reason })
    }

    @Test fun theLinkDropsInTheMiddleAndTheTransferResumes() {
        val long = "m".repeat(250)   // a message order of a few hundred chars is still one chunk: force several chunks with many lots
        val lots = (1..22).joinToString(",") { "learn:long-lot-name-%02d".format(it) }
        serverOrder(PolicyActions.CATALOG_AVAILABLE, mapOf("lots" to lots), seq = 1)
        serverOrder(PolicyActions.MESSAGE_SHOW, mapOf("id" to "x", "text" to long.take(200)), seq = 2)
        courier.syncServer(setOf(tvCode))
        val tokenLen = queue.pending(tvCode).first().token.length; assertTrue(tokenLen > OrderFrames.CHUNK_SIZE, "several chunks: $tokenLen")
        val rx = receiver
        // cut after HELLO + BEGIN + 1 chunk
        val r1 = courier.deliver(tvCode, LoopLink(rx, cutAfterWrites = 3))
        assertTrue(r1 is CourierResult.LinkDown); assertEquals(0, k.engine.current.version)
        assertEquals(2, queue.pending(tvCode).size, "nothing lost, nothing marked as done")
        // same receiver object (the TV kept its partial order): resume from where it is
        val link2 = LoopLink(rx)
        val r2 = courier.deliver(tvCode, link2)
        assertEquals(CourierResult.Done(2, 0), r2)
        assertEquals(2, k.engine.current.version)
        // the first chunk was not sent again: HELLO, BEGIN, chunk(s)… the second pass starts at the TV's offset
        assertTrue(link2.log.count { it == OrderFrames.CHUNK } < (tokenLen / OrderFrames.CHUNK_SIZE + 1) + 2)
    }

    @Test fun theTvRestartsAndLosesItsPartialOrderThePhoneSimplyStartsOver() {
        serverOrder(PolicyActions.CATALOG_AVAILABLE, mapOf("lots" to (1..22).joinToString(",") { "learn:long-lot-name-%02d".format(it) }), seq = 1)
        courier.syncServer(setOf(tvCode))
        assertTrue(courier.deliver(tvCode, LoopLink(receiver, cutAfterWrites = 3)) is CourierResult.LinkDown)
        val engine2 = k.engine(k.store)                             // TV restarted: new receiver, no partial
        assertEquals(CourierResult.Done(1, 0), courier.deliver(tvCode, LoopLink(TvOrderReceiver(engine2))))
        assertEquals(22, engine2.current.available.size)
    }

    @Test fun theAckIsLostButTheOrderWasApplied() {
        serverOrder(PolicyActions.APP_MIN_VERSION, mapOf("version" to "6"), seq = 1)
        courier.syncServer(setOf(tvCode))
        // the TV applies it, then the link dies before the ack reaches the phone: answers dropped after BEGIN's answer
        val r = courier.deliver(tvCode, LoopLink(receiver, dropAnswersFrom = 3))
        assertTrue(r is CourierResult.LinkDown); assertEquals(6, k.engine.current.minVersion); assertEquals(1, queue.pending(tvCode).size)
        // next link: the TV's HELLO answer carries the missing acknowledgement, nothing is applied twice
        val r2 = courier.deliver(tvCode, LoopLink(receiver))
        assertEquals(1, k.engine.current.version); assertEquals(0, queue.pending(tvCode).size); assertEquals(1, queue.pendingAcks().size)
        assertTrue(r2 is CourierResult.Done)
    }

    @Test fun anOldTvIgnoresTheFramesAndNothingIsLost() {
        serverOrder(PolicyActions.RIGHTS_REFRESH, seq = 1); courier.syncServer(setOf(tvCode))
        val oldTv = object : OrderLink {                             // an old TV: its channel drops any frame type it does not know
            var seen = 0
            override fun write(frame: ByteArray) { seen++ }
            override fun read(): OwnerFrames.Frame? = null
        }
        assertEquals(CourierResult.NotSupported, courier.deliver(tvCode, oldTv))
        assertEquals(1, queue.pending(tvCode).size, "still queued for a newer TV")
        // and the receiver hands every frame it does not own back to the other handlers
        val rx = receiver
        assertNull(rx.onFrame(OwnerFrames.COMMAND, ByteArray(0))); assertNull(rx.onFrame(OwnerFrames.ACTIVATION, "cbx1.x.y".toByteArray())); assertNull(rx.onFrame(99, ByteArray(0)))
        assertTrue(OrderFrames.TYPES.none { it <= OwnerFrames.ACTIVATION }, "new types never collide with the existing ones")
    }

    @Test fun anOldPhoneNeverSendsOrderFramesSoNothingChangesForTheTv() {
        val rx = receiver
        assertNull(rx.onFrame(OwnerFrames.ACTIVATION, "x".toByteArray()))
        assertEquals(0, k.engine.current.version)
    }

    @Test fun theTvIgnoresMalformedTransferFrames() {
        val rx = receiver
        assertEquals(emptyList(), rx.onFrame(OrderFrames.BEGIN, "id=zz\ntotal=5".toByteArray()))
        assertEquals(emptyList(), rx.onFrame(OrderFrames.BEGIN, "id=00000000\ntotal=99999".toByteArray()))
        assertEquals(emptyList(), rx.onFrame(OrderFrames.CHUNK, "00000000|0|abc".toByteArray()), "chunk without BEGIN")
        assertEquals(emptyList(), rx.onFrame(OrderFrames.CHUNK, "garbage".toByteArray()))
        // a token whose bytes do not match the announced id is dropped, not judged
        val t = k.order(PolicyActions.RIGHTS_REFRESH, seq = 1); val id = OrderFrames.idOf(t)
        rx.onFrame(OrderFrames.BEGIN, "id=$id\ntotal=${t.length}".toByteArray())
        assertEquals(emptyList(), rx.onFrame(OrderFrames.CHUNK, "$id|0|${"x".repeat(t.length)}".toByteArray()))
        assertEquals(0, k.engine.current.version)
    }

    @Test fun aTamperedOrderInTheQueueIsStillJudgedByTheTvNotByThePhone() {
        val t = k.order(PolicyActions.BUDGET_SET, mapOf("name" to "lots_mb", "value" to "10"), seq = 1)
        val p = t.split('.'); val body = String(java.util.Base64.getUrlDecoder().decode(p[1])).replace("value|10", "value|99")
        server.orders += ServerOrder(tvCode, p[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray()) + "." + p[2])
        courier.syncServer(setOf(tvCode)); courier.deliver(tvCode, LoopLink(receiver))
        assertNull(k.engine.current.budgets["lots_mb"]); courier.syncServer(setOf(tvCode))
        assertEquals(AckReason.BAD_SIGNATURE, server.received.single().ack.reason)
    }

    @Test fun skipsAnOrderTheTvIsAlreadyPast() {
        serverOrder(PolicyActions.RIGHTS_REFRESH, seq = 5); courier.syncServer(setOf(tvCode))
        k.engine.receive(k.order(PolicyActions.RIGHTS_REFRESH, seq = 9))        // the TV got a later order by another path
        assertEquals(CourierResult.Done(1, 1), courier.deliver(tvCode, LoopLink(receiver)))
        assertTrue(queue.pending(tvCode).isEmpty())
    }

    @Test fun expiredLongAgoOrdersAreNotCarried() {
        k.wall.v = NOW0 + 100 * DAY
        val stale = k.order(PolicyActions.RIGHTS_REFRESH, seq = 1, expiresAt = NOW0 + DAY)
        assertFalse(queue.add(tvCode, stale))
    }
}
