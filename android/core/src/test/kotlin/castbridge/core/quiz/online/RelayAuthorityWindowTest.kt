package castbridge.core.quiz.online

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QuizRoom
import kotlin.test.Test
import kotlin.test.assertEquals

/** relay-R1 § 5 : par relais, les téléphones de la maison gardent la fenêtre de réponse du service PLUS la latence mesurée (bornée) ; hors relais rien ne change. */
class RelayAuthorityWindowTest {
    private var now = 10_000L
    private val t = ScriptedTransport()
    private val session = PlayTvSession(clock = { now }, transports = { t }, ticket = { "cbp1.ticket-de-test" })

    init {
        t.reply = { m ->
            when (m) {
                is ClientMsg.Create -> t.push(ServerMsg.Welcome(1, "room-1", "K7M2QX4T", "tv-token", PlayRole.HOST, null, 1, emptyList()))
                is ClientMsg.Join -> if (m.token == null) t.push(ServerMsg.Welcome(2, "room-1", "K7M2QX4T", "seat-1", PlayRole.PLAYER, "p1", 1, emptyList()))
                else -> {}
            }
        }
        session.start("dev-tv-000001", "cbx1.activation", PlayTvSession.Intent.Create("TV A", "DUEL"))
    }

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    /** Ouvre une question de 15 s : l'annonce arrive à now = 10 000, l'ouverture locale est à 11 500. */
    private fun open(relay: RelayAuthority): String {
        val room = QuizRoom(EmbeddedQuestionSource().bank(), clock = { 1_000L }, random = java.util.Random(3), autoTick = false)
        room.join(room.code, "Awa"); room.join(room.code, "Bello")
        room.setMode(QuizRoom.Mode.DUEL); room.startGame(seed = 5)
        val token = relay.join(null, "Awa", null, "dev-phone-0001").player!!.token
        t.push(ServerMsg.State(5, room.view(null), false))
        val q = room.duel!!.question
        t.push(ServerMsg.Question(6, q.id, 0, room.duel!!.questions.size, q.question, q.choices, 20_000L, 18_500L, 15_000L))
        return token
    }

    private fun remaining(relay: RelayAuthority, token: String): Long = (relay.view(token).m("duel")["remainingMs"] as Number).toLong()

    @Test fun withoutARelayTheCountdownIsTheServersOwn() {
        val relay = RelayAuthority(session, { now })
        val tok = open(relay)
        now = 12_000L
        assertEquals(14_500L, remaining(relay, tok))
        now = 26_600L
        assertEquals(0L, remaining(relay, tok), "fenêtre close")
    }

    @Test fun overTheRelayTheWindowIsLongerByTheMeasuredLatency() {
        val extra = PlayRelayProfile.windowFor(0, PlayRelayProfile.rules(castbridge.core.connect.NetState.VIA_RELAY, RelayLink(700, 300)))
        assertEquals(700L, extra)
        val relay = RelayAuthority(session, { now }, extraWindowMs = { extra })
        val tok = open(relay)
        now = 12_000L
        assertEquals(15_200L, remaining(relay, tok), "14 500 + 700 : la réponse envoyée à temps par le téléphone arrive à temps au service malgré la liaison lente")
        now = 26_600L
        assertEquals(600L, remaining(relay, tok), "la rallonge ne dépasse jamais la grâce du service")
        now = 27_300L
        assertEquals(0L, remaining(relay, tok))
    }

    @Test fun theExtensionIsReadAtEachCallSoAMeasureThatArrivesLaterIsUsed() {
        var ms = 0L
        val relay = RelayAuthority(session, { now }, extraWindowMs = { ms })
        val tok = open(relay)
        now = 12_000L
        assertEquals(14_500L, remaining(relay, tok))
        ms = 300L
        assertEquals(14_800L, remaining(relay, tok))
    }

    @Test fun aNegativeExtensionNeverShortensTheWindow() {
        val relay = RelayAuthority(session, { now }, extraWindowMs = { -5_000L })
        val tok = open(relay)
        now = 12_000L
        assertEquals(14_500L, remaining(relay, tok))
    }
}
