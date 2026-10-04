package castbridge.play.poc

import castbridge.core.owner.ActivationKind
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.PlayRules
import castbridge.play.HubFixture
import castbridge.play.PlayHub
import castbridge.play.TestConn
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.dev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * w20-04b : avec `webPlay` faux, le SEUL client admis dans une salle est une CastBridge-TV activée (ticket `cbp1` + activation `cbx1`, comme à `create`) ; tout le reste est refusé
 * AVANT la recherche du code.
 */
class TvOnlyJoinTest {
    private fun tvHub(vararg more: Pair<String, Any>): PlayHub = HubFixture.hub(HubFixture.config("webPlay" to false, *more))

    private fun welcomes(c: TestConn) = synchronized(c.out) { c.out.map { Json.parse(it) as Map<*, *> }.filter { it["t"] == "welcome" } }
    private fun ticketOf(tv: TestRights.Tv) = TestKeys.ticket(deviceCode = tv.code)
    private fun prod(tv: TestRights.Tv) = TestRights.activation(device = tv)
    private fun trial(tv: TestRights.Tv) = TestRights.activation(ActivationKind.TRIAL, device = tv, rights = TestRights.trialUsage())

    /** La salle d'une TV de production (hôte), ouverte par le chemin ordinaire ; rend la connexion de l'hôte et le code. */
    private fun host(hub: PlayHub, tv: TestRights.Tv = TestRights.tv, activation: String = prod(tv)): Pair<TestConn, String> {
        val c = HubFixture.open(hub, ticketOf(tv), TestRights.create(activation))
        return c to (welcomes(c).single()["code"] as String)
    }

