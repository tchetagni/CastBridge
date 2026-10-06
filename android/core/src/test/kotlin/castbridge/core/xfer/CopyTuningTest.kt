package castbridge.core.xfer

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/** R-20 : réglages purs de la copie (notifications, attentes, profil de la TV), reprise sans re-hachage, fsync périodique, refus code 8. */
class CopyTuningTest {
    private val mib = 1L shl 20

    // ---- notifications de la file
    @Test fun aStalledCopyPostsOnce() {
        val g = NotificationGate()
        assertTrue(g.shouldPost("« a.avi »", 10, 0))
        for (t in 1..60) assertFalse(g.shouldPost("« a.avi »", 10, t * 1_000L), "second $t")
        assertEquals(1, g.posted)
    }

    @Test fun aChangeIsPostedButSpaced() {
        val g = NotificationGate()
        assertTrue(g.shouldPost("x", 1, 0))
        assertFalse(g.shouldPost("x", 2, 500))            // moins d'une seconde
        assertFalse(g.shouldPost("x", 2, 1_500))          // pourcentage seul : 2 s
        assertTrue(g.shouldPost("x", 2, 2_000))
        assertFalse(g.shouldPost("y", 2, 2_500))          // texte : 1 s
        assertTrue(g.shouldPost("y", 2, 3_000))
        g.reset(); assertTrue(g.shouldPost("y", 2, 3_001))
    }

    @Test fun neverMoreThanTheOldTwoSecondTick() {
        val g = NotificationGate()
        var posts = 0
        for (t in 0..120) if (g.shouldPost("x", t, t * 1_000L)) posts++      // un pourcentage nouveau chaque seconde
        assertTrue(posts <= 61, "posts=$posts")
    }

