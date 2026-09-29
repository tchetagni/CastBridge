package castbridge.core.dl

import castbridge.core.tv.StoragePolicy
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.VolumeKind

/**
 * The space rule shared with uploads, applied to downloads: a download only starts on a volume that will still have at
 * least [MIN_FREE] free once it (and every download already heading there) is complete; while it runs, a download whose
 * volume would drop under that line is paused, and resumed when there is room again. Pure functions: fully unit-tested.
 */
object DownloadSpace {
    /** 1 GiB left free at the end, always. */
    const val MIN_FREE = 1L shl 30
    /** Resume only with this much margin above the line, so that a download does not pause/resume in a loop. */
    const val HYSTERESIS = 256L shl 20

    sealed class Choice {
        data class Ok(val volume: StorageVolume, val note: String?) : Choice()
        data class Refused(val message: String) : Choice()
    }

    /**
     * Where a new download of [size] bytes (null = unknown yet) goes. [target] is the storage setting ("auto", "internal"
     * or a volume id: the USB drive is preferred in "auto"); [reserved] = bytes still to come for downloads already on each
     * volume. A folder chosen through the system picker (SAF) has no path aria2 could write to: never used.
     */
    fun choose(volumes: List<StorageVolume>, target: String, size: Long?, reserved: Map<String, Long>, minFree: Long = MIN_FREE): Choice {
        val usable = volumes.filter { it.kind != VolumeKind.SAF }
        if (usable.isEmpty()) return Choice.Refused("Aucun stockage utilisable pour les téléchargements.")
        val adjusted = usable.map { it.copy(free = if (it.free < 0) it.free else it.free - (reserved[it.id] ?: 0)) }
        // A download follows the upload target, but never fails just because the preferred volume is full: fall back to "auto".
        val plan = StoragePolicy.plan(adjusted, target, size ?: 0, minFree)
        plan.candidates.firstOrNull()?.let { return Choice.Ok(usable.first { u -> u.id == it.volume.id }, null) }
        if (target != StoragePolicy.AUTO) {
            val auto = StoragePolicy.plan(adjusted, StoragePolicy.AUTO, size ?: 0, minFree)
            auto.candidates.firstOrNull()?.let { c ->
                return Choice.Ok(usable.first { it.id == c.volume.id }, "Le stockage choisi n'a pas assez de place : téléchargement vers ${c.volume.label}.")
            }
        }
        val tooBig = plan.skipped.firstOrNull { it.reason.startsWith("file too large") }
        return Choice.Refused(when {
            tooBig != null -> "Fichier trop gros pour la clé en FAT32 (4 Go maximum par fichier). Reformatez-la en exFAT depuis un ordinateur."
            plan.refusal?.message == "volume unavailable" && usable.none { it.kind == VolumeKind.REMOVABLE } ->
                "La clé USB choisie est absente : rebranchez-la."
            size != null -> "Pas assez de place : ce téléchargement fait ${human(size)} et la TV doit garder ${human(minFree)} libre. " +
                "Branchez une clé USB ou libérez de la place."
            else -> "Pas assez de place : la TV doit garder ${human(minFree)} libre. Branchez une clé USB ou libérez de la place."
        })
    }

    /** A running download as seen by the in-flight check. */
    data class Running(val id: String, val volumeId: String, val remaining: Long?, val pausedForSpace: Boolean)

    data class Decision(val pause: Set<String>, val resume: Set<String>)

    /**
     * In-flight check. [running] is in priority order (first = most important). Per volume, downloads are allowed in that
     * order while `free - (bytes still to come) >= minFree`; the others are paused. A download of unknown size counts as 0
     * but is paused as soon as the volume itself is under the line. A download paused for space resumes only with
     * [HYSTERESIS] of margin.
     */
    fun check(running: List<Running>, freeOf: (String) -> Long, minFree: Long = MIN_FREE): Decision {
        val pause = HashSet<String>(); val resume = HashSet<String>()
        for ((vol, list) in running.groupBy { it.volumeId }) {
            val free = freeOf(vol)
            if (free < 0) continue                              // unknown: nothing to decide on
            var committed = 0L
            for (r in list) {
                val need = r.remaining ?: 0
                val margin = if (r.pausedForSpace) HYSTERESIS else 0
                val fits = free - committed - need - margin >= minFree
                if (fits) {
                    committed += need
                    if (r.pausedForSpace) resume += r.id
                } else if (!r.pausedForSpace) pause += r.id
            }
        }
        return Decision(pause, resume)
    }

    fun human(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.FRANCE, "%.1f Go", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "${b shr 20} Mo"
        else -> "${b shr 10} ko"
    }
}
