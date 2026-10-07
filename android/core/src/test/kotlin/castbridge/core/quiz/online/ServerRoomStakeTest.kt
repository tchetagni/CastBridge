package castbridge.core.quiz.online

import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QuizDuel
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.Verdict
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le Quiz MISÉ (games-G5, W22 § 3.3 option B), sur `ServerRoom` seul, en mémoire : le hub (ici le test) a déjà vérifié les blocages `cbe1` ; la salle exige le blocage de chaque TV, une mise par SIÈGE (les
 * téléphones relayés, au plus le `k` de la TV), au moins deux TV pour partir, sièges figés au départ, cagnotte partagée selon le classement (`Pot.split`, ex æquo à parts égales, personne n'a marqué = chacun
 * reprend sa mise), résultat `cbr1` signé UNE fois et relu avec la clé publique du service, `ABORT` pour toute partie interrompue. Une salle LIBRE ne change pas.
 */
class ServerRoomStakeTest {
    private val bank = EmbeddedQuestionSource().bank()
    private val signer = Ed25519Signer(MessageDigest.getInstance("SHA-256").digest("castbridge-quiz-room-test-result-key".toByteArray()))
    private val ring = KeyRing(listOf(signer.trusted()))
    private val spooled = ArrayList<Pair<String, String>>()
    private val ROOM = "0123456789abcdef0123456789abcdef"
    private val A = "AAAA-AAAA-AAAA-AAAA"
    private val B = "BBBB-BBBB-BBBB-BBBB"
    private val C = "CCCC-CCCC-CCCC-CCCC"
    private val SPEC = StakeSpec("NDEM", 20)

    private fun eid(c: Char) = "Eid" + c.toString().repeat(19)
    private fun escrow(id: String, k: Int = 1, per: Long = 20) = QuizEscrow(eid(id[0]), id, k, per * k)

    private fun room(duel: Int = 3, stake: StakeSpec? = SPEC, withSigner: Boolean = true, settings: ServerRoom.Settings = ServerRoom.Settings(duelCount = duel)) =
        ServerRoom(ROOM, PlayScope.INTERNET, bank, java.util.Random(11), createdAt = 0, settings = settings, stake = stake, signer = if (withSigner) signer else null, onResult = { rid, t -> spooled += rid to t })

    private fun List<ServerRoom.Out>.to(conn: String) = filter { it.to == conn }.map { it.msg }
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.typed(conn: String) = to(conn).filterIsInstance<T>()
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.one(conn: String? = null): T = (if (conn == null) map { it.msg } else to(conn)).filterIsInstance<T>().first()
    private fun List<ServerRoom.Out>.error(conn: String? = null): ServerMsg.Error? = (if (conn == null) map { it.msg } else to(conn)).filterIsInstance<ServerMsg.Error>().firstOrNull()
    private fun List<ServerRoom.Out>.ack(): String = map { it.msg }.filterIsInstance<ServerMsg.Ack>().single().result

    private fun verified(token: String): PlayResult {
        val v = PlayResult.verify(token, ring)
        assertTrue(v is Verdict.Accepted, "le résultat est authentique et cohérent : $v")
        return (v as Verdict.Accepted).value
    }

    private fun ServerRoom.host(k: Int = 2, id: String = A, escrow: QuizEscrow? = escrow(id, k), name: String? = null, mode: String? = "DUEL") =
        handle("tvA", ClientMsg.Create(name, mode), 0, "198.51.100.1", ServerRoom.Admission(id, escrow))

    /** Une TV invitée avec son blocage : entrée (spectatrice relais), puis le SERVICE lui accorde le relais, comme le fait le hub. */
    private fun ServerRoom.guest(conn: String, id: String, k: Int = 1, escrow: QuizEscrow? = escrow(id, k), now: Long = 0): List<ServerRoom.Out> {
        val o = handle(conn, ClientMsg.Join(code, "TV $id", null, dv(), true), now, "198.51.100.2", ServerRoom.Admission(id, escrow))
        if (o.typed<ServerMsg.Welcome>(conn).isNotEmpty()) assertTrue(grantRelay(conn))
        return o
    }

