package castbridge.core.trust

/**
 * Facts of the phone's home screen (« CastBridge TV » tab). Two paths feed it: the trusted registry ([savedCount], [defaultName],
 * [stepView]) and the code (PIN) path ([pinTvName], [pinStored], last `/api/info` answer: [lastInfoOkAt] / [lastInfoStatus]).
 * @param lastInfoStatus HTTP status of the last info request (0 = no answer), null = never asked.
 * @param credentialIsToken the credential the last info request was SENT with is a session token (`TvAuth.isToken(client.pin)` in `TvHome.kt`), not a typed
 *   code. It alone decides the trusted path: a registry with a saved TV but no live session probes with the PIN stored under the TV name (mixed case).
 * @param freshMs 15 s = 3 x the poll period (`TvHome.kt`: 2 s + the request timeout) : a single lost answer must not flip the chip. w14-06 MUST advance [nowMs]
 *   with a timer (a chip decided once never expires) and take it from a monotonic clock (`SystemClock.elapsedRealtime()`), the same one used to stamp [lastInfoOkAt].
 */
data class HomeFacts(
    val savedCount: Int, val defaultName: String?, val stepView: LinkView?, val pinTvName: String?, val pinStored: Boolean,
    val lastInfoOkAt: Long?, val lastInfoStatus: Int?, val nowMs: Long, val credentialIsToken: Boolean = false, val freshMs: Long = FRESH_MS,
) {
    companion object { const val FRESH_MS = 15_000L }
}

/** What the home screen draws: the chip ([view]), whether the code assistant opens ([showWizard]) and why ([wizardReason], now rendered). */
data class HomeView(val view: LinkView, val showWizard: Boolean, val wizardReason: String?)

/** Home-screen decisions of `TvHome.kt` as one pure function. Rule: a state nobody observed is « vérification », never « Connectée » on a memory older than `freshMs`. */
object HomeLinkView {
    const val CODE_CHANGED = "Code refusé par la TV : saisissez le code affiché sur la TV."
    const val RECONNECTING = "Reconnexion à la TV…"
    private val idle = LinkMachine().view(LinkMachine.Model())

    /**
     * 1. Trusted path = the request was sent with a session token. A 401 then means « token to renew » (the link loop does it): the chip goes back to
     *    « vérification » (never « Connectée ») with [RECONNECTING], no wizard.
     * 2. Otherwise the credential is a typed code. With a known TV and a stored code, the code path decides (also when a registry TV is saved but has no live
     *    session: mixed case): a 401 opens the wizard WITH its reason ([CODE_CHANGED]), never a silent « Reconnexion… ».
     * 3. No usable code: the registry view if a TV is saved, else the initial wizard.
     */
    fun decide(f: HomeFacts): HomeView {
        val registry = LinkStart.view(f.savedCount, f.defaultName, f.stepView)
        if (f.credentialIsToken && f.savedCount > 0) {
            if (f.lastInfoStatus == 401) return HomeView(LinkStart.checking(f.defaultName?.let { SendChoices.display(it) } ?: f.pinTvName?.let { SendChoices.display(it) } ?: "votre TV"), false, RECONNECTING)
            return HomeView(registry ?: idle, false, null)
        }
        val pinTv = f.pinTvName
        if (pinTv == null || !f.pinStored) return if (registry != null) HomeView(registry, false, null) else HomeView(idle, true, null)
        val name = SendChoices.display(pinTv)
        // corrigé par construction (motif rendu) : le 401 du chemin code ouvre l'assistant ET dit pourquoi (F2 de W13)
        if (f.lastInfoStatus == 401) return HomeView(LinkStart.checking(name), true, CODE_CHANGED)
        // mixed case without a fresh answer: the registry view wins while it says something good
        if (registry != null && registry.state.isGood) return HomeView(registry, false, null)
        val ok = f.lastInfoOkAt
        val fresh = ok != null && f.nowMs - ok in 0..f.freshMs && (f.lastInfoStatus == null || f.lastInfoStatus in 200..299)
        val view = if (fresh) LinkView(LinkState.Connected(RouteKind.LAN, name), "Connectée", name, LinkAction.NONE, Tone.GOOD, false)
        else LinkStart.checking(name)
        return HomeView(view, false, null)
    }
}
