package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.WalletReason
import castbridge.core.wallet.WalletView
import java.time.ZoneId

/**
 * L'état du portefeuille de CETTE TV, tel que l'écran et la carte le disent (précision du propriétaire, 2026-10-04 : « toute activation donne lieu à un portefeuille, il est autonome et hors
 * ligne mais sa mise à jour dépend du serveur »). Le portefeuille existe dès l'activation ; la TV ne calcule JAMAIS un solde ni une attribution (elle ne crée aucune valeur) : avant la première
 * synchronisation il n'y a AUCUN chiffre ; ensuite le dernier instantané signé reste affichable hors ligne ; au-delà de 35 jours un bandeau dit que le serveur doit mettre à jour.
 */
object WalletStatus {
    enum class State { NEVER_SYNCED, WAITING_ACTIVATION, LICENSE_PENDING, SYNCED, OFFLINE, STALE }

    /** Un instantané plus vieux que cela (échéance mensuelle des attributions + marge) : « Mise à jour du serveur attendue ». */
    const val STALE_MS = 35L * 24 * 3600 * 1000
    const val NEVER_TEXT = "Portefeuille créé à l'activation : en attente de la première synchronisation"
    const val PENDING_TEXT = "Jetons en attente de notification de votre activation"
    const val STALE_BANNER = "Mise à jour du serveur attendue"

    /** [showBalances] : seul un instantané signé autorise des chiffres. [line] : la phrase d'état ; [banners] : avis à afficher au-dessus des soldes. */
    data class View(val state: State, val showBalances: Boolean, val line: String, val banners: List<String>)

    /**
     * @param snapshot dernier `cbw1` vérifié (ou null) ; @param online le dernier échange avec le serveur a abouti ; @param nowMs heure de la TV ;
     * @param lastReason motif fermé du dernier refus de synchronisation (`ACTIVATE`, `LICENSE_PENDING`…) ou null ; @param licenseState `edition.license` de la dernière synchronisation ou null.
     */
    fun of(snapshot: Snapshot?, online: Boolean, nowMs: Long, lastReason: String?, licenseState: String?, zone: ZoneId): View {
        val pending = lastReason == "LICENSE_PENDING" || licenseState == "PENDING"
        if (snapshot == null) return when {
            pending -> View(State.LICENSE_PENDING, false, PENDING_TEXT, emptyList())
            lastReason == "ACTIVATE" -> View(State.WAITING_ACTIVATION, false, WalletReason.ACTIVATE.text(), emptyList())
            else -> View(State.NEVER_SYNCED, false, NEVER_TEXT, emptyList())
        }
        val stale = nowMs - snapshot.at > STALE_MS
        val banners = buildList { if (stale) add(STALE_BANNER); if (pending) add(PENDING_TEXT) }
        val line = if (online) "Soldes " + WalletView.asOf(snapshot.at, zone) else WalletView.offlineLine(snapshot, zone)
        return View(if (stale) State.STALE else if (online) State.SYNCED else State.OFFLINE, true, line, banners)
    }

    /** La ligne de la carte d'accueil : les soldes signés, ou « en attente » SANS chiffre. */
    fun cardLine(v: View, snapshot: Snapshot?): String = when {
        v.showBalances -> WalletView.balanceLine(snapshot)
        v.state == State.LICENSE_PENDING -> "En attente de notification"
        else -> "En attente de synchronisation"
    }
}
