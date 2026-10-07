package castbridge.core.gateway

import castbridge.core.tv.BtProtocol
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-28 compatibility (relay-R4, inventory I-1): the gateway has its own RFCOMM UUID (…0007); an old TV only has the old one (…0002, shared with the SSH tunnel), an old phone only
 * asks for the old one. The pure rules of both sides, with a fake Bluetooth (a dial function that fails for a UUID the TV does not offer) and a fake clock.
 */
class GatewayServiceTest {
    private val new = Gw.SERVICE_UUID
    private val old = Gw.LEGACY_SERVICE_UUID

    // ------------------------------------------------------------------ TV

    @Test fun theTvListensOnItsOwnUuidAlwaysAndOnTheOldOneOnlyWhileItsSshOverBluetoothIsOff() {
        assertEquals(listOf(new, old), GatewayService.tvListens(sshBluetoothOn = false), "an old phone still finds the gateway")
        assertEquals(listOf(new), GatewayService.tvListens(sshBluetoothOn = true), "the SSH tunnel owns the old UUID while it is on")
    }

    @Test fun theGatewayAndTheSshTunnelNeverListenOnTheSameUuid() {
        for (sshOn in listOf(false, true)) {
            val listeners = GatewayService.tvListens(sshOn) + (if (sshOn) listOf(BtProtocol.SSH_SERVICE_UUID) else emptyList()) +
                listOf(BtProtocol.SERVICE_UUID, BtProtocol.API_SERVICE_UUID, BtProtocol.API_MUX_SERVICE_UUID, castbridge.core.owner.OwnerFrames.SERVICE_UUID)
            assertEquals(listeners.size, listeners.toSet().size, "sshOn=$sshOn : two listeners on one UUID: ${listeners.groupBy { it }.filterValues { it.size > 1 }.keys}")
        }
    }

    @Test fun theOldUuidReturnsToTheGatewayWhenTheSshStops() {
        // the transitions of BtGatewayHost.sshBluetoothChanged: wanted -> closed, not wanted -> open again
        val seen = listOf(false, true, true, false).map { GatewayService.tvListens(it).contains(old) }
        assertEquals(listOf(true, false, false, true), seen)
    }

    // ------------------------------------------------------------------ phone: which UUIDs, in which order

    @Test fun anUnknownTvIsAskedForTheNewServiceFirstThenTheOldOne() {
        assertEquals(listOf(new, old), GatewayService.PhoneChoice().order(null))
        assertEquals(listOf(new, old), GatewayService.PhoneChoice().order(emptyList()))
    }

    @Test fun aTvThatAnnouncesTheNewServiceIsNeverAskedForTheOldOne() {
        val c = GatewayService.PhoneChoice()
        assertEquals(listOf(new), c.order(listOf(BtProtocol.SERVICE_UUID, new.uppercase())), "case does not matter: Android may spell the UUID either way")
        // not even after an old TV was met: this TV announces the new service, so it was updated since (the old UUID may now be its SSH tunnel)
        c.connected(old)
        assertEquals(listOf(new), c.order(listOf(new)))
    }

    @Test fun aServiceListWithoutTheNewUuidProvesNothing() {
        // Android's cache may predate the update of the TV: the new service is still asked first
        assertEquals(listOf(new, old), GatewayService.PhoneChoice().order(listOf(BtProtocol.SERVICE_UUID, BtProtocol.API_SERVICE_UUID)))
    }

    @Test fun anOldTvIsRememberedThenTheNewServiceIsAskedAgainAfterTheRecheckDelay() {
        var t = 1_000_000L
        val c = GatewayService.PhoneChoice(now = { t }, legacyRecheckMs = 10 * 60_000L)
        c.connected(old)
        assertEquals(listOf(old), c.order(null), "straight to the only service of an old TV")
        t += 9 * 60_000L; assertEquals(listOf(old), c.order(null))
        t += 61_000L; assertEquals(listOf(new, old), c.order(null), "an updated TV may have appeared")
    }

    @Test fun connectingToTheNewServiceForgetsTheMemoryOfAnOldTv() {
        var t = 0L
        val c = GatewayService.PhoneChoice(now = { t })
        c.connected(old); c.connected(new)
        assertEquals(listOf(new, old), c.order(null))
    }

    // ------------------------------------------------------------------ phone: the whole attempt

    /** A TV that offers only [offered]; every other UUID fails like Android's « service discovery failed ». */
    private fun tv(vararg offered: String, away: Boolean = false, tried: MutableList<String> = mutableListOf()): (String) -> String = { u ->
        tried += u
        if (away || u !in offered) throw IOException("service discovery failed") else "link:$u"
    }

    @Test fun aNewTvIsReachedOnItsOwnServiceWithoutAnyPause() {
        val pauses = mutableListOf<Long>(); val tried = mutableListOf<String>()
        val c = GatewayService.PhoneChoice()
        assertEquals("link:$new", c.connect(null, sleep = { pauses += it }, dial = tv(new, old, tried = tried)))
        assertEquals(listOf(new), tried); assertEquals(emptyList(), pauses)
    }

