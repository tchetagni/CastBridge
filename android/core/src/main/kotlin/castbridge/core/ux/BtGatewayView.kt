package castbridge.core.ux

/**
 * « Passerelle Bluetooth » rendue accessible (demande du propriétaire du 2026-10-03) : quand montrer le bouton, quelle TV présélectionner,
 * les textes, la barre visible depuis tous les onglets, les lignes à taper sur le Mac ; puis la bascule automatique sur le Bluetooth quand la
 * TV n'est plus sur le Wi-Fi ([BtFallback]). Tout est pur : les écrans du téléphone (TvHome, MainActivity, BtGatewayCard) ne font que
 * dessiner ce que ces fonctions renvoient. La passerelle elle-même (castbridge.sender.BtSshGatewayService) est inchangée : boucle locale
 * par défaut, exposition du SSH sur le réseau seulement sur demande et avec l'avertissement, la TV vérifie toujours le code ou le jeton.
 */

/** A Bluetooth device bonded with the phone, as the screen reads it (name, address, and two hints that it is a TV). */
data class BondedDevice(val name: String, val address: String, val tvClass: Boolean = false, val castBridgeService: Boolean = false)

sealed class GatewayOffer {
    /** Nothing to offer: the TV answers on the Wi-Fi and no TV is bonded in Bluetooth. */
    object Hidden : GatewayOffer() { override fun toString() = "Hidden" }
    /** The gateway runs: its card (state, lines for the Mac, « Arrêter ») replaces the button. */
    object Running : GatewayOffer() { override fun toString() = "Running" }
    /** Full-width button; [preselected] non-null means one tap starts it (API + SSH, loopback only). */
    data class Start(val title: String, val hint: String, val button: String, val preselected: BondedDevice?, val candidates: List<BondedDevice>) : GatewayOffer() {
        val oneTap: Boolean get() = preselected != null
    }
    /** No TV bonded with this phone: the way is « Ajouter ma TV (Bluetooth) » first. */
    data class AddTv(val title: String, val hint: String, val button: String) : GatewayOffer()
}

data class GatewayStart(val api: Boolean, val ssh: Boolean, val exposeLan: Boolean)
data class MacCommand(val label: String, val line: String)
data class GatewayGlance(val title: String, val detail: String, val action: String, val warning: Boolean)

object BtGatewayView {
    const val SSH_PORT = 2222
    const val API_PORT = 18765
    const val TITLE = "Passerelle Bluetooth"
    const val HINT = "Pas de Wi-Fi ? Relier la TV par Bluetooth"
    const val RUNNING_HELP = "Sur le Mac relié en USB, tapez ces lignes dans le Terminal :"
    const val ADD_TV_HELP = "Appairez d'abord la TV avec ce téléphone : aucun code à saisir."
    const val CHOOSE_HELP = "Plusieurs appareils pourraient être votre TV : choisissez-la dans la liste."
    /** Stands for the TV's code in every line shown or copied; never replaced by the real code. */
    const val PIN_PLACEHOLDER = "<code>"

    private val TV_WORD = Regex("""(^|[^\p{L}\p{N}])tv($|[^\p{L}])""", RegexOption.IGNORE_CASE)
    private val TV_BRANDS = Regex("""android\s?tv|smart\s?tv|google\s?tv|fire\s?tv|mitv|castbridge|bravia|hisense|roku""", RegexOption.IGNORE_CASE)
    private val NOT_TV = Regex("""headset|casque|buds|airpods|écouteur|ecouteur|carkit|speaker|enceinte|watch|montre""", RegexOption.IGNORE_CASE)
    private val PIN_VALUE = Regex("""(X-CB-Pin:\s*)([^'"\s]+)""", RegexOption.IGNORE_CASE)

    private fun known(d: BondedDevice, knownTvs: Set<String>) = knownTvs.any { it.equals(d.address, ignoreCase = true) }
    private fun strong(d: BondedDevice, knownTvs: Set<String>) = known(d, knownTvs) || d.castBridgeService

    /** Is this bonded device probably a TV? A TV saved in CastBridge, the CastBridge service, a TV device class, or a TV-like name. */
    fun looksLikeTv(d: BondedDevice, knownTvs: Set<String>): Boolean {
        if (strong(d, knownTvs)) return true
        if (NOT_TV.containsMatchIn(d.name)) return false
        return d.tvClass || TV_WORD.containsMatchIn(d.name) || TV_BRANDS.containsMatchIn(d.name)
    }

    /** The bonded TVs, surest first (saved in CastBridge or carrying its service), in the phone's order otherwise. */
    fun candidates(bonded: List<BondedDevice>, knownTvs: Set<String>): List<BondedDevice> =
        bonded.filter { looksLikeTv(it, knownTvs) }.sortedBy { if (strong(it, knownTvs)) 0 else 1 }

