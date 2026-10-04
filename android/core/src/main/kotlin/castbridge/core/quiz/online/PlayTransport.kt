package castbridge.core.quiz.online

/** Transport abstrait du protocole `play` : du texte JSON dans les deux sens (WebSocket, SSE + POST, long-poll, ou mémoire en test). Pas de socket ici. */
interface PlayTransport {
    enum class Status { CONNECTING, OPEN, CLOSED }

    val state: Status
    /** Envoie un message client déjà encodé par [PlayCodec]. */
    fun send(text: String)
    /** Écouteur des messages serveur (texte JSON) ; un seul écouteur actif. */
    fun onMessage(listener: (String) -> Unit)
}

/** w20-05a : un transport qui mesure lui-même l'aller-retour de ses envois (ms) ; la TV s'en sert pour la règle 11 (rtt/2 à l'annonce). */
interface RttSource { fun onRtt(listener: (Long) -> Unit) }

/** w20-05a : un transport qui sait dire qu'il a échoué sur un certificat non valide (jamais de contournement : l'état devient rouge). */
interface TransportHealth { val certificateInvalid: Boolean }

/** Fabrique de transports de la TV : [ticket] n'est donné qu'au premier envoi d'une session neuve (création ou entrée) ; null pour une reprise. */
fun interface TransportFactory { fun open(ticket: String?): PlayTransport }

/**
 * Transport EN MÉMOIRE branché directement sur une [ServerRoom] : même codec, même ordre des messages qu'avec une vraie socket, mais synchrone
 * (preuve « un seul cœur » du contrat des deux autorités ; w20-03 rejouera la même chose sur socket). [now] est l'horloge du serveur.
 */
class MemoryPlayHub(private val room: ServerRoom, private val now: () -> Long) {
    private val peers = HashMap<String, Peer>()

    inner class Peer(val id: String) : PlayTransport {
        override var state = PlayTransport.Status.OPEN; internal set
        private var listener: ((String) -> Unit)? = null
        override fun onMessage(listener: (String) -> Unit) { this.listener = listener }
        internal fun receive(m: ServerMsg) { listener?.invoke(PlayCodec.encode(m)) }
        override fun send(text: String) {
            if (state != PlayTransport.Status.OPEN) return
            when (val d = PlayCodec.decodeClient(text)) {
                is PlayCodec.Decoded.Ok -> deliver(room.handle(id, d.msg, now()))
                is PlayCodec.Decoded.Bad -> receive(ServerMsg.Error(room.seq(), d.reason, d.detail, false))
            }
        }
    }

    fun connect(id: String): PlayTransport = Peer(id).also { peers[id] = it }

    /** La connexion tombe (le siège est gardé pour `resume`). */
    fun drop(id: String) { peers.remove(id)?.let { it.state = PlayTransport.Status.CLOSED; deliver(room.disconnect(id, now())) } }

    /** Fait avancer la salle à l'heure courante et distribue ce qu'elle produit. */
    fun tick() = deliver(room.tick(now()))

    private fun deliver(outs: List<ServerRoom.Out>) { for (o in outs) peers[o.to]?.receive(o.msg) }
}
