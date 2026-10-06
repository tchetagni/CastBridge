package castbridge.core.trust

import castbridge.core.tv.BtProtocol

/**
 * What the phone may claim about « its TV » before (and between) real observations, and what « Ouvrir avec CastBridge » offers.
 * Pure: the screens only feed facts in and draw what comes out (table-tested in `SendChoiceTest`).
 *
 * Rule (field bug of 2026-10-02, docs/agent-reports/fix-notv-cold-start.md): « Aucune TV ajoutée » is said ONLY when the phone knows
 * no TV at all, neither in the trusted registry ([SavedTvs]) nor as the TV of the code (PIN) path. A state nobody observed yet is
 * « vérification… », never a default value.
 */
object LinkStart {
    /** Shown from the moment a TV is known until the link loop publishes its first real step. */
    fun checking(tvName: String) = LinkView(LinkState.Connecting, "Vérification de la liaison avec $tvName…",
        "Recherche de la TV enregistrée (Bluetooth puis Wi-Fi).", LinkAction.NONE, Tone.NEUTRAL, busy = true)

    /** Several TVs are saved and none is the default (a lost or stale choice): never « Aucune TV ». */
    fun noDefault(count: Int) = LinkView(LinkState.Connecting, "Choisissez votre TV", "$count TV sont enregistrées et aucune n'est choisie : touchez « Gérer » pour en choisir une.",
        LinkAction.NONE, Tone.WARN, busy = false)

    /**
     * The view the screens get from the link loop. [savedCount]/[defaultName]: the trusted registry; [stepView]: the view of the last
     * published step, or null before the first one. Null result = really no TV saved (the screen shows its « Ajouter ma TV »).
     */
    fun view(savedCount: Int, defaultName: String?, stepView: LinkView?): LinkView? = when {
        savedCount == 0 -> null
        defaultName == null -> noDefault(savedCount)
        stepView == null || stepView.state is LinkState.NoTv -> checking(defaultName)
        else -> stepView
    }
}

/** The one check of the code (PIN) path made by « Ouvrir avec » (a single authenticated request; never repeated after a refusal: the TV locks after 5 wrong codes). */
enum class PinCheck { UNKNOWN, OK, REJECTED, LOCKED, UNREACHABLE }

/** How a file leaves: the trusted link's queue, the code (PIN) upload of the home screen, or not at all. */
enum class SendRoute { QUEUE, PIN_UPLOAD, NONE }

/** The single button the dialog proposes besides copy/move (it opens CastBridge on the right screen). */
enum class SendAction(val label: String) { NONE(""), ADD_TV("Ajouter ma TV"), ENTER_PIN("Saisir le code PIN de la TV"), OPEN_APP("Ouvrir CastBridge") }

/**
 * Facts for « Ouvrir avec CastBridge ».
 * @param savedCount trusted TVs saved (Bluetooth registry); [defaultName] the default one's name (null if none chosen).
 * @param stepView view of the last step published by the link loop, null before the first one.
 * @param session a usable trusted session exists; [sessionName] its TV; [btOnly] it has no IP route (Bluetooth only).
 * @param pinTvName the TV of the code path (home screen, « Trouvons votre TV »), null if none; [pinStored] a well-formed code is kept for it.
 */
data class SendFacts(
    val savedCount: Int = 0, val defaultName: String? = null, val stepView: LinkView? = null,
    val session: Boolean = false, val sessionName: String? = null, val btOnly: Boolean = false,
    val pinTvName: String? = null, val pinStored: Boolean = false, val pinCheck: PinCheck = PinCheck.UNKNOWN,
    val refusal: RefusalRecord? = null, val nowMs: Long = 0,
    /** The name the code path finds the saved default TV by (mDNS name, else its name): where a code typed in the dialog goes when no code-path TV is known. */
    val defaultPinKey: String? = null,
)

/** What the dialog draws. [note]: the one explanation line (why « Déplacer » waits, what is wrong), or null. */
data class SendChoice(val route: SendRoute, val status: String, val copyEnabled: Boolean, val moveEnabled: Boolean, val note: String?, val action: SendAction, val banner: String? = null,
                      /** The TV a code typed in the dialog belongs to when [action] is ENTER_PIN (its mDNS name, what the code path finds it by). */
                      val pinKey: String? = null)

object SendChoices {
    const val NO_TV = "Aucune TV ajoutée : ouvrez CastBridge pour ajouter votre TV."
    const val MOVE_WAITS = "« Déplacer » attend que la TV soit jointe, car il supprime l'original du téléphone. « Copier » part dès que la liaison est établie : suivez-le dans la notification."
    const val MOVE_NO_BT = "Le déplacement n'est pas disponible par Bluetooth."

    /** States of the trusted link where the loop keeps trying by itself: a queued copy will leave when it succeeds. */
    private fun transient(s: LinkState) = s is LinkState.Connecting || s is LinkState.Reconnecting || s is LinkState.TvUnreachable ||
        s is LinkState.CredentialExpired || s is LinkState.Bonding || s.isGood

    /** A refusal « téléphone non autorisé » (code 8) younger than this makes the phone stop believing it is trusted by that TV. */
    const val UNTRUSTED_WINDOW_MS = 10 * 60_000L

