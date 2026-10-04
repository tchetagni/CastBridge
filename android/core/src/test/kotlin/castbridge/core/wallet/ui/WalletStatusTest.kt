package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.ui.WalletStatus.State
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Précision du propriétaire (2026-10-04) : « toute activation donne lieu à un portefeuille, il est autonome et hors ligne mais sa mise à jour dépend du serveur ». La TV ne calcule jamais un solde :
 * avant la première synchronisation, AUCUN chiffre ; ensuite le dernier instantané signé reste affichable hors ligne ; au-delà de 35 jours, un bandeau dit que le serveur doit mettre à jour.
 */
class WalletStatusTest {
    private val day = 24L * 3600 * 1000
    private val at = 1_790_000_000_000L
    private val z = ZoneOffset.UTC
    private val sp = " "
    private fun snap() = Snapshot("0123456789abcdef", "7K3M-9PQ2-XH4T-V8RM", "PROD", 3450, 0, 12, 0, 7, at, Snapshot.Flags(false, true, true))

    @Test fun neverSyncedShowsNoFigureAtAll() {
        val v = WalletStatus.of(null, online = true, nowMs = at, lastReason = null, licenseState = null, zone = z)
        assertEquals(State.NEVER_SYNCED, v.state); assertFalse(v.showBalances)
        assertEquals("Portefeuille créé à l'activation : en attente de la première synchronisation", v.line)
        assertTrue(v.line.none { it.isDigit() }); assertTrue(v.banners.isEmpty())
        assertEquals(State.NEVER_SYNCED, WalletStatus.of(null, online = false, nowMs = at, lastReason = null, licenseState = null, zone = z).state)   // hors ligne sans instantané : même état
    }

    @Test fun serverAnswersActivateOrLicensePendingAreWaitingStatesNotErrors() {
        val a = WalletStatus.of(null, true, at, "ACTIVATE", null, z)
        assertEquals(State.WAITING_ACTIVATION, a.state); assertEquals("Activez la TV pour recevoir des jetons", a.line); assertFalse(a.showBalances)
        val p = WalletStatus.of(null, true, at, "LICENSE_PENDING", null, z)
        assertEquals(State.LICENSE_PENDING, p.state); assertEquals("Jetons en attente de notification de votre activation", p.line)
        assertEquals(State.LICENSE_PENDING, WalletStatus.of(null, true, at, null, "PENDING", z).state)
    }

    @Test fun syncedShowsTheSignedBalancesWithTheirDate() {
        val v = WalletStatus.of(snap(), true, at + 60_000, null, "ACTIVE", z)
        assertEquals(State.SYNCED, v.state); assertTrue(v.showBalances); assertEquals("Soldes au 21/09 14:13", v.line); assertTrue(v.banners.isEmpty())
    }

    @Test fun offlineKeepsTheLastSnapshotReadable() {
        val v = WalletStatus.of(snap(), false, at + 3 * day, null, "ACTIVE", z)
        assertEquals(State.OFFLINE, v.state); assertTrue(v.showBalances); assertEquals("Hors ligne : soldes au 21/09 14:13", v.line); assertTrue(v.banners.isEmpty())
    }

    @Test fun staleAfterThirtyFiveDaysSaysTheServerMustUpdate() {
        val ok = WalletStatus.of(snap(), false, at + 35 * day, null, null, z)
        assertEquals(State.OFFLINE, ok.state)                                              // 35 jours pile : pas encore
        val v = WalletStatus.of(snap(), false, at + 35 * day + 1, null, null, z)
        assertEquals(State.STALE, v.state); assertEquals(listOf("Mise à jour du serveur attendue"), v.banners); assertTrue(v.showBalances)
        assertEquals("Hors ligne : soldes au 21/09 14:13", v.line)
        val online = WalletStatus.of(snap(), true, at + 40 * day, null, null, z)
        assertEquals(State.STALE, online.state); assertEquals("Soldes au 21/09 14:13", online.line)
    }

    @Test fun aTvClockBehindTheSnapshotIsNeverStale() {
        assertEquals(State.SYNCED, WalletStatus.of(snap(), true, at - 400 * day, null, null, z).state)
    }

    @Test fun licensePendingNoticeIsABannerAboveTheBalances() {
        val v = WalletStatus.of(snap(), true, at, null, "PENDING", z)
        assertEquals(listOf("Jetons en attente de notification de votre activation"), v.banners); assertTrue(v.showBalances)
        val both = WalletStatus.of(snap(), false, at + 36 * day, "LICENSE_PENDING", null, z)
        assertEquals(listOf("Mise à jour du serveur attendue", "Jetons en attente de notification de votre activation"), both.banners)
    }

    @Test fun cardLineHasFiguresOnlyFromASignedSnapshot() {
        val s = snap()
        assertEquals("3${sp}450 NDEM · 12 MBOKO", WalletStatus.cardLine(WalletStatus.of(s, true, at, null, null, z), s))
        assertEquals("3${sp}450 NDEM · 12 MBOKO", WalletStatus.cardLine(WalletStatus.of(s, false, at + 50 * day, null, null, z), s))
        assertEquals("En attente de synchronisation", WalletStatus.cardLine(WalletStatus.of(null, true, at, null, null, z), null))
        assertEquals("En attente de synchronisation", WalletStatus.cardLine(WalletStatus.of(null, true, at, "ACTIVATE", null, z), null))
        assertEquals("En attente de notification", WalletStatus.cardLine(WalletStatus.of(null, true, at, "LICENSE_PENDING", null, z), null))
    }
}
