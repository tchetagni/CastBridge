package castbridge.core.tv.status

import castbridge.core.net.NetState
import castbridge.core.wallet.ui.WalletStatus

/** Traduit ce que la TV sait déjà (état réseau, portefeuille, volumes, Quiz) en mesures de [StatusSnapshot]. Pur : la lecture Android reste dans le récepteur. */
object StatusFeedRules {
    /** Jetons : jamais synchronisé (y compris en attente d'activation ou de notification), synchronisé, ou serveur hors ligne / instantané trop ancien. */
    fun tokens(s: WalletStatus.State): TokensSync = when (s) {
        WalletStatus.State.NEVER_SYNCED, WalletStatus.State.WAITING_ACTIVATION, WalletStatus.State.LICENSE_PENDING -> TokensSync.NEVER
        WalletStatus.State.SYNCED -> TokensSync.SYNCED
        WalletStatus.State.OFFLINE, WalletStatus.State.STALE -> TokensSync.OFFLINE
    }

    /** « En attente de notification de l'activation » : la source existante est l'état du portefeuille. */
    fun licencePending(s: WalletStatus.State): Boolean = s == WalletStatus.State.LICENSE_PENDING

    /** Internet : « en vérification » sans sonde réelle veut dire « non testé » (gris), jamais un bleu qui dure. */
    fun internetPath(state: NetState, measured: Boolean): InternetPath = when (state) {
        NetState.CHECKING -> if (measured) InternetPath.CHECKING else InternetPath.UNTESTED
        NetState.INTERNET_WIFI, NetState.INTERNET_ETHERNET -> InternetPath.DIRECT
        NetState.INTERNET_VIA_PHONE -> InternetPath.VIA_PHONE
        NetState.NONE -> InternetPath.NONE
    }

    /** Quiz en ligne : drapeau éteint = pas de pastille ; TV non activée = rouge ; sinon l'état du dernier sondage du service (null = pas sondé = pas de pastille). */
    fun quiz(flagOn: Boolean, locked: Boolean, serviceUp: Boolean?): QuizLink = when {
        !flagOn -> QuizLink.IDLE
        locked -> QuizLink.NOT_ACTIVATED
        serviceUp == true -> QuizLink.CONNECTED
        serviceUp == false -> QuizLink.SERVER_UNREACHABLE
        else -> QuizLink.IDLE
    }

    fun bluetoothPhase(phoneLinkedByBluetooth: Boolean, gatewayConnected: Boolean): BtPhase = when {
        phoneLinkedByBluetooth -> BtPhase.PHONE_LINKED
        gatewayConnected -> BtPhase.GATEWAY_ONLY
        else -> BtPhase.IDLE
    }

    data class Vol(val free: Long, val total: Long, val writable: Boolean)
    data class StorageReading(val present: Boolean, val free: Long, val total: Long, val readOnly: Boolean)

    /** Le stockage à dire : le volume le plus serré (part libre la plus faible) ; un volume en lecture seule suffit à rougir ; aucun volume mesurable = absent. */
    fun storage(volumes: List<Vol>): StorageReading {
        val usable = volumes.filter { it.total > 0 }
        if (usable.isEmpty()) return StorageReading(false, 0, 0, false)
        val worst = usable.minByOrNull { it.free.coerceIn(0, it.total).toDouble() / it.total }!!
        return StorageReading(true, worst.free, worst.total, usable.any { !it.writable })
    }
}
