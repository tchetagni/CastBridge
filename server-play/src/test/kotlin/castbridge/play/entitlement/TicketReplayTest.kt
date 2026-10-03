package castbridge.play.entitlement

import castbridge.play.ClientIp
import castbridge.play.HubFixture
import castbridge.play.HubFixture.config
import castbridge.play.HubFixture.hub
import castbridge.play.HubFixture.open
import castbridge.play.TestConn
import castbridge.play.TestKeys
import castbridge.play.TestRights
import java.net.InetAddress
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exigence I1 de l'audit Opus de w20-03 : un ticket ne vaut pas plus que son but (UNE salle), les salles par sujet et la création par adresse sont plafonnées, tout échoue FERMÉ. */
class TicketReplayTest {
    private val now = System.currentTimeMillis()

    @Test fun replayingOneTicketFourHundredTimesOpensExactlyOneRoom() {
        val h = hub(config("perIp" to 1_000_000))
        val ticket = TestKeys.ticket(now = now, deviceId = "dev-replay")
        val conns = (0 until 400).map { open(h, ticket) }
        assertEquals(1, h.roomCount(), "un seul ticket, une seule salle")
        assertEquals(1, conns.count { it.welcomed() })
        val refused = conns.filter { !it.welcomed() }
        assertEquals(399, refused.size)
        assertTrue(refused.all { it.errorReason() == "PLAY_TICKET_REFUSED" }, refused.map { it.errorReason() }.toSet().toString())
        assertEquals(1, h.usedTicketCount(), "un seul jti mémorisé")
    }

    @Test fun twoTicketsOfTheSameSubjectCannotExceedThePerSubjectCapButAnotherSubjectIsNotAffected() {
        val h = hub(config("perSubject" to 2))
        val a = (0 until 3).map { open(h, TestKeys.ticket(now = now, deviceId = "dev-same")) }
        assertEquals(listOf(true, true, false), a.map { it.welcomed() }, "deux salles ouvertes, la troisième refusée")
        assertEquals("PLAY_BUSY", a[2].errorReason()); assertTrue("2 parties" in a[2].errorMessage()!!, a[2].errorMessage())
        val other = open(h, TestKeys.ticket(now = now, deviceId = "dev-other", deviceCode = TestRights.otherTv.code), TestRights.create(TestRights.activation(device = TestRights.otherTv)))
        assertTrue(other.welcomed(), "un autre appareil n'est pas touché")
        assertEquals(3, h.roomCount())
    }

    @Test fun theSubjectCapIsConfigurableAndOneMeansOne() {
        val h = hub(config("perSubject" to 1))
        assertTrue(open(h, TestKeys.ticket(now = now, deviceId = "dev-one")).welcomed())
        assertEquals("PLAY_BUSY", open(h, TestKeys.ticket(now = now, deviceId = "dev-one")).errorReason())
    }

    @Test fun expiredNotYetValidWrongKeyWrongAudienceAndBlockedTicketsOpenNothing() {
        val h = hub()
        val tickets = listOf(
            "expiré" to TestKeys.ticket(now = now - 300_000, lifeMs = 60_000),
            "pas encore valable" to TestKeys.ticket(now = now, iat = now + 600_000),
            "mauvaise clé" to TestKeys.ticket(pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()),
            "mauvaise audience" to TestKeys.ticket(aud = "autre-service"),
            "appareil bloqué" to TestKeys.ticket(blocked = true),
            "sans jti" to TestKeys.ticket(jti = null),
            "absent" to "",
        )
        for ((why, t) in tickets) assertEquals("PLAY_TICKET_REFUSED", open(h, t.ifEmpty { null }).errorReason(), why)
        assertEquals(0, h.roomCount()); assertEquals(0, h.usedTicketCount(), "un ticket refusé ne laisse aucune trace en mémoire")
    }

    @Test fun anAbsentVerifierKeyMeansNoRoomCanBeCreated() {
        val h = hub(verifier = TicketVerifier(emptyList()))
        assertEquals("PLAY_TICKET_REFUSED", open(h, TestKeys.ticket()).errorReason())
        assertEquals(0, h.roomCount())
    }

    @Test fun roomCreationIsRateLimitedPerClientAddressAndAnIpv6SlashSixtyFourIsOneAddress() {
        val h = hub(config("perIp" to 3))
        val results = (0 until 5).map { open(h, TestKeys.ticket(now = now), c = TestConn("203.0.113.5")) }
        assertEquals(listOf(true, true, true, false, false), results.map { it.welcomed() })
        assertEquals("PLAY_BUSY", results[3].errorReason()); assertTrue("adresse" in results[3].errorMessage()!!)
        assertTrue(open(h, TestKeys.ticket(now = now), c = TestConn("203.0.113.6")).welcomed(), "une autre adresse n'est pas touchée")
        // IPv6 : 2^16 adresses d'un même /64 partagent UN plafond (la clé de limite est déjà réduite au /64 par le transport)
        val h6 = hub(config("perIp" to 2))
        val keys = (1..4).map { ClientIp.resolve(InetAddress.getByName("2001:db8:0:1::$it"), null, emptyList()) }
        assertEquals(1, keys.toSet().size)
        val r6 = keys.map { open(h6, TestKeys.ticket(now = now), c = TestConn(it)) }
        assertEquals(listOf(true, true, false, false), r6.map { it.welcomed() })
    }

    @Test fun usedTicketMemoryIsBoundedAndTheServiceFailsClosedWhenFull() {
        val h = hub(config("used" to 5))
        val r = (0 until 8).map { open(h, TestKeys.ticket(now = now)) }
        assertEquals(5, r.count { it.welcomed() })
        assertTrue(h.usedTicketCount() <= 5)
        assertEquals("PLAY_BUSY", r[7].errorReason())
    }

    @Test fun theTickIsSweepingUsedTicketsThatHaveExpired() {
        val h = hub()
        open(h, TestKeys.ticket(now = now - 50_000, lifeMs = 60_000))      // valable encore 10 s
        assertEquals(1, h.usedTicketCount())
        h.sweepUsedTickets(now + 20_000)
        assertEquals(0, h.usedTicketCount())
    }
}
