package castbridge.play

import castbridge.core.quiz.online.PlayProtocol

/** `GET /play/health` et `GET /play/.well-known/caps` : JSON sans secret (ni adresse IP, ni code de salle, ni jeton). */
class HealthController(private val cfg: PlayConfig, private val hub: PlayHub, private val limits: ConnectionLimits, private val startedAt: Long = System.currentTimeMillis(),
                       /** État réel des révocations : `ok`, `none` (aucune liste acceptée) ou `stale`. */ private val revocations: () -> String = { "none" }) {
    fun health(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        return """{"status":"ok","version":"${cfg.version}","proto":${PlayProtocol.PROTO},"rooms":${hub.roomCount()},"maxRooms":${cfg.maxRooms},""" +
            """"connections":${limits.total()},"maxConnections":${cfg.maxConnections},"memoryUsedMb":$used,"memoryMaxMb":${rt.maxMemory() / (1024 * 1024)},""" +
            """"uptimeSec":${(System.currentTimeMillis() - startedAt) / 1000},"usedTickets":${hub.usedTicketCount()},"revocations":"${revocations()}"}"""
    }

    fun caps(): String = """{"name":"${PlayProtocol.NAME}","proto":${PlayProtocol.PROTO},"caps":[${PlayProtocol.CAPS.joinToString(",") { "\"$it\"" }}],""" +
        """"transports":["ws","sse","longpoll"],"maxMessageBytes":${PlayProtocol.MAX_MESSAGE_BYTES},"pingSeconds":${cfg.pingMs / 1000},"pollSeconds":${cfg.pollMs / 1000}}"""
}
