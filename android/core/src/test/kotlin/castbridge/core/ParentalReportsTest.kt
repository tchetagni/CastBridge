package castbridge.core

import castbridge.core.net.JsonLite
import castbridge.core.parental.*
import castbridge.core.trust.MemoryTrustPersistence
import castbridge.core.trust.TrustRegistry
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import java.io.*
import java.security.SecureRandom
import java.time.ZoneId
import kotlin.test.*

private class RClock(var t: Long = 1_750_000_000_000L) { fun now() = t; fun advanceMin(m: Long) { t += m * 60_000 } }

private const val PHONE_A = "AA:BB:CC:DD:EE:01"
private const val PHONE_B = "AA:BB:CC:DD:EE:02"
private const val CHILD_PHONE = "AA:BB:CC:DD:EE:99"
private val KEY = "ab".repeat(32)

/** A TV with a PIN, one profile, a trust registry holding the three phones, and the reports layer. */
private class ReportRig(val clock: RClock = RClock()) {
    val kv = MemoryKv()
    val trust = TrustRegistry(MemoryTrustPersistence(), clock::now).also { it.trust(PHONE_A, "Téléphone de Maman"); it.trust(PHONE_B, "Téléphone de Papa"); it.trust(CHILD_PHONE, "Téléphone de Léa") }
    val engine = ParentalEngine(kv, clock::now, { ZoneId.of("UTC") }, PinHasher(iterations = 1000)).also {
        it.createPin("4821")
        it.edit { c -> c.copy(enabled = true, profiles = listOf(ChildProfile("c1", "Léa"), ChildProfile("c2", "Marc")), activeProfile = "c1") }
        it.editApps { s -> s.copy(supervise = true, baselined = true) }
    }
    val recipients = ReportRecipients(PrefixKv(kv, "r."), { trust.isTrusted(it) }, clock::now)
    val outbox = ReportOutbox(PrefixKv(kv, "o."), clock::now)
    val reports = ParentalReports(PrefixKv(kv, "p."), engine, recipients, outbox, { "TV salon" }, clock::now) { ZoneId.of("UTC") }
    val host = ReportSyncHost(recipients, outbox, { trust.isTrusted(it) }, { "TV salon" }, clock::now)
    init { engine.onEvent = reports::onEvent }
    val env = AppEnv("castbridge.receiver")
}

/** Several stores on one MemoryKv (the TV uses separate keys of the same preferences file). */
private class PrefixKv(private val kv: KvStore, private val prefix: String) : KvStore {
    override fun get(key: String) = kv.get(prefix + key)
    override fun put(key: String, value: String?) = kv.put(prefix + key, value)
}

class ParentalOutboxTest {
    private val clock = RClock()
    private fun box(kv: KvStore = MemoryKv(), perPhone: Int = 40, maxBytes: Int = 200_000) = ReportOutbox(kv, clock::now, SecureRandom(), maxPerRecipient = perPhone, maxBytes = maxBytes, batch = 5)

    @Test fun deliversInOrderAndKeepsUntilAcknowledged() {
        val b = box()
        val ids = (1..3).map { b.enqueue(PHONE_A, "alert", """{"n":$it}""", KEY).id }
        val first = b.pending(PHONE_A)
        assertEquals(ids, first.map { it.id }, "oldest first")
        assertTrue(first.all { it.attempts == 1 })
        // retry: the link broke before the phone acknowledged -> the same reports come back, in the same order, with a higher attempt count
        val again = b.pending(PHONE_A)
        assertEquals(ids, again.map { it.id }); assertTrue(again.all { it.attempts == 2 })
        b.ack(PHONE_A, ids.take(2))
        assertEquals(listOf(ids[2]), b.pending(PHONE_A).map { it.id })
        b.ack(PHONE_A, listOf(ids[2]))
        assertEquals(0, b.count()); assertTrue(b.pending(PHONE_A).isEmpty())
    }

    @Test fun aPhoneCanOnlyAcknowledgeItsOwnReports() {
        val b = box()
        val a = b.enqueue(PHONE_A, "daily", "{}", KEY); val bb = b.enqueue(PHONE_B, "daily", "{}", KEY)
        b.ack(PHONE_B, listOf(a.id))
        assertEquals(1, b.count(PHONE_A)); assertEquals(1, b.count(PHONE_B))
        assertEquals(listOf(bb.id), b.pending(PHONE_B).map { it.id }, "no cross delivery")
    }

    @Test fun expiresAfterTheMaximumAge() {
        val b = box()
        b.enqueue(PHONE_A, "daily", "{}", KEY)
        clock.advanceMin(13 * 24 * 60); assertEquals(1, b.pending(PHONE_A).size)
        clock.advanceMin(2 * 24 * 60)
        assertTrue(b.pending(PHONE_A).isEmpty(), "older than 14 days: dropped")
        assertEquals(0, b.count())
    }

    @Test fun isBoundedPerPhoneKeepingTheNewest() {
        val b = box(perPhone = 4)
        val ids = (1..9).map { b.enqueue(PHONE_A, "alert", """{"n":$it}""", KEY).id }
        assertEquals(4, b.count(PHONE_A))
        assertEquals(ids.takeLast(4), b.all().map { it.id })
        b.enqueue(PHONE_B, "alert", "{}", KEY)
        assertEquals(4, b.count(PHONE_A), "another phone's reports do not push the first one out")
    }

