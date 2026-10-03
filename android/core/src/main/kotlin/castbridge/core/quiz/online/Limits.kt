package castbridge.core.quiz.online

/**
 * Seaux de limites du service de jeu en ligne (DESIGN-W20 § 2.8, T-6) : par clé d'adresse, par appareil (`deviceHash`), par salle, et globale. PUR : l'horloge (ms) est
 * injectée, aucun fil ; le service l'appelle sous son propre verrou ([Limits] est synchronisé de lui-même, section très courte, O(1)).
 *
 * Mémoire BORNÉE : au plus [maxKeys] seaux en tout (100 000), éviction du MOINS récemment utilisé ; une clé évincée repart avec un seau plein (jamais l'inverse :
 * l'éviction ne bloque personne). Les clés d'adresse sont fournies par l'appelant (IPv4 telle quelle, IPv6 réduite à son /64).
 *
 * Valeurs de départ ([Config]) et pourquoi elles sont plus larges que la conception initiale (« 20 par minute, 8 ouvertes ») : derrière un CGNAT d'opérateur mobile,
 * une école ou une box familiale, des dizaines d'appareils partagent UNE adresse ; la limite par adresse ne doit pas punir ces joueurs.
 *  - nouvelles connexions : 60 par minute et 600 par heure et par clé d'adresse (une classe de 40 élèves qui se connectent en même temps tient dans 60) ; 60 par seconde en tout ;
 *  - connexions OUVERTES par adresse : 8 ; relevé à 64 quand au moins 8 appareils distincts d'une même adresse sont assis dans une MÊME salle (salle de classe) :
 *    `CASTBRIDGE_PLAY_MAX_PER_IP_SHARED` ;
 *  - par appareil : 20 entrées en salle par minute (un joueur ne change pas de salle 20 fois par minute) ;
 *  - par salle : 300 messages par seconde, rafale 600 (8 joueurs à 10/s = 80/s, plus 50 spectateurs et la TV, avec marge).
 * Chaque refus donne une attente `retryAfterMs` (temps réel avant le prochain jeton), jamais un blocage définitif ni une liste noire d'adresses.
 */
class Limits(private val clock: () -> Long = System::currentTimeMillis, val config: Config = Config(), private val maxKeys: Int = MAX_KEYS) {
    data class Config(
        val connPerMinute: Int = 60,
        val connPerHour: Int = 600,
        val connPerSecondGlobal: Int = 60,
        /** Un seul /48 IPv6 (65 536 /64) ne doit pas saturer le seau global : 300 par minute. */
        val connPerMinutePer48: Int = 300,
        val maxOpenPerIp: Int = 8,
        val maxOpenPerIpShared: Int = 64,
        val sharedDevices: Int = 8,
        val joinPerMinutePerDevice: Int = 20,
        val roomMessagesPerSecond: Int = 300,
        val roomBurst: Int = 600,
    )

    enum class Scope { IP_MINUTE, IP_HOUR, PREFIX48, GLOBAL, DEVICE, ROOM }

    /** `retryAfterMs` = attente avant le prochain jeton (au moins 1). */
    data class Decision(val allowed: Boolean, val retryAfterMs: Long = 0L, val scope: Scope? = null) {
        val retryAfterSeconds: Long get() = (retryAfterMs + 999) / 1_000
    }

    private class Bucket(val capacity: Double, val perMs: Double, var tokens: Double, var at: Long)

