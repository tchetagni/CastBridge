package castbridge.core.games

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.quiz.Json
import castbridge.core.wallet.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * La salle « maison » générique (code à 4 chiffres, jeton de place, spectateurs, changement de place) autour d'un jeu à règles : horloge fausse, aléa à graine, ordinateur
 * sans délai sauf mention. Jeux de test : [Nim] (rien de caché) et [Hands] (mains cachées).
 */
class GameRoomTest {
    private var now = 1_000L
    private val signer = Ed25519Signer(ByteArray(32) { 0x42 })

    private fun nim(vararg kinds: SeatKind = arrayOf(SeatKind.PHONE, SeatKind.PHONE), clock: MoveClock? = null, aiDelayMs: Long = 0, max: Int = 4, seed: Long = 5) =
        RulesRoom(Nim, clock = { now }, random = java.util.Random(seed), maxPlayers = max, autoTick = false, aiDelayMs = aiDelayMs, wallClock = { 1_790_000_000_000L + now })
            .also { it.configure(kinds.toList(), clock) }

    private fun hands(vararg kinds: SeatKind = arrayOf(SeatKind.PHONE, SeatKind.PHONE)) =
        RulesRoom(Hands, clock = { now }, random = java.util.Random(7), maxPlayers = 4, autoTick = false, aiDelayMs = 0, wallClock = { 1_790_000_000_000L + now })
            .also { it.configure(kinds.toList(), null) }

    private fun <S, M : GameMove> RulesRoom<S, M>.join(name: String) = joinRoom(code, name).player!!
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.l(k: String) = this[k] as List<Any?>
    private fun Map<String, Any?>.n(k: String) = (this[k] as Number).toInt()

    // ---------------------------------------------------------------- code, jetons, places

    @Test fun codeTokensSeatsAndReconnection() {
        val r = nim(max = 4)
        assertTrue(r.code.matches(Regex("\\d{4}")))
        assertEquals(JoinStatus.BAD_CODE, r.joinRoom(if (r.code == "0000") "1111" else "0000", "Ali").status)
        assertEquals(JoinStatus.BAD_NAME, r.joinRoom(r.code, " <> ").status)
        val a = r.join("Ali"); val b = r.join("Ali"); val s = r.join("Carine")
        assertEquals("Ali 2", b.name, "deux pseudos identiques : le second est numéroté")
        assertEquals(0, r.seatOf(a)); assertEquals(1, r.seatOf(b)); assertNull(r.seatOf(s), "le troisième regarde")
        assertEquals(1, r.view(null)["spectators"])
        r.join("Dan")
        assertEquals(JoinStatus.FULL, r.joinRoom(r.code, "Eve").status, "quatre personnes au plus dans cette salle")
        assertEquals(listOf("Ali", "Ali 2", "Carine", "Dan"), r.players().map { it.name })
        assertNull(r.start())
        val back = r.joinRoom(r.code, null, a.token).player!!
        assertSame(a, back); assertEquals(0, r.seatOf(back), "reprise par le jeton, même en pleine partie")
        assertNotEquals(a.token, b.token)
        for (viewer in listOf(a, b, s)) for (other in listOf(a, b, s)) if (viewer !== other) assertFalse(r.viewJson(viewer.token).contains(other.token), "personne ne voit le jeton d'un autre")
    }

    @Test fun tenPeopleAtMostByDefault() {
        val r = RulesRoom(Nim, clock = { now }, random = java.util.Random(1), autoTick = false).also { it.configure(listOf(SeatKind.PHONE, SeatKind.PHONE), null) }
        assertEquals(10, r.maxPlayers)
        repeat(10) { assertEquals(JoinStatus.OK, r.joinRoom(r.code, "J$it").status) }
        assertEquals(JoinStatus.FULL, r.joinRoom(r.code, "Onze").status)
        r.leave(r.players().last().token)
        assertEquals(JoinStatus.OK, r.joinRoom(r.code, "Onze").status, "une place libérée en laisse une")
        r.close()
    }

