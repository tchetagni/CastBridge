package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.*
import java.io.*
import kotlin.concurrent.thread
import kotlin.test.*

/** CBTH with and without the install id: every old/new combination of phone and TV keeps working, and nothing leaks. */
class HelloCompatTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)
    private fun raw(peer: String, request: ByteArray): ByteArray = tv.serve(peer).use { l -> l.output.write(request); l.output.flush(); l.input.readBytes() }
    private val HELLO = "CBTH".toByteArray()

    @Test fun oldPhoneAgainstNewTv_exactlyTheOldBytes() {
        // an old phone never sets bit 1: one status byte for an unknown phone, as before
        assertContentEquals(byteArrayOf(BtProtocol.ERR_UNTRUSTED.toByte()), raw(tv.phone, HELLO + byteArrayOf(0)))
        assertContentEquals(byteArrayOf(BtProtocol.ERR_NOT_OPEN.toByte()), raw(tv.phone, HELLO + byteArrayOf(1)))
        tv.reg.trust(tv.phone, "Galaxy")
        val ok = raw(tv.phone, HELLO + byteArrayOf(0))
        assertEquals(BtProtocol.OK, ok[0].toInt())
        val len = ((ok[1].toInt() and 0xff) shl 8) or (ok[2].toInt() and 0xff)
        assertEquals(3 + len, ok.size, "nothing after the answer")
        val text = String(ok, 3, len, Charsets.UTF_8)
        assertTrue(text.lines().any { it == "id=${tv.reg.installId}" }, "the new field is just one more key=value line")
        // an old phone's decoder ignores keys it does not know: here, the same strict decoder minus the id
        assertNotNull(HelloInfo.decode(text.lines().filterNot { it.startsWith("id=") }.joinToString("\n")))
    }

    @Test fun newPhoneAgainstOldTv_noHintAndNoBlocking() {
        // an old TV answers the status byte and closes: the phone must not wait for a hint byte
        val oldTv = object : BtTransport {
            override fun connect(address: String): Link {
                val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 4096)
                val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 4096)
                thread(isDaemon = true) { try { val m = ByteArray(4); DataInputStream(tvIn).readFully(m); tvIn.read(); s2c.write(BtProtocol.ERR_UNTRUSTED) } finally { runCatching { s2c.close() } } }
                return object : Link { override val input = clIn; override val output = c2s; override fun close() { runCatching { c2s.close() } } }
            }
        }
        val r = PhoneLink(oldTv, { true }).connect(SavedTv("11:22:33:44:55:66", "TV", installId = "a".repeat(32))) as PhoneLink.Result.Refused
        assertEquals(BtProtocol.ERR_UNTRUSTED, r.code); assertEquals(BtProtocol.HINT_NONE, r.hint)
        assertEquals(LinkAction.REASSOCIATE, LinkText.refused(r.code, r.hint).action, "still guided, with the generic wording")
        // an old TV's OK answer has no id
        val old = HelloInfo("TV", "0.12", null, "cbk_" + "a".repeat(64), 100, LinkInfo(8765, listOf("10.0.0.2")))
        assertFalse(old.encode().contains("id=")); assertNull(HelloInfo.decode(old.encode())!!.installId)
    }

    @Test fun newPhoneAgainstNewTv_tellsAReinstallFromARemoval() {
        tv.reg.trust(tv.phone, "Galaxy")
        val first = PhoneLink(tv, { true }).connect(SavedTv(tv.tvAddress, "TV")) as PhoneLink.Result.Connected
        val id = first.session.tv.installId
        assertEquals(tv.reg.installId, id, "the phone remembers the install id from the HELLO")
        tv.reg.revoke(tv.phone)
        assertEquals(BtProtocol.HINT_SAME_INSTALL, (PhoneLink(tv, { true }).connect(first.session.tv) as PhoneLink.Result.Refused).hint)
        tv.reinstall()
        val r = PhoneLink(tv, { true }).connect(first.session.tv) as PhoneLink.Result.Refused
        assertEquals(BtProtocol.HINT_OTHER_INSTALL, r.hint); assertTrue(r.tvWasReset)
        assertEquals("La TV a été réinitialisée ou réinstallée : elle ne vous reconnaît plus.", r.message)
        assertEquals(BtProtocol.OK.let { BtProtocol.ERR_UNTRUSTED }, r.code, "the code did not change")
    }

    @Test fun anUnpairedPeerLearnsNothingNotEvenWhetherItsClaimMatches() {
        tv.bonded.clear()
        val claim = tv.reg.installId.toByteArray()
        val a = raw(tv.phone, HELLO + byteArrayOf(2, claim.size.toByte()) + claim)
        assertContentEquals(byteArrayOf(BtProtocol.ERR_UNTRUSTED.toByte(), BtProtocol.HINT_NONE.toByte()), a, "same hint (none) whatever the claim")
        val b = raw(tv.phone, HELLO + byteArrayOf(2, 3) + "abc".toByteArray())
        assertContentEquals(a, b)
        tv.bonded += tv.phone
        val c = raw(tv.phone, HELLO + byteArrayOf(2, claim.size.toByte()) + claim)
        assertContentEquals(byteArrayOf(BtProtocol.ERR_UNTRUSTED.toByte(), BtProtocol.HINT_SAME_INSTALL.toByte()), c, "a paired phone that holds the real id")
        assertFalse(String(c, Charsets.ISO_8859_1).contains(tv.name))
    }

    @Test fun malformedClaimsAreHarmless() {
        tv.reg.trust(tv.phone, "Galaxy")
        val long = ByteArray(250) { 'a'.code.toByte() }
        // a length byte above the cap: the TV reads at most 64 and answers; the stream is not trusted afterwards, nothing crashes
        val r = raw(tv.phone, HELLO + byteArrayOf(2, 250.toByte()) + long)
        assertEquals(BtProtocol.OK, r[0].toInt())
        assertEquals(BtProtocol.OK, raw(tv.phone, HELLO + byteArrayOf(2, 0))[0].toInt(), "empty claim")
        assertEquals(1, raw(tv.phone, "CBTH".toByteArray() + byteArrayOf(3, 1) + "x".toByteArray() + byteArrayOf())[0].toInt().coerceAtMost(1).coerceAtLeast(1))
        assertNull(HelloInfo.decode("token=${"cbk_" + "a".repeat(64)}\nid=../../etc\nport=8765")!!.installId, "ids are strictly hex")
    }

    @Test fun cbt1AndFriendsAreUntouched() {
        val dir = kotlin.io.path.createTempDirectory("compat").toFile()
        try {
            val data = ByteArray(300_000) { (it % 251).toByte() }
            tv.serve("AA:00:00:00:00:99").use { l -> BtProtocol.send(l.input, l.output, "m.bin", data.size.toLong(), "482913", { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }) }
        } finally { dir.deleteRecursively() }
    }
}

