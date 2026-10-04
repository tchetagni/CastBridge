package castbridge.core.wallet

import castbridge.core.owner.KeyRing
import castbridge.core.quiz.Json
import castbridge.core.update.Ed25519
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Les deux monnaies du portefeuille (docs : conception W22 § 1.1). */
enum class WalletCurrency { NDEM, MBOKO }

/**
 * Pourquoi une pièce signée est refusée (journal, tests, vecteurs communs Kotlin ↔ Java : les NOMS sont stables). Jamais montré tel quel au joueur : voir [WalletReason].
 * [shown] est le motif d'écran correspondant (null : défaut interne, l'écran dit « illisible » ou ne dit rien).
 */
enum class WalletRefusal(val shown: WalletReason?) {
    UNREADABLE(null), UNKNOWN_KEY(null), REVOKED_KEY(null), BAD_SIGNATURE(null), OTHER_TV(WalletReason.BOUND_OTHER_TV), OUT_OF_BOUNDS(null), AMOUNT_MISMATCH(null),
    WRONG_AUDIENCE(null), EXPIRED(null), NOT_YET_VALID(WalletReason.CLOCK), TOO_LONG_LIFE(null), BAD_TOTALS(null), CLOCK(WalletReason.CLOCK),
    VOUCHER_BAD(WalletReason.VOUCHER_BAD), VOUCHER_EXPIRED(WalletReason.VOUCHER_EXPIRED), VOUCHER_OTHER_TV(WalletReason.VOUCHER_OTHER_TV),
}

/** Résultat d'une vérification : la pièce lue, ou un motif de refus. Une pièce acceptée est AUTHENTIQUE (signée par une clé de l'anneau), jamais « crue sur parole ». */
sealed class Verdict<out T> {
    data class Accepted<T>(val value: T) : Verdict<T>()
    data class Rejected(val reason: WalletRefusal) : Verdict<Nothing>()
}

/**
 * Codec commun des pièces signées `cbw1`, `cbe1`, `cbr1` (même famille que le ticket `cbp1` : `<préfixe>.<b64url charge JSON>.<b64url signature Ed25519>`).
 * La signature porte sur `<domaine>\n<préfixe>.<charge b64url>` (ASCII) : une pièce ne vaut JAMAIS pour un autre format ni pour le ticket `cbp1` (séparation de domaine).
 * La charge est signée TELLE QU'ENVOYÉE ; la lecture est STRICTE : base64url canonique sans bourrage, UTF-8 valide, objet JSON dont la réécriture compacte est identique octet pour octet
 * (pas d'espace, pas de clé en double, pas de nombre décimal ou à zéros de tête), ensemble de clés EXACT (ni manquante ni en trop), types exacts. Les clés viennent d'un [KeyRing]
 * compilé : aucune confiance de clé n'est lue sur le réseau. Les anneaux sont SÉPARÉS par usage (portefeuille, résultat, bons) ; aucune [castbridge.core.owner.KeyScope] n'est partagée.
 */
object WalletFormats {
    const val MAX_AMOUNT = 1_000_000_000_000L
    const val SKEW_MS = 60_000L
    private val B64URL = Regex("^[A-Za-z0-9_-]+$")
    private val KID = Regex("^[0-9a-f]{16}$")

    /** Mot signé avec ses domaines : `<domaine>\n<préfixe>.<charge b64url>`. */
    internal fun signedText(domain: String, prefix: String, payloadB64: String): ByteArray = "$domain\n$prefix.$payloadB64".toByteArray(Charsets.US_ASCII)