    @Test fun isBoundedInTotalSize() {
        val b = box(maxBytes = 2_000)
        val big = "x".repeat(500)
        repeat(20) { b.enqueue(PHONE_A, "daily", """{"t":"$big"}""", KEY) }
        assertTrue(b.count() in 1..4, "size bound keeps only the newest few (was ${b.count()})")
    }

    @Test fun survivesARestartAndNeverStoresTheKey() {
        val kv = MemoryKv()
        val id = box(kv).enqueue(PHONE_A, "daily", """{"a":1}""", KEY).id
        assertEquals(listOf(id), box(kv).pending(PHONE_A).map { it.id })
        assertFalse(kv.dump().values.any { it.contains(KEY) }, "the signing key is not stored in the outbox")
    }

    @Test fun dropRecipientForgetsItsReports() {
        val b = box(); b.enqueue(PHONE_A, "daily", "{}", KEY); b.enqueue(PHONE_B, "daily", "{}", KEY)
        b.dropRecipient(PHONE_A); assertEquals(0, b.count(PHONE_A)); assertEquals(1, b.count(PHONE_B))
    }
}

class ParentalRecipientsTest {
    @Test fun nobodyReceivesByDefault() {
        val r = ReportRig()
        assertTrue(r.recipients.active().isEmpty())
        for (p in listOf(PHONE_A, PHONE_B, CHILD_PHONE)) assertFalse(r.recipients.isRecipient(p), "$p is trusted but not designated")
    }

    @Test fun designationNeedsThePinAndATrustedPhone() {
        val r = ReportRig()
        assertNotNull(r.recipients.designate(PHONE_A, pinVerified = false)); assertTrue(r.recipients.active().isEmpty())
        assertNotNull(r.recipients.designate("AA:BB:CC:DD:EE:77", pinVerified = true), "unknown phone")
        assertNotNull(r.recipients.designate("not an address", pinVerified = true))
        assertNull(r.recipients.designate(PHONE_A, pinVerified = true))
        assertTrue(r.recipients.isRecipient(PHONE_A)); assertFalse(r.recipients.isRecipient(CHILD_PHONE), "the child's phone is not designated by accident")
        // removal also needs the PIN
        assertNotNull(r.recipients.remove(PHONE_A, pinVerified = false)); assertTrue(r.recipients.isRecipient(PHONE_A))
        assertNull(r.recipients.remove(PHONE_A, pinVerified = true)); assertFalse(r.recipients.isRecipient(PHONE_A))
    }

    @Test fun aRevokedPhoneStopsBeingARecipient() {
        val r = ReportRig()
        r.recipients.designate(PHONE_A, true)
        r.engine.onEvent(ParentalEvent.Tamper("test"))
        assertEquals(1, r.outbox.count(PHONE_A))
        r.trust.revoke(PHONE_A)
        assertTrue(r.recipients.active().isEmpty())
        r.reports.cleanOrphans()
        assertEquals(0, r.outbox.count(), "what waited for a forgotten phone is dropped")
    }

    @Test fun atMostThreeAndANewKeyAtEachDesignation() {
        val r = ReportRig()
        r.trust.trust("AA:BB:CC:DD:EE:03", "C"); r.trust.trust("AA:BB:CC:DD:EE:04", "D")
        for (a in listOf(PHONE_A, PHONE_B, "AA:BB:CC:DD:EE:03")) assertNull(r.recipients.designate(a, true))
        assertNotNull(r.recipients.designate("AA:BB:CC:DD:EE:04", true))
        val k1 = r.recipients.get(PHONE_A)!!.key
        r.recipients.remove(PHONE_A, true); r.recipients.designate(PHONE_A, true)
        assertNotEquals(k1, r.recipients.get(PHONE_A)!!.key)
        assertEquals(64, k1.length)
    }

    @Test fun phoneIdIsOpaque() {
        val r = ReportRig()
        val id = r.recipients.phoneId(PHONE_A)
        assertFalse(id.contains("AA") && id.contains(":")); assertEquals(12, id.length)
        assertEquals(id, r.recipients.phoneId(PHONE_A.lowercase()))
        assertNotEquals(id, r.recipients.phoneId(PHONE_B))
    }
}

class ParentalReportServiceTest {
    private fun rigWithPhone(): ReportRig = ReportRig().also { it.recipients.designate(PHONE_A, true) }

    private fun bodies(r: ReportRig, kind: String) = r.outbox.all().filter { it.kind == kind }.map { JsonLite.obj(it.body) }

    @Test fun nothingIsQueuedWithoutADesignatedPhone() {
        val r = ReportRig()
        r.engine.appTick("a.x", "X", 60_000, r.env)
        r.engine.onEvent(ParentalEvent.Tamper("x")); r.clock.advanceMin(24 * 60)
        assertEquals(0, r.reports.tick()); assertEquals(0, r.outbox.count())
    }

