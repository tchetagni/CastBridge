package castbridge.core.lots

import kotlin.test.*

class DeliveryQueueTest {
    private var clock = 10_000L
    private val store = MemoryQueueStore()
    private fun q() = DeliveryQueue(store) { clock }
    private fun m(scope: String, v: Int = 1, size: Long = 100) = LotMeta(LotId("learn", scope), v, size, "cd".repeat(32), "t $scope")
    private val tv = "tv1"
    private fun manifest(vararg held: LotMeta, rejected: List<LotRejection> = emptyList()) =
        TvManifest(LOT_SCHEMA, 1000, held.sumOf { it.bytes }, 0, held.map { TvLot(it, 1) }, emptyList(), rejected)

    @Test fun happyPathPendingSendingSentConfirmed() {
        val q = q(); val a = m("a")
        q.reconcile(tv, listOf(a to 0), null)
        assertEquals(DeliveryState.PENDING, q.get(tv, a.id)!!.state); assertEquals(a.id, q.next(tv)!!.lot.id)
        q.begin(tv, a.id); assertEquals(DeliveryState.SENDING, q.get(tv, a.id)!!.state)
        q.progress(tv, a.id, 40); assertEquals(40, q.get(tv, a.id)!!.offset)
        q.sent(tv, a.id); assertEquals(DeliveryState.SENT, q.get(tv, a.id)!!.state); assertNull(q.next(tv))
        q.reconcile(tv, listOf(a to 0), manifest(a)); assertEquals(DeliveryState.CONFIRMED, q.get(tv, a.id)!!.state)
        assertFalse(q.hasWork(tv))
    }

    @Test fun orderIsPriorityThenSmallestThenName() {
        val q = q(); val big = m("big", size = 500); val small = m("small", size = 10); val urgent = m("zz", size = 900)
        q.reconcile(tv, listOf(big to 5, small to 5, urgent to 0), null)
        assertEquals(listOf("zz", "small", "big"), buildList { repeat(3) { q.next(tv)!!.let { d -> add(d.lot.id.scope); q.cancel(tv, d.lot.id) } } })
    }

