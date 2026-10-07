package castbridge.play.stake

import castbridge.core.chess.online.ChessEscrow
import castbridge.core.owner.KeyRing
import castbridge.core.owner.TrustedKey
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.EscrowTicket
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletRefusal
import java.util.Base64

/**
 * La porte des mises (option B de la conception W22 § 3.3) : le service ne détient QUE les clés PUBLIQUES « portefeuille » de l'API (`CASTBRIDGE_PLAY_WALLET_PUBKEY`, `_2` pour une rotation) et
 * vérifie le blocage `cbe1` que la TV joint à `create` / `join`. Il ne parle jamais à l'API, ne connaît aucun identifiant du grand livre sauf ce que le blocage signé lui dit (identité de la TV,
 * identifiant du blocage), et ne débite rien : un blocage forgé ne correspond à aucun blocage réel, l'API refuse alors de régler (conception § 5.2, S-8).
 *
 * Contrôles, fermés (au moindre doute : refus) : signature d'une clé du jeu de clés, audience `castbridge-play`, validité (≤ 30 min, ni échu ni futur), [EscrowTicket.verify] ; puis, pour CETTE
 * connexion : identité = celle de l'activation prouvée à l'entrée (une TV ne mise pas avec le blocage d'une autre), même monnaie et même mise que la salle, un seul siège (`k` = 1 : aux échecs une
 * mise par TV), mise dans les bornes du service. L'employer ensuite ([reserve]) est un acte à part : un blocage ne sert qu'à UNE salle, jamais deux (sinon une partie ne se règlerait pas).
 */
class EscrowGate(walletPubKeys: List<String>, private val maxNdem: Long = DEFAULT_MAX_NDEM, private val maxMboko: Long = DEFAULT_MAX_MBOKO, private val maxReserved: Int = 50_000) {
    private val ring: KeyRing = KeyRing(walletPubKeys.mapNotNull { WalletKeys.rawBase64(it) }.distinct().map { TrustedKey(KeyRing.idOf(it), it, emptySet()) })
    val configured: Boolean = walletPubKeys.any { WalletKeys.rawBase64(it) != null }

    /** Pourquoi un blocage est refusé (journal et tests ; l'écran dit seulement `STAKE_ESCROW_INVALID`). */
    enum class Why { NO_KEY, UNREADABLE, BAD_SIGNATURE, UNKNOWN_KEY, EXPIRED, NOT_YET_VALID, OTHER_TV, OTHER_STAKE, BAD_SEATS, BAD_AMOUNT, OTHER }

    sealed class Result {
        class Ok(val ticket: EscrowTicket) : Result() { fun escrow() = ChessEscrow(ticket.eid, ticket.id, ticket.amt) }
        class Refused(val why: Why) : Result()
    }

    /** Vérifie [token] pour la TV d'identité [identity] qui entre dans une salle misée [spec]. SANS ÉTAT : rien n'est réservé. */
    fun check(token: String?, nowMs: Long, identity: String, spec: StakeSpec): Result {
        if (!configured) return Result.Refused(Why.NO_KEY)
        val t = when (val v = EscrowTicket.verify(token, ring, nowMs)) {
            is Verdict.Accepted -> v.value
            is Verdict.Rejected -> return Result.Refused(when (v.reason) {
                WalletRefusal.BAD_SIGNATURE -> Why.BAD_SIGNATURE
                WalletRefusal.UNKNOWN_KEY, WalletRefusal.REVOKED_KEY -> Why.UNKNOWN_KEY
                WalletRefusal.EXPIRED -> Why.EXPIRED
                WalletRefusal.NOT_YET_VALID -> Why.NOT_YET_VALID
                WalletRefusal.UNREADABLE -> Why.UNREADABLE
                else -> Why.OTHER
            })
        }
        if (t.id != identity) return Result.Refused(Why.OTHER_TV)
        if (t.cur.name != spec.cur || t.per != spec.per) return Result.Refused(Why.OTHER_STAKE)
        if (t.k != 1) return Result.Refused(Why.BAD_SEATS)
        if (!inBounds(spec)) return Result.Refused(Why.BAD_AMOUNT)
        return Result.Ok(t)
    }

    /** La mise demandée est-elle dans les bornes du service (NDEM 1..[maxNdem], MBOKO 1..[maxMboko]) ? */
    fun inBounds(spec: StakeSpec): Boolean = spec.per >= 1 && spec.per <= (when (spec.cur) { WalletCurrency.NDEM.name -> maxNdem; WalletCurrency.MBOKO.name -> maxMboko; else -> 0L })

    // ------------------------------------------------------------------ un blocage ne sert qu'à une salle

    private val reserved = LinkedHashMap<String, Long>()   // eid -> échéance (ms)

    /** Emploie le blocage [eid] (valable jusqu'à [expMs]) : faux s'il l'est déjà (autre salle, ou salle déjà finie) ou si la table est pleine (refus, jamais d'éviction d'un blocage valable). */
    @Synchronized fun reserve(eid: String, expMs: Long, nowMs: Long): Boolean {
        sweep(nowMs)
        if (eid in reserved) return false
        if (reserved.size >= maxReserved) return false
        reserved[eid] = expMs
        return true
    }

    /** Rend le blocage (l'entrée a été refusée par la salle : la TV peut le réemployer ailleurs). */
    @Synchronized fun release(eid: String) { reserved.remove(eid) }

    @Synchronized fun reservedCount(): Int = reserved.size

    @Synchronized private fun sweep(nowMs: Long) {
        val it = reserved.entries.iterator()
        while (it.hasNext()) if (it.next().value + GRACE_MS < nowMs) it.remove()
    }

    companion object {
        const val DEFAULT_MAX_NDEM = 1_000L
        const val DEFAULT_MAX_MBOKO = 100L
        /** Un blocage échu reste mémorisé un peu plus longtemps (une salle qui l'a employé peut durer). */
        const val GRACE_MS = 6 * 3_600_000L
    }
}

/** Clés publiques d'Ed25519 en Base64 : 32 octets bruts, ou SPKI X.509 de 44 octets (même lecture que les tickets). */
object WalletKeys {
    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    /** La clé brute de 32 octets en Base64, ou null si le texte n'en est pas une. */
    fun rawBase64(text: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(text.trim().replace('-', '+').replace('_', '/'))
        val key = when {
            raw.size == 32 -> raw
            raw.size == 44 && raw.copyOfRange(0, 12).contentEquals(SPKI_PREFIX) -> raw.copyOfRange(12, 44)
            else -> return null
        }
        Base64.getEncoder().encodeToString(key)
    }.getOrNull()
}
