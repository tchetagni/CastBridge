package castbridge.core.quiz.online

import castbridge.core.owner.Activation
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QuizRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Correctifs de l'audit Opus du Quiz en ligne (docs/agent-reports/audit-opus-poc-quiz-en-ligne.md) : preuves en ROUGE d'abord, sur `ServerRoom` seul et sur `ServerAuthority`.
 * C-1 (nom d'un spectateur), H-1 (grâce du relais), H-2 (attribution des `welcome`), M-9 (question 1), B-5, B-6, mutations D.
 */
class AuditOpusFixesTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(s: ServerRoom.Settings = ServerRoom.Settings(duelCount = 10, seatsPerTable = 16, duelQuestionMs = 10_000)) = ServerRoom("r1", PlayScope.INTERNET, bank, java.util.Random(11), createdAt = 0, settings = s)
    private fun List<ServerRoom.Out>.msgs() = map { it.msg }
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.one(): T = msgs().filterIsInstance<T>().first()
    private fun List<ServerRoom.Out>.error(): String? = msgs().filterIsInstance<ServerMsg.Error>().firstOrNull()?.reason
    private fun List<ServerRoom.Out>.ack(): String = msgs().filterIsInstance<ServerMsg.Ack>().single().result
    private fun ServerRoom.host() = handle("tv", ClientMsg.Create(null, "DUEL"), 0).one<ServerMsg.Welcome>()
    private fun ServerRoom.guestTv(conn: String, spectate: Boolean = true, ip: String? = null): ServerMsg.Welcome {
        val w = handle(conn, ClientMsg.Join(code, "TV $conn", null, dv(), spectate), 0, ip).one<ServerMsg.Welcome>(); assertTrue(grantRelay(conn)); return w
    }
    private fun ServerRoom.token(conn: String, name: String): String = handle(conn, ClientMsg.Join(code, name, null, dv(), false), 0).one<ServerMsg.Welcome>().token
    private fun ServerRoom.startDuel() { handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0); handle("tv", ClientMsg.Act(null, "start", null, "5", 2), 0) }

    // ---------------------------------------------------------------- C-1

    @Test fun aNamelessSpectatorTvIsAdmittedAndNamedTV() {
        val r = room(); r.host()
        val outs = r.handle("b", ClientMsg.Join(r.code, null, null, dv(), true), 0)
        assertEquals(null, outs.error(), "un spectateur sans nom ne doit pas être refusé")
        assertEquals(PlayRole.SPECTATOR, outs.one<ServerMsg.Welcome>().role)
        assertEquals(1, r.spectatorCount())
    }

    @Test fun aNamelessPlayerIsStillRefused() {
        val r = room(); r.host()
        assertEquals(PlayProtocol.BAD_REQUEST, r.handle("b", ClientMsg.Join(r.code, null, null, dv(), false), 0).error())
    }

    // ---------------------------------------------------------------- H-1

    @Test fun theGuestTvRelayIsInTheClosingGrace() {
        val r = room(); r.host(); r.guestTv("b")
        val carl = r.token("b", "Carl"); r.token("b", "Dan")
        r.startDuel()
        val o = r.table(0).opensAtServerMs
        for (p in r.tick(5_000).filter { it.msg is ServerMsg.Ping }) {
            val id = (p.msg as ServerMsg.Ping).id
            if (p.to == "tv") r.handle("tv", ClientMsg.Pong(id), 5_000) else r.handle("b", ClientMsg.Pong(id), 5_900)   // la TV invitée (EDGE) : RTT 900 ms
        }
        val q = r.table(0).room.duel!!.question
        r.tick(o + 10_050)   // la fenêtre de 10 s est passée sur l'horloge locale du serveur ; la grâce du relais (900 ms) la retient
        val ack = r.handle("b", ClientMsg.RelayAct(carl, q.id, q.answer, 9_600, 5), o + 10_350).ack()
        assertEquals("OK", ack, "la réponse arrivée à 10 350 compte 9 950 ms (≤ 10 000) : la salle ne devait pas avoir clos")
    }

    // ---------------------------------------------------------------- M-9

    @Test fun questionOneOpensAfterTheServerGapLikeTheOthers() {
        val r = room(); r.host(); r.token("tv", "Awa"); r.token("tv", "Bob")
        r.startDuel()
        val t = r.table(0)
        assertEquals(PlayTiming.INTER_QUESTION_GAP_MS, t.opensAtServerMs - 0L, "la question 1 s'ouvre 1,5 s après le départ (horloge serveur), comme les suivantes")
        val q = t.room.duel!!.question
        val early = r.handle("tv", ClientMsg.RelayAct(r.token("tv", "Cy"), q.id, q.answer, 0, 9), 100).ack()
        assertEquals("TOO_EARLY", early)
    }

    // ---------------------------------------------------------------- mutation D : un spectateur relais qui porte des sièges n'est pas purgé

    @Test fun aRelaySpectatorCarryingSeatsIsNotPurgedAfterFiveMinutes() {
        val r = room(); r.host()
        val b = r.guestTv("b"); r.token("b", "Carl")
        r.disconnect("b", 0)
        r.tick(SPECTATOR_PURGE + 60_000)
        val back = r.handle("b2", ClientMsg.Resume("r1", b.token, 0), SPECTATOR_PURGE + 61_000)
        assertEquals(null, back.error(), "la TV invitée, coupée plus de 5 minutes, reprend son siège relais")
        assertTrue(back.msgs().any { it is ServerMsg.Welcome })
    }

    @Test fun aRelaySpectatorWithoutSeatsIsPurged() {
        val r = room(); r.host()
        val b = r.guestTv("b")
        r.disconnect("b", 0)
        r.tick(SPECTATOR_PURGE + 60_000)
        assertEquals(PlayReason.PLAY_BAD_CODE.name, r.handle("b2", ClientMsg.Resume("r1", b.token, 0), SPECTATOR_PURGE + 61_000).error())
    }

    // ---------------------------------------------------------------- B-6, B-5

    @Test fun aRelayedSeatResumesOnlyThroughItsOwnTvJoin() {
        val r = room(); r.host(); r.guestTv("b")
        val carl = r.token("b", "Carl")
        val outs = r.handle("intrus", ClientMsg.Resume("r1", carl, 0), 10)
        assertNull(outs.msgs().filterIsInstance<ServerMsg.Welcome>().firstOrNull(), "un siège relayé ne se reprend pas par un resume ordinaire")
    }

    @Test fun kickingARelayTvBansItsDeviceButNotTheWholeAddress() {
        val r = room(); r.host()
        val ip = "203.0.113.50"
        val w = r.guestTv("b", spectate = false, ip = ip)
        val hostOuts = r.handle("tv", ClientMsg.Kick(w.playerId!!), 5)
        assertNotNull(hostOuts)
        // une autre TV derrière la même adresse (CGNAT) peut encore entrer
        assertEquals(null, r.handle("c", ClientMsg.Join(r.code, "TV c", null, dv(), true), 6, ip).error(), "le bannissement d'une TV ne ferme pas toute l'adresse partagée")
    }

    // ---------------------------------------------------------------- H-2 : attribution des welcome / error d'un relais

    private class Harness {
        val t = ScriptedTransport(); val a = ServerAuthority(t)
        fun welcome(token: String, role: PlayRole, pid: String? = null) = ServerMsg.Welcome(1, "room-1", "K7M2QX4T", token, role, pid, 1, emptyList())
        init { a.create(null, "DUEL"); t.push(welcome("tv-own-token-00000", PlayRole.HOST)) }
        fun relayJoinAsync(timeoutMs: Long): Pair<Thread, Array<Pair<ServerMsg.Welcome?, ServerMsg.Error?>?>> {
            val box = arrayOfNulls<Pair<ServerMsg.Welcome?, ServerMsg.Error?>>(1)
            val th = Thread { box[0] = a.relayJoin("K7M2QX4T", "Carl", "dev-1", null, timeoutMs) }.also { it.start() }
            val end = System.currentTimeMillis() + 3_000
            while (t.all<ClientMsg.Join>().isEmpty() && System.currentTimeMillis() < end) Thread.sleep(10)
            return th to box
        }
    }

    @Test fun aLatePhoneWelcomeNeverOverwritesTheTvToken() {
        val h = Harness()
        val (th, box) = h.relayJoinAsync(150)
        th.join(2_000)
        assertNull(box[0]!!.first, "pas de réponse dans le délai : l'entrée du téléphone expire")
        h.t.push(h.welcome("relayed-seat-token-", PlayRole.PLAYER, "p1"))   // la coupure EDGE a retardé le welcome du siège du téléphone
        assertEquals("tv-own-token-00000", h.a.token, "le jeton de la TV reste celui de la TV (sinon la reprise attache la TV au siège du téléphone)")
    }

    @Test fun aResumeWelcomeIsNotGivenToThePhone() {
        val h = Harness()
        val (th, box) = h.relayJoinAsync(3_000)
        h.t.push(h.welcome("tv-own-token-00000", PlayRole.HOST))              // la TV reprend (resume) pendant qu'un téléphone entre
        h.t.push(h.welcome("phone-seat-token-0", PlayRole.PLAYER, "p2"))
        th.join(4_000)
        assertEquals("phone-seat-token-0", box[0]!!.first?.token, "le téléphone reçoit SON siège, pas le jeton de siège de la TV")
        assertEquals("tv-own-token-00000", h.a.token)
    }

    @Test fun anUnrelatedErrorDoesNotFailThePhoneEntry() {
        val h = Harness()
        val (th, box) = h.relayJoinAsync(3_000)
        h.t.push(ServerMsg.Error(1, PlayHub_BUSY, "débit", true))
        h.t.push(ServerMsg.Error(1, "PLAY_MAINTENANCE", "annonce", true))
        h.t.push(h.welcome("phone-seat-token-1", PlayRole.PLAYER, "p3"))
        th.join(4_000)
        assertEquals("phone-seat-token-1", box[0]!!.first?.token)
        assertNull(box[0]!!.second)
    }

    @Test fun aGuestTvThatJoinedAsPlayerStillAttributesByPendingJoin() {
        val t = ScriptedTransport(); val a = ServerAuthority(t)
        a.joinRoom("K7M2QX4T", "TV B", "dev-b", "cbx1.a", spectate = false)
        t.push(ServerMsg.Welcome(1, "room-1", "K7M2QX4T", "tv-b-token-000000", PlayRole.PLAYER, "pB", 1, emptyList()))
        val box = arrayOfNulls<ServerMsg.Welcome>(1)
        val th = Thread { box[0] = a.relayJoin("K7M2QX4T", "Carl", "dev-1", null, 3_000).first }.also { it.start() }
        while (t.all<ClientMsg.Join>().size < 2) Thread.sleep(10)
        t.push(ServerMsg.Welcome(2, "room-1", "K7M2QX4T", "phone-token-000000", PlayRole.PLAYER, "pC", 1, emptyList()))
        th.join(4_000)
        assertEquals("phone-token-000000", box[0]?.token); assertEquals("tv-b-token-000000", a.token)
    }

    // ---------------------------------------------------------------- B-1 : un pseudonyme refusé par le service se dit « choisissez un autre nom »

    @Test fun aNameRefusedByTheServiceIsBadNameForThePhone() {
        var now = 10_000L
        val t = ScriptedTransport()
        val s = PlayTvSession(clock = { now }, transports = { t }, ticket = { "cbp1.t" })
        t.reply = { m ->
            when {
                m is ClientMsg.Create -> t.push(ServerMsg.Welcome(1, "room-1", "K7M2QX4T", "tv-token", PlayRole.HOST, null, 1, emptyList()))
                m is ClientMsg.Join && m.token == null -> t.push(ServerMsg.Error(2, PlayReason.BAD_NAME.name, "Pseudonyme refusé.", true))
            }
        }
        s.start("dev-tv-000001", "cbx1.a", PlayTvSession.Intent.Create("TV A", "DUEL"))
        val relay = RelayAuthority(s, { now })
        assertEquals(QuizRoom.Join.BAD_NAME, relay.join(null, "Zorro", null, "dev-phone-0001").status)
    }

    // ---------------------------------------------------------------- M-4 : la porte de la TV = la règle du service

    private fun act(issuedAt: Long, signed: Boolean) = Activation(castbridge.core.owner.ActivationKind.PRODUCTION, castbridge.core.owner.Subject.TV, "kid", 0L, "", issuedAt, issuedAt, issuedAt + 86_400_000L * 365,
        "lic-1", "", 0, if (signed) mapOf(castbridge.core.owner.FactorKind.values().first() to "f") else emptyMap(), emptyList(), if (signed) "sig" else "")

    @Test fun theTvOffersTheServiceOnlyAnActivationItCanVerify() {
        val shortKey = act(2_000, signed = false); val real = act(1_000, signed = true)
        assertEquals(real, PlayActivation.pick(listOf(shortKey, real), 3_000), "la clé courte (plus récente) est invérifiable par le service : on prend la signée")
        assertNull(PlayActivation.pick(listOf(shortKey), 3_000), "seulement une clé courte : rien à présenter, la tuile le dit")
        assertFalse(PlayActivation.verifiable(shortKey)); assertTrue(PlayActivation.verifiable(real))
    }

    @Test fun aTvWithoutAVerifiableActivationSeesTheTileBlockedWithTheReason() {
        val tile = PlayGate.tile(true, HostEdition.PROD, true, false, verifiableActivation = false)
        assertEquals(PlayTile.Blocked(PlayGate.MSG_ACTIVATION_FILE), tile)
        assertEquals(PlayTile.Available, PlayGate.tile(true, HostEdition.PROD, true, false, verifiableActivation = true))
    }

    // ---------------------------------------------------------------- M-5 : « Retour » pendant l'ouverture

    @Test fun anOpeningCancelledByBackNeverBecomesTheSession() {
        val g = OpenGate()
        val first = g.begin()
        g.cancel()                                   // Retour : stop() pendant que le fil attend le ticket
        assertFalse(g.isCurrent(first), "l'ouverture annulée ne doit pas affecter session/relais (salle fantôme)")
        val second = g.begin()
        assertTrue(g.isCurrent(second)); assertFalse(g.isCurrent(first))
    }

    private companion object { const val SPECTATOR_PURGE = ServerRoom.SPECTATOR_PURGE_MS; const val PlayHub_BUSY = "PLAY_BUSY" }
}