    /** The one TV to use without asking, or null when there is none or several equally likely. */
    fun preferred(bonded: List<BondedDevice>, knownTvs: Set<String>): BondedDevice? {
        val c = candidates(bonded, knownTvs)
        val sure = c.filter { strong(it, knownTvs) }
        return when {
            sure.size == 1 -> sure.first()
            sure.isEmpty() && c.size == 1 -> c.first()
            else -> null
        }
    }

    /**
     * What the home and the « Trouvons votre TV » / « TV injoignable » screens offer. Shown when the TV is unreachable or a TV is bonded;
     * without the Bluetooth permission ([canListBonded] false) the button leads to the choice screen, which asks for it.
     */
    fun offer(tvReachable: Boolean, bonded: List<BondedDevice>, knownTvs: Set<String>, running: Boolean, canListBonded: Boolean = true): GatewayOffer {
        if (running) return GatewayOffer.Running
        if (!canListBonded) return if (tvReachable) GatewayOffer.Hidden else GatewayOffer.Start(TITLE, HINT, "Choisir la TV et démarrer", null, emptyList())
        val c = candidates(bonded, knownTvs)
        if (c.isEmpty()) return if (tvReachable) GatewayOffer.Hidden else GatewayOffer.AddTv(TITLE, HINT, "Ajouter ma TV (Bluetooth)")
        val pre = preferred(bonded, knownTvs)
        return GatewayOffer.Start(TITLE, HINT, pre?.let { "Démarrer la passerelle vers ${it.name}" } ?: "Choisir la TV et démarrer", pre, c)
    }

    /** The quick button: API and SSH, listening on the phone's loopback only (never on its networks). */
    fun oneTapStart() = GatewayStart(api = true, ssh = true, exposeLan = false)

    /** The exact lines to type on the Mac (USB ADB) while the gateway runs; the code is the placeholder [PIN_PLACEHOLDER]. */
    fun macCommands(ssh: Boolean, api: Boolean, lanSsh: Boolean = false): List<MacCommand> {
        val out = mutableListOf<MacCommand>()
        if (ssh) {
            out += MacCommand("SSH, étape 1 : relier le port", "adb forward tcp:$SSH_PORT tcp:$SSH_PORT")
            out += MacCommand("SSH, étape 2 : se connecter avec votre clé", "ssh -p $SSH_PORT tv@127.0.0.1")
            if (lanSsh) out += MacCommand("SSH depuis un appareil du réseau du téléphone", "ssh -p $SSH_PORT tv@<adresse du téléphone>")
        }
        if (api) {
            out += MacCommand("API, étape 1 : relier le port", "adb forward tcp:$API_PORT tcp:$API_PORT")
            out += MacCommand("API, étape 2 : remplacez $PIN_PLACEHOLDER par le code de la TV", "curl -H 'X-CB-Pin: $PIN_PLACEHOLDER' http://127.0.0.1:$API_PORT/api/hello")
        }
        return out
    }

    /** What « Copier » puts on the clipboard (and what a state line may show): any value after X-CB-Pin is masked again as [PIN_PLACEHOLDER]. */
    fun forClipboard(line: String): String = PIN_VALUE.replace(line) { m -> m.groupValues[1] + PIN_PLACEHOLDER }

    /** The line over the other tabs while the gateway runs, with « Arrêter ». */
    fun glance(running: Boolean, tv: String, ssh: Boolean, api: Boolean, lan: Boolean): GatewayGlance? {
        if (!running) return null
        val parts = listOfNotNull(if (ssh) "SSH $SSH_PORT" else null, if (api) "API $API_PORT" else null)
        val where = if (lan && ssh) " · SSH ouvert aussi sur le réseau du téléphone" else " (boucle locale)"
        return GatewayGlance("Passerelle Bluetooth active vers $tv", "Mac : " + parts.joinToString(" · ") + where, "Arrêter", lan && ssh)
    }

    /** Visible on every tab but the home, which carries the full card. */
    fun stripVisible(onHome: Boolean, glance: GatewayGlance?): Boolean = glance != null && !onHome
}

/** The state light of the phone's TV line: green Wi-Fi, orange Bluetooth only (slower), red unreachable, black nothing set up. */
enum class LinkLight { GREEN, ORANGE, RED, BLACK }

/** The one gesture a red or orange state offers. */
enum class Gesture(val label: String) {
    ADD_TV_BT("Ajouter ma TV (Bluetooth)"), START_GATEWAY("Passerelle Bluetooth"), RETRY("Réessayer"), STOP_OTHER("Arrêter la passerelle")
}

data class LinkSignal(val light: LinkLight, val title: String, val cause: String? = null, val gesture: Gesture? = null, val slowNote: String? = null)

sealed class FallbackAction {
    object None : FallbackAction() { override fun toString() = "None" }
    /** Stop the gateway the switch started (the TV is back on the Wi-Fi). */
    object Stop : FallbackAction() { override fun toString() = "Stop" }
    /** Start the gateway to [tv], API only, loopback only. */
    data class StartApi(val tv: BondedDevice) : FallbackAction()
}