    @Test fun dailySummaryLeavesOncePerDayAtTheChosenTime() {
        val r = rigWithPhone()                                    // 2025-06-15 15:06 UTC
        r.engine.appTick("a.video", "Vidéo", 12 * 60_000, r.env); r.engine.tick(UseKind.GAMES, 8 * 60_000)
        assertEquals(0, r.reports.tick(), "before 20:00")
        r.clock.advanceMin(5 * 60)                               // 20:06
        assertEquals(2 + 0, r.reports.tick() - (if (isSunday(r.clock.t)) 2 else 0), "one daily per profile")
        assertEquals(0, r.reports.tick(), "only once that day")
        val d = bodies(r, "daily").first { (it["profile"] as Map<*, *>)["id"] == "c1" }
        assertEquals("daily", d["type"]); assertEquals("2025-06-15", d["day"]); assertEquals(20L, d["totalMin"])
        @Suppress("UNCHECKED_CAST") assertEquals("Vidéo", (d["apps"] as List<Map<String, Any?>>)[0]["label"])
        r.clock.advanceMin(24 * 60)
        assertTrue(r.reports.tick() >= 2, "next day, next summary")
    }

    private fun isSunday(t: Long) = java.time.Instant.ofEpochMilli(t).atZone(ZoneId.of("UTC")).dayOfWeek == java.time.DayOfWeek.SUNDAY

    @Test fun parentChoosesTheTimeAndWeeklyDay() {
        val r = rigWithPhone()
        r.reports.saveConfig(ReportConfig(dailyAtMin = 18 * 60, weeklyDow = 7), null)   // 2025-06-15 is a Sunday
        assertTrue(isSunday(r.clock.t))
        r.clock.advanceMin(3 * 60)                               // 18:06
        val n = r.reports.tick()
        assertEquals(4, n, "2 daily + 2 weekly on a Sunday")
        val w = bodies(r, "weekly")[0]
        assertEquals("weekly", w["type"]); assertEquals(7, (w["days"] as List<*>).size)
        assertEquals(0, r.reports.tick())
        // not on another day
        r.clock.advanceMin(24 * 60)
        assertEquals(2, r.reports.tick(), "Monday: daily only")
    }

    @Test fun togglesPerProfile() {
        val r = rigWithPhone()
        r.reports.saveConfig(ReportConfig(profiles = mapOf("c2" to ProfileReportOptions(daily = false, weekly = false, alertLimit = false, alertBlocked = false)), weeklyDow = 7), null)
        r.clock.advanceMin(5 * 60)
        r.reports.tick()
        val profiles = bodies(r, "daily").map { (it["profile"] as Map<*, *>)["id"] }
        assertEquals(listOf("c1"), profiles, "Marc's daily summary is off")
        assertTrue(bodies(r, "weekly").all { (it["profile"] as Map<*, *>)["id"] == "c1" })
        // alerts of the active profile c1 are on
        r.engine.editApps { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)))) }
        r.engine.appTick("a.x", "Jeu", 15_000, r.env)
        assertEquals(1, bodies(r, "alert").size)
        // switch c1's blocked alert off
        r.reports.saveConfig(r.reports.config().copy(profiles = mapOf("c1" to ProfileReportOptions(alertBlocked = false))), null)
        r.clock.advanceMin(30)
        r.engine.appTick("a.x", "Jeu", 15_000, r.env)
        assertEquals(1, bodies(r, "alert").size, "toggle off: no new alert")
    }

    @Test fun immediateAlertsForLimitBlockedTamperAndNewApp() {
        val r = rigWithPhone()
        r.engine.edit { it.copy(profiles = listOf(ChildProfile("c1", "Léa", dailyLimitMin = 1), ChildProfile("c2", "Marc"))) }
        r.engine.editApps { it.copy(rules = mapOf("c1" to listOf(AppRule("a.blocked", AppState.BLOCKED)))) }
        r.engine.appTick("a.blocked", "Jeu X", 15_000, r.env)
        r.engine.appTick("a.ok", "Vidéo", 60_000, r.env); r.engine.appTick("a.ok", "Vidéo", 15_000, r.env)
        r.engine.reportSupervision(SupervisionInfo(SupervisionState.ACTIVE, "usage"))
        r.engine.reportSupervision(SupervisionInfo(SupervisionState.NOT_AUTHORIZED, detail = "accès retiré"))
        r.engine.syncInstalled(listOf(InstalledApp("a.old", "Vieux"))); r.engine.syncInstalled(listOf(InstalledApp("a.old", "Vieux"), InstalledApp("a.new", "Nouveau")))
        val types = bodies(r, "alert").map { it["alert"] }.toSet()
        assertEquals(setOf("blocked", "limit", "tamper", "newapp"), types)
        val blocked = bodies(r, "alert").first { it["alert"] == "blocked" }
        assertTrue((blocked["text"] as String).contains("Jeu X")); assertEquals("a.blocked", blocked["pkg"])
        assertEquals("Léa", ((blocked["profile"]) as Map<*, *>)["name"])
    }

    @Test fun alertsAreDeduplicatedAndCapped() {
        val r = rigWithPhone()
        r.engine.editApps { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)))) }
        repeat(10) { r.engine.appTick("a.x", "X", 15_000, r.env); r.clock.advanceMin(1) }
        assertEquals(1, bodies(r, "alert").size, "the same refusal within 10 minutes is sent once")
        // many different apps in an hour: capped
        repeat(30) { i -> r.engine.onEvent(ParentalEvent.AppBlocked("c1", "a.$i", "App $i", "bloquée")) }
        assertTrue(bodies(r, "alert").size <= ParentalReports.MAX_ALERTS_PER_HOUR)
    }

    @Test fun reportsNeverContainSecretsOrAddresses() {
        val r = rigWithPhone()
        r.engine.appTick("a.video", "Vidéo", 5 * 60_000, r.env)
        r.engine.onEvent(ParentalEvent.Tamper("accès retiré")); r.engine.onEvent(ParentalEvent.NewAppInstalled("a.n", "N"))
        r.reports.sendNow(); r.clock.advanceMin(5 * 60); r.reports.tick()
        assertTrue(r.outbox.count() >= 4)
        val key = r.recipients.get(PHONE_A)!!.key
        for (m in r.outbox.all()) {
            val low = m.body.lowercase()
            assertFalse(low.contains("pbkdf2")); assertFalse(low.contains("cbk_")); assertFalse(low.contains("\"pin\"")); assertFalse(low.contains("token"))
            assertFalse(m.body.contains("4821")); assertFalse(m.body.contains(key)); assertFalse(m.body.contains(PHONE_A)); assertFalse(m.body.contains("123456"))
            JsonLite.obj(m.body)
        }
    }

    @Test fun sendNowQueuesTodaysSummariesWhateverTheToggles() {
        val r = rigWithPhone()
        r.reports.saveConfig(ReportConfig(profiles = mapOf("c1" to ProfileReportOptions(daily = false), "c2" to ProfileReportOptions(daily = false))), null)
        assertEquals(2, r.reports.sendNow())
        assertEquals(0, ReportRig().reports.sendNow(), "nobody designated: nothing")
    }

    @Test fun configIsVersionedAndValidated() {
        val r = ReportRig()
        assertNotNull(r.reports.saveConfig(ReportConfig(), 0)); assertNull(r.reports.saveConfig(ReportConfig(), 0), "stale rev")
        assertFailsWith<IllegalArgumentException> { ReportConfig.fromMap(mapOf("dailyAtMin" to 2000)) }
        assertFailsWith<IllegalArgumentException> { ReportConfig.fromMap(mapOf("weeklyDow" to 9)) }
        assertFailsWith<IllegalArgumentException> { ReportConfig.fromMap(mapOf("profiles" to listOf(mapOf("id" to "../x")))) }
    }
}

