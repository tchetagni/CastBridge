package castbridge.core.tokens

import castbridge.core.lots.InstallKey
import castbridge.core.net.JsonLite
import castbridge.core.owner.*
import castbridge.core.quiz.Boost
import castbridge.core.quiz.QuizBoosts
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/** Fixture commune : clé serveur, une TV, deux installations, bons déjà vérifiés. */
class TokenKit {
    val t0 = 1_800_000_000_000L
    val server = Ed25519Signer(ByteArray(32) { 1 })
    val ring = KeyRing(listOf(server.trusted(setOf(KeyScope.ISSUE_PRODUCTION))))
    val dev = DeviceIdentity.fingerprints(RawFactors("FLASH-1", "cid-1", "AA:BB:CC:00:22:01", "10:20:30:40:60:01", "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0", "SYS1", "11:22:33:44:66:01"))
    val install = InstallKey.fromSeed(ByteArray(32) { 5 })
    val key = WalletKey.derive(install.priv)
    val dir: File = Files.createTempDirectory("wallet-test").toFile()
    val file = File(dir, "wallet.txt")
    fun wallet(provider: WalletKeyProvider = FixedWalletKey(key), compactAbove: Int = 500, keepRecent: Int = TokenWallet.KEEP_RECENT, keepWindowMs: Long = TokenWallet.KEEP_WINDOW_MS) = TokenWallet(file, provider, compactAbove, keepRecent, keepWindowMs)

    fun token(grant: Long, amount: Long = 20, license: String = "lic-1") = TokenGrant.issue(server, grant, "%016x".format(grant), t0, t0, t0 + 86_400_000L, Envelope.Target.Device(DeviceIdentity.kFor(dev.n), dev.byKind), TokenGrant(license, grant, amount, install.pub, 0))
    fun verify(token: String, lastGrant: Long = 0) = TokenGrant.verify(token, ring, RevocationState(), dev, install.pub, lastGrant, t0 + 1000)
    fun credit(w: TokenWallet, grant: Long, amount: Long = 20): CreditResult { val t = token(grant, amount); return w.credit((verify(t, w.lastGrant()) as TokenGrantResult.Accepted).grant, TokenGrant.fingerprint(t)) }
    fun close() { dir.deleteRecursively() }
}

class TokenWalletTest {
    private val k = TokenKit()
    @AfterTest fun cleanup() = k.close()

    @Test fun creditThenSpendKeepsBalanceAcrossRestarts() {
        val w = k.wallet()
        assertEquals(WalletState.EMPTY, w.state()); assertEquals(0, w.balance())
        assertEquals(CreditResult.OK, k.credit(w, 1, 20))
        val r = w.spend("second-chance", 5, 10, "quiz:g:second-chance:1") as SpendResult.Ok
        assertEquals(1, r.seq); assertEquals(15, r.balance); assertFalse(r.replayed)
        val again = k.wallet(); assertEquals(15, again.balance()); assertEquals(1, again.lastGrant())
        assertEquals(2, (again.spend("extra-joker", 2, 11, "quiz:g:extra-joker:1") as SpendResult.Ok).seq)
    }

    @Test fun anOperationDebitsOnlyOnce_evenAcrossARestart() {
        val w = k.wallet(); k.credit(w, 1, 20)
        w.spend("second-chance", 5, 10, "op-1")
        val replay = k.wallet().spend("second-chance", 5, 99, "op-1") as SpendResult.Ok
        assertTrue(replay.replayed); assertEquals(1, replay.seq); assertEquals(15, replay.balance)
        assertEquals(SpendResult.OpConflict, k.wallet().spend("extra-joker", 5, 99, "op-1"))
        assertEquals(15, k.wallet().balance())
    }

