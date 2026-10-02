package castbridge.core.trust

/** The link chip of the pairing screen (`TvPairScreen.kt`): one source ([LinkStart.view]); « Aucune TV ajoutée. » only when no TV is known at all (registry list AND code path). */
object PairScreenView {
    const val NO_TV_LIST = "Aucune TV ajoutée."
    private val idle = LinkMachine().view(LinkMachine.Model())

    /** [trustedList]: names of the saved TVs as the screen lists them; the larger of it and [savedCount] counts. [pinTvName]: the TV of the code (PIN) path, if any (R-02). */
    fun decide(savedCount: Int, defaultName: String?, stepView: LinkView?, trustedList: List<String>, pinTvName: String? = null): LinkView =
        LinkStart.view(maxOf(savedCount, trustedList.size), defaultName, stepView)
            ?: pinTvName?.let { LinkStart.checking(SendChoices.display(it)) }
            ?: idle

    /** The empty-list line: said only when really nothing is known, neither saved nor as the TV of the code path (R-02). */
    fun emptyListText(savedCount: Int, trustedList: List<String>, pinTvName: String? = null): String? =
        if (maxOf(savedCount, trustedList.size) == 0 && pinTvName == null) NO_TV_LIST else null

    /** What a tap on the chip action does (R-03): REASSOCIATE carries the address of the TV to re-associate; without an address it degrades to ADD_TV. */
    data class ActionRequest(val action: LinkAction, val address: String?)

    fun request(action: LinkAction, tvAddress: String?): ActionRequest = when {
        action == LinkAction.REASSOCIATE && tvAddress.isNullOrBlank() -> ActionRequest(LinkAction.ADD_TV, null)
        action == LinkAction.REASSOCIATE -> ActionRequest(action, tvAddress)
        else -> ActionRequest(action, null)
    }
}