    // ---- attentes bornées
    @Test fun backoffGrowsAndIsBounded() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..5).map { Backoff.delayMs(it) })
        assertEquals(100L, Backoff.delayMs(0, 100, 3_000)); assertEquals(3_000L, Backoff.delayMs(9, 100, 3_000)); assertEquals(30_000L, Backoff.delayMs(500))
    }

    // ---- profil de la TV dans les caps
    @Test fun tvProfileOnlyShrinksTheBlocks() {
        assertEquals(4 shl 20, CopyTuning.blockSize(348 * mib, null))                  // TV 0.14.37 : rien d'annoncé
        assertEquals(1 shl 20, CopyTuning.blockSize(348 * mib, 1 shl 20))              // ressources faibles
        assertEquals(4 shl 20, CopyTuning.blockSize(348 * mib, 16 shl 20))             // jamais plus gros que la politique
        assertEquals(1 shl 20, CopyTuning.blockSize(348 * mib, 100_000))               // borné à 1 Mo
        assertEquals(2, CopyTuning.streams(2, 6)); assertEquals(1, CopyTuning.streams(0, 0)); assertEquals(6, CopyTuning.streams(6, 8))
    }

    private class Stub(val caps: () -> TransferApi.Caps?) : TransferApi {
        var capsCalls = 0; var beginManifest: Manifest? = null
        override fun caps(): TransferApi.Caps? { capsCalls++; return caps.invoke() }
        override fun begin(m: Manifest, target: String?, discard: Boolean): TransferApi.Begin { beginManifest = m; return TransferApi.Begin(true, m.id, BlockMap(m.blocks), emptyList(), 0, 4, "") }
        override fun state(id: String, withHashes: Boolean): TransferApi.Begin? = null
        override fun finish(id: String, root: String): TransferApi.Finish = TransferApi.Finish.Done
        override fun abort(id: String) {}
    }
    private object NoSrc : BlockSource { override val size = 348L shl 20; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int) = len }

    @Test fun capsBlockSizeReachesTheManifest() {
        val low = Stub { TransferApi.Caps(1, 2, 1 shl 20) }
        assertEquals(TransferClient.Result.Done, TransferClient(low, NoSrc, "a.avi", { _, _ -> emptyList() }).run())
        assertEquals(1 shl 20, low.beginManifest!!.blockSize)
        val old = Stub { TransferApi.Caps(1, 6) }
        TransferClient(old, NoSrc, "a.avi", { _, _ -> emptyList() }).run()
        assertEquals(4 shl 20, old.beginManifest!!.blockSize)
    }

    @Test fun anUnreachableTvIsAskedAFiniteNumberOfTimesWithGrowingWaits() {
        val api = Stub { throw IOException("TV introuvable") }
        val waits = ArrayList<Long>()
        var now = 0L
        val r = TransferClient(api, NoSrc, "a.avi", { _, _ -> emptyList() }, sleep = { waits += it; now += it }, clock = { now }).run()
        assertTrue(r is TransferClient.Result.Failed && r.reason == castbridge.core.tv.TvWait.GAVE_UP_TEXT, r.toString())
        assertTrue(api.capsCalls in 10..40, "appels : ${api.capsCalls}")
        assertTrue(waits.all { it <= 30_000 } && waits.zipWithNext().all { (a, b) -> b >= a }, waits.toString())
        assertTrue(waits.sum() in CopyTuning.MAX_UNREACHABLE_MS..CopyTuning.MAX_UNREACHABLE_MS + 30_000, "environ 10 minutes : ${waits.sum()}")
    }

    // ---- la reprise ne relit que ce qui manque
    @Test fun resumeNeverRehashesBlocksTheTvAlreadyHolds() {
        val m = Manifest("a.avi", 348 * mib, 4 shl 20)                           // 87 blocs
        val reads = AtomicLong()
        val src = object : BlockSource { override val size = m.size; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int { reads.addAndGet(len.toLong()); java.util.Arrays.fill(buf, off, off + len, 7); return len } }
        val book = HashBook(m, src)
        val held = 80
        for (i in 0 until held) book.preload(i, "ab".repeat(32))
        book.all()
        assertEquals((held until m.blocks).sumOf { m.length(it).toLong() }, reads.get(), "seuls les blocs manquants sont lus")
        assertTrue(reads.get() < m.size / 8)
    }

    // ---- fsync périodique côté TV
    private fun block(m: Manifest, i: Int): Pair<ByteArray, String> { val b = ByteArray(m.length(i)) { (it + i).toByte() }; return b to Hash.hex(Hash.sha256(b)) }

    private fun syncsFor(every: Long, blocks: Int = 8): Int {
        val dir = kotlin.io.path.createTempDirectory("cbsync").toFile()
        try {
            val m = Manifest("a.bin", blocks * mib, mib.toInt())
            val a = PartAssembler.open(dir, m, syncEveryBytes = { every })
            for (i in 0 until m.blocks) { val (b, sha) = block(m, i); assertEquals(PartAssembler.Block.Ok, a.writeBlock(i, sha, b.inputStream(), b.size.toLong(), false)) }
            Thread.sleep(300)
            val n = a.syncs.get(); a.close(); return n
        } finally { dir.deleteRecursively() }
    }

    @Test fun periodicSyncIsRareAndNeverPerBlock() {
        assertEquals(0, syncsFor(0))
        val n = syncsFor(2 * mib)
        assertTrue(n in 1..4, "8 blocs de 1 Mo, un fsync tous les 2 Mo : $n (jamais 8)")
        assertTrue(syncsFor(64 * mib) == 0, "sous le seuil : aucun fsync avant la fin")
    }

    // ---- code 8 : attentes, un seul message, appareils éligibles
    @Test fun untrustedIsAskedAgainAfter30s1mnThen5mn() {
        var now = 0L
        val b = UntrustedBackoff({ now })
        assertTrue(b.mayTry("aa:bb"))
        assertTrue(b.onRefused("AA:BB", BtProtocol.ERR_UNTRUSTED), "premier refus : le message")
        assertFalse(b.mayTry("aa:bb")); now = 29_999; assertFalse(b.mayTry("aa:bb")); now = 30_000; assertTrue(b.mayTry("aa:bb"))
        assertFalse(b.onRefused("aa:bb", BtProtocol.ERR_UNTRUSTED), "pas un second message")
        now += 59_999; assertFalse(b.mayTry("aa:bb")); now += 1; assertTrue(b.mayTry("aa:bb"))
        b.onRefused("aa:bb", BtProtocol.ERR_UNTRUSTED)
        now += 299_999; assertFalse(b.mayTry("aa:bb")); now += 1; assertTrue(b.mayTry("aa:bb"))
        b.onRefused("aa:bb", BtProtocol.ERR_UNTRUSTED); assertEquals(300_000, b.waitMs("aa:bb"))   // reste à 5 min
        b.clear("aa:bb"); assertTrue(b.mayTry("aa:bb")); assertTrue(b.onRefused("aa:bb", BtProtocol.ERR_UNTRUSTED), "après le code : un nouveau refus redit le message")
        assertTrue(b.mayTry("autre")); assertFalse(b.onRefused("autre", BtProtocol.ERR_BUSY)); assertTrue(b.mayTry("autre"))
    }

    @Test fun onlyCastBridgeDevicesAreResumed() {
        assertEquals(true, RecoveryCandidates.declaresCastBridge(listOf("7C5E3B9A-4D2F-4C61-9B0E-CB0000000001")))
        assertEquals(false, RecoveryCandidates.declaresCastBridge(listOf("0000110b-0000-1000-8000-00805f9b34fb")))   // écouteurs : A2DP
        assertNull(RecoveryCandidates.declaresCastBridge(null)); assertNull(RecoveryCandidates.declaresCastBridge(emptyList()))
        assertTrue(RecoveryCandidates.eligible(true, false)); assertTrue(RecoveryCandidates.eligible(false, true)); assertTrue(RecoveryCandidates.eligible(null, true))
        assertFalse(RecoveryCandidates.eligible(false, false)); assertFalse(RecoveryCandidates.eligible(null, false), "HOCO Y12 Ultra")
    }

    // ---- le code demandé dans la boite d'envoi
    private val gone = RefusalRecord(BtProtocol.ERR_UNTRUSTED, 1_000_000)
    private val soon = 1_000_000L + 60_000

    @Test fun savedTvThatForgotThePhoneAsksTheCodeInTheDialog() {
        val c = SendChoices.decide(SendFacts(1, "Salon", null, refusal = gone, nowMs = soon, defaultPinKey = "CastBridge TV Salon"))
        assertEquals(SendAction.ENTER_PIN, c.action); assertEquals("CastBridge TV Salon", c.pinKey); assertEquals(SendRoute.NONE, c.route)
        assertEquals(LinkRefusalTexts.banner(BtProtocol.ERR_UNTRUSTED), c.banner)
        assertTrue(PinEntry.showsField(c.action)); assertEquals("Valider et envoyer", PinEntry.submitLabel(true)); assertEquals("Valider", PinEntry.submitLabel(false))
    }

    @Test fun afterTheCodeTheCopyLeavesAtOnce() {
        val c = SendChoices.afterCode("CastBridge TV Salon")
        assertEquals(SendRoute.PIN_UPLOAD, c.route); assertTrue(c.copyEnabled)
    }

    @Test fun codeRequiredIsRecognisedAndNotConfusedWithAnUnreachableTv() {
        assertTrue(LinkRefusalTexts.isCodeRequired(LinkRefusalTexts.ticket(BtProtocol.ERR_UNTRUSTED)))
        assertTrue(LinkRefusalTexts.isCodeRequired("Autorisation de la TV expirée : reconnectez le téléphone à la TV (401)"))
        assertFalse(LinkRefusalTexts.isCodeRequired(castbridge.core.tv.TvWait.GAVE_UP_TEXT)); assertFalse(LinkRefusalTexts.isCodeRequired(null))
        assertFalse(LinkRefusalTexts.isCodeRequired(LinkRefusalTexts.ticket(BtProtocol.ERR_SPACE)))
    }
}
