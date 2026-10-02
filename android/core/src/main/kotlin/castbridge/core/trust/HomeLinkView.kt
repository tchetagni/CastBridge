package castbridge.core.trust

/**
 * Facts of the phone's home screen (« CastBridge TV » tab). Two paths feed it: the trusted registry ([savedCount], [defaultName],
 * [stepView]) and the code (PIN) path ([pinTvName], [pinStored], last `/api/info` answer: [lastInfoOkAt] / [lastInfoStatus]).
 * @param lastInfoStatus HTTP status of the last info request (0 = no answer), null = never asked.
 */
data class HomeFacts(
    val savedCount: Int, val defaultName: String?, val stepView: LinkView?, val pinTvName: String?, val pinStored: Boolean,
    val lastInfoOkAt: Long?, val lastInfoStatus: Int?, val nowMs: Long, val freshMs: Long = 10_000,
)

/** What the home screen draws: the chip ([view]), whether the code assistant opens ([showWizard]) and why ([wizardReason], now rendered). */
data class HomeView(val view: LinkView, val showWizard: Boolean, val wizardReason: String?)

/** Home-screen decisions of `TvHome.kt` as one pure function. Rule: a state nobody observed is « vérification », never « Connectée » on a memory older than `freshMs`. */
object HomeLinkView {
    const val CODE_CHANGED = "Le code de la TV a changé : saisissez-le à nouveau."
    const val RECONNECTING = "Reconnexion à la TV…"
    private val idle = LinkMachine().view(LinkMachine.Model())

    fun decide(f: HomeFacts): HomeView {
        if (f.savedCount > 0) {
            // trusted path: a 401 is a token to renew (the link loop does it), never a code to retype
            val reason = if (f.lastInfoStatus == 401) RECONNECTING else null
            return HomeView(LinkStart.view(f.savedCount, f.defaultName, f.stepView) ?: idle, false, reason)
        }
        val pinTv = f.pinTvName
        // initial wizard of TvHome.kt:104: nothing saved and no usable code for a known TV
        if (pinTv == null || !f.pinStored) return HomeView(idle, true, null)
        val name = SendChoices.display(pinTv)
        // REGRESSION R-01 : comportement actuel (le 401 du chemin code ouvre l'assistant), corrigé par w14-06 qui rend le motif (F2 de W13)
        if (f.lastInfoStatus == 401) return HomeView(LinkStart.checking(name), true, CODE_CHANGED)
        val ok = f.lastInfoOkAt
        val fresh = ok != null && f.nowMs - ok in 0..f.freshMs && (f.lastInfoStatus == null || f.lastInfoStatus in 200..299)
        val view = if (fresh) LinkView(LinkState.Connected(RouteKind.LAN, name), "Connectée", name, LinkAction.NONE, Tone.GOOD, false)
        else LinkStart.checking(name)
        return HomeView(view, false, null)
    }
}
