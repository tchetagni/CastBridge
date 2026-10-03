package castbridge.core.link

import castbridge.core.tv.WifiDirect
import castbridge.core.ux.SignalLevel

/**
 * « Seul le Bluetooth : Wi-Fi Direct automatique » (demande du propriétaire du 2026-10-03, docs/agent-reports/auto-wifi-direct.md, R-14).
 *
 * Quand le téléphone et la TV ne se parlent que par Bluetooth (aucun réseau commun, ou un réseau qui isole ses clients), un gros envoi ne doit pas
 * ramper à 100-300 ko/s : la TV crée un groupe Wi-Fi Direct à la demande du téléphone (CBTN, mot de passe frais transmis par le lien Bluetooth
 * chiffré), le téléphone le rejoint sans aucune action dans le cas normal, l'envoi passe par lui (dizaines de Mo/s) ; le Bluetooth reste la voie de
 * contrôle et de repli. Tout ce qui DÉCIDE est ici, pur et testé ; le téléphone et la TV n'exécutent que des effets.
 */

/** Où en est la permission dont le téléphone a besoin pour rejoindre le groupe ([WdJoin.permission]). */
enum class WdPermission { GRANTED, NOT_NEEDED, NOT_ASKED, ASKING, DENIED }

/** Comment le téléphone rejoint le groupe de la TV. */
enum class JoinMethod {
    /** API 33+ : `WifiP2pManager.connect` avec `WifiP2pConfig.Builder().setNetworkName().setPassphrase()` : aucune boîte de dialogue, ni sur le téléphone ni sur la TV. */
    P2P_CONNECT,
    /** API 29-32 : `WifiNetworkSpecifier` : Android montre SA boîte « Se connecter à l'appareil ? » (la voie P2P exigerait la localisation, refusée par conception). */
    NETWORK_SPECIFIER,
    /** Android 8-9 : pas de jonction par programme. */
    NONE,
}

object WdJoin {
    const val MIN_API = 29
    const val NEARBY_API = 33
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"

    fun method(api: Int): JoinMethod = when {
        api >= NEARBY_API -> JoinMethod.P2P_CONNECT
        api >= MIN_API -> JoinMethod.NETWORK_SPECIFIER
        else -> JoinMethod.NONE
    }

    /** La permission d'exécution à demander (juste à temps, une fois) ; null = aucune. Jamais la localisation (DESIGN-W7 § permissions). */
    fun permission(api: Int): String? = if (method(api) == JoinMethod.P2P_CONNECT) NEARBY_WIFI_DEVICES else null

    /** Android affiche-t-il une boîte de confirmation à chaque jonction ? */
    fun systemDialog(m: JoinMethod): Boolean = m == JoinMethod.NETWORK_SPECIFIER

    /** P2P : 20 s suffisent (association WPA2 + DHCP) ; boîte système : le temps que l'usager touche « Se connecter ». */
    fun joinTimeoutMs(m: JoinMethod): Long = when (m) { JoinMethod.P2P_CONNECT -> 20_000L; JoinMethod.NETWORK_SPECIFIER -> 50_000L; JoinMethod.NONE -> 0L }
}

/** L'adresse HTTP de la TV dans le groupe : celle que donne Android (`WifiP2pInfo.groupOwnerAddress`), sinon celle que dit la TV, sinon 192.168.49.1. */
object WdAddress {
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /** Littéral IPv4 privé seulement : jamais un nom à résoudre, jamais une adresse publique. */
    fun usable(ip: String?): Boolean {
        val m = ip?.let { IPV4.matchEntire(it) } ?: return false
        val o = m.groupValues.drop(1).map { it.toInt() }
        if (o.any { it > 255 }) return false
        return o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168)
    }

    fun base(goIp: String?, tvIp: String?, port: Int): String {
        val ip = listOf(goIp, tvIp).firstOrNull(::usable) ?: WifiDirect.GROUP_OWNER_IP
        return "http://$ip:$port"
    }
}

/** Ligne d'état honnête de l'envoi : la couleur suit la signalétique de la TV (core/ux/TvSignal.kt) : vert = marche, orange = dégradé, rouge = rien ne marche. */
data class StateLine(val level: SignalLevel, val text: String, val detail: String? = null)

/**
 * Échecs récents de Wi-Fi Direct : 3 échecs en moins de 10 minutes ⇒ Bluetooth pendant 10 minutes, puis un nouvel essai est permis.
 * Jamais de boucle : un groupe qui ne monte pas ne fait pas recommencer sans fin.
 */