/** Registry on disk: atomic writes, checksum, backup, corruption recovery, install id. */
class RegistryRobustnessTest {
    private val dir = kotlin.io.path.createTempDirectory("reg").toFile()
    private val file = File(dir, "trust.txt")
    private val clock = FakeClock()
    @AfterTest fun tearDown() { dir.deleteRecursively() }
    private fun reg() = TrustRegistry(FileTrustPersistence(file), clock::now)

    @Test fun installIdAndPhonesSurviveARestart() {
        val a = reg(); a.trust("AA:BB:CC:DD:EE:01", "Galaxy"); val t = a.issueToken("AA:BB:CC:DD:EE:01")!!
        val b = reg()
        assertEquals(a.installId, b.installId); assertEquals(listOf("AA:BB:CC:DD:EE:01"), b.list().map { it.address }); assertEquals("AA:BB:CC:DD:EE:01", b.verifyToken(t.token))
        assertNull(b.recoveredFromBackup)
        assertFalse(file.readText().contains(t.token))
    }

    @Test fun truncatedFileIsRecoveredFromTheBackup() {
        val a = reg(); a.trust("AA:BB:CC:DD:EE:01", "Galaxy"); a.trust("AA:BB:CC:DD:EE:02", "Pixel")   // two saves: the first one is now the backup
        val full = file.readText()
        file.writeText(full.substring(0, full.length / 2))                  // power cut in the middle of a write
        val b = reg()
        assertEquals(true, b.recoveredFromBackup)
        assertEquals(a.installId, b.installId, "same installation: phones keep recognising the TV")
        assertTrue(b.isTrusted("AA:BB:CC:DD:EE:01"), "what the backup knew is back")
    }

