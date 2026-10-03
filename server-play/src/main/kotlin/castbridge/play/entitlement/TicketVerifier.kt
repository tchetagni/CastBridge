package castbridge.play.entitlement

import castbridge.core.quiz.Json
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Vérifie le ticket d'ouverture de salle `cbp1.<charge utile base64url>.<signature base64url>` émis par l'API principale (`POST /api/v1/play/ticket`).
 *
 * - Signature Ed25519 sur `castbridge-play-ticket-v1\n` + `cbp1.<charge utile>` (séparation de domaine : la signature d'un ticket ne vaut pour aucun autre message signé).
 * - Charge utile JSON : `aud` (= [AUDIENCE]), `deviceId` (appareil attesté par l'API), `blocked`, `country`, `deviceCode` (code d'appareil annoncé par la TV, lié à son
 *   activation par [HostRightsEvaluator]), `iat`/`exp` en ms, `jti` (identifiant aléatoire de 128 bits en hexadécimal). Aucune édition, aucun droit.
 * - Le service n'a QUE les clés PUBLIQUES (`CASTBRIDGE_PLAY_TICKET_PUBKEY*`). Sans clé configurée, aucun ticket n'est valide.
 * - [check] est SANS ÉTAT (contrôle d'origine, lecture) ; l'usage unique est [UsedTickets], pris à la création d'une salle.
 */
class TicketVerifier(pubKeys: List<String>, private val audience: String = AUDIENCE) {
    private val keys: List<PublicKey> = pubKeys.mapNotNull { parse(it) }
    val configured: Boolean get() = keys.isNotEmpty()

    /** Pourquoi un ticket est refusé (journal et tests ; jamais montré tel quel au client : `PLAY_TICKET_REFUSED`). */
    enum class Refusal { NO_KEY, MALFORMED, BAD_SIGNATURE, WRONG_AUDIENCE, BLOCKED, EXPIRED, NOT_YET_VALID, TOO_LONG }

    /** L'attestation d'un ticket valide. `toString` ne montre rien (jamais de jti ni d'appareil dans un journal). */
    class Ticket(val deviceId: String, val deviceCode: String?, val country: String?, val iat: Long, val exp: Long, val jti: String) {
        override fun toString() = "Ticket(***)"
    }

    sealed class Result {
        class Ok(val ticket: Ticket) : Result()
        class Refused(val why: Refusal) : Result()
    }

    fun check(ticket: String?, nowMs: Long): Result {
        if (keys.isEmpty()) return Result.Refused(Refusal.NO_KEY)
        if (ticket == null || ticket.length > MAX_LENGTH) return Result.Refused(Refusal.MALFORMED)
        val parts = ticket.split('.')
        if (parts.size != 3 || parts[0] != PREFIX) return Result.Refused(Refusal.MALFORMED)
        val dec = Base64.getUrlDecoder()
        if (!B64URL.matches(parts[1]) || !B64URL.matches(parts[2])) return Result.Refused(Refusal.MALFORMED)   // alphabet base64url strict, sans bourrage ni espace
        val sig = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return Result.Refused(Refusal.MALFORMED)
        val signed = (DOMAIN + parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII)
        if (keys.none { k -> runCatching { Signature.getInstance("Ed25519").run { initVerify(k); update(signed); verify(sig) } }.getOrDefault(false) }) return Result.Refused(Refusal.BAD_SIGNATURE)
        val body = runCatching { Json.parse(String(dec.decode(parts[1]), Charsets.UTF_8)) as? Map<*, *> }.getOrNull() ?: return Result.Refused(Refusal.MALFORMED)
        if (body["aud"] != audience) return Result.Refused(Refusal.WRONG_AUDIENCE)
        val deviceId = (body["deviceId"] as? String)?.takeIf { it.length in 1..64 && it.none { c -> c.isISOControl() } } ?: return Result.Refused(Refusal.MALFORMED)
        val jti = (body["jti"] as? String)?.takeIf { JTI.matches(it) } ?: return Result.Refused(Refusal.MALFORMED)
        val iat = (body["iat"] as? Number)?.toLong() ?: return Result.Refused(Refusal.MALFORMED)
        val exp = (body["exp"] as? Number)?.toLong() ?: return Result.Refused(Refusal.MALFORMED)
        val code = (body["deviceCode"] as? String)?.takeIf { it.length <= 24 && it.none { c -> c.isISOControl() } }
        val country = (body["country"] as? String)?.takeIf { it.length <= 8 }
        if (body["blocked"] != false) return Result.Refused(Refusal.BLOCKED)           // absent ou autre que « false » = refusé (fermé)
        if (exp - iat > MAX_LIFE_MS || exp <= iat) return Result.Refused(Refusal.TOO_LONG)
        if (nowMs >= exp) return Result.Refused(Refusal.EXPIRED)
        if (iat > nowMs + SKEW_MS) return Result.Refused(Refusal.NOT_YET_VALID)
        return Result.Ok(Ticket(deviceId, code, country, iat, exp, jti))
    }

    fun verify(ticket: String?, nowMs: Long): Boolean = check(ticket, nowMs) is Result.Ok

    private fun parse(text: String): PublicKey? = runCatching {
        val raw = Base64.getDecoder().decode(text.trim().replace('-', '+').replace('_', '/'))   // STRICT : un caractère étranger ou un saut de ligne au milieu est une erreur
        val spki = if (raw.size == 32) SPKI_PREFIX + raw else raw
        KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
    }.getOrNull()

    companion object {
        const val PREFIX = "cbp1"
        const val AUDIENCE = "castbridge-play"
        const val DOMAIN = "castbridge-play-ticket-v1\n"
        const val MAX_LENGTH = 1_200
        const val SKEW_MS = 60_000L
        const val MAX_LIFE_MS = 15 * 60_000L
        private val B64URL = Regex("^[A-Za-z0-9_-]+$")
        private val JTI = Regex("^[0-9a-f]{32}$")
        private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    }
}

/**
 * Les `jti` déjà utilisés, jusqu'à leur `exp` (un ticket échu est de toute façon refusé par [TicketVerifier.check]). Mémoire BORNÉE : au plus [cap] entrées ;
 * plein ⇒ balayage des échus, puis REFUS ([Use.FULL]) : on n'évince JAMAIS un `jti` encore valable (le rejeu redeviendrait possible). Seuls des tickets à signature
 * valide arrivent ici, et l'API en émet au plus 20 par appareil et par heure : le plafond ne se remplit pas par un flot anonyme.
 */
class UsedTickets(private val cap: Int = 20_000) {
    enum class Use { OK, REPLAY, FULL }
    private val used = HashMap<String, Long>()

    @Synchronized fun use(jti: String, exp: Long, now: Long): Use {
        if (used.size >= cap) sweepLocked(now)
        val seen = used[jti]
        if (seen != null && seen > now) return Use.REPLAY
        if (seen == null && used.size >= cap) return Use.FULL
        used[jti] = exp
        return Use.OK
    }

    @Synchronized fun sweep(now: Long) = sweepLocked(now)
    @Synchronized fun size(): Int = used.size
    private fun sweepLocked(now: Long) { used.values.removeIf { it <= now } }
}

/** Fenêtre glissante par clé (adresse cliente, /64 en IPv6) : au plus [max] événements par [windowMs] ; au plus [cap] clés (plafond dur, balayage). */
class RateWindow(private val max: Int, private val windowMs: Long, private val cap: Int = 50_000) {
    private val events = HashMap<String, ArrayDeque<Long>>()

    /** Compte un événement ; faux = plafond atteint (rien n'est compté en plus). */
    @Synchronized fun allow(key: String, now: Long): Boolean {
        if (events.size >= cap && key !in events) { sweepLocked(now); if (events.size >= cap) return false }
        val q = events.getOrPut(key) { ArrayDeque() }
        while (q.isNotEmpty() && now - q.first() >= windowMs) q.removeFirst()
        if (q.size >= max) return false
        q.addLast(now)
        return true
    }

    @Synchronized fun sweep(now: Long) = sweepLocked(now)
    @Synchronized fun size(): Int = events.size
    private fun sweepLocked(now: Long) { events.values.forEach { q -> while (q.isNotEmpty() && now - q.first() >= windowMs) q.removeFirst() }; events.values.removeIf { it.isEmpty() } }
}

/** Parties ouvertes aujourd'hui (jour UTC) par clé (essai : la clé est l'activation signée) ; au plus [cap] clés, jour échu = oublié. */
class DayCounter(private val cap: Int = 50_000) {
    private val counts = HashMap<String, Int>()
    private var day = Long.MIN_VALUE

    private fun roll(now: Long) { val d = Math.floorDiv(now, 86_400_000L); if (d != day) { counts.clear(); day = d } }

    @Synchronized fun count(key: String, now: Long): Int { roll(now); return counts[key] ?: 0 }

    /** Compte une partie de plus ; faux = table pleine (refus : l'essai échoue fermé). */
    @Synchronized fun record(key: String, now: Long): Boolean {
        roll(now)
        if (key !in counts && counts.size >= cap) return false
        counts[key] = (counts[key] ?: 0) + 1
        return true
    }

    @Synchronized fun size(): Int = counts.size
}