    private fun ServerRoom.phone(tv: String, name: String, now: Long = 0): List<ServerRoom.Out> = handle(tv, ClientMsg.Join(code, name, null, dv(), false), now)
    private fun ServerRoom.phoneToken(tv: String, name: String): String = phone(tv, name).one<ServerMsg.Welcome>().token

    private fun ServerRoom.begin(now: Long = 0): List<ServerRoom.Out> {
        handle("tvA", ClientMsg.Act(null, "mode", null, "DUEL", 1), now)
        return handle("tvA", ClientMsg.Act(null, "start", null, "5", 2), now)
    }

    private class Phone(val tv: String, val token: String, val right: (Int) -> Boolean)

    /** Joue le Duel jusqu'au bout : chaque téléphone répond par SA TV (`relayAct`), l'hôte passe les écrans. [spread] : écart (ms) entre deux réponses (0 = au même instant : ex æquo possibles). */
    private fun ServerRoom.drive(phones: List<Phone>, start: Long = 0, spread: Long = 100): Pair<Long, List<ServerRoom.Out>> {
        var now = start; var guard = 0; var seq = 100L
        val outs = ArrayList<ServerRoom.Out>()
        while (phase() == ServerRoom.State.PLAYING && guard++ < 10_000) {
            val d = table(0).room.duel!!
            if (d.phase == QuizDuel.Phase.QUESTION && now >= table(0).opensAtServerMs) {
                val q = d.question
                for (p in phones) { now += spread; outs += handle(p.tv, ClientMsg.RelayAct(p.token, q.id, if (p.right(d.index)) q.answer else (q.answer + 1) % 4, 900L, seq++), now) }
            }
            now += 500
            outs += tick(now)
            if (d.phase == QuizDuel.Phase.BOARD || d.phase == QuizDuel.Phase.REVEAL) outs += handle("tvA", ClientMsg.Act(null, "skip", null, null, seq++), now)
        }
        return now to outs
    }

    /** Une table prête : l'hôte A (k = 2) avec Alice et Ann, la TV B (k = 2, un seul téléphone ici) avec Carl. Rend aussi les jetons. */
    private inner class Table(val r: ServerRoom, val alice: String, val ann: String, val carl: String, val tokenB: String)

    private fun table(duel: Int = 3): Table {
        val r = room(duel)
        r.host(k = 2)
        val b = r.guest("tvB", B, k = 2)
        return Table(r, r.phoneToken("tvA", "Alice"), r.phoneToken("tvA", "Ann"), r.phoneToken("tvB", "Carl"), b.one<ServerMsg.Welcome>("tvB").token)
    }

    // ------------------------------------------------------------------ une salle libre ne change pas

    @Test fun aFreeRoomIsExactlyAsBefore() {
        val r = room(stake = null)
        val c = r.handle("tvA", ClientMsg.Create(null, "DUEL"), 0)
        assertEquals(PlayRole.HOST, c.one<ServerMsg.Welcome>().role)
        assertNull(c.error(), "aucun blocage demandé")
        val g = r.handle("tvB", ClientMsg.Join(r.code, "TV B", null, dv(), true), 0)
        assertEquals(PlayRole.SPECTATOR, g.one<ServerMsg.Welcome>().role)
        r.grantRelay("tvB")
        r.phone("tvB", "Carl"); r.phone("tvA", "Alice")
        assertEquals("OK", r.begin().ack())
        val v = c.filter { it.msg is ServerMsg.State }.map { (it.msg as ServerMsg.State).view }.last()
        assertFalse("stake" in v, "pas de bloc `stake` dans une salle libre")
        @Suppress("UNCHECKED_CAST") assertEquals("Compétition entre amis", (v["settings"] as Map<String, Any?>)["playLabel"], "le libellé d'une salle libre ne change pas")
        r.drive(emptyList())
        assertNull(r.resultToken, "aucun résultat signé pour une salle libre")
        assertTrue(spooled.isEmpty())
        assertEquals(false, r.isStaked)
    }

