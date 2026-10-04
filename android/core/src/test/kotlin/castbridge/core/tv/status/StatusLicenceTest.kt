package castbridge.core.tv.status

import castbridge.core.lots.Right
import castbridge.core.owner.*
import kotlin.test.*

/**
 * Règle du propriétaire (2026-10-04) : « la production s'affiche en rouge 1 semaine avant la limite et orange à la moitié du temps écoulé ».
 * Bornes exactes, horloge injectée (nowMs), jamais de vert sans durée connue.
 */
class StatusLicenceTest {
    private val DAY = StatusThresholds.DAY_MS
    private val T = 1_800_000_000_000L
    private fun valid(totalDays: Long, elapsedDays: Long, extraMs: Long = 0): Verdict =
        StatusRules.licence(LicenceState.VALID, LicenceTiming(T, T + totalDays * DAY), T + elapsedDays * DAY + extraMs)
    private fun level(totalDays: Long, elapsedDays: Long, extraMs: Long = 0) = valid(totalDays, elapsedDays, extraMs).level

    @Test fun ninetyDayKeyIsGreenAt44OrangeAt45RedAt83() {
        assertEquals(StatusLevel.OK, level(90, 0)); assertEquals(StatusLevel.OK, level(90, 44))
        assertEquals(StatusLevel.OK, level(90, 45, -1), "une milliseconde avant la moitié")
        assertEquals(StatusLevel.WARN, level(90, 45)); assertEquals(StatusLevel.WARN, level(90, 82))
        assertEquals(StatusLevel.WARN, level(90, 83, -1), "une milliseconde avant les 7 derniers jours")
        assertEquals(StatusLevel.ERROR, level(90, 83)); assertEquals(StatusLevel.ERROR, level(90, 89))
        assertEquals(StatusLevel.ERROR, level(90, 90)); assertEquals(StatusLevel.ERROR, level(90, 120))
    }
    @Test fun shortKeysRedWinsOrangePeriodIsEmptyNeverNegative() {
        for (e in 0..2) assertEquals(StatusLevel.OK.takeIf { e < 5 }, level(10, e.toLong()), "D=10, $e j écoulés")
        assertEquals(StatusLevel.ERROR, level(10, 3)); assertEquals(StatusLevel.ERROR, level(10, 5)); assertEquals(StatusLevel.ERROR, level(10, 9))
        assertEquals(StatusLevel.OK, level(10, 3, -1)); assertNotEquals(StatusLevel.WARN, level(10, 3))
        for (e in 0..10) assertNotEquals(StatusLevel.WARN, level(10, e.toLong()), "D=10 n'a jamais d'orange")
        assertEquals(StatusLevel.ERROR, level(14, 7)); assertEquals(StatusLevel.OK, level(14, 7, -1)); assertNotEquals(StatusLevel.WARN, level(14, 7))
        assertEquals(StatusLevel.WARN, level(15, 7, 1 + DAY / 2)); assertEquals(StatusLevel.ERROR, level(15, 8))
        assertEquals(StatusLevel.ERROR, level(3, 0), "clé de 3 jours : rouge dès le début")
    }
    @Test fun wordsCarryTheRemainingTime() {
        assertEquals("expire dans 6 j", valid(90, 84).word)
        assertEquals("expire dans 7 j", valid(90, 83).word)
        assertEquals("45 j restants", valid(90, 45).word)
        assertEquals("46 j restants", valid(90, 44).word)
        assertEquals("expirée", valid(90, 91).word)
        assertEquals("Licence · expire dans 6 j", valid(90, 84).text("Licence"))
        assertEquals("Licence · 45 j restants", valid(90, 45).text("Licence"))
    }
    @Test fun unlimitedKeyIsAlwaysGreen() {
        for (d in listOf(0L, 100L, 5000L)) {
            val v = StatusRules.licence(LicenceState.VALID, LicenceTiming(T, null), T + d * DAY)
            assertEquals(StatusLevel.OK, v.level); assertEquals("illimitée", v.word)
        }
        assertEquals("Licence · illimitée", StatusRules.licence(LicenceState.VALID, LicenceTiming(T, null), T).text("Licence"))
    }
    @Test fun unknownDurationIsGreyNeverGreen() {
        val v = StatusRules.licence(LicenceState.VALID, null, T)
        assertEquals(StatusLevel.UNKNOWN, v.level); assertEquals("durée inconnue", v.word)
    }
    @Test fun expiredInvalidNoKeyAreRedAndPendingIsOrange() {
        for (s in listOf(LicenceState.EXPIRED, LicenceState.INVALID, LicenceState.NO_KEY)) assertEquals(StatusLevel.ERROR, StatusRules.licence(s, LicenceTiming(T, null), T).level, s.name)
        assertEquals(StatusLevel.ERROR, StatusRules.licence(LicenceState.VALID, LicenceTiming(T, T + 10 * DAY), T + 10 * DAY).level, "date de fin atteinte = expirée")
        assertEquals(StatusLevel.WARN, StatusRules.licence(LicenceState.PENDING_NOTIFICATION, LicenceTiming(T, null), T).level)
        assertEquals(StatusLevel.OFF, StatusRules.licence(LicenceState.NOT_REQUIRED, null, T).level)
    }
    @Test fun trialIsOrangeDecidedByTheOwnerRedInTheLastWeekAndWhenOver() {   // essai = orange, décidé par le propriétaire
        fun trial(totalDays: Long, elapsedDays: Long, extra: Long = 0) = StatusRules.licence(LicenceState.TRIAL, LicenceTiming(T, T + totalDays * DAY), T + elapsedDays * DAY + extra)
        assertEquals(StatusLevel.WARN, trial(30, 0).level); assertEquals("essai", trial(30, 0).word)
        assertEquals(StatusLevel.WARN, trial(30, 22).level); assertEquals(StatusLevel.WARN, trial(30, 23, -1).level)
        assertEquals(StatusLevel.ERROR, trial(30, 23).level, "à 7 jours de la fin")
        assertEquals(StatusLevel.ERROR, trial(30, 29).level)
        assertEquals(StatusLevel.ERROR, trial(30, 30).level); assertEquals("essai terminé", trial(30, 31).word)
        assertEquals(StatusLevel.WARN, StatusRules.licence(LicenceState.TRIAL, null, T).level)
    }

