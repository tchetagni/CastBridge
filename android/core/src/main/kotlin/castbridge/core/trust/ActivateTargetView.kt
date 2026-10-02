package castbridge.core.trust

/** Which TV the activation screen reads (`ActivateTvActivity.kt:63`) and the one line saying why its state may be unreadable. */
object ActivateTargetView {
    data class Target(val name: String, val base: String?, val viaPin: Boolean)

    const val NO_TV = "Aucune TV n'est ajoutée dans CastBridge (onglet « CastBridge TV » > « Ajouter ma TV »), donc l'état d'activation ne peut pas être lu. L'envoi d'une clé, lui, n'en dépend pas."
    const val NOT_JOINED = "TV non jointe pour le moment : l'état d'activation n'est pas lisible."
    const val BT_ONLY = "TV jointe par Bluetooth seulement : l'état d'activation n'est pas lisible."

    /** (target, message): message null = state readable. Never « Aucune TV » when [pinTvName] is known (R-02 family, fixed by construction). */
    fun decide(savedCount: Int, defaultName: String?, stepView: LinkView?, pinTvName: String?, pinBase: String?): Pair<Target?, String?> {
        val s = stepView?.state
        if (savedCount > 0 && defaultName != null && s != null && s.isGood) {
            val bt = s is LinkState.Connected && s.route == RouteKind.BLUETOOTH
            return Target(defaultName, null, false) to (if (bt) BT_ONLY else null)
        }
        if (pinTvName != null)
            return Target(SendChoices.display(pinTvName), pinBase, true) to (if (pinBase != null) null else NOT_JOINED)
        if (savedCount > 0) return (defaultName?.let { Target(it, null, false) }) to NOT_JOINED
        return null to NO_TV
    }
}
