package castbridge.core.link

import castbridge.core.tv.WifiDirect
import castbridge.core.ux.SignalLevel

/**
 * Le bouton « Wi-Fi Direct » de la carte TV de CastBridge (demande du propriétaire du 2026-10-03, docs/agent-reports/wd-manual-button.md) : un déclencheur
 * EXPLICITE du Wi-Fi Direct quand le lien Bluetooth et le code sont établis, avec la cause en français quand il ne peut pas partir. Tout ce qui décide est ici,
 * pur et testé ; l'écran ([castbridge.sender.WdManualCard]) ne fait que montrer la [View] et appeler `AutoWifiDirect.startNow`.
 */
object WdManualView {
    const val TITLE = "Wi-Fi Direct"
    const val HINT = "Relier la TV directement, sans box ni Wi-Fi"
    const val ACTIVE_TITLE = "Wi-Fi Direct actif · Arrêter"
    const val LINE_ON = "Par Wi-Fi Direct"
    const val LINE_PREPARING = "Mise en place…"
    const val LAN_CONFIRM = "Un réseau commun fonctionne déjà : utiliser quand même Wi-Fi Direct ?"
    const val MANUAL_KEEP = "Reste actif jusqu'à ce que vous l'arrêtiez (ou 10 minutes hors de cet écran)."

    /** Ce que le téléphone sait de cette TV maintenant. [credentialValid] : un code ou un jeton utilisable (jamais sa valeur ici). */
    data class Facts(
        val hasTv: Boolean = true,
        /** La TV est appairée en Bluetooth (liaison système) : sans cela, rien ne part. */
        val btBonded: Boolean,
        /** La liaison de contrôle Bluetooth a répondu (session de confiance en cours). */
        val btLinked: Boolean,
        val credentialValid: Boolean,
        /** Capacité `wd` dite par la TV (HELLO) ; null = TV ancienne qui ne le dit pas : on ne propose pas. */
        val tvOffersWd: Boolean?,
        val api: Int,
        val permission: WdPermission,
        val phoneWifiOn: Boolean = true,
        /** [WifiDirect.Err] retenu pour cette TV : seul `trial` est définitif ; les autres causes se réessaient à la main. */
        val tvWdError: String? = null,
        val trialTv: Boolean = false,
        /** Un réseau commun répond déjà (jamais 192.168.49.x). */
        val lanAlive: Boolean = false,
        /** Un profil enfant est actif sur la TV : le bouton reste utilisable (aucune donnée n'est touchée), seule la mesure sur fichier est omise. */
        val tvChildProfile: Boolean = false,
        /** État de l'automate ([WdClient.State]) et vrai quand la session est celle de ce bouton. */
        val state: WdClient.State? = null,
        val manual: Boolean = false,
    )

    enum class Cause { NO_BOND, BT_NOT_LINKED, NO_CODE, NO_WD, TRIAL, PHONE_TOO_OLD, PERMISSION_DENIED, PERMISSION_PENDING, PHONE_WIFI_OFF }

    /** L'unique action proposée avec une cause. */
    enum class Action { PAIR_BLUETOOTH, RETRY_LINK, ENTER_CODE, GRANT_PERMISSION, OPEN_WIFI, NONE }

    sealed class View {
        /** Aucune TV choisie : rien à montrer. */
        object Hidden : View() { override fun toString() = "Hidden" }
        /** Bouton gris : la [cause] en français et une [action]. */
        data class Disabled(val cause: Cause, val text: String, val action: Action, val actionLabel: String?) : View()
        /**
         * Bouton actif « Wi-Fi Direct ». [confirmLan] : un réseau commun marche, la question est posée avant ([LAN_CONFIRM]). [askPermission] : la permission
         * sera demandée au toucher, une fois. [last] : la ligne rouge de la dernière tentative ratée (null = rien).
         */
        data class Ready(val confirmLan: Boolean, val askPermission: Boolean, val last: StateLine? = null) : View()
        /** Mise en place (orange) : le bouton propose « Annuler ». */
        data class Working(val line: StateLine) : View()
        /** Connecté (vert) : « Wi-Fi Direct actif · Arrêter ». */
        data class Active(val line: StateLine, val detail: String?) : View()
    }

