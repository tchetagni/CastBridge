package castbridge.core.tokens

import castbridge.core.owner.*
import java.security.MessageDigest
import java.util.Base64

/**
 * Bon de jetons : corps de l'enveloppe `cbx1` de type `tokens` (docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md § 3.4 c). Signé par le serveur (portée `ISSUE_PRODUCTION`), lié à la TV
 * (cible `device`) ET à son installation ([installPub]) : une réinstallation ne peut pas le rejouer. [grant] croît par (licence, appareil) : un bon ne se crédite qu'une fois.
 */
class TokenGrant(val license: String, val grant: Long, val amount: Long, val installPub: ByteArray, val expiry: Long, val fresh: Boolean = false) {
    /** 16 hex = les 8 premiers octets de SHA-256(installPub) : même formule que `InstallKey.installId`. */
    val installId: String get() = installIdOf(installPub)

    /**
     * Corps canonique, dans l'ordre fixe : license, grant, amount, install, expiry, fresh. [fresh] = « bon d'ouverture » (D-W5-J1) : seul un bon `fresh=1` ouvre un porte-jetons vide ou illisible,
     * seul un bon `fresh=0` est crédité sur un porte-jetons en état OK.
     */
    fun body(): List<String> = listOf("license=$license", "grant=$grant", "amount=$amount", "install=${hex(installPub)}", "expiry=$expiry", "fresh=${if (fresh) 1 else 0}")

    override fun equals(other: Any?) = other is TokenGrant && other.license == license && other.grant == grant && other.amount == amount && other.installPub.contentEquals(installPub) && other.expiry == expiry && other.fresh == fresh
    override fun hashCode() = license.hashCode() * 31 + grant.hashCode()

    companion object {
        const val TYPE = "tokens"
        const val AMOUNT_MAX = 10_000L
        /** Fenêtre d'installation maximale d'un bon ordinaire (D-W5-J3) : 72 h. */
        const val WINDOW_MAX_MS = 72L * 3600 * 1000
        /** Fenêtre d'installation maximale d'un bon d'ouverture (`fresh=1`) : 48 h. */
        const val OPENER_WINDOW_MAX_MS = 48L * 3600 * 1000
        private val FRESH = Regex("^fresh=[01]$")

        /** La fenêtre maximale (ms) d'un bon selon sa nature. */
        fun windowMax(fresh: Boolean): Long = if (fresh) OPENER_WINDOW_MAX_MS else WINDOW_MAX_MS
        private val INSTALL_HEX = Regex("^[0-9a-f]{64}$")

        /** Le bon du corps [lines], ou null si le texte n'est pas EXACTEMENT le corps canonique (ordre, chiffres sans signe ni zéro de tête). */
        fun parseBody(lines: List<String>): TokenGrant? = runCatching {
            require(lines.size == 6)
            fun v(i: Int, k: String) = lines[i].also { require(it.startsWith("$k=")) }.substringAfter('=')
            val license = v(0, "license").also { require(Envelope.ID.matches(it)) }
            val installHex = v(3, "install").also { require(INSTALL_HEX.matches(it)) }
            require(FRESH.matches(lines[5]))
            val g = TokenGrant(license, v(1, "grant").toLong(), v(2, "amount").toLong(), unhex(installHex), v(4, "expiry").toLong(), v(5, "fresh") == "1")
            require(g.body() == lines)
            g
        }.getOrNull()

        /** Le jeton signé d'un bon (outils d'émission, tests, miroir serveur). */
        fun issue(signer: Signer, seq: Long, nonce: String, issuedAt: Long, notBefore: Long, expiresAt: Long, target: Envelope.Target.Device, grant: TokenGrant): String {
            require(Envelope.HEX.matches(nonce) && expiresAt > notBefore) { "fenêtre ou nonce invalide" }
            require(expiresAt <= issuedAt + windowMax(grant.fresh)) { "fenêtre d'installation trop longue (72 h, 48 h pour un bon d'ouverture)" }
            require(grant.amount in 1..AMOUNT_MAX && grant.grant >= 1) { "bon hors bornes" }
            val unsigned = Envelope(TYPE, signer.keyId, seq, nonce, issuedAt, notBefore, expiresAt, target, grant.body(), "")
            return unsigned.withSignature(Base64.getEncoder().encodeToString(signer.sign(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8)))).encode()
        }

