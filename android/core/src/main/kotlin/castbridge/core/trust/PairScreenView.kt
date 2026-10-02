package castbridge.core.trust

/** The link chip of the pairing screen (`TvPairScreen.kt`): one source ([LinkStart.view]); « Aucune TV ajoutée. » only when no TV is saved. */
object PairScreenView {
    const val NO_TV_LIST = "Aucune TV ajoutée."
    private val idle = LinkMachine().view(LinkMachine.Model())

    /** [trustedList]: names of the saved TVs as the screen lists them; the larger of it and [savedCount] counts. */
    fun decide(savedCount: Int, defaultName: String?, stepView: LinkView?, trustedList: List<String>): LinkView =
        LinkStart.view(maxOf(savedCount, trustedList.size), defaultName, stepView) ?: idle

    /** The empty-list line: said only when really nothing is saved. */
    fun emptyListText(savedCount: Int, trustedList: List<String>): String? = if (maxOf(savedCount, trustedList.size) == 0) NO_TV_LIST else null
}