    // ------------------------------------------------------------------ l'entrée : une mise bloquée par TV

    @Test fun theHostMustBringItsEscrowAndIsToldTheStakeOtherwise() {
        val r = room()
        val refused = r.handle("tvA", ClientMsg.Create(null, "DUEL"), 0, null, ServerRoom.Admission(A, null))
        val e = refused.error("tvA")!!
        assertEquals("STAKE_ESCROW_REQUIRED", e.reason); assertTrue(e.retryable)
        assertEquals(mapOf("game" to "quiz", "cur" to "NDEM", "per" to 20L), e.data, "la TV apprend quoi bloquer")
        assertTrue(refused.typed<ServerMsg.Welcome>("tvA").isEmpty(), "aucune place sans blocage")
        // une salle misée est un Duel : le Millionnaire est refusé, avec ou sans blocage
        assertEquals(PlayProtocol.BAD_REQUEST, r.host(mode = "MILLIONAIRE").error("tvA")?.reason)
        val ok = r.host()
        assertEquals(PlayRole.HOST, ok.one<ServerMsg.Welcome>().role)
        val v = ok.filter { it.msg is ServerMsg.State }.map { (it.msg as ServerMsg.State).view }.last()
        @Suppress("UNCHECKED_CAST") val s = v["stake"] as Map<String, Any?>
        assertEquals("NDEM", s["cur"]); assertEquals(20L, s["per"]); assertEquals(1, s["tvs"]); assertEquals(false, s["started"]); assertEquals(false, s["settled"])
        assertEquals(listOf(eid('A')), r.escrowIds())
        @Suppress("UNCHECKED_CAST") assertEquals("Partie avec mise", (v["settings"] as Map<String, Any?>)["playLabel"], "la page des téléphones écrit « Partie avec mise », pas « Compétition entre amis »")
    }

    @Test fun aGuestTvNeedsItsOwnEscrowOneTvOneEscrowAndNoWatchOnly() {
        val r = room(); r.host()
        val no = r.guest("tvB", B, escrow = null)
        assertEquals("STAKE_ESCROW_REQUIRED", no.error("tvB")?.reason, "pas de blocage : la mise à bloquer est dite, rien n'est consommé")
        assertEquals(mapOf("game" to "quiz", "cur" to "NDEM", "per" to 20L), no.error("tvB")?.data)
        assertTrue(no.typed<ServerMsg.Welcome>("tvB").isEmpty(), "pas de simple regard sur une salle misée")
        assertEquals(PlayRole.SPECTATOR, r.guest("tvB", B).one<ServerMsg.Welcome>("tvB").role, "avec son blocage, la TV entre (spectatrice relais)")
        assertEquals("SAME_TV", r.guest("tvB2", B).error("tvB2")?.reason, "une TV ne mise qu'une fois")
        assertEquals("SAME_TV", r.guest("tvA2", A).error("tvA2")?.reason, "l'hôte non plus ne revient pas en invité")
        assertEquals(listOf(eid('A'), eid('B')), r.escrowIds())
    }

    @Test fun aRoomTakesAtMostEightStakingTvsAndNoNewTvOnceTheGameStarted() {
        val r = room(); r.host()
        val ids = "BCDEFGH".map { ch -> ch.toString().repeat(4) + "-" + ch.toString().repeat(4) + "-" + ch.toString().repeat(4) + "-" + ch.toString().repeat(4) }
        ids.forEachIndexed { i, id -> assertEquals(PlayRole.SPECTATOR, r.guest("tv$i", id).one<ServerMsg.Welcome>("tv$i").role) }
        assertEquals("PLAY_ROOM_FULL", r.guest("tvX", "JJJJ-JJJJ-JJJJ-JJJJ").error("tvX")?.reason, "la 9e TV est refusée")
        val s = room(); s.host(); s.guest("tvB", B)
        s.phone("tvA", "Alice"); s.phone("tvB", "Carl")
        assertEquals("OK", s.begin().ack())
        val late = s.guest("tvC", C)
        assertEquals("STAKE_ROOM_STARTED", late.error("tvC")?.reason, "les mises sont figées au départ")
        assertEquals(ServerRoom.State.PLAYING, s.phase())
    }