    /** The TV said « je ne vous reconnais plus » in the last 10 minutes: every trusted route (session, queue) is off until a PIN or a link succeeds. */
    fun untrusted(f: SendFacts): Boolean = f.refusal != null && f.refusal.code == BtProtocol.ERR_UNTRUSTED && f.nowMs - f.refusal.atMs in 0..UNTRUSTED_WINDOW_MS

    fun decide(f: SendFacts): SendChoice {
        val c = decideRoutes(f).let { if (it.action == SendAction.ENTER_PIN && it.pinKey == null) it.copy(pinKey = f.pinTvName ?: f.defaultPinKey ?: f.defaultName) else it }
        // the explanation is shown whenever nothing can leave because of that refusal
        return if (untrusted(f) && c.route == SendRoute.NONE) c.copy(banner = LinkRefusalTexts.banner(f.refusal!!.code)) else c
    }

    private fun decideRoutes(f: SendFacts): SendChoice {
        val forgotten = untrusted(f)
        // 1) a real trusted session: the queue, at once
        if (f.session && !forgotten) {
            val name = f.sessionName ?: f.defaultName ?: "TV"
            return if (f.btOnly) SendChoice(SendRoute.QUEUE, "TV : $name (par Bluetooth : plus lent)", true, false, MOVE_NO_BT, SendAction.NONE)
            else SendChoice(SendRoute.QUEUE, "TV : $name", true, true, null, SendAction.NONE)
        }
        // 2) the code path verified right now (same request as the home screen's green dot)
        if (f.pinTvName != null && f.pinStored && f.pinCheck == PinCheck.OK)
            return SendChoice(SendRoute.PIN_UPLOAD, "TV : ${display(f.pinTvName)}", true, true, null, SendAction.NONE)
        // 3) a trusted TV saved but not joined yet
        val view = if (forgotten) null else LinkStart.view(f.savedCount, f.defaultName, f.stepView)
        if (view != null) {
            if (f.defaultName == null) return SendChoice(SendRoute.NONE, view.title + " : " + view.detail, false, false, null, SendAction.OPEN_APP)
            if (transient(view.state)) return SendChoice(SendRoute.QUEUE, view.title, true, false, MOVE_WAITS, SendAction.NONE)
            // the loop does not retry by itself (the TV forgot the phone, Bluetooth off, pairing to redo…): say it, one action, no dead queue
            if (f.pinTvName == null || !f.pinStored)
                return SendChoice(SendRoute.NONE, view.title + " : " + view.detail, false, false, null, SendAction.OPEN_APP)
        }
        // 4) the code (PIN) path
        // R-20: the saved TV forgot this phone and no code-path TV is known: the code is asked right in the dialog, for that TV (never a dead end)
        val pinTv = f.pinTvName ?: if (forgotten && f.defaultName != null)
            return SendChoice(SendRoute.NONE, "TV : ${f.defaultName}", false, false, null, SendAction.ENTER_PIN, pinKey = f.defaultPinKey ?: f.defaultName)
        else return SendChoice(SendRoute.NONE, if (forgotten) "TV : ${f.defaultName ?: "TV"}" else NO_TV, false, false, null, SendAction.ADD_TV)
        val name = display(pinTv)
        if (!f.pinStored) return SendChoice(SendRoute.NONE, "$name demande son code PIN, que ce téléphone n'a pas : saisissez le code affiché sur la TV.", false, false, null, SendAction.ENTER_PIN)
        return when (f.pinCheck) {
            PinCheck.OK -> SendChoice(SendRoute.PIN_UPLOAD, "TV : $name", true, true, null, SendAction.NONE)        // (handled above; kept for completeness)
            PinCheck.UNKNOWN -> SendChoice(SendRoute.PIN_UPLOAD, "Vérification de la liaison avec $name…", true, false, MOVE_WAITS, SendAction.NONE)
            PinCheck.UNREACHABLE -> SendChoice(SendRoute.PIN_UPLOAD, "$name ne répond pas pour le moment (même Wi-Fi ? CastBridge-TV ouvert ?).", true, false, MOVE_WAITS, SendAction.NONE)
            PinCheck.REJECTED -> SendChoice(SendRoute.NONE, "Le code PIN de $name a changé ou n'est pas le bon : saisissez le code affiché sur la TV.", false, false, null, SendAction.ENTER_PIN)
            PinCheck.LOCKED -> SendChoice(SendRoute.NONE, "$name est verrouillée une minute après trop d'essais de code : réessayez ensuite avec le bon code.", false, false, null, SendAction.ENTER_PIN)
        }
    }

    /** R-20: the choice right after the code was accepted in the dialog (« Valider et envoyer »): the copy leaves at once through the code path. */
    fun afterCode(tvKey: String): SendChoice = SendChoice(SendRoute.PIN_UPLOAD, "TV : ${display(tvKey)}", true, true, null, SendAction.NONE, pinKey = tvKey)

    /** "CastBridge TV SMART_TV"(mDNS name of the code path) → "SMART_TV", as the home screen shows it. */
    fun display(name: String) = name.removePrefix("CastBridge TV ").ifBlank { "Ma TV" }
}
