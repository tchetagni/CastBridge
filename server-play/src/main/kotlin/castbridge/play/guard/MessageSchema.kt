package castbridge.play.guard

import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol

/**
 * Schéma des messages entrants (T-13) : première ligne de défense AVANT le codec de `play-v1`. Le codec valide types, bornes et énumérations ; ici s'ajoute ce qu'un
 * décodeur récursif ne doit pas avoir à encaisser : profondeur d'imbrication bornée (un message de 2 Ko fait 2 000 crochets : pile épuisée), nombre de clés bornée,
 * et AUCUNE exception ne sort jamais (ni trace de pile dans une réponse). Un message invalide reçoit `BAD_REQUEST` ou `UNSUPPORTED`, sans effet sur la salle.
 * Les clés inconnues restent acceptées (règle additive W19).
 */
object MessageSchema {
    const val MAX_DEPTH = 6
    const val MAX_KEYS = 40

    /** Décode un message client ; jamais d'exception. */
    fun decode(text: String): PlayCodec.Decoded {
        if (text.length > PlayProtocol.MAX_MESSAGE_BYTES) return bad("message trop long")
        shape(text)?.let { return bad(it) }
        return try { PlayCodec.decodeClient(text) } catch (_: Throwable) { bad("message illisible") }
    }

    private fun bad(detail: String) = PlayCodec.Decoded.Bad(PlayProtocol.BAD_REQUEST, detail)

    /** Contrôle de forme sans récursion : profondeur et nombre de clés/éléments (les séparateurs `,` hors chaînes). */
    internal fun shape(text: String): String? {
        var depth = 0; var inString = false; var escaped = false; var items = 0
        for (ch in text) {
            if (inString) {
                if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{', '[' -> { if (++depth > MAX_DEPTH) return "imbrication trop profonde" }
                '}', ']' -> depth--
                ',' -> if (++items > MAX_KEYS * 4) return "trop d'éléments"
            }
        }
        return null
    }
}

/** Compte les messages invalides d'UNE connexion : au troisième dans la minute, la session est fermée (1008). */
class InvalidTally(private val limit: Int = LIMIT, private val windowMs: Long = WINDOW_MS, private val clock: () -> Long = System::currentTimeMillis) {
    private val at = ArrayDeque<Long>()

    /** Note un message invalide ; vrai = trop nombreux, il faut fermer. */
    @Synchronized fun bad(): Boolean {
        val now = clock()
        while (at.isNotEmpty() && now - at.first() >= windowMs) at.removeFirst()
        at.addLast(now)
        return at.size >= limit
    }

    companion object { const val LIMIT = 3; const val WINDOW_MS = 60_000L }
}
