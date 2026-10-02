package castbridge.core.trust

/**
 * Facts of the manual-IP / selected-TV screen (`TvScreen.kt`). [lastStatus]: HTTP status of the last info request (0 = no answer, null = never asked).
 * [locked] / [trusted]: the 401 body said « locked » / the credential in use is a token. [lastOkAt] and [nowMs] are carried for the freshness rule of w14-06.
 */
data class ManualTvFacts(
    val base: String?, val pinStored: Boolean, val lastStatus: Int?, val lastOkAt: Long?, val nowMs: Long,
    val locked: Boolean = false, val trusted: Boolean = false,
)

/** [reachableText] = the « injoignable » line (null = say nothing), [pinError] = the red line under the code field, [canSend] = a file may be sent. */
data class ManualTvView(val reachableText: String?, val pinError: String?, val canSend: Boolean)

object ManualTvViews {
    const val UNREACHABLE = "TV injoignable — si une vidéo est en cours, elle continue sur la TV."
    const val LOCKED = "Trop d'essais : TV verrouillée 60 s"
    const val EXPIRED = "Autorisation de ce téléphone expirée : reconnexion…"
    const val BAD_PIN = "PIN incorrect"

    fun decide(f: ManualTvFacts): ManualTvView {
        val pinError = if (f.lastStatus == 401) when { f.locked -> LOCKED; f.trusted -> EXPIRED; else -> BAD_PIN } else null
        // REGRESSION R-01 : comportement actuel (TvScreen.kt:61) : « joignable » tant qu'aucune réponse n'a été observée ; une TV qui refuse (401) compte comme joignable
        val reachable = f.lastStatus == null || f.lastStatus == 401 || f.lastStatus in 200..299
        val text = if (f.base != null && !reachable) UNREACHABLE else null
        return ManualTvView(text, pinError, f.base != null && f.pinStored && pinError == null)
    }
}
