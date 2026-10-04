package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.WalletReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletGateTest {
    private fun snap(frozen: Boolean = false) = Snapshot("0123456789abcdef", "7K3M-9PQ2-XH4T-V8RM", "PROD", 3450, 0, 12, 0, 7, 1_790_000_000_000L, Snapshot.Flags(frozen, true, true))
    private val policy = PolicyView(1000, 200, convert = true, transfer = true, vouchers = true, stakesNdem = true, stakesMboko = true, transferCapNdem = 5000, transferCapMboko = 50)

    @Test fun activatedNeedsAnActivationAndNoLock() {
        assertFalse(WalletGate.activated(0, false)); assertTrue(WalletGate.activated(1, false)); assertFalse(WalletGate.activated(2, true))
    }

    @Test fun cardNeedsActivationAndTheFlagAndIsHiddenOnlyWhenTheServiceIsDown() {
        // « toute activation donne lieu à un portefeuille » : la carte existe dès l'activation, même avant la première synchronisation
        assertTrue(WalletGate.cardVisible(true, true, ServerWallet.ENABLED, hasSnapshot = true))
        assertTrue(WalletGate.cardVisible(true, true, ServerWallet.ENABLED, hasSnapshot = false))
        assertTrue(WalletGate.cardVisible(true, true, ServerWallet.UNKNOWN, hasSnapshot = false))    // jamais synchronisé : « en attente de la première synchronisation »
        assertTrue(WalletGate.cardVisible(true, true, ServerWallet.UNKNOWN, hasSnapshot = true))     // hors ligne avec un instantané déjà reçu
        assertFalse(WalletGate.cardVisible(false, true, ServerWallet.ENABLED, true))                 // TV non activée
        assertFalse(WalletGate.cardVisible(true, false, ServerWallet.ENABLED, true))                 // drapeau local éteint
        assertFalse(WalletGate.cardVisible(true, true, ServerWallet.UNAVAILABLE, true))              // 404 / 503 : carte cachée
        assertFalse(WalletGate.cardVisible(true, true, ServerWallet.UNAVAILABLE, false))
    }

    @Test fun screenIsOpenableEvenWhenTheServiceIsDownSoItCanSayWhy() {
        assertTrue(WalletGate.screenOpenable(true, true))
        assertFalse(WalletGate.screenOpenable(false, true)); assertFalse(WalletGate.screenOpenable(true, false))
    }

    @Test fun serverStateFollowsTheAnswers() {
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.UNKNOWN, 200, null))
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.UNAVAILABLE, 200, null))
        assertEquals(ServerWallet.UNAVAILABLE, WalletGate.serverAfter(ServerWallet.ENABLED, 404, null))
        assertEquals(ServerWallet.UNAVAILABLE, WalletGate.serverAfter(ServerWallet.ENABLED, 503, null))
        // un refus métier ou un incident passager ne dit rien sur l'activation du module
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.ENABLED, 503, "OFFLINE"))
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.ENABLED, 409, "ACTIVATE"))
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.ENABLED, 429, "RATE_LIMIT"))
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.ENABLED, 500, null))
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.ENABLED, 401, null))
        assertEquals(ServerWallet.ENABLED, WalletGate.serverAfter(ServerWallet.ENABLED, null, null))     // réseau coupé
        assertEquals(ServerWallet.UNKNOWN, WalletGate.serverAfter(ServerWallet.UNKNOWN, null, null))
    }

    @Test fun offlineDisablesOperationsWithTheirReason() {
        val off = WalletReason.OFFLINE.text()
        for (op in listOf(WalletOp.CONVERT, WalletOp.SEND, WalletOp.RECEIVE)) {
            val g = WalletGate.gate(op, online = false, snapshot = snap(), policy = policy)
            assertFalse(g.allowed, op.name); assertEquals(off, g.reason, op.name)
        }
        assertTrue(WalletGate.gate(WalletOp.HISTORY, online = false, snapshot = snap(), policy = policy, hasCachedHistory = true).allowed)
        val h = WalletGate.gate(WalletOp.HISTORY, online = false, snapshot = snap(), policy = policy, hasCachedHistory = false)
        assertFalse(h.allowed); assertEquals(off, h.reason)
    }

    @Test fun onlineOperationsAreOpenUnlessTheSignedSnapshotOrThePolicySaysNo() {
        for (op in WalletOp.values()) assertTrue(WalletGate.gate(op, true, snap(), policy).allowed, op.name)
        // compte gelé : ni convertir ni envoyer, mais recevoir et consulter
        assertEquals(WalletReason.FROZEN.text(), WalletGate.gate(WalletOp.CONVERT, true, snap(frozen = true), policy).reason)
        assertFalse(WalletGate.gate(WalletOp.SEND, true, snap(frozen = true), policy).allowed)
        assertTrue(WalletGate.gate(WalletOp.RECEIVE, true, snap(frozen = true), policy).allowed)
        assertTrue(WalletGate.gate(WalletOp.HISTORY, true, snap(frozen = true), policy).allowed)
        // interrupteurs de la politique
        val noConvert = policy.copy(convert = false); val noTransfer = policy.copy(transfer = false)
        assertEquals("Conversion suspendue pour maintenance : réessayez plus tard", WalletGate.gate(WalletOp.CONVERT, true, snap(), noConvert).reason)
        assertEquals("Transferts suspendus pour maintenance : réessayez plus tard", WalletGate.gate(WalletOp.SEND, true, snap(), noTransfer).reason)
        assertTrue(WalletGate.gate(WalletOp.SEND, true, snap(), noConvert).allowed)
    }

    @Test fun withoutSnapshotNothingThatNeedsBalancesIsOffered() {
        val g = WalletGate.gate(WalletOp.CONVERT, true, null, policy)
        assertFalse(g.allowed); assertEquals("Disponible après la première synchronisation", g.reason)
        assertFalse(WalletGate.gate(WalletOp.SEND, true, null, policy).allowed)
        assertTrue(WalletGate.gate(WalletOp.RECEIVE, true, null, policy).allowed)
        assertTrue(WalletGate.gate(WalletOp.HISTORY, true, null, policy).allowed)
    }

    @Test fun unknownPolicyLeavesTheServerAsTheJudge() {
        assertTrue(WalletGate.gate(WalletOp.CONVERT, true, snap(), null).allowed)
        assertNull(WalletGate.gate(WalletOp.CONVERT, true, snap(), null).reason)
    }
}
