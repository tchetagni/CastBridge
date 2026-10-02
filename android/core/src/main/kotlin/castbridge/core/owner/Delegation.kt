package castbridge.core.owner

import java.util.Base64

/** Why a [Delegation] is refused, in the order the checks run (docs/coordination/DESIGN-W4-VENTE-TERRAIN.md § 3). */
enum class DelegationRefusal {
    MALFORMED, UNKNOWN_TYPE, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, KEY_NOT_ALLOWED, BAD_DELEGATION, STALE_SEQUENCE, NOT_YET_VALID, WINDOW_CLOSED
}

sealed class DelegationResult {
    data class Accepted(val delegation: Delegation) : DelegationResult()
    data class Refused(val reason: DelegationRefusal, val message: String) : DelegationResult()
}

/**
 * A mandate signed by the owner (envelope `type=delegation`, scope [KeyScope.DELEGATE]): it lets the key of a field agent ("point focal") issue bounded production and trial keys and sell
 * vouchers, never anything else. The agent signs ordinary activations with its own key; the TV learns that key from the delegation ([TicketedActivation], [DelegatedVerifier]).
 * Rentals are managed online by the server (decisions P1/P2 of wave 5): [maxRentalDays] is always 0 and the delegation carries no master key.
 *
 * Canonical body (fixed order, optional lines omitted): `agent`, `pub`, `name`, `scopes` (sorted), `maxKeyDays`, `maxRentalDays`, `maxSales`, `bundles` (sorted, or `tout`),
 * then `confirmOrders=1`, `sellVouchers=1`, `maxConfirmXafPerDay=<n>`.
 *
 * Two delegations of the same agent: see [newer] (the most recent one wins, whoever signed it).
 */
