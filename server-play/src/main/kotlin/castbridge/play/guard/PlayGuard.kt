package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.Limits
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.Pseudonym
import castbridge.core.quiz.online.ServerMsg
import castbridge.play.PlayConn

/**
 * Les gardes du service (w20-07) réunies derrière UN objet que `PlayHub` appelle en quelques points : décodage strict + compte des invalides, pseudonymes, débit par appareil
 * et par salle, journal sans secret. `PlayHub` ne contient AUCUNE règle : s'il faut une règle de plus, elle va ici.
 *
 * Toutes les décisions sont DOUCES : refus d'un message, jamais de liste noire d'adresses, jamais de blocage par pays, jamais d'empreinte de navigateur.
 */
class PlayGuard(val limits: Limits = Limits(), log: LogRedactor = LogRedactor()) {
    @Volatile var log: LogRedactor = log
    val filter = LimitFilter(limits) { this.log }

    /** Décode un message entrant ; compte les invalides de la connexion. */
    fun decode(c: PlayConn, text: String): PlayCodec.Decoded = MessageSchema.decode(text).also {
        if (it is PlayCodec.Decoded.Bad) this.log.event("play.msg.invalid", it.detail, mapOf("reason" to it.reason, "ip" to c.ip), level = "warn")
    }

    /** Vrai si la connexion a envoyé trop de messages invalides : à fermer (1008). */
    fun tooManyInvalid(c: PlayConn): Boolean = c.invalidTally.bad()

    /** Refuse un message AVANT son aiguillage : pseudonyme interdit (`BAD_NAME` avec motif) ou débit d'entrée de l'appareil dépassé. Null = laisser passer. */
    fun refuse(c: PlayConn, msg: ClientMsg): ServerMsg.Error? {
        val name = when (msg) { is ClientMsg.Create -> msg.name; is ClientMsg.Join -> msg.name; else -> null }
        if (name != null) {
            val r = Pseudonym.check(name)
            if (r is Pseudonym.Result.Refused) {
                log.event("play.name.refused", "pseudonyme refusé", mapOf("reason" to r.reason.code, "name" to name, "ip" to c.ip), level = "warn")
                return ServerMsg.Error(0, PlayReason.BAD_NAME.code, r.text, PlayReason.BAD_NAME.retryable)
            }
        }
        return filter.device(c, msg)
    }

    /** Un siège vient d'être pris (ou la salle créée) : journalise ; un JOUEUR (pas un spectateur) est mémorisé pour le plafond relevé des adresses partagées. */
    fun seated(c: PlayConn, msg: ClientMsg, roomId: String, role: castbridge.core.quiz.online.PlayRole) {
        if (msg is ClientMsg.Join && msg.deviceHash != null && role == castbridge.core.quiz.online.PlayRole.PLAYER) {
            limits.noteSeat(c.ip, roomId, msg.deviceHash!!); c.seatKey = roomId to msg.deviceHash!!
        }
        log.event(if (msg is ClientMsg.Create) "play.room.created" else "play.room.seated", "", mapOf("roomId" to roomId, "ip" to c.ip))
    }

    /** La connexion est tombée : son siège ne compte plus pour le plafond relevé. */
    fun unseated(c: PlayConn) { c.seatKey?.let { limits.dropSeat(c.ip, it.first, it.second) } }

    /** La salle est fermée : son seau part. */
    fun forget(roomId: String) = limits.forgetRoom(roomId)
}
