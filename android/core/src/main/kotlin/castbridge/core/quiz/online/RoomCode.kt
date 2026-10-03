package castbridge.core.quiz.online

/** Code de salle Internet : 8 caractères Crockford (32 symboles, sans I L O U) = 40 bits, affiché `XXXX-XXXX` (DESIGN-W20 § 1.6). */
object RoomCode {
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 8
    const val TTL_MS = 2 * 60 * 60_000L
    /** Essais faux sur une salle avant renouvellement du code. */
    const val ROTATE_AFTER = 50

    /** 40 bits tirés de [random] (passer un générateur sécurisé en production). */
    fun generate(random: java.util.Random): String {
        val bits = random.nextLong() and ((1L shl 40) - 1)
        return String(CharArray(LENGTH) { ALPHABET[((bits ushr (5 * it)) and 31).toInt()] })
    }

    fun display(code: String): String = if (code.length == LENGTH) code.substring(0, 4) + "-" + code.substring(4) else code

    /** Casse, tirets et espaces ignorés ; I et L lus comme 1, O comme 0 ; null si le résultat n'est pas un code valide. */
    fun normalize(raw: String?): String? {
        val s = raw?.uppercase()?.filter { it != '-' && !it.isWhitespace() }?.map { when (it) { 'I', 'L' -> '1'; 'O' -> '0'; else -> it } }?.joinToString("") ?: return null
        return s.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    fun expired(createdAt: Long, now: Long, ttlMs: Long = TTL_MS) = now - createdAt >= ttlMs
}

/** Essais de code faux : par IP (10 / 5 min) et par salle (50 ⇒ nouveau code). Pure, horloge passée. */
class BadCodeCounter(private val perIpMax: Int = PlayProtocol.MAX_BAD_CODES_PER_IP, private val windowMs: Long = PlayProtocol.BAD_CODE_WINDOW_MS,
                     private val perRoomMax: Int = RoomCode.ROTATE_AFTER) {
    private val byIp = HashMap<String, ArrayDeque<Long>>()
    private var room = 0

    /** Note un essai faux d'une IP ; true = cette IP est maintenant bloquée. */
    fun ipFail(ip: String, now: Long): Boolean {
        val q = byIp.getOrPut(ip) { ArrayDeque() }
        q.addLast(now); trim(q, now)
        return q.size >= perIpMax
    }

    fun ipBlocked(ip: String, now: Long): Boolean = byIp[ip]?.let { trim(it, now); it.size >= perIpMax } ?: false

    /** Note un essai faux sur la salle ; true = il faut renouveler le code (le compteur repart de zéro). */
    fun roomFail(): Boolean { room++; return (room >= perRoomMax).also { if (it) room = 0 } }

    private fun trim(q: ArrayDeque<Long>, now: Long) { while (q.isNotEmpty() && now - q.first() >= windowMs) q.removeFirst() }
}
