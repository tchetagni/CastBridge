package castbridge.core.relay

import castbridge.core.connect.NetState

/**
 * La moitié TV de la garde de coût REL-F7 (R-47, audit anti-régression 2026-10-07 b, I-13 ; la règle commune, côté téléphone aussi, est [RelayCost]) : ce que la TV sait du réseau du
 * téléphone qui lui prête son tuyau.
 *
 * Avant : une valeur GLOBALE et volatile (`TvNet.phoneMetered`), remise à `null` à chaque coupure (le téléphone qui se reconnecte ne redisait rien), `null` AUTORISAIT le fond, et le
 * `RELAY_STATE` de n'importe quel téléphone l'écrasait : une mise à jour de l'application (~40 Mo) ou des paquets de questions passaient par les données mobiles, mangeaient les 5 Mo
 * du jour, puis le jeu était refusé jusqu'à minuit. Maintenant :
 *  - une valeur PAR téléphone (son adresse Bluetooth), valable pour UN tuyau (l'identité du tuyau sur lequel il l'a dite) : le téléphone du tuyau en place n'est jamais jugé sur ce que
 *    dit un autre téléphone, ni sur ce qu'il disait d'un tuyau précédent ;
 *  - oubliée à la coupure du tuyau ([pipeCut]) : la TV ne sait plus rien, et le téléphone qui se reconnecte la redit après sa poignée de main (`MeteredAnnouncer`) ;
 *  - tant que le téléphone du tuyau en place n'a rien dit sur CE tuyau, son réseau est INCONNU, donc facturé ([RelayCost.billed]) : aucun téléchargement de fond ne part.
 *
 * Pur : les adresses sont données normalisées par l'appelant, l'identité du tuyau est `Entry.attaches`.
 */
class TvBulkGate {
    private class Fact(val pipe: Int, val metered: Boolean)
    private val said = HashMap<String, Fact>()

    /** Le téléphone [phone] a dit, sur le tuyau [pipe], que son réseau est facturé ([metered] vrai) ou non. */
    @Synchronized fun said(phone: String, pipe: Int, metered: Boolean) { said[phone] = Fact(pipe, metered) }

    /** Ce que le téléphone [phone] a dit de son réseau SUR le tuyau [pipe] : null = rien dit sur CE tuyau (inconnu), ou pas de téléphone. */
    @Synchronized fun metered(phone: String?, pipe: Int): Boolean? = phone?.let { said[it] }?.takeIf { it.pipe == pipe }?.metered

    /** Le tuyau a été coupé : la TV ne sait plus rien du réseau des téléphones. */
    @Synchronized fun pipeCut() { said.clear() }

    /**
     * Les tâches de fond volumineuses (mise à jour, questions, lots) peuvent-elles partir ? [pipePhone] et [pipe] : le téléphone du tuyau en place et l'identité de ce tuyau (null et -1
     * sans tuyau). La décision est la règle commune [RelayCost.tvBulkAllowed].
     */
    fun bulkAllowed(net: NetState, gameActive: Boolean, pipePhone: String?, pipe: Int): Boolean = RelayCost.tvBulkAllowed(net, gameActive, metered(pipePhone, pipe))
}
