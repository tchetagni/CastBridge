package castbridge.core.phone

import castbridge.core.trust.CopyAndPlay
import castbridge.core.tv.Mp4Atoms

/**
 * How the bytes of « Copier sur la TV et lire » / « Déplacer vers la TV » travel (R-08, docs/agent-reports/copy-and-play-handoff.md).
 *
 * The TV can only start a video before the end of the copy if it SEES the bytes arriving: with « Transfert rapide » the blocks land, in any order, in the
 * hidden `.cbx/<id>.data` until `finish`; `/api/info` lists nothing, `/stream/` and `/api/play` answer 404, so the hand-off of [CastSession] could only
 * happen once the copy was complete. The classic ordered path (one connection from byte 0, PUT /upload) writes the visible `.part` + `.meta`: `/api/info`
 * reports `received`, `/api/play` starts the growing file and `/stream/` serves its prefix. Pure table, tested in `CopyRouteTest`.
 */
enum class CopyTransport {
    /** One connection, from byte 0, into the TV's visible `.part`: the TV can start playing during the copy. */
    ORDERED,
    /** « Transfert rapide »: several connections, blocks in any order, invisible to the TV's player until complete and verified. */
    FAST,
    /** This action copies nothing (LIVE, Bluetooth only, trial TV: [CopyAndPlay] degrades or disables it). */
    NONE,
}

object CopyRoute {
    /**
     * [layout]: the MP4 layout of the file (null = not probed yet / not an ISO file). [ipRoute] false = Bluetooth only. [fastEnabled]: the « Transfert rapide »
     * setting of the phone (on by default).
     */
    data class Facts(
        val action: CastAction,
        val src: CastSource,
        val layout: Mp4Atoms.Layout? = null,
        val ipRoute: Boolean = true,
        val edition: CopyAndPlay.Edition = CopyAndPlay.Edition.UNKNOWN,
        val fastEnabled: Boolean = true,
    )

    /** [handoffDuringCopy]: the TV may take over before the last byte (false: it starts at the end of the copy, or nothing plays). */
    data class Decision(val transport: CopyTransport, val handoffDuringCopy: Boolean, val why: String)

    fun decide(f: Facts): Decision {
        val fast = if (f.fastEnabled) CopyTransport.FAST else CopyTransport.ORDERED
        return when {
            f.action == CastAction.LIVE -> Decision(CopyTransport.NONE, false, "lecture en direct : rien n'est copié")
            !f.ipRoute -> Decision(CopyTransport.NONE, false, CopyAndPlay.BLUETOOTH_ONLY)
            f.edition == CopyAndPlay.Edition.TRIAL -> Decision(CopyTransport.NONE, false, CopyAndPlay.TRIAL_NOTE)
            // a photo is only stored: nothing plays, the fastest path wins
            !CastPlan.playsOnTv(f.action, f.src) -> Decision(fast, false, "rien n'est lu sur la TV : copie la plus rapide")
            // the TV needs the whole file anyway (index at the end): the fastest copy is also the soonest playback
            f.layout == Mp4Atoms.Layout.MOOV_AT_END -> Decision(fast, false, "index MP4 à la fin : la TV attend le fichier complet")
            // the video plays on the TV: ordered from byte 0 so that it can start as soon as it holds enough
            else -> Decision(CopyTransport.ORDERED, true, "lecture sur la TV pendant la copie : envoi dans l'ordre")
        }
    }
}

/** What the phone says while a copy runs before the TV takes over (the « Copier sur la TV et lire » screen, the remote, the mini bar). */
object CopyHandoff {
    const val COPYING = "Copie en cours · la TV démarrera la lecture dès qu'elle aura assez d'avance"
    const val PHONE_PLAYS = "La lecture continue ici en attendant."
    /** Under the « Copier sur la TV et lire » button of « Ouvrir avec CastBridge ». */
    const val BUTTON_HINT = "La TV démarre la lecture dès qu'elle a assez d'avance ; ce téléphone devient sa télécommande."

    /** The headline: the plain promise, or, for an MP4 whose index is at the end, why the TV must wait and about how long ([fullInMs] null = unknown yet). */
    fun line(moovAtEnd: Boolean, fullInMs: Long?): String =
        if (!moovAtEnd) COPYING
        else "Copie en cours · ce MP4 a son index à la fin du fichier : la TV ne peut démarrer qu'une fois la copie complète" +
            when (fullInMs) { null -> " (durée en cours d'estimation)"; 0L -> ""; else -> ", dans environ ${CopyProgress.wait(fullInMs)}" }

    /** The line under it: only when the phone really plays the file meanwhile (from « Ouvrir avec », nothing plays here). */
    fun phoneLine(phonePlays: Boolean): String? = if (phonePlays) PHONE_PLAYS else null
}