class ParentalSyncTest {
    private class PipeLink(override val input: InputStream, override val output: OutputStream, private val onClose: () -> Unit) : Link { override fun close() = onClose() }

    /** An in-memory link served by the TV's BtProtocol.serve; [cutAfterReply]: the phone's link dies right after reading the TV's answer (no ack). */
    private fun connect(r: ReportRig, peer: String): Pair<Link, Thread> {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val tv = Thread {
            try { BtProtocol.serve(File(System.getProperty("java.io.tmpdir")), tvIn, s2c, null, peer, 0, parental = r.host) }
            catch (_: Exception) {} finally { runCatching { s2c.close() }; runCatching { tvIn.close() } }
        }.apply { isDaemon = true; start() }
        return PipeLink(clIn, c2s) { runCatching { c2s.close() }; runCatching { clIn.close() } } to tv
    }

    private fun sync(r: ReportRig, peer: String, inbox: ReportInbox, tvAddr: String = "11:22:33:44:55:66"): SyncResult {
        val (l, t) = connect(r, peer)
        try { return l.use { ReportSync.run(it, tvAddr, inbox) } } finally { t.join(3000) }
    }

    private val inboxClock = RClock()
    private fun inbox() = ReportInbox(MemoryInboxPersistence(), inboxClock::now)

    @Test fun designatedPhoneGetsKeyThenSignedReportsInOrder() {
        val r = ReportRig(); r.recipients.designate(PHONE_A, true)
        r.engine.onEvent(ParentalEvent.Tamper("un")); r.clock.advanceMin(11); r.engine.onEvent(ParentalEvent.Tamper("deux"))
        val box = inbox()
        val res = sync(r, PHONE_A, box)
        assertEquals(true, res.designated); assertEquals(2, res.stored.size); assertEquals(0, res.rejected)
        assertTrue(box.hasKey("11:22:33:44:55:66"))
        assertEquals(listOf("deux", "un"), box.history().map { it.body["text"] }, "newest first")
        assertEquals(0, r.outbox.count(), "acknowledged: gone from the TV")
        assertTrue(r.recipients.get(PHONE_A)!!.keyDelivered)
        assertEquals(0, sync(r, PHONE_A, box).stored.size, "nothing new")
        // the second visit does not resend the key
        assertFalse(r.host.fetch(PHONE_A).json.contains("\"key\""))
    }