data class WdBackoff(val failures: Int = 0, val lastFailureAt: Long = 0, val until: Long = 0) {
    fun failed(now: Long): WdBackoff {
        val n = if (failures > 0 && now - lastFailureAt > WINDOW_MS) 1 else failures + 1
        return if (n >= MAX_FAILURES) WdBackoff(0, now, now + PAUSE_MS) else WdBackoff(n, now, until)
    }
    fun succeeded() = WdBackoff()
    fun blocked(now: Long) = now < until

    companion object {
        const val MAX_FAILURES = 3
        const val PAUSE_MS = 10 * 60_000L
        const val WINDOW_MS = 10 * 60_000L
    }
}

object BulkRoute {
    /** En dessous, le Bluetooth suffit (un lot, une image) : monter un groupe coûterait plus que l'envoi. */
    const val MIN_WD_BYTES = 5L shl 20
    /** Un même fichier ne change de voie après une perte du groupe que 2 fois. */
    const val MAX_REROUTES = 2

    /**
     * Ce que le téléphone sait au moment d'envoyer un fichier en masse (copie, copie-et-lecture, déplacement, file).
     * [lanAlive] : la session de contrôle passe par un réseau commun qui répond. [isolated] : la TV annonce une adresse du même sous-réseau qui ne répond
     * pas (DESIGN-W7 § 4.3). [tvOffersWd] : capacité `wd.cap` dite par la TV (null = TV ancienne, on essaie). [tvWdError] : la cause que la TV a donnée
     * ([WifiDirect.Err]). [backoffUntil] : [WdBackoff.until]. [wdUp] : un groupe est déjà rejoint et répond.
     */
    data class Facts(
        val bytes: Long,
        val lanAlive: Boolean,
        val btConnected: Boolean,
        val api: Int,
        val permission: WdPermission,
        val now: Long,
        val isolated: Boolean = false,
        val phoneWifiOn: Boolean = true,
        val tvOffersWd: Boolean? = null,
        val tvWdError: String? = null,
        val autoWifiDirect: Boolean = true,
        val trialTv: Boolean = false,
        val wdUp: Boolean = false,
        val backoffUntil: Long = 0,
        val wifiAskedOnce: Boolean = false,
        val foreground: Boolean = true,
    )

    enum class Why { SMALL, SETTING_OFF, TRIAL, PHONE_TOO_OLD, TV_NO_WD, TV_WIFI_OFF, BACKOFF, PERMISSION_DENIED, PERMISSION_PENDING, PHONE_WIFI_OFF, BACKGROUND }
    enum class Ask { PERMISSION, PHONE_WIFI }

    sealed class Decision {
        object UseLan : Decision() { override fun toString() = "UseLan" }
        /** [start] = monter le groupe (sinon il est déjà là). */
        data class UseWd(val start: Boolean) : Decision()
        data class UseBt(val why: Why) : Decision()
        data class Wait(val why: Why) : Decision()
        data class AskOnce(val what: Ask) : Decision()
        object NoRoute : Decision() { override fun toString() = "NoRoute" }
    }

    fun decide(f: Facts): Decision {
        if (f.lanAlive && !f.isolated) return Decision.UseLan                    // jamais remplacer un réseau commun qui marche
        if (f.wdUp) return Decision.UseWd(start = false)
        if (!f.btConnected) return Decision.NoRoute
        if (f.trialTv || f.tvWdError == WifiDirect.Err.TRIAL) return Decision.UseBt(Why.TRIAL)
        if (f.bytes < MIN_WD_BYTES) return Decision.UseBt(Why.SMALL)
        if (!f.autoWifiDirect) return Decision.UseBt(Why.SETTING_OFF)
        if (WdJoin.method(f.api) == JoinMethod.NONE) return Decision.UseBt(Why.PHONE_TOO_OLD)
        if (f.tvWdError == WifiDirect.Err.WIFI_OFF) return Decision.UseBt(Why.TV_WIFI_OFF)
        if (f.tvOffersWd == false) return Decision.UseBt(Why.TV_NO_WD)
        if (f.now < f.backoffUntil) return Decision.UseBt(Why.BACKOFF)
        if (!f.phoneWifiOn) return if (!f.wifiAskedOnce && f.foreground) Decision.AskOnce(Ask.PHONE_WIFI) else Decision.UseBt(Why.PHONE_WIFI_OFF)
        return when (f.permission) {
            WdPermission.GRANTED, WdPermission.NOT_NEEDED -> Decision.UseWd(start = true)
            WdPermission.ASKING -> Decision.Wait(Why.PERMISSION_PENDING)
            WdPermission.NOT_ASKED -> if (f.foreground) Decision.AskOnce(Ask.PERMISSION) else Decision.UseBt(Why.BACKGROUND)
            WdPermission.DENIED -> Decision.UseBt(Why.PERMISSION_DENIED)
        }
    }