    /** Construit la pièce `prefix.payload.signature` (signature Ed25519 de [sign] sur le mot signé). La clé privée n'est JAMAIS dans ce module : l'appelant passe une fonction. */
    internal fun seal(prefix: String, domain: String, payload: Map<String, Any?>, sign: (ByteArray) -> ByteArray): String {
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.write(payload).toByteArray(Charsets.UTF_8))
        return "$prefix.$b64." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(signedText(domain, prefix, b64)))
    }

    /** Une pièce ouverte et authentifiée : la clé [kid] a signé exactement cette charge. */
    internal class Opened(val kid: String, val body: Map<String, Any?>)

    internal class Bad(val reason: WalletRefusal) : RuntimeException(null, null, false, false)

    internal fun strictB64(s: String): ByteArray? {
        if (!B64URL.matches(s)) return null
        val raw = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull() ?: return null
        return raw.takeIf { Base64.getUrlEncoder().withoutPadding().encodeToString(it) == s }   // forme canonique : pas de bits de queue cachés
    }

    internal fun open(token: String?, prefix: String, domain: String, ring: KeyRing, maxLength: Int): Verdict<Opened> {
        if (token == null || token.length > maxLength) return Verdict.Rejected(WalletRefusal.UNREADABLE)
        val parts = token.split('.')
        if (parts.size != 3 || parts[0] != prefix) return Verdict.Rejected(WalletRefusal.UNREADABLE)
        val payload = strictB64(parts[1]) ?: return Verdict.Rejected(WalletRefusal.UNREADABLE)
        val sig = strictB64(parts[2])?.takeIf { it.size == 64 } ?: return Verdict.Rejected(WalletRefusal.UNREADABLE)
        val text = runCatching {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString()
        }.getOrNull() ?: return Verdict.Rejected(WalletRefusal.UNREADABLE)
        val body = runCatching { Json.parse(text) as? Map<*, *> }.getOrNull() ?: return Verdict.Rejected(WalletRefusal.UNREADABLE)
        if (Json.write(body) != text) return Verdict.Rejected(WalletRefusal.UNREADABLE)   // clé en double, espace, nombre non canonique : refus
        val kid = (body["kid"] as? String)?.takeIf { KID.matches(it) } ?: return Verdict.Rejected(WalletRefusal.UNREADABLE)
        val key = ring.find(kid) ?: return Verdict.Rejected(WalletRefusal.UNKNOWN_KEY)
        if (ring.isRevoked(kid)) return Verdict.Rejected(WalletRefusal.REVOKED_KEY)
        val pub = runCatching { Base64.getDecoder().decode(key.publicKeyBase64.trim()) }.getOrNull() ?: return Verdict.Rejected(WalletRefusal.UNKNOWN_KEY)
        if (!Ed25519.verify(pub, signedText(domain, prefix, parts[1]), sig)) return Verdict.Rejected(WalletRefusal.BAD_SIGNATURE)
        @Suppress("UNCHECKED_CAST")
        return Verdict.Accepted(Opened(kid, body as Map<String, Any?>))
    }

    /** Lecteur de champs strict : l'ensemble des clés doit être EXACTEMENT [keys]. */
    internal class Fields(private val m: Map<String, Any?>, keys: Set<String>) {
        init { if (m.keys != keys) throw Bad(WalletRefusal.UNREADABLE) }
        fun str(k: String, re: Regex): String = (m[k] as? String)?.takeIf { re.matches(it) } ?: throw Bad(WalletRefusal.UNREADABLE)
        fun long(k: String): Long = m[k] as? Long ?: throw Bad(WalletRefusal.UNREADABLE)
        fun bool(k: String): Boolean = m[k] as? Boolean ?: throw Bad(WalletRefusal.UNREADABLE)
        fun amount(k: String, min: Long = 0): Long = long(k).also { if (it < min || it > MAX_AMOUNT) throw Bad(WalletRefusal.OUT_OF_BOUNDS) }
        /** Instant (ms) ou numéro de séquence : ≥ 0, borné (10¹⁵) pour que les sommes ne débordent jamais. */
        fun nonNeg(k: String): Long = long(k).also { if (it < 0 || it > 1_000_000_000_000_000L) throw Bad(WalletRefusal.OUT_OF_BOUNDS) }
        fun cur(k: String): WalletCurrency = WalletCurrency.values().firstOrNull { it.name == m[k] } ?: throw Bad(WalletRefusal.UNREADABLE)
        fun obj(k: String, keys: Set<String>): Fields { @Suppress("UNCHECKED_CAST") return Fields(m[k] as? Map<String, Any?> ?: throw Bad(WalletRefusal.UNREADABLE), keys) }
        fun list(k: String): List<*> = m[k] as? List<*> ?: throw Bad(WalletRefusal.UNREADABLE)
    }

    internal inline fun <T> guard(block: () -> T): Verdict<T> = try { Verdict.Accepted(block()) } catch (b: Bad) { Verdict.Rejected(b.reason) }

    internal val ID = Regex("^[A-Za-z0-9._:-]{1,64}$")
    internal val EID = Regex("^[A-Za-z0-9_-]{22}$")
    internal val HEX32 = Regex("^[0-9a-f]{32}$")
}
