package castbridge.core.relay

import castbridge.core.connect.MemoryKeyValueStore
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** relay-R1 § 3 : quand le téléphone ouvre le tuyau sans rien demander (synchronisé, coût, retrait), et quand il le ferme. */
class RelayPolicyTest {
    private val MB = 1024L * 1024L

    private fun input(
        synced: Boolean = true, optedOut: Boolean = false, net: PhoneNet = PhoneNet.UNMETERED, allowMobile: Boolean = false,
        used: Long = 0, cap: Long = RelayPolicy.DEFAULT_CAP_BYTES, need: PipeNeed? = PipeNeed.PLAY,
    ) = RelayInput(synced, optedOut, net, allowMobile, used, cap, need)

    private fun refused(i: RelayInput) = (RelayPolicy.decide(i) as RelayDecision.Refuse).reason

    @Test fun anUnmeteredNetworkIsFreeWithNoByteLimit() {
        assertEquals(RelayDecision.Open(null), RelayPolicy.decide(input(net = PhoneNet.UNMETERED, used = 900 * MB)))
        assertEquals(RelayDecision.Open(null), RelayPolicy.decide(input(net = PhoneNet.UNMETERED, need = PipeNeed.UPDATE_NOW)), "même une mise à jour : réseau non facturé")
    }

    @Test fun theDefaultCapIsFiveMegabytesADay() {
        assertEquals(5 * MB, RelayPolicy.DEFAULT_CAP_BYTES)
        assertEquals(10 * 60_000L, RelayPolicy.IDLE_STOP_MS)
    }

    @Test fun aMeteredNetworkOpensWithTheRemainingOfTheDailyCap() {
        assertEquals(RelayDecision.Open(5 * MB), RelayPolicy.decide(input(net = PhoneNet.METERED)))
        assertEquals(RelayDecision.Open(2 * MB), RelayPolicy.decide(input(net = PhoneNet.METERED, used = 3 * MB)))
        assertEquals(RelayDecision.Open(1), RelayPolicy.decide(input(net = PhoneNet.METERED, used = 5 * MB - 1)))
        for (need in listOf(PipeNeed.PLAY, PipeNeed.WALLET, PipeNeed.ASSIST)) assertEquals(RelayDecision.Open(5 * MB), RelayPolicy.decide(input(net = PhoneNet.METERED, need = need)), need.name)
    }

    @Test fun theCapIsRespectedAtTheByte() {
        assertEquals(RelayReason.CAP, refused(input(net = PhoneNet.METERED, used = 5 * MB)))
        assertEquals(RelayReason.CAP, refused(input(net = PhoneNet.METERED, used = 5 * MB + 1)))
        assertEquals(RelayReason.CAP, refused(input(net = PhoneNet.METERED, used = 1 * MB, cap = 1 * MB)))
        assertEquals(RelayDecision.Open(1), RelayPolicy.decide(input(net = PhoneNet.METERED, used = 1 * MB - 1, cap = 1 * MB)))
    }

    @Test fun bulkNeverGoesOverMeteredDataWithoutTheExplicitSetting() {
        assertEquals(RelayReason.METERED_BULK, refused(input(net = PhoneNet.METERED, need = PipeNeed.UPDATE_NOW)), "REL-F7 : 0 octet bulk sur réseau facturé")
        assertEquals(RelayReason.METERED_BULK, refused(input(net = PhoneNet.METERED, need = PipeNeed.UPDATE_NOW, used = 0)), "même le premier octet")
        assertEquals(RelayDecision.Open(null), RelayPolicy.decide(input(net = PhoneNet.METERED, need = PipeNeed.UPDATE_NOW, allowMobile = true)))
    }

    @Test fun theMobileDataSettingLiftsTheLimit() {
        assertEquals(RelayDecision.Open(null), RelayPolicy.decide(input(net = PhoneNet.METERED, allowMobile = true, used = 400 * MB)))
    }

    @Test fun noNetworkMeansOfflineWhateverTheSettings() {
        assertEquals(RelayReason.OFFLINE, refused(input(net = PhoneNet.NONE)))
        assertEquals(RelayReason.OFFLINE, refused(input(net = PhoneNet.NONE, allowMobile = true)))
    }

    @Test fun onlyASynchronizedPhoneRelaysAndTheOwnerCanWithdraw() {
        assertEquals(RelayReason.NOT_SYNCED, refused(input(synced = false)))
        assertEquals(RelayReason.OPTED_OUT, refused(input(optedOut = true)), "« Ne plus relayer pour cette TV »")
        assertEquals(RelayReason.NOT_SYNCED, refused(input(synced = false, optedOut = true)), "sans synchronisation on ne répond rien d'autre")
        assertEquals(RelayReason.OPTED_OUT, refused(input(optedOut = true, net = PhoneNet.NONE)), "le retrait prime sur le réseau")
    }

