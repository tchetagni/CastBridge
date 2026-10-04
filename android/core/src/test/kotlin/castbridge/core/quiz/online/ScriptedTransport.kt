package castbridge.core.quiz.online

/**
 * Transport de test : garde ce que la TV envoie (décodé) et laisse le test pousser des messages du « serveur ». [reply] répond de façon synchrone
 * à chaque message client (comme un service local sans latence). Ne sert que dans `core` (aucune socket).
 */
class ScriptedTransport : PlayTransport, RttSource {
    @Volatile override var state = PlayTransport.Status.OPEN
    val sent = ArrayList<ClientMsg>()
    var reply: (ClientMsg) -> Unit = {}
    private var listener: ((String) -> Unit)? = null
    private var rttListener: ((Long) -> Unit)? = null
    override fun onRtt(listener: (Long) -> Unit) { rttListener = listener }
    fun rtt(ms: Long) { rttListener?.invoke(ms) }

    override fun onMessage(listener: (String) -> Unit) { this.listener = listener }
    override fun send(text: String) {
        val m = (PlayCodec.decodeClient(text) as PlayCodec.Decoded.Ok).msg
        synchronized(sent) { sent += m }
        reply(m)
    }
    fun push(m: ServerMsg) { listener?.invoke(PlayCodec.encode(m)) }
    inline fun <reified T : ClientMsg> all(): List<T> = synchronized(sent) { sent.filterIsInstance<T>() }
}
