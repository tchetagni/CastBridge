package castbridge.core.chess.online

import castbridge.core.chess.ClockMode
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ChessOptions
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayRole
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.Verdict
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La salle d'échecs en ligne (`game:chess`, pure) : partie complète de deux TV, `STALE`, pendule du serveur, abandon, forfait à 60 s, reprise par jeton, spectateurs, mises (blocages vérifiés par
 * le hub, résultat `cbr1` signé et vérifié avec la clé publique du service). Aucune socket : le test joue le rôle du hub.
 */
class ChessServerRoomTest {
    private val signer = Ed25519Signer(MessageDigest.getInstance("SHA-256").digest("castbridge-chess-room-test-result-key".toByteArray()))
    private val ring = KeyRing(listOf(signer.trusted()))
    private val spooled = ArrayList<Pair<String, String>>()

    private val ESC_A = ChessEscrow("EidAAAAAAAAAAAAAAAAAAA", "AAAA-AAAA-AAAA-AAAA", 20)
    private val ESC_B = ChessEscrow("EidBBBBBBBBBBBBBBBBBBB", "BBBB-BBBB-BBBB-BBBB", 20)
    private val ROOM_ID = "0123456789abcdef0123456789abcdef"

    private fun newRoom(color: String = "white", seconds: Int = 30, mode: ClockMode = ClockMode.COMPETITION, stake: StakeSpec? = null, seed: Long = 7,
                        settings: ChessServerRoom.Settings = ChessServerRoom.Settings()) =
        ChessServerRoom(ROOM_ID, java.util.Random(seed), 1_000L, ChessServerRoom.Options(seconds, mode, color, stake), signer = signer, onResult = { rid, t -> spooled += rid to t }, settings = settings)

    private fun create(r: ChessServerRoom, conn: String = "A", escrow: ChessEscrow? = null, identity: String? = "AAAA-AAAA-AAAA-AAAA", at: Long = 1_000L, name: String? = "TV Salon") =
        r.handle(conn, ClientMsg.Create(name, null, game = "chess", chess = ChessOptions(30, "COMPETITION", "white")), at, "198.51.100.1", ChessServerRoom.Admission(identity, escrow))

    private fun join(r: ChessServerRoom, conn: String, escrow: ChessEscrow? = null, identity: String? = "BBBB-BBBB-BBBB-BBBB", spectate: Boolean = false, at: Long = 2_000L, name: String? = "TV Chambre", code: String = r.code) =
        r.handle(conn, ClientMsg.Join(code, name, null, null, spectate, null, null, null), at, "198.51.100.2", ChessServerRoom.Admission(identity, escrow))

    private fun move(r: ChessServerRoom, conn: String, uci: String, ply: Int, at: Long, seq: Long = ply.toLong() + 100) = r.handle(conn, ClientMsg.GameAct("move", uci, ply, seq), at)
    private fun resume(r: ChessServerRoom, conn: String, token: String, at: Long) = r.handle(conn, ClientMsg.Resume(ROOM_ID, token, 0), at)

    private fun msgs(out: List<ChessServerRoom.Out>, conn: String) = out.filter { it.to == conn }.map { it.msg }
    private fun ack(out: List<ChessServerRoom.Out>, conn: String): String? = msgs(out, conn).filterIsInstance<ServerMsg.Ack>().lastOrNull()?.result
    private fun states(out: List<ChessServerRoom.Out>, conn: String) = msgs(out, conn).filterIsInstance<ServerMsg.State>()
    private fun results(out: List<ChessServerRoom.Out>, conn: String) = msgs(out, conn).filterIsInstance<ServerMsg.Result>()
    private fun welcome(out: List<ChessServerRoom.Out>, conn: String) = msgs(out, conn).filterIsInstance<ServerMsg.Welcome>().single()
    private fun errors(out: List<ChessServerRoom.Out>, conn: String) = msgs(out, conn).filterIsInstance<ServerMsg.Error>()
    private fun view(out: List<ChessServerRoom.Out>, conn: String) = states(out, conn).last().view
    private fun result(v: Map<String, Any?>) = v["result"] as Map<*, *>?

