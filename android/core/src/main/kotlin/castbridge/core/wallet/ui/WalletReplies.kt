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
            sw?.flag("stakesNdem", true) ?: true, sw?.flag("stakesMboko", true) ?: true, cap?.long("NDEM") ?: 0, cap?.long("MBOKO") ?: 0, games(m.map("games")))
    }

    /** Les jeux misés de la politique (champ additif `games` : un serveur plus ancien n'en a pas, la TV garde alors son échelle de repli). Une entrée illisible est ignorée. */
    private fun games(g: Map<*, *>?): Map<String, GamePolicyView> {
        if (g == null) return emptyMap()
        val out = LinkedHashMap<String, GamePolicyView>()
        for ((k, v) in g) {
            val name = k as? String ?: continue
            val e = v as? Map<*, *> ?: continue
            val stakes = e.map("stakes") ?: continue
            fun scale(cur: String) = (stakes[cur] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong()?.takeIf { n -> n > 0 } }.distinct().sorted()
            val caps = e.map("winCaps")
            out[name] = GamePolicyView(e.flag("enabled", true), scale("NDEM"), scale("MBOKO"), (e.long("feeBp") ?: 0L).toInt().coerceIn(0, 2_000),
                (caps?.long("day") ?: 0L).toInt().coerceAtLeast(0), (caps?.long("week") ?: 0L).toInt().coerceAtLeast(0), (caps?.long("month") ?: 0L).toInt().coerceAtLeast(0),
                (e.long("seats") ?: 1L).toInt().coerceIn(1, 8))   // sièges qui misent par TV : 1 aux échecs, 8 au Quiz (un serveur plus ancien ne le dit pas : 1)
        }
        return out
    }

    /** Réponse de `POST /escrow` : le blocage `cbe1` et ce qui le décrit ; sans `cbe1` ni `eid` la réponse est « illisible ». */
    fun parseEscrow(body: String): EscrowDone? {
        val m = obj(body) ?: return null
        val cbe1 = m.str("cbe1")?.takeIf { it.startsWith("cbe1.") && it.length <= 1_200 } ?: return null
        return EscrowDone(cbe1, m.str("eid") ?: return null, m.long("iat") ?: return null, m.long("exp") ?: return null, m.flag("replayed", false), m.str("snapshot"))
    }

    /** Réponse de `POST /settle` : le règlement, avec les frais (champ additif : 0 s'il est absent). */
    fun parseSettle(body: String): SettleDone? {
        val m = obj(body) ?: return null
        val lines = (m["lines"] as? List<*>).orEmpty().mapNotNull { l ->
            val x = l as? Map<*, *> ?: return@mapNotNull null
            SettleLine(x.str("eid") ?: return@mapNotNull null, x.str("id") ?: return@mapNotNull null, x.long("used") ?: return@mapNotNull null, x.long("pay") ?: return@mapNotNull null, x.long("fee") ?: 0L)
        }
        if (lines.isEmpty()) return null
        return SettleDone(m.str("rid") ?: return null, m.str("kind") ?: return null, m.str("cur") ?: return null, m.str("game"), m.long("fee") ?: 0L, lines)
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