data class Delegation(
    /** Kid of the owner key that signed (envelope `kid`). */ val ownerKid: String, val seq: Long, val nonce: String, val issuedAt: Long, val notBefore: Long, val expiresAt: Long,
    /** Kid of the agent key (16 hex, = [KeyRing.idOf] of [agentPub]). */ val agent: String, /** Raw Ed25519 public key of the agent, Base64. */ val agentPub: String, val name: String,
    val scopes: Set<KeyScope>, val maxKeyDays: Int, val maxRentalDays: Int, val maxSales: Int, val bundles: List<String>,
    val confirmOrders: Boolean = false, val sellVouchers: Boolean = false, val maxConfirmXafPerDay: Int? = null, val signature: String = "",
) {
    fun toEnvelope(): Envelope = Envelope(TYPE, ownerKid, seq, nonce, issuedAt, notBefore, expiresAt, Envelope.Target.Any, body(), signature)
    fun canonicalPayload(): String = toEnvelope().canonicalPayload()
    fun encode(): String = toEnvelope().encode()

    /** The key the TV and the tools trust for [agent] while the mandate lasts: scopes of the delegation, events only inside `notBefore..expiresAt`. */
    fun agentKey() = TrustedKey(agent, agentPub, scopes, notBefore..expiresAt)

    private fun body(): List<String> = buildList {
        add("agent=$agent"); add("pub=$agentPub"); add("name=$name"); add("scopes=${scopes.map { it.name }.sorted().joinToString(",")}")
        add("maxKeyDays=$maxKeyDays"); add("maxRentalDays=$maxRentalDays"); add("maxSales=$maxSales"); add("bundles=${bundles.joinToString(",")}")
        if (confirmOrders) add("confirmOrders=1")
        if (sellVouchers) add("sellVouchers=1")
        maxConfirmXafPerDay?.let { add("maxConfirmXafPerDay=$it") }
    }

    companion object {
        const val TYPE = "delegation"
        const val ALL_BUNDLES = "tout"
        /** The only scopes an agent may hold: never TRANSFER, REVOKE, REGISTRY, COMMAND_*, SUPER_UNLIMITED, POLICY, DELEGATE. */
        val ALLOWED_SCOPES: Set<KeyScope> = setOf(KeyScope.ISSUE_TRIAL, KeyScope.ISSUE_PRODUCTION)
        const val MAX_VALIDITY_DAYS = 180
        const val DEFAULT_VALIDITY_DAYS = 90
        const val MAX_KEY_DAYS = 3660
        const val MAX_SALES = 10_000
        const val MAX_CONFIRM_XAF_PER_DAY = 100_000_000
        val NAME = Regex("^[a-z0-9-]{1,32}$")
        private const val DAY = 24L * 3600 * 1000
        private const val SKEW_MS = 24L * 3600 * 1000

        /** What is wrong with the bounds of [d] (French, for the screen), or null. Checked by [issue] and by [verify]. */
        fun problem(d: Delegation): String? = when {
            !NAME.matches(d.name) -> "nom du point focal : 1 à 32 caractères parmi a-z, 0-9 et -"
            d.scopes.isEmpty() -> "aucune portée"
            !ALLOWED_SCOPES.containsAll(d.scopes) -> "portées hors du sous-ensemble autorisé"
            d.maxKeyDays !in 1..MAX_KEY_DAYS -> "durée maximale d'une clé hors bornes (1 à $MAX_KEY_DAYS jours)"
            d.maxRentalDays != 0 -> "les locations sont gérées en ligne : durée de location autorisée = 0"
            d.maxSales !in 1..MAX_SALES -> "quota de ventes hors bornes (1 à $MAX_SALES)"
            d.bundles.isEmpty() || d.bundles != d.bundles.distinct().sorted() || (ALL_BUNDLES in d.bundles && d.bundles.size > 1) || !d.bundles.all { Envelope.ID.matches(it) } -> "liste de bouquets invalide"
            d.maxConfirmXafPerDay != null && (!d.confirmOrders || d.maxConfirmXafPerDay !in 1..MAX_CONFIRM_XAF_PER_DAY) -> "plafond de confirmation sans confirmation de commandes, ou hors bornes"
            d.expiresAt <= d.notBefore || d.expiresAt - d.notBefore > MAX_VALIDITY_DAYS * DAY -> "validité du mandat : $MAX_VALIDITY_DAYS jours au plus"
            d.agent.length != 16 || !Envelope.HEX.matches(d.agent) || !pubOk(d.agentPub) || KeyRing.idOf(d.agentPub) != d.agent -> "clé du point focal incohérente"
            d.agent == d.ownerKid -> "le point focal ne peut pas être la clé du propriétaire"
            else -> null
        }

        private fun pubOk(b64: String) = runCatching { Base64.getDecoder().decode(b64).size == 32 }.getOrDefault(false)

        /**
         * Signs a delegation for the agent key [agentPublicKeyBase64]. [notBefore] defaults to [at]; the mandate lasts [validityDays] (1 to 180, 90 by default). Refuses out-of-bounds input.
         * [scopes] are only those of [ALLOWED_SCOPES]; the signer is expected to hold [KeyScope.DELEGATE] (the TV checks it). Returns the `cbx1` token.
         */
        fun issue(signer: Signer, at: Long, seq: Long, nonce: String, agentPublicKeyBase64: String, name: String, maxKeyDays: Int, maxSales: Int, bundles: List<String>,
                  scopes: Set<KeyScope> = ALLOWED_SCOPES, validityDays: Int = DEFAULT_VALIDITY_DAYS, notBefore: Long = at,
                  confirmOrders: Boolean = false, sellVouchers: Boolean = false, maxConfirmXafPerDay: Int? = null): String {
            if (!Envelope.HEX.matches(nonce)) throw IssueException("Nonce invalide")
            if (at <= 0 || notBefore <= 0 || seq < 0) throw IssueException("Date ou numéro de séquence invalide")
            val pub = agentPublicKeyBase64.trim()
            val agent = try { KeyRing.idOf(pub) } catch (e: IllegalArgumentException) { throw IssueException("Clé publique du point focal illisible") }
            val d = Delegation(signer.keyId, seq, nonce, at, notBefore, notBefore + validityDays * DAY, agent, pub, name, scopes,
                maxKeyDays, 0, maxSales, bundles.sorted(), confirmOrders, sellVouchers, maxConfirmXafPerDay)
            problem(d)?.let { throw IssueException("Délégation refusée : $it") }
            return sign(signer, d)
        }

        /** Signs [d] without any bound check (the vector generator builds invalid mandates with it; tools use [issue]). */
        internal fun sign(signer: Signer, d: Delegation): String {
            val unsigned = d.copy(signature = "")
            return unsigned.copy(signature = Base64.getEncoder().encodeToString(signer.sign(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8)))).encode()
        }

        fun decode(token: String): Delegation? = Envelope.decode(token)?.let(::from)

        /** The delegation view of an envelope of type `delegation`, or null (wrong type or target, body not canonical). */
        fun from(e: Envelope): Delegation? = runCatching {
            require(e.type == TYPE && e.target == Envelope.Target.Any)
            val fields = e.body.map { require('=' in it); it.substringBefore('=') to it.substringAfter('=') }
            fun at(i: Int, k: String) = fields[i].also { require(it.first == k) }.second
            val scopes = at(3, "scopes").split(',').map { KeyScope.valueOf(it) }
            var confirm = false; var vouchers = false; var cap: Int? = null
            for ((k, v) in fields.drop(8)) when (k) {
                "confirmOrders" -> { require(v == "1"); confirm = true }
                "sellVouchers" -> { require(v == "1"); vouchers = true }
                "maxConfirmXafPerDay" -> cap = v.toInt()
                else -> error("champ inconnu")
            }
            Delegation(e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, at(0, "agent"), at(1, "pub"), at(2, "name"), scopes.toSet(),
                at(4, "maxKeyDays").toInt(), at(5, "maxRentalDays").toInt(), at(6, "maxSales").toInt(), at(7, "bundles").split(','), confirm, vouchers, cap, e.signature)
                .also { require(it.canonicalPayload() == e.canonicalPayload()) }
        }.getOrNull()

        /**
         * Of two delegations of the same agent, the one that replaces the other on a TV: same owner key: the higher [seq]; two owner keys: the most recent [issuedAt]
         * (then the higher seq). Equal: [a].
         */
        fun newer(a: Delegation, b: Delegation): Delegation = when {
            a.ownerKid == b.ownerKid -> if (b.seq > a.seq) b else a
            b.issuedAt != a.issuedAt -> if (b.issuedAt > a.issuedAt) b else a
            else -> if (b.seq > a.seq) b else a
        }

        /**
         * The agent keys to add to a REPLAY ring ([KeyRing.withDelegated], [LicenseBook.replay]): every delegation of [tokens] that was valid when it was issued (checked at its own `issuedAt`),
         * so that an expired mandate keeps its past events (each key carries its `notBefore..expiresAt` window). Revoked keys stay revoked through [ring].
         */
        fun replayKeys(tokens: List<String>, ring: KeyRing): List<TrustedKey> = tokens.mapNotNull { t ->
            val d = decode(t) ?: return@mapNotNull null
            (verify(t, ring, RevocationState(), d.issuedAt) as? DelegationResult.Accepted)?.delegation?.agentKey()
        }

        /**
         * Verifies [token] against the TV's [ring] (owner keys, with [KeyScope.DELEGATE]) and [revocations]. [nowMs] is the TV time; a signed message proves time reached its `issuedAt`.
         * [seqState] (optional) holds the highest `seq` accepted per owner key: an older one is refused and an accepted one is recorded.
         */
        fun verify(token: String, ring: KeyRing, revocations: RevocationState, nowMs: Long, seqState: SeqState? = null): DelegationResult {
            fun no(r: DelegationRefusal, m: String) = DelegationResult.Refused(r, m)
            val env = Envelope.decode(token) ?: return no(DelegationRefusal.MALFORMED, "Mandat illisible")
            if (env.type != TYPE) return no(DelegationRefusal.UNKNOWN_TYPE, "Ce message n'est pas un mandat de point focal")
            val d = from(env) ?: return no(DelegationRefusal.MALFORMED, "Mandat illisible")
            val key = ring.find(d.ownerKid) ?: return no(DelegationRefusal.UNKNOWN_KEY, "Mandat signé par une clé inconnue de cet appareil")
            if (ring.isRevoked(d.ownerKid) || d.ownerKid in revocations.keys) return no(DelegationRefusal.REVOKED_KEY, "Mandat signé par une clé révoquée")
            if (ring.isRevoked(d.agent) || d.agent in revocations.keys) return no(DelegationRefusal.REVOKED_KEY, "Ce point focal a été révoqué")
            if (!key.verify(d.canonicalPayload(), d.signature)) return no(DelegationRefusal.BAD_SIGNATURE, "Signature du mandat invalide")
            if (!key.allows(KeyScope.DELEGATE)) return no(DelegationRefusal.KEY_NOT_ALLOWED, "Cette clé n'a pas le droit de déléguer")
            if (!ALLOWED_SCOPES.containsAll(d.scopes)) return no(DelegationRefusal.KEY_NOT_ALLOWED, "Le mandat accorde des droits qu'un point focal ne peut pas avoir")
            problem(d)?.let { return no(DelegationRefusal.BAD_DELEGATION, "Mandat invalide : $it") }
            if (ring.find(d.agent) != null) return no(DelegationRefusal.BAD_DELEGATION, "Mandat invalide : la clé du point focal est déjà une clé de cet appareil")
            if (seqState != null && d.seq < seqState.last(d.ownerKid)) return no(DelegationRefusal.STALE_SEQUENCE, "Mandat plus ancien que celui déjà installé")
            val now = maxOf(nowMs, d.issuedAt)
            if (now + SKEW_MS < d.notBefore) return no(DelegationRefusal.NOT_YET_VALID, "Mandat pas encore valable")
            if (now > d.expiresAt) return no(DelegationRefusal.WINDOW_CLOSED, "Mandat périmé : le point focal doit en obtenir un nouveau")
            seqState?.record(d.ownerKid, d.seq)
            return DelegationResult.Accepted(d)
        }
    }
}