    @Test fun trustedButNotDesignatedPhoneGetsNothingButItsIdentity() {
        val r = ReportRig(); r.recipients.designate(PHONE_A, true)
        r.engine.onEvent(ParentalEvent.Tamper("secret"))
        val box = inbox()
        val res = sync(r, CHILD_PHONE, box)
        assertEquals(false, res.designated); assertTrue(res.stored.isEmpty()); assertFalse(box.hasKey("11:22:33:44:55:66"))
        assertEquals(r.recipients.phoneId(CHILD_PHONE), res.phoneId, "it learns its own opaque id, to ask a parent to designate it")
        val raw = r.host.fetch(CHILD_PHONE).json
        assertFalse(raw.contains("reports")); assertFalse(raw.contains("secret")); assertFalse(raw.contains("key"))
        assertEquals(1, r.outbox.count(PHONE_A), "the other phone's reports are untouched")
    }

    @Test fun untrustedPeerIsRefused() {
        val r = ReportRig(); r.recipients.designate(PHONE_A, true)
        val (l, t) = connect(r, "AA:BB:CC:DD:EE:55")
        val e = assertFailsWith<BtProtocol.Refused> { l.use { ReportSync.run(it, "x", inbox()) } }
        t.join(3000)
        assertEquals(BtProtocol.ERR_UNTRUSTED, e.code)
        assertEquals(BtProtocol.ERR_UNTRUSTED, r.host.fetch("not an address").status)
        // a phone that was forgotten by the TV
        r.trust.revoke(PHONE_A); assertEquals(BtProtocol.ERR_UNTRUSTED, r.host.fetch(PHONE_A).status)
    }

    @Test fun brokenLinkBeforeTheAckMeansRedeliveryWithoutDuplicates() {
        val r = ReportRig(); r.recipients.designate(PHONE_A, true)
        r.engine.onEvent(ParentalEvent.Tamper("un"))
        val box = inbox()
        // phone reads the answer, stores it, then the link dies before the acknowledgement is sent
        val (l, t) = connect(r, PHONE_A)
        try {
            ParentalSyncProtocol.fetch(l.input, l.output) { reply ->
                reply["key"]?.let { box.setKey("11:22:33:44:55:66", it as String) }
                @Suppress("UNCHECKED_CAST") (reply["reports"] as List<Map<String, Any?>>).forEach { box.accept("11:22:33:44:55:66", it) }
                throw IOException("link lost")
            }
        } catch (_: IOException) {} finally { l.close(); t.join(3000) }
        assertEquals(1, box.history().size); assertEquals(1, r.outbox.count(), "not acknowledged: still on the TV")
        assertFalse(r.recipients.get(PHONE_A)!!.keyDelivered)
        val res = sync(r, PHONE_A, box)
        assertEquals(0, res.stored.size, "already stored: a duplicate"); assertEquals(1, box.history().size)
        assertEquals(0, r.outbox.count(), "now acknowledged")
    }

    @Test fun forgedReportsAreRejected() {
        val r = ReportRig(); r.recipients.designate(PHONE_A, true)
        r.engine.onEvent(ParentalEvent.Tamper("vrai"))
        val box = inbox()
        sync(r, PHONE_A, box)
        val real = box.history()[0]
        // another device on the LAN signs with a key it guessed, or without any
        val body = JsonLite.write(linkedMapOf("v" to 1, "type" to "alert", "alert" to "tamper", "text" to "faux", "tv" to "TV salon"))
        val forged = linkedMapOf("id" to "ff-1", "ts" to r.clock.t, "kind" to "alert", "body" to body, "mac" to ReportMac.sign("cd".repeat(32), "ff-1", r.clock.t, "alert", body))
        assertEquals(ReportInbox.Outcome.REJECTED, box.accept("11:22:33:44:55:66", forged))
        assertEquals(ReportInbox.Outcome.REJECTED, box.accept("11:22:33:44:55:66", forged - "mac"))
        // a genuine report altered in transit
        val key = r.recipients.get(PHONE_A)!!.key
        val ok = linkedMapOf("id" to "ok-1", "ts" to r.clock.t, "kind" to "alert", "body" to body, "mac" to ReportMac.sign(key, "ok-1", r.clock.t, "alert", body))
        assertEquals(ReportInbox.Outcome.REJECTED, box.accept("11:22:33:44:55:66", ok + ("body" to body.replace("faux", "autre"))))
        assertEquals(ReportInbox.Outcome.REJECTED, box.accept("AA:AA:AA:AA:AA:AA", ok), "unknown TV: no key")
        assertEquals(ReportInbox.Outcome.STORED, box.accept("11:22:33:44:55:66", ok))
        assertEquals(1, box.history().count { it.body["text"] == "vrai" }); assertEquals(real.id, box.history().first { it.body["text"] == "vrai" }.id)
    }

    @Test fun aPhoneCannotAcknowledgeAnotherPhonesReports() {
        val r = ReportRig(); r.recipients.designate(PHONE_A, true); r.recipients.designate(PHONE_B, true)
        r.engine.onEvent(ParentalEvent.Tamper("pour les deux"))
        assertEquals(1, r.outbox.count(PHONE_A)); assertEquals(1, r.outbox.count(PHONE_B))
        val idA = r.outbox.all().first { it.to == PHONE_A }.id
        r.host.ack(PHONE_B, listOf(idA), true)
        assertEquals(1, r.outbox.count(PHONE_A))
        r.host.ack(CHILD_PHONE, listOf(idA), true)                 // not a recipient at all
        assertEquals(1, r.outbox.count(PHONE_A))
    }

