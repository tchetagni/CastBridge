package castbridge.core.trust

/**
 * Facts of the manual-IP / selected-TV screen (`TvScreen.kt`). [lastStatus]: HTTP status of the last info request (0 = no answer, null = never asked).
 * [locked] / [trusted]: the 401 body said « locked » / the credential in use is a token.
 * No timestamps here (decided: they were unused). Freshness of the manual screen is not decided by this function: w14-06 feeds the latest status only,
 * and a status older than a few polls must be reset to null by the caller (then [ManualTvViews] says « jamais interrogée »).
 */
data class ManualTvFacts(
    val base: String?, val pinStored: Boolean, val lastStatus: Int?,
    val locked: Boolean = false, val trusted: Boolean = false,
)

/** [reachableText] = the « injoignable » line (null = say nothing), [pinError] = the red line under the code field, [canSend] = a file may be sent. */
data class ManualTvView(val reachableText: String?, val pinError: String?, val canSend: Boolean)

object ManualTvViews {
    const val UNREACHABLE = "TV injoignable — si une vidéo est en cours, elle continue sur la TV."
    const val LOCKED = "Trop d'essais : la TV est verrouillée 60 s, attendez puis réessayez."
    const val EXPIRED = "Autorisation de ce téléphone expirée : reconnexion en cours, patientez."
    const val BAD_PIN = "Code incorrect : retapez le code affiché sur la TV."

    fun decide(f: ManualTvFacts): ManualTvView {
        val pinError = if (f.lastStatus == 401) when { f.locked -> LOCKED; f.trusted -> EXPIRED; else -> BAD_PIN } else null
        // R-01 (TvScreen.kt:61), défaut reproduit : « joignable » tant qu'aucune réponse n'a été observée (test regressionR01_*, à inverser par w14-06). Une TV qui refuse (401) EST joignable : correct.
        val reachable = f.lastStatus == null || f.lastStatus == 401 || f.lastStatus in 200..299
        val text = if (f.base != null && !reachable) UNREACHABLE else null
        return ManualTvView(text, pinError, f.base != null && f.pinStored && pinError == null)
    }
}
