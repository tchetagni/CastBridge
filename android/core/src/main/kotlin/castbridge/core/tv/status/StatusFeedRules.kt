package castbridge.core.tv.status

import castbridge.core.net.NetState
import castbridge.core.tv.StoragePolicy
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.VolumeKind
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

    /** Un volume que la TV connaît. [usb] : une clé amovible (sinon la mémoire interne) ; [id] : son identifiant dans le registre des volumes de la TV. */
    data class Vol(val free: Long, val total: Long, val writable: Boolean, val usb: Boolean = false, val id: String = "")
    /** Ce que dit la pastille « Stockage » : le volume décrit ([usb] : c'est la clé), s'il est en lecture seule, et l'alerte rouge éventuelle d'un AUTRE volume. */
    data class StorageReading(val present: Boolean, val free: Long, val total: Long, val readOnly: Boolean, val usb: Boolean = false, val other: OtherStorage = OtherStorage.NONE)

    /**
     * Le volume DESTINATION des copies, celui dont la pastille « Stockage » dit l'espace restant (décision du propriétaire du 2026-10-18) : le volume où irait le prochain fichier selon la politique de la TV
     * ([StoragePolicy.plan], cible [target] = `auto`, `internal` ou l'identifiant d'une clé), donc la clé USB quand elle est montée, inscriptible et choisie (la plus libre en `auto`, hors clé lente déclassée),
     * sinon la mémoire interne. Une clé choisie à la main reste la destination même en lecture seule (les copies y sont refusées : la pastille doit le dire). Un dossier choisi (SAF) ne se mesure pas :
     * mémoire interne. Null quand rien ne peut recevoir (ni clé choisie, ni candidat, ni mémoire interne).
     */
    fun destinationId(volumes: List<StorageVolume>, target: String): String? {
        volumes.firstOrNull { it.id == target && it.kind == VolumeKind.REMOVABLE }?.let { return it.id }
        val planned = StoragePolicy.plan(volumes, target, 0, 0).candidates.firstOrNull()?.volume
        return (planned?.takeIf { it.kind != VolumeKind.SAF } ?: volumes.firstOrNull { it.kind == VolumeKind.INTERNAL })?.id
    }

    /** Rouge pour un volume : lecture seule, ou moins de 5 % libre (le seuil exact de [StatusRules.storage]). */
    private fun red(v: Vol) = !v.writable || v.free.coerceIn(0, v.total) * 100 < v.total * StatusThresholds.STORAGE_WARN_FREE_PERCENT

    /**
     * Le stockage à dire : le volume [destinationId] (la clé USB quand elle reçoit les copies) ; sans destination connue, absente ou non mesurable, la mémoire interne. Un volume en lecture seule suffit à rougir
     * quand c'est le volume décrit. Un AUTRE volume rouge (lecture seule, ou moins de 5 % libre) ne change pas le volume décrit : il est signalé dans [StorageReading.other] (la mémoire interne d'abord).
     * Aucun volume mesurable = absent.
     */
    fun storage(volumes: List<Vol>, destinationId: String? = null): StorageReading {
        val usable = volumes.filter { it.total > 0 }
        if (usable.isEmpty()) return StorageReading(false, 0, 0, false)
        val di = usable.indexOfFirst { destinationId != null && it.id == destinationId }.takeIf { it >= 0 } ?: usable.indexOfFirst { !it.usb }.takeIf { it >= 0 } ?: 0
        val dest = usable[di]
        val others = usable.filterIndexed { i, v -> i != di && red(v) }.sortedBy { it.usb }
        val other = others.firstOrNull()?.let { v ->
            when { v.usb && !v.writable -> OtherStorage.USB_READ_ONLY; v.usb -> OtherStorage.USB_FULL; !v.writable -> OtherStorage.INTERNAL_READ_ONLY; else -> OtherStorage.INTERNAL_FULL }
        } ?: OtherStorage.NONE
        return StorageReading(true, dest.free, dest.total, !dest.writable, dest.usb, other)
    }
}