    @Test fun inboxIsBoundedAndPersistent() {
        val p = MemoryInboxPersistence(); val clock = RClock()
        val box = ReportInbox(p, clock::now, maxReports = 5, maxAgeMs = 10 * 24 * 3600_000L)
        box.setKey("11:22:33:44:55:66", KEY)
        fun env(i: Int, ts: Long = clock.t) = linkedMapOf("id" to "id$i", "ts" to ts, "kind" to "daily", "body" to """{"tv":"TV","n":$i}""").let {
            it + ("mac" to ReportMac.sign(KEY, "id$i", ts, "daily", it["body"] as String))
        }
        for (i in 1..8) { box.accept("11:22:33:44:55:66", env(i)); clock.advanceMin(1) }
        assertEquals(5, box.history().size); assertEquals("id8", box.history()[0].id)
        assertEquals(5, box.unread()); box.markAllRead(); assertEquals(0, box.unread())
        val again = ReportInbox(p, clock::now, maxReports = 5, maxAgeMs = 10 * 24 * 3600_000L)
        assertEquals(5, again.history().size); assertTrue(again.hasKey("11:22:33:44:55:66"))
        clock.advanceMin(11 * 24 * 60)
        assertTrue(again.history().isEmpty(), "retention counts from the day the phone received it (here 10 days)")
    }

    @Test fun anOlderTvAnswersErrMagic() {
        val out = ByteArrayOutputStream()
        val r = BtProtocol.serve(File(System.getProperty("java.io.tmpdir")), ByteArrayInputStream("CBTP\u0000\u0000".toByteArray()), out, null, "AA", 0, parental = null)
        assertEquals(BtProtocol.ERR_MAGIC, r); assertEquals(BtProtocol.ERR_MAGIC, out.toByteArray()[0].toInt())
    }
}

class ParentalAppsApiTest {
    private val rig = ReportRig()
    private val installed = mutableListOf(InstalledApp("com.netflix.ninja", "Netflix", AppCategory.VIDEO), InstalledApp("com.android.tv.settings", "Réglages"), InstalledApp("castbridge.receiver", "CastBridge-TV"))
    private val api = ParentalApi(rig.engine, installed = { installed }, appEnv = { AppEnv("castbridge.receiver") }, supervisionSetup = { mapOf("usageGranted" to false) },
        reports = rig.reports, trustedPhones = { listOf(PHONE_A to "Maman", CHILD_PHONE to "Léa") })

    private fun post(path: String, body: String, params: Map<String, String> = emptyMap()) = api.handleBody("/api/parental$path", "POST", params, body.toByteArray())!!
    private fun obj(r: castbridge.core.tv.ApiReply) = JsonLite.obj(r.json)
    init { rig.engine.editApps { it.copy(baselined = false) } }
    private val newRoutes = listOf("/apps/list", "/apps/rules/set", "/reports/config/get", "/reports/config/set", "/reports/recipients/add", "/reports/recipients/remove", "/reports/now")

    @Test fun newRoutesRefuseAPinInTheUrlEvenWhenCorrect() {
        for (route in newRoutes) for (k in listOf("pin", "ppin", "code", "new")) {
            val r = post(route, """{"pin":"4821"}""", mapOf(k to "4821"))
            assertEquals(400, r.status, "$route?$k=")
        }
        assertEquals(400, api.handle("/api/parental/supervision", "GET", mapOf("pin" to "4821"))!!.status)
        assertTrue(rig.recipients.active().isEmpty())
    }

    @Test fun newRoutesNeedTheParentalPin() {
        for (route in newRoutes) {
            assertEquals(403, post(route, """{"pin":"0001"}""").status, route)
            assertEquals(403, post(route, "{}").status, "$route without pin")
            rig.engine.verifyPin("4821")
        }
        assertTrue(rig.recipients.active().isEmpty())
    }

    @Test fun supervisionStatusIsReadableWithoutTheParentalPinAndHasNoSecret() {
        val r = api.handle("/api/parental/supervision", "GET", emptyMap())!!
        assertEquals(200, r.status)
        val o = obj(r)
        @Suppress("UNCHECKED_CAST") assertEquals("unauthorized", (o["supervision"] as Map<String, Any?>)["state"], "supervise is on but no detector has reported: never « active »")
        assertTrue(r.json.contains("usageGranted")); assertFalse(r.json.contains("pbkdf2"))
        assertEquals(405, api.handleBody("/api/parental/supervision", "POST", emptyMap(), "{}".toByteArray())!!.status.let { if (it == 404) 405 else it })
    }