    @Test fun transientFailureBacksOffAndKeepsTheOffset() {
        val q = q(); val a = m("a"); q.reconcile(tv, listOf(a to 0), null)
        q.begin(tv, a.id); q.failedTransient(tv, a.id, "lien coupé", 30)
        val d = q.get(tv, a.id)!!; assertEquals(DeliveryState.PENDING, d.state); assertEquals(30, d.offset); assertEquals(1, d.attempts)
        assertNull(q.next(tv), "waiting for its delay"); assertEquals(clock + 5000, q.nextReadyAt(tv))
        clock += 5000; assertNotNull(q.next(tv))
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L), (1..4).map { DeliveryQueue.backoffMs(it) })
        assertEquals(15 * 60_000L, DeliveryQueue.backoffMs(40), "capped at 15 minutes")
        q.retry(tv, a.id); assertNotNull(q.next(tv), "the user can send now")
    }

    @Test fun newerVersionSupersedesWhatWasPending() {
        val q = q(); q.reconcile(tv, listOf(m("a", 1) to 0), null)
        q.begin(tv, LotId("learn", "a")); q.progress(tv, LotId("learn", "a"), 50)
        q.reconcile(tv, listOf(m("a", 2) to 0), null)
        val d = q.get(tv, LotId("learn", "a"))!!; assertEquals(2, d.lot.version); assertEquals(0, d.offset); assertEquals(DeliveryState.PENDING, d.state)
        assertEquals(1, q.of(tv).size, "one entry per lot: v1 is never sent")
        q.reconcile(tv, listOf(m("a", 3) to 0), manifest(m("a", 1)))               // TV held v1, v2 was never sent, v3 is
        assertEquals(3, q.get(tv, LotId("learn", "a"))!!.lot.version)
    }

    @Test fun noLongerWantedIsCancelledButConfirmedHistoryStays() {
        val q = q(); val a = m("a"); val b = m("b")
        q.reconcile(tv, listOf(a to 0, b to 0), null); q.confirmed(tv, a.id)
        q.reconcile(tv, emptyList(), null)
        assertEquals(DeliveryState.CONFIRMED, q.get(tv, a.id)!!.state); assertEquals(DeliveryState.CANCELLED, q.get(tv, b.id)!!.state)
        q.reconcile(tv, listOf(b to 0), null); assertEquals(DeliveryState.PENDING, q.get(tv, b.id)!!.state, "wanted again")
    }

    @Test fun theTvManifestIsTheTruthAfterAReset() {
        val q = q(); val a = m("a")
        q.reconcile(tv, listOf(a to 0), null); q.confirmed(tv, a.id)
        q.reconcile(tv, listOf(a to 0), manifest())                                  // the TV reports nothing: reset / evicted
        assertEquals(DeliveryState.PENDING, q.get(tv, a.id)!!.state); assertEquals(0, q.get(tv, a.id)!!.attempts)
    }

    @Test fun refusalIsKeptWithItsReasonAndNotRetriedUntilAskedOrNewer() {
        val q = q(); val a = m("a"); q.reconcile(tv, listOf(a to 0), null)
        q.refused(tv, a.id, "Pas assez de place"); q.reconcile(tv, listOf(a to 0), manifest())
        assertEquals(DeliveryState.REFUSED, q.get(tv, a.id)!!.state); assertNull(q.next(tv))
        q.reconcile(tv, listOf(m("a", 2) to 0), manifest()); assertEquals(DeliveryState.PENDING, q.get(tv, a.id)!!.state)
        // a Bluetooth delivery the TV refused tells why through its manifest
        q.sent(tv, a.id)
        q.reconcile(tv, listOf(m("a", 2) to 0), manifest(rejected = listOf(LotRejection(a.id, 2, "corrompu", 1))))
        assertEquals(DeliveryState.REFUSED, q.get(tv, a.id)!!.state); assertEquals("corrompu", q.get(tv, a.id)!!.lastError)
    }

    @Test fun survivesARestartAndNothingStaysInFlight() {
        var q = q(); val a = m("a"); val b = m("b")
        q.reconcile(tv, listOf(a to 0, b to 1), null); q.begin(tv, a.id); q.progress(tv, a.id, 60); q.refused(tv, b.id, "non")
        q.recordSeen(tv, manifest(m("c")))
        q = q()                                                                          // the phone rebooted
        assertEquals(DeliveryState.PENDING, q.get(tv, a.id)!!.state); assertEquals(60, q.get(tv, a.id)!!.offset, "resume hint kept")
        assertEquals("non", q.get(tv, b.id)!!.lastError); assertEquals(1, q.seen(tv)!!.manifest.lots.size)
        assertEquals(clock, q.seen(tv)!!.at)
    }

    @Test fun corruptPersistenceIsIgnored() {
        store.text = "not json"; assertTrue(q().all().isEmpty())
        store.text = """{"deliveries":[{"tv":"x","feature":"learn"}]}"""; assertTrue(q().all().isEmpty())
    }

    @Test fun twoTvsHaveIndependentQueues() {
        val q = q(); val a = m("a")
        q.reconcile("tvA", listOf(a to 0), null); q.reconcile("tvB", listOf(a to 0), null)
        q.confirmed("tvA", a.id)
        assertEquals(DeliveryState.CONFIRMED, q.get("tvA", a.id)!!.state); assertEquals(DeliveryState.PENDING, q.get("tvB", a.id)!!.state)
        q.forget("tvB"); assertTrue(q.of("tvB").isEmpty()); assertEquals(1, q.of("tvA").size)
    }
}

/** The offline-first scenarios, end to end: phone store + queue + planner + a real TvLotStore behind the TV API. */
class OfflineScenariosTest {
    private val dirs = ArrayList<java.io.File>()
    private var clock = 1_000_000L
    private val queueStore = MemoryQueueStore()
    private lateinit var phone: LotStore
    private lateinit var phoneDir: java.io.File
    private val tvA = "tv-A"; private val tvB = "tv-B"
    private var need = listOf(Need(LotId("learn", "cm2"), 0), Need(LotId("quiz", "cm2"), 1))
    private val tvs = ArrayList<FakeTv>()

    @BeforeTest fun setUp() { phoneDir = Kit.tmp(); dirs += phoneDir; phone = LotStore(phoneDir, 100_000) { clock } }
    @AfterTest fun tearDown() { dirs.forEach { it.deleteRecursively() }; tvs.forEach { it.dir.deleteRecursively() } }