    @Test fun startNeedsThePhonePlayersAndNoSeatChangeDuringTheGame() {
        val r = nim()
        assertEquals("En attente de 2 joueurs sur téléphone", r.start())
        val a = r.join("Ali")
        assertEquals("En attente du joueur 2 sur téléphone", r.start())
        r.join("Bea")
        assertNull(r.start()); assertEquals(RoomStage.PLAYING, r.roomStage)
        assertEquals("Une partie est déjà en cours.", r.start())
        assertEquals(ActResult.FORBIDDEN, r.act(a.token, "sit", "2"), "pas de changement de place en partie")
        assertEquals(ActResult.FORBIDDEN, r.act(a.token, "stand", null))
    }

    @Test fun aNewcomerDuringAGameOnlyWatchesEvenIfASeatIsFree() {
        val r = nim(); val a = r.join("Ali")
        assertNull(r.start(force = true), "départ forcé avec une place vide (jamais fait par la TV, mais la salle doit tenir)")
        val late = r.join("Retardataire")
        assertNull(r.seatOf(late), "pendant la partie on ne prend pas une place libre : on regarde")
        assertEquals(ActResult.FORBIDDEN, r.act(late.token, "sit", "2"))
        assertEquals(0, r.seatOf(a))
    }

    @Test fun seatsChangeBeforeTheGameAndAnOccupiedSeatIsRefused() {
        val r = nim()
        val a = r.join("Ali"); val b = r.join("Bea"); val c = r.join("Carine")
        assertEquals(ActResult.OK, r.act(a.token, "stand", null)); assertNull(r.seatOf(a))
        assertEquals(ActResult.OK, r.act(c.token, "sit", "1")); assertEquals(0, r.seatOf(c))
        assertEquals(ActResult.FORBIDDEN, r.act(a.token, "sit", "1"), "occupée par quelqu'un de présent")
        assertEquals(ActResult.FORBIDDEN, r.act(a.token, "sit", "2"))
        for (bad in listOf("x", "0", "3", "-1", "")) assertEquals(ActResult.BAD_REQUEST, r.act(a.token, "sit", bad), "« $bad »")
        assertEquals(ActResult.BAD_REQUEST, r.act(a.token, "sit", null))
        // quelqu'un qui n'est plus là (35 s de silence) ne garde pas sa place
        r.streamOpened(b); r.streamClosed(b); now += 40_000
        assertFalse(r.isConnected(b))
        assertEquals(ActResult.OK, r.act(a.token, "sit", "2")); assertEquals(1, r.seatOf(a)); assertNull(r.seatOf(b))
        // quitter libère la place avant la partie
        assertTrue(r.leave(a.token)); assertNull(r.seatOf(a)); assertFalse(r.leave("inconnu"))
        r.close()
    }

    @Test fun configureFillsPhoneSeatsWithThePlayersAlreadyThere() {
        val r = nim(SeatKind.REMOTE, SeatKind.PHONE)
        val a = r.join("Ali"); assertEquals(1, r.seatOf(a), "la place 1 est à la télécommande : le téléphone prend la 2")
        assertNull(r.start())
        r.close()
        val t = nim(SeatKind.PHONE, SeatKind.PHONE); val x = t.join("X"); val y = t.join("Y")
        assertTrue(t.configure(listOf(SeatKind.AI, SeatKind.PHONE), null))
        assertNull(t.seatOf(x), "la place de X est devenue celle de l'ordinateur : il regarde"); assertEquals(1, t.seatOf(y))
        assertFailsWith<IllegalArgumentException> { t.configure(listOf(SeatKind.AI, SeatKind.AI), null) }
        assertFailsWith<IllegalArgumentException> { t.configure(listOf(SeatKind.PHONE), null) }
        assertFailsWith<IllegalArgumentException> { t.configure(List(5) { SeatKind.PHONE }, null) }
    }

    // ---------------------------------------------------------------- coups

