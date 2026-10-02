package castbridge.core.trust

import castbridge.core.phone.CastAction
import castbridge.core.phone.MediaKind

/**
 * « Copier sur la TV et lire » of « Ouvrir avec CastBridge »: it is the EXISTING cast action [CastAction.COPY] (the file is copied to the TV,
 * which starts playing as soon as it holds enough; same path as the library menu and the cast sheet). This object only decides the state of the
 * button. Pure: the dialog feeds facts in and draws what comes out (table-tested in `CopyAndPlayTest`).
 *
 * A trial TV closes uploads but allows cast streaming (`TrialPolicy`), so the button degrades to [CastAction.LIVE] (play only, no copy) with
 * an explanation. The phone does not learn the TV edition yet (no `SendGuard`/proof in this build): [Edition.UNKNOWN] behaves like COPY and the
 * TV stays the judge (the trial TV refuses the upload itself, the failure is reported by [CastSession]).
 */
object CopyAndPlay {
    val LABEL = CastAction.COPY.label
    const val TRIAL_NOTE = "La copie n'est pas disponible en version d'essai : le fichier sera seulement lu sur la TV."
    const val BLUETOOTH_ONLY = "Lire sur la TV demande le Wi-Fi : la liaison Bluetooth seule ne suffit pas."
    const val NO_TV_READY = "Aucune TV prête : voyez l'état de la liaison ci-dessus."
    const val NOT_PLAYABLE = "CastBridge ne sait pas lire ce type de fichier sur la TV."

    /** How the phone reaches its TV right now. [CHECKING]: a TV is known, the link is not confirmed yet (like « Copier », allowed). */
    enum class Link { SESSION, PIN_PATH, CHECKING, NONE }
    enum class Edition { TRIAL, PRODUCTION, UNKNOWN }

    /** [ipRoute]: the transport has a Wi-Fi/IP base (false = Bluetooth only, where streaming from the phone is impossible). */
    data class Facts(val link: Link, val ipRoute: Boolean, val edition: Edition, val kind: MediaKind)

    /** What the dialog draws: [label] of the button, [action] the cast action to start (COPY, or LIVE when the copy is closed), [reason] the one explanation line. */
    sealed class Decision(val label: String, val action: CastAction, val reason: String?) {
        class Enabled : Decision(LABEL, CastAction.COPY, null)
        class Degraded(reasonText: String) : Decision(LABEL, CastAction.LIVE, reasonText)
        class Disabled(reasonText: String) : Decision(LABEL, CastAction.COPY, reasonText)
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