    // ---- lecture des activations signées (réutilise KeyBadge) ----
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val issuer = ActivationIssuer(Ed25519Signer(ByteArray(32) { (it + 9).toByte() }))
    private fun act(kind: ActivationKind, vararg rights: Right) = Activation.decode(issuer.issue(ActivationIssuer.Request(kind, DeviceCode.of(fp), fp, T, rights = rights.toList(),
        license = if (kind == ActivationKind.TRIAL) "trial" else "lic-1", seat = SeatIds.of(if (kind == ActivationKind.TRIAL) "trial" else "lic-1", fp))).token)!!

    @Test fun readerUsesTheSignedUsageRight() {
        val prod = act(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T), Right.Usage(T, T + 90 * DAY))
        val r = LicenceReader.read(listOf(prod), T + 45 * DAY, locked = true, pendingNotification = false)
        assertEquals(LicenceState.VALID, r.state); assertEquals(LicenceTiming(T, T + 90 * DAY), r.timing)
        assertEquals(StatusLevel.WARN, StatusRules.licence(r.state, r.timing, T + 45 * DAY).level)
        assertEquals(StatusLevel.OK, StatusRules.licence(r.state, r.timing, T + 44 * DAY).level)
        val unlimited = act(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T))
        assertEquals(null, LicenceReader.read(listOf(unlimited), T + 900 * DAY, true, false).timing!!.toMs)
        assertEquals(LicenceState.TRIAL, LicenceReader.read(listOf(act(ActivationKind.TRIAL, Right.Usage(T, T + 30 * DAY))), T + DAY, true, false).state)
        assertEquals(LicenceState.PENDING_NOTIFICATION, LicenceReader.read(listOf(prod), T, true, true).state)
        assertEquals(LicenceState.EXPIRED, LicenceReader.read(listOf(prod), T + 91 * DAY, true, false).state)
        assertEquals(LicenceState.NO_KEY, LicenceReader.read(emptyList(), T, true, false).state)
        assertEquals(LicenceState.NOT_REQUIRED, LicenceReader.read(listOf(prod), T, false, false).state)
    }
}
