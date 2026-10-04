package castbridge.core.quiz.online

import castbridge.core.quiz.Json
import castbridge.core.ux.SignalLevel
import kotlin.test.*

class PlayCodecTest {
    private fun ok(s: String) = (PlayCodec.decodeClient(s) as PlayCodec.Decoded.Ok).msg
    private fun bad(s: String) = PlayCodec.decodeClient(s) as PlayCodec.Decoded.Bad

    private val clientSamples: List<ClientMsg> = listOf(
        ClientMsg.Hello(1, PlayProtocol.CAPS, "dev-0123456789", "cbp1.ticket"), ClientMsg.Create("Salon", "DUEL"), ClientMsg.Create(null, null),
        ClientMsg.Join("K7M2QX4T", "Awa", null, "dev-0123456789", false), ClientMsg.Join("K7M2QX4T", "Awa", "tok", null, true),
        ClientMsg.Resume("room-1", "tok", 42), ClientMsg.Act("q1", "answer", 2, null, 7), ClientMsg.Act(null, "start", null, "11", 1),
        ClientMsg.RelayAct("tok", "q1", 3, 9_900, 8), ClientMsg.Scope(true), ClientMsg.Scope(false), ClientMsg.Kick("p2"), ClientMsg.Mute("p2", true),
        ClientMsg.Report("q1", "WRONG_ANSWER"), ClientMsg.Pong("p9"),
    )

    @Test fun everyClientMessageRoundTrips() {
        for (m in clientSamples) assertEquals(m, ok(PlayCodec.encode(m)), m.type)
    }

    @Test fun everyClientMessageCarriesItsTypeAsT() {
        for (m in clientSamples) assertEquals(m.type, Json.obj(PlayCodec.encode(m))["t"])
    }

    @Test fun unknownTypeIsUnsupported() {
        assertEquals(PlayProtocol.UNSUPPORTED, bad("""{"t":"teleport"}""").reason)
    }

    @Test fun malformedIsBadRequest() {
        for (s in listOf("not json", "[]", "{}", """{"t":5}""", """{"t":"join"}""", """{"t":"join","code":5}""", """{"t":"act","action":"answer","choice":4}""",
            """{"t":"act","action":"launch"}""", """{"t":"act","action":"answer","choice":"1"}""", """{"t":"relayAct","token":"a","questionId":"q","choice":1,"localElapsedMono":-1}""",
            """{"t":"relayAct","token":"a","questionId":"q","choice":1,"localElapsedMono":60001}""", """{"t":"hello","proto":0}""", """{"t":"hello","proto":1,"caps":"x"}""",
            """{"t":"resume","roomId":"r","token":"t","lastSeq":-1}""", """{"t":"scope"}""", """{"t":"pong","id":""}""", """{"t":"join","code":"AAAAAAAA","name":"${"x".repeat(65)}"}""",
            "{\"t\":\"pong\",\"id\":\"a\\u0001b\"}"))
            assertEquals(PlayProtocol.BAD_REQUEST, bad(s).reason, s)
    }

    @Test fun tooManyCapsAndOversizeAreRejected() {
        val caps = (1..13).joinToString(",") { "\"c$it\"" }
        assertEquals(PlayProtocol.BAD_REQUEST, bad("""{"t":"hello","proto":1,"caps":[$caps]}""").reason)
        val big = """{"t":"report","reason":"x","questionId":"${"q".repeat(PlayProtocol.MAX_MESSAGE_BYTES)}"}"""
        assertEquals(PlayProtocol.BAD_REQUEST, bad(big).reason)
        assertTrue(PlayCodec.decodeClient("""{"t":"pong","id":"${"a".repeat(2_050)}"}""") is PlayCodec.Decoded.Bad)
    }

    @Test fun exactlyTheLimitIsAccepted() {
        val pad = PlayProtocol.MAX_MESSAGE_BYTES - """{"t":"pong","id":""}""".length
        assertTrue(PlayCodec.decodeClient("""{"t":"pong","id":"${"a".repeat(PlayProtocol.MAX_ID)}"}""") is PlayCodec.Decoded.Ok)
        assertTrue(pad > PlayProtocol.MAX_ID, "les champs bornés tiennent largement dans 2 Ko")
    }

    @Test fun unknownKeysAreIgnoredAdditiveRule() {
        assertEquals(ClientMsg.Pong("p1"), ok("""{"t":"pong","id":"p1","futureField":{"a":1},"seq":3}"""))
        assertEquals(ClientMsg.Join("K7M2QX4T", null, null, null, false), ok("""{"t":"join","code":"K7M2QX4T","newThing":[1,2]}"""))
    }

    @Test fun serverMessagesRoundTripThroughDecodeServer() {
        val safety = SafetyView(PlayScope.INTERNET, SignalLevel.GREEN, "Partie sûre", "Internet · partie sûre", null, listOf("a", "b"))
        val samples: List<ServerMsg> = listOf(
            ServerMsg.Welcome(3, "r1", "K7M2QX4T", "tok", PlayRole.HOST, null, 1, PlayProtocol.CAPS), ServerMsg.Welcome(4, "r1", "K7M2QX4T", "tok", PlayRole.PLAYER, "p1", 1, emptyList()),
            ServerMsg.State(5, linkedMapOf("v" to 9L, "stage" to "PLAYING"), true), ServerMsg.Question(6, "q1", 0, 10, "Capitale ?", listOf("A", "B", "C", "D"), 5_500, 4_000, 20_000),
            ServerMsg.Reveal(7, "q1", 0, 2, "Parce que."), ServerMsg.Reveal(7, "q1", 0, 2, null), ServerMsg.Safety(8, safety), ServerMsg.Ping(9, "p1", 123),
            ServerMsg.Error(10, "PLAY_ROOM_FULL", "Salle complète", true), ServerMsg.RoomGone(11, "EXPIRED"),
            ServerMsg.Replay(12, listOf(EventRing.Event(10, "joined", mapOf("playerId" to "p2")), EventRing.Event(11, "state"))), ServerMsg.Ack(13, 7, "TOO_EARLY"),
        )
        for (m in samples) {
            val back = PlayCodec.decodeServer(PlayCodec.encode(m))
            assertEquals(m.type, back?.type); assertEquals(m.seq, back?.seq)
            if (m !is ServerMsg.State && m !is ServerMsg.Replay) assertEquals(m, back, m.type)
        }
        val st = PlayCodec.decodeServer(PlayCodec.encode(samples[2])) as ServerMsg.State
        assertEquals("PLAYING", st.view["stage"]); assertTrue(st.full)
        val rp = PlayCodec.decodeServer(PlayCodec.encode(samples[10])) as ServerMsg.Replay
        assertEquals(listOf(10L, 11L), rp.events.map { it.seq }); assertEquals("p2", rp.events[0].data["playerId"])
    }

