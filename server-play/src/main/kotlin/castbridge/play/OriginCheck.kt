package castbridge.play

/**
 * Contrôle de l'en-tête `Origin` (WebSocket, POST de repli) : liste blanche `CASTBRIDGE_PLAY_ORIGINS`. Un `Origin` absent ou `null` (client natif :
 * CastBridge-TV, CastBridge) n'est accepté QUE avec un ticket valide ; une origine présente mais inconnue est toujours refusée.
 */
class OriginCheck(private val allowed: Set<String>) {
    fun allows(origin: String?, hasValidTicket: Boolean): Boolean {
        val o = origin?.trim()?.lowercase()
        if (o == null || o.isEmpty() || o == "null") return hasValidTicket
        return o in allowed
    }

    /** Pour les GET de repli (flux, long-poll) : le secret de connexion (128 bits) fait foi ; une origine présente doit être connue, absente = même origine. */
    fun allowsRead(origin: String?): Boolean {
        val o = origin?.trim()?.lowercase()
        return o == null || o.isEmpty() || o in allowed
    }
}
