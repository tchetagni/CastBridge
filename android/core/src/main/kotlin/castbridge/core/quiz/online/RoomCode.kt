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

    /**
     * Frappe PROCHE d'un code vivant : mêmes huit symboles à un seul près (faute de frappe, ou énumération qui rôde autour d'un code). C'est ce qui s'attribue à une salle :
     * un code faux ordinaire ne vise aucune salle en particulier. Les deux arguments sont déjà normalisés ([normalize]).
     */
    fun nearMiss(typed: String, code: String): Boolean = typed.length == LENGTH && code.length == LENGTH && typed != code && typed.indices.count { typed[it] != code[it] } == 1

    fun expired(createdAt: Long, now: Long, ttlMs: Long = TTL_MS) = now - createdAt >= ttlMs
}

/**
 * Essais de code faux : par adresse (30 / 5 min et 300 / jour, voir [MAX_PER_DAY]) et par salle (50 ⇒ nouveau code). Pure, horloge passée.
 * Mémoire BORNÉE (audit w20-03, B3) : une file vidée par le temps est retirée, interroger ne crée rien, [maxEntries] adresses au plus
 * (les plus anciennement actives partent d'abord) et [sweep] balaie les files expirées (appelé par le tick du service).
 * Les clés sont des CLÉS d'adresse fournies par l'appelant (IPv4 telle quelle, IPv6 réduite à son préfixe /64).
 */
class BadCodeCounter(private val perIpMax: Int = PlayProtocol.MAX_BAD_CODES_PER_IP, private val windowMs: Long = PlayProtocol.BAD_CODE_WINDOW_MS,
                     private val perRoomMax: Int = RoomCode.ROTATE_AFTER, private val maxEntries: Int = MAX_ENTRIES,
                     private val perIpDayMax: Int = MAX_PER_DAY, private val dayMs: Long = DAY_MS) {
    private val byIp = LinkedHashMap<String, ArrayDeque<Long>>()   // essais récents (fenêtre de [windowMs]) ; ordre = dernière faute
    /** Compteur de jour à fenêtre fixe : [début, nombre]. Mémoire O(1) par adresse (jamais [perIpDayMax] dates) ; borné aussi à [maxEntries]. */
    private val byDay = LinkedHashMap<String, LongArray>()
    private var room = 0

    /** Note un essai faux d'une adresse ; true = cette adresse est maintenant bloquée. */
    fun ipFail(ip: String, now: Long): Boolean {
        val q = byIp.remove(ip) ?: ArrayDeque()
        q.addLast(now); trim(q, now)
        byIp[ip] = q
        while (byIp.size > maxEntries) { val eldest = byIp.keys.iterator(); eldest.next(); eldest.remove() }
        val d = byDay.remove(ip)?.takeIf { now - it[0] < dayMs } ?: longArrayOf(now, 0L)
        d[1]++
        byDay[ip] = d
        while (byDay.size > maxEntries) { val eldest = byDay.keys.iterator(); eldest.next(); eldest.remove() }
        return q.size >= perIpMax || d[1] >= perIpDayMax
    }

    fun ipBlocked(ip: String, now: Long): Boolean = shortBlocked(ip, now) || dayBlocked(ip, now)

    private fun shortBlocked(ip: String, now: Long): Boolean {
        val q = byIp[ip] ?: return false
        trim(q, now)
        if (q.isEmpty()) { byIp.remove(ip); return false }
        return q.size >= perIpMax
    }

    private fun dayBlocked(ip: String, now: Long): Boolean {
        val d = byDay[ip] ?: return false
        if (now - d[0] >= dayMs) { byDay.remove(ip); return false }
        return d[1] >= perIpDayMax
    }

    /** Attente avant que l'adresse puisse réessayer (0 si elle n'est pas bloquée) : la plus longue des deux fenêtres qui bloquent. */
    fun retryAfterMs(ip: String, now: Long): Long {
        val short = if (shortBlocked(ip, now)) byIp.getValue(ip).first() + windowMs - now else 0L
        val day = if (dayBlocked(ip, now)) byDay.getValue(ip)[0] + dayMs - now else 0L
        return maxOf(short, day, 0L)
    }

    /** Retire les files expirées. */
    fun sweep(now: Long) {
        val it = byIp.entries.iterator()
        while (it.hasNext()) { val q = it.next().value; trim(q, now); if (q.isEmpty()) it.remove() }
        byDay.entries.removeIf { now - it.value[0] >= dayMs }
    }

    /** Nombre d'adresses suivies par la fenêtre courte. */
    fun size(): Int = byIp.size

    /** Nombre d'adresses suivies par le compteur de jour. */
    fun daySize(): Int = byDay.size

    /** Note un essai faux sur la salle ; true = il faut renouveler le code (le compteur repart de zéro). */
    fun roomFail(): Boolean { room++; return (room >= perRoomMax).also { if (it) room = 0 } }

    private fun trim(q: ArrayDeque<Long>, now: Long) { while (q.isNotEmpty() && now - q.first() >= windowMs) q.removeFirst() }

    companion object {
        const val MAX_ENTRIES = 50_000
        /**
         * Codes faux par adresse et par jour : 300 (la conception disait 100). Derrière un CGNAT ou dans une école, des dizaines de joueurs partagent une adresse et en tapent
         * de faux de bonne foi ; 300 par jour reste très loin de l'énumération (2^40 codes pour 400 salles vivantes).
         */
        const val MAX_PER_DAY = 300
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