/**
 * What the switch rule sees. [wifiTv]: the TV is found on the Wi-Fi; [absentMs]: since when it is not; [target]: the one bonded TV
 * ([BtGatewayView.preferred]); [bondedTvs]: how many bonded devices look like a TV; [gatewayTv]: the address the running gateway reaches;
 * [autoStarted]: started by this rule (only then does it stop it); [answering]: the TV answered through the current route; [failure]: the
 * gateway's last message; [sinceAutoStartMs]: time since this rule last started it (null: never).
 */
data class FallbackInput(val enabled: Boolean, val wifiTv: Boolean, val absentMs: Long, val target: BondedDevice?, val bondedTvs: Int,
                         val apiRunning: Boolean, val gatewayTv: String?, val autoStarted: Boolean, val sshRunning: Boolean,
                         val answering: Boolean, val failure: String?, val sinceAutoStartMs: Long?)

/** [viaBluetooth]: use [BtFallback.LOOPBACK_BASE] as the TV's address; [signal]: null leaves the existing Wi-Fi state line alone. */
data class FallbackDecision(val action: FallbackAction, val viaBluetooth: Boolean, val signal: LinkSignal?)

/** « Basculer sur Bluetooth quand le Wi-Fi est absent » (on by default, can be turned off). */
object BtFallback {
    const val LOOPBACK_BASE = "http://127.0.0.1:${BtGatewayView.API_PORT}"
    /** The first seconds without the TV are a search (discovery takes a moment), not a failure. */
    const val GRACE_MS = 8_000L
    /** A failed automatic start is tried again after this pause, never in a loop. */
    const val RETRY_MS = 30_000L
    const val SLOW_NOTE = "Par Bluetooth, l'envoi de fichiers est lent (~100-300 ko/s)."
    private const val NEITHER = "TV injoignable : ni Wi-Fi ni Bluetooth"
    private const val NO_WIFI = "TV injoignable sur le Wi-Fi"
    private const val CONNECTING = "Wi-Fi absent · connexion par Bluetooth…"
    private const val ACTIVE = "Wi-Fi absent · liaison Bluetooth active"

    private fun cause(failure: String?, fallback: String) = failure?.takeIf { it.isNotBlank() }?.let { BtGatewayView.forClipboard(it) } ?: fallback

    fun decide(i: FallbackInput): FallbackDecision {
        val none = FallbackDecision(FallbackAction.None, false, null)
        if (!i.enabled) return if (i.wifiTv || i.absentMs < GRACE_MS) none
            else FallbackDecision(FallbackAction.None, false, LinkSignal(LinkLight.RED, NO_WIFI, "La bascule automatique sur Bluetooth est désactivée.", Gesture.START_GATEWAY))
        if (i.wifiTv) return FallbackDecision(if (i.apiRunning && i.autoStarted && !i.sshRunning) FallbackAction.Stop else FallbackAction.None, false, null)
        if (i.absentMs < GRACE_MS) return none
        val t = i.target ?: return FallbackDecision(FallbackAction.None, false,
            if (i.bondedTvs > 1) LinkSignal(LinkLight.RED, NO_WIFI, BtGatewayView.CHOOSE_HELP, Gesture.START_GATEWAY)
            else LinkSignal(LinkLight.RED, NEITHER, "La TV n'est pas sur ce Wi-Fi et n'est pas appairée en Bluetooth avec ce téléphone.", Gesture.ADD_TV_BT))
        if (i.apiRunning && !t.address.equals(i.gatewayTv, ignoreCase = true)) return FallbackDecision(FallbackAction.None, false,
            LinkSignal(LinkLight.RED, NO_WIFI, "La passerelle Bluetooth est reliée à une autre TV.", Gesture.STOP_OTHER))
        if (!i.apiRunning) {
            val since = i.sinceAutoStartMs
            return if (since == null || since >= RETRY_MS) FallbackDecision(FallbackAction.StartApi(t), false, LinkSignal(LinkLight.ORANGE, CONNECTING))
            else FallbackDecision(FallbackAction.None, false, LinkSignal(LinkLight.RED, NEITHER,
                cause(i.failure, "La TV ne répond pas en Bluetooth : allumée, à portée, CastBridge-TV ouverte ?"), Gesture.RETRY))
        }
        return when {
            i.answering -> FallbackDecision(FallbackAction.None, true, LinkSignal(LinkLight.ORANGE, ACTIVE, slowNote = SLOW_NOTE))
            i.failure != null -> FallbackDecision(FallbackAction.None, true, LinkSignal(LinkLight.RED, NEITHER, cause(i.failure, ""), Gesture.RETRY))
            else -> FallbackDecision(FallbackAction.None, true, LinkSignal(LinkLight.ORANGE, CONNECTING))
        }
    }
}
