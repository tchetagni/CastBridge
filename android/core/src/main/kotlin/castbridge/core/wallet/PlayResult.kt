package castbridge.core.wallet

import castbridge.core.owner.KeyRing
import castbridge.core.owner.Signer

/**
 * Résultat de partie `cbr1` SIGNÉ PAR LE SERVICE DE JEU avec sa clé dédiée (domaine `castbridge-play-result-v1`) : le service ne détient aucun identifiant du grand livre, il
 * atteste seulement qui a utilisé et gagné quoi parmi les mises DÉJÀ bloquées ([EscrowTicket]). L'API vérifie par la clé publique de résultat.
 *
 * [lines] : au plus 16 lignes `[eid, id, used, pay]` (une par blocage). [check] est le contrôle local, sans clé : Σ pay = Σ used (rien ne se crée, rien ne se perd), `eid` uniques,
 * et un résultat `ABORT` ne déplace rien (chaque blocage est rendu en entier : used = pay = 0).
 */
data class PlayResult(val kid: String, val rid: String, val room: String, val game: String, val cur: WalletCurrency, val per: Long, val kind: Kind, val at: Long, val lines: List<Line>) {
    enum class Kind { END, ABORT }
    data class Line(val eid: String, val id: String, val used: Long, val pay: Long)

    /** Vrai quand les lignes sont cohérentes (voir la classe). Ne regarde AUCUNE signature. */
    fun check(): Boolean {
        if (lines.isEmpty() || lines.size > MAX_LINES) return false
        if (lines.map { it.eid }.toSet().size != lines.size) return false
        if (lines.any { it.used !in 0..WalletFormats.MAX_AMOUNT || it.pay !in 0..WalletFormats.MAX_AMOUNT }) return false
        if (lines.sumOf { it.pay } != lines.sumOf { it.used }) return false
        if (kind == Kind.ABORT && lines.any { it.used != 0L || it.pay != 0L }) return false
        return true
    }

    internal fun payload(): Map<String, Any?> = linkedMapOf(
        "kid" to kid, "rid" to rid, "room" to room, "game" to game, "cur" to cur.name, "per" to per, "kind" to kind.name, "at" to at,
        "lines" to lines.map { listOf(it.eid, it.id, it.used, it.pay) },
    )

    companion object {
        const val PREFIX = "cbr1"
        const val DOMAIN = "castbridge-play-result-v1"
        const val MAX_LINES = 16
        const val MAX_LENGTH = 6_000
        private val KEYS = setOf("kid", "rid", "room", "game", "cur", "per", "kind", "at", "lines")
        private val ROOM = Regex("^[A-Za-z0-9_-]{1,32}$")
        private val GAME = Regex("^[a-z0-9_-]{1,32}$")

        /**
         * La pièce signée de [result] (pour le service de jeu). Refuse de signer un résultat incohérent ([check]) ou dont `kid` n'est pas celui de [signer] : le service ne peut donc
         * pas, même par erreur, attester une création de jetons.
         */
        fun sign(result: PlayResult, signer: Signer): String {
            require(result.kid == signer.keyId) { "kid ≠ clé du signataire" }
            require(result.check()) { "résultat incohérent" }
            return WalletFormats.seal(PREFIX, DOMAIN, result.payload()) { signer.sign(it) }
        }

        /** Vérifie [token] avec l'anneau des clés de RÉSULTAT ; un résultat authentique mais incohérent est refusé ([WalletRefusal.BAD_TOTALS]). */
        fun verify(token: String?, ring: KeyRing): Verdict<PlayResult> {
            val o = when (val v = WalletFormats.open(token, PREFIX, DOMAIN, ring, MAX_LENGTH)) { is Verdict.Rejected -> return v; is Verdict.Accepted -> v.value }
            val v = WalletFormats.guard {
                val f = WalletFormats.Fields(o.body, KEYS)
                val raw = f.list("lines")
                if (raw.isEmpty() || raw.size > MAX_LINES) throw WalletFormats.Bad(WalletRefusal.OUT_OF_BOUNDS)
                val lines = raw.map { row ->
                    val r = row as? List<*> ?: throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                    if (r.size != 4) throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                    val eid = (r[0] as? String)?.takeIf { WalletFormats.EID.matches(it) }; val id = (r[1] as? String)?.takeIf { WalletFormats.ID.matches(it) }
                    val used = r[2] as? Long; val pay = r[3] as? Long
                    if (eid == null || id == null || used == null || pay == null) throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                    if (used !in 0..WalletFormats.MAX_AMOUNT || pay !in 0..WalletFormats.MAX_AMOUNT) throw WalletFormats.Bad(WalletRefusal.OUT_OF_BOUNDS)
                    Line(eid, id, used, pay)
                }
                val kind = Kind.values().firstOrNull { it.name == o.body["kind"] } ?: throw WalletFormats.Bad(WalletRefusal.UNREADABLE)
                PlayResult(o.kid, f.str("rid", WalletFormats.HEX32), f.str("room", ROOM), f.str("game", GAME), f.cur("cur"), f.amount("per", min = 1), kind, f.nonNeg("at"), lines)
            }
            if (v is Verdict.Accepted && !v.value.check()) return Verdict.Rejected(WalletRefusal.BAD_TOTALS)
            return v
        }
    }
}