    private val buckets = object : LinkedHashMap<String, Bucket>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bucket>?): Boolean = size > maxKeys
    }

    private fun bucket(key: String, capacity: Int, perMs: Double, now: Long): Bucket {
        val b = buckets.getOrPut(key) { Bucket(capacity.toDouble(), perMs, capacity.toDouble(), now) }
        b.tokens = minOf(b.capacity, b.tokens + maxOf(0L, now - b.at) * b.perMs); b.at = now
        return b
    }

    private fun wait(b: Bucket): Long = maxOf(1L, Math.ceil((1.0 - b.tokens) / b.perMs).toLong())

    private fun take(key: String, capacity: Int, perMs: Double, scope: Scope, now: Long): Decision {
        val b = bucket(key, capacity, perMs, now)
        if (b.tokens >= 1.0) { b.tokens -= 1.0; return Decision(true) }
        return Decision(false, wait(b), scope)
    }

    /** Une nouvelle connexion (WebSocket ou session de repli) depuis cette clé d'adresse. Rien n'est consommé si un seau refuse. */
    @Synchronized fun admitConnection(ipKey: String): Decision {
        val now = clock()
        val c = config
        val g = bucket("g", c.connPerSecondGlobal, c.connPerSecondGlobal / 1_000.0, now)
        val m = bucket("m|$ipKey", c.connPerMinute, c.connPerMinute / 60_000.0, now)
        val h = bucket("h|$ipKey", c.connPerHour, c.connPerHour / 3_600_000.0, now)
        val p48 = group48(ipKey)?.let { bucket("p48|$it", c.connPerMinutePer48, c.connPerMinutePer48 / 60_000.0, now) }
        val denied = listOfNotNull(m to Scope.IP_MINUTE, h to Scope.IP_HOUR, p48?.let { it to Scope.PREFIX48 }, g to Scope.GLOBAL).firstOrNull { it.first.tokens < 1.0 }
        if (denied != null) return Decision(false, wait(denied.first), denied.second)
        g.tokens -= 1.0; m.tokens -= 1.0; h.tokens -= 1.0; p48?.let { it.tokens -= 1.0 }
        return Decision(true)
    }

    /** Une entrée en salle (`join`, `resume`) d'un appareil. */
    @Synchronized fun admitDevice(deviceHash: String): Decision =
        take("d|$deviceHash", config.joinPerMinutePerDevice, config.joinPerMinutePerDevice / 60_000.0, Scope.DEVICE, clock())

    /** Un message à destination d'une salle (tous clients confondus). */
    @Synchronized fun admitRoom(roomId: String): Decision =
        take("r|$roomId", config.roomBurst, config.roomMessagesPerSecond / 1_000.0, Scope.ROOM, clock())

    /** Rend la salle oubliable (salle fermée) : son seau part. */
    @Synchronized fun forgetRoom(roomId: String) { buckets.remove("r|$roomId") }

    @Synchronized fun size(): Int = buckets.size

    // ------------------------------------------------------------------ plafond de connexions OUVERTES par adresse

    private class Seat(val room: String, val device: String, val since: Long)
    private val seats = HashMap<String, ArrayDeque<Seat>>()   // clé d'adresse -> sièges de JOUEURS récents

    /**
     * Note qu'un JOUEUR (jamais un spectateur : n'importe qui en devient un avec un identifiant inventé) s'est assis dans une salle depuis cette adresse. Borné : 128 sièges par
     * adresse, 2 h. Un même (salle, appareil) ne compte qu'une fois.
     */
    @Synchronized fun noteSeat(ipKey: String, roomId: String, deviceHash: String) {
        val now = clock()
        val q = seats.getOrPut(ipKey) { ArrayDeque() }
        while (q.isNotEmpty() && now - q.first().since >= SEAT_MEMORY_MS) q.removeFirst()
        if (q.none { it.room == roomId && it.device == deviceHash }) q.addLast(Seat(roomId, deviceHash, now))
        while (q.size > MAX_SEATS_PER_IP) q.removeFirst()
        if (seats.size > maxKeys) seats.remove(seats.keys.first())
    }

    /** Le joueur est parti (connexion fermée) : son siège ne compte plus. */
    @Synchronized fun dropSeat(ipKey: String, roomId: String, deviceHash: String) {
        val q = seats[ipKey] ?: return
        q.removeAll { it.room == roomId && it.device == deviceHash }
        if (q.isEmpty()) seats.remove(ipKey)
    }

    /**
     * Appareils distincts assis (joueurs connectés depuis au moins [SEAT_MIN_AGE_MS]) dans la salle de cette adresse qui en compte le plus. Le plafond relevé ne se gagne donc
     * ni avec des spectateurs, ni avec des identifiants inventés à la volée, ni en une rafale de quelques secondes.
     */
    @Synchronized fun seatedDevices(ipKey: String): Int {
        val q = seats[ipKey] ?: return 0
        val now = clock()
        while (q.isNotEmpty() && now - q.first().since >= SEAT_MEMORY_MS) q.removeFirst()
        if (q.isEmpty()) { seats.remove(ipKey); return 0 }
        return q.filter { now - it.since >= SEAT_MIN_AGE_MS }.groupBy { it.room }.values.maxOfOrNull { room -> room.map { it.device }.toSet().size } ?: 0
    }

    /** Plafond de connexions ouvertes pour cette adresse : [Config.maxOpenPerIp] ; si au moins [Config.sharedDevices] appareils sont réellement assis, `maxOpenPerIp + appareils assis`, au plus [Config.maxOpenPerIpShared]. */
    @Synchronized fun openCeiling(ipKey: String): Int {
        val n = seatedDevices(ipKey)
        return if (n >= config.sharedDevices) minOf(config.maxOpenPerIpShared, config.maxOpenPerIp + n) else config.maxOpenPerIp
    }

    @Synchronized fun isShared(ipKey: String): Boolean = seatedDevices(ipKey) >= config.sharedDevices

    /** Préfixe /48 d'une clé IPv6 `v6:aaaa:bbbb:cccc:dddd::/64` (ses trois premiers groupes) ; null pour une IPv4. */
    private fun group48(key: String): String? = if (key.startsWith("v6:")) key.removePrefix("v6:").split(":").take(3).joinToString(":") else null

    companion object {
        const val MAX_KEYS = 100_000
        const val MAX_SEATS_PER_IP = 128
        const val SEAT_MEMORY_MS = 2 * 60 * 60_000L
        /** Un siège ne compte pour le plafond relevé qu'après 30 s de présence. */
        const val SEAT_MIN_AGE_MS = 30_000L
    }
}