    @Test fun reasonsHaveStableWireNames() {
        assertEquals(setOf("nosync", "optout", "offline", "metered_bulk", "cap", "background", "busy"), RelayReason.values().map { it.wire }.toSet())
        for (r in RelayReason.values()) assertEquals(r, RelayReason.of(r.wire))
        assertNull(RelayReason.of("inconnu"))
        assertNull(RelayReason.of(null))
    }

    @Test fun needsKnowTheirCostClass() {
        assertTrue(PipeNeed.UPDATE_NOW.bulk)
        for (n in listOf(PipeNeed.PLAY, PipeNeed.WALLET, PipeNeed.ASSIST)) assertFalse(n.bulk, n.name)
        for (n in PipeNeed.values()) assertEquals(n, PipeNeed.of(n.wire))
        assertNull(PipeNeed.of("x"))
    }

    // ------------------------------------------------------------------ compteur du jour

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: String = "Africa/Douala") = ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()

    @Test fun theMeterCountsPerLocalDayAndPersists() {
        val kv = MemoryKeyValueStore()
        var now = at(2026, 10, 7, 9, 0)
        val m = RelayMeter(kv, { now }, ZoneId.of("Africa/Douala"))
        assertEquals(0, m.usedToday())
        m.add(1_000); m.add(500)
        assertEquals(1_500, m.usedToday())
        assertEquals(1_500, RelayMeter(kv, { now }, ZoneId.of("Africa/Douala")).usedToday(), "survit au redémarrage de l'application (compteur persisté)")
        now = at(2026, 10, 7, 23, 59)
        assertEquals(1_500, m.usedToday())
        now = at(2026, 10, 8, 0, 1)
        assertEquals(0, m.usedToday(), "un nouveau jour : compteur remis à zéro")
        m.add(10)
        assertEquals(10, m.usedToday())
    }

    @Test fun theMeterIgnoresNegativeAndHugeIncrementsAndNeverOverflows() {
        val m = RelayMeter(MemoryKeyValueStore(), { 0L }, ZoneId.of("UTC"))
        m.add(-50); m.add(0)
        assertEquals(0, m.usedToday())
        m.add(Long.MAX_VALUE); m.add(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, m.usedToday(), "saturé, jamais négatif")
    }

    @Test fun aDamagedMeterReadsAsZero() {
        val kv = MemoryKeyValueStore()
        kv.put("relay.day", "2026-10-07"); kv.put("relay.bytes", "pas un nombre")
        assertEquals(0, RelayMeter(kv, { at(2026, 10, 7, 10, 0) }, ZoneId.of("Africa/Douala")).usedToday())
    }

    @Test fun theMeterUsesTheLocalDayOfThePhoneNotUtc() {
        val kv = MemoryKeyValueStore()
        // 23:30 à Douala (UTC+1) = 22:30 UTC ; 00:30 le lendemain à Douala = 23:30 UTC la veille : deux jours locaux, un seul jour UTC
        var now = at(2026, 10, 7, 23, 30)
        val m = RelayMeter(kv, { now }, ZoneId.of("Africa/Douala"))
        m.add(100)
        now = at(2026, 10, 8, 0, 30)
        assertEquals(0, m.usedToday())
    }

    // ------------------------------------------------------------------ arrêt à l'inactivité

    @Test fun theIdleStopFiresTenMinutesAfterTheLastConnection() {
        val s = IdleStop(start = 0)
        assertFalse(s.expired(RelayPolicy.IDLE_STOP_MS - 1))
        assertTrue(s.expired(RelayPolicy.IDLE_STOP_MS))
        // une connexion ouverte repousse l'échéance
        s.update(now = 5 * 60_000, openStreams = 2, totalBytes = 1_000)
        assertFalse(s.expired(5 * 60_000 + RelayPolicy.IDLE_STOP_MS - 1))
        s.update(now = 9 * 60_000, openStreams = 0, totalBytes = 1_000)    // connexion fermée : la dernière activité reste celle de 5 min
        assertTrue(s.expired(5 * 60_000 + RelayPolicy.IDLE_STOP_MS), "dix minutes après la dernière fois où une connexion était ouverte")
    }

    @Test fun trafficKeepsTheLinkAliveButKeepalivePingsDoNot() {
        val s = IdleStop(start = 0)
        s.update(now = 60_000, openStreams = 0, totalBytes = 0)
        s.update(now = 120_000, openStreams = 0, totalBytes = 14)         // un PING aller-retour : 14 octets, du bruit
        s.update(now = 180_000, openStreams = 0, totalBytes = 28)
        assertTrue(s.expired(RelayPolicy.IDLE_STOP_MS), "des PING ne tiennent pas le tuyau ouvert")
        s.update(now = 200_000, openStreams = 0, totalBytes = 28 + IdleStop.NOISE_BYTES)   // du vrai trafic (une poignée de main TLS)
        assertFalse(s.expired(200_000 + RelayPolicy.IDLE_STOP_MS - 1))
        assertTrue(s.expired(200_000 + RelayPolicy.IDLE_STOP_MS))
    }
}
