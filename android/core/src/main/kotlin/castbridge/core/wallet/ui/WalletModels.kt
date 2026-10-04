package castbridge.core.wallet.ui

/** Sens d'une conversion (noms du serveur : `dir` = N2M ou M2N). N2M : NDEM -> MBOKO ; M2N : MBOKO -> NDEM. */
enum class ConvertDir(val wire: String) { N2M("N2M"), M2N("M2N") }

/** Lecture de `GET /api/v1/wallet/policy` (affichage seulement : le serveur reste l'autorité de chaque opération). */
data class PolicyView(val rate: Long, val reverseFeeBp: Long, val convert: Boolean, val transfer: Boolean, val vouchers: Boolean, val stakesNdem: Boolean, val stakesMboko: Boolean,
                      val transferCapNdem: Long, val transferCapMboko: Long)

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
