package castbridge.core.quiz.online

import kotlin.test.Test
import kotlin.test.assertEquals

/** M-6 (audit Opus) : la TV ne redemande pas un ticket à chaque réouverture ; un ticket encore valable (< 9 min) sert à rouvrir une session par `resume`. */
class TicketReuseTest {
    @Test fun theCacheReusesATicketUnderNineMinutesAndRefetchesAfter() {
        var now = 0L; var n = 0
        val c = TicketCache({ now }) { "t${++n}" }
        assertEquals("t1", c.fresh())
        assertEquals("t1", c.reusable()); assertEquals(1, n, "dans les 9 minutes : aucun nouvel appel à l'API")
        now = 9 * 60_000L - 1
        assertEquals("t1", c.reusable()); assertEquals(1, n)
        now = 9 * 60_000L + 1
        assertEquals("t2", c.reusable(), "au-delà de 9 minutes : un ticket neuf"); assertEquals(2, n)
        assertEquals("t3", c.fresh(), "une création ou une entrée prend toujours un ticket neuf (usage unique côté service)")
    }

    @Test fun reopeningASeatedSessionReusesTheCurrentTicket() {
        var now = 100_000L; var fresh = 0; var reused = 0
        val made = ArrayList<ScriptedTransport>()
        val session = PlayTvSession(clock = { now }, transports = { _ ->
            ScriptedTransport().also { t ->
                if (made.isEmpty()) t.reply = { m -> if (m is ClientMsg.Create) t.push(ServerMsg.Welcome(1, "room-1", "K7M2QX4T", "tv-token", PlayRole.HOST, null, 1, emptyList())) }
                made += t
            }
        }, ticket = { fresh++; "cbp1.fresh-$fresh" }, resumeTicket = { reused++; "cbp1.current" })
        session.start("dev-tv-000001", "cbx1.act", PlayTvSession.Intent.Create("TV A", "DUEL"))
        assertEquals(1, fresh)
        made[0].state = PlayTransport.Status.CLOSED      // session de service perdue (410)
        session.tick()
        assertEquals(2, made.size, "une nouvelle session s'ouvre")
        assertEquals(1, fresh, "la reprise ne consomme pas un ticket neuf de l'API (plafonds de l'API : 20 par heure et par appareil)")
        assertEquals(1, reused)
        assertEquals("cbp1.current", made[1].all<ClientMsg.Hello>().single().ticket)
        assertEquals(1, made[1].all<ClientMsg.Resume>().size)
    }
}
