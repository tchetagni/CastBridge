package castbridge.core.quiz.online

/**
 * Anneau des derniers évènements PUBLICS d'une salle (jamais de réponse avant clôture), pour `resume(lastSeq)` : un client qui revient
 * dans la fenêtre reçoit les évènements manqués ; plus ancien que l'anneau, il reçoit un `state` complet.
 */
class EventRing(val capacity: Int = 50) {
    data class Event(val seq: Long, val kind: String, val data: Map<String, Any?> = emptyMap())

    private val items = ArrayDeque<Event>()
    /** Plus grand `seq` déjà oublié par l'anneau (0 tant que rien n'a été évincé). */
    private var evictedUpTo = 0L

    fun add(seq: Long, kind: String, data: Map<String, Any?> = emptyMap()) {
        items.addLast(Event(seq, kind, data))
        while (items.size > capacity) evictedUpTo = items.removeFirst().seq
    }

    val latestSeq: Long get() = items.lastOrNull()?.seq ?: 0L
    val size: Int get() = items.size

    /** Évènements de `seq > lastSeq`, ou null si l'anneau ne couvre plus `lastSeq` (trop vieux : resynchronisation complète). */
    fun since(lastSeq: Long): List<Event>? {
        if (lastSeq < evictedUpTo) return null
        return items.filter { it.seq > lastSeq }
    }
}
