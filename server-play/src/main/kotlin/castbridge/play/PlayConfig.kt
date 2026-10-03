package castbridge.play

import java.io.File
import java.net.InetAddress

/** Réseau IP (`10.0.0.0/8`, `::1/128`) : sert à reconnaître le proxy de confiance. Pas de DNS : une adresse littérale seulement. */
class Cidr private constructor(private val net: ByteArray, private val bits: Int) {
    fun contains(a: InetAddress): Boolean {
        val b = a.address
        if (b.size != net.size) return false
        var left = bits
        for (i in b.indices) {
            if (left <= 0) break
            val mask = if (left >= 8) 0xff else (0xff shl (8 - left)) and 0xff
            if ((b[i].toInt() and mask) != (net[i].toInt() and mask)) return false
            left -= 8
        }
        return true
    }

    companion object {
        private val LITERAL = Regex("^[0-9a-fA-F:.]+$")
        fun literal(s: String): InetAddress? = if (LITERAL.matches(s) && (':' in s || s.count { it == '.' } == 3)) runCatching { InetAddress.getByName(s) }.getOrNull() else null
        fun parse(text: String): Cidr? {
            val parts = text.trim().split('/')
            val addr = literal(parts[0]) ?: return null
            val max = addr.address.size * 8
            val bits = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else max
            return if (bits in 0..max) Cidr(addr.address, bits) else null
        }
    }
}

/**
 * Réglages du service castbridge-play. Valeurs de production par défaut ; [fromEnv] lit les variables `CASTBRIDGE_PLAY_*` (la seule lecture
 * d'environnement du service : aucune clé privée, aucun jeton d'administration, aucun secret de licence : `NoSecretsTest`).
 */