    @Test fun anOldTvIsReachedOnTheOldServiceAfterThePauseTheBluetoothStackNeeds() {
        val pauses = mutableListOf<Long>(); val tried = mutableListOf<String>(); val log = mutableListOf<String>()
        val c = GatewayService.PhoneChoice()
        assertEquals("link:$old", c.connect(null, sleep = { pauses += it }, log = { log += it }, dial = tv(old, tried = tried)))
        assertEquals(listOf(new, old), tried)
        assertEquals(listOf(GatewayService.GAP_MS), pauses, "never two connect() back to back to the same TV")
        assertTrue(log.any { "old TV" in it }, log.toString())
        // the gateway HELLO is answered on that connection (R-36: only now is the TV known to be an old one): the next connection of the same run goes straight to the old service, no failed attempt, no pause
        c.helloAnswered()
        tried.clear(); pauses.clear()
        assertEquals("link:$old", c.connect(null, sleep = { pauses += it }, dial = tv(old, tried = tried)))
        assertEquals(listOf(old), tried); assertEquals(emptyList(), pauses)
    }

    @Test fun aLegacyServiceThatAnswersTheConnectionButNotTheHelloIsNotRememberedAsAnOldTv() {
        // R-36 (audit I-8) : une TV À JOUR dont le SSH par Bluetooth est allumé écoute …0002 pour son tunnel SSH ; si …0007 échoue un instant (connexion concurrente, cache SDP sans …0007),
        // sshd répond à la connexion sur …0002 : ce n'est PAS la passerelle (aucune poignée de main). Retenir « TV ancienne » 10 minutes enverrait chaque essai suivant au seul …0002.
        val c = GatewayService.PhoneChoice()
        val tried = mutableListOf<String>()
        var newIsBusy = true                                   // …0007 échoue la première fois seulement
        assertEquals("link:$old", c.connect(null, sleep = {}) { u -> tried += u; if (u == new && newIsBusy) { newIsBusy = false; throw IOException("already at opened state") } else "link:$u" })
        assertEquals(listOf(new, old), tried)
        assertEquals(listOf(new, old), c.order(null), "…0002 a répondu à la connexion, personne n'a répondu au HELLO de la passerelle : la TV n'est pas connue comme ancienne")
        tried.clear()
        assertEquals("link:$new", c.connect(null, sleep = {}) { u -> tried += u; "link:$u" })
        assertEquals(listOf(new), tried, "l'essai suivant revient à …0007 : la passerelle n'est pas perdue 10 minutes")
    }

    @Test fun theOldUuidIsRememberedOnlyWhenTheGatewayHelloWasAnswered() {
        var t = 0L
        val c = GatewayService.PhoneChoice(now = { t }, legacyRecheckMs = 10 * 60_000L)
        assertEquals("link:$old", c.connect(null, sleep = {}, dial = tv(old)))
        assertEquals(listOf(new, old), c.order(null), "connected, not yet answered")
        c.helloAnswered()
        assertEquals(listOf(old), c.order(null), "the HELLO was answered on the old UUID: an old TV")
        t += 10 * 60_000L + 1; assertEquals(listOf(new, old), c.order(null), "…for ten minutes")
        c.helloAnswered()                                       // nothing connected since: nothing changes
        assertEquals(listOf(new, old), c.order(null))
    }

    @Test fun aHelloAnsweredOnTheNewUuidForgetsAnOldTv() {
        val c = GatewayService.PhoneChoice()
        c.connected(old)                                        // an old TV was met
        assertEquals("link:$new", c.connect(listOf(new), sleep = {}, dial = tv(new)))
        c.helloAnswered()
        assertEquals(listOf(new, old), c.order(null), "the TV was updated since")
    }

    @Test fun aNewerConnectionReplacesTheOneWaitingForItsHello() {
        // …0002 answered a connection but no HELLO ever came ; the next attempt reaches …0007 : a late helloAnswered() must not resurrect the old UUID
        val c = GatewayService.PhoneChoice()
        assertEquals("link:$old", c.connect(null, sleep = {}, dial = tv(old)))
        assertEquals("link:$new", c.connect(null, sleep = {}, dial = tv(new, old)))
        c.helloAnswered()
        assertEquals(listOf(new, old), c.order(null))
    }

    @Test fun aTvThatIsAwayFailsOnBothAttemptsAndTheLastErrorIsRaised() {
        val tried = mutableListOf<String>()
        val c = GatewayService.PhoneChoice()
        val e = assertFailsWith<IOException> { c.connect(null, sleep = {}, dial = tv(away = true, tried = tried)) }
        assertEquals("service discovery failed", e.message)
        assertEquals(listOf(new, old), tried)
        assertFalse(c.order(null) == listOf(old), "nothing is remembered: the TV was not met")
    }

    @Test fun aTvThatAnnouncesTheNewServiceIsNotAskedForTheOldOneEvenWhenTheNewOneFails() {
        val tried = mutableListOf<String>()
        assertFailsWith<IOException> { GatewayService.PhoneChoice().connect(listOf(new), sleep = {}, dial = tv(away = true, tried = tried)) }
        assertEquals(listOf(new), tried, "on an updated TV the old UUID may be its SSH tunnel: the phone's HELLO and PIN must never be written to it")
    }

    @Test fun aStopRequestedBetweenTheAttemptsEndsThem() {
        var stop = false; val tried = mutableListOf<String>()
        val e = assertFailsWith<IOException> {
            GatewayService.PhoneChoice().connect<String>(null, stopping = { stop }, sleep = {}) { u -> tried += u; stop = true; throw IOException("closed by the user") }
        }
        assertEquals(listOf(new), tried, "stopped after the first attempt, the old service is not tried")
        assertEquals("closed by the user", e.message)
        assertEquals("arrêt demandé", assertFailsWith<IOException> { GatewayService.PhoneChoice().connect<String>(null, stopping = { true }, sleep = {}) { "never" } }.message)
    }
}
