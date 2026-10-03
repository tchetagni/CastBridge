package castbridge.play

import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Partie complète à 8 joueurs + l'hôte sur socket réelle, les TROIS transports mélangés dans la même salle (4 WebSocket, 2 SSE, 2 long-poll) : délai de 1 à 2 s entre les questions,
 * aucune fuite de réponse avant la clôture, équité (aucune réponse comptée avant l'ouverture ni plus vite que la vérité), deux tricheurs refusés `TOO_EARLY`.
 */
class EightClientGameTest {
    private val servers = ArrayList<PlayServer>()
    @AfterTest fun stop() { servers.forEach { it.close() } }

    @Test fun eightClientsOverWebSocketSseAndLongPollPlayOneFairDuel() {
        val srv = PlayServer(PlayConfig(port = 0, ticketPubKeys = listOf(TestKeys.pub))).start().also { servers += it }
        fun ip(n: Int) = "203.0.113.$n"
        val seats = (0 until 8).map { k ->
            val wire: Wire = when {
                k < 4 -> WsWire(srv.port, xff = ip(10 + k))
                k < 6 -> SseWire(srv.port, xff = ip(10 + k))
                else -> PollWire(srv.port, xff = ip(10 + k))
            }
            Seat("Joueur${k + 1}", wire, rank = k, delayMs = 100L + 70 * k, cheatAtIndex = when (k) { 3 -> 3; 5 -> 6; else -> -1 })
        }
        val run = GameRun(srv, WsWire(srv.port, xff = ip(1)), seats)
        try { run.play().verify() } finally { run.stop() }
    }
}
