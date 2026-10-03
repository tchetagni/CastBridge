package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.Limits
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.ServerMsg

/**
 * Filtre par MESSAGE (la connexion, elle, est filtrée AVANT l'upgrade par `ConnectionLimits` + [Limits.admitConnection]) : débit d'entrée en salle par appareil, débit
 * de messages par salle. Un refus est un `PLAY_BUSY` avec `retryAfterMs` STRUCTURÉ (jamais seulement dans le texte) : le message est ignoré, rien n'est fermé, aucune liste
 * noire. L'effet sur la salle d'un message refusé est nul.
 */
class LimitFilter(val limits: Limits, private val log: () -> LogRedactor) {
    /** `join` d'un appareil identifié : seau par appareil. */
    fun device(c: castbridge.play.PlayConn, msg: ClientMsg): ServerMsg.Error? {
        val device = (msg as? ClientMsg.Join)?.deviceHash ?: return null
        val d = limits.admitDevice(device)
        if (d.allowed) return null
        log().event("play.limit.exceeded", "débit d'entrée par appareil dépassé", mapOf("scope" to d.scope?.name, "device" to device, "ip" to c.ip, "retryAfterMs" to d.retryAfterMs))
        return busy(d.retryAfterMs)
    }

    /** Un message à destination d'une salle : seau par salle. */
    fun room(c: castbridge.play.PlayConn, roomId: String): ServerMsg.Error? {
        val d = limits.admitRoom(roomId)
        if (d.allowed) return null
        log().event("play.limit.exceeded", "débit de messages de la salle dépassé", mapOf("scope" to d.scope?.name, "roomId" to roomId, "ip" to c.ip, "retryAfterMs" to d.retryAfterMs))
        return busy(d.retryAfterMs)
    }

    private fun busy(retryAfterMs: Long) = ServerMsg.Error(0, PlayReason.PLAY_BUSY.code, PlayReason.PLAY_BUSY.message, true, maxOf(retryAfterMs, 1L))
}