    /** Deux TV assises : A (hôte, Blancs) et B ; les jetons de siège viennent des `welcome`, comme pour de vraies TV. */
    private inner class Table(val r: ChessServerRoom, val tokenA: String, val tokenB: String)

    private fun table(stake: StakeSpec? = null, seconds: Int = 30, mode: ClockMode = ClockMode.COMPETITION, settings: ChessServerRoom.Settings = ChessServerRoom.Settings()): Table {
        val r = newRoom(stake = stake, seconds = seconds, mode = mode, settings = settings)
        val a = create(r, escrow = if (stake != null) ESC_A else null)
        val b = join(r, "B", escrow = if (stake != null) ESC_B else null)
        return Table(r, welcome(a, "A").token, welcome(b, "B").token)
    }

    /** 1. f3 e5 2. g4 Dh4# : les Noirs matent en quatre demi-coups. Rend la sortie du dernier coup. */
    private fun foolsMate(r: ChessServerRoom, start: Long = 3_000L): List<ChessServerRoom.Out> {
        assertEquals("OK", ack(move(r, "A", "f2f3", 0, start), "A"))
        assertEquals("OK", ack(move(r, "B", "e7e5", 1, start + 1_000), "B"))
        assertEquals("OK", ack(move(r, "A", "g2g4", 2, start + 2_000), "A"))
        val last = move(r, "B", "d8h4", 3, start + 3_000)
        assertEquals("OK", ack(last, "B"))
        return last
    }

    // ------------------------------------------------------------------ partie libre

    @Test fun twoTvsPlayAFullFreeGame() {
        val r = newRoom()
        val c = create(r)
        val w = welcome(c, "A")
        assertEquals(PlayRole.HOST, w.role); assertEquals("chess", w.game); assertEquals(r.code, w.code)
        assertEquals("LOBBY", view(c, "A")["stage"], "la salle attend son adversaire")
        assertEquals(ChessServerRoom.State.OPEN, r.phase())
        val j = join(r, "B")
        assertEquals(PlayRole.PLAYER, welcome(j, "B").role)
        assertEquals(ChessServerRoom.State.PLAYING, r.phase())
        // les deux TV reçoivent le même état, chacune sa vue : « me » et « legal » seulement pour le trait
        val va = view(j, "A"); val vb = view(j, "B")
        assertEquals("PLAYING", va["stage"]); assertEquals("PLAYING", vb["stage"])
        assertEquals("w", (va["me"] as Map<*, *>)["color"]); assertEquals("b", (vb["me"] as Map<*, *>)["color"])
        assertEquals(20, (va["legal"] as List<*>).size, "les Blancs ont le trait : 20 coups légaux")
        assertTrue((vb["legal"] as List<*>).isEmpty(), "les Noirs n'ont pas le trait : aucun coup")
        assertNull(va["stake"], "partie libre : aucune mise")
        assertTrue(results(j, "A").isEmpty() && results(j, "B").isEmpty())
        val end = foolsMate(r)
        val rv = result(view(end, "A"))!!
        assertEquals("b", rv["winner"]); assertEquals("CHECKMATE", rv["reason"])
        assertEquals("FINISHED", view(end, "B")["stage"])
        assertEquals(ChessServerRoom.State.FINISHED, r.phase())
        assertTrue(results(end, "A").isEmpty() && spooled.isEmpty(), "partie libre : aucun cbr1")
        assertNotNull(view(end, "A")["pgn"])
        assertEquals(listOf("f3", "e5", "g4", "Qh4#"), view(end, "A")["san"])
    }

    @Test fun theHostColourCanBeChosenOrRandom() {
        val black = newRoom(color = "black"); val a = create(black)
        assertEquals("b", (view(a, "A")["me"] as Map<*, *>)["color"])
        // au hasard : les deux couleurs sortent selon la graine, et l'adversaire prend l'autre
        val colours = (1L..20L).map { seed ->
            val r = newRoom(color = "random", seed = seed); val h = create(r); val g = join(r, "B")
            val ch = (view(h, "A")["me"] as Map<*, *>)["color"]; val cg = (view(g, "B")["me"] as Map<*, *>)["color"]
            assertNotEquals(ch, cg); ch
        }.toSet()
        assertEquals(setOf("w", "b"), colours)
    }