        /** Empreinte courte (16 hex) d'un jeton, inscrite au porte-jetons à côté du crédit. */
        fun fingerprint(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.trim().toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

        /**
         * Vérifie [token] dans l'ordre : MALFORMED, UNKNOWN_TYPE, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, KEY_NOT_ALLOWED, BAD_GRANT, WRONG_TARGET, STALE_SEQUENCE, NOT_YET_VALID, WINDOW_CLOSED.
         * [nowMs] doit être `TvClock.now` (l'horloge d'une TV hors ligne n'est pas fiable). Ne crédite rien : c'est `TokenWallet.credit` qui le fait.
         */
        fun verify(token: String, ring: KeyRing, revocations: RevocationState, device: Fingerprints, installPub: ByteArray, lastGrant: Long, nowMs: Long, skewMs: Long = 24L * 3600 * 1000): TokenGrantResult {
            val env = Envelope.decode(token) ?: return no(TokenRejection.MALFORMED, "Bon de jetons illisible")
            if (env.type != TYPE) return no(TokenRejection.UNKNOWN_TYPE, "Ce message n'est pas un bon de jetons")
            val key = ring.find(env.keyId) ?: return no(TokenRejection.UNKNOWN_KEY, "Clé inconnue")
            if (ring.isRevoked(env.keyId) || env.keyId in revocations.keys) return no(TokenRejection.REVOKED_KEY, "Clé révoquée")
            if (!key.verify(env.canonicalPayload(), env.signature)) return no(TokenRejection.BAD_SIGNATURE, "Signature invalide")
            if (!key.allows(KeyScope.ISSUE_PRODUCTION)) return no(TokenRejection.KEY_NOT_ALLOWED, "Cette clé ne peut pas émettre de jetons")
            val g = parseBody(env.body)?.takeIf { it.amount in 1..AMOUNT_MAX && it.grant >= 1 && it.expiry >= 0 } ?: return no(TokenRejection.BAD_GRANT, "Bon de jetons hors schéma")
            if (!g.installPub.contentEquals(installPub)) return no(TokenRejection.BAD_GRANT, "Bon destiné à une autre installation de CastBridge-TV")
            if (env.expiresAt - env.issuedAt > windowMax(g.fresh)) return no(TokenRejection.BAD_GRANT, "Fenêtre d'installation trop longue")
            val t = env.target as? Envelope.Target.Device ?: return no(TokenRejection.WRONG_TARGET, "Bon destiné à un autre appareil")
            if (!DeviceIdentity.matches(t.factors, t.k, device)) return no(TokenRejection.WRONG_TARGET, "Bon destiné à un autre appareil")
            if (g.grant <= lastGrant) return no(TokenRejection.STALE_SEQUENCE, "Bon déjà crédité ou dépassé")
            if (nowMs + skewMs < env.notBefore) return no(TokenRejection.NOT_YET_VALID, "Bon pas encore valable")
            if (nowMs > env.expiresAt || (g.expiry != 0L && nowMs > g.expiry)) return no(TokenRejection.WINDOW_CLOSED, "Bon expiré")
            return TokenGrantResult.Accepted(g, env, fingerprint(token))
        }

        private fun no(r: TokenRejection, m: String) = TokenGrantResult.Rejected(r, m)
        fun installIdOf(pub: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(pub).take(8).joinToString("") { "%02x".format(it) }
        internal fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        internal fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/** Motifs de refus d'un bon, dans l'ordre de vérification. */
enum class TokenRejection { MALFORMED, UNKNOWN_TYPE, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, KEY_NOT_ALLOWED, BAD_GRANT, WRONG_TARGET, STALE_SEQUENCE, NOT_YET_VALID, WINDOW_CLOSED }

/** Résultat de [TokenGrant.verify]. */
sealed class TokenGrantResult {
    class Accepted(val grant: TokenGrant, val envelope: Envelope, val fingerprint: String) : TokenGrantResult()
    class Rejected(val reason: TokenRejection, val message: String) : TokenGrantResult()
}