    fun of(f: Facts): View {
        if (!f.hasTv) return View.Hidden
        // une session en cours (manuelle ou automatique) passe avant toute cause : l'usager voit son état et peut l'arrêter
        when (val s = f.state) {
            is WdClient.State.Requesting, is WdClient.State.Joining, is WdClient.State.Probing -> return View.Working(StateLine(SignalLevel.ORANGE, LINE_PREPARING))
            is WdClient.State.Up -> return View.Active(StateLine(SignalLevel.GREEN, LINE_ON), if (f.manual) MANUAL_KEEP else "Ouvert automatiquement : il se ferme 30 s après la fin des envois.")
            else -> {}
        }
        if (!f.btBonded) return disabled(Cause.NO_BOND, "Associez d'abord la TV en Bluetooth", Action.PAIR_BLUETOOTH, "Associer la TV")
        if (!f.btLinked) return disabled(Cause.BT_NOT_LINKED, "La liaison Bluetooth avec la TV n'est pas encore établie", Action.RETRY_LINK, "Réessayer")
        if (!f.credentialValid) return disabled(Cause.NO_CODE, "Entrez le code de la TV", Action.ENTER_CODE, "Entrer le code")
        if (f.trialTv || f.tvWdError == WifiDirect.Err.TRIAL) return disabled(Cause.TRIAL, "Version d'essai de la TV : pas de Wi-Fi Direct", Action.NONE, null)
        if (f.tvOffersWd != true) return disabled(Cause.NO_WD, "Cette TV ne propose pas Wi-Fi Direct", Action.NONE, null)
        if (WdJoin.method(f.api) == JoinMethod.NONE) return disabled(Cause.PHONE_TOO_OLD, "Ce téléphone (Android 9 ou plus ancien) ne peut pas rejoindre la TV en Wi-Fi Direct", Action.NONE, null)
        when (f.permission) {
            WdPermission.DENIED -> return disabled(Cause.PERMISSION_DENIED, "Autorisez les appareils à proximité", Action.GRANT_PERMISSION, "Autoriser")
            WdPermission.ASKING -> return disabled(Cause.PERMISSION_PENDING, "En attente de l'autorisation « Appareils à proximité »…", Action.NONE, null)
            else -> {}
        }
        if (!f.phoneWifiOn) return disabled(Cause.PHONE_WIFI_OFF, "Allumez le Wi-Fi du téléphone (inutile de choisir un réseau)", Action.OPEN_WIFI, "Ouvrir le Wi-Fi")
        val last = (f.state as? WdClient.State.Failed)?.let { StateLine(SignalLevel.RED, WdClient.explain(it.fail, it.detail)) }
        return View.Ready(confirmLan = f.lanAlive, askPermission = f.permission == WdPermission.NOT_ASKED, last = last)
    }

    private fun disabled(c: Cause, text: String, a: Action, label: String?) = View.Disabled(c, text, a, label)

    /** Ce que fait le toucher sur le bouton. */
    sealed class Start {
        /** Rien à faire : la cause est déjà montrée (jamais de contournement du code ni de la liaison). */
        data class Refuse(val cause: Cause) : Start()
        /** Poser [LAN_CONFIRM] d'abord. */
        object ConfirmLan : Start() { override fun toString() = "ConfirmLan" }
        /** Demander « Appareils à proximité » (juste à temps, une fois) puis repartir. */
        object AskPermission : Start() { override fun toString() = "AskPermission" }
        object Go : Start() { override fun toString() = "Go" }
        /** Un groupe est déjà là ou en cours : le toucher n'en monte pas un second. */
        object AlreadyUp : Start() { override fun toString() = "AlreadyUp" }
    }

    /**
     * Le toucher : même mécanique que le chemin automatique, mais SANS le seuil de [BulkRoute.MIN_WD_BYTES], SANS la pause de 10 minutes
     * ([WdBackoff]) ni le réglage automatique ; JAMAIS sans lien, sans code valide, sans capacité de la TV. [lanConfirmed] : l'usager a répondu oui à [LAN_CONFIRM].
     */
    fun start(f: Facts, lanConfirmed: Boolean): Start = when (val v = of(f)) {
        View.Hidden -> Start.Refuse(Cause.NO_BOND)
        is View.Disabled -> Start.Refuse(v.cause)
        is View.Working, is View.Active -> Start.AlreadyUp
        is View.Ready -> when {
            v.confirmLan && !lanConfirmed -> Start.ConfirmLan
            v.askPermission -> Start.AskPermission
            else -> Start.Go
        }
    }
}

/**
 * La durée d'une session MANUELLE : elle reste montée jusqu'à l'arrêt par l'usager, ou jusqu'à 10 minutes hors de l'écran de la TV (la minuterie repart à
 * zéro à chaque retour). Une copie en cours la garde en vie. Horloge monotone passée en paramètre : testée en temps simulé.
 */
data class WdManualLease(val awaySince: Long? = null) {
    /** À appeler à chaque tic avec la visibilité de l'écran. */
    fun seen(now: Long, onScreen: Boolean): WdManualLease = when {
        onScreen -> if (awaySince == null) this else WdManualLease(null)
        awaySince == null -> WdManualLease(now)
        else -> this
    }

    fun expired(now: Long, busy: Boolean = false): Boolean = !busy && awaySince != null && now - awaySince >= AWAY_MS

    companion object {
        const val AWAY_MS = 10 * 60_000L
    }
}
