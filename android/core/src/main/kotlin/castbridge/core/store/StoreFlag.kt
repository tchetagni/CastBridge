package castbridge.core.store

/** Le drapeau `store.enabled` : la Boutique est-elle allumée ? (w17-02) */
object StoreFlag {
    /** Nom du drapeau dans les ordres signés (`flag.set`) et dans la liste close de la TV. */
    const val NAME = "store.enabled"

    /**
     * Priorité : réglage W12 ([settingsValue], null = non réglé) > ordre signé `flag.set` ([flagOrder], null = aucun) > [compiledDefault].
     * KDoc : « défaut compilé faux en release, vrai en debug : décision D-W17-10 » (le défaut est choisi par l'appelant, selon la variante).
     */
    fun enabled(settingsValue: Boolean?, flagOrder: Boolean?, compiledDefault: Boolean): Boolean = settingsValue ?: flagOrder ?: compiledDefault
}