    @Test fun illegalOrOutOfTurnMovesAreRefusedAndChangeNothing() {
        val r = nim(); val a = r.join("A"); val b = r.join("B"); val s = r.join("Spectateur")
        assertNull(r.start())
        assertEquals(listOf("take:1", "take:2", "take:3"), r.view(a.token).l("legal"), "les coups légaux sont listés pour celui qui a la main")
        assertTrue(r.view(b.token).l("legal").isEmpty()); assertTrue(r.view(s.token).l("legal").isEmpty())
        val pile0 = r.view(null).m("table").n("pile")
        assertEquals(ActResult.ILLEGAL, r.act(a.token, "move", "take:9", 0))
        assertEquals(ActResult.ILLEGAL, r.act(a.token, "move", "hello", 0))
        assertEquals(ActResult.NOT_YOUR_TURN, r.act(b.token, "move", "take:1", 0))
        assertEquals(ActResult.FORBIDDEN, r.act(s.token, "move", "take:1", 0), "un spectateur ne joue pas")
        assertEquals(ActResult.UNKNOWN_PLAYER, r.act("forged", "move", "take:1", 0))
        assertEquals(ActResult.BAD_REQUEST, r.act(a.token, "move", "", 0)); assertEquals(ActResult.BAD_REQUEST, r.act(a.token, "move", null, 0))
        assertEquals(ActResult.BAD_REQUEST, r.act(a.token, "dance", null, null))
        assertEquals(ActResult.NOT_YOUR_TURN, r.hostMove("take:1"), "la télécommande ne joue pas la place d'un téléphone")
        assertEquals(pile0, r.view(null).m("table").n("pile"), "rien n'a changé")
        assertEquals(ActResult.OK, r.act(a.token, "move", "take:2", 0))
        assertEquals(ActResult.STALE, r.act(a.token, "move", "take:2", 0), "un doublon n'est pas joué deux fois")
        assertEquals(pile0 - 2, r.view(b.token).m("table").n("pile"))
        assertEquals(ActResult.OK, r.act(b.token, "move", "take:1", 1))
        assertEquals(2, r.view(s.token)["moveNo"])
    }

    @Test fun theFormatOfTheSharedState() {
        val r = nim(); val a = r.join("Ali"); r.join("Bea")
        val lobby = r.view(a.token)
        assertEquals("nim", lobby["game"]); assertEquals(1, lobby["rules"]); assertEquals(1, lobby["protocol"]); assertEquals("LOBBY", lobby["stage"]); assertEquals(r.code, lobby["code"])
        assertNull(lobby["table"], "pas de table avant la partie"); assertEquals(0, lobby["moveNo"])
        assertEquals(listOf("s1", "s2"), lobby.l("seats").map { (it as Map<*, *>)["id"] })
        assertEquals(mapOf("id" to "p1", "name" to "Ali", "seat" to "s1", "index" to 0), lobby.m("me"))
        r.start()
        val v = r.view(a.token)
        assertEquals("PLAYING", v["stage"]); assertEquals(listOf("s1"), v.l("toMove"))
        @Suppress("UNCHECKED_CAST") val seat1 = v.l("seats")[0] as Map<String, Any?>
        assertEquals(mapOf<String, Any?>("id" to "s1", "index" to 0, "kind" to "PHONE", "name" to "Ali", "connected" to true, "playerId" to "p1", "toMove" to true, "me" to true),
            seat1.filterKeys { it in setOf("id", "index", "kind", "name", "connected", "playerId", "toMove", "me") })
        // le JSON est du vrai JSON et se relit
        assertEquals(v["v"], Json.obj(r.viewJson(a.token))["v"])
        assertEquals(setOf("v", "protocol", "game", "rules", "stage", "code", "settings", "seats", "me", "spectators", "players", "moveNo", "toMove", "legal", "clock", "notice", "result", "table"), v.keys)
        assertNull(v["result"])
    }

    @Test fun theVersionGrowsAtEveryChangeAndWaitersWakeUp() {
        val r = nim(); val a = r.join("Ali"); val b = r.join("Bea")
        val v0 = r.version
        r.start(); assertTrue(r.version > v0)
        val v1 = r.version
        assertEquals(v1, r.awaitChange(v1, 30), "rien n'a changé : le délai s'écoule")
        var woken = -1L
        val t = Thread { woken = r.awaitChange(v1, 5_000) }.apply { start() }
        Thread.sleep(100); r.act(a.token, "move", "take:1", 0)
        t.join(10_000)
        assertTrue(woken > v1, "réveillé par le coup")
        var calls = 0; r.onChange = { calls++ }
        r.act(b.token, "move", "take:1", 1); assertEquals(1, calls, "un coup, un seul changement signalé"); r.onChange = null
    }

