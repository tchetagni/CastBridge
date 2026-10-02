package castbridge.core.trust

import castbridge.core.phone.MediaKind

/**
 * « Copier et lire sur la TV » of « Ouvrir avec CastBridge »: playback starts at once by streaming from the phone (the cast flow's LIVE
 * action) while the normal background copy goes to the TV library. Pure: the dialog feeds facts in and draws what comes out
 * (table-tested in `CopyAndPlayTest`); no state decision lives in the activity.
 *
 * The copy part follows the same rules as « Copier vers la TV » ([SendChoices]): a copy is promised ([Decision.copies]) only when the
 * TV will take it. A trial TV closes uploads but allows cast streaming (`TrialPolicy`), so the button degrades to « Lire sur la TV ».
 * The phone does not learn the TV edition yet (no `SendGuard`/proof in this build): [Edition.UNKNOWN] behaves like « Copier » and the
 * TV stays the judge (its refusal is reported by the copy itself).
 */
object CopyAndPlay {
    const val LABEL = "Copier et lire sur la TV"
    const val LABEL_PLAY_ONLY = "Lire sur la TV"
    const val TRIAL_NOTE = "La copie n'est pas disponible en version d'essai : le fichier sera seulement lu sur la TV."
    const val BLUETOOTH_ONLY = "Lire sur la TV demande le Wi-Fi : la liaison Bluetooth seule ne suffit pas."
    const val NO_TV_READY = "Aucune TV prête : voyez l'état de la liaison ci-dessus."
    const val NOT_PLAYABLE = "CastBridge ne sait pas lire ce type de fichier sur la TV."

    /** How the phone reaches its TV right now. [CHECKING]: a TV is known, the link is not confirmed yet (like « Copier », allowed). */
    enum class Link { SESSION, PIN_PATH, CHECKING, NONE }
    enum class Edition { TRIAL, PRODUCTION, UNKNOWN }

    /** [ipRoute]: the transport has a Wi-Fi/IP base (false = Bluetooth only, where streaming from the phone is impossible). */
    data class Facts(val link: Link, val ipRoute: Boolean, val edition: Edition, val kind: MediaKind)

    /** What the dialog draws: [label] of the button, [copies] whether the background copy is enqueued too, [reason] the one explanation line. */
    sealed class Decision(val label: String, val copies: Boolean, val reason: String?) {
        class Enabled : Decision(LABEL, true, null)
        class Degraded(reasonText: String) : Decision(LABEL_PLAY_ONLY, false, reasonText)
        class Disabled(reasonText: String) : Decision(LABEL, false, reasonText)
    }

    /** The link kind from the dialog's own facts and what [SendChoices.decide] answered (same states as « Copier »). */
    fun linkOf(f: SendFacts, c: SendChoice): Link = when {
        f.session -> Link.SESSION
        c.route == SendRoute.NONE || !c.copyEnabled -> Link.NONE
        c.route == SendRoute.PIN_UPLOAD && f.pinTvName != null && f.pinStored && f.pinCheck == PinCheck.OK -> Link.PIN_PATH
        else -> Link.CHECKING
    }

    fun decide(f: Facts): Decision = when {
        f.link == Link.NONE -> Decision.Disabled(NO_TV_READY)
        f.kind == MediaKind.OTHER -> Decision.Disabled(NOT_PLAYABLE)
        !f.ipRoute -> Decision.Disabled(BLUETOOTH_ONLY)
        f.edition == Edition.TRIAL -> Decision.Degraded(TRIAL_NOTE)
        else -> Decision.Enabled()
    }
}
