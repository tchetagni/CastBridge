package castbridge.core.wallet.ui

/** Sens d'une conversion (noms du serveur : `dir` = N2M ou M2N). N2M : NDEM -> MBOKO ; M2N : MBOKO -> NDEM. */
enum class ConvertDir(val wire: String) { N2M("N2M"), M2N("M2N") }

/** Lecture de `GET /api/v1/wallet/policy` (affichage seulement : le serveur reste l'autorité de chaque opération). [games] : les jeux misés (échecs) avec leur échelle, leurs frais et leurs plafonds. */
data class PolicyView(val rate: Long, val reverseFeeBp: Long, val convert: Boolean, val transfer: Boolean, val vouchers: Boolean, val stakesNdem: Boolean, val stakesMboko: Boolean,
                      val transferCapNdem: Long, val transferCapMboko: Long, val games: Map<String, GamePolicyView> = emptyMap())

/** La politique d'un jeu misé telle que le serveur la dit : interrupteur, échelle de mises par monnaie, frais de plateforme (points de base), plafonds de parties GAGNÉES (0 = sans plafond). */
data class GamePolicyView(val enabled: Boolean, val stakesNdem: List<Long>, val stakesMboko: List<Long>, val feeBp: Int, val capDay: Int, val capWeek: Int, val capMonth: Int)

/** Réponse de `POST /escrow` : le blocage `cbe1` signé par l'API (à porter au service de jeu), son identifiant, sa validité ; l'instantané `cbw1` neuf est donné au cache signé. */
data class EscrowDone(val cbe1: String, val eid: String, val iat: Long, val exp: Long, val replayed: Boolean, val snapshotToken: String?) {
    /** Jamais le blocage signé ni l'instantané dans un journal ni dans un message d'échec de test. */
    override fun toString() = "EscrowDone(eid=$eid, iat=$iat, exp=$exp, replayed=$replayed)"
}

/** Une ligne d'un règlement : ce que le service a attribué (`pay`, avant frais) et les frais de plateforme prélevés (`fee`) ; ce que la TV reçoit vraiment est `pay − fee`. */
data class SettleLine(val eid: String, val id: String, val used: Long, val pay: Long, val fee: Long)

/** Réponse de `POST /settle` (aussi pour un rejeu) : le règlement d'une partie misée. */
data class SettleDone(val rid: String, val kind: String, val cur: String, val game: String?, val fee: Long, val lines: List<SettleLine>)

/** Une ligne d'historique, libellé français déjà fourni par le serveur (contrepartie masquée). */
data class HistoryLine(val id: Long, val kind: String, val currency: String, val amount: Long, val at: Long, val label: String, val counterparty: String?)
data class HistoryPage(val lines: List<HistoryLine>, val next: Long?)

data class Notice(val reason: String, val text: String)
data class EditionInfo(val ed: String, val license: String, val grace: Boolean, val boundOther: Boolean)

/** Réponse de `POST /sync` : l'instantané `cbw1` (vérifié puis mis en cache par le client), 20 lignes d'historique, avis, édition. */
data class SyncData(val snapshotToken: String, val history: List<HistoryLine>, val notices: List<Notice>, val edition: EditionInfo?)

data class ConvertDone(val dir: String, val q: Long, val rate: Long, val reverseFeeBp: Long, val ndemGross: Long, val fee: Long, val ndemNet: Long, val replayed: Boolean, val snapshotToken: String?)
data class TransferDone(val cur: String, val amt: Long, val to: String?, val replayed: Boolean, val snapshotToken: String?)
data class ReceiveCodeView(val code: String, val expMs: Long)

/** Un refus du serveur : statut HTTP, motif fermé (`details[0]`) s'il y en a un, texte français du serveur. */
data class ApiFailure(val status: Int, val reason: String?, val message: String?)
