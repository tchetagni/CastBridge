package castbridge.core.tv.status

import castbridge.core.status.IconKind
import castbridge.core.status.IconState
import castbridge.core.status.StatusIcon
import castbridge.core.status.Tech
import kotlin.test.*

/** Instantané des mesures de la TV -> pastilles : règles, ordre, « + n » et fraîcheur. Le dessin Android n'est pas prouvé ici. */
class StatusSnapshotTest {
    private val GB = 1L shl 30
    private val DAY = StatusThresholds.DAY_MS
    private fun healthy() = StatusSnapshot(
        wifiEnabled = true, wifiConnected = true, wifiRssiDbm = -50, wifiHasInternet = true,
        internet = InternetPath.DIRECT, internetLatencyMs = 80,
        btEnabled = true, bt = BtPhase.IDLE,
        storagePresent = true, storageFreeBytes = 50 * GB, storageTotalBytes = 100 * GB,
        phones = 3, licence = LicenceState.VALID, licenceTiming = LicenceTiming(0, 400 * DAY), nowMs = 100 * DAY,
        tokens = TokensSync.SYNCED, tokenBalance = 20)
    private fun byId(s: StatusSnapshot) = s.badges().associateBy { it.id }

    @Test fun anEmptySnapshotShowsNoGreenBecauseNothingWasMeasured() {
        val b = StatusSnapshot().badges()
        assertTrue(b.isNotEmpty())
        for (x in b) assertEquals(StatusLevel.UNKNOWN, x.level, "${x.id} ne doit pas être vert sans mesure")
        assertTrue(b.none { it.level == StatusLevel.OK })
        assertEquals("non testé", b.first { it.id == "internet" }.stateWord)
        assertEquals("jamais synchronisé", b.first { it.id == "tokens" }.stateWord)
    }
    @Test fun healthySnapshotIsGreenInTheDisplayOrder() {
        val b = healthy().badges()
        assertEquals(listOf("internet", "wifi", "bluetooth", "storage", "phones", "licence", "tokens"), b.map { it.id })
        for (x in b) assertEquals(StatusLevel.OK, x.level, x.id)
    }
    @Test fun orderWithEverythingPresent() {
        val s = healthy().copy(wifiDirectEnabled = true, wifiDirect = DirectPhase.GROUP_ACTIVE, copyRunning = true, copyPercent = 5, quiz = QuizLink.CONNECTED, quizLatencyMs = 100)
        assertEquals(listOf("internet", "wifi", "bluetooth", "storage", "wifi_direct", "phones", "copy", "licence", "quiz", "tokens"), s.badges().map { it.id })
    }
    @Test fun textsCarryTheLabelAndTheStateWord() {
        val s = healthy().copy(wifiRssiDbm = -80, internet = InternetPath.VIA_PHONE, storageFreeBytes = 12 * GB, licenceTiming = LicenceTiming(0, 106 * DAY), btEnabled = false)
        val m = byId(s)
        assertEquals("Wi-Fi · signal faible", m["wifi"]!!.text)
        assertEquals("Internet · lent", m["internet"]!!.text)
        assertEquals("Stockage · 12 % libre", m["storage"]!!.text)
        assertEquals("Téléphones · 3/8", m["phones"]!!.text)
        assertEquals("Licence · expire dans 6 j", m["licence"]!!.text)
        assertEquals("Bluetooth · désactivé", m["bluetooth"]!!.text)
        assertEquals(StatusLevel.OFF, m["bluetooth"]!!.level)
        assertEquals("Wi-Fi · connecté", healthy().badges().first { it.id == "wifi" }.text)
        assertEquals("Bluetooth · prêt", healthy().badges().first { it.id == "bluetooth" }.text)
    }
    @Test fun everyBadgeAlwaysHasAStateWordAndAFrenchLabel() {
        val worst = healthy().copy(wifiConnected = false, internet = InternetPath.NONE, btEnabled = true, bt = BtPhase.ADAPTER_ERROR, storageError = true,
            wifiDirectEnabled = true, wifiDirect = DirectPhase.FAILED, copyFailed = true, quiz = QuizLink.SERVER_UNREACHABLE, licence = LicenceState.EXPIRED, tokens = TokensSync.OFFLINE)
        for (s in listOf(StatusSnapshot(), healthy(), worst)) for (b in s.badges()) { assertTrue(b.label.isNotBlank(), b.id); assertTrue(b.stateWord.isNotBlank(), b.id) }
        assertTrue(worst.badges().count { it.level == StatusLevel.ERROR } >= 6)
    }
    @Test fun visibilityOfOptionalBadges() {
        val ids = healthy().badges().map { it.id }
        assertFalse("copy" in ids); assertFalse("quiz" in ids); assertFalse("wifi_direct" in ids)
        assertTrue("copy" in healthy().copy(copyFailed = true).badges().map { it.id })
        assertTrue("wifi_direct" in healthy().copy(wifiDirect = DirectPhase.FAILED).badges().map { it.id })
        assertFalse("licence" in healthy().copy(licence = LicenceState.NOT_REQUIRED).badges().map { it.id })
    }
    @Test fun trialIsOrangeDecidedByTheOwner() {   // essai = orange, décidé par le propriétaire (2026-10-04)
        val l = byId(healthy().copy(licence = LicenceState.TRIAL, licenceTiming = LicenceTiming(0, 130 * DAY)))["licence"]!!
        assertEquals(StatusLevel.WARN, l.level); assertEquals("essai", l.stateWord)
        assertEquals(StatusLevel.ERROR, byId(healthy().copy(licence = LicenceState.TRIAL, licenceTiming = LicenceTiming(0, 100 * DAY)))["licence"]!!.level)
    }
    @Test fun tokensStatesFromTheLastSnapshot() {
        assertEquals(StatusLevel.UNKNOWN, byId(healthy().copy(tokens = TokensSync.NEVER))["tokens"]!!.level)
        assertEquals(StatusLevel.OK, byId(healthy().copy(tokens = TokensSync.SYNCED, tokenBalance = 9))["tokens"]!!.level)
        assertEquals(StatusLevel.WARN, byId(healthy().copy(tokens = TokensSync.OFFLINE, tokenBalance = 9))["tokens"]!!.level)
        assertEquals(StatusLevel.ERROR, byId(healthy().copy(tokens = TokensSync.SYNCED, tokenBalance = 0))["tokens"]!!.level)
    }
    @Test fun unmeasuredFieldsAreGreyNotGreen() {
        val m = byId(healthy().copy(wifiEnabled = null, btEnabled = null, storagePresent = null, phones = null, licence = null, internet = InternetPath.UNTESTED))
        for (id in listOf("wifi", "bluetooth", "storage", "phones", "licence", "internet")) assertEquals(StatusLevel.UNKNOWN, m[id]!!.level, id)
    }

