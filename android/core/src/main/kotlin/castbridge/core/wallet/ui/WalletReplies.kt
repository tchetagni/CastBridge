package castbridge.core.wallet.ui

import castbridge.core.net.JsonLite

/**
 * Lecture TOLÉRANTE des réponses de l'API portefeuille (corps de `WalletSyncController`, `WalletOpsController`, `WalletHistoryController`) : un champ inconnu est ignoré, une partie facultative
 * absente ou de mauvais type est vide, une réponse sans ses champs essentiels est « illisible » (null). Jamais d'exception : un serveur plus récent ne doit pas faire planter la TV.
 */
object WalletReplies {
    private fun obj(body: String): Map<String, Any?>? = runCatching { JsonLite.obj(body) }.getOrNull()
    private fun Map<*, *>.long(k: String): Long? = (this[k] as? Number)?.toLong()
    private fun Map<*, *>.str(k: String): String? = this[k] as? String
    private fun Map<*, *>.flag(k: String, default: Boolean): Boolean = this[k] as? Boolean ?: default
    private fun Map<*, *>.map(k: String): Map<*, *>? = this[k] as? Map<*, *>

    private fun lines(v: Any?): List<HistoryLine> = (v as? List<*>).orEmpty().mapNotNull { m ->
        if (m !is Map<*, *>) return@mapNotNull null
        HistoryLine(m.long("id") ?: return@mapNotNull null, m.str("kind") ?: return@mapNotNull null, m.str("currency") ?: return@mapNotNull null, m.long("amount") ?: return@mapNotNull null,
            m.long("at") ?: return@mapNotNull null, m.str("label") ?: return@mapNotNull null, m.str("counterparty"))
    }

    fun parseSync(body: String): SyncData? {
        val m = obj(body) ?: return null
        val snapshot = m.str("snapshot")?.takeIf { it.isNotBlank() } ?: return null
        val notices = (m["notices"] as? List<*>).orEmpty().mapNotNull { n -> (n as? Map<*, *>)?.let { x -> Notice(x.str("reason") ?: return@let null, x.str("text") ?: return@let null) } }
        val edition = m.map("edition")?.let { e -> EditionInfo(e.str("ed") ?: return@let null, e.str("license") ?: return@let null, e.flag("grace", false), e.flag("boundOther", false)) }
        return SyncData(snapshot, lines(m["history"]), notices, edition)
    }

    fun parsePolicy(body: String): PolicyView? {
        val m = obj(body) ?: return null
        val rate = m.long("rate")?.takeIf { it > 0 } ?: return null
        val bp = m.long("reverseFeeBp")?.takeIf { it >= 0 } ?: return null
        val sw = m.map("switches"); val cap = m.map("transferDailyCap")
        return PolicyView(rate, bp, sw?.flag("convert", true) ?: true, sw?.flag("transfer", true) ?: true, sw?.flag("vouchers", true) ?: true,
            sw?.flag("stakesNdem", true) ?: true, sw?.flag("stakesMboko", true) ?: true, cap?.long("NDEM") ?: 0, cap?.long("MBOKO") ?: 0)
    }

    fun parseHistory(body: String): HistoryPage? {
        val m = obj(body) ?: return null
        if (m["lines"] !is List<*>) return null
        return HistoryPage(lines(m["lines"]), m.long("next"))
    }

    fun parseConvert(body: String): ConvertDone? {
        val m = obj(body) ?: return null
        return ConvertDone(m.str("dir") ?: return null, m.long("q") ?: return null, m.long("rate") ?: return null, m.long("reverseFeeBp") ?: return null, m.long("ndemGross") ?: return null,
            m.long("fee") ?: return null, m.long("ndemNet") ?: return null, m.flag("replayed", false), m.str("snapshot"))
    }

    fun parseTransfer(body: String): TransferDone? {
        val m = obj(body) ?: return null
        return TransferDone(m.str("cur") ?: return null, m.long("amt") ?: return null, m.str("to"), m.flag("replayed", false), m.str("snapshot"))
    }

    fun parseReceive(body: String): ReceiveCodeView? {
        val m = obj(body) ?: return null
        return ReceiveCodeView(m.str("code")?.takeIf { it.isNotBlank() } ?: return null, m.long("exp") ?: return null)
    }

    /** `{"status":409,"message":"…","details":["MOTIF"]}` : le motif fermé est `details[0]` ; un corps qui n'est pas du JSON (page d'un proxy) donne un refus sans motif ni texte. */
    fun parseFailure(status: Int, body: String): ApiFailure {
        val m = obj(body) ?: return ApiFailure(status, null, null)
        return ApiFailure(status, (m["details"] as? List<*>)?.firstOrNull() as? String, m.str("message"))
    }
}