    @Test fun staleDuplicatesIllegalMovesAndWrongTurnAreRefusedWithoutEffect() {
        val r = table().r
        assertEquals("ILLEGAL", ack(move(r, "A", "e2e5", 0, 3_000), "A"))
        assertEquals("NOT_YOUR_TURN", ack(move(r, "B", "e7e5", 0, 3_000), "B"))
        assertEquals("OK", ack(move(r, "A", "e2e4", 0, 3_100), "A"))
        assertEquals("STALE", ack(move(r, "A", "e2e4", 0, 3_200), "A"), "le doublon (renvoi sur une liaison lente) est écarté")
        assertEquals("STALE", ack(move(r, "B", "e7e5", 0, 3_300), "B"), "un coup qui répond à un demi-coup déjà joué est périmé")
        assertEquals("OK", ack(move(r, "B", "e7e5", 1, 3_400), "B"))
        assertEquals(2, r.playedPlies())
        // sans numéro de demi-coup, le serveur ne pourrait pas écarter un doublon : demande mal formée
        assertEquals("BAD_REQUEST", ack(r.handle("A", ClientMsg.GameAct("move", "g1f3", null, 5), 3_500), "A"))
        assertEquals("BAD_REQUEST", ack(r.handle("A", ClientMsg.GameAct("teleport", null, 2, 6), 3_500), "A"))
        assertEquals(2, r.playedPlies())
    }

    @Test fun beforeTheOpponentArrivesNothingCanBePlayed() {
        val r = newRoom(); create(r)
        assertEquals("IGNORED", ack(move(r, "A", "e2e4", 0, 1_500), "A"))
        assertEquals("UNKNOWN_PLAYER", ack(move(r, "Z", "e2e4", 0, 1_500), "Z"))
    }

    @Test fun spectatorsWatchButCannotPlayAndSeeNoLegalMoves() {
        val r = table().r
        val s = join(r, "S", spectate = true, identity = "CCCC-CCCC-CCCC-CCCC")
        assertEquals(PlayRole.SPECTATOR, welcome(s, "S").role)
        assertEquals("FORBIDDEN", ack(move(r, "S", "e2e4", 0, 3_000), "S"))
        val out = move(r, "A", "e2e4", 0, 3_100)
        val vs = view(out, "S")
        assertNull((vs["me"] as Map<*, *>)["color"]); assertTrue((vs["legal"] as List<*>).isEmpty())
        assertEquals(1, vs["spectators"])
        assertEquals("e2e4", vs["lastMove"])
    }

    @Test fun thirdPlayerIsRefusedButMayWatchAndSameTvCannotPlayItself() {
        val r = newRoom(); create(r)
        val self = join(r, "X", identity = "AAAA-AAAA-AAAA-AAAA")
        assertEquals("SAME_TV", errors(self, "X").single().reason)
        join(r, "B")
        val late = join(r, "C", identity = "CCCC-CCCC-CCCC-CCCC")
        assertEquals("SEATS_TAKEN", errors(late, "C").single().reason)
        assertEquals(PlayRole.SPECTATOR, welcome(join(r, "C2", identity = "CCCC-CCCC-CCCC-CCCC", spectate = true), "C2").role)
    }

    @Test fun spectatorsAreCapped() {
        val r = newRoom(settings = ChessServerRoom.Settings(maxSpectators = 2)); create(r); join(r, "B")
        join(r, "S1", spectate = true); join(r, "S2", spectate = true)
        assertEquals("PLAY_ROOM_FULL", errors(join(r, "S3", spectate = true), "S3").single().reason)
    }

    @Test fun aWrongCodeIsRefusedLikeTheQuiz() {
        val r = newRoom(); create(r)
        val out = join(r, "B", code = "ZZZZZZZZ")
        assertEquals("PLAY_BAD_CODE", errors(out, "B").single().reason)
        assertEquals(ChessServerRoom.State.OPEN, r.phase())
    }

    @Test fun theViewNeverCarriesASeatToken() {
        val r = newRoom(); val a = create(r); val b = join(r, "B")
        val tokens = listOf(welcome(a, "A").token, welcome(b, "B").token)
        val json = (states(b, "A") + states(b, "B")).joinToString { Json.write(it.view) }
        for (t in tokens) assertFalse(json.contains(t), "un jeton de siège ne paraît jamais dans une vue")
    }

