package castbridge.play.entitlement

import castbridge.core.lots.Right
import castbridge.core.owner.ActivationKind
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.HubFixture
import castbridge.play.HubFixture.config
import castbridge.play.HubFixture.hub
import castbridge.play.HubFixture.open
import castbridge.play.PlayConfig
import castbridge.play.TestConn
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.TestRights.DAY
import castbridge.play.dev
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Audit Opus de w20-04 : B1 (activation copiée), B2 (révocations fermées), I1 (déni de service par jti), I3 (locations horaires), I4 (salles d'essai à 8). */
class AuditW2004Test {
    private val now = System.currentTimeMillis()
    private fun t(i: Int) = TestKeys.ticket(now = now, deviceId = "dev-fake-$i")

    // ---- B1 : la même activation sur N faux appareils ne dépasse jamais le plafond par identité ----

    @Test fun manyFakeDevicesCarryingTheSameActivationNeverExceedThePerIdentityRoomCap() {
        val h = hub(config("perSubject" to 2))
        val results = (0 until 12).map { open(h, t(it), TestRights.create(TestRights.PROD)) }
        assertEquals(2, results.count { it.welcomed() }, "même activation = même identité = au plus 2 salles, quels que soient les appareils API")
        assertEquals(2, h.roomCount())
        assertEquals("PLAY_BUSY", results.last().errorReason())
    }

    @Test fun theTrialIdentityHoldsOneRoomEvenWithFreshDevices() {
        val h = hub(config("perSubject" to 2))
        val trial = TestRights.activation(ActivationKind.TRIAL, rights = TestRights.trialUsage(now))
        assertEquals(1, (0 until 5).count { open(h, t(it), TestRights.create(trial)).welcomed() })
    }

    @Test fun aProductionIdentityHasADailyCreationCapAcrossFreshDevices() {
        val h = hub(config("perSubject" to 1000, "perIdentityDay" to 5))
        val results = (0 until 9).map { open(h, t(it), TestRights.create(TestRights.PROD)) }
        assertEquals(5, results.count { it.welcomed() }, "5 par jour et par identité d'activation, même avec 9 appareils frais")
        assertTrue(results.last().errorMessage()!!.contains("aujourd'hui"), results.last().errorMessage())
        // une autre activation (autre identité) n'est pas touchée
        assertTrue(open(h, TestKeys.ticket(now = now, deviceId = "dev-other-tv", deviceCode = TestRights.otherTv.code), TestRights.create(TestRights.activation(device = TestRights.otherTv))).welcomed(), "une autre identité n'est pas touchée")
    }

    // ---- B2 : fermé tant que les révocations ne sont pas connues ----

    @Test fun createIsRefusedRetryableWhileNoRevocationListIsAcceptedAndTheTicketIsNotBurned() {
        var ready = false
        val h = hub(ready = { ready })
        val ticket = t(1)
        val c = open(h, ticket)
        assertEquals("PLAY_MAINTENANCE", c.errorReason()); assertTrue(c.lastError()!!["retryable"] == true)
        assertEquals(0, h.roomCount()); assertEquals(0, h.usedTicketCount(), "le ticket n'est pas brûlé par un refus de service")
        ready = true
        assertTrue(open(h, ticket).welcomed(), "le même ticket sert ensuite")
    }

    @Test fun startupWithoutARevocationsUrlIsRefusedUnlessDirect() {
        val base = mapOf("CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "10.0.0.1/32")
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ base[it] }) }
        val ok = PlayConfig.fromEnv({ (base + ("CASTBRIDGE_PLAY_REVOCATIONS_URL" to "https://bridge.sti-cm.com/api/v1/revocations"))[it] })
        assertEquals("https://bridge.sti-cm.com/api/v1/revocations", ok.revocationsUrl)
        PlayConfig.fromEnv({ (base + ("CASTBRIDGE_PLAY_DIRECT" to "1"))[it] })
    }

    // ---- I1 : un /48 ne peut pas brûler la table des jti ----

    @Test fun oneIpv6Slash48CannotMakeMoreCreationsThanItsHourlyCap() {
        val h = hub(config("per48" to 5, "perSubject" to 1000, "perIdentityDay" to 1000))
        val ips = (0 until 20).map { "v6:2001:0db8:abcd:%04x::/64".format(it) }
        val results = ips.mapIndexed { i, ip -> open(h, t(i), TestRights.create(TestRights.PROD), TestConn(ip)) }
        assertEquals(5, results.count { it.welcomed() }, "au plus 5 salles pour tout un /48")
        assertEquals(5, h.usedTicketCount(), "les créations refusées par le /48 ne brûlent pas de jti")
    }

    @Test fun theDefaultJtiCapIsTwoHundredThousand() { assertEquals(200_000, PlayConfig().maxUsedTickets) }

    // ---- I3 : une location horaire n'est pas couverte en ligne (le service ne compte pas les minutes) ----

    @Test fun anHourlyRentalLineIsNotHonouredOnlineButADailyOneIs() {
        val eval = HostRightsEvaluator(TrustedIssuers.parse(TestRights.trustedSpec).ring, { castbridge.core.owner.RevocationState() })
        val hourly = TestRights.activation(now = now, rights = listOf(Right.Rental("loc-h", listOf("cm2"), now - DAY, now - DAY, 30, maxUsageMinutes = 600)))
        val r = eval.evaluate(TestRights.CODE, listOf(hourly), now)
        assertTrue(r.coveredScopes.isEmpty(), "location horaire : aucune question réservée en ligne")
        val daily = TestRights.activation(now = now, rights = listOf(TestRights.rental("cm2", now - DAY)))
        assertEquals(setOf("cm2"), eval.evaluate(TestRights.CODE, listOf(daily), now).coveredScopes)
    }

    // ---- I4 : salle d'essai = 8 joueurs au plus ----

    @Test fun aTrialRoomSeatsAtMostEightPlayersEvenIfTheServerAllowsMore() {
        val h = hub(settings = ServerRoom.Settings(seatsPerTable = 12))
        val trial = TestRights.activation(ActivationKind.TRIAL, rights = TestRights.trialUsage(now))
        val host = open(h, t(1), TestRights.create(trial))
        val code = ((host.out.map { Json.parse(it) as Map<*, *> }.first { it["t"] == "welcome" })["code"]) as String
        val joined = (0 until 12).count {
            val p = TestConn(); h.register(p)
            h.onText(p, PlayCodec.encode(ClientMsg.Join(code, "J$it", null, dev(), false))); p.welcomed()
        }
        assertEquals(8, joined)
    }
}