    private fun join(hub: PlayHub, code: String, ticket: String?, activation: String?, name: String = "TV Chambre", device: String = dev(), ip: String = "198.51.100.9", spectate: Boolean = false): TestConn {
        val c = TestConn(ip); hub.register(c)
        if (ticket != null) hub.onText(c, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket)))
        hub.onText(c, PlayCodec.encode(ClientMsg.Join(code, name, null, device, spectate, activation)))
        return c
    }

    private fun endRoomsExcept(hub: PlayHub, keepCode: String) {
        hub.rooms().filter { it.code != keepCode }.forEach { it.close("TEST", System.currentTimeMillis()) }
        hub.tick()
    }

    @Test fun joinWithoutTicketIsRefusedBeforeLookup() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val room = hub.rooms().single()
        val liveCode = join(hub, code, null, null)
        val wrongCode = join(hub, "ZZZZZZZZ", null, null)
        assertFalse(liveCode.welcomed(), "un join sans ticket ne doit jamais entrer")
        assertEquals("PLAY_TICKET_REFUSED", liveCode.errorReason())
        // même réponse pour un code vivant et pour un code faux : un inconnu n'apprend rien sur les salles
        assertEquals(liveCode.errorReason(), wrongCode.errorReason())
        assertEquals(liveCode.errorMessage(), wrongCode.errorMessage())
        assertEquals(liveCode.lastError()!!["retryable"], wrongCode.lastError()!!["retryable"])
        assertNull(liveCode.lastError()!!["retryAfterMs"]); assertNull(wrongCode.lastError()!!["retryAfterMs"])
        // aucun compteur de codes faux ni rotation du code touchés : 120 essais (adresse, appareil et salle) sans ticket
        val near = code.dropLast(1) + (if (code.last() == '0') '1' else '0')
        repeat(120) { i ->
            val typed = if (i % 2 == 0) near else "ZZZZZ%03d".format(i % 1000).replace('I', '1').replace('L', '1').replace('O', '0').replace('U', 'V')
            val c = join(hub, typed, null, null, device = dev())   // même adresse, un appareil neuf à chaque essai (le seau par appareil du garde est un autre contrôle)
            assertEquals("PLAY_TICKET_REFUSED", c.errorReason(), "essai $i")
            assertNull(c.lastError()!!["retryAfterMs"], "aucun blocage par adresse n'est compté pour un appelant sans ticket (essai $i)")
        }
        assertEquals(code, room.code, "le code de la salle n'a pas tourné : un inconnu n'atteint pas la rotation")
        assertEquals(1, hub.usedTicketCount(), "rien n'est brûlé : seul le ticket de l'hôte est compté")
    }

    @Test fun aValidTicketWithoutActivationIsScopeForbidden() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val c = join(hub, code, ticketOf(TestRights.otherTv), null)
        assertFalse(c.welcomed())
        assertEquals("PLAY_SCOPE_FORBIDDEN", c.errorReason())
        assertEquals(PlayRules.MSG_ACTIVATE, c.errorMessage())
        assertEquals(0, hub.rooms().single().seatCount(), "aucun siège pris")
    }

    @Test fun anActivationOfAnotherTvIsRefused() {
        val hub = tvHub()
        val (_, code) = host(hub)
        // le ticket dit TV « B », l'activation est celle de la TV « A » : refus, ticket brûlé
        val ticket = ticketOf(TestRights.otherTv)
        val c = join(hub, code, ticket, prod(TestRights.tv))
        assertFalse(c.welcomed())
        assertEquals("PLAY_SCOPE_FORBIDDEN", c.errorReason())
        assertTrue(c.errorMessage()!!.contains("n'est pas celle de cette TV"), c.errorMessage())
        val again = join(hub, code, ticket, prod(TestRights.otherTv))
        assertEquals("PLAY_TICKET_REFUSED", again.errorReason(), "une preuve fausse brûle le ticket : on ne tâtonne pas")
    }

    @Test fun aTicketUsedForCreateCannotJoinAndTheReverse() {
        val hub = tvHub()
        val tvA = TestRights.tv; val tvB = TestRights.otherTv
        val (_, code) = host(hub, tvA)
        // un ticket de la TV B sert pour créer une salle, puis il est présenté à join : même table de jti, refus
        val t1 = ticketOf(tvB)
        val created = HubFixture.open(hub, t1, TestRights.create(prod(tvB)))
        assertTrue(created.welcomed())
        val second = join(hub, code, t1, prod(tvB))
        assertEquals("PLAY_TICKET_REFUSED", second.errorReason(), "ticket déjà servi à create : refusé à join")
        // et dans l'autre sens : join réussi, puis le même ticket pour create
        val t2 = ticketOf(tvB)
        assertTrue(join(hub, code, t2, prod(tvB), device = dev()).welcomed())
        val create = HubFixture.open(hub, t2, TestRights.create(prod(tvB)))
        assertEquals("PLAY_TICKET_REFUSED", create.errorReason(), "ticket déjà servi à join : refusé à create")
        // et deux join avec le même ticket
        val t3 = ticketOf(tvB)
        assertTrue(join(hub, code, t3, prod(tvB), device = dev()).welcomed())
        assertEquals("PLAY_TICKET_REFUSED", join(hub, code, t3, prod(tvB), device = dev()).errorReason())
    }

    @Test fun aTrialTvCountsCreationsAndEntriesInTheSameDailyCounter() {
        val hub = tvHub()
        val (_, codeA) = host(hub)
        val t = TestRights.Tv("T-essai")
        assertTrue(HubFixture.open(hub, ticketOf(t), TestRights.create(trial(t))).welcomed(), "création 1")
        endRoomsExcept(hub, codeA)
        assertTrue(HubFixture.open(hub, ticketOf(t), TestRights.create(trial(t))).welcomed(), "création 2")
        endRoomsExcept(hub, codeA)
        assertTrue(join(hub, codeA, ticketOf(t), trial(t)).welcomed(), "entrée 3")
        val fourth = HubFixture.open(hub, ticketOf(t), TestRights.create(trial(t)))
        assertFalse(fourth.welcomed(), "la 4e opération du jour est refusée, créations et entrées dans le même compteur")
        assertEquals("PLAY_SCOPE_FORBIDDEN", fourth.errorReason())
        assertEquals(PlayRules.MSG_TRIAL_DAILY, fourth.errorMessage())
        val fifth = join(hub, codeA, ticketOf(t), trial(t))
        assertEquals(PlayRules.MSG_TRIAL_DAILY, fifth.errorMessage(), "une entrée de plus est refusée de même")
    }

    @Test fun aTrialTvCannotHoldTwoLiveConnectionsAtOnce() {
        val hub = tvHub()
        val (_, codeA) = host(hub)
        val t = TestRights.Tv("T-vivante")
        assertTrue(join(hub, codeA, ticketOf(t), trial(t)).welcomed(), "première connexion vivante")
        val (_, codeB) = host(hub, TestRights.Tv("Autre-hote"))
        val second = join(hub, codeB, ticketOf(t), trial(t))
        assertFalse(second.welcomed(), "essai : une seule connexion vivante par identité")
        assertEquals("PLAY_BUSY", second.errorReason())
    }

    @Test fun aProductionTvEntersAndItsSeatIsARelay() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val tvB = TestRights.otherTv
        val b = join(hub, code, ticketOf(tvB), prod(tvB), name = "TV Chambre", spectate = true)
        assertTrue(b.welcomed(), "la TV de production entre : ${b.out}")
        // son siège est un siège RELAIS : un second `join` de la même connexion enregistre un joueur local (sinon « déjà dans la salle »)
        hub.onText(b, PlayCodec.encode(ClientMsg.Join(code, "Carl", null, dev(), false)))
        val ws = welcomes(b)
        assertEquals(2, ws.size, "welcome de la TV puis welcome du joueur local relayé : ${b.out}")
        assertEquals("PLAYER", ws[1]["role"])
        assertNotNull(ws[1]["playerId"])
    }

    @Test fun aWrongCodeByAnAuthenticatedTvIsCountedPerIdentity() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val tvB = TestRights.otherTv
        val act = prod(tvB)
        // 30 essais faux depuis 30 adresses et 30 appareils différents : seul le compteur d'IDENTITÉ de la TV peut les relier
        repeat(30) { i ->
            val c = join(hub, "ZZZZZZZZ", ticketOf(tvB), act, device = dev(), ip = "198.51.100.${i + 10}")
            assertEquals("PLAY_BAD_CODE", c.errorReason(), "essai $i")
            assertNull(c.lastError()!!["retryAfterMs"], "pas encore bloquée à l'essai $i")
        }
        val blocked = join(hub, "ZZZZZZZZ", ticketOf(tvB), act, device = dev(), ip = "198.51.100.99")
        assertEquals("PLAY_BAD_CODE", blocked.errorReason())
        assertTrue(((blocked.lastError()!!["retryAfterMs"] as Number?)?.toLong() ?: 0L) > 0L, "le 31e essai faux en 5 min est bloqué avec retryAfterMs : ${blocked.lastError()}")
        // un code VALIDE n'est jamais refusé par un compteur de codes faux ; et les essais faux n'ont pas brûlé le ticket
        val ticket = ticketOf(tvB)
        join(hub, "ZZZZZZZZ", ticket, act, device = dev(), ip = "198.51.100.120")
        val good = join(hub, code, ticket, act, device = dev(), ip = "198.51.100.121")
        assertTrue(good.welcomed(), "code valide : entre malgré le blocage des codes faux : ${good.out}")
    }

    @Test fun webPlayTrueKeepsTodaysBehaviour() {
        val hub = HubFixture.hub(HubFixture.config("webPlay" to true))
        val (_, code) = host(hub)
        val c = join(hub, code, null, null, name = "Joueur", device = dev())
        assertTrue(c.welcomed(), "webPlay=true : le join libre d'aujourd'hui, inchangé")
        assertEquals(1, hub.rooms().single().seatCount())
    }
}