    /** Après l'échec d'un envoi par Wi-Fi Direct : le remettre dans la file pour une nouvelle décision (re-jonction ou Bluetooth) ? Seulement si le groupe est tombé. */
    fun rerouteAfterLoss(viaWd: Boolean, groupLost: Boolean, reroutes: Int): Boolean = viaWd && groupLost && reroutes < MAX_REROUTES

    /** Une phrase, en français, pour la ligne d'état et la file. */
    fun explain(why: Why): String = when (why) {
        Why.SMALL -> "Petit fichier : le Bluetooth suffit."
        Why.SETTING_OFF -> "Wi-Fi Direct automatique désactivé dans les Réglages : envoi par Bluetooth."
        Why.TRIAL -> "Version d'essai de la TV : pas de Wi-Fi Direct."
        Why.PHONE_TOO_OLD -> "Ce téléphone (Android 9 ou plus ancien) ne peut pas rejoindre la TV en Wi-Fi Direct : envoi par Bluetooth."
        Why.TV_NO_WD -> "Cette TV ne propose pas le Wi-Fi Direct : envoi par Bluetooth."
        Why.TV_WIFI_OFF -> "Le Wi-Fi de la TV est éteint : allumez-le (sans le connecter) pour un envoi rapide. Envoi par Bluetooth."
        Why.BACKOFF -> "Wi-Fi Direct n'a pas pu s'établir (3 essais) : envoi par Bluetooth, nouvel essai dans 10 minutes."
        Why.PERMISSION_DENIED -> "Autorisation « Appareils à proximité » refusée : envoi par Bluetooth (lent). Réglages de l'app › Autorisations pour l'accorder."
        Why.PERMISSION_PENDING -> "En attente de l'autorisation « Appareils à proximité »…"
        Why.PHONE_WIFI_OFF -> "Le Wi-Fi du téléphone est éteint : allumez-le (sans réseau) pour un envoi rapide. Envoi par Bluetooth."
        Why.BACKGROUND -> "CastBridge n'est pas à l'écran : envoi par Bluetooth (ouvrez CastBridge une fois pour autoriser le Wi-Fi Direct)."
    }
}

/** Les mots de la ligne d'état de l'envoi (docs/agent-reports/auto-wifi-direct.md § « Ligne d'état »). */
object BulkLine {
    const val WD_RUNNING = "Par Wi-Fi Direct (automatique)"
    const val BT_SLOW = "Bluetooth seulement : lent"
    const val WD_PREPARING = "$BT_SLOW · Wi-Fi Direct en préparation…"
    const val LAN = "Par le Wi-Fi (réseau commun)"
    const val BT_SMALL = "Par Bluetooth"
    const val NOTHING = "TV injoignable : ni Wi-Fi ni Bluetooth"

    fun of(d: BulkRoute.Decision, wd: WdClient.State?): StateLine = when (d) {
        BulkRoute.Decision.UseLan -> StateLine(SignalLevel.GREEN, LAN)
        BulkRoute.Decision.NoRoute -> StateLine(SignalLevel.RED, NOTHING)
        is BulkRoute.Decision.UseWd -> when (wd) {
            is WdClient.State.Up -> StateLine(SignalLevel.GREEN, WD_RUNNING)
            is WdClient.State.Failed -> StateLine(SignalLevel.ORANGE, BT_SLOW, WdClient.explain(wd.fail, wd.detail))
            else -> StateLine(SignalLevel.ORANGE, WD_PREPARING)
        }
        is BulkRoute.Decision.UseBt -> if (d.why == BulkRoute.Why.SMALL) StateLine(SignalLevel.GREEN, BT_SMALL, BulkRoute.explain(d.why))
            else StateLine(SignalLevel.ORANGE, BT_SLOW, (wd as? WdClient.State.Failed)?.let { WdClient.explain(it.fail, it.detail) + " " }.orEmpty() + BulkRoute.explain(d.why))
        is BulkRoute.Decision.Wait -> StateLine(SignalLevel.ORANGE, BT_SLOW, BulkRoute.explain(d.why))
        is BulkRoute.Decision.AskOnce -> StateLine(SignalLevel.ORANGE, BT_SLOW,
            if (d.what == BulkRoute.Ask.PERMISSION) "Autorisez « Appareils à proximité » : la copie passera par Wi-Fi Direct (rapide)."
            else "Allumez le Wi-Fi du téléphone (inutile de choisir un réseau) : la copie passera par Wi-Fi Direct.")
    }
}