    // ------------------------------------------------------------------ pendule (celle du serveur)

    @Test fun theServerClockMakesTheLatePlayerLoseAndNeverExceedsAMinute() {
        val r = table(seconds = 600).r    // demandé : 10 minutes ; borné à 60 s
        val v = view(move(r, "A", "e2e4", 0, 3_000), "A")
        assertEquals(60_000L, (v["clock"] as Map<*, *>)["perMoveMs"], "jamais plus d'une minute par coup")
        assertTrue(r.tick(3_000 + 59_999).none { it.msg is ServerMsg.State }, "avant l'échéance rien ne change")
        val out = r.tick(3_000 + 60_001)
        val rv = result(view(out, "A"))!!
        assertEquals("w", rv["winner"]); assertEquals("TIMEOUT", rv["reason"], "les Noirs n'ont pas joué à temps")
        assertEquals("IGNORED", ack(move(r, "B", "e7e5", 1, 3_000 + 60_500), "B"), "la partie est finie : plus aucun coup")
        assertEquals(1, r.playedPlies())
    }

    @Test fun aMoveArrivingAfterTheDeadlineIsNeverPlayed() {
        val r = table(seconds = 10).r
        val out = move(r, "A", "e2e4", 0, 2_000 + 10_001)
        assertEquals("OVER", ack(out, "A"), "le coup en retard n'est jamais joué : la partie est perdue au temps")
        assertEquals("TIMEOUT", result(view(out, "B"))!!["reason"])
        assertEquals(0, r.playedPlies())
    }

    @Test fun practiceModeNeverLosesOnTimeInAFreeGame() {
        val r = table(seconds = 10, mode = ClockMode.PRACTICE).r
        val out = r.tick(2_000 + 10_001)
        assertEquals(1, r.playedPlies(), "un coup est joué d'office")
        assertEquals(ChessServerRoom.State.PLAYING, r.phase())
        assertTrue(states(out, "A").isNotEmpty())
    }

    // ------------------------------------------------------------------ abandon, nulle, forfait, reprise

    @Test fun resignationEndsTheGame() {
        val r = table().r
        move(r, "A", "e2e4", 0, 3_000)
        val out = r.handle("B", ClientMsg.GameAct("resign", null, null, 9), 3_100)
        assertEquals("OK", ack(out, "B"))
        val rv = result(view(out, "A"))!!
        assertEquals("w", rv["winner"]); assertEquals("RESIGNATION", rv["reason"])
    }

    @Test fun aDrawIsAgreedByBothSides() {
        val r = table().r
        val offer = r.handle("A", ClientMsg.GameAct("draw", "offer", null, 1), 3_000)
        assertEquals("OK", ack(offer, "A")); assertEquals("w", view(offer, "B")["drawOffer"])
        val declined = r.handle("B", ClientMsg.GameAct("draw", "decline", null, 2), 3_100)
        assertNull(view(declined, "A")["drawOffer"]); assertEquals("PLAYING", view(declined, "A")["stage"])
        assertEquals("BAD_REQUEST", ack(r.handle("A", ClientMsg.GameAct("draw", "maybe", null, 5), 3_150), "A"))
        r.handle("A", ClientMsg.GameAct("draw", "offer", null, 3), 3_200)
        val out = r.handle("B", ClientMsg.GameAct("draw", "accept", null, 4), 3_300)
        val rv = result(view(out, "A"))!!
        assertNull(rv["winner"]); assertEquals("AGREEMENT", rv["reason"])
    }

    /** Les Blancs jouent e4 puis partent ; les Noirs répondent : la pendule des Blancs (absents) court jusqu'à 64 s, leur forfait tombe à 63 s. */
    private fun whiteLeavesAfterItsMove(t: Table) {
        move(t.r, "A", "e2e4", 0, 3_000)
        t.r.disconnect("A", 3_001)
        assertEquals("OK", ack(move(t.r, "B", "e7e5", 1, 4_000), "B"))
    }