    @Test fun appsListFlagsNeverBlockableAndNewApps() {
        val first = obj(post("/apps/list", """{"pin":"4821"}"""))
        @Suppress("UNCHECKED_CAST") val rows = (first["apps"] as List<Map<String, Any?>>).associateBy { it["pkg"] }
        assertEquals(true, rows["castbridge.receiver"]!!["never"]); assertEquals(false, rows["com.netflix.ninja"]!!["never"])
        assertEquals("video", rows["com.netflix.ninja"]!!["category"])
        assertTrue(rows.values.none { it["new"] == true })
        installed += InstalledApp("com.new.game", "Nouveau jeu")
        @Suppress("UNCHECKED_CAST") val again = (obj(post("/apps/list", """{"pin":"4821"}"""))["apps"] as List<Map<String, Any?>>).first { it["pkg"] == "com.new.game" }
        assertEquals(true, again["new"])
    }

    @Test fun rulesAreVersionedAndValidated() {
        val rev = rig.engine.appSettings().rev
        fun set(rev: Int, rules: String, extra: String = "") = post("/apps/rules/set", """{"pin":"4821","rev":$rev,"settings":{"supervise":true,"newApp":"block"$extra,"rules":$rules}}""")
        val ok = set(rev, """[{"profile":"c1","apps":[{"pkg":"com.netflix.ninja","state":"limit","limitMin":30},{"pkg":"com.android.chrome","state":"block"}]}]""")
        assertEquals(200, ok.status, ok.json)
        assertEquals(AppState.LIMITED, rig.engine.appSettings().rule("c1", "com.netflix.ninja")!!.state)
        assertTrue("com.android.chrome" in rig.engine.appSettings().known, "an app with a rule is reviewed")
        assertEquals(409, set(rev, "[]").status, "stale rev")
        val now = rig.engine.appSettings().rev
        assertEquals(400, set(now, """[{"profile":"c1","apps":[{"pkg":"a b","state":"block"}]}]""").status)
        assertEquals(400, set(now, """[{"profile":"c1","apps":[{"pkg":"a.b","state":"limit"}]}]""").status, "limit needs minutes")
        assertEquals(400, set(now, """[{"profile":"c1","apps":[{"pkg":"a.b","state":"nope"}]}]""").status)
        assertEquals(400, set(now, """[{"profile":"c1","apps":[{"pkg":"a.b","state":"block"},{"pkg":"a.b","state":"allow"}]}]""").status)
        assertEquals(400, set(now, """[{"profile":"../x","apps":[]}]""").status)
        assertEquals(now, rig.engine.appSettings().rev, "nothing changed by refused calls")
        // reviewing a new app without a rule
        rig.engine.editApps { it.copy(news = listOf(NewApp("com.n", "N", 1))) }
        assertEquals(200, set(rig.engine.appSettings().rev, "[]", ""","reviewed":["com.n"]""").status)
        assertTrue(rig.engine.appSettings().news.isEmpty())
    }

    @Test fun recipientRoutesNeedPinAndTrustedPhone() {
        val phones = obj(post("/reports/config/get", """{"pin":"4821"}"""))
        @Suppress("UNCHECKED_CAST") val list = phones["phones"] as List<Map<String, Any?>>
        assertTrue(list.none { it["designated"] == true }, "nobody by default, the child's phone included")
        assertFalse(phones.toString().contains(PHONE_A) || phones.toString().contains(CHILD_PHONE), "no Bluetooth address in the API")
        val idA = list.first { it["name"] == "Maman" }["id"] as String
        assertEquals(404, post("/reports/recipients/add", """{"pin":"4821","phoneId":"000000000000"}""").status)
        assertEquals(403, post("/reports/recipients/add", """{"pin":"0001","phoneId":"$idA"}""").status); assertTrue(rig.recipients.active().isEmpty())
        rig.engine.verifyPin("4821")
        assertEquals(200, post("/reports/recipients/add", """{"pin":"4821","phoneId":"$idA"}""").status)
        assertTrue(rig.recipients.isRecipient(PHONE_A)); assertFalse(rig.recipients.isRecipient(CHILD_PHONE))
        rig.engine.onEvent(ParentalEvent.Tamper("x")); assertEquals(1, rig.outbox.count(PHONE_A))
        assertEquals(403, post("/reports/recipients/remove", """{"pin":"0001","phoneId":"$idA"}""").status); assertTrue(rig.recipients.isRecipient(PHONE_A))
        rig.engine.verifyPin("4821")
        assertEquals(200, post("/reports/recipients/remove", """{"pin":"4821","phoneId":"$idA"}""").status)
        assertFalse(rig.recipients.isRecipient(PHONE_A)); assertEquals(0, rig.outbox.count(), "its reports are dropped with it")
    }

    @Test fun reportConfigRoundTripAndReportNow() {
        val got = obj(post("/reports/config/get", """{"pin":"4821"}"""))
        @Suppress("UNCHECKED_CAST") val cfg = got["config"] as Map<String, Any?>
        assertEquals(2, (cfg["profiles"] as List<*>).size, "one entry per profile")
        val modified = JsonLite.write(linkedMapOf("pin" to "4821", "rev" to cfg["rev"], "config" to cfg + ("dailyAtMin" to 19 * 60)))
        assertEquals(200, post("/reports/config/set", modified).status)
        assertEquals(19 * 60, rig.reports.config().dailyAtMin)
        assertEquals(409, post("/reports/config/set", modified).status)
        assertEquals(0, (obj(post("/reports/now", """{"pin":"4821"}"""))["queued"] as Number).toInt(), "nobody designated")
    }