class PlayConfig(
    val port: Int = 8080,
    val bind: String = "0.0.0.0",
    val maxRooms: Int = 400,
    val maxConnections: Int = 3_000,
    val maxPerIp: Int = 8,
    /** Origines autorisées pour la page de jeu (WebSocket, POST). */
    val origins: Set<String> = setOf("https://bridge.sti-cm.com"),
    /** Réseaux dont l'`X-Forwarded-For` est cru (dernier saut seulement). AUCUN par défaut (audit w20-03) : en production, l'adresse exacte de nginx en /32. */
    val trustedProxies: List<Cidr> = emptyList(),
    /** Clés publiques Ed25519 des tickets (Base64 : 32 octets bruts ou SPKI X.509). Vide = aucune salle ne peut être créée. */
    val ticketPubKeys: List<String> = emptyList(),
    val lotsDir: File? = null,
    /** Clés PUBLIQUES des émetteurs d'activations de confiance (`nom:clé:PORTÉES`, w20-04) : sans elles, aucune activation n'est valable, donc aucune salle. */
    val trustedKeys: List<String> = emptyList(),
    /** Paquets réservés (lecture seule) et gel des ids réservables ; absents = aucune question réservée servie. */
    val reservedDir: File? = null,
    val reservedIdsFile: File? = null,
    /** Adresse de la liste signée des révocations (lecture publique, https) ; absente = aucune relecture. */
    val revocationsUrl: String? = null,
    /** Salles ouvertes en même temps par appareil attesté (sujet du ticket). */
    val maxRoomsPerSubject: Int = 2,
    /** Créations de salle (tickets valides présentés) par adresse cliente (/64 en IPv6) et par heure. */
    val createsPerIpPerHour: Int = 20,
    /** `jti` mémorisés jusqu'à leur échéance (plafond dur, plein = refus). */
    val maxUsedTickets: Int = 200_000,
    /** Créations de salle par identité d'activation (code d'appareil signé) et par jour UTC, production et grâce (l'essai a son propre plafond : 3). */
    val createsPerIdentityPerDay: Int = 30,
    /** Créations de salle par /48 (IPv4 : l'adresse) et par heure : un /48 IPv6 contient 65 536 /64. */
    val createsPer48PerHour: Int = 200,
    /** Fichier (volume) où la dernière liste de révocations valide est gardée, relue au démarrage ; absent = pas de persistance. */
    val revocationsFile: File? = null,
    val tickMs: Long = 200,
    val pingMs: Long = 25_000,
    val pongTimeoutMs: Long = 40_000,
    val ratePerSec: Int = castbridge.core.quiz.online.PlayProtocol.RATE_PER_SEC,
    val burst: Int = castbridge.core.quiz.online.PlayProtocol.BURST,
    val outboxMaxBytes: Int = 64 * 1024,
    val maxFrameBytes: Int = 20 * 1024,   // `create` porte une activation (≤ 16 Ko, PlayProtocol.MAX_CREATE_BYTES)
    val pollMs: Long = 25_000,
    /** Échéance GLOBALE de lecture de la tête d'une requête (anti-goutte-à-goutte). */
    val headDeadlineMs: Long = 10_000,
    /** Une écriture WebSocket qui dure plus longtemps (client qui ne lit plus) coupe la connexion. */
    val writeTimeoutMs: Long = 10_000,
    val fallbackIdleMs: Long = 40_000,
    val roomIdleMs: Long = 10 * 60_000L,
    val roomMaxMs: Long = 2 * 60 * 60_000L,
    val version: String = VERSION,
) {
    companion object {
        const val VERSION = "w20-04"
        /** Les SEULES variables d'environnement lues par le service. */
        val ENV_NAMES = listOf("CASTBRIDGE_PLAY_PORT", "CASTBRIDGE_PLAY_BIND", "CASTBRIDGE_PLAY_MAX_ROOMS", "CASTBRIDGE_PLAY_MAX_CONNECTIONS", "CASTBRIDGE_PLAY_MAX_PER_IP",
            "CASTBRIDGE_PLAY_ORIGINS", "CASTBRIDGE_PLAY_TRUSTED_PROXIES", "CASTBRIDGE_PLAY_LOTS_DIR", "CASTBRIDGE_PLAY_TICKET_PUBKEY", "CASTBRIDGE_PLAY_TICKET_PUBKEY_2",
            "CASTBRIDGE_PLAY_TICKET_PUBKEY_3", "CASTBRIDGE_PLAY_TRUSTED_KEYS", "CASTBRIDGE_PLAY_RESERVED_DIR", "CASTBRIDGE_PLAY_RESERVED_IDS", "CASTBRIDGE_PLAY_REVOCATIONS_URL",
            "CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT", "CASTBRIDGE_PLAY_CREATES_PER_IP_HOUR", "CASTBRIDGE_PLAY_MAX_USED_TICKETS", "CASTBRIDGE_PLAY_DIRECT",
            "CASTBRIDGE_PLAY_CREATES_PER_IDENTITY_DAY", "CASTBRIDGE_PLAY_CREATES_PER_48_HOUR", "CASTBRIDGE_PLAY_REVOCATIONS_FILE")

        /**
         * Les réseaux de confiance sont OBLIGATOIRES (adresse exacte de nginx en /32) : absents, le service refuse de démarrer, sauf `CASTBRIDGE_PLAY_DIRECT=1` (staging, tests :
         * accès direct, aucun proxy). Une entrée invalide est une erreur, jamais ignorée en silence (même en accès direct).
         */
        internal fun parseTrusted(raw: String?, direct: Boolean): List<Cidr> {
            if (raw == null) {
                if (direct) {
                    System.err.println("ATTENTION : CASTBRIDGE_PLAY_DIRECT=1 : aucun proxy de confiance, accès direct (staging ou tests seulement, jamais en production)")
                    return emptyList()
                }
                throw IllegalStateException("CASTBRIDGE_PLAY_TRUSTED_PROXIES est absent : indiquer l'adresse exacte de nginx en /32 (ou CASTBRIDGE_PLAY_DIRECT=1 en staging)")
            }
            val parts = raw.split(',').map { it.trim() }
            val bad = parts.filter { Cidr.parse(it) == null }
            if (bad.isNotEmpty()) throw IllegalStateException("CASTBRIDGE_PLAY_TRUSTED_PROXIES contient une entrée invalide : « ${bad.first().take(40)} »")
            return parts.map { Cidr.parse(it)!! }
        }

        /** `args` accepte `--server.port=8090` et `--port=8090` (même sens). */
        fun fromEnv(env: (String) -> String? = System::getenv, args: Array<String> = emptyArray()): PlayConfig {
            fun e(k: String) = env(k)?.trim()?.takeIf { it.isNotEmpty() }
            fun arg(vararg names: String) = args.firstNotNullOfOrNull { a -> names.firstNotNullOfOrNull { n -> a.takeIf { it.startsWith("--$n=") }?.substringAfter('=') } }
            val d = PlayConfig()
            val direct = e("CASTBRIDGE_PLAY_DIRECT") == "1"
            val trusted = parseTrusted(e("CASTBRIDGE_PLAY_TRUSTED_PROXIES"), direct)
            // les révocations échouent FERMÉ : sans adresse de liste signée, aucune révocation ne serait jamais connue ; seul l'accès direct (staging, tests) s'en passe
            if (e("CASTBRIDGE_PLAY_REVOCATIONS_URL") == null && !direct) throw IllegalStateException("CASTBRIDGE_PLAY_REVOCATIONS_URL est absent : indiquer l'adresse https de la liste signée des révocations (ou CASTBRIDGE_PLAY_DIRECT=1 en staging)")
            return PlayConfig(
                port = (arg("server.port", "port") ?: e("CASTBRIDGE_PLAY_PORT"))?.toIntOrNull() ?: d.port,
                bind = e("CASTBRIDGE_PLAY_BIND") ?: d.bind,
                maxRooms = e("CASTBRIDGE_PLAY_MAX_ROOMS")?.toIntOrNull() ?: d.maxRooms,
                maxConnections = e("CASTBRIDGE_PLAY_MAX_CONNECTIONS")?.toIntOrNull() ?: d.maxConnections,
                maxPerIp = e("CASTBRIDGE_PLAY_MAX_PER_IP")?.toIntOrNull() ?: d.maxPerIp,
                origins = e("CASTBRIDGE_PLAY_ORIGINS")?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet() ?: d.origins,
                trustedProxies = trusted,
                ticketPubKeys = listOf("CASTBRIDGE_PLAY_TICKET_PUBKEY", "CASTBRIDGE_PLAY_TICKET_PUBKEY_2", "CASTBRIDGE_PLAY_TICKET_PUBKEY_3").mapNotNull { e(it) },
                lotsDir = e("CASTBRIDGE_PLAY_LOTS_DIR")?.let { File(it) },
                trustedKeys = e("CASTBRIDGE_PLAY_TRUSTED_KEYS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.trustedKeys,
                reservedDir = e("CASTBRIDGE_PLAY_RESERVED_DIR")?.let { File(it) },
                reservedIdsFile = e("CASTBRIDGE_PLAY_RESERVED_IDS")?.let { File(it) } ?: e("CASTBRIDGE_PLAY_RESERVED_DIR")?.let { File(it, "reserved-ids.json") },
                revocationsUrl = e("CASTBRIDGE_PLAY_REVOCATIONS_URL"),
                maxRoomsPerSubject = e("CASTBRIDGE_PLAY_MAX_ROOMS_PER_SUBJECT")?.toIntOrNull()?.coerceIn(1, 20) ?: d.maxRoomsPerSubject,
                createsPerIpPerHour = e("CASTBRIDGE_PLAY_CREATES_PER_IP_HOUR")?.toIntOrNull()?.coerceIn(1, 10_000) ?: d.createsPerIpPerHour,
                createsPerIdentityPerDay = e("CASTBRIDGE_PLAY_CREATES_PER_IDENTITY_DAY")?.toIntOrNull()?.coerceIn(1, 10_000) ?: d.createsPerIdentityPerDay,
                createsPer48PerHour = e("CASTBRIDGE_PLAY_CREATES_PER_48_HOUR")?.toIntOrNull()?.coerceIn(1, 100_000) ?: d.createsPer48PerHour,
                revocationsFile = e("CASTBRIDGE_PLAY_REVOCATIONS_FILE")?.let { File(it) },
                maxUsedTickets = e("CASTBRIDGE_PLAY_MAX_USED_TICKETS")?.toIntOrNull()?.coerceIn(100, 500_000) ?: d.maxUsedTickets,
            )
        }
    }
}