    private fun newTv(max: Long = 10_000, starter: Long = 0) = FakeTv(starter, max, { clock }).also { tvs += it }
    private fun queue() = DeliveryQueue(queueStore) { clock }
    private fun delivery(q: DeliveryQueue = queue(), s: LotStore = phone) = LotDelivery(q, s) { need }

    /** The phone downloads a lot (what LotSync does): signed catalog + verified file. */
    private fun download(feature: String, scope: String, version: Int, size: Int): LotMeta {
        val d = Kit.bytes(scope.hashCode() + version * 7 + feature.length, size)
        val m = Kit.meta(feature, scope, version, d, "$feature $scope v$version")
        val part = phone.partFile(m).also { it.parentFile.mkdirs(); it.writeBytes(d) }
        assertIs<LotStore.Install.Ok>(phone.install(m, part, Kit.sign(listOf(m)).toJson()))
        return m
    }

    @Test fun tvAbsentForDaysThenBack_onlyTheLatestVersionIsSent() {
        val tv = newTv(); val q = queue(); val d = delivery(q)
        download("learn", "cm2", 1, 400); d.enqueue(tvA)
        val st = LotStatusText.of(LotId("learn", "cm2"), phone, q, tvA, reachable = false, nowMs = clock)
        assertEquals(LotStage.WAITING_TV, st.stage); assertContains(st.label, "la TV n'est pas à portée")
        // the TV stays away: the server publishes v2, v3 which the phone downloads and queues
        clock += 3 * 86_400_000L; download("learn", "cm2", 2, 420); d.enqueue(tvA)
        clock += 2 * 86_400_000L; val v3 = download("learn", "cm2", 3, 450); d.enqueue(tvA)
        val away = DirectTransport(tv, reachable = false)
        val r0 = d.deliver(tvA, away); assertFalse(r0.reachable); assertEquals(1, r0.pending); assertTrue(away.sends.isEmpty())
        assertEquals(3, q.get(tvA, v3.id)!!.lot.version)
        // the TV comes back
        val back = DirectTransport(tv); val r = d.deliver(tvA, back)
        assertTrue(r.reachable); assertEquals(listOf(v3.id), r.confirmed); assertEquals(listOf("learn:cm2@3"), back.sends, "v1 and v2 are never replayed")
        assertEquals(3, tv.learn.held[v3.id]!!.version)
        val ok = LotStatusText.of(v3.id, phone, q, tvA, true, clock); assertEquals(LotStage.UP_TO_DATE, ok.stage); assertEquals("À jour sur la TV", ok.label)
        assertContains(ok.detail, "données"); assertContains(LotStatusText.tvBudget(tvA, q, clock), "libres")
    }

    @Test fun linkDropAtEveryByteOffset_resumesWithoutResendingAnything() {
        val size = 120
        for (cut in 0..size) {
            val tv = newTv(); val q = queue(); val d = delivery(q)
            phone.remove(LotId("learn", "cm2")); queueStore.text = null
            val m = download("learn", "cm2", 1, size); need = listOf(Need(m.id, 0))
            val link = DirectTransport(tv, chunk = 7).also { it.dropAfterBytes = cut.toLong() }
            val r1 = d.deliver(tvA, link)
            val dq = queue()
            if (cut >= size) { assertEquals(listOf(m.id), r1.confirmed, "no drop at $cut"); continue }
            assertTrue(r1.confirmed.isEmpty(), "dropped at $cut"); assertEquals(cut.toLong(), tv.store.received(LotNames.fileName(m)), "the TV holds exactly what arrived")
            assertEquals(DeliveryState.PENDING, dq.get(tvA, m.id)!!.state)
            clock += 6_000                                                                // the retry delay passed
            val r2 = delivery(dq).deliver(tvA, link)
            assertEquals(listOf(m.id), r2.confirmed, "cut at $cut"); assertEquals(size.toLong(), link.sentBytes, "every byte sent exactly once (cut at $cut)")
            assertEquals(listOf(m), tv.learn.installed())
        }
    }