    @Test fun eachTvSeatsAtMostAsManyPlayersAsItBlockedStakes() {
        val r = room(); r.host(k = 2); r.guest("tvB", B, k = 1)
        r.phone("tvA", "Alice"); r.phone("tvA", "Ann")
        assertEquals("PLAY_ROOM_FULL", r.phone("tvA", "Anna").error("tvA")?.reason, "la TV A a bloqué 2 mises : un 3e téléphone n'a pas de place")
        assertEquals(PlayRole.PLAYER, r.phone("tvB", "Carl").one<ServerMsg.Welcome>("tvB").role)
        assertEquals("PLAY_ROOM_FULL", r.phone("tvB", "Dan").error("tvB")?.reason, "la TV B n'a bloqué qu'une mise")
        assertEquals(3, r.table(0).room.players().size)
    }

    @Test fun aSeatedPhoneComesBackWithItsTokenAfterTheStartButNobodyNewSits() {
        val t = table()
        t.r.begin()
        assertEquals("STAKE_ROOM_STARTED", t.r.phone("tvA", "Intrus").error("tvA")?.reason)
        // la reprise d'un siège existant reste permise (coupure de la TV puis retour)
        val back = t.r.handle("tvA", ClientMsg.Join(t.r.code, "Alice", t.alice, dv(), false), 10)
        assertEquals(t.alice, back.one<ServerMsg.Welcome>("tvA").token)
    }

    // ------------------------------------------------------------------ le départ : deux TV qui misent

    @Test fun theGameNeedsTwoTvsEachWithAPlayerAndTheStakersAreFrozenAtTheStart() {
        val r = room(); r.host(k = 2); r.phone("tvA", "Alice"); r.phone("tvA", "Ann")
        val one = r.begin()
        assertEquals("BAD_REQUEST", one.ack()); assertTrue(one.error("tvA")!!.message.contains("au moins deux TV"), one.error("tvA")!!.message)
        assertEquals(ServerRoom.State.OPEN, r.phase())
        r.guest("tvB", B, k = 2)
        assertEquals("BAD_REQUEST", r.begin().ack(), "une TV qui n'a aucun joueur ne compte pas")
        r.phone("tvB", "Carl")
        val started = r.begin()
        assertEquals("OK", started.ack())
        assertEquals(ServerRoom.State.PLAYING, r.phase())
        val frozen = r.stakers()
        assertEquals(listOf(eid('A'), eid('B')), frozen.keys.toList())
        assertEquals(listOf(2, 1), frozen.values.map { it.size })
        val s = stakeOf(started, "tvA")
        assertEquals(60L, s["pot"]); assertEquals(3, s["seats"]); assertEquals(true, s["started"])
    }

    /** Le bloc `stake` de la dernière vue envoyée à la connexion [conn]. */
    private fun stakeOf(outs: List<ServerRoom.Out>, conn: String): Map<*, *> = outs.typed<ServerMsg.State>(conn).last().view["stake"] as Map<*, *>

    // ------------------------------------------------------------------ le règlement