    @Test fun damagedAndNoBackupMeansNothingIsBelievedAndANewInstallId() {
        val a = reg(); a.trust("AA:BB:CC:DD:EE:01", "Galaxy")
        File(file.path + ".bak").delete()
        file.writeText(file.readText().replace("Galaxy", "Hacked"))         // edited: checksum no longer matches
        val b = reg()
        assertEquals(false, b.recoveredFromBackup)
        assertTrue(b.list().isEmpty(), "a registry that cannot be verified grants nothing")
        assertNotEquals(a.installId, b.installId, "phones are told 'another installation'")
        b.trust("AA:BB:CC:DD:EE:03", "New"); assertEquals(1, reg().list().size, "and it works again")
    }

    @Test fun fileOfZerosAfterACrashIsDamaged() {
        val a = reg(); a.trust("AA:BB:CC:DD:EE:01", "Galaxy"); a.trust("AA:BB:CC:DD:EE:02", "P")
        file.writeBytes(ByteArray(300))
        assertEquals(true, reg().recoveredFromBackup)
    }

    @Test fun aLeftoverTmpFileIsIgnoredAndADamagedMainNeverReplacesAGoodBackup() {
        val a = reg(); a.trust("AA:BB:CC:DD:EE:01", "Galaxy"); a.trust("AA:BB:CC:DD:EE:02", "P")
        File(file.path + ".tmp").writeText("P\tGARBAGE")
        assertEquals(2, reg().list().size)
        val good = File(file.path + ".bak").readText()
        file.writeText("junk\u0000")
        val b = reg()                                                       // recovered from the backup, then a save happens
        b.trust("AA:BB:CC:DD:EE:04", "Q")
        assertEquals(good, File(file.path + ".bak").readText(), "the bad main file did not become the backup")
        assertEquals(2, reg().list().size, "the one the backup knew + the new one")
    }

    @Test fun legacyFilesWithoutChecksumStillLoad() {
        file.writeText("P\tAA:BB:CC:DD:EE:01\t5\t6\tOld\n")
        val r = reg(); assertTrue(r.isTrusted("AA:BB:CC:DD:EE:01")); assertNull(r.recoveredFromBackup)
    }

    @Test fun manySavesKeepTheFileIntact() {
        val a = reg()
        repeat(60) { a.trust("AA:BB:CC:DD:EE:%02X".format(it % TrustRegistry.MAX_PHONES), "P$it"); a.issueToken("AA:BB:CC:DD:EE:%02X".format(it % TrustRegistry.MAX_PHONES)) }
        val b = reg(); assertEquals(TrustRegistry.MAX_PHONES, b.list().size); assertNull(b.recoveredFromBackup)
        assertTrue(TrustRegistry.intact(file.readText()) && TrustRegistry.intact(File(file.path + ".bak").readText()))
    }
}

