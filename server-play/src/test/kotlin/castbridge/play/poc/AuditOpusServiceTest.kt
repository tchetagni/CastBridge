package castbridge.play.poc

import castbridge.core.owner.ActivationKind
import castbridge.core.owner.InstallSigner
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProof
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.PlayRules
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.HubFixture
import castbridge.play.PlayHub
import castbridge.play.TestConn
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.dev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audit Opus du Quiz en ligne, côté SERVICE (chemin d'entrée des TV) : C-1 (câblage réel de « Rejoindre »), M-2, M-3, H-3 (preuve de possession), mutations B, C, H, taille des messages M-1.
 * Chaque test est d'abord écrit contre le code audité (ROUGE par assertion) ; voir docs/agent-reports/sonnet-w20-05.md, « Correctifs de l'audit ».
 */
class AuditOpusServiceTest {
    private fun tvHub(vararg more: Pair<String, Any>): PlayHub = HubFixture.hub(HubFixture.config("webPlay" to false, *more))
    private fun welcomes(c: TestConn) = synchronized(c.out) { c.out.map { Json.parse(it) as Map<*, *> }.filter { it["t"] == "welcome" } }
    private fun ticketOf(tv: TestRights.Tv, ik: String? = null) = TestKeys.ticket(deviceCode = tv.code, ik = ik)
    private fun prod(tv: TestRights.Tv) = TestRights.activation(device = tv)
    private fun trial(tv: TestRights.Tv) = TestRights.activation(ActivationKind.TRIAL, device = tv, rights = TestRights.trialUsage())

    private fun host(hub: PlayHub, tv: TestRights.Tv = TestRights.tv, activation: String = prod(tv), name: String? = null, mode: String = "DUEL"): Pair<TestConn, String> {
        val c = HubFixture.open(hub, ticketOf(tv), TestRights.create(activation, name = name, mode = mode))
        return c to (welcomes(c).single()["code"] as String)
    }

    private fun join(hub: PlayHub, code: String, ticket: String?, activation: String?, name: String? = "TV Chambre", device: String = dev(), ip: String = "198.51.100.9", spectate: Boolean = true, proof: String? = null): TestConn {
        val c = TestConn(ip); hub.register(c)
        if (ticket != null) hub.onText(c, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket)))
        hub.onText(c, PlayCodec.encode(ClientMsg.Join(code, name, null, device, spectate, activation, proof)))
        return c
    }

    // ---------------------------------------------------------------- C-1

    @Test fun theRealWiringOfJoinEntersWithoutAName() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val b = join(hub, code, ticketOf(TestRights.otherTv), prod(TestRights.otherTv), name = null)   // exactement ce que PlayHub envoie : Join(code, null) + spectate
        assertTrue(b.welcomed(), "« Rejoindre » d'une vraie TV doit entrer : ${b.errors()}")
        assertEquals("SPECTATOR", welcomes(b).single()["role"])
    }

    @Test fun aJoinRefusedByTheRoomBurnsNeitherTheTicketNorATrialGame() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val t = TestRights.Tv("T-refus")
        val before = hub.usedTicketCount()
        repeat(5) { i ->
            val refused = join(hub, code, ticketOf(t), trial(t), name = null, spectate = false)   // un joueur sans nom : la salle refuse
            assertFalse(refused.welcomed(), "essai $i")
            assertEquals(PlayProtocol.BAD_REQUEST, refused.errorReason())
        }
        assertEquals(before, hub.usedTicketCount(), "un refus de la salle ne brûle aucun ticket")
        // le compteur d'essai n'a rien compté : les 3 parties du jour sont encore là
        repeat(3) { i ->
            val ok = join(hub, code, ticketOf(t), trial(t), name = "TV essai")
            assertTrue(ok.welcomed(), "entrée ${i + 1} sur 3 : ${ok.errors()}")
            hub.onClosed(ok)
        }
        val fourth = join(hub, code, ticketOf(t), trial(t), name = "TV essai")
        assertEquals(PlayRules.MSG_TRIAL_DAILY, fourth.errorMessage(), "la 4e est refusée : exactement 3 parties comptées")
    }

    @Test fun aRefusedJoinLeavesTheSameTicketUsable() {
        val hub = tvHub()
        val (_, code) = host(hub)
        val tvB = TestRights.otherTv
        val ticket = ticketOf(tvB)
        val refused = join(hub, code, ticket, prod(tvB), name = null, spectate = false)
        assertFalse(refused.welcomed())
        val again = join(hub, code, ticket, prod(tvB), name = "TV B")
        assertTrue(again.welcomed(), "le ticket n'a pas été brûlé par le refus de la salle : ${again.errors()}")
    }

    // ---------------------------------------------------------------- M-2

    @Test fun aTrialTvIsNotBlockedAfterQuitWhileItsRoomLives() {
        val hub = tvHub()
        val tv = TestRights.Tv("T-quitte")
        val (hostConn, code) = host(hub, tv, trial(tv))
        val guest = join(hub, code, ticketOf(TestRights.otherTv), prod(TestRights.otherTv))   // l'invitée garde la salle vivante
        assertTrue(guest.welcomed())
        hub.onClosed(hostConn)                                   // « Quitter » : le transport de l'hôte se ferme, la salle vit
        val again = HubFixture.open(hub, ticketOf(tv), TestRights.create(trial(tv)))
        assertTrue(again.welcomed(), "l'hôte d'essai qui a quitté peut rouvrir : ${again.errors()} ${again.errorMessage()}")
    }

    // ---------------------------------------------------------------- M-3

    @Test fun aTrialRoomCannotRestartMoreOftenThanTheDailyGames() {
        val hub = tvHub()
        val tv = TestRights.Tv("T-redemarre")
        val (hostConn, code) = host(hub, tv, trial(tv))
        val guest = join(hub, code, ticketOf(TestRights.otherTv), prod(TestRights.otherTv))
        hub.onText(guest, PlayCodec.encode(ClientMsg.Join(code, "Carl", null, dev(), false)))   // un joueur local relayé
        fun act(a: String, arg: String? = null, seq: Long) = hub.onText(hostConn, PlayCodec.encode(ClientMsg.Act(null, a, null, arg, seq)))
        act("mode", "DUEL", 1)
        val room = hub.rooms().single()
        var started = 0
        repeat(5) { i ->
            act("start", "5", 10L + i)
            if (room.phase() == ServerRoom.State.PLAYING) { started++; act("end", null, 100L + i); act("lobby", null, 200L + i) }   // « Nouvelle partie » = retour au salon puis départ
        }
        assertEquals(3, started, "essai : la création + 2 « Nouvelle partie » = 3 parties du jour, pas plus (${hostConn.errors()})")
        assertEquals(PlayRules.MSG_TRIAL_DAILY, hostConn.errorMessage())
    }

    // ---------------------------------------------------------------- mutations B, C, H

    @Test fun mutationH_aTrialTvWatchesItsOwnRoomWithOneConnectionCap() {
        val hub = tvHub()
        val tv = TestRights.Tv("T-propre")
        val (_, code) = host(hub, tv, trial(tv))
        val own = join(hub, code, ticketOf(tv), trial(tv), name = "TV propre")
        assertTrue(own.welcomed(), "rejoindre SA salle ne compte pas deux fois : ${own.errors()}")
    }

    @Test fun mutationC_aResumeKeepsTheIdentityInTheLiveConnectionCap() {
        val hub = tvHub("perSubject" to 1)
        val tvA = TestRights.tv; val tvB = TestRights.otherTv
        val (_, codeA) = host(hub, tvA)
        val (_, codeC) = host(hub, TestRights.Tv("T-autre"))
        val b1 = join(hub, codeA, ticketOf(tvB), prod(tvB))
        assertTrue(b1.welcomed())
        val token = welcomes(b1).single()["token"] as String
        val roomId = welcomes(b1).single()["roomId"] as String
        hub.onClosed(b1)                                                  // coupure
        val b2 = TestConn("198.51.100.9"); hub.register(b2)
        hub.onText(b2, PlayCodec.encode(ClientMsg.Resume(roomId, token, 0)))
        assertTrue(b2.welcomed(), "la reprise rend le siège")
        val second = join(hub, codeC, ticketOf(tvB), prod(tvB))
        assertFalse(second.welcomed(), "après une reprise, la TV compte toujours dans ses connexions vivantes : une activation copiée n'entre pas ailleurs")
        assertEquals("PLAY_BUSY", second.errorReason())
    }

    @Test fun mutationB_twoConcurrentJoinsOfATrialTvCountAtMostThreeGames() {
        val hub = tvHub()
        val tv = TestRights.Tv("T-course")
        val (_, code) = host(hub, tv, trial(tv))                  // partie 1
        assertTrue(join(hub, code, ticketOf(tv), trial(tv)).welcomed())   // partie 2 (sa propre salle : spectateur)
        // entre le contrôle bon marché et le verrou, une autre entrée de la même identité prend la 3e partie : seul le recontrôle sous verrou l'arrête
        val results = ArrayList<TestConn>()
        hub.afterJoinPrecheck = { hub.afterJoinPrecheck = null; results += join(hub, code, ticketOf(tv), trial(tv)) }
        val first = join(hub, code, ticketOf(tv), trial(tv))
        val all = results + first
        assertEquals(1, all.count { it.welcomed() }, "une seule des deux entrées simultanées prend la 3e partie : ${all.map { it.errorMessage() }}")
    }

    // ---------------------------------------------------------------- H-3 : preuve de possession de la clé d'installation de la TV

    private val signer = InstallSigner.create()
    private fun ik(s: InstallSigner = signer) = PlayProof.installHash(s.publicKeyBase64)!!
    private fun proofOf(ticket: String, activation: String, s: InstallSigner = signer) = PlayProof.build(s::sign, s.publicKeyBase64, ticket, activation)

    private fun strictHub() = tvHub("requireProof" to true)

    private fun create(hub: PlayHub, ticket: String?, activation: String, proof: String?): TestConn {
        val c = TestConn(); hub.register(c)
        if (ticket != null) hub.onText(c, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket)))
        hub.onText(c, PlayCodec.encode(ClientMsg.Create(null, "DUEL", activation, emptyList(), proof)))
        return c
    }

    @Test fun aCreateWithTheTicketPinnedKeyProofEnters() {
        val hub = strictHub()
        val t = ticketOf(TestRights.tv, ik()); val a = prod(TestRights.tv)
        assertTrue(create(hub, t, a, proofOf(t, a)).welcomed())
    }

    @Test fun aCopiedActivationWithoutTheInstallKeyIsRefused() {
        val hub = strictHub()
        val a = prod(TestRights.tv)
        val noProof = ticketOf(TestRights.tv, ik())
        val c1 = create(hub, noProof, a, null)
        assertFalse(c1.welcomed(), "une activation copiée sans preuve de possession n'ouvre pas de salle")
        assertEquals("PLAY_SCOPE_FORBIDDEN", c1.errorReason())
        val attacker = InstallSigner.create()
        val t2 = ticketOf(TestRights.tv, ik())
        assertFalse(create(hub, t2, a, proofOf(t2, a, attacker)).welcomed(), "preuve d'une autre clé que celle épinglée dans le ticket")
        val t3 = ticketOf(TestRights.tv, ik()); val other = ticketOf(TestRights.tv, ik())
        assertFalse(create(hub, t3, a, proofOf(other, a)).welcomed(), "preuve faite pour un autre ticket : rejouée, elle ne vaut rien")
        val t4 = ticketOf(TestRights.tv, ik())
        assertFalse(create(hub, t4, a, proofOf(t4, prod(TestRights.tv))).welcomed(), "preuve faite pour une autre activation")
    }

    @Test fun aTicketWithoutAPinnedKeyIsRefusedWhenProofIsRequiredAndAcceptedOtherwise() {
        val a = prod(TestRights.tv)
        val legacy = ticketOf(TestRights.tv)   // ticket d'une API qui ne connaît pas la clé d'installation
        val strict = create(strictHub(), legacy, a, null)
        assertFalse(strict.welcomed()); assertEquals("PLAY_SCOPE_FORBIDDEN", strict.errorReason())
        assertTrue(create(tvHub("requireProof" to false), ticketOf(TestRights.tv), a, null).welcomed(), "migration d'une flotte mixte : réglage explicite, jamais le défaut")
    }

    @Test fun aJoinNeedsTheSameProof() {
        val hub = strictHub()
        val tvA = TestRights.tv; val tvB = TestRights.otherTv
        val tA = ticketOf(tvA, ik()); val aA = prod(tvA)
        val h = create(hub, tA, aA, proofOf(tA, aA)); val code = welcomes(h).single()["code"] as String
        val aB = prod(tvB)
        val tNo = ticketOf(tvB, ik())
        val refused = join(hub, code, tNo, aB, name = null)
        assertFalse(refused.welcomed()); assertEquals("PLAY_SCOPE_FORBIDDEN", refused.errorReason())
        val tOk = ticketOf(tvB, ik())
        assertTrue(join(hub, code, tOk, aB, name = null, proof = proofOf(tOk, aB)).welcomed())
    }

    // ---------------------------------------------------------------- M-1 : taille des messages create / join

    private fun bigActivation(n: Int) = "cbx1." + "A".repeat(n)

    @Test fun createAndJoinCarryingALargeActivationReachTheEvaluation() {
        val hub = tvHub()
        val c = TestConn(); hub.register(c)
        hub.onText(c, PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticketOf(TestRights.tv))))
        hub.onText(c, PlayCodec.encode(ClientMsg.Create(null, "DUEL", bigActivation(3_000))))
        assertNotEquals(PlayProtocol.BAD_REQUEST, c.errorReason(), "une activation de ≈ 3 000 caractères (école : 20 achats) n'est pas « trop longue » : elle est évaluée")
        assertEquals("PLAY_SCOPE_FORBIDDEN", c.errorReason(), "signature fausse, donc refusée par l'évaluation, pas par la taille")
        val (_, code) = host(hub)
        val j = join(hub, code, ticketOf(TestRights.otherTv), bigActivation(3_000), name = null)
        assertEquals("PLAY_SCOPE_FORBIDDEN", j.errorReason(), "idem pour join")
    }

    @Test fun otherMessagesKeepTheTwoKilobyteCapAndHugeOnesAreRefused() {
        val hub = tvHub()
        val (hostConn, _) = host(hub)
        val kick = """{"t":"kick","playerId":"${"x".repeat(3_000)}"}"""
        hub.onText(hostConn, kick)
        assertEquals(PlayProtocol.BAD_REQUEST, hostConn.errorReason())
        assertEquals("message trop long", hostConn.lastError()!!["message"])
        val c = TestConn(); hub.register(c)
        hub.onText(c, PlayCodec.encode(ClientMsg.Create(null, "DUEL", bigActivation(9_000))))
        assertEquals("message trop long", c.lastError()!!["message"], "au-delà de 8 192 caractères, même un create est refusé")
        assertNull(c.lastError()!!["retryAfterMs"])
    }
}