    @Test fun phoneRestartMidDelivery() {
        val tv = newTv(); val m = download("learn", "cm2", 1, 300); need = listOf(Need(m.id, 0))
        val link = DirectTransport(tv, chunk = 50).also { it.dropAfterBytes = 120 }
        delivery().deliver(tvA, link)                                                     // killed here
        phone = LotStore(phoneDir, 100_000) { clock }                                     // restart: everything is reloaded from disk
        val q2 = queue(); assertEquals(DeliveryState.PENDING, q2.get(tvA, m.id)!!.state)
        clock += 6_000
        val r = delivery(q2, phone).deliver(tvA, link); assertEquals(listOf(m.id), r.confirmed); assertEquals(300, link.sentBytes)
    }

    @Test fun tvResetRebuildsTheQueue() {
        var tv = newTv(); val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        assertEquals(listOf(m.id), delivery().deliver(tvA, DirectTransport(tv)).confirmed)
        tv = newTv()                                                                      // factory reset: empty manifest
        val link = DirectTransport(tv)
        val r = delivery().deliver(tvA, link)
        assertEquals(listOf(m.id), r.confirmed); assertEquals(listOf(m), tv.learn.installed())
        assertEquals(1, link.sends.size)
        // and a TV that already holds it costs nothing
        val again = DirectTransport(tv); delivery().deliver(tvA, again); assertTrue(again.sends.isEmpty())
    }

    @Test fun twoTvsAreServedIndependently() {
        val a = newTv(); val b = newTv(); val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        val d = delivery()
        d.enqueue(tvA); d.enqueue(tvB)
        d.deliver(tvA, DirectTransport(a))
        assertEquals(DeliveryState.CONFIRMED, queue().get(tvA, m.id)!!.state); assertEquals(DeliveryState.PENDING, queue().get(tvB, m.id)!!.state)
        assertTrue(b.learn.installed().isEmpty())
        d.deliver(tvB, DirectTransport(b)); assertEquals(listOf(m), b.learn.installed())
    }

    @Test fun serverUnreachableWhileTheTvIsReachable_deliversFromThePhoneStore() {
        val tv = newTv(); val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        val remote = FakeRemote().also { it.down = true }
        val sync = LotSync(phone, remote, listOf(Kit.pub), 10, { Net.UNMETERED }, { })
        assertContains(sync.sync(listOf(m.id)).blocked!!, "injoignable")
        assertEquals(listOf(m.id), delivery().deliver(tvA, DirectTransport(tv)).confirmed)
    }

    @Test fun tvReachableWhilePhoneHasNoInternet() {
        val tv = newTv(); val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        val sync = LotSync(phone, FakeRemote(), listOf(Kit.pub), 10, { Net.NONE }, { })
        assertContains(sync.sync(listOf(m.id)).blocked!!, "Pas de connexion")
        assertEquals(listOf(m.id), delivery().deliver(tvA, DirectTransport(tv)).confirmed)
    }

    @Test fun tvBudgetConflictSkipsOneLotAndProposesWhatToDrop() {
        val tv = newTv(max = 1000, starter = 200)
        val a = download("learn", "cm2", 1, 500); val b = download("quiz", "cm2", 1, 400)
        need = listOf(Need(a.id, 0), Need(b.id, 1))
        val r = delivery().deliver(tvA, DirectTransport(tv))
        assertEquals(listOf(b.id), r.skipped.map { it.meta.id }.also { assertEquals(listOf(a.id), r.confirmed) })
        assertEquals(listOf(a.id), r.skipped.single().dropSuggestion)
        val text = LotStatusText.skipped(r.skipped.single())
        assertContains(text, "Non envoyé"); assertContains(text, "learn cm2"); assertContains(text, "il manque")
        assertEquals(700, tv.store.usedBytes()); assertContains(LotStatusText.tvBudget(tvA, queue(), clock), "libres")
    }

    @Test fun aRefusalByTheTvIsShownAndNotRetriedForever() {
        val tv = FakeTv(0, 10_000, { clock }, keys = listOf(Kit.otherPub)).also { tvs += it }       // a TV that does not trust this server key
        val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        val link = DirectTransport(tv)
        val r = delivery().deliver(tvA, link)
        assertEquals(1, r.refused.size); assertContains(r.refused.single().second, "non signé")
        val q = queue(); val st = LotStatusText.of(m.id, phone, q, tvA, true, clock)
        assertEquals(LotStage.REFUSED, st.stage); assertContains(st.detail, "non signé")
        delivery(q).deliver(tvA, link); assertEquals(1, link.sends.size, "not sent again until the user asks")
        q.retry(tvA, m.id); delivery(q).deliver(tvA, link); assertEquals(2, link.sends.size)
    }