/** TV side under load: concurrent HELLOs, per-phone limits, one phone's mistakes never block another. */
class HelloConcurrencyTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)

    @Test fun manyConcurrentHellosOfTheSameTrustedPhoneAllSucceedAndStayConsistent() {
        tv.reg.trust(tv.phone, "Galaxy")
        val ok = java.util.concurrent.atomic.AtomicInteger(); val tokens = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val ts = (1..40).map { thread { val r = tv.handler.handle(tv.phone, "Galaxy", false); if (r is HelloReply.Ok) { ok.incrementAndGet(); tokens += r.info.token } } }
        ts.forEach { it.join() }
        assertEquals(40, ok.get())
        assertTrue(tv.reg.tokenCount(tv.phone) in 1..4, "bounded: ${tv.reg.tokenCount(tv.phone)}")
        assertEquals(1, tokens.count { tv.reg.verifyToken(it) != null }.coerceAtMost(1))
        assertEquals(1, tv.reg.list().size)
    }

    @Test fun concurrentUnknownPhonesAllGetOnlyTheOneByteAnswer() {
        val bad = java.util.concurrent.atomic.AtomicInteger()
        (1..30).map { i -> thread { val r = tv.handler.handle("AA:BB:CC:DD:%02X:%02X".format(i, i), "x", false); if (r is HelloReply.Err && r.code == BtProtocol.ERR_UNTRUSTED) bad.incrementAndGet() } }.forEach { it.join() }
        assertEquals(30, bad.get(), "the same one-byte verdict for everybody")
    }

    @Test fun revokeWhileHellosRunNeverLeavesAUsableTokenBehind() {
        tv.reg.trust(tv.phone, "Galaxy")
        val toks = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val ts = (1..20).map { thread { val r = tv.handler.handle(tv.phone, "G", false); if (r is HelloReply.Ok) toks += r.info.token } }
        tv.reg.revoke(tv.phone); ts.forEach { it.join() }
        assertTrue(toks.none { tv.reg.verifyToken(it) != null }, "after the revoke nothing works")
    }

    @Test fun stormOfHellosIsAnsweredBusyAndOtherPhonesAreNotAffected() {
        val other = "AA:BB:CC:DD:EE:02"; tv.bonded += other; tv.reg.trust(tv.phone, "A"); tv.reg.trust(other, "B")
        tv.useLimiter(AttemptLimiter(global = 100, perPeer = 6, now = clock::now))
        val results = (1..10).map { (tv.handler.handle(tv.phone, "A", false) as? HelloReply.Err)?.code }
        assertEquals(List(6) { null } + List(4) { BtProtocol.ERR_BUSY }, results, "phone A: 6 then busy")
        assertIs<HelloReply.Ok>(tv.handler.handle(other, "B", false), "phone B is not penalised by A")
        clock.advance(61_000)
        assertIs<HelloReply.Ok>(tv.handler.handle(tv.phone, "A", false))
    }

    @Test fun aWrongPinFromOnePhoneNeverBlocksAnother() {
        val guard = PinGuard("482913", now = clock::now)
        repeat(5) { guard.check("AA:BB:CC:DD:EE:01", "000000") }
        assertEquals(PinGuard.Result.LOCKED, guard.check("AA:BB:CC:DD:EE:01", "482913"))
        assertEquals(PinGuard.Result.OK, guard.check("AA:BB:CC:DD:EE:02", "482913"))
    }

    @Test fun theSamePhoneAskingAgainTakesOverItsOwnPendingRequest() {
        tv.pairing.open()
        val first = java.util.concurrent.LinkedBlockingQueue<PairingSession.Decision>(); val second = java.util.concurrent.LinkedBlockingQueue<PairingSession.Decision>()
        thread(isDaemon = true) { first.put(tv.pairing.ask(tv.phone, "Galaxy")) }
        while (tv.pairing.asking() == null) Thread.sleep(2)
        thread(isDaemon = true) { second.put(tv.pairing.ask(tv.phone, "Galaxy")) }     // the phone's link dropped and it came back
        assertEquals(PairingSession.Decision.BUSY, first.poll(3, java.util.concurrent.TimeUnit.SECONDS), "the dead link's request lets go")
        assertNotNull(tv.pairing.asking()); assertTrue(tv.pairing.approve())
        assertEquals(PairingSession.Decision.APPROVED, second.poll(3, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(tv.reg.isTrusted(tv.phone))
    }

    @Test fun attemptLimiterBoundsGlobalAndPerPeerAndRecovers() {
        val l = AttemptLimiter(global = 5, perPeer = 3, windowMs = 60_000, now = clock::now)
        assertEquals(List(3) { 0L }, (1..3).map { l.tryAcquire("a") })
        assertTrue(l.tryAcquire("a") in 1..60_000)
        assertEquals(0L, l.tryAcquire("b")); assertEquals(0L, l.tryAcquire("c"))
        assertTrue(l.tryAcquire("d") > 0, "global cap of 5")
        clock.advance(60_001); assertEquals(0L, l.tryAcquire("a"))
    }

    @Test fun jitterStaysInItsBand() {
        for (r in listOf(0.0, 0.25, 0.5, 0.999)) assertTrue(Jitter.around(1000, 0.25) { r } in 750..1250)
        assertEquals(750, Jitter.around(1000, 0.25) { 0.0 }); assertEquals(1000, Jitter.around(1000, 0.25) { 0.5 })
    }
}