    @Test fun aTvAbsentForSixtySecondsForfeitsWhileTheOtherIsThere() {
        val t = table(seconds = 60); val r = t.r
        whiteLeavesAfterItsMove(t)
        val before = r.tick(3_001 + 59_999)
        assertTrue(before.none { o -> o.msg is ServerMsg.State && result((o.msg as ServerMsg.State).view) != null }, "59,999 s d'absence : pas de forfait")
        val out = r.tick(3_001 + 60_000)
        val rv = result(view(out, "B"))!!
        assertEquals("b", rv["winner"]); assertEquals("FORFEIT", rv["reason"])
        assertEquals("Forfait (déconnexion) : les Noirs gagnent", rv["text"])
        assertEquals(ChessServerRoom.State.FINISHED, r.phase())
    }

    @Test fun aShorterForfeitDelayIsAParameterOfTheRoom() {
        val r = table(seconds = 60, settings = ChessServerRoom.Settings(forfeitMs = 10_000)).r
        r.disconnect("B", 3_000)
        assertEquals("FORFEIT", result(view(r.tick(3_000 + 10_000), "A"))!!["reason"])
    }

    @Test fun theViewTellsHowLongTheOpponentHasBeenAway() {
        val r = table(seconds = 60).r
        val dropped = view(r.disconnect("B", 10_000), "A")
        assertEquals(false, (dropped["black"] as Map<*, *>)["connected"])
        assertEquals(0L, ((dropped["room"] as Map<*, *>)["away"] as Map<*, *>)["b"], "l'absence commence ici ; le client décompte lui-même le forfait")
        val v = view(move(r, "A", "e2e4", 0, 10_000 + 20_000), "A")      // un changement d'état 20 s plus tard rapporte la durée
        val room = v["room"] as Map<*, *>
        assertEquals(20_000L, (room["away"] as Map<*, *>)["b"]); assertNull((room["away"] as Map<*, *>)["w"])
        assertEquals(60_000L, room["forfeitMs"])
    }

    @Test fun bothTvsAbsentInterruptTheGameAndNobodyWins() {
        // en entraînement la pendule ne fait perdre personne : seules les deux absences comptent
        val r = table(seconds = 60, mode = ClockMode.PRACTICE).r
        join(r, "S", spectate = true, identity = "CCCC-CCCC-CCCC-CCCC")
        r.disconnect("A", 3_000); r.disconnect("B", 3_500)
        r.tick(3_500 + 59_999)
        val out = r.tick(3_500 + 60_000)
        val rv = result(view(out, "S"))!!
        assertNull(rv["winner"]); assertEquals("ABANDONED", rv["reason"])
        assertEquals("Les deux TV ont quitté la partie : partie interrompue, mises rendues", rv["text"])
        assertEquals(ChessServerRoom.State.FINISHED, r.phase())
    }

    @Test fun inAStakedGameTheClockOfTheTvToMoveAlwaysDecidesFirst() {
        val r = table(stake = StakeSpec("NDEM", 20), seconds = 60).r
        r.disconnect("A", 3_000); r.disconnect("B", 3_500)       // les deux partent ; les Blancs (au trait depuis 2 s) perdent au temps à 62 s, avant tout forfait
        val out = r.tick(62_001)
        assertEquals("TIMEOUT", result(view(r.handle("S", ClientMsg.Join(r.code, "S", null, null, true), 62_002, null, ChessServerRoom.Admission("CCCC-CCCC-CCCC-CCCC")), "S"))!!["reason"])
        assertTrue(out.isEmpty() || out.none { it.msg is ServerMsg.RoomGone })
    }

    @Test fun aTvResumesItsSeatWithItsTokenAndTheGameContinues() {
        val t = table(); val r = t.r
        move(r, "A", "e2e4", 0, 3_000)
        r.disconnect("B", 4_000)
        val out = resume(r, "B2", t.tokenB, 24_000)            // 20 s plus tard, sur une NOUVELLE connexion
        val w = welcome(out, "B2")
        assertEquals(t.tokenB, w.token, "le même siège")
        val st = states(out, "B2").first()
        assertTrue(st.full, "une vue COMPLÈTE : une partie d'échecs tient en ≈ 1 Ko, pas de rejeu")
        assertEquals(listOf("e4"), st.view["san"])
        assertEquals("b", (st.view["me"] as Map<*, *>)["color"])
        assertEquals("OK", ack(move(r, "B2", "e7e5", 1, 24_500), "B2"))
        // un jeton inconnu ou une autre salle : la même réponse qu'un code faux
        assertEquals("PLAY_BAD_CODE", errors(r.handle("B3", ClientMsg.Resume(ROOM_ID, "forged", 0), 25_000), "B3").single().reason)
        assertEquals("PLAY_BAD_CODE", errors(r.handle("B3", ClientMsg.Resume("autre-salle", t.tokenB, 0), 25_000), "B3").single().reason)
    }