    @Test fun thePotIsSplitByRankingAndAggregatedPerTvWithTheUnusedStakeGivenBack() {
        val t = table()
        t.r.begin()
        val phones = listOf(Phone("tvA", t.alice) { true }, Phone("tvA", t.ann) { false }, Phone("tvB", t.carl) { it == 0 })
        val (now, outs) = t.r.drive(phones)
        assertEquals(ServerRoom.State.FINISHED, t.r.phase())
        val token = t.r.resultToken
        assertNotNull(token)
        val res = verified(token!!)
        assertEquals("quiz", res.game); assertEquals(PlayResult.Kind.END, res.kind); assertEquals(20L, res.per); assertEquals(QuizSettlement.ridOf(ROOM), res.rid)
        // A : Alice + Ann = 2 sièges misés (40 utilisés) ; B : Carl = 1 siège sur 2 bloqués (20 utilisés, 20 rendus par l'API)
        assertEquals(listOf(40L, 20L), res.lines.map { it.used })
        assertEquals(listOf(42L, 18L), res.lines.map { it.pay }, "classement Alice > Carl > Ann (0 point) : 70 % / 30 % de 60, agrégé par TV")
        assertEquals(res.lines.sumOf { it.used }, res.lines.sumOf { it.pay })
        // les deux TV (et elles seules) reçoivent le même résultat, après le dernier état ; la salle l'a déposé une fois pour le collecteur
        for (tv in listOf("tvA", "tvB")) {
            val rs = outs.typed<ServerMsg.Result>(tv)
            assertEquals(1, rs.size, tv); assertEquals(token, rs.single().token)
            val lastState = outs.indexOfLast { it.to == tv && it.msg is ServerMsg.State }
            assertTrue(outs.indexOfFirst { it.to == tv && it.msg is ServerMsg.Result } > lastState - 1, "le résultat suit l'état final")
        }
        assertEquals(listOf(QuizSettlement.ridOf(ROOM) to token), spooled)
        // le bloc `stake` de la vue dit l'issue et la part de chaque joueur (avant frais éventuels)
        val st = stakeOf(outs, "tvA")
        assertEquals(true, st["settled"]); assertEquals("SPLIT", st["outcome"])
        val payouts = st["payouts"] as Map<*, *>
        assertEquals(60L, payouts.values.sumOf { it as Long }, "la cagnotte est entièrement distribuée")
        // un tick de plus ne signe rien de nouveau
        t.r.tick(now + 60_000)
        assertEquals(1, spooled.size)
    }

    @Test fun aTieSharesTheTopPlacesEqually() {
        val r = room(); r.host(k = 1); r.guest("tvB", B, k = 1)
        val pa = r.phoneToken("tvA", "Alice"); val pb = r.phoneToken("tvB", "Carl")
        r.begin()
        r.drive(listOf(Phone("tvA", pa) { true }, Phone("tvB", pb) { true }), spread = 0)   // au même instant : mêmes points
        val res = verified(r.resultToken!!)
        val d = r.table(0).room.duel!!
        assertEquals(d.ranking()[0].second, d.ranking()[1].second, "ex æquo")
        assertEquals(listOf(20L, 20L), res.lines.map { it.pay }, "ex æquo : 70 + 30 = 100 % partagés à parts égales ⇒ chacun reprend sa mise")
        assertEquals(res.lines.map { it.used }, res.lines.map { it.pay })
    }

    @Test fun nobodyScoredEveryTvGetsItsUsedStakeBack() {
        val t = table()
        t.r.begin()
        val (_, outs) = t.r.drive(listOf(Phone("tvA", t.alice) { false }, Phone("tvA", t.ann) { false }, Phone("tvB", t.carl) { false }))
        val res = verified(t.r.resultToken!!)
        assertEquals(PlayResult.Kind.END, res.kind)
        assertEquals(listOf(40L, 20L), res.lines.map { it.used }); assertEquals(res.lines.map { it.used }, res.lines.map { it.pay }, "cagnotte non distribuable : chacun reprend sa mise utilisée")
        val st = stakeOf(outs, "tvA")
        assertEquals("REFUND", st["outcome"]); assertNull(st["payouts"])
    }

