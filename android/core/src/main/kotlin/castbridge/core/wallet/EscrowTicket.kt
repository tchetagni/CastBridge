package castbridge.core.wallet

import castbridge.core.owner.KeyRing

/**
 * Blocage de mise `cbe1` signé par l'API (domaine `castbridge-wallet-escrow-v1`) : lu par le service de jeu, qui n'a QUE la clé publique. [amt] doit valoir [per] × [k]
 * (vérifié), la validité est de 30 minutes au plus.
 */
data class EscrowTicket(val kid: String, val eid: String, val id: String, val cur: WalletCurrency, val per: Long, val k: Int, val amt: Long, val iat: Long, val exp: Long) {
    internal fun payload(): Map<String, Any?> = linkedMapOf("aud" to AUDIENCE, "kid" to kid, "eid" to eid, "id" to id, "cur" to cur.name, "per" to per, "k" to k.toLong(), "amt" to amt, "iat" to iat, "exp" to exp)

    companion object {
        const val PREFIX = "cbe1"
        const val DOMAIN = "castbridge-wallet-escrow-v1"
        const val AUDIENCE = "castbridge-play"
        const val MAX_LENGTH = 1_200
        const val MAX_LIFE_MS = 30 * 60_000L
        private val KEYS = setOf("aud", "kid", "eid", "id", "cur", "per", "k", "amt", "iat", "exp")

        fun verify(token: String?, ring: KeyRing, nowMs: Long): Verdict<EscrowTicket> {
            val o = when (val v = WalletFormats.open(token, PREFIX, DOMAIN, ring, MAX_LENGTH)) { is Verdict.Rejected -> return v; is Verdict.Accepted -> v.value }
            return WalletFormats.guard {
                val f = WalletFormats.Fields(o.body, KEYS)
                if (f.str("aud", Regex("^.{1,64}$")) != AUDIENCE) throw WalletFormats.Bad(WalletRefusal.WRONG_AUDIENCE)
                val per = f.amount("per", min = 1)
                val k = f.long("k").also { if (it !in 1..8) throw WalletFormats.Bad(WalletRefusal.OUT_OF_BOUNDS) }
                val amt = f.amount("amt", min = 1)
                val iat = f.nonNeg("iat"); val exp = f.nonNeg("exp")
                val t = EscrowTicket(o.kid, f.str("eid", WalletFormats.EID), f.str("id", WalletFormats.ID), f.cur("cur"), per, k.toInt(), amt, iat, exp)
                if (per * k != amt) throw WalletFormats.Bad(WalletRefusal.AMOUNT_MISMATCH)           // per ≤ 10¹², k ≤ 8 : pas de débordement
                if (exp <= iat || exp - iat > MAX_LIFE_MS) throw WalletFormats.Bad(WalletRefusal.TOO_LONG_LIFE)
                if (nowMs >= exp) throw WalletFormats.Bad(WalletRefusal.EXPIRED)
                if (iat > nowMs + WalletFormats.SKEW_MS) throw WalletFormats.Bad(WalletRefusal.NOT_YET_VALID)
                t
            }
        }
    }
}