    @Test fun aTvThatComesBackBeforeTheForfeitDelayKeepsItsSeat() {
        val settings = ChessServerRoom.Settings(forfeitMs = 10_000)
        val gone = table(seconds = 60, settings = settings)
        gone.r.disconnect("A", 3_000)
        assertEquals("FORFEIT", result(view(gone.r.tick(3_000 + 10_000), "B"))!!["reason"], "témoin : sans retour, la TV perd à 10 s")
        val back = table(seconds = 60, settings = settings)
        back.r.disconnect("A", 3_000)
        resume(back.r, "A2", back.tokenA, 3_000 + 9_000)
        val out = back.r.tick(3_000 + 11_000)
        assertEquals(ChessServerRoom.State.PLAYING, back.r.phase(), "revenue avant le délai : pas de forfait")
        assertTrue(out.none { o -> o.msg is ServerMsg.State && result((o.msg as ServerMsg.State).view) != null })
    }

    @Test fun anOldConnectionDroppingAfterTheResumeDoesNotRemoveTheSeat() {
        val t = table(seconds = 60, settings = ChessServerRoom.Settings(forfeitMs = 10_000)); val r = t.r
        resume(r, "A2", t.tokenA, 5_000)          // la TV reprend sur une nouvelle connexion alors que l'ancienne n'est pas encore vue tombée
        r.disconnect("A", 5_100)                  // le hub voit enfin tomber l'ancienne
        assertTrue(r.tick(5_100 + 11_000).none { o -> o.msg is ServerMsg.State && result((o.msg as ServerMsg.State).view) != null }, "la TV est bien là : jamais de forfait")
        assertEquals(ChessServerRoom.State.PLAYING, r.phase())
    }

    // ------------------------------------------------------------------ mises : blocages vérifiés par le hub, résultat signé par le service

    private fun verified(out: List<ChessServerRoom.Out>, conn: String): PlayResult {
        val t = results(out, conn).single().token
        val v = PlayResult.verify(t, ring)
        assertTrue(v is Verdict.Accepted, "le résultat est authentique : $v")
        return (v as Verdict.Accepted).value
    }

    @Test fun aStakedGameEndsWithASignedResultAndTheWinnerTakesBothStakes() {
        val t = table(stake = StakeSpec("NDEM", 20)); val r = t.r
        val s = join(r, "S", spectate = true, identity = "CCCC-CCCC-CCCC-CCCC", at = 2_500)
        assertEquals(20L, (view(s, "S")["stake"] as Map<*, *>)["per"], "même un spectateur sait ce qui se joue")
        val end = foolsMate(r)
        val pr = verified(end, "A")
        assertEquals(pr, verified(end, "B"), "les deux TV reçoivent le MÊME résultat")
        assertEquals("chess", pr.game); assertEquals(PlayResult.Kind.END, pr.kind); assertEquals(20L, pr.per)
        assertEquals(ChessSettlement.ridOf(ROOM_ID), pr.rid, "un seul résultat possible par salle (identifiant déterministe)")
        assertEquals(ROOM_ID, pr.room)
        val a = pr.lines.single { it.eid == ESC_A.eid }; val b = pr.lines.single { it.eid == ESC_B.eid }
        assertEquals(20L to 0L, a.used to a.pay, "les Blancs ont perdu : mise perdue")
        assertEquals(20L to 40L, b.used to b.pay, "les Noirs gagnent les deux mises")
        assertEquals(ESC_A.id, a.id); assertEquals(ESC_B.id, b.id)
        assertTrue(pr.check())
        assertEquals(listOf(pr.rid to results(end, "A").single().token), spooled, "copie déposée pour le collecteur de l'hôte")
        assertTrue(results(end, "S").isEmpty(), "un spectateur ne reçoit aucun résultat")
        assertEquals(40L, (view(end, "A")["stake"] as Map<*, *>)["pot"])
        assertEquals(true, (view(end, "A")["stake"] as Map<*, *>)["settled"])
    }