    @Test fun concurrentSpendsNeverOverdrawAndSameOperationDebitsOnce() {
        val w = k.wallet(); k.credit(w, 1, 50)
        val pool = Executors.newFixedThreadPool(16); val go = CountDownLatch(1); val ok = AtomicInteger(); val insufficient = AtomicInteger()
        repeat(24) { i -> pool.execute { go.await(); when (w.spend("swap-question", 5, i.toLong(), "op-$i")) { is SpendResult.Ok -> ok.incrementAndGet(); is SpendResult.Insufficient -> insufficient.incrementAndGet(); else -> fail("inattendu") } } }
        go.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(10, ok.get()); assertEquals(14, insufficient.get()); assertEquals(0, w.balance()); assertEquals(0, k.wallet().balance())

        val w2 = TokenWallet(File(k.dir, "w2.txt"), FixedWalletKey(k.key)); val t = k.token(1, 30); w2.credit((k.verify(t) as TokenGrantResult.Accepted).grant, TokenGrant.fingerprint(t))
        val pool2 = Executors.newFixedThreadPool(8); val go2 = CountDownLatch(1); val fresh = AtomicInteger()
        repeat(8) { pool2.execute { go2.await(); (w2.spend("second-chance", 5, 1, "same-op") as? SpendResult.Ok)?.let { if (!it.replayed) fresh.incrementAndGet() } } }
        go2.countDown(); pool2.shutdown(); assertTrue(pool2.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(1, fresh.get()); assertEquals(25, w2.balance())
    }

    @Test fun aFailedWriteDebitsNothing() {
        val w = k.wallet(); k.credit(w, 1, 20)
        File(k.dir, "wallet.txt.tmp").mkdirs()       // SafeFile cannot create its temporary file
        assertEquals(SpendResult.WriteFailed, w.spend("second-chance", 5, 10, "op-1"))
        assertEquals(20, w.balance()); assertEquals(20, k.wallet().balance())
        File(k.dir, "wallet.txt.tmp").deleteRecursively()
        assertTrue(w.spend("second-chance", 5, 10, "op-1") is SpendResult.Ok)       // the same operation may be retried: it was never debited
        assertEquals(15, w.balance())
    }

    @Test fun spendIsBoundedAndValidated() {
        val w = k.wallet(); k.credit(w, 1, 5)
        assertEquals(SpendResult.Invalid, w.spend("second-chance", -3, 1, "op-neg"))
        assertEquals(SpendResult.Invalid, w.spend("second-chance", 0, 1, "op-zero"))
        assertEquals(SpendResult.Invalid, w.spend("Second Chance", 1, 1, "op-item"))
        assertEquals(SpendResult.Invalid, w.spend("second-chance", 1, 1, "op|pipe"))
        assertEquals(SpendResult.Invalid, w.spend("second-chance", 1, 1, ""))
        assertEquals(5, w.balance())
        assertEquals(5, (w.spend("second-chance", 6, 1, "op-big") as SpendResult.Insufficient).balance)
    }

    @Test fun withoutAKeyTheWalletIsReadOnlyAndSaysSo() {
        val w = k.wallet(); k.credit(w, 1, 20)
        var key: ByteArray? = null
        val locked = k.wallet(WalletKeyProvider { key })
        assertEquals(WalletState.NO_KEY, locked.state()); assertEquals(0, locked.balance())
        assertEquals("Jetons indisponibles sur cette TV", (locked.spend("second-chance", 5, 1, "op-1") as SpendResult.Unreadable).message)
        assertEquals(CreditResult.UNREADABLE, locked.credit(TokenGrant("lic-1", 2, 5, k.install.pub, 0), "0123456789abcdef"))
        assertNull(locked.report())
        key = k.key
        assertEquals(WalletState.OK, locked.state()); assertEquals(20, locked.balance())
    }

    @Test fun anotherKeyOrAnEmptyOrAGarbageFileGivesAnUnreadableWalletAndNothingIsWritten() {
        k.credit(k.wallet(), 1, 20)
        val before = k.file.readText()
        val other = k.wallet(FixedWalletKey(WalletKey.derive(ByteArray(32) { 9 })))
        assertEquals(WalletState.UNREADABLE, other.state()); assertEquals(0, other.balance())
        assertEquals("Porte-jetons illisible : reconnectez le téléphone pour le resynchroniser", other.message())
        assertTrue(other.spend("second-chance", 5, 1, "op-1") is SpendResult.Unreadable)
        assertEquals(CreditResult.UNREADABLE, other.credit(TokenGrant("lic-1", 2, 5, k.install.pub, 0), "0123456789abcdef"))
        assertEquals(before, k.file.readText())
        k.file.writeText("")                       // wiped file: not a fresh wallet (it would allow replaying old vouchers)
        assertEquals(WalletState.UNREADABLE, k.wallet().state())
        k.file.writeText("n'importe quoi\n"); assertEquals(WalletState.UNREADABLE, k.wallet().state())
    }

    @Test fun aGrantIsCreditedOnlyOnceAndOnlyForTheSameLicenceAndInstallation() {
        val w = k.wallet()
        val t = k.token(1, 20); val g = (k.verify(t) as TokenGrantResult.Accepted).grant
        assertEquals(CreditResult.OK, w.credit(g, TokenGrant.fingerprint(t)))
        assertEquals(CreditResult.STALE, w.credit(g, TokenGrant.fingerprint(t)))
        assertEquals(20, w.balance())
        assertEquals(CreditResult.WRONG_LICENSE, w.credit(TokenGrant("lic-2", 2, 5, k.install.pub, 0), "0123456789abcdef"))
        assertEquals(CreditResult.WRONG_INSTALL, w.credit(TokenGrant("lic-1", 2, 5, InstallKey.fromSeed(ByteArray(32) { 6 }).pub, 0), "0123456789abcdef"))
        assertEquals(CreditResult.BAD_GRANT, w.credit(TokenGrant("lic-1", 2, 0, k.install.pub, 0), "0123456789abcdef"))
        assertEquals(CreditResult.BAD_GRANT, w.credit(TokenGrant("lic-1", 2, 10_001, k.install.pub, 0), "0123456789abcdef"))
        assertEquals(20, w.balance())
    }

    @Test fun reportListsOnlyNewSpendsAndIsAuthenticated() {
        val w = k.wallet(); k.credit(w, 1, 20)
        w.spend("second-chance", 5, 10, "op-1"); w.spend("extra-joker", 2, 11, "op-2")
        val r = w.report(1)!!
        assertEquals(listOf(2L), r.spends.map { it.seq }); assertEquals(7, r.spentTotal); assertEquals(1, r.lastGrant); assertEquals("lic-1", r.license); assertEquals(k.install.installId, r.install)
        assertTrue(r.render().startsWith("castbridge-token-report-v1\nlicense=lic-1\ninstall=${k.install.installId}\nlastGrant=1\nspentTotal=7\nlastSpendSeq=2\nspend=2|11|quiz|extra-joker|2|op-2\nmac="))
        assertEquals(TokenWallet.mac(k.key, r.lines().joinToString("\n")), r.mac)
        assertNotEquals(r.mac, w.report(0)!!.mac)
        val json = JsonLite.obj(r.toJson()); assertEquals(7L, (json["spentTotal"] as Number).toLong()); assertEquals(1, (json["spends"] as List<*>).size)
    }

    @Test fun ackIgnoresAnAckBeyondTheLocalSequenceAndCompactsWhenLong() {
        val w = k.wallet(compactAbove = 6, keepRecent = 1, keepWindowMs = 0); k.credit(w, 1, 100)
        repeat(5) { w.spend("swap-question", 3, it.toLong(), "op-$it") }
        assertFalse(w.ack(6)); assertFalse(w.ack(-1))
        assertEquals(9, k.file.readText().trimEnd().lines().size)
        assertTrue(w.ack(4))
        val lines = k.file.readText().trimEnd().lines()
        assertTrue(lines.any { it.startsWith("acked=4|12|") }); assertEquals(1, lines.count { it.startsWith("spend=") }); assertTrue(lines.any { it.startsWith("granted=1|100|") })
        assertEquals(85, w.balance()); assertEquals(15, w.summary().spentTotal); assertEquals(5, w.summary().lastSpendSeq)
        assertEquals(6, (k.wallet().spend("swap-question", 3, 9, "op-new") as SpendResult.Ok).seq)
    }

    @Test fun theMainFileIsAuthoritativeAndTheBackupIsOnlyUsedWhenItIsMissing() {
        val w = k.wallet(); k.credit(w, 1, 20); w.spend("second-chance", 5, 1, "op-1")
        val bak = SafeFile.bak(k.file); assertTrue(bak.isFile)
        k.file.delete(); assertEquals(WalletState.OK, k.wallet().state())          // power cut between the two renames of a first write
        k.file.writeText(bak.readText().lines().first() + "\nfaux\n"); assertEquals(WalletState.UNREADABLE, k.wallet().state())   // a corrupt main is NOT silently replaced by the backup
    }

    @Test fun walletSourceHasNoWipeApiAndNoAndroid() {
        val src = File(System.getProperty("user.dir")).let { var d: File? = it; while (d != null && !File(d, "android/core/src/main/kotlin/castbridge/core/tokens").isDirectory) d = d.parentFile; File(d!!, "android/core/src/main/kotlin/castbridge/core/tokens") }
        val all = src.listFiles()!!.joinToString("\n") { it.readText() }
        assertFalse(Regex("fun (delete|reset|clear)").containsMatchIn(File(src, "TokenWallet.kt").readText()))
        assertFalse(all.contains("import android")); assertFalse(all.contains("VirtualWallet")); assertFalse(all.contains("Pot."))
    }

    /** QuizBoosts.charge over this wallet, as w5-17 will plug it: (gameId, item, purchaseNo) = one operation key = at most one debit, a retry is a replay. */
    private class WalletBoosts(val w: TokenWallet, val p: TokenPolicy, val now: () -> Long) : QuizBoosts {
        private fun item(b: Boost) = TokenItem.valueOf(b.name)
        override fun available(b: Boost) = w.state() == WalletState.OK
        override fun cost(b: Boost) = p.cost(item(b))
        override fun balance() = w.balance()
        override fun charge(b: Boost, gameId: String, purchaseNo: Int): Boolean {
            val it = item(b)
            if (!UUID_RE.matches(gameId) || purchaseNo < 1 || purchaseNo > p.maxPerGame(it) || purchaseNo > b.maxPerGame) return false
            return w.spend(it.wire, p.cost(it), now(), TokenPolicy.opKey(gameId, it, purchaseNo)) is SpendResult.Ok
        }
        companion object { val UUID_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") }
    }

    private fun uuid() = java.util.UUID.randomUUID().toString()

    @Test fun theQuizBoostsContractIsServedByTheWalletAndARetryNeverDebitsTwice() {
        val w = k.wallet(); k.credit(w, 1, 20); val p = TokenPolicy()
        val boosts = WalletBoosts(w, p) { 5 }; val g1 = uuid()
        assertTrue(boosts.charge(Boost.SECOND_CHANCE, g1, 1)); assertEquals(15, boosts.balance())
        assertTrue(boosts.charge(Boost.SECOND_CHANCE, g1, 1), "retry of the SAME purchase: same Ok"); assertEquals(15, boosts.balance(), "no second debit")
        assertFalse(boosts.charge(Boost.SECOND_CHANCE, g1, 2), "one second chance per game"); assertEquals(15, boosts.balance())
        assertTrue(boosts.charge(Boost.EXTRA_JOKER, g1, 1)); assertTrue(boosts.charge(Boost.EXTRA_JOKER, g1, 1)); assertEquals(13, boosts.balance(), "retry of purchase #1 after a success is not purchase #2")
        assertTrue(boosts.charge(Boost.EXTRA_JOKER, g1, 2)); assertEquals(11, boosts.balance())
        assertTrue(boosts.charge(Boost.EXTRA_JOKER, g1, 2)); assertEquals(11, boosts.balance())
        assertFalse(boosts.charge(Boost.EXTRA_JOKER, g1, 3), "at most twice per game"); assertEquals(11, boosts.balance())
        assertFalse(boosts.charge(Boost.SWAP_QUESTION, g1, 0)); assertFalse(boosts.charge(Boost.SWAP_QUESTION, "g1", 1), "a game id must be a unique UUID")
        // the retry also survives a restart of the TV app
        assertTrue(WalletBoosts(k.wallet(), p) { 6 }.charge(Boost.SECOND_CHANCE, g1, 1)); assertEquals(11, k.wallet().balance())
        assertTrue(boosts.charge(Boost.SECOND_CHANCE, uuid(), 1)); assertEquals(6, boosts.balance())       // a new game: a new purchase
        assertFalse(WalletBoosts(w, TokenPolicy(TokenSettings(secondChance = 50)), { 5 }).charge(Boost.SECOND_CHANCE, uuid(), 1), "solde insuffisant : rien n'est pris"); assertEquals(6, w.balance())
    }

    @Test fun tokenItemsAndCapsMirrorTheQuizBoosts() {
        assertEquals(Boost.values().map { it.name }, TokenItem.values().map { it.name })
        val p = TokenPolicy()
        for (b in Boost.values()) { val it = TokenItem.valueOf(b.name); assertEquals(b.maxPerGame, p.maxPerGame(it), "cap of ${b.name}"); assertEquals(b.label, p.label(it), "label of ${b.name}") }
    }

    @Test fun twoInstancesOnTheSameFileLoseNoSpend() {
        val a = k.wallet(); val b = k.wallet(); k.credit(a, 1, 50)
        assertEquals(50, b.balance())
        assertEquals(1L, (a.spend("second-chance", 5, 1, "op-a1") as SpendResult.Ok).seq)
        assertEquals(2L, (b.spend("extra-joker", 2, 2, "op-b1") as SpendResult.Ok).seq)       // b had cached the pre-spend state
        assertEquals(3L, (a.spend("swap-question", 3, 3, "op-a2") as SpendResult.Ok).seq)
        assertEquals(4L, (b.spend("second-chance", 5, 4, "op-b2") as SpendResult.Ok).seq)
        assertEquals(35, a.balance()); assertEquals(35, b.balance()); assertEquals(35, k.wallet().balance())
        assertTrue((a.spend("extra-joker", 2, 9, "op-b1") as SpendResult.Ok).replayed, "an operation done through the other instance is a replay")
        assertEquals(35, k.wallet().balance())
        assertEquals(CreditResult.STALE, b.credit(TokenGrant("lic-1", 1, 5, k.install.pub, 0), "0123456789abcdef"), "a voucher credited through the other instance is not credited twice")
        assertEquals(CreditResult.OK, k.credit(b, 2, 10)); assertEquals(45, a.balance())
    }

    @Test fun manyInstancesAndThreadsOnOneFileNeverLoseOrDoubleDebit() {
        k.credit(k.wallet(), 1, 100)
        val wallets = List(4) { k.wallet() }
        val pool = Executors.newFixedThreadPool(8); val go = CountDownLatch(1); val ok = AtomicInteger()
        repeat(40) { i -> pool.execute { go.await(); if (wallets[i % 4].spend("swap-question", 3, i.toLong(), "op-$i") is SpendResult.Ok) ok.incrementAndGet() } }
        go.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(33, ok.get()); assertEquals(1, k.wallet().balance()); assertEquals(33, k.wallet().summary().lastSpendSeq)
    }

    @Test fun theFactoryGivesOneInstancePerPathAndSharesTheLock() {
        val a = TokenWallet.of(k.file, FixedWalletKey(k.key)); val b = TokenWallet.of(File(k.dir, "./wallet.txt"), FixedWalletKey(k.key))
        assertSame(a, b)
        k.credit(a, 1, 10); assertEquals(10, b.balance())
        assertTrue(File(k.dir, "wallet.lock").isFile, "the lock file next to the wallet")
    }

    @Test fun compactionKeepsTheLastAckedSpendsSoARetryIsStillDeduplicatedAndTheCapStillHolds() {
        val w = k.wallet(compactAbove = 6, keepRecent = 3, keepWindowMs = 0); k.credit(w, 1, 100)
        for (i in 1..6) w.spend("swap-question", 1, i * 1000L, "quiz:g:swap-question:$i")
        assertTrue(w.ack(6))
        val lines = k.file.readText().trimEnd().lines()
        assertEquals(3, lines.count { it.startsWith("spend=") }); assertTrue(lines.any { it.startsWith("acked=3|3|") })
        assertEquals(94, w.balance()); assertEquals(6, w.summary().lastSpendSeq)
        val r = w.spend("swap-question", 1, 99_000, "quiz:g:swap-question:6") as SpendResult.Ok
        assertTrue(r.replayed); assertEquals(6, r.seq); assertEquals(94, w.balance())
        assertEquals(1, w.spendCount(TokenPolicy.opPrefix("g", TokenItem.SWAP_QUESTION).removeSuffix(":") + ":6"))
        // by default (64 recent, 24 h) nothing a game just did is ever compacted
        val d = k.wallet(compactAbove = 6); d.spend("second-chance", 5, 10_000, "quiz:h:second-chance:1"); d.ack(7)
        assertEquals(1, k.file.readText().lines().count { it.contains("quiz:h:second-chance:1") })
    }

    @Test fun compactionFoldsTheGrantLinesAndKeepsTheChainAndTheNumbering() {
        val w = k.wallet(compactAbove = 5, keepRecent = 1, keepWindowMs = 0)
        for (g in 1L..4L) k.credit(w, g, 10)
        w.spend("second-chance", 5, 1000, "op-1"); w.spend("extra-joker", 2, 2000, "op-2")
        assertTrue(w.ack(1))
        val lines = k.file.readText().trimEnd().lines()
        assertEquals(0, lines.count { it.startsWith("grant=") }); assertTrue(lines.any { it.startsWith("granted=4|40|") })
        assertEquals(33, w.balance()); assertEquals(4, w.lastGrant())
        val again = k.wallet(); assertEquals(WalletState.OK, again.state()); assertEquals(33, again.balance()); assertEquals(7, again.summary().spentTotal)
        assertEquals(CreditResult.STALE, again.credit(TokenGrant("lic-1", 4, 10, k.install.pub, 0), "0123456789abcdef"))
        assertEquals(CreditResult.OK, k.credit(again, 5, 10)); assertEquals(43, k.wallet().balance()); assertEquals(5, k.wallet().lastGrant())
        // the folded line is part of the chain: altering it makes the wallet unreadable
        val text = k.file.readText().replace("granted=4|40|", "granted=4|99|"); k.file.writeText(text)
        assertEquals(WalletState.UNREADABLE, k.wallet().state())
    }
}
