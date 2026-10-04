package castbridge.core.quiz.online

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QuizDuel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * w20-04b : une TV INVITÉE (siège relais accordé par le service seul) relaie SES joueurs locaux comme l'hôte relaie les siens ; une TV ne répond jamais pour les joueurs d'une autre.
 * Les mêmes règles que `MultiTvRelayTest` (service), sur `ServerRoom` seul, en mémoire.
 */
class ServerRoomRelayerTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(s: ServerRoom.Settings = ServerRoom.Settings(duelCount = 10, seatsPerTable = 16)) = ServerRoom("r1", PlayScope.INTERNET, bank, java.util.Random(11), createdAt = 0, settings = s)

    private fun List<ServerRoom.Out>.msgs() = map { it.msg }
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.one(): T = msgs().filterIsInstance<T>().first()
    private fun List<ServerRoom.Out>.error(): String? = msgs().filterIsInstance<ServerMsg.Error>().firstOrNull()?.reason
    private fun List<ServerRoom.Out>.ack(): String = msgs().filterIsInstance<ServerMsg.Ack>().single().result

    private fun ServerRoom.host() = handle("tv", ClientMsg.Create(null, "DUEL"), 0).one<ServerMsg.Welcome>()

    /** Une TV invitée : entre comme spectatrice (ou joueuse), puis le SERVICE lui accorde le relais. */
    private fun ServerRoom.guestTv(conn: String, name: String = "TV $conn", spectate: Boolean = true, grant: Boolean = true): ServerMsg.Welcome {
        val w = handle(conn, ClientMsg.Join(code, name, null, dv(), spectate), 0).one<ServerMsg.Welcome>()
        if (grant) assertTrue(grantRelay(conn))
        return w
    }

    /** Un joueur local de la TV [conn] ; rend son jeton de siège relayé. */
    private fun ServerRoom.relayed(conn: String, name: String, now: Long = 0, device: String = dv()): List<ServerRoom.Out> = handle(conn, ClientMsg.Join(code, name, null, device, false), now)
    private fun ServerRoom.token(conn: String, name: String): String = relayed(conn, name).one<ServerMsg.Welcome>().token

    private fun ServerRoom.startDuel() {
        handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        handle("tv", ClientMsg.Act(null, "start", null, "5", 2), 0)
    }

    private fun names(r: ServerRoom) = r.table(0).room.players().map { it.name }.toSet()

    @Test fun onlyTheServiceGrantsTheRelay() {
        val r = room(); r.host()
        r.guestTv("b", grant = false)
        // sans grantRelay : une TV invitée est une connexion ordinaire, « déjà dans la salle »
        assertEquals(PlayProtocol.FORBIDDEN, r.relayed("b", "Carl").error())
        assertFalse(r.grantRelay("inconnue"), "pas de siège : rien n'est accordé")
        assertTrue(r.grantRelay("b"))
        assertEquals(PlayRole.PLAYER, r.relayed("b", "Carl").one<ServerMsg.Welcome>().role)
        // aucun message client n'accorde le relais : un joueur ordinaire reste refusé
        r.handle("p", ClientMsg.Join(r.code, "Awa", null, dv(), false), 0)
        assertEquals(PlayProtocol.FORBIDDEN, r.relayed("p", "Intrus").error())
    }

    @Test fun aTvRelaysOnlyItsOwnPlayers() {
        val r = room(); r.host()
        r.guestTv("b")
        val a1 = r.token("tv", "Alice"); val a2 = r.token("tv", "Ann")
        val b1 = r.token("b", "Carl"); val b2 = r.token("b", "Dan")
        r.handle("p", ClientMsg.Join(r.code, "Awa", null, dv(), false), 0)
        r.startDuel()
        val q = r.table(0).room.duel!!.question
        fun relay(from: String, token: String, seq: Long) = r.handle(from, ClientMsg.RelayAct(token, q.id, q.answer, 9_000, seq), 10_000).ack()
        assertEquals("FORBIDDEN", relay("b", a1, 10), "la TV B ne répond pas pour un joueur de la TV A")
        assertEquals("FORBIDDEN", relay("tv", b1, 11), "l'hôte non plus ne répond pas pour un joueur de la TV B")
        assertEquals("FORBIDDEN", relay("p", b1, 12), "un joueur ordinaire ne relaie pas")
        assertEquals("UNKNOWN_PLAYER", relay("b", "00000000000000000000000000000000", 13))
        assertEquals(0, r.table(0).answeredCount(), "aucune réponse refusée n'a été comptée")
        assertEquals("OK", relay("b", b1, 14)); assertEquals("OK", relay("tv", a1, 15)); assertEquals("OK", relay("b", b2, 16)); assertEquals("OK", relay("tv", a2, 17))
        assertEquals(4, r.table(0).answeredCount())
    }

    @Test fun aTvRelaysAtMostMaxRelayedSeatsAndTheCapIsPerTv() {
        val r = room(ServerRoom.Settings(duelCount = 10, seatsPerTable = 16, maxRelayedPerTv = 3)); r.host()
        r.guestTv("b")
        repeat(3) { assertEquals(PlayRole.PLAYER, r.relayed("b", "B$it").one<ServerMsg.Welcome>().role) }
        assertEquals(PlayReason.PLAY_ROOM_FULL.name, r.relayed("b", "B3").error(), "le 4e siège de la TV B est refusé")
        repeat(3) { assertEquals(PlayRole.PLAYER, r.relayed("tv", "A$it").one<ServerMsg.Welcome>().role) }
        assertEquals(PlayReason.PLAY_ROOM_FULL.name, r.relayed("tv", "A3").error(), "le plafond est aussi celui de l'hôte, par TV")
        val big = room(); big.host(); big.guestTv("b")
        repeat(8) { big.relayed("b", "J$it") }
        assertEquals(PlayReason.PLAY_ROOM_FULL.name, big.relayed("b", "J8").error(), "8 sièges relayés par TV au plus")
        assertEquals(8, names(big).size)
    }

    @Test fun aRelayedSeatResumesOnlyThroughItsOwnTv() {
        val r = room(); r.host(); r.guestTv("b")
        val a1 = r.token("tv", "Alice")
        // la TV B présente le jeton de siège d'Alice : ce n'est pas SON siège, elle en obtient un neuf
        val other = r.handle("b", ClientMsg.Join(r.code, "Carl", a1, dv(), false), 0).one<ServerMsg.Welcome>()
        assertNotEquals(a1, other.token)
        // la TV A, elle, retrouve son siège
        assertEquals(a1, r.handle("tv", ClientMsg.Join(r.code, "Alice", a1, dv(), false), 0).one<ServerMsg.Welcome>().token)
        // et un client non assis ne reprend pas un siège relayé par un `join` à jeton
        val stranger = r.handle("x", ClientMsg.Join(r.code, "Voleur", a1, dv(), false), 0).one<ServerMsg.Welcome>()
        assertNotEquals(a1, stranger.token)
    }

    @Test fun onlyThePlayersOfALostTvAreAbsentAndTheTableDoesNotPause() {
        val r = room(); val host = r.host()
        val b = r.guestTv("b")
        r.relayed("tv", "Alice"); r.relayed("tv", "Ann"); r.relayed("b", "Carl"); r.relayed("b", "Dan")
        r.startDuel()
        val room = r.table(0).room
        fun connected(name: String, now: Long) = room.players().first { it.name == name }.let { room.isConnected(it, now) }
        r.disconnect("b", 1_000)
        assertTrue(connected("Alice", 40_000) && connected("Ann", 40_000), "les joueurs de la TV A restent présents")
        assertFalse(connected("Carl", 40_000) || connected("Dan", 40_000), "les joueurs de la TV B sont absents")
        r.tick(41_000)
        assertNull(r.table(0).pausedAt, "la perte d'une TV invitée ne met pas la table en pause")
        // la TV B reprend : ses joueurs sont de nouveau présents
        r.handle("b2", ClientMsg.Resume(r.roomId, b.token, 0), 42_000)
        assertTrue(connected("Carl", 42_000) && connected("Dan", 80_000), "reprise : les joueurs de la TV B sont présents")
        assertNull(r.table(0).pausedAt)
        assertEquals(host.token.length, 32)
    }

    @Test fun hostLostWithAnotherTvsPlayersPresentDoesNotPauseButAbandonsAfterSixtySeconds() {
        val r = room(); r.host()
        r.guestTv("b")
        r.relayed("tv", "Alice"); r.relayed("b", "Carl")
        r.startDuel()
        r.disconnect("tv", 5_000)
        r.tick(6_000)
        assertNull(r.table(0).pausedAt, "des joueurs distants (ceux de la TV B) jouent : pas de pause")
        assertNull(r.table(0).abandoned)
        r.tick(5_000 + ServerRoom.HOST_LOST_MS)
        assertEquals("HOST_LOST", r.table(0).abandoned, "abandon à 60 s (règle des joueurs distants)")
        // témoin : un hôte perdu avec ses seuls joueurs locaux met bien la table en pause
        val solo = room(); solo.host(); solo.relayed("tv", "Alice"); solo.relayed("tv", "Ann"); solo.startDuel()
        solo.disconnect("tv", 5_000); solo.tick(6_000)
        assertTrue(solo.table(0).pausedAt != null, "témoin : hôte seul avec ses locaux : pause")
    }

    @Test fun safetyCountsAnotherTvsPlayersAsRemote() {
        val r = room(); r.host(); r.guestTv("b")
        r.relayed("tv", "Alice"); r.relayed("tv", "Ann"); r.relayed("b", "Carl"); r.relayed("b", "Dan")
        val f = r.safetyFacts()
        assertEquals(2, f.localPlayers, "locaux = relayés par l'hôte")
        assertEquals(3, f.remotePlayers, "distants = la TV B et ses deux joueurs")
    }

    @Test fun closingInternetTurnsAnotherTvsPlayersIntoSpectators() {
        val r = room(); r.host(); r.guestTv("b")
        r.relayed("tv", "Alice"); r.relayed("b", "Carl")
        r.handle("tv", ClientMsg.Scope(false), 5_000)
        assertEquals(setOf("Alice"), names(r), "les joueurs de l'hôte restent ; ceux d'une autre TV passent spectateurs")
    }

    @Test fun kickingARelayTvRemovesItsRelayedSeatsToo() {
        val r = room(); r.host()
        val b = r.guestTv("b", spectate = false)
        r.relayed("tv", "Alice"); val b1 = r.token("b", "Carl"); r.relayed("b", "Dan")
        assertEquals(setOf("Alice", "Carl", "Dan", "TV b"), names(r))
        r.handle("tv", ClientMsg.Kick(b.playerId!!), 1_000)
        assertEquals(setOf("Alice"), names(r), "la TV expulsée et SES joueurs locaux partent ; ceux de l'hôte restent")
        r.startDuel()
        val q = r.table(0).room.duel!!.question
        assertEquals("UNKNOWN_PLAYER", r.handle("tv", ClientMsg.RelayAct(b1, q.id, q.answer, 5_000, 1), 10_000).ack(), "le siège relayé n'existe plus")
        assertEquals("FORBIDDEN", r.handle("b", ClientMsg.RelayAct(b1, q.id, q.answer, 5_000, 2), 10_000).ack(), "la TV expulsée n'est plus assise")
    }

    @Test fun fourPlayersRelayedByTheSameTvWithIdenticalAnswersAreNotUnrankedForSharingAnAddress() {
        val r = room(); r.host(); r.guestTv("b")
        val tokens = listOf("Carl", "Dan", "Eve", "Fay").map { r.token("b", it) }
        r.relayed("tv", "Alice")
        r.startDuel()
        val t = r.table(0)
        var now = 0L
        for (i in 0 until 10) {
            var guard = 0
            while (!(t.room.duel!!.index == i && t.room.duel!!.phase == QuizDuel.Phase.QUESTION && now >= t.opensAtServerMs) && guard++ < 20_000) { now += 100; r.tick(now) }
            check(guard < 20_000) { "la question $i ne s'ouvre pas" }
            val q = t.room.duel!!.question
            val local = 3_000L + 250L * i      // des temps de joueurs, identiques pour les quatre téléphones de la TV B
            now = t.opensAtServerMs + local
            for ((k, tok) in tokens.withIndex()) assertEquals("OK", r.handle("b", ClientMsg.RelayAct(tok, q.id, q.answer, local, 100L * i + k), now).ack(), "question $i, joueur $k")
            now += 100; r.tick(now)
        }
        assertEquals(0, r.unrankedSeats(), "aucun siège ne sort du classement : ${r.botResults()}")
        assertTrue(r.botResults().values.none { it.flagged }, "${r.botResults()}")
    }
}