    // ---------------------------------------------------------------- vue partielle : jamais la main des autres

    @Test fun everyoneSeesOnlyTheirOwnHandAndNeverTheSeedNorTheFullState() {
        val r = hands(); val a = r.join("Ali"); val b = r.join("Bea"); val s = r.join("Carine")
        assertNull(r.start())
        val eng = r.engine!!
        val handA = eng.state.hands.getValue(PlayerId("s1")).map { it.code }; val handB = eng.state.hands.getValue(PlayerId("s2")).map { it.code }
        assertEquals(3, handA.size); assertEquals(3, handB.size)
        assertEquals(handA, r.view(a.token).m("table").l("hand")); assertEquals(handB, r.view(b.token).m("table").l("hand"))
        assertNull(r.view(s.token).m("table")["hand"], "un spectateur ne voit aucune main"); assertNull(r.view(null).m("table")["hand"], "la table (TV) non plus")
        assertEquals(mapOf("s1" to 3, "s2" to 3), r.view(s.token).m("table").m("handSizes").mapValues { (it.value as Number).toInt() }, "mais le nombre de cartes de chacun est public")
        for ((viewer, own, other) in listOf(Triple(a, handA, handB), Triple(b, handB, handA))) {
            val text = r.viewJson(viewer.token)
            for (c in other) assertFalse(text.contains("\"$c\""), "la main de l'autre ($c) ne doit pas figurer dans la vue de ${viewer.name}")
            for (c in own) assertTrue(text.contains("\"$c\""))
        }
        for (viewer in listOf(s.token, null)) { val text = r.viewJson(viewer); for (c in handA + handB) assertFalse(text.contains("\"$c\""), "ni la TV ni les spectateurs ne voient $c") }
        // ni la graine ni l'état complet, nulle part
        for (text in listOf(a.token, b.token, s.token, null).map { r.viewJson(it) }) { assertFalse(text.contains("seed")); assertFalse(text.contains(eng.seed.toString()), "la graine ne sort jamais avant la fin") }
        // les coups légaux d'un joueur sont ses propres cartes
        assertEquals(handA.distinct().map { "play:$it" }, r.view(a.token).l("legal")); assertTrue(r.view(b.token).l("legal").isEmpty())
        // une carte jouée devient publique
        val played = handA.first()
        assertEquals(ActResult.OK, r.act(a.token, "move", "play:$played", 0))
        assertTrue(r.viewJson(s.token).contains("\"$played\""), "la carte posée sur la table est vue de tous")
        assertEquals(ActResult.ILLEGAL, r.act(b.token, "move", "play:$played", 1), "on ne joue pas la carte d'un autre")
    }

    // ---------------------------------------------------------------- télécommande, ordinateur

    @Test fun soloAgainstTheComputerAnswersAtOnceWithoutDelay() {
        val r = nim(SeatKind.REMOTE, SeatKind.AI)
        assertNull(r.start())
        assertEquals(listOf("take:1", "take:2", "take:3"), r.view(null).l("legal"), "la TV liste les coups légaux de la télécommande")
        val pile = r.view(null).m("table").n("pile")
        assertEquals(ActResult.OK, r.hostMove("take:2"))
        assertEquals(pile - 3, r.view(null).m("table").n("pile"), "l'ordinateur a répondu (premier coup légal : 1)")
        assertEquals(2, r.engine!!.moveNo)
        assertEquals(ActResult.ILLEGAL, r.hostMove("take:7"))
        assertEquals(ActResult.ILLEGAL, r.hostMove("pfff"))
        assertTrue(r.hostResign()); assertEquals(RoomStage.FINISHED, r.roomStage)
        assertEquals(EndReason.RESIGNATION, r.engine!!.result?.reason)
        assertEquals(ActResult.IGNORED, r.hostMove("take:1"), "partie finie")
    }