    @Test fun statusKeepsOldFieldsAndAddsSupervision() {
        val o = obj(api.handle("/api/parental", "GET", emptyMap())!!)
        for (k in listOf("pinSet", "enabled", "rev", "active", "profiles", "locked", "retryAfter", "sessionLeftSec", "supervision")) assertTrue(o.containsKey(k), k)
    }

    @Test fun oldPhoneSavingTheConfigKeepsTheAppRules() {
        rig.engine.editApps { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)))) }
        val old = JsonLite.write(linkedMapOf("pin" to "4821", "rev" to rig.engine.config().rev, "config" to rig.engine.config().toMap()))
        assertEquals(200, post("/config/set", old).status)
        assertEquals(AppState.BLOCKED, rig.engine.appSettings().rule("c1", "a.x")!!.state)
    }
}

class ParentalReportTextTest {
    private fun rep(kind: String, body: Map<String, Any?>) = StoredReport("i", "TV", 1, kind, body)

    @Test fun sentences() {
        assertEquals("1 h 05", ReportText.minutes(65)); assertEquals("45 min", ReportText.minutes(45))
        val alert = rep("alert", mapOf("alert" to "blocked", "text" to "Léa a essayé d'ouvrir « Jeu »"))
        assertEquals("Application bloquée", ReportText.title(alert)); assertEquals("Léa a essayé d'ouvrir « Jeu »", ReportText.text(alert))
        val daily = rep("daily", mapOf("profile" to mapOf("name" to "Léa"), "totalMin" to 80, "apps" to listOf(mapOf("label" to "YouTube", "min" to 50), mapOf("label" to "Jeu", "min" to 20)),
            "blocked" to listOf(mapOf("what" to "x")), "supervision" to mapOf("state" to "unauthorized", "label" to "Surveillance de toute la TV : non autorisée")))
        assertEquals("Rapport du jour : Léa", ReportText.title(daily))
        val t = ReportText.text(daily)
        assertTrue(t.startsWith("1 h 20 d'écran : YouTube 50 min, Jeu 20 min")); assertTrue(t.contains("1 blocage(s)")); assertTrue(t.contains("non autorisée"))
        assertNull(ReportText.notification(emptyList()))
        assertEquals("2 nouveaux rapports parentaux", ReportText.notification(listOf(alert, daily))!!.first)
    }
}

/** The new routes through the real server: still behind the TV's own PIN, and the client of the phone parses them. */
class ParentalWholeTvHttpTest {
    private val dir = kotlin.io.path.createTempDirectory("parental-whole").toFile()
    private val port = java.net.ServerSocket(0).use { it.localPort }
    private val rig = ReportRig()
    private val api = ParentalApi(rig.engine, installed = { listOf(InstalledApp("com.netflix.ninja", "Netflix", AppCategory.VIDEO)) },
        supervisionSetup = { mapOf("usageGranted" to false) }, reports = rig.reports, trustedPhones = { listOf(PHONE_A to "Maman") })
    private val server = castbridge.core.tv.ReceiverServer(dir, FakePlayer(), port, pin = "123456", extension = api).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun code(method: String, path: String, tvPin: String?): Int {
        val c = java.net.URI(base + path).toURL().openConnection() as java.net.HttpURLConnection
        c.requestMethod = method; tvPin?.let { c.setRequestProperty("X-CB-Pin", it) }
        return c.responseCode
    }

    @Test fun supervisionRouteIsBehindTheTvPin() {
        assertEquals(401, code("GET", "/api/parental/supervision", null))
        assertEquals(200, code("GET", "/api/parental/supervision", "123456"))
    }

    @Test fun phoneClientUsesTheNewRoutes() {
        val c = ParentalClient(base, "123456")
        @Suppress("UNCHECKED_CAST") assertEquals("unauthorized", (c.supervision()["supervision"] as Map<String, Any?>)["state"])
        val apps = c.apps("4821")
        assertEquals("Netflix", apps.apps.single().label)
        val saved = c.saveApps("4821", apps.settings.copy(supervise = true, rules = mapOf("c1" to listOf(AppRule("com.netflix.ninja", AppState.LIMITED, 30)))))
        assertEquals(30, saved.rule("c1", "com.netflix.ninja")!!.limitMin)
        assertEquals(409, assertFailsWith<ParentalError> { c.saveApps("4821", apps.settings) }.code, "stale rev")
        assertEquals(403, assertFailsWith<ParentalError> { c.apps("0001") }.code)
        val rl = c.reportsConfig("4821")
        assertEquals(listOf("Maman"), rl.phones.map { it.name }); assertFalse(rl.phones[0].designated)
        val after = c.designate("4821", rl.phones[0].id)
        assertTrue(after.phones[0].designated)
        assertTrue(rig.recipients.isRecipient(PHONE_A))
        assertEquals(1, c.reportNow("4821").coerceAtLeast(0).let { if (it >= 1) 1 else 0 })
        assertFalse(c.removeRecipient("4821", rl.phones[0].id).phones[0].designated)
        assertEquals(0, rig.outbox.count())
    }
}
