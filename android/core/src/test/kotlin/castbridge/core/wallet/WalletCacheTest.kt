package castbridge.core.wallet

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletCacheTest {
    private class Mem(var text: String? = null) : WalletStore { override fun read() = text; override fun write(text: String) { this.text = text } }

    private val now = WalletTestKeys.NOW
    private val dev = WalletTestKeys.DEVICE_CODE
    private val mine = Voucher.targetOf(dev)!!
    private fun cache(store: Mem = Mem()) = WalletCache(store, WalletTestKeys.walletRing, WalletTestKeys.voucherKeys, WalletTestKeys.TV)
    private fun snap(seq: Long, n: Long = 100, id: String = WalletTestKeys.TV) =
        TestMint.snapshot(Snapshot(WalletTestKeys.wallet.keyId, id, "PROD", n, 0, 1, 0, seq, now, Snapshot.Flags(false, true, true)))
    private fun vtext(nonce: String = "0102030405060708090a", cur: WalletCurrency = WalletCurrency.NDEM, amount: Long = 500, target: String = Voucher.ANY_TARGET) =
        VoucherCode.encode(TestMint.voucher(cur = cur, amount = amount, nonceHex = nonce, targetHex = target))
    private fun nonce(i: Int) = "%020x".format(i)

    @Test fun olderSnapshotNeverOverwritesTheNewest() {
        val c = cache()
        assertEquals(WalletCache.SnapshotOffer.Stored, c.offerSnapshot(snap(5, n = 500)))
        assertEquals(WalletCache.SnapshotOffer.Ignored, c.offerSnapshot(snap(4, n = 9_999)))
        assertEquals(WalletCache.SnapshotOffer.Ignored, c.offerSnapshot(snap(5, n = 9_999)))
        assertEquals(500, c.snapshot!!.n)
        assertEquals(WalletCache.SnapshotOffer.Stored, c.offerSnapshot(snap(6, n = 600)))
        assertEquals(600, c.snapshot!!.n)
    }

    @Test fun anotherIdentityOrTamperedSnapshotIsRefused() {
        val c = cache()
        assertEquals(WalletCache.SnapshotOffer.Refused(WalletRefusal.OTHER_TV), c.offerSnapshot(snap(1, id = "tv-autre")))
        assertTrue(c.offerSnapshot(snap(1).dropLast(3) + "AAA") is WalletCache.SnapshotOffer.Refused)
        assertNull(c.snapshot)
    }

    @Test fun pendingNeverEntersTheBalance() {
        val c = cache()
        c.offerSnapshot(snap(1, n = 1_000))
        assertTrue(c.receiveVoucher(vtext(amount = 500), now, dev) is WalletCache.VoucherOffer.Added)
        assertEquals(1_000, c.snapshot!!.n)            // le solde n'a pas bougé
        assertEquals(500, c.pendingNdem); assertEquals(0, c.pendingMboko)
        assertEquals(listOf("1 000 NDEM · 1 MBOKO", "au 21/09 14:13 · +500 en attente"), WalletView.cardLines(c.snapshot, c.pendingNdem, c.pendingMboko, ZoneOffset.UTC))
        c.dropPending("0102030405060708090a")
        assertEquals(0, c.pendingNdem); assertEquals(1_000, c.snapshot!!.n)             // retirer l'attente ne crédite pas non plus
    }

    @Test fun sameVoucherTwiceIsOneLine() {
        val c = cache()
        assertTrue(c.receiveVoucher(vtext(), now, dev) is WalletCache.VoucherOffer.Added)
        assertEquals(WalletCache.VoucherOffer.Duplicate, c.receiveVoucher(vtext(), now, dev))
        assertEquals(1, c.pending.size)
        c.dropPending("0102030405060708090a")
        assertEquals(WalletCache.VoucherOffer.Duplicate, c.receiveVoucher(vtext(), now, dev))   // nonce « vu » : pas de réactivation sur cette TV
        assertEquals(0, c.pending.size)
    }

    @Test fun capsAndRefusals() {
        val c = cache()
        for (i in 1..10) assertTrue(c.receiveVoucher(vtext(nonce(i), amount = 100), now, dev) is WalletCache.VoucherOffer.Added)
        assertEquals(WalletCache.VoucherOffer.Full, c.receiveVoucher(vtext(nonce(11), amount = 100), now, dev))          // 10 bons
        val d = cache()
        assertTrue(d.receiveVoucher(vtext(nonce(1), amount = 20_000), now, dev) is WalletCache.VoucherOffer.Added)
        assertEquals(WalletCache.VoucherOffer.Full, d.receiveVoucher(vtext(nonce(2), amount = 1), now, dev))             // 20 000 NDEM
        val e = cache()
        assertTrue(e.receiveVoucher(vtext(nonce(1), WalletCurrency.MBOKO, 200, mine), now, dev) is WalletCache.VoucherOffer.Added)
        assertEquals(WalletCache.VoucherOffer.Full, e.receiveVoucher(vtext(nonce(2), WalletCurrency.MBOKO, 1, mine), now, dev))   // 200 MBOKO
        assertTrue(e.receiveVoucher(vtext(nonce(3), WalletCurrency.NDEM, 5), now, dev) is WalletCache.VoucherOffer.Added)  // l'autre monnaie reste permise
        // refus : MBOKO toute TV, autre TV, illisible, faute de frappe
        assertEquals(WalletCache.VoucherOffer.Refused(WalletRefusal.VOUCHER_BAD), cache().receiveVoucher(vtext(nonce(9), WalletCurrency.MBOKO, 5), now, dev))
        assertEquals(WalletCache.VoucherOffer.Refused(WalletRefusal.VOUCHER_OTHER_TV), cache().receiveVoucher(vtext(nonce(9), target = mine), now, WalletTestKeys.OTHER_DEVICE_CODE))
        assertEquals(WalletCache.VoucherOffer.Malformed, cache().receiveVoucher("n'importe quoi", now, dev))
        val groups = vtext().split('-').toMutableList(); groups[6] = (if (groups[6][0] == 'Z') 'Y' else 'Z') + groups[6].substring(1)
        assertEquals(WalletCache.VoucherOffer.BadGroup(7), cache().receiveVoucher(groups.joinToString("-"), now, dev))
    }

    @Test fun seenNoncesAreBoundedAt500() {
        val store = Mem(); val c = cache(store)
        for (i in 1..510) { c.receiveVoucher(vtext(nonce(i), amount = 1), now, dev); c.dropPending(nonce(i)) }
        assertEquals(500, store.text!!.lines().count { it.startsWith("N ") })
        assertTrue(c.receiveVoucher(vtext(nonce(1), amount = 1), now, dev) is WalletCache.VoucherOffer.Added)   // le plus ancien a été purgé
    }

    @Test fun persistenceReloadsAndIgnoresAlteredLines() {
        val store = Mem()
        val c = cache(store)
        c.offerSnapshot(snap(3, n = 77)); c.receiveVoucher(vtext(), now, dev)
        val again = cache(store)
        assertEquals(77, again.snapshot!!.n); assertEquals(500, again.pendingNdem)
        // fichier altéré : instantané modifié, bon à signature cassée, nonce invalide
        val lines = store.text!!.lines().map { l -> if (l.startsWith("S ")) l.dropLast(3) + "AAA" else if (l.startsWith("P ")) l.dropLast(8) + "0000-000" else l }
        val broken = cache(Mem(lines.joinToString("\n") + "\nN zz\n"))
        assertNull(broken.snapshot); assertEquals(0, broken.pending.size)
        // un autre en-tête, ou rien : cache vide, sans erreur
        assertNull(cache(Mem("n'importe quoi")).snapshot); assertNull(cache(Mem(null)).snapshot)
        // une TV d'une autre identité ne relit pas l'instantané
        assertNull(WalletCache(store, WalletTestKeys.walletRing, WalletTestKeys.voucherKeys, "tv-autre").snapshot)
        c.clear(); assertNull(cache(store).snapshot)
    }
}