    @Test fun theComputerWaitsItsDelayAndOnlyThenPlays() {
        val r = nim(SeatKind.REMOTE, SeatKind.AI, aiDelayMs = 700)
        r.start()
        r.hostMove("take:1"); assertEquals(1, r.engine!!.moveNo, "l'ordinateur ne joue pas tout de suite")
        now += 699; r.tick(); assertEquals(1, r.engine!!.moveNo)
        now += 1; r.tick(); assertEquals(2, r.engine!!.moveNo, "700 ms après : il joue")
        now += 5_000; r.tick(); assertEquals(2, r.engine!!.moveNo, "et rien de plus tant que la télécommande n'a pas joué")
        val first = nim(SeatKind.AI, SeatKind.REMOTE, aiDelayMs = 700); first.start()
        assertEquals(0, first.engine!!.moveNo); now += 700; first.tick(); assertEquals(1, first.engine!!.moveNo, "l'ordinateur ouvre la partie")
    }

    @Test fun twoPlayersAtTheTvShareTheRemoteAndPauseIsAllowedOnlyWithoutPhones() {
        val r = nim(SeatKind.REMOTE, SeatKind.REMOTE, clock = MoveClock(10_000))
        r.start()
        assertTrue(r.canPause)
        assertEquals(ActResult.OK, r.hostMove("take:1")); assertEquals(ActResult.OK, r.hostMove("take:1"), "la télécommande joue chacun son tour")
        assertTrue(r.hostPause(true)); now += 60_000; r.tick(); assertEquals(RoomStage.PLAYING, r.roomStage, "pas de perte au temps pendant la pause")
        assertTrue(r.hostPause(false))
        val p = nim(SeatKind.REMOTE, SeatKind.PHONE); p.join("Tel"); p.start()
        assertFalse(p.canPause); assertFalse(p.hostPause(true), "un adversaire sur téléphone n'est jamais mis en attente")
    }

    // ---------------------------------------------------------------- horloge, déconnexion, reprise

    @Test fun theHostClockDecidesTheTimeouts() {
        val r = nim(clock = MoveClock(30_000)); val a = r.join("A"); r.join("B"); r.start()
        assertEquals(30_000L, r.view(a.token).m("clock")["perMoveMs"])
        now += 29_999; r.tick(); assertEquals(RoomStage.PLAYING, r.roomStage)
        now += 1; r.tick()
        assertEquals(RoomStage.FINISHED, r.roomStage)
        val res = r.view(a.token).m("result")
        assertEquals("WINNERS", res["kind"]); assertEquals(listOf("s2"), res["winners"]); assertEquals("TIMEOUT", res["reason"]); assertEquals("s1", res["by"])
        assertTrue((res["text"] as String).contains("à temps"), res["text"] as String)
    }

    @Test fun aPhoneThatGoesSilentPausesItsClockThenForfeitsSixtySecondsAfterItsLastSignOfLife() {
        val r = nim(clock = MoveClock(300_000)); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        r.streamOpened(a); r.streamOpened(b)                 // les deux sont en ligne (flux ouvert)
        now += 10_000; r.tick()
        r.streamClosed(a)                                    // le flux de Ali se coupe : dernier signe de vie à cet instant
        val lastSign = now
        now += 20_000; r.tick()
        assertTrue(r.isConnected(a), "35 s de délai de présence avant de le dire déconnecté")
        now += 20_000; r.tick()                              // 40 s de silence
        assertFalse(r.isConnected(a))
        val seat = r.view(b.token).l("seats")[0] as Map<*, *>
        assertEquals(false, seat["connected"]); assertEquals(20_000L, seat["graceLeftMs"], "60 s de silence depuis le dernier signe de vie : il en reste 20")
        assertEquals(RoomStage.PLAYING, r.roomStage)
        assertEquals(290_000L, r.view(b.token).m("clock")["remainingMs"], "sa pendule s'est arrêtée à son dernier signe de vie : les 40 s de silence lui sont rendues")
        now += 10_000; r.tick()
        assertEquals(290_000L, r.view(b.token).m("clock")["remainingMs"], "et elle ne bouge plus")
        now = lastSign + 60_000; r.tick()
        assertEquals(RoomStage.FINISHED, r.roomStage)
        val res = r.view(b.token).m("result")
        assertEquals("DISCONNECTED", res["reason"]); assertEquals(listOf("s2"), res["winners"]); assertEquals("s1", res["by"])
    }