    @Test fun threePlayersOnThreeTvsShareSixtyThirtyTen() {
        val r = room(); r.host(k = 1); r.guest("tvB", B, k = 1); r.guest("tvC", C, k = 1)
        val pa = r.phoneToken("tvA", "Alice"); val pb = r.phoneToken("tvB", "Carl"); val pc = r.phoneToken("tvC", "Dora")
        r.begin()
        r.drive(listOf(Phone("tvA", pa) { true }, Phone("tvB", pb) { it < 2 }, Phone("tvC", pc) { it == 0 }))
        val res = verified(r.resultToken!!)
        assertEquals(listOf(20L, 20L, 20L), res.lines.map { it.used })
        assertEquals(listOf(36L, 18L, 6L), res.lines.map { it.pay }, "60 % / 30 % / 10 % de 60")
    }

    // ------------------------------------------------------------------ interruptions : chacun reprend sa mise

    @Test fun theHostCancelsInTheLobbyAndEveryEscrowIsGivenBackInFull() {
        val r = room(); r.host(); r.guest("tvB", B, k = 3)
        val o = r.handle("tvA", ClientMsg.GameAct("cancel", null, null, 5), 100)
        assertEquals("OK", o.ack())
        val res = verified(r.resultToken!!)
        assertEquals(PlayResult.Kind.ABORT, res.kind); assertTrue(res.lines.all { it.used == 0L && it.pay == 0L }); assertEquals(listOf(A, B), res.lines.map { it.id })
        for (tv in listOf("tvA", "tvB")) assertEquals(r.resultToken, o.typed<ServerMsg.Result>(tv).single().token)
        assertEquals(1, spooled.size)
        assertEquals("FORBIDDEN", r.handle("tvB", ClientMsg.GameAct("cancel", null, null, 6), 200).ack())
        assertEquals("BAD_REQUEST", r.begin(300).ack(), "une salle annulée ne démarre plus")
    }

    @Test fun onlyTheHostCancelsAndOnlyBeforeTheStart() {
        val t = table()
        assertEquals("FORBIDDEN", t.r.handle("tvB", ClientMsg.GameAct("cancel", null, null, 5), 1).ack(), "une TV invitée ne ferme pas la salle de l'hôte")
        assertEquals(ServerRoom.State.OPEN, t.r.phase())
        t.r.begin()
        assertEquals("FORBIDDEN", t.r.handle("tvA", ClientMsg.GameAct("cancel", null, null, 6), 2).ack(), "après le départ on ne s'en va pas avec sa mise")
        assertNull(t.r.resultToken)
    }

    @Test fun aClosedRoomWhilePlayingAbortsAndTheResultComesBeforeTheEndOfTheRoom() {
        val t = table()
        t.r.begin()
        val o = t.r.close("SHUTDOWN", 5_000)
        val res = verified(t.r.resultToken!!)
        assertEquals(PlayResult.Kind.ABORT, res.kind); assertTrue(res.lines.all { it.used == 0L && it.pay == 0L })
        for (tv in listOf("tvA", "tvB")) {
            val order = o.to(tv).map { it::class.simpleName }
            assertEquals(listOf("Result", "RoomGone"), order, "$tv : le résultat avant la fin de la salle")
        }
        assertEquals(ServerRoom.State.GONE, t.r.phase())
        assertEquals(1, spooled.size)
    }

    @Test fun anExpiredRoomThatNeverStartedRefundsEveryEscrow() {
        val r = room(); r.host(); r.guest("tvB", B)
        r.tick(RoomCode.TTL_MS + 1_000)
        assertEquals(ServerRoom.State.GONE, r.phase())
        val res = verified(r.resultToken!!)
        assertEquals(PlayResult.Kind.ABORT, res.kind); assertEquals(2, res.lines.size)
    }