    // ---- « + n » ----
    private fun b(id: String, l: StatusLevel) = Badge(id, l, id, "x")
    @Test fun redAndOrangeAreNeverHiddenBehindPlusN() {
        val all = listOf(b("a", StatusLevel.OK), b("b", StatusLevel.OK), b("c", StatusLevel.ERROR), b("d", StatusLevel.OK), b("e", StatusLevel.WARN),
            b("f", StatusLevel.OK), b("g", StatusLevel.OK), b("h", StatusLevel.ERROR), b("i", StatusLevel.OK))
        for (max in 0..9) {
            val s = StatusBadges.split(all, max)
            assertTrue(s.hidden.none { it.urgent }, "max=$max masque une pastille urgente")
            assertEquals(all.size, s.visible.size + s.hidden.size)
        }
        val s = StatusBadges.split(all, 4)
        assertEquals(listOf("a", "c", "e", "h"), s.visible.map { it.id }.filter { it in setOf("a", "c", "e", "h") })
        assertEquals(4, s.visible.size)
    }
    @Test fun splitKeepsTheDisplayOrderAndFillsInOrder() {
        val all = (1..10).map { b("n$it", StatusLevel.OK) }
        val s = StatusBadges.split(all, 6)
        assertEquals((1..6).map { "n$it" }, s.visible.map { it.id })
        assertEquals((7..10).map { "n$it" }, s.hidden.map { it.id })
        val mixed = all.map { if (it.id == "n9") b("n9", StatusLevel.ERROR) else it }
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n9"), StatusBadges.split(mixed, 6).visible.map { it.id })
        assertEquals(all, StatusBadges.split(all, 20).visible); assertTrue(StatusBadges.split(all, 20).hidden.isEmpty())
    }
    @Test fun maxVisibleConstantsAreReadableOn720p() {
        assertTrue(StatusBadges.MAX_VISIBLE_PLAYER in 4..7); assertTrue(StatusBadges.MAX_VISIBLE_HOME >= StatusBadges.MAX_VISIBLE_PLAYER)
    }

    // ---- fusion avec la barre de connexions ----
    private fun icon(k: IconKind, s: IconState = IconState.CONNECTED) = StatusIcon(k, Tech.WIFI_LAN, null, k.label, s, 0)
    @Test fun mergeKeepsSnapshotFirstAndDoesNotDuplicateInternetOrWifiDirect() {
        val m = StatusBadges.merge(healthy().badges(), listOf(icon(IconKind.INTERNET), icon(IconKind.WIFI_DIRECT_GROUP), icon(IconKind.PHONE), icon(IconKind.SSH)))
        assertEquals(healthy().badges().size + 2, m.size)
        assertEquals(healthy().badges().map { it.id }, m.take(healthy().badges().size).map { it.id })
        assertEquals(1, m.count { it.id.startsWith("internet") })
        assertEquals(StatusLevel.WARN, m.first { it.id.startsWith("ssh") }.level)
        assertEquals(StatusLevel.OK, m.first { it.id.startsWith("phone") }.level)
    }

    // ---- fraîcheur ----
    @Test fun neverRefreshedBoardIsGreyAndRefreshesAreSeen() {
        var now = 1_000L
        val board = StatusBoard { now }
        assertTrue(board.current().all { it.level == StatusLevel.UNKNOWN })
        board.update(healthy())
        assertEquals(StatusLevel.OK, board.current().first { it.id == "wifi" }.level)
        now += 5_000; board.update(healthy().copy(wifiConnected = false))
        assertEquals(StatusLevel.ERROR, board.current().first { it.id == "wifi" }.level, "le changement doit se voir au relevé suivant")
    }
    @Test fun aStaleSnapshotIsNeverGreen() {
        var now = 0L
        val board = StatusBoard { now }
        board.update(healthy())
        now += StatusBadges.STALE_MS
        assertEquals(StatusLevel.OK, board.current().first { it.id == "wifi" }.level)
        now += 1
        val c = board.current()
        assertTrue(c.all { it.level == StatusLevel.UNKNOWN && it.stateWord == "périmé" })
        assertEquals(healthy().badges().map { it.id }, c.map { it.id })
        board.update(healthy()); assertEquals(StatusLevel.OK, board.current().first { it.id == "wifi" }.level)
    }
}