    @Test fun comingBackWithTheTokenResumesTheGameWhereItStopped() {
        val r = nim(); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        assertEquals(ActResult.OK, r.act(a.token, "move", "take:1", 0))
        r.streamOpened(a); r.streamClosed(a); r.streamOpened(b)
        now += 45_000; r.tick()                              // Ali a disparu depuis 45 s : « déconnecté », 15 s pour revenir
        assertFalse(r.isConnected(a)); assertEquals(RoomStage.PLAYING, r.roomStage)
        assertEquals(15_000L, (r.view(b.token).l("seats")[0] as Map<*, *>)["graceLeftMs"])
        val back = r.joinRoom(r.code, null, a.token).player!!
        assertSame(a, back); assertEquals(0, r.seatOf(back)); assertTrue(r.isConnected(back))
        now += 30_000; r.touch(back); r.tick()
        assertEquals(RoomStage.PLAYING, r.roomStage, "revenu à temps : rien n'est perdu")
        val seat = r.view(back.token).l("seats")[0] as Map<*, *>; assertEquals(true, seat["connected"]); assertNull(seat["graceLeftMs"])
        assertEquals(ActResult.OK, r.act(b.token, "move", "take:1", 1)); assertEquals(ActResult.OK, r.act(back.token, "move", "take:1", 2))
        assertEquals(3, r.engine!!.moveNo)
    }

    @Test fun comingBackAfterSixtySecondsOfSilenceIsTooLate() {
        val r = nim(); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        r.streamOpened(a); r.streamClosed(a); r.streamOpened(b)
        now += 61_000
        r.joinRoom(r.code, null, a.token)                    // revient trop tard : le forfait est prononcé d'abord
        assertEquals(RoomStage.FINISHED, r.roomStage)
        assertEquals("DISCONNECTED", r.view(a.token).m("result")["reason"])
    }

    @Test fun aMoveSentAfterSixtySecondsOfSilenceFindsTheGameAlreadyLost() {
        val r = nim(); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        r.streamOpened(b)
        now += 61_000                                        // Ali s'est tu 61 s (aucun tic de fond n'a tourné) puis envoie son coup
        assertEquals(ActResult.IGNORED, r.act(a.token, "move", "take:1", 0), "son coup n'est pas joué : le forfait est prononcé d'abord")
        assertEquals(RoomStage.FINISHED, r.roomStage)
        val res = r.view(b.token).m("result")
        assertEquals("DISCONNECTED", res["reason"]); assertEquals("s1", res["by"]); assertEquals(0, r.engine!!.moveNo)
    }

    @Test fun everyoneGoneAbandonsTheGameWithoutALoser() {
        val r = nim(); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        r.streamOpened(a); r.streamOpened(b); r.streamClosed(a); r.streamClosed(b)
        now += 100_000; r.tick()
        assertEquals(RoomStage.FINISHED, r.roomStage)
        val res = r.view(null).m("result")
        assertEquals("DRAW", res["kind"]); assertEquals("ABANDONED", res["reason"]); assertNull(res["by"])
    }

    // ---------------------------------------------------------------- fin, journal, nouvelle partie

    @Test fun resignFinishesAndTheResultReadsInFrench() {
        val r = nim(); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        assertEquals(ActResult.OK, r.act(b.token, "resign", null))
        assertEquals(RoomStage.FINISHED, r.roomStage)
        val res = r.view(a.token).m("result")
        assertEquals(listOf("s1"), res["winners"]); assertEquals("RESIGNATION", res["reason"]); assertEquals("Bea a abandonné : Ali gagne la partie", res["text"])
        assertEquals(ActResult.FORBIDDEN, r.act(b.token, "resign", null), "la partie est finie")
        assertEquals(ActResult.IGNORED, r.act(b.token, "move", "take:1", 0), "un coup après la fin n'est pas pris en compte")
        assertEquals(ActResult.FORBIDDEN, r.act(r.join("Spectateur").token, "resign", null))
    }