    @Test fun unknownOrBrokenServerMessagesAreIgnoredByTheClient() {
        assertNull(PlayCodec.decodeServer("""{"t":"newKind","seq":1}""")); assertNull(PlayCodec.decodeServer("garbage")); assertNull(PlayCodec.decodeServer("""{"t":"question","seq":1}"""))
    }

    @Test fun measuredMaximumSizesFitAndAreWrittenForTheReport() {
        val long = "x".repeat(PlayProtocol.MAX_ID)
        val maxClient: List<ClientMsg> = listOf(
            ClientMsg.Hello(999, List(PlayProtocol.MAX_CAPS) { "c".repeat(24) }, long, "t".repeat(PlayProtocol.MAX_TICKET)), ClientMsg.Create("n".repeat(16), "MILLIONAIRE"),
            ClientMsg.Join("K7M2QX4T", "n".repeat(16), long, long, true), ClientMsg.Resume(long, long, 1L shl 53), ClientMsg.Act(long, "audience", 3, "a".repeat(PlayProtocol.MAX_ARG), 1L shl 53),
            ClientMsg.RelayAct(long, long, 3, PlayProtocol.MAX_ELAPSED_MS, 1L shl 53), ClientMsg.Scope(false), ClientMsg.Kick(long), ClientMsg.Mute(long, true),
            ClientMsg.Report(long, "r".repeat(PlayProtocol.MAX_REASON)), ClientMsg.Pong(long),
        )
        val lines = ArrayList<String>()
        for (m in maxClient) {
            val n = PlayCodec.encode(m).toByteArray().size
            assertTrue(n <= PlayProtocol.MAX_MESSAGE_BYTES, "${m.type} : $n octets")
            assertTrue(PlayCodec.decodeClient(PlayCodec.encode(m)) is PlayCodec.Decoded.Ok, "${m.type} au maximum doit passer le décodage strict")
            lines += "client ${m.type} $n"
        }
        java.io.File("build").mkdirs()
        java.io.File("build/play-message-sizes.txt").writeText(lines.joinToString("\n") + "\n")
    }

    // ---- w20-04b : `join.activation` (additif) ----

    @Test fun joinCarriesAnOptionalActivationAndStaysIdenticalWithoutIt() {
        val act = "cbx1." + "A".repeat(3_000)
        val withAct = ClientMsg.Join("K7M2QX4T", "TV Chambre", null, "dev-0123456789", true, act)
        val wire = PlayCodec.encode(withAct)
        assertTrue(wire.length > PlayProtocol.MAX_MESSAGE_BYTES, "un join de TV dépasse 2 048 octets")
        assertEquals(PlayCodec.Decoded.Ok(withAct), PlayCodec.decodeClient(wire), "un join qui porte `activation` passe le décodage (jusqu'à MAX_CREATE_BYTES)")
        // sans activation : le fil est celui d'avant (aucune clé nouvelle) et la limite reste 2 048
        val plain = ClientMsg.Join("K7M2QX4T", "Awa", null, "dev-0123456789", false)
        assertFalse(PlayCodec.encode(plain).contains("activation"))
        assertEquals(null, (PlayCodec.decodeClient(PlayCodec.encode(plain)) as PlayCodec.Decoded.Ok).msg.let { (it as ClientMsg.Join).activation })
        val longName = """{"t":"join","code":"K7M2QX4T","name":"${"n".repeat(2_100)}"}"""
        assertTrue(PlayCodec.decodeClient(longName) is PlayCodec.Decoded.Bad, "sans activation : 2 048 octets au plus")
        // bornes : ASCII visible seulement, ≤ MAX_ACTIVATION, jamais plus de MAX_CREATE_BYTES
        assertTrue(PlayCodec.decodeClient(PlayCodec.encode(plain).replace("}", ""","activation":"${"A".repeat(PlayProtocol.MAX_ACTIVATION + 1)}"}""")) is PlayCodec.Decoded.Bad)
        assertTrue(PlayCodec.decodeClient(PlayCodec.encode(plain).replace("}", ""","activation":"cbx1 espace"}""")) is PlayCodec.Decoded.Bad)
        assertTrue(PlayCodec.decodeClient(PlayCodec.encode(plain).replace("}", ""","activation":12}""")) is PlayCodec.Decoded.Bad)
        assertTrue(PlayCodec.decodeClient("""{"t":"join","code":"K7M2QX4T","activation":"x","pad":"${"p".repeat(PlayProtocol.MAX_CREATE_BYTES)}"}""") is PlayCodec.Decoded.Bad)
        // l'activation n'apparaît jamais dans le texte d'un message journalisé
        assertFalse(withAct.toString().contains("AAAA"), withAct.toString())
    }
}
