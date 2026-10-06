package castbridge.core.xfer

/**
 * R-21 (B) : les connexions de voie que la TV tient pour un transfert. Aucune ne doit rester ouverte sans servir :
 *  - une voie dont la requête n'a rien lu depuis [idleMs] (téléphone parti, lien mort, écriture bloquée) est fermée (son socket, donc le fil qui lisait) ;
 *  - une voie au repos (entre deux requêtes) depuis [restMs] (2 min : un téléphone qui attend sa file garde sa connexion) est fermée aussi ;
 *  - au plus [maxStreams] voies par session : les surnuméraires (les plus anciennement actives) sont fermées.
 * Pure (horloge et fermeture injectées, testée en JVM) ; le serveur appelle [sweep] toutes les quelques secondes.
 */
class LaneRegistry(private val now: () -> Long = System::currentTimeMillis, private val idleMs: Long = IDLE_MS, private val restMs: Long = REST_MS,
                   private val maxStreams: () -> Int = { 8 }) {
    inner class Handle internal constructor(val session: String, internal val closer: () -> Unit) {
        @Volatile internal var lastMs = now()
        @Volatile internal var closed = false
        @Volatile internal var resting = false
        /** Octets lus ou écrits, ou une requête qui commence : la voie vit. */
        fun touch() { lastMs = now(); resting = false }
        /** La requête est finie : la connexion reste ouverte (keep-alive) mais au repos. */
        fun idle() { lastMs = now(); resting = true }
        /** Le fil de cette connexion a fini : plus rien à surveiller. */
        fun forget() { synchronized(this@LaneRegistry) { all.remove(this) } }
        val wasClosed: Boolean get() = closed
    }

    private val all = ArrayList<Handle>()
    /** Voies fermées par le balayage depuis le début (tests, journal). */
    @Volatile var closedCount = 0; private set

    @Synchronized fun register(session: String, closer: () -> Unit): Handle = Handle(session, closer).also { all += it }
    @Synchronized fun open(): Int = all.count { !it.closed }
    @Synchronized fun openOf(session: String): Int = all.count { it.session == session && !it.closed }

    /** Ferme ce qui doit l'être ; renvoie le nombre de voies fermées. */
    fun sweep(): Int {
        val toClose = ArrayList<Handle>()
        synchronized(this) {
            val t = now()
            all.removeAll { it.closed }
            for (h in all) if (t - h.lastMs >= (if (h.resting) restMs else idleMs)) toClose += h
            val cap = maxStreams().coerceAtLeast(1)
            for ((_, hs) in all.filter { it !in toClose }.groupBy { it.session }) {
                if (hs.size > cap) toClose += hs.sortedBy { it.lastMs }.take(hs.size - cap)
            }
            toClose.forEach { it.closed = true }
            all.removeAll(toClose.toSet())
            closedCount += toClose.size
        }
        toClose.forEach { runCatching { it.closer() } }
        return toClose.size
    }

    companion object { const val IDLE_MS = 30_000L; const val REST_MS = 120_000L }
}
