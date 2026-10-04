package castbridge.core.tv.status

import castbridge.core.net.NetState
import castbridge.core.wallet.ui.WalletStatus
import kotlin.test.*

/** Traduction des états existants de la TV en mesures de l'instantané. */
class StatusFeedRulesTest {
    private val GB = 1L shl 30
    @Test fun walletStatesMapToTokenSyncStates() {
        val m = mapOf(WalletStatus.State.NEVER_SYNCED to TokensSync.NEVER, WalletStatus.State.WAITING_ACTIVATION to TokensSync.NEVER,
            WalletStatus.State.LICENSE_PENDING to TokensSync.NEVER, WalletStatus.State.SYNCED to TokensSync.SYNCED,
            WalletStatus.State.OFFLINE to TokensSync.OFFLINE, WalletStatus.State.STALE to TokensSync.OFFLINE)
        assertEquals(WalletStatus.State.values().size, m.size)
        for ((k, v) in m) assertEquals(v, StatusFeedRules.tokens(k), k.name)
        assertTrue(StatusFeedRules.licencePending(WalletStatus.State.LICENSE_PENDING))
        assertFalse(StatusFeedRules.licencePending(WalletStatus.State.SYNCED))
    }
    @Test fun internetPathNeverShowsALastingBlueWithoutProbe() {
        assertEquals(InternetPath.UNTESTED, StatusFeedRules.internetPath(NetState.CHECKING, measured = false))
        assertEquals(InternetPath.CHECKING, StatusFeedRules.internetPath(NetState.CHECKING, measured = true))
        assertEquals(InternetPath.DIRECT, StatusFeedRules.internetPath(NetState.INTERNET_WIFI, false))
        assertEquals(InternetPath.DIRECT, StatusFeedRules.internetPath(NetState.INTERNET_ETHERNET, true))
        assertEquals(InternetPath.VIA_PHONE, StatusFeedRules.internetPath(NetState.INTERNET_VIA_PHONE, true))
        assertEquals(InternetPath.NONE, StatusFeedRules.internetPath(NetState.NONE, true))
    }
    @Test fun quizOnlineMapping() {
        assertEquals(QuizLink.IDLE, StatusFeedRules.quiz(false, false, true))
        assertEquals(QuizLink.NOT_ACTIVATED, StatusFeedRules.quiz(true, true, true))
        assertEquals(QuizLink.CONNECTED, StatusFeedRules.quiz(true, false, true))
        assertEquals(QuizLink.SERVER_UNREACHABLE, StatusFeedRules.quiz(true, false, false))
        assertEquals(QuizLink.IDLE, StatusFeedRules.quiz(true, false, null))
    }
    @Test fun bluetoothPhaseMapping() {
        assertEquals(BtPhase.PHONE_LINKED, StatusFeedRules.bluetoothPhase(true, true))
        assertEquals(BtPhase.GATEWAY_ONLY, StatusFeedRules.bluetoothPhase(false, true))
        assertEquals(BtPhase.IDLE, StatusFeedRules.bluetoothPhase(false, false))
    }
    @Test fun storagePicksTheTightestVolumeAndAnyReadOnlyRedens() {
        val internal = StatusFeedRules.Vol(50 * GB, 100 * GB, true); val usb = StatusFeedRules.Vol(3 * GB, 100 * GB, true)
        val r = StatusFeedRules.storage(listOf(internal, usb))
        assertTrue(r.present); assertEquals(3 * GB, r.free); assertFalse(r.readOnly)
        assertEquals(StatusLevel.ERROR, StatusRules.storage(r.present, r.free, r.total, r.readOnly).level)
        assertTrue(StatusFeedRules.storage(listOf(internal, StatusFeedRules.Vol(90 * GB, 100 * GB, false))).readOnly)
        assertFalse(StatusFeedRules.storage(emptyList()).present)
        assertFalse(StatusFeedRules.storage(listOf(StatusFeedRules.Vol(0, 0, true))).present, "un volume sans taille n'est pas mesurable")
        assertEquals(StatusLevel.OFF, StatusRules.storage(false, 0, 0).level)
    }
}
