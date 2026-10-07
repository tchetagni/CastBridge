package castbridge.core.relay

import castbridge.core.connect.NetState

/**
 * Le drapeau « réseau facturé » que le téléphone dit à la TV (`RELAY_STATE`, [RelayFrames.State.metered]) : vrai = facturé, faux = non facturé, null = INCONNU (aucun réseau validé : une
 * transition Wi-Fi / données mobiles, un portail captif). Une seule définition pour le démarrage, l'état envoyé et la surveillance.
 */
fun PhoneNet.meteredFlag(): Boolean? = when (this) {
    PhoneNet.NONE -> null
    PhoneNet.METERED -> true
    PhoneNet.UNMETERED -> false
}

/**
 * R-33 (audit anti-régression 2026-10-07 b, I-13) : la garde de coût REL-F7 (« 0 octet de gros volume sur un réseau facturé, le plafond du petit volume respecté ») était percée parce que
 * « inconnu » valait « autorisé ». Règle commune : **un réseau dont on ne sait pas s'il est facturé est FACTURÉ**.
 *  - côté téléphone : [countsAgainstCap] (les octets d'une transition sans réseau validé comptent contre les 5 Mo du jour) ;
 *  - côté TV : [tvBulkAllowed] décide si les tâches de fond volumineuses (mise à jour de l'application, questions, lots) peuvent partir par le tuyau d'un téléphone. À BRANCHER dans
 *    `TvNet.backgroundBulkAllowed` (fichier de la TV : chantier fix-tv) ; la valeur doit y être gardée PAR téléphone et rester « inconnue » tant que ce téléphone ne l'a pas dite.
 */
object RelayCost {
    /** Facturé tant que le téléphone n'a pas dit que ce n'est PAS le cas (null = inconnu = facturé). */
    fun billed(metered: Boolean?): Boolean = metered != false

    /** Les octets du tuyau s'ajoutent-ils au compteur du jour ? Tout réseau qui n'est pas SÛREMENT gratuit compte, sauf le réglage « Données mobiles pour la TV » (qui lève le plafond). */
    fun countsAgainstCap(net: PhoneNet, allowMobile: Boolean): Boolean = !allowMobile && net != PhoneNet.UNMETERED

    /**
     * Les tâches de fond volumineuses de la TV peuvent-elles partir maintenant ? Seul le tuyau d'un téléphone les retient ([NetState.VIA_RELAY]) : une partie en cours, ou un téléphone dont
     * le réseau est facturé OU inconnu. Le réseau propre de la TV ([NetState.DIRECT]) ne les retient jamais ; sans Internet ([NetState.NONE]) elles échouent d'elles-mêmes.
     */
    fun tvBulkAllowed(net: NetState, gameActive: Boolean, phoneMetered: Boolean?): Boolean = !(net == NetState.VIA_RELAY && (gameActive || billed(phoneMetered)))
}

/**
 * Ce que le téléphone dit à la TV de son réseau, sur le canal propriétaire : la TV OUBLIE la valeur à chaque coupure du tuyau (elle ne sait plus si le téléphone est sur données
 * mobiles) et le téléphone qui se reconnecte ne la redisait pas (R-33, I-13). Il la dit donc après chaque poignée de main réussie de la passerelle ([connected] puis [next]) et à chaque
 * changement pendant que le tuyau est ouvert (Wi-Fi puis données mobiles). Au plus une annonce toutes les [minGapMs] : un lien qui se reconnecte en boucle ne fait pas une rafale
 * de connexions Bluetooth vers la TV. Pur : l'heure est donnée.
 */
class MeteredAnnouncer(private val minGapMs: Long = MIN_GAP_MS) {
    private var told: Boolean? = null          // ce que la TV sait depuis la poignée de main en cours ; null = rien
    private var lastAt: Long? = null

    /** Le tuyau vient de se (re)brancher : la TV a tout oublié. */
    @Synchronized fun connected() { told = null }

    /** L'état à dire à la TV maintenant, ou null (déjà dit, réseau inconnu, ou dit à l'instant : le prochain relevé le dira). */
    @Synchronized fun next(net: PhoneNet, nowMs: Long): RelayFrames.State? {
        val metered = net.meteredFlag() ?: return null            // rien de vrai à dire : la TV garde sa dernière valeur, et sans valeur elle compte « facturé » (RelayCost.billed)
        if (told == metered) return null
        lastAt?.let { if (nowMs - it < minGapMs) return null }
        told = metered; lastAt = nowMs
        return RelayFrames.State(RelayFrames.Phase.OPEN, metered = metered)
    }

    companion object { const val MIN_GAP_MS = 5_000L }
}