    @Test fun aLostHostNeverAbortsAStakedGameItsStakeStaysInPlay() {
        val t = table()
        t.r.begin()                                  // aucun « hôte automatique » demandé : le service l'impose aux parties misées (la TV le demande de toute façon)
        t.r.disconnect("tvA", 1_000)
        t.r.tick(1_000 + ServerRoom.HOST_LOST_MS + 1_000)
        assertEquals(ServerRoom.State.PLAYING, t.r.phase()); assertNull(t.r.table(0).abandoned)
        assertNull(t.r.resultToken, "un hôte qui perd ne se fait pas rembourser en débranchant sa TV")
        // la partie va au bout avec la seule TV B : les joueurs de A ne répondent plus (0 point) mais leur mise reste en jeu ; règlement normal
        t.r.drive(listOf(Phone("tvB", t.carl) { true }), start = 100_000)
        assertEquals(ServerRoom.State.FINISHED, t.r.phase())
        val res = verified(t.r.resultToken!!)
        assertEquals(PlayResult.Kind.END, res.kind)
        assertEquals(listOf(40L, 20L), res.lines.map { it.used }, "les trois sièges figés au départ misent, joueurs de la TV perdue compris")
        assertEquals(listOf(0L, 60L), res.lines.map { it.pay }, "Carl seul a marqué : il prend la cagnotte")
    }

    @Test fun aGuestTvThatLeftBeforeTheStartDoesNotPlayAndItsStakeComesBackInFull() {
        // « Quitter » avant le départ promet « votre mise sera rendue » : la TV partie ne joue pas, ses téléphones quittent la table, son blocage reste au résultat avec « utilisé 0 »
        val r = room(); r.host(k = 1); r.guest("tvB", B, k = 1); r.guest("tvC", C, k = 1)
        val pa = r.phoneToken("tvA", "Alice"); val pb = r.phoneToken("tvB", "Carl"); r.phoneToken("tvC", "Dora")
        assertEquals(3, r.table(0).room.players().size)
        val before = stakeOf(r.disconnect("tvC", 500), "tvA")
        assertEquals(2, before["tvs"], "le salon ne compte que les TV présentes"); assertEquals(40L, before["pot"])
        assertEquals("OK", r.begin(700).ack())
        assertEquals(2, r.table(0).room.players().size, "les téléphones de la TV partie n'entrent pas dans le Duel")
        assertEquals(listOf(1, 1, 0), r.stakers().values.map { it.size }, "sièges figés : A 1, B 1, C aucun")
        r.drive(listOf(Phone("tvA", pa) { true }, Phone("tvB", pb) { it == 0 }), start = 1_000)
        val res = verified(r.resultToken!!)
        assertEquals(PlayResult.Kind.END, res.kind)
        assertEquals(listOf(A, B, C), res.lines.map { it.id }, "le blocage de la TV partie figure au résultat")
        assertEquals(listOf(20L, 20L, 0L), res.lines.map { it.used }, "elle n'a rien engagé : l'API lui rend tout")
        assertEquals(listOf(28L, 12L, 0L), res.lines.map { it.pay }, "70 / 30 de 40 entre les deux TV qui ont joué")
    }

    @Test fun aTvThatLeftDoesNotCountToReachTwoStakingTvs() {
        val r = room(); r.host(k = 1); r.guest("tvB", B, k = 1)
        r.phone("tvA", "Alice"); r.phone("tvB", "Carl")
        assertEquals(1, stakeOf(r.disconnect("tvB", 500), "tvA")["tvs"], "la TV B n'est plus là : une seule TV mise")
        val refused = r.begin(600)
        assertEquals("BAD_REQUEST", refused.ack()); assertTrue(refused.error("tvA")!!.message.contains("au moins deux TV"))
        assertEquals(ServerRoom.State.OPEN, r.phase())
    }

    @Test fun aLostGuestTvKeepsItsStakeInPlayToo() {
        val t = table()
        t.r.begin()
        t.r.disconnect("tvB", 1_000)
        t.r.tick(1_000 + ServerRoom.HOST_LOST_MS + 1_000)
        assertEquals(ServerRoom.State.PLAYING, t.r.phase()); assertNull(t.r.resultToken)
        t.r.drive(listOf(Phone("tvA", t.alice) { true }), start = 100_000)
        val res = verified(t.r.resultToken!!)
        assertEquals(PlayResult.Kind.END, res.kind); assertEquals(listOf(40L, 20L), res.lines.map { it.used }); assertEquals(listOf(60L, 0L), res.lines.map { it.pay })
    }

