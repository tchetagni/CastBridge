package castbridge.core.games.bataille

import castbridge.core.games.ActResult
import castbridge.core.games.EndReason
import castbridge.core.games.GameJournal
import castbridge.core.games.Outcome
import castbridge.core.games.PlayerId
import castbridge.core.games.RoomStage
import castbridge.core.games.RulesRoom
import castbridge.core.games.SeatKind
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.wallet.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La Bataille (démonstration) comme PREMIER CLIENT de la plateforme : solo contre l'ordinateur le plus simple, et salle maison à deux téléphones, de bout en bout dans la salle
 * (horloge fausse, aléa à graine, ordinateur sans délai) : la partie entière se joue, ne montre jamais un paquet, et son journal signé se rejoue.
 */
class BatailleRoomTest {
    private var now = 1_000L
    private val signer = Ed25519Signer(ByteArray(32) { 0x31 })
    private fun room(vararg kinds: SeatKind, seed: Long = 21) = RulesRoom(Bataille, clock = { now }, random = java.util.Random(seed), autoTick = false, aiDelayMs = 0, wallClock = { 1_790_000_000_000L + now })
        .also { it.configure(kinds.toList()) }

    /** Les cartes que personne ne doit voir : celles des paquets, sauf celles de la dernière levée (montrées à tous). */
    private fun secrets(r: RulesRoom<BatailleState, Flip>): Set<String> {
        val s = r.engine!!.state
        return s.piles.flatten().map { it.code }.toSet() - s.table.map { it.card.code }.toSet() - (s.last?.shown?.map { it.card.code } ?: emptyList()).toSet()
    }

    private fun checkJournal(r: RulesRoom<BatailleState, Flip>) {
        val j = r.journal(signer.keyId)
        assertNotNull(j, "partie finie : un journal")
        assertNull(GameJournal.impossible(j, Bataille), "la graine et les coups suffisent à rejouer la partie")
        assertTrue(j.entries.all { it.move == "flip" }); assertEquals(r.engine!!.moveNo, j.entries.size); assertTrue(j.entries.size <= GameJournal.MAX_ENTRIES)
        val token = GameJournal.sign(j, signer)
        assertEquals(j, (GameJournal.verify(token, KeyRing(listOf(signer.trusted()))) as Verdict.Accepted).value)
        val payload = String(java.util.Base64.getUrlDecoder().decode(token.split('.')[1]))
        assertFalse(Regex("\"(\\d{1,2}|[JQKA])[CDHS]\"").containsMatchIn(payload), "aucune carte, aucune main dans le journal : seulement la graine et les coups")
    }

    @Test fun soloAgainstTheTrivialComputerFromStartToFinish() {
        val r = room(SeatKind.REMOTE, SeatKind.AI)
        assertNull(r.start())
        @Suppress("UNCHECKED_CAST") assertEquals(listOf("flip"), r.view(null)["legal"] as List<String>, "la TV peut retourner")
        var presses = 0
        while (r.roomStage == RoomStage.PLAYING) {
            assertEquals(ActResult.OK, r.hostMove("flip")); presses++
            if (r.roomStage == RoomStage.PLAYING) assertEquals(listOf(PlayerId("s1")), r.engine!!.toMove(), "l'ordinateur a répondu : c'est de nouveau à la télécommande")
            for (code in secrets(r)) assertFalse(r.viewJson(null).contains("\"$code\""), "la vue de la TV ne montre aucun paquet")
            assertTrue(presses <= Bataille.MAX_FLIPS, "au plus un appui par retournement")
        }
        assertEquals(RoomStage.FINISHED, r.roomStage)
        assertEquals(EndReason.RULES, r.engine!!.result!!.reason)
        assertTrue(r.engine!!.result!!.outcome is Outcome.Winners || r.engine!!.result!!.outcome == Outcome.Draw)
        assertTrue((r.view(null)["result"] as Map<*, *>)["text"].toString().isNotBlank())
        assertEquals(r.engine!!.moveNo, r.engine!!.log.size); assertTrue(r.engine!!.log.none { it.auto })
        checkJournal(r)
        r.close()
    }

    @Test fun twoPhonesPlayAWholeGameInTheHouseRoomAndNeverSeeAPile() {
        val r = room(SeatKind.PHONE, SeatKind.PHONE, seed = 33)
        val a = r.joinRoom(r.code, "Awa").player!!; val b = r.joinRoom(r.code, "Bello").player!!; val spectator = r.joinRoom(r.code, "Carine").player!!
        assertNull(r.start())
        val seed = r.engine!!.seed
        while (r.roomStage == RoomStage.PLAYING) {
            val e = r.engine!!
            val who = if (e.toMove().single().id == "s1") a else b
            val before = e.moveNo
            assertEquals(ActResult.OK, r.act(who.token, "move", "flip", before))
            if (r.roomStage == RoomStage.PLAYING) {
                assertEquals(ActResult.STALE, r.act(who.token, "move", "flip", before), "le même coup renvoyé n'est jamais rejoué")
                assertEquals(before + 1, r.engine!!.moveNo)
                for (code in secrets(r)) for (v in listOf(a.token, b.token, spectator.token, null)) {
                    val text = r.viewJson(v)
                    assertFalse(text.contains("\"$code\""), "un paquet est visible dans la vue de ${v?.take(4)}")
                    assertFalse(text.contains(seed.toString()), "la graine est visible")
                }
            }
        }
        assertEquals(RoomStage.FINISHED, r.roomStage)
        assertEquals(r.engine!!.moveNo, (r.view(a.token)["moveNo"] as Number).toInt())
        checkJournal(r)
        // le salon est prêt pour une revanche avec les mêmes places
        assertTrue(r.backToLobby()); assertNull(r.start()); assertEquals(RoomStage.PLAYING, r.roomStage)
        r.close()
    }

    @Test fun aPhoneCutMidGameComesBackAndTheGameGoesOnToTheEnd() {
        val r = room(SeatKind.PHONE, SeatKind.PHONE, seed = 44)
        val a = r.joinRoom(r.code, "Awa").player!!; val b = r.joinRoom(r.code, "Bello").player!!
        r.start()
        r.streamOpened(b)
        fun turn(): Boolean { val e = r.engine!!; val who = if (e.toMove().single().id == "s1") a else b; return r.act(who.token, "move", "flip", e.moveNo) == ActResult.OK }
        repeat(20) { assertTrue(turn()) }
        // Awa perd sa connexion 40 s : la partie attend, rien n'est perdu
        r.streamOpened(a); r.streamClosed(a)
        now += 40_000; r.tick()
        assertFalse(r.isConnected(a)); assertEquals(RoomStage.PLAYING, r.roomStage)
        val frozenAt = r.engine!!.moveNo
        // elle revient avec son jeton (sa page rouverte) : même place, la partie continue à la même levée
        val back = r.joinRoom(r.code, null, a.token).player!!
        assertEquals(0, r.seatOf(back)); assertEquals(frozenAt, r.engine!!.moveNo)
        var guard = 0
        while (r.roomStage == RoomStage.PLAYING && guard++ < 400) { r.touch(a); r.touch(b); assertTrue(turn()) }
        assertEquals(RoomStage.FINISHED, r.roomStage)
        assertEquals(EndReason.RULES, r.engine!!.result!!.reason, "la coupure n'a rien coûté : ${r.engine!!.result}")
        checkJournal(r)
        r.close()
    }
}