    @Test fun bluetoothFileDeliveryStaysSentUntilTheNextManifest() {
        val tv = newTv(); val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        val blind = object : LotTransport {
            override val label = "Bluetooth"; override val canReadManifest = false
            override fun manifest(): TvManifest? = null
            override fun setPriority(ids: List<LotId>) = false
            override fun remove(id: LotId) = false
            override fun send(meta: LotMeta, file: java.io.File, proofJson: String, onConfirmed: (Long) -> Unit, cancelled: () -> Boolean) = SendResult.Delivered
        }
        val r = delivery().deliver(tvA, blind)
        assertEquals(listOf(m.id), r.sent); assertTrue(r.confirmed.isEmpty())
        val q = queue(); assertEquals(DeliveryState.SENT, q.get(tvA, m.id)!!.state)
        assertEquals("Envoyé", LotStatusText.of(m.id, phone, q, tvA, false, clock).label)
        // later, over Wi-Fi: the TV (which adopted the files meanwhile) tells it holds the lot
        val link = DirectTransport(tv); tv.store.receive(LotNames.fileName(m), 0, m.bytes, phone.file(m.id, 1)!!.readBytes()); tv.store.installReceived(LotNames.fileName(m), phone.proof(m.id)!!)
        val r2 = delivery(q).deliver(tvA, link)
        assertEquals(DeliveryState.CONFIRMED, queue().get(tvA, m.id)!!.state); assertTrue(link.sends.isEmpty())
    }

    @Test fun lotsEvictedOnThePhoneMeanwhileAreDropped() {
        val tv = newTv(); val m = download("learn", "cm2", 1, 200); need = listOf(Need(m.id, 0))
        val d = delivery(); d.enqueue(tvA); phone.remove(m.id)
        val link = DirectTransport(tv); val r = d.deliver(tvA, link)
        assertTrue(link.sends.isEmpty()); assertEquals(0, r.pending); assertEquals(listOf(m.id), r.plan!!.notOnPhone)
    }

    @Test fun statusTextsInFrench() {
        clock += 10 * 86_400_000L
        val q = queue(); val id = LotId("learn", "cm2")
        assertEquals("Pas encore téléchargé", LotStatusText.of(id, phone, q, tvA, false, clock).label)
        val m = download("learn", "cm2", 1, 100)
        assertEquals("Téléchargé sur le téléphone", LotStatusText.of(id, phone, q, tvA, false, clock).label)
        q.reconcile(tvA, listOf(m to 0), null)
        assertEquals("En attente d'envoi à la TV", LotStatusText.of(id, phone, q, tvA, true, clock).label)
        q.begin(tvA, id); q.progress(tvA, id, 50); assertEquals("Envoi en cours", LotStatusText.of(id, phone, q, tvA, true, clock).label)
        assertContains(LotStatusText.of(id, phone, q, tvA, true, clock).detail, "50 %")
        assertEquals("il y a 3 jours", LotStatusText.age(clock - 3 * 86_400_000L, clock)); assertEquals("hier", LotStatusText.age(clock - 30 * 3600_000L, clock))
        assertEquals("il y a 5 min", LotStatusText.age(clock - 300_000, clock)); assertEquals("à l'instant", LotStatusText.age(clock, clock))
        assertContains(LotStatusText.tvBudget("nobody", q, clock), "jamais vue")
    }
}

class StarterBudgetTest {
    @Test fun bundledStarterDataFitsTheTvBudgetWithRoomForLots() {
        val r = StarterBudget.measure()
        println(r.text())
        assertTrue(r.learnBytes > 0 && r.quizBytes > 0, "the bundled data is measured")
        assertTrue(r.total <= LotBudget.TV_MAX_BYTES, "starter data ${r.total} > 10 Mo")
        assertTrue(r.total < LotBudget.TV_MAX_BYTES - (2L shl 20), "keep at least 2 Mo of the 10 Mo for lots pushed by the phone")
    }

    @Test fun contractValues() {
        assertEquals(10L * 1024 * 1024, LotBudget.TV_MAX_BYTES); assertEquals(100L * 1024 * 1024, LotBudget.PHONE_MAX_BYTES)
    }
}
