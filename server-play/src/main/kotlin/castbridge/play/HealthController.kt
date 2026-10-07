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
            """"uptimeSec":${(System.currentTimeMillis() - startedAt) / 1000},"usedTickets":${hub.usedTicketCount()},"revocations":"${revocations()}",""" +
            """"revocationsEnforced":${revocations() == "ok"},"proof":"${if (cfg.requireProof) "required" else "optional"}",""" +
            """"chess":${cfg.chess},"chessRooms":${hub.chessRooms().size},"stakes":${stakesOn()}}"""
    }

    /** Les mises sont acceptées : interrupteur allumé, clés présentes (le hub le sait), échecs ouverts. */
    private fun stakesOn(): Boolean = cfg.chess && hub.stakesOn()

    /**
     * Les capacités du service (sans secret). `caps` = [PlayProtocol.CAPS] moins `chess` quand les échecs sont coupés, plus `stakes` quand les mises sont acceptées ; `chess` et `stakes` en booléens
     * pour la TV, qui sonde ce chemin avant d'ouvrir l'écran « Échecs › En ligne » (plus de drapeau `online=1` sur la TV).
     */
    fun caps(): String {
        val caps = PlayProtocol.CAPS.filter { it != PlayProtocol.GAME_CHESS || cfg.chess } + (if (stakesOn()) listOf(PlayProtocol.CAP_STAKES) else emptyList())
        val games = if (cfg.chess) PlayProtocol.GAMES.sorted() else emptyList()
        return """{"name":"${PlayProtocol.NAME}","proto":${PlayProtocol.PROTO},"caps":[${caps.joinToString(",") { "\"$it\"" }}],""" +
            """"games":[${games.joinToString(",") { "\"$it\"" }}],"chess":${cfg.chess},"stakes":${stakesOn()},""" +
            """"transports":["ws","sse","longpoll"],"maxMessageBytes":${PlayProtocol.MAX_MESSAGE_BYTES},"pingSeconds":${cfg.pingMs / 1000},"pollSeconds":${cfg.pollMs / 1000},"maxCreateBytes":${PlayProtocol.MAX_CREATE_BYTES}${if (cfg.revocationsMode == RevocationsMode.OFF) ",\"revocations\":\"off\"" else ""}}"""
    }
}
