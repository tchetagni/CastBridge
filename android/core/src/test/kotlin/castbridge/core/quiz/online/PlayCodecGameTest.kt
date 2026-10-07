package castbridge.core.quiz.online

import castbridge.core.quiz.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les messages ADDITIFS des salles de jeu à tour de rôle (échecs en ligne) : aller-retour, bornes, et jamais un secret dans une trace. Le Quiz n'est pas touché. */
class PlayCodecGameTest {
    private fun ok(s: String) = (PlayCodec.decodeClient(s) as PlayCodec.Decoded.Ok).msg
    private fun bad(s: String) = PlayCodec.decodeClient(s) as PlayCodec.Decoded.Bad

    private val escrow = "cbe1." + "A".repeat(300) + "." + "B".repeat(86)

    private val samples: List<ClientMsg> = listOf(
        ClientMsg.Create("TV Salon", null, "cbx1.act", emptyList(), "pub.sig", "chess", ChessOptions(30, "COMPETITION", "random"), null, null),
        ClientMsg.Create("TV Salon", null, "cbx1.act", emptyList(), "pub.sig", "chess", ChessOptions(45, "COMPETITION", "white"), StakeSpec("NDEM", 20), escrow),
        ClientMsg.Create(null, "DUEL"),
        ClientMsg.Join("K7M2QX4T", "TV Chambre", null, null, false, "cbx1.act", "pub.sig", escrow),
        ClientMsg.Join("K7M2QX4T", null, null, null, true, "cbx1.act", "pub.sig", null),
        ClientMsg.GameAct("move", "e2e4", 0, 7), ClientMsg.GameAct("move", "e7e8q", 99, 8), ClientMsg.GameAct("resign", null, null, 9), ClientMsg.GameAct("draw", "offer", null, 10), ClientMsg.GameAct("cancel", null, null, 11),
    )

    @Test fun everyNewClientMessageRoundTrips() {
        for (m in samples) assertEquals(m, ok(PlayCodec.encode(m)), m.toString())
    }

    @Test fun theQuizCreateIsUnchangedOnTheWire() {
        // une création de Quiz n'écrit AUCUN des champs neufs : un service ancien lit exactement la même chose qu'avant
        val json = Json.obj(PlayCodec.encode(ClientMsg.Create("Salon", "DUEL", "cbx1.a", emptyList(), "p.s")))
        assertEquals(setOf("t", "name", "mode", "activation", "rentals", "proof"), json.keys)
        val join = Json.obj(PlayCodec.encode(ClientMsg.Join("K7M2QX4T", "Awa", null, "dev", false)))
        assertEquals(setOf("t", "code", "name", "token", "deviceHash", "spectate"), join.keys)
    }

    @Test fun gameFieldsAreOptionalAndStrict() {
        assertNull((ok("""{"t":"create","name":"TV"}""") as ClientMsg.Create).game)
        assertEquals("chess", (ok("""{"t":"create","game":"chess"}""") as ClientMsg.Create).game)
        // un jeu inconnu du service n'est PAS un message mal formé (le service répond GAME_UNKNOWN) ; un nom invalide l'est
        assertEquals("cards", (ok("""{"t":"create","game":"cards"}""") as ClientMsg.Create).game)
        for (s in listOf("""{"t":"create","game":5}""", """{"t":"create","game":"Chess!"}""", """{"t":"create","game":""}""", """{"t":"create","game":"${"x".repeat(17)}"}""",
            """{"t":"create","game":"chess","chess":"vite"}""", """{"t":"create","game":"chess","chess":{"perMoveSeconds":0,"mode":"COMPETITION","color":"white"}}""",
            """{"t":"create","game":"chess","chess":{"perMoveSeconds":30,"mode":"COMPETITION"}}""",
            """{"t":"create","game":"chess","stake":{"cur":"EUR","per":20}}""", """{"t":"create","game":"chess","stake":{"cur":"NDEM","per":0}}""",
            """{"t":"create","game":"chess","stake":{"cur":"NDEM","per":-5}}""", """{"t":"create","game":"chess","stake":{"cur":"NDEM","per":"20"}}""", """{"t":"create","game":"chess","stake":{"cur":"NDEM"}}""",
            """{"t":"create","game":"chess","stake":{"cur":"NDEM","per":2000000000}}""", """{"t":"create","game":"chess","stake":[1]}""",
            """{"t":"create","game":"chess","escrow":5}""", """{"t":"create","game":"chess","escrow":"cbe1 avec espace"}""", """{"t":"create","game":"chess","escrow":"${"x".repeat(PlayProtocol.MAX_ESCROW + 1)}"}""",
            """{"t":"join","code":"K7M2QX4T","escrow":"é"}"""))
            assertEquals(PlayProtocol.BAD_REQUEST, bad(s).reason, s)
        assertNull((ok("""{"t":"create","game":"chess","escrow":""}""") as ClientMsg.Create).escrow)
    }

