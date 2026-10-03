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
    /** Réseaux dont l'`X-Forwarded-For` est cru (dernier saut seulement) : boucle locale et réseaux Docker par défaut. */
    val trustedProxies: List<Cidr> = DEFAULT_TRUSTED.mapNotNull { Cidr.parse(it) },
    /** Clés publiques Ed25519 des tickets (Base64 : 32 octets bruts ou SPKI X.509). Vide = aucune salle ne peut être créée. */
    val ticketPubKeys: List<String> = emptyList(),
    val lotsDir: File? = null,
    val tickMs: Long = 200,
    val pingMs: Long = 25_000,
    val pongTimeoutMs: Long = 40_000,
    val ratePerSec: Int = castbridge.core.quiz.online.PlayProtocol.RATE_PER_SEC,
    val burst: Int = castbridge.core.quiz.online.PlayProtocol.BURST,
    val outboxMaxBytes: Int = 64 * 1024,
    val maxFrameBytes: Int = 8 * 1024,
    val pollMs: Long = 25_000,
    val fallbackIdleMs: Long = 40_000,
    val roomIdleMs: Long = 10 * 60_000L,
    val roomMaxMs: Long = 2 * 60 * 60_000L,
    val version: String = VERSION,
) {
    companion object {
        const val VERSION = "w20-03"
        val DEFAULT_TRUSTED = listOf("127.0.0.0/8", "::1/128", "172.16.0.0/12")
        /** Les SEULES variables d'environnement lues par le service. */
        val ENV_NAMES = listOf("CASTBRIDGE_PLAY_PORT", "CASTBRIDGE_PLAY_BIND", "CASTBRIDGE_PLAY_MAX_ROOMS", "CASTBRIDGE_PLAY_MAX_CONNECTIONS", "CASTBRIDGE_PLAY_MAX_PER_IP",
            "CASTBRIDGE_PLAY_ORIGINS", "CASTBRIDGE_PLAY_TRUSTED_PROXIES", "CASTBRIDGE_PLAY_LOTS_DIR", "CASTBRIDGE_PLAY_TICKET_PUBKEY", "CASTBRIDGE_PLAY_TICKET_PUBKEY_2",
            "CASTBRIDGE_PLAY_TICKET_PUBKEY_3")

        /** `args` accepte `--server.port=8090` et `--port=8090` (même sens). */
        fun fromEnv(env: (String) -> String? = System::getenv, args: Array<String> = emptyArray()): PlayConfig {
            fun e(k: String) = env(k)?.trim()?.takeIf { it.isNotEmpty() }
            fun arg(vararg names: String) = args.firstNotNullOfOrNull { a -> names.firstNotNullOfOrNull { n -> a.takeIf { it.startsWith("--$n=") }?.substringAfter('=') } }
            val d = PlayConfig()
            return PlayConfig(
                port = (arg("server.port", "port") ?: e("CASTBRIDGE_PLAY_PORT"))?.toIntOrNull() ?: d.port,
                bind = e("CASTBRIDGE_PLAY_BIND") ?: d.bind,
                maxRooms = e("CASTBRIDGE_PLAY_MAX_ROOMS")?.toIntOrNull() ?: d.maxRooms,
                maxConnections = e("CASTBRIDGE_PLAY_MAX_CONNECTIONS")?.toIntOrNull() ?: d.maxConnections,
                maxPerIp = e("CASTBRIDGE_PLAY_MAX_PER_IP")?.toIntOrNull() ?: d.maxPerIp,
                origins = e("CASTBRIDGE_PLAY_ORIGINS")?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet() ?: d.origins,
                trustedProxies = e("CASTBRIDGE_PLAY_TRUSTED_PROXIES")?.split(',')?.mapNotNull { Cidr.parse(it) } ?: d.trustedProxies,
                ticketPubKeys = listOf("CASTBRIDGE_PLAY_TICKET_PUBKEY", "CASTBRIDGE_PLAY_TICKET_PUBKEY_2", "CASTBRIDGE_PLAY_TICKET_PUBKEY_3").mapNotNull { e(it) },
                lotsDir = e("CASTBRIDGE_PLAY_LOTS_DIR")?.let { File(it) },
            )
        }
    }
}
