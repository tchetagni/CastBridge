package castbridge.play.quizstake

import castbridge.core.owner.ActivationKind
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizDuel
import castbridge.core.quiz.online.ChessOptions
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.QuizSettlement
import castbridge.core.quiz.online.ServerRoom
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.Verdict
import castbridge.play.HubFixture
import castbridge.play.PlayConfig
import castbridge.play.PlayHub
import castbridge.play.StakeServices
import castbridge.play.TestConn
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.chess.StakeKit
import castbridge.play.dev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Le service héberge le Quiz MISÉ (games-G5, option B de W22 § 3.3) : MÊME chaîne d'entrée que le Quiz libre (ticket `cbp1`, activation `cbx1`, preuve), puis la porte des blocages `cbe1` (clé PUBLIQUE du
 * portefeuille, identité de CETTE TV, monnaie et mise de la salle, de 1 à 8 sièges), la salle de Quiz arbitre, le résultat `cbr1` est signé avec la clé dédiée et déposé pour le collecteur. Boucle locale du vrai
 * [PlayHub] : plusieurs « TV » (connexions simulées) dont les téléphones jouent PAR elles (`join` d'une connexion assise, `relayAct`). Les résultats sont relus avec la clé publique du service, comme le fait l'API.
 */
class QuizStakeHubTest {
    private var now = 1_000_000L
    private var seq = 100L
    private val spec = StakeSpec("NDEM", 20)
    private fun cfg() = HubFixture.config("webPlay" to false)
    private fun hub(stakes: StakeServices? = StakeKit.services(), duel: Int = 3): PlayHub =
        HubFixture.hub(cfg(), settings = ServerRoom.Settings(duelCount = duel), stakes = stakes, clock = { now })

    private val tvA = TestRights.tv
    private val tvB = TestRights.otherTv
    private val tvC = TestRights.Tv("C")

    /** Une TV connectée au service : sa connexion simulée, son ticket et son activation. */
    private inner class Peer(val hub: PlayHub, val tv: TestRights.Tv, val trial: Boolean = false, ip: String = "198.51.100.${(10..250).random()}") {
        val conn = TestConn(ip)
        val activation: String = if (trial) TestRights.activation(ActivationKind.TRIAL, device = tv, rights = TestRights.trialUsage()) else TestRights.activation(device = tv)
        init { hub.register(conn) }

        fun hello(ticket: String = TestKeys.ticket(deviceCode = tv.code)) = hub.onText(conn, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket)))
        fun send(m: ClientMsg) = hub.onText(conn, PlayCodec.encode(m))
        fun escrow(cur: String = "NDEM", per: Long = 20, k: Int = 1, id: String = tv.code) = StakeKit.escrow(id, cur, per, k)

        /** `create` d'un Quiz : libre (sans mise) ou misé (mise + blocage). */
        fun create(stake: StakeSpec? = null, escrow: String? = null, mode: String? = "DUEL") { hello(); send(ClientMsg.Create(null, mode, activation, emptyList(), null, stake = stake, escrow = escrow)) }
        /** Entrée d'une TV invitée (spectatrice relais) avec ou sans blocage ; le ticket est celui de `hello`. */
        fun join(code: String, escrow: String? = null) = send(ClientMsg.Join(code, "TV ${tv.name}", null, dev(), true, activation, null, escrow))
        /** Un joueur local relayé par cette TV : la connexion assise envoie `join` sans activation. */
        fun phone(code: String, name: String): String? { val n = welcomes().size; send(ClientMsg.Join(code, name, null, dev(), false)); return welcomes().getOrNull(n)?.get("token") as String? }

        fun msgs(): List<Map<*, *>> = synchronized(conn.out) { conn.out.map { Json.parse(it) as Map<*, *> } }
        fun welcomes() = msgs().filter { it["t"] == "welcome" }
        fun errors() = msgs().filter { it["t"] == "error" }
        fun reason() = errors().lastOrNull()?.get("reason")
        fun results() = msgs().filter { it["t"] == "result" }.map { it["token"] as String }
        fun code() = welcomes().first()["code"] as String
        @Suppress("UNCHECKED_CAST") fun lastView(): Map<String, Any?> = msgs().last { it["t"] == "state" }["view"] as Map<String, Any?>
    }

    private fun verified(token: String): PlayResult {
        val v = PlayResult.verify(token, StakeKit.resultRing)
        assertTrue(v is Verdict.Accepted, "le résultat est authentique et cohérent : $v")
        return (v as Verdict.Accepted).value
    }

    /** Une partie prête : A (hôte, k = 2) avec Alice et Ann, B (k = 1) avec Carl. */
    private inner class Table(val hub: PlayHub, val a: Peer, val b: Peer, val alice: String, val ann: String, val carl: String)

    private fun table(stakes: StakeServices? = StakeKit.services(), duel: Int = 3): Table {
        val hub = hub(stakes, duel)
        val a = Peer(hub, tvA); a.create(spec, a.escrow(k = 2))
        val b = Peer(hub, tvB); b.hello(); b.join(a.code(), b.escrow(k = 1))
        return Table(hub, a, b, a.phone(a.code(), "Alice")!!, a.phone(a.code(), "Ann")!!, b.phone(a.code(), "Carl")!!)
    }

    private fun Table.start() { a.send(ClientMsg.Act(null, "mode", null, "DUEL", seq++)); a.send(ClientMsg.Act(null, "start", null, "5", seq++)) }

    /** Joue le Duel jusqu'au bout : chaque téléphone répond par sa TV (`relayAct`), l'hôte passe les écrans (`skip`). */
    private fun Table.drive(right: Map<String, (Int) -> Boolean>) {
        val room = hub.rooms().single()
        val owners = mapOf(alice to a, ann to a, carl to b)
        var guard = 0
        while (room.phase() == ServerRoom.State.PLAYING && guard++ < 10_000) {
            val d = room.table(0).room.duel!!
            if (d.phase == QuizDuel.Phase.QUESTION && now >= room.table(0).opensAtServerMs) {
                val q = d.question
                for ((token, peer) in owners) { now += 100; peer.send(ClientMsg.RelayAct(token, q.id, if (right.getValue(token)(d.index)) q.answer else (q.answer + 1) % 4, 900L, seq++)) }
            }
            now += 500; hub.tick()
            if (d.phase == QuizDuel.Phase.BOARD || d.phase == QuizDuel.Phase.REVEAL) a.send(ClientMsg.Act(null, "skip", null, null, seq++))
        }
    }

    // ------------------------------------------------------------------ une salle libre ne change pas

    @Test fun aFreeQuizRoomIsExactlyAsBeforeEvenWhenTheServiceHasStakes() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create()
        assertEquals("HOST", a.welcomes().single()["role"])
        assertFalse("stake" in a.lastView(), "pas de bloc `stake` dans une salle libre")
        val b = Peer(hub, tvB); b.hello(); b.join(a.code())
        assertEquals("SPECTATOR", b.welcomes().single()["role"])
        assertNotNull(b.phone(a.code(), "Carl"))
        assertEquals(false, hub.rooms().single().isStaked)
    }

    // ------------------------------------------------------------------ de la création au résultat signé

    @Test fun aStakedQuizFromEscrowToSignedResultAndSpool() {
        val dir = StakeKit.tempDir()
        val services = StakeKit.services(dir)
        val t = table(services)
        assertTrue(t.hub.rooms().single().isStaked)
        t.a.send(ClientMsg.Act(null, "mode", null, "DUEL", seq++))
        t.a.send(ClientMsg.Act(null, "start", null, "5", seq++))
        assertEquals(ServerRoom.State.PLAYING, t.hub.rooms().single().phase())
        t.drive(mapOf(t.alice to { true }, t.ann to { false }, t.carl to { it == 0 }))
        // les deux TV reçoivent le MÊME résultat signé (jamais un téléphone : aucun ne parle au service)
        val ra = verified(t.a.results().single()); val rb = verified(t.b.results().single())
        assertEquals(ra, rb)
        assertEquals("quiz", ra.game); assertEquals(PlayResult.Kind.END, ra.kind); assertEquals(20L, ra.per); assertEquals(QuizSettlement.ridOf(t.hub.rooms().single().roomId), ra.rid)
        assertEquals(listOf(tvA.code, tvB.code), ra.lines.map { it.id })
        assertEquals(listOf(40L, 20L), ra.lines.map { it.used }, "Alice + Ann misent pour A, Carl pour B (une mise par siège)")
        assertEquals(listOf(42L, 18L), ra.lines.map { it.pay }, "Alice 70 %, Carl 30 %, Ann 0 point : agrégé par TV")
        assertEquals(ra.lines.sumOf { it.used }, ra.lines.sumOf { it.pay })
        // copie déposée pour le collecteur de l'hôte, sous l'identifiant du résultat ; la vue dit l'issue
        assertEquals(listOf(ra.rid), services.spool.list())
        assertEquals(t.a.results().single(), services.spool.read(ra.rid))
        assertEquals("SPLIT", (t.a.lastView()["stake"] as Map<*, *>)["outcome"])
        assertEquals(t.hub.rooms().single().resultToken, t.a.results().single())
    }

    // ------------------------------------------------------------------ l'entrée : la mise est dite, le blocage vérifié

    @Test fun anEscrowIsRequiredTheStakeIsToldAndNothingIsConsumedByTheRefusal() {
        val hub = hub()
        val a = Peer(hub, tvA); a.hello()
        a.send(ClientMsg.Create(null, "DUEL", a.activation, emptyList(), null, stake = spec))
        assertEquals("STAKE_ESCROW_REQUIRED", a.reason())
        @Suppress("UNCHECKED_CAST") val data = a.errors().last()["data"] as Map<String, Any?>
        assertEquals("quiz", data["game"]); assertEquals("NDEM", data["cur"]); assertEquals(20, (data["per"] as Number).toInt())
        assertEquals(0, hub.rooms().size); assertEquals(0, hub.usedTicketCount(), "le ticket n'est pas brûlé : la TV bloque puis recommence avec le MÊME ticket")
        a.send(ClientMsg.Create(null, "DUEL", a.activation, emptyList(), null, stake = spec, escrow = a.escrow(k = 3)))
        assertEquals("HOST", a.welcomes().single()["role"])
        assertEquals(1, hub.usedTicketCount())
        // un blocage sans mise bloquerait l'argent pour rien ; Millionnaire misé : refusé
        val a2 = Peer(hub, tvC); a2.hello()
        a2.send(ClientMsg.Create(null, "DUEL", a2.activation, emptyList(), null, escrow = a2.escrow())); assertEquals("STAKE_BAD", a2.reason())
        a2.send(ClientMsg.Create(null, "MILLIONAIRE", a2.activation, emptyList(), null, stake = spec, escrow = a2.escrow())); assertEquals("STAKE_BAD", a2.reason())
        assertEquals(1, hub.rooms().size)
    }

    @Test fun aTvEntersAStakedRoomOnlyWithItsOwnEscrowOnTheSameLinkAndTicket() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create(spec, a.escrow(k = 2))
        val b = Peer(hub, tvB); b.hello()
        b.join(a.code())
        assertEquals("STAKE_ESCROW_REQUIRED", b.reason(), "pas de simple regard d'une salle misée")
        @Suppress("UNCHECKED_CAST") assertEquals("quiz", (b.errors().last()["data"] as Map<String, Any?>)["game"])
        assertEquals(0, b.welcomes().size); assertEquals(1, hub.usedTicketCount(), "seule la création a brûlé son ticket")
        b.join(a.code(), b.escrow(k = 2))
        assertEquals("SPECTATOR", b.welcomes().single()["role"]); assertEquals(2, hub.usedTicketCount())
        assertEquals(2, hub.rooms().single().escrowIds().size)
        // la même TV ne mise pas deux fois, et une TV qui arrive la partie commencée n'entre plus
        val b2 = Peer(hub, tvB); b2.hello(); b2.join(a.code(), b2.escrow()); assertEquals("SAME_TV", b2.reason())
        a.phone(a.code(), "Alice"); b.phone(a.code(), "Carl")
        a.send(ClientMsg.Act(null, "start", null, "5", seq++))
        val c = Peer(hub, tvC); c.hello(); c.join(a.code(), c.escrow()); assertEquals("STAKE_ROOM_STARTED", c.reason())
    }

    @Test fun aTrialTvNeverStakesAndItsTicketSurvivesTheRefusal() {
        val hub = hub()
        val t = Peer(hub, tvC, trial = true); t.hello()
        t.send(ClientMsg.Create(null, "DUEL", t.activation, emptyList(), null, stake = spec, escrow = t.escrow()))
        assertEquals("STAKE_TRIAL_FREE_ONLY", t.reason(), "essai = parties libres seulement, même en NDEM")
        assertEquals(0, hub.rooms().size)
        t.send(ClientMsg.Create(null, "DUEL", t.activation, emptyList(), null))
        assertEquals("HOST", t.welcomes().single()["role"], "le même ticket sert ensuite pour une partie libre")
        // entrer dans une salle misée : même refus, avec ou sans blocage
        val a = Peer(hub, tvA); a.create(spec, a.escrow())
        val t2 = Peer(hub, TestRights.Tv("T2"), trial = true); t2.hello()
        t2.join(a.code(), t2.escrow(id = t2.tv.code)); assertEquals("STAKE_TRIAL_FREE_ONLY", t2.reason())
        t2.join(a.code()); assertEquals("STAKE_TRIAL_FREE_ONLY", t2.reason())
    }

    @Test fun everyBadEscrowIsRefusedBeforeAnythingIsConsumed() {
        val hub = hub()
        val now0 = System.currentTimeMillis()
        val cases = linkedMapOf(
            "clé inconnue" to StakeKit.escrow(tvA.code, signer = StakeKit.otherWalletSigner),
            "échu" to StakeKit.escrow(tvA.code, now = now0 - 3_600_000L, lifeMs = 60_000L),
            "autre monnaie" to StakeKit.escrow(tvA.code, cur = "MBOKO", per = 20),
            "autre mise" to StakeKit.escrow(tvA.code, per = 50),
            "neuf sièges" to StakeKit.escrow(tvA.code, k = 9),
            "zéro siège" to StakeKit.escrow(tvA.code, k = 0, amt = 0),
            "mauvaise audience" to StakeKit.escrow(tvA.code, aud = "autre-service"),
            "montant incohérent" to StakeKit.escrow(tvA.code, k = 2, amt = 999),
            "d'une autre TV" to StakeKit.escrow(tvB.code),
            "illisible" to "cbe1.pas-un-blocage.xx",
        )
        val a = Peer(hub, tvA); a.hello()
        for ((why, token) in cases) {
            a.send(ClientMsg.Create(null, "DUEL", a.activation, emptyList(), null, stake = spec, escrow = token))
            assertEquals("STAKE_ESCROW_INVALID", a.reason(), why)
        }
        assertEquals(0, hub.rooms().size); assertEquals(0, hub.usedTicketCount())
        for (k in listOf(1, 2, 8)) {   // de 1 à 8 sièges : accepté
            val x = Peer(hub, TestRights.Tv("K$k")); x.create(spec, x.escrow(k = k))
            assertEquals("HOST", x.welcomes().single()["role"], "k = $k")
        }
    }

    @Test fun anEscrowServesOneRoomAndAReplayNeverFreesTheFirstRoom() {
        val hub = hub()
        val a = Peer(hub, tvA)
        val esc = a.escrow(k = 2)
        a.create(spec, esc)
        assertEquals(1, hub.rooms().size)
        // le MÊME blocage pour une seconde salle : refusé, et le refus ne libère pas la réservation de la première (régression : un second essai réussissait)
        repeat(3) {
            val again = Peer(hub, tvA); again.create(spec, esc)
            assertEquals("STAKE_ESCROW_INVALID", again.reason(), "essai ${it + 1}")
        }
        assertEquals(1, hub.rooms().size)
        // même chose pour les échecs (même porte, même table de réservations)
        val c = Peer(hub, tvC)
        val chessEsc = c.escrow()
        c.hello(); c.send(ClientMsg.Create("TV C", null, c.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), spec, chessEsc))
        assertEquals(1, hub.chessRooms().size)
        repeat(3) {
            val again = Peer(hub, tvC); again.hello(); again.send(ClientMsg.Create("TV C", null, again.activation, emptyList(), null, "chess", ChessOptions(30, "COMPETITION", "white"), spec, chessEsc))
            assertEquals("STAKE_ESCROW_INVALID", again.reason(), "échecs, essai ${it + 1}")
        }
        assertEquals(1, hub.chessRooms().size)
    }

    @Test fun aRefusedEntryGivesTheEscrowBackSoItCanBeUsedElsewhere() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create(spec, a.escrow())
        // une TV déjà dans la salle (l'hôte lui-même) ne peut pas y revenir en invitée : la salle refuse, le blocage n'est PAS consommé
        val self = Peer(hub, tvA); self.hello()
        val esc = self.escrow()
        self.join(a.code(), esc)
        assertEquals("SAME_TV", self.reason())
        val other = Peer(hub, tvA); other.create(spec, esc)   // le même blocage sert pour une autre salle
        assertEquals("HOST", other.welcomes().single()["role"])
        assertEquals(2, hub.rooms().size)
    }

    @Test fun withoutTheKeysStakesAreSuspendedButFreeRoomsStayOpen() {
        val hub = hub(stakes = null)
        assertFalse(hub.stakesOn())
        val a = Peer(hub, tvA); a.hello()
        a.send(ClientMsg.Create(null, "DUEL", a.activation, emptyList(), null, stake = spec, escrow = a.escrow()))
        assertEquals("STAKES_SUSPENDED", a.reason())
        a.send(ClientMsg.Create(null, "DUEL", a.activation, emptyList(), null))
        assertEquals("HOST", a.welcomes().single()["role"])
        // une TV qui entre dans une salle libre avec un blocage : refusé (son argent resterait bloqué pour rien)
        val b = Peer(hub, tvB); b.hello(); b.join(a.code(), b.escrow())
        assertTrue(b.reason() == "STAKES_SUSPENDED" || b.reason() == "STAKE_BAD", "refus : ${b.reason()}")
    }

    @Test fun anEscrowInAFreeRoomIsRefusedBecauseItsMoneyWouldStayBlockedForNothing() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create()
        val b = Peer(hub, tvB); b.hello(); b.join(a.code(), b.escrow())
        assertEquals("STAKE_BAD", b.reason())
        assertEquals(0, b.welcomes().size)
        b.join(a.code())
        assertEquals("SPECTATOR", b.welcomes().single()["role"], "sans blocage, la TV entre dans la salle libre")
    }

    // ------------------------------------------------------------------ interruptions

    @Test fun aServiceStopAbortsEveryStakedRoomAndSpoolsTheAbort() {
        val dir = StakeKit.tempDir()
        val services = StakeKit.services(dir)
        val t = table(services)
        t.start()
        val free = Peer(t.hub, tvC); free.create()
        assertEquals(1, t.hub.stakedRoomCount(), "une salle misée vivante (la salle libre ne compte pas)")
        val n = t.hub.finishGames("SHUTDOWN")
        assertEquals(1, n, "une partie misée interrompue")
        val ra = verified(t.a.results().single()); val rb = verified(t.b.results().single())
        assertEquals(ra, rb); assertEquals(PlayResult.Kind.ABORT, ra.kind); assertTrue(ra.lines.all { it.used == 0L && it.pay == 0L }, "chaque blocage est rendu en entier")
        assertEquals(listOf(ra.rid), services.spool.list())
        assertEquals(0, t.hub.stakedRoomCount())
        assertEquals(1, t.hub.rooms().size, "la salle libre n'est pas touchée par l'interruption")
        assertEquals("OPEN", t.hub.rooms().single().phase().name)
    }

    @Test fun aGuestTvThatLeavesBeforeTheStartDoesNotPlayAndItsEscrowIsGivenBackInTheResult() {
        // « Quitter » avant le départ promet « votre mise sera rendue » : la TV D est partie quand l'hôte démarre ; elle ne joue pas, ses téléphones non plus, son blocage figure au résultat avec « utilisé 0 »
        val services = StakeKit.services()
        val hub = hub(services)
        val a = Peer(hub, tvA); a.create(spec, a.escrow(k = 1))
        val b = Peer(hub, tvB); b.hello(); b.join(a.code(), b.escrow(k = 1))
        val c = Peer(hub, tvC); c.hello(); c.join(a.code(), c.escrow(k = 1))
        val alice = a.phone(a.code(), "Alice")!!; val carl = b.phone(a.code(), "Carl")!!; c.phone(a.code(), "Dora")
        hub.onClosed(c.conn)
        a.send(ClientMsg.Act(null, "mode", null, "DUEL", seq++)); a.send(ClientMsg.Act(null, "start", null, "5", seq++))
        val room = hub.rooms().single()
        assertEquals(ServerRoom.State.PLAYING, room.phase())
        assertEquals(2, room.table(0).room.players().size, "les téléphones de la TV partie n'entrent pas dans le Duel")
        var guard = 0
        while (room.phase() == ServerRoom.State.PLAYING && guard++ < 10_000) {
            val d = room.table(0).room.duel!!
            if (d.phase == QuizDuel.Phase.QUESTION && now >= room.table(0).opensAtServerMs) {
                val q = d.question
                now += 100; a.send(ClientMsg.RelayAct(alice, q.id, q.answer, 900L, seq++))
                now += 100; b.send(ClientMsg.RelayAct(carl, q.id, (q.answer + 1) % 4, 900L, seq++))
            }
            now += 500; hub.tick()
            if (d.phase == QuizDuel.Phase.BOARD || d.phase == QuizDuel.Phase.REVEAL) a.send(ClientMsg.Act(null, "skip", null, null, seq++))
        }
        val r = verified(a.results().single())
        assertEquals(listOf(tvA.code, tvB.code, tvC.code), r.lines.map { it.id })
        assertEquals(listOf(20L, 20L, 0L), r.lines.map { it.used }, "la TV partie n'a rien engagé : l'API lui rend tout")
        assertEquals(listOf(40L, 0L, 0L), r.lines.map { it.pay }, "Alice seule a marqué : toute la cagnotte de deux mises")
        assertTrue(c.results().isEmpty(), "la TV partie n'est plus connectée : son résultat l'attend dans le dépôt du collecteur")
        assertEquals(listOf(r.rid), services.spool.list())
    }

    @Test fun theHostCancelsAnEmptyStakedRoomAndTheStakeIsGivenBack() {
        val services = StakeKit.services()
        val hub = hub(services)
        val a = Peer(hub, tvA); a.create(spec, a.escrow(k = 2))
        a.send(ClientMsg.GameAct("cancel", null, null, 5))
        val r = verified(a.results().single())
        assertEquals(PlayResult.Kind.ABORT, r.kind); assertEquals(listOf(tvA.code), r.lines.map { it.id })
        assertEquals(listOf(r.rid), services.spool.list())
    }

    @Test fun aGameThatNeedsTwoTvsRefusesToStartWithOne() {
        val hub = hub()
        val a = Peer(hub, tvA); a.create(spec, a.escrow(k = 2))
        a.phone(a.code(), "Alice"); a.phone(a.code(), "Ann")
        a.send(ClientMsg.Act(null, "start", null, "5", seq++))
        assertEquals("BAD_REQUEST", a.reason())
        assertTrue((a.errors().last()["message"] as String).contains("deux TV"))
        assertEquals(ServerRoom.State.OPEN, hub.rooms().single().phase())
    }

    @Test fun aTvWithoutAnEscrowCannotSeatPhonesInAStakedRoom() {
        val t = table()
        // la TV C n'a pas pu entrer (pas de blocage) : elle n'a donc aucun droit de relayer ; ses téléphones n'ont pas de salle
        val c = Peer(t.hub, tvC); c.hello(); c.join(t.a.code())
        assertEquals("STAKE_ESCROW_REQUIRED", c.reason())
        c.send(ClientMsg.Join(t.a.code(), "Intrus", null, dev(), false))
        assertEquals(0, c.welcomes().size, "aucun siège sans blocage")
    }

    // ------------------------------------------------------------------ capacités et santé

    @Test fun capsAndHealthSayWhetherTheQuizCanBeStaked() {
        fun controller(h: PlayHub) = castbridge.play.HealthController(cfg(), h, castbridge.play.ConnectionLimits(100, 100))
        val on = Json.parse(controller(hub()).caps()) as Map<*, *>
        assertEquals(true, on["quizStakes"]); assertTrue((on["caps"] as List<*>).contains("quizStakes"))
        val off = Json.parse(controller(hub(stakes = null)).caps()) as Map<*, *>
        assertEquals(false, off["quizStakes"]); assertFalse((off["caps"] as List<*>).contains("quizStakes"), "un service sans clés n'annonce pas le Quiz misé : la TV ne propose alors que « Libre »")
        val busy = hub()
        val a = Peer(busy, tvA); a.create(spec, a.escrow())
        val health = Json.parse(controller(busy).health()) as Map<*, *>
        assertEquals(true, health["quizStakes"]); assertEquals(1, (health["stakedRooms"] as Number).toInt(), "les parties misées vivantes : à vider avant un déploiement")
    }
}