    @Test fun theHostCannotEndAStakedGameToGetRefunded() {
        val t = table()
        t.r.begin()
        val o = t.r.handle("tvA", ClientMsg.Act(null, "end", null, null, 9), 10)
        assertEquals("FORBIDDEN", o.ack()); assertTrue(o.error("tvA")!!.message.contains("avec mise"))
        assertEquals(ServerRoom.State.PLAYING, t.r.phase()); assertNull(t.r.resultToken)
    }

    @Test fun aStakedRoomPlaysOneGameOnly() {
        val t = table()
        t.r.begin()
        t.r.drive(listOf(Phone("tvA", t.alice) { true }, Phone("tvB", t.carl) { false }))
        assertNotNull(t.r.resultToken)
        assertEquals("FORBIDDEN", t.r.handle("tvA", ClientMsg.Act(null, "lobby", null, null, 20), 100_000).ack(), "pas de retour en salle d'attente : les mises sont réglées")
        val again = t.r.handle("tvA", ClientMsg.Act(null, "start", null, "5", 21), 100_001)
        assertEquals("BAD_REQUEST", again.ack()); assertTrue(again.error("tvA")!!.message.contains("terminée"))
        assertEquals(1, spooled.size, "un seul résultat par salle")
    }

    @Test fun aTvThatComesBackAfterTheEndGetsItsSignedResultAgain() {
        val t = table()
        t.r.begin()
        t.r.drive(listOf(Phone("tvA", t.alice) { true }, Phone("tvB", t.carl) { false }))
        t.r.disconnect("tvB", 200_000)
        val back = t.r.handle("tvB2", ClientMsg.Resume(ROOM, t.tokenB, 0), 201_000)
        assertEquals(t.r.resultToken, back.typed<ServerMsg.Result>("tvB2").single().token)
        assertTrue(back.typed<ServerMsg.State>("tvB2").isNotEmpty())
    }

    @Test fun aGuestTvWithoutPlayersKeepsItsEscrowInTheResultEvenIfItLeftForLong() {
        val r = room(); r.host(k = 2); r.guest("tvB", B, k = 2); r.guest("tvC", C, k = 1)
        r.phone("tvA", "Alice"); r.phone("tvB", "Carl")
        r.disconnect("tvC", 1_000)
        r.tick(1_000 + ServerRoom.SPECTATOR_PURGE_MS + 10_000)   // 5 min d'absence : un spectateur ordinaire serait purgé, pas une TV qui a bloqué une mise
        r.begin(400_000)
        r.close("SHUTDOWN", 410_000)
        val res = verified(r.resultToken!!)
        assertEquals(listOf(A, B, C), res.lines.map { it.id }, "le blocage de la TV C figure dans le résultat : il est rendu tout de suite, pas à l'échéance")
    }

    @Test fun anEightTvResultStaysUnderTheApiLimit() {
        val r = room(); r.host(k = 1)
        val ids = "BCDEFGH".map { ch -> ch.toString().repeat(4) + "-" + ch.toString().repeat(4) + "-" + ch.toString().repeat(4) + "-" + ch.toString().repeat(4) }
        ids.forEachIndexed { i, id -> r.guest("tv$i", id, k = 1); r.phone("tv$i", "J$i") }
        r.phone("tvA", "Hote")
        r.close("SHUTDOWN", 10)
        val token = r.resultToken!!
        assertTrue(token.length < 4_096, "plafond de lecture de l'API : ${token.length}")
        assertEquals(8, verified(token).lines.size)
    }

    @Test fun aRoomWithoutTheServiceKeySignsNothing() {
        val r = room(withSigner = false)
        r.host(); r.guest("tvB", B); r.phone("tvA", "Alice"); r.phone("tvB", "Carl")
        r.close("SHUTDOWN", 10)
        assertNull(r.resultToken); assertTrue(spooled.isEmpty())
    }
}