    @Test fun gameActsAreBounded() {
        assertEquals(ClientMsg.GameAct("move", "e2e4", 3, 5), ok("""{"t":"game","op":"move","arg":"e2e4","ply":3,"seq":5}"""))
        assertEquals(ClientMsg.GameAct("resign", null, null, 0), ok("""{"t":"game","op":"resign"}"""))
        for (s in listOf("""{"t":"game"}""", """{"t":"game","op":""}""", """{"t":"game","op":"${"x".repeat(17)}"}""", """{"t":"game","op":"move","arg":"${"x".repeat(65)}"}""",
            """{"t":"game","op":"move","ply":-1}""", """{"t":"game","op":"move","ply":100001}""", """{"t":"game","op":"move","ply":"3"}""", """{"t":"game","op":"move","seq":-1}"""))
            assertEquals(PlayProtocol.BAD_REQUEST, bad(s).reason, s)
    }

    @Test fun anEscrowOfTheMaximumSizeStillFitsInAJoin() {
        val max = "c" + "x".repeat(PlayProtocol.MAX_ESCROW - 1)
        val text = PlayCodec.encode(ClientMsg.Join("K7M2QX4T", "TV", null, null, false, null, null, max))
        assertTrue(text.length < PlayProtocol.MAX_MESSAGE_BYTES, "un join misé sans activation tient dans 2 Ko (${text.length})")
        assertEquals(max, (ok(text) as ClientMsg.Join).escrow)
    }

    @Test fun newServerMessagesRoundTrip() {
        val samples: List<ServerMsg> = listOf(
            ServerMsg.Welcome(3, "r1", "K7M2QX4T", "tok", PlayRole.HOST, "p1", 1, PlayProtocol.CAPS, "chess"), ServerMsg.Welcome(3, "r1", "K7M2QX4T", "tok", PlayRole.PLAYER, "p1", 1, emptyList()),
            ServerMsg.Error(10, "STAKE_ESCROW_REQUIRED", "Mise", true, 0L, linkedMapOf("game" to "chess", "cur" to "NDEM", "per" to 20L)), ServerMsg.Error(10, "PLAY_ROOM_FULL", "Salle", true),
            ServerMsg.Result(11, "cbr1.charge.signature"),
        )
        for (m in samples) assertEquals(m, PlayCodec.decodeServer(PlayCodec.encode(m)), m.type)
        // additif : aucun champ neuf quand il n'y a rien à dire
        assertFalse(PlayCodec.encode(samples[1]).contains("game")); assertFalse(PlayCodec.encode(samples[3]).contains("data"))
        assertNull(PlayCodec.decodeServer("""{"t":"result","seq":1}"""), "un résultat sans jeton est ignoré")
        assertNull(PlayCodec.decodeServer("""{"t":"result","seq":1,"token":""}"""))
    }

    @Test fun noSecretsInAnyTrace() {
        val create = ClientMsg.Create("TV", null, "cbx1.SECRETACT", emptyList(), "pub.SECRETSIG", "chess", ChessOptions(30, "COMPETITION", "white"), StakeSpec("NDEM", 20), "cbe1.SECRETESCROW.sig")
        for (s in listOf(create.toString(), ClientMsg.Join("K7M2QX4T", "TV", null, null, false, "cbx1.SECRETACT", null, "cbe1.SECRETESCROW.sig").toString(),
            ServerMsg.Result(1, "cbr1.SECRETRESULT.sig").toString(), PlayRedact.scrub("posté cbe1.SECRETESCROW.sig puis cbr1.SECRETRESULT.sig et cbw1.SECRETSNAP.sig")))
            assertFalse(s.contains("SECRET"), s)
    }

    @Test fun theCapabilityIsAdvertisedAndTheListStaysWithinTheCodecLimits() {
        assertTrue("chess" in PlayProtocol.CAPS && PlayProtocol.CAPS.size <= PlayProtocol.MAX_CAPS && PlayProtocol.CAPS.all { it.length <= 24 })
        assertEquals(PlayProtocol.CAPS, (ok(PlayCodec.encode(ClientMsg.Hello(1, PlayProtocol.CAPS, null, null))) as ClientMsg.Hello).caps)
        assertEquals(setOf("chess"), PlayProtocol.GAMES)
    }

    @Test fun gameReasonsAreStableAndSpeakFrench() {
        assertEquals(listOf("GAME_UNAVAILABLE", "GAME_UNKNOWN", "STAKES_SUSPENDED", "STAKE_TRIAL_FREE_ONLY", "STAKE_ESCROW_REQUIRED", "STAKE_ESCROW_INVALID", "STAKE_BAD", "SEATS_TAKEN", "SAME_TV"),
            GameReason.values().map { it.code })
        GameReason.values().forEach { assertTrue(it.message.length > 20 && it.http in 400..599, it.code) }
        // aucun code n'est partagé avec PlayReason (le Quiz garde les siens)
        assertTrue(GameReason.values().none { PlayReason.of(it.code) != null })
        // l'écran de la TV dit le motif en français, jamais un code brut
        assertEquals(GameReason.STAKE_TRIAL_FREE_ONLY.message, PlayErrors.text("STAKE_TRIAL_FREE_ONLY"))
        assertEquals(PlayReason.PLAY_BAD_CODE.message, PlayErrors.text("PLAY_BAD_CODE"))
    }
}