    @Test fun theFinishedGameYieldsAJournalOnlyAtTheEndWhichReplaysAndHidesTheHands() {
        val r = hands(); val a = r.join("Ali"); val b = r.join("Bea")
        var finished = 0
        r.onFinished = { finished++ }
        r.start()
        assertNull(r.journal(signer.keyId), "pas de journal avant la fin : la graine fixe les mains")
        var moveNo = 0
        while (r.roomStage == RoomStage.PLAYING) {
            val e = r.engine!!; val p = e.toMove().single(); val who = if (p == PlayerId("s1")) a else b
            val card = Hands.legal(e.state, p).first().card.code
            assertEquals(ActResult.OK, r.act(who.token, "move", "play:$card", moveNo++))
        }
        assertEquals(1, finished, "la fin de partie est annoncée une seule fois")
        val j = r.journal(signer.keyId)!!
        assertEquals("mains", j.rulesId); assertEquals(6, j.entries.size)
        assertNull(GameJournal.impossible(j, Hands), "le journal se rejoue : la graine et les coups suffisent")
        val token = GameJournal.sign(j, signer)
        assertEquals(j, (GameJournal.verify(token, KeyRing(listOf(signer.trusted()))) as Verdict.Accepted).value)
        assertEquals(j.gameId, r.journal(signer.keyId)!!.gameId, "le même identifiant de partie à chaque lecture"); assertEquals(j.gameId, r.gameId)
        assertFailsWith<IllegalArgumentException> { r.journal("zz") }
    }

    @Test fun aRematchKeepsTheSeatsAndDealsANewSeed() {
        val r = hands(); val a = r.join("Ali"); val b = r.join("Bea"); r.start()
        val seed1 = r.engine!!.seed; val id1 = r.gameId
        r.act(a.token, "resign", null)
        assertTrue(r.backToLobby()); assertEquals(RoomStage.LOBBY, r.roomStage); assertNull(r.engine)
        assertEquals(0, r.seatOf(a)); assertEquals(1, r.seatOf(b))
        assertNull(r.start())
        assertNotEquals(seed1, r.engine!!.seed, "une nouvelle graine à chaque partie"); assertNotEquals(id1, r.gameId)
        assertEquals(RoomStage.PLAYING, r.roomStage)
    }

    @Test fun closedRoomRefusesEverything() {
        val r = nim(); val a = r.join("A"); r.join("B"); r.start()
        r.close()
        assertEquals(JoinStatus.CLOSED, r.joinRoom(r.code, "C").status)
        assertEquals(ActResult.CLOSED, r.act(a.token, "move", "take:1", 0))
        assertEquals(RoomStage.CLOSED, r.roomStage); assertEquals("La salle est fermée.", r.start())
        assertFalse(r.backToLobby()); assertFalse(r.configure(listOf(SeatKind.PHONE, SeatKind.PHONE), null))
        assertEquals("CLOSED", r.view(null)["stage"]); assertEquals(EndReason.ABANDONED, r.engine!!.result?.reason, "fermer abandonne la partie sans perdant")
        r.close()
    }

    @Test fun cannotReconfigureDuringTheGame() {
        val r = nim(); r.join("A"); r.join("B"); r.start()
        assertFalse(r.configure(listOf(SeatKind.PHONE, SeatKind.PHONE), null))
    }

    @Test fun theNamesOfTheSeatsAreReadableOnTheTvAndOnPhones() {
        val solo = nim(SeatKind.REMOTE, SeatKind.AI); solo.start()
        assertEquals(listOf("Vous", "Ordinateur"), solo.names())
        val mixed = nim(SeatKind.REMOTE, SeatKind.PHONE); assertEquals(listOf("Joueur de la TV", "Téléphone (libre)"), mixed.names())
        mixed.join("Awa"); assertEquals(listOf("Joueur de la TV", "Awa"), mixed.names())
        val two = nim(SeatKind.REMOTE, SeatKind.REMOTE); assertEquals(listOf("Joueur 1", "Joueur 2"), two.names())
    }

    @Test fun theBackgroundTickerPlaysTheComputerAndShutsDownWithTheRoom() {
        val r = RulesRoom(Nim, clock = { System.nanoTime() / 1_000_000 }, random = java.util.Random(3), autoTick = true, aiDelayMs = 50)
        r.configure(listOf(SeatKind.REMOTE, SeatKind.AI), null); r.start(); r.hostMove("take:1")
        val end = System.nanoTime() + 5_000_000_000L
        while (r.engine!!.moveNo < 2 && System.nanoTime() < end) Thread.sleep(20)
        assertEquals(2, r.engine!!.moveNo, "le fil de fond a joué l'ordinateur après son délai")
        r.close()
    }
}
