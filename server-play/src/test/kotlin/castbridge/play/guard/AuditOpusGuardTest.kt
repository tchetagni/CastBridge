package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Audit Opus de w20-07 côté service : I3 (types inconnus, noms longs), B3 (adresse partagée), I7 (journal inondable). */
class AuditOpusGuardTest {
    @Test fun aNewerClientWithAnUnknownMessageTypeIsNotCutOff() {
        val hub = GuardHarness.hub()
        val c = FakeConn("future").also { hub.register(it) }
        repeat(6) { hub.onText(c, "{\"t\":\"nouveauType$it\",\"x\":1}") }
        assertNull(c.closedWith, "UNSUPPORTED répond sans compter : un client plus récent n'est pas coupé")
        assertTrue(c.errors().all { it.contains("UNSUPPORTED") })
        repeat(3) { hub.onText(c, "pas du json") }
        assertEquals(1008, c.closedWith, "BAD_REQUEST compte toujours")
    }

    @Test fun aNameOfSeventeenCharactersOrMoreAnswersBadNameWithTheLengthReason() {
        val hub = GuardHarness.hub()
        val (_, code) = GuardHarness.host(hub)
        val c = GuardHarness.join(hub, code, "A".repeat(17), "long")
        val e = c.errors().single()
        assertTrue(e.contains("\"reason\":\"BAD_NAME\"") && e.contains("16 caractères"), e)
        assertFalse(c.welcomed())
    }

    @Test fun aMaliciousPupilBehindTheSameAddressDoesNotKeepAnHonestPlayerOut() {
        var now = 1_000L
        val hub = GuardHarness.hub(clock = { now })
        hub.guard = PlayGuard(castbridge.core.quiz.online.Limits(config = castbridge.core.quiz.online.Limits.Config(joinPerMinutePerDevice = 1_000_000)), LogRedactor.silent())
        val (_, code) = GuardHarness.host(hub)
        val shared = "203.0.113.200"
        // l'élève malveillant tape 400 codes faux (un toutes les 11 s : ni la fenêtre courte ni rien ne l'arrête avant)
        repeat(400) { i -> now += 11_000; GuardHarness.join(hub, "ZZZZZ%03d".format(i % 1000), "Xa", "bad$i", ip = shared, device = "bad-device-0001") }
        val honest = GuardHarness.join(hub, code, "Amina", "honest", ip = shared)
        assertTrue(honest.welcomed(), "le bon code d'un autre appareil de la même adresse entre : ${honest.errors()}")
        // et même une rafale serrée (la fenêtre de 5 minutes) ne ferme pas la porte au bon code
        repeat(60) { i -> GuardHarness.join(hub, "YYYYY%03d".format(i), "Xa", "burst$i", ip = shared, device = "bad-device-0002") }
        assertTrue(GuardHarness.join(hub, code, "Bello", "honest2", ip = shared).welcomed(), "après 60 faux d'affilée, le BON code entre encore")
    }

    @Test fun theDailyQuotaIsPerAddressAndDevicePairNotPerAddress() {
        var now = 1_000L
        val hub = GuardHarness.hub(clock = { now })
        hub.guard = PlayGuard(castbridge.core.quiz.online.Limits(config = castbridge.core.quiz.online.Limits.Config(joinPerMinutePerDevice = 1_000_000)), LogRedactor.silent())
        val (_, code) = GuardHarness.host(hub)
        val shared = "203.0.113.201"
        repeat(310) { i -> now += 11_000; GuardHarness.join(hub, "ZZZZZ%03d".format(i % 1000), "Xa", "m$i", ip = shared, device = "mal-device-0001") }
        val again = GuardHarness.join(hub, "ZZZZZ999", "Xa", "m999", ip = shared, device = "mal-device-0001").errors().single()
        assertTrue(Regex("\"retryAfterMs\":\\d{7,}").containsMatchIn(again), "la paire adresse+appareil est bloquée pour la journée : $again")
        val other = GuardHarness.join(hub, "ZZZZZ998", "Xa", "o1", ip = shared, device = "other-device-0002").errors().single()
        assertFalse(other.contains("retryAfterMs"), "un autre appareil de la même adresse n'est pas bloqué : $other")
        assertTrue(GuardHarness.join(hub, code, "Dina", "fine", ip = shared, device = "fine-device-0003").welcomed())
    }

    @Test fun theLogIsSampledPerActionAndAddress() {
        val lines = ArrayList<String>()
        var now = 1_000_000L
        val log = LogRedactor({ lines += it }, { now })
        repeat(500) { log.event("play.msg.invalid", "x", mapOf("ip" to "203.0.113.9"), level = "warn") }
        assertTrue(lines.size <= 2, "au plus une ligne par seconde et par (action, adresse) : ${lines.size}")
        log.event("play.msg.invalid", "x", mapOf("ip" to "203.0.113.10"), level = "warn")
        assertEquals(2, lines.size, "une autre adresse a sa propre clé")
        now += 1_000
        log.event("play.msg.invalid", "x", mapOf("ip" to "203.0.113.9"), level = "warn")
        assertTrue(lines.last().contains("\"suppressed\":499"), "le compteur de lignes supprimées est dit : ${lines.last()}")
    }
}
