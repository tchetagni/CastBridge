package castbridge.core.connect

/**
 * « La TV a-t-elle Internet, et par où ? » : LA vérité unique de CastBridge-TV (relay-R1, docs/coordination/DESIGN-RELAIS-TELEPHONE-2026-10-07.md § 2.4).
 * Elle remplace les quatre définitions d'avant (portefeuille, jeu, Langues, tunnel : inventaire I-3) : tous lisent celle-ci.
 *
 * - [DIRECT]    : le réseau propre de la TV atteint Internet (validé par le système, ou le serveur CastBridge a répondu en direct il y a peu).
 * - [VIA_RELAY] : le tuyau d'un téléphone synchronisé est ouvert ET une vérification de bout en bout a réussi à travers lui.
 * - [NONE]      : ni l'un ni l'autre.
 *
 * Ne pas confondre avec `castbridge.core.net.NetState`, qui est l'état LISSÉ de la pastille « Internet » de l'accueil (CHECKING, Wi-Fi, Ethernet…) :
 * celui-ci sert aux DÉCISIONS (ouvrir une partie, synchroniser le portefeuille, choisir le chemin du tunnel, demander un tuyau).
 */
enum class NetState(val wire: String) {
    DIRECT("direct"),
    VIA_RELAY("via_relay"),
    NONE("none");

    /** Internet est utilisable (par le réseau propre ou par le téléphone). */
    val up: Boolean get() = this != NONE

    companion object {
        fun fromWire(w: String?): NetState? = values().firstOrNull { it.wire == w }
    }
}

/** Ce que les mesures disent, sans décision (rempli par l'application de la TV à partir de `TvService` et de `TvConnect`). */
data class NetFacts(
    /** La TV a mesuré au moins une fois (avant : on ne la dit pas hors ligne). */
    val checked: Boolean,
    /** Le réseau propre de la TV (Wi-Fi, Ethernet…) est là, même sans Internet derrière. */
    val linkUp: Boolean,
    /** Le système dit « Internet validé » sur le réseau propre, ou une sonde réelle a répondu. */
    val directValidated: Boolean,
    /** Le serveur CastBridge a répondu EN DIRECT il y a peu (battement de cœur) : preuve que le réseau propre sort, même si le système ne l'a pas validé. */
    val directContactRecent: Boolean,
    /** Un téléphone est attaché au tuyau Bluetooth. */
    val relayConnected: Boolean,
    /** Une vérification de bout en bout (vers le serveur CastBridge) a réussi à travers ce tuyau. */
    val relayConfirmed: Boolean,
)

object NetStates {
    /** Une réponse directe du serveur reste une preuve 20 minutes (le battement de cœur est toutes les 15 min). */
    const val CONTACT_FRESH_MS = 20 * 60_000L

    fun of(f: NetFacts): NetState = when {
        f.directValidated || (f.linkUp && f.directContactRecent) -> NetState.DIRECT
        f.relayConnected && f.relayConfirmed -> NetState.VIA_RELAY
        else -> NetState.NONE
    }

    /** La forme courte pour le tunnel et les tests : réseau propre validé, tuyau attaché, tuyau vérifié. */
    fun of(directOk: Boolean, relayConnected: Boolean, relayOk: Boolean): NetState =
        of(NetFacts(checked = true, linkUp = true, directValidated = directOk, directContactRecent = false, relayConnected = relayConnected, relayConfirmed = relayOk))

    /** Avant la première mesure, la TV ne se dit pas hors ligne (même optimisme qu'avant pour le portefeuille). */
    fun reachable(f: NetFacts): Boolean = !f.checked || of(f).up

    /**
     * Le dernier contact avec le serveur a été fait EN DIRECT, a réussi, et date de moins de [CONTACT_FRESH_MS] (une date dans le futur ne compte pas). [directFailedAt] : date du dernier
     * appel réel ÉCHOUÉ sur le réseau propre (0 = aucun, [NetProbePlan.directFailedAt]) ; un échec POSTÉRIEUR au contact annule la preuve (R-41, I-2) : la box a perdu Internet depuis.
     */
    fun contactRecent(lastOk: Boolean, via: String?, lastAt: Long, now: Long, directFailedAt: Long = 0L): Boolean =
        lastOk && via == Routes.Via.DIRECT.key && lastAt > 0 && lastAt <= now && now - lastAt <= CONTACT_FRESH_MS && !(directFailedAt > lastAt)
}
