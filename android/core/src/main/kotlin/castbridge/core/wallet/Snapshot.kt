package castbridge.core.wallet

import castbridge.core.owner.KeyRing

/**
 * Instantané de solde `cbw1` signé par la clé « portefeuille » de l'API (domaine `castbridge-wallet-snapshot-v1`). C'est ce que la TV AFFICHE hors ligne : un cache signé, jamais une autorité.
 * [n] / [m] : disponible NDEM / MBOKO ; [nb] / [mb] : bloqué (mises en cours) ; [seq] : dernière écriture du grand livre (croissant).
 */
data class Snapshot(val kid: String, val id: String, val ed: String, val n: Long, val nb: Long, val m: Long, val mb: Long, val seq: Long, val at: Long, val flags: Flags) {
    data class Flags(val frozen: Boolean, val stakesN: Boolean, val stakesM: Boolean)

    internal fun payload(): Map<String, Any?> = linkedMapOf(
        "kid" to kid, "id" to id, "ed" to ed, "n" to n, "nb" to nb, "m" to m, "mb" to mb, "seq" to seq, "at" to at,
        "flags" to linkedMapOf("frozen" to flags.frozen, "stakesN" to flags.stakesN, "stakesM" to flags.stakesM),
    )

    companion object {
        const val PREFIX = "cbw1"
        const val DOMAIN = "castbridge-wallet-snapshot-v1"
        const val MAX_LENGTH = 1_200
        private val EDITIONS = setOf("TRIAL", "PROD", "UNLIMITED", "NONE")
        private val KEYS = setOf("kid", "id", "ed", "n", "nb", "m", "mb", "seq", "at", "flags")

        /** Vérifie [token] : lecture stricte, clé de [ring], signature, puis champs ; une TV ne reçoit que son propre instantané ([expectedId]). */
        fun verify(token: String?, ring: KeyRing, expectedId: String): Verdict<Snapshot> {
            val o = when (val v = WalletFormats.open(token, PREFIX, DOMAIN, ring, MAX_LENGTH)) { is Verdict.Rejected -> return v; is Verdict.Accepted -> v.value }
            return WalletFormats.guard {
                val f = WalletFormats.Fields(o.body, KEYS)
                val id = f.str("id", WalletFormats.ID)
                val ed = f.str("ed", Regex("^[A-Z]{1,12}$")).also { if (it !in EDITIONS) throw WalletFormats.Bad(WalletRefusal.OUT_OF_BOUNDS) }
                val fl = f.obj("flags", setOf("frozen", "stakesN", "stakesM"))
                val s = Snapshot(o.kid, id, ed, f.amount("n"), f.amount("nb"), f.amount("m"), f.amount("mb"), f.nonNeg("seq"), f.nonNeg("at"), Flags(fl.bool("frozen"), fl.bool("stakesN"), fl.bool("stakesM")))
                if (id != expectedId) throw WalletFormats.Bad(WalletRefusal.OTHER_TV)
                s
            }
        }
    }
}