    @Test fun aStakedDrawRefundsBothStakes() {
        val r = table(stake = StakeSpec("NDEM", 20)).r
        r.handle("A", ClientMsg.GameAct("draw", "offer", null, 1), 3_000)
        val out = r.handle("B", ClientMsg.GameAct("draw", "accept", null, 2), 3_100)
        val pr = verified(out, "A")
        assertEquals(PlayResult.Kind.END, pr.kind)
        assertEquals(listOf(20L to 20L, 20L to 20L), pr.lines.map { it.used to it.pay }, "nulle : chacun reprend sa mise")
    }

    @Test fun aStakedResignationLosesTheStake() {
        val r = table(stake = StakeSpec("NDEM", 20)).r
        val out = r.handle("A", ClientMsg.GameAct("resign", null, null, 1), 3_000)
        val pr = verified(out, "B")
        assertEquals(0L, pr.lines.single { it.eid == ESC_A.eid }.pay, "abandon = mise perdue")
        assertEquals(40L, pr.lines.single { it.eid == ESC_B.eid }.pay)
    }

    @Test fun aStakedForfeitAndATimeoutLoseTheStake() {
        val t = table(stake = StakeSpec("NDEM", 20), seconds = 60); val r = t.r
        whiteLeavesAfterItsMove(t)
        val out = r.tick(3_001 + 60_000)
        val pr = verified(out, "B")
        assertEquals(0L, pr.lines.single { it.eid == ESC_A.eid }.pay, "forfait = mise perdue")
        assertEquals(40L, pr.lines.single { it.eid == ESC_B.eid }.pay)
        // la TV absente retrouve son résultat à la reprise
        val back = resume(r, "A2", t.tokenA, 90_000)
        assertEquals(results(out, "B").single().token, results(back, "A2").single().token)
        // temps dépassé : les Blancs n'ont pas joué leur premier coup
        val timeout = table(stake = StakeSpec("NDEM", 20)).r.tick(2_000 + 30_001)
        assertEquals(0L, verified(timeout, "A").lines.single { it.eid == ESC_A.eid }.pay, "temps dépassé = défaite")
    }

    @Test fun aServiceClosingAnOngoingStakedGameAbortsAndRefundsEverything() {
        val r = table(stake = StakeSpec("NDEM", 20)).r
        move(r, "A", "e2e4", 0, 3_000)
        val out = r.close("DRAIN", 4_000)
        val pr = verified(out, "A")
        assertEquals(PlayResult.Kind.ABORT, pr.kind)
        assertTrue(pr.lines.all { it.used == 0L && it.pay == 0L }, "interrompue : rien n'est utilisé, tout est rendu")
        assertEquals(2, pr.lines.size)
        assertEquals("ABANDONED", result(view(out, "B"))!!["reason"])
        assertTrue(out.any { it.msg is ServerMsg.RoomGone })
        assertEquals(ChessServerRoom.State.GONE, r.phase())
    }

    @Test fun theHostCancellingBeforeAnyoneJoinsGetsItsEscrowReleasedByAnAbort() {
        val r = newRoom(stake = StakeSpec("MBOKO", 5)); create(r, escrow = ESC_A)
        val out = r.handle("A", ClientMsg.GameAct("cancel", null, null, 3), 5_000)
        assertEquals("OK", ack(out, "A"))
        val pr = verified(out, "A")
        assertEquals(PlayResult.Kind.ABORT, pr.kind); assertEquals(listOf(ESC_A.eid), pr.lines.map { it.eid }); assertEquals(5L, pr.per)
        assertEquals("Partie annulée : mises rendues", result(view(out, "A"))!!["text"])
        // personne d'autre ne peut annuler, et pas après le début
        val r2 = table().r
        assertEquals("FORBIDDEN", ack(r2.handle("B", ClientMsg.GameAct("cancel", null, null, 1), 3_000), "B"))
        assertEquals("FORBIDDEN", ack(r2.handle("A", ClientMsg.GameAct("cancel", null, null, 1), 3_000), "A"))
    }

