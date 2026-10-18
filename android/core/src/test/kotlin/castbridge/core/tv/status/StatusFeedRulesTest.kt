package castbridge.core.tv.status

import castbridge.core.net.NetState
import castbridge.core.tv.Fs
import castbridge.core.tv.StoragePolicy
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.VolumeKind
import castbridge.core.wallet.ui.WalletStatus
import java.io.File
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

    // ---- Stockage : la pastille décrit le volume où arrivent les copies, pas le plus serré (décision du propriétaire du 2026-10-18) ----

    private fun vol(pct: Int, usb: Boolean = false, writable: Boolean = true, id: String = if (usb) "usb-1" else "internal") = StatusFeedRules.Vol(pct * GB, 100 * GB, writable, usb, id)
    private fun level(r: StatusFeedRules.StorageReading) = StatusRules.storageOf(r.present, r.free, r.total, r.readOnly, false, r.other).level

    @Test fun theDestinationKeyIsTheVolumeMeasuredNotTheTightestOne() {
        val r = StatusFeedRules.storage(listOf(vol(50), vol(37, usb = true)), "usb-1")
        assertTrue(r.present && r.usb); assertEquals(37 * GB, r.free); assertEquals(100 * GB, r.total); assertFalse(r.readOnly); assertEquals(OtherStorage.NONE, r.other)
        assertEquals(StatusLevel.OK, level(r))
        val k = StatusFeedRules.storage(listOf(vol(12), vol(80, usb = true)), "usb-1")      // l'ancienne règle disait 12 % (le plus serré) : la clé à 80 % est celle qu'on lit
        assertTrue(k.usb); assertEquals(80 * GB, k.free); assertEquals(StatusLevel.OK, level(k))
        assertEquals(StatusLevel.WARN, level(StatusFeedRules.storage(listOf(vol(50), vol(12, usb = true)), "usb-1")))
        assertEquals(StatusLevel.ERROR, level(StatusFeedRules.storage(listOf(vol(50), vol(4, usb = true)), "usb-1")))
    }
    @Test fun withoutAKeyThatReceivesTheCopiesItIsTheInternalMemory() {
        val r = StatusFeedRules.storage(listOf(vol(12)), null)
        assertTrue(r.present); assertFalse(r.usb); assertEquals(12 * GB, r.free); assertEquals(OtherStorage.NONE, r.other)
        val withKey = listOf(vol(12), vol(90, usb = true))
        assertFalse(StatusFeedRules.storage(withKey, "internal").usb, "une clé branchée qui ne reçoit pas les copies n'est pas décrite")
        assertEquals(12 * GB, StatusFeedRules.storage(withKey, "internal").free)
        assertFalse(StatusFeedRules.storage(withKey, null).usb, "destination inconnue : la mémoire interne")
        assertFalse(StatusFeedRules.storage(listOf(vol(12)), "usb-gone").usb, "clé retirée : on repasse à la mémoire interne")
    }
    @Test fun anotherVolumeInRedKeepsTheDestinationAndOnlyAddsARemark() {
        fun other(vararg v: StatusFeedRules.Vol, dest: String) = StatusFeedRules.storage(v.toList(), dest)
        val a = other(vol(3), vol(37, usb = true), dest = "usb-1")                         // clé verte, mémoire interne à 3 %
        assertTrue(a.usb); assertEquals(37 * GB, a.free); assertEquals(OtherStorage.INTERNAL_FULL, a.other)
        assertEquals(OtherStorage.INTERNAL_READ_ONLY, other(vol(50, writable = false), vol(37, usb = true), dest = "usb-1").other)
        assertEquals(OtherStorage.USB_READ_ONLY, other(vol(40), vol(60, usb = true, writable = false), dest = "internal").other)     // clé en lecture seule ignorée par les envois
        assertEquals(OtherStorage.USB_FULL, other(vol(40), vol(2, usb = true), dest = "internal").other)
        assertEquals(OtherStorage.NONE, other(vol(5), vol(37, usb = true), dest = "usb-1").other, "5 % est orange, pas rouge : rien à signaler")
        assertEquals(OtherStorage.NONE, other(vol(3), vol(37, usb = true), dest = "internal").other, "le volume décrit n'est jamais « l'autre » : ici la clé est verte")
        assertEquals(OtherStorage.INTERNAL_FULL, other(vol(2, usb = true, id = "usb-2"), vol(3), vol(37, usb = true), dest = "usb-1").other, "la mémoire interne d'abord")
    }
    @Test fun aReadOnlyDestinationIsReadOnlyAndTheOthersDoNotRedenIt() {
        val r = StatusFeedRules.storage(listOf(vol(50), vol(60, usb = true, writable = false)), "usb-1")
        assertTrue(r.usb && r.readOnly); assertEquals(StatusLevel.ERROR, level(r))
        val internal = StatusFeedRules.storage(listOf(vol(50), vol(60, usb = true, writable = false)), "internal")      // l'ancienne règle rougissait pour n'importe quel volume en lecture seule
        assertFalse(internal.readOnly); assertEquals(OtherStorage.USB_READ_ONLY, internal.other); assertEquals(StatusLevel.WARN, level(internal))
    }
    @Test fun nothingMeasurableIsAbsent() {
        assertFalse(StatusFeedRules.storage(emptyList()).present)
        assertFalse(StatusFeedRules.storage(listOf(StatusFeedRules.Vol(0, 0, true))).present, "un volume sans taille n'est pas mesurable")
        assertFalse(StatusFeedRules.storage(listOf(vol(50, usb = true).copy(total = 0)), "usb-1").present)
        assertEquals(StatusLevel.OFF, StatusRules.storage(false, 0, 0).level)
        val gone = StatusFeedRules.storage(listOf(vol(50, usb = true).copy(total = 0), vol(30)), "usb-1")      // la clé choisie n'est pas mesurable : la mémoire interne
        assertTrue(gone.present && !gone.usb); assertEquals(30 * GB, gone.free)
    }

    private fun sv(id: String, kind: VolumeKind, free: Long = 50 * GB, writable: Boolean = true, bps: Long = 0) =
        StorageVolume(id, id, File("/x/$id"), kind, if (kind == VolumeKind.INTERNAL) Fs.UNKNOWN else Fs.EXFAT, free, 100 * GB, kind != VolumeKind.INTERNAL, writable, bps)
    private val internalVolume = sv("internal", VolumeKind.INTERNAL, 10 * GB)
    private fun dest(volumes: List<StorageVolume>, target: String) = StatusFeedRules.destinationId(volumes, target)

    @Test fun theDestinationIsWhereTheTvStoragePolicySendsTheNextCopy() {
        val key = sv("usb-1", VolumeKind.REMOVABLE)
        assertEquals("usb-1", dest(listOf(internalVolume, key), StoragePolicy.AUTO), "auto : la clé inscriptible reçoit les copies")
        assertEquals("internal", dest(listOf(internalVolume), StoragePolicy.AUTO), "pas de clé")
        assertEquals("internal", dest(listOf(internalVolume, key), StoragePolicy.INTERNAL), "cible : la mémoire interne")
        assertEquals("usb-1", dest(listOf(internalVolume, key), "usb-1"), "cible : cette clé")
        assertEquals("internal", dest(listOf(internalVolume, key.copy(writable = false)), StoragePolicy.AUTO), "auto : une clé en lecture seule est ignorée")
        assertEquals("usb-1", dest(listOf(internalVolume, key.copy(writable = false)), "usb-1"), "clé choisie mais en lecture seule : elle reste la destination, les copies y sont refusées")
        assertEquals("internal", dest(listOf(internalVolume), "usb-1"), "clé choisie et retirée")
        assertEquals("internal", dest(listOf(internalVolume, key.copy(writeBps = 500_000)), StoragePolicy.AUTO), "clé lente : après la mémoire interne, comme pour l'envoi")
        assertEquals("usb-2", dest(listOf(internalVolume, key, sv("usb-2", VolumeKind.REMOVABLE, free = 80 * GB)), StoragePolicy.AUTO), "deux clés : la plus libre")
        val saf = StorageVolume("saf", "Dossier choisi", File("saf:"), VolumeKind.SAF, Fs.UNKNOWN, -1, 0, true, true)
        assertEquals("internal", dest(listOf(internalVolume, saf), "saf"), "un dossier choisi ne se mesure pas : la mémoire interne")
        assertNull(dest(emptyList(), StoragePolicy.AUTO))
    }
}