    @Test fun aStakedRoomNobodyJoinsExpiresWithAnAbortForTheHost() {
        val r = newRoom(stake = StakeSpec("NDEM", 10), settings = ChessServerRoom.Settings(openTtlMs = 60_000)); create(r, escrow = ESC_A)
        assertTrue(r.tick(1_000 + 59_999).isEmpty())
        val out = r.tick(1_000 + 60_000)
        assertEquals(PlayResult.Kind.ABORT, verified(out, "A").kind)
        assertTrue(out.any { it.msg is ServerMsg.RoomGone })
    }

    @Test fun aStakedRoomAsksTheJoiningTvForItsEscrowAndSaysHowMuch() {
        val r = newRoom(stake = StakeSpec("NDEM", 50)); create(r, escrow = ESC_A)
        val out = join(r, "B", escrow = null)
        val e = errors(out, "B").single()
        assertEquals("STAKE_ESCROW_REQUIRED", e.reason)
        assertEquals(mapOf("game" to "chess", "cur" to "NDEM", "per" to 50L), e.data, "la TV sait quoi bloquer")
        assertEquals(ChessServerRoom.State.OPEN, r.phase(), "rien n'a bougé : la TV peut bloquer puis revenir")
        // elle peut aussi regarder sans rien miser
        assertEquals(PlayRole.SPECTATOR, welcome(join(r, "B", spectate = true), "B").role)
        // et entrer ensuite avec son blocage
        assertEquals(PlayRole.PLAYER, welcome(join(r, "B2", escrow = ESC_B), "B2").role)
    }

    @Test fun theEscrowIdsOfTheSeatsAreKnownToTheService() {
        val r = table(stake = StakeSpec("NDEM", 20)).r
        assertEquals(setOf(ESC_A.eid, ESC_B.eid), r.escrowIds().toSet())
    }

    @Test fun nearMissesRotateTheCodeOnlyWhileTheRoomWaits() {
        val r = newRoom(); create(r)
        val first = r.code
        var rotated = false
        for (i in 1..RoomCode.ROTATE_AFTER) rotated = r.noteNearMiss(100_000L + i)
        assertTrue(rotated); assertNotEquals(first, r.code)
        join(r, "B", code = r.code)
        val code2 = r.code
        for (i in 1..RoomCode.ROTATE_AFTER) r.noteNearMiss(300_000L + i)
        assertEquals(code2, r.code, "une partie en cours ne change plus de code")
    }

    @Test fun aFinishedGameKeepsItsResultThenTheRoomGoes() {
        val r = table(stake = StakeSpec("NDEM", 10), settings = ChessServerRoom.Settings(finishedKeepMs = 60_000)).r
        val end = foolsMate(r)
        assertTrue(r.tick(6_000 + 59_000).none { it.msg is ServerMsg.RoomGone })
        val gone = r.tick(6_000 + 60_001)
        assertTrue(gone.any { it.msg is ServerMsg.RoomGone })
        assertEquals(ChessServerRoom.State.GONE, r.phase())
        assertEquals(1, results(end, "A").size)
    }

    @Test fun aGameThatLastsTooLongIsInterruptedNotJudged() {
        val r = table(stake = StakeSpec("NDEM", 10), settings = ChessServerRoom.Settings(maxGameMs = 10_000)).r
        val out = r.tick(2_000 + 10_000)
        assertEquals(PlayResult.Kind.ABORT, verified(out, "A").kind)
    }

    @Test fun everyMessageTheRoomEmitsSurvivesTheCodec() {
        val r = table(stake = StakeSpec("NDEM", 20)).r
        val end = foolsMate(r)
        for (o in end) {
            val back = PlayCodec.decodeServer(PlayCodec.encode(o.msg))
            assertEquals(o.msg.type, back?.type)
            if (o.msg is ServerMsg.Result || o.msg is ServerMsg.Ack) assertEquals(o.msg, back)
        }
        // le jeton du résultat ne se montre jamais dans une trace
        assertFalse(results(end, "A").single().toString().contains("cbr1"))
    }
}
