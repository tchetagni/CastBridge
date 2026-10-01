package castbridge.sender.agent

import android.content.Context
import castbridge.core.library.agent.AutoRename
import castbridge.core.library.agent.Entry
import castbridge.core.library.agent.Loc
import castbridge.core.library.agent.Op
import castbridge.core.library.agent.State
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvInfo
import castbridge.sender.UploadService
import java.util.concurrent.ConcurrentHashMap

/**
 * « Rangement automatique des nouveaux envois » (OFF by default, see the assistant's settings): a file sent to the TV by Wi-Fi gets
 * a clean name only when the rules are sure (series with season and episode, films with a year, WhatsApp / camera videos). The renaming is
 * written in the assistant's journal once the file is on the TV, so « Annuler un rangement » renames it back to its original name.
 *
 * Three steps, so that it can never do harm:
 *  1. [nameFor] (when the send starts): a CANDIDATE name, nothing written anywhere;
 *  2. [settle] (in the upload service, before the first byte): the candidate is kept only if the TV answers AND has no file with that name
 *     (the upload resumes into an existing file of the same name: a clean name that collides must never be used); otherwise the original name is sent;
 *  3. [completed] (the file arrived): the rename is added to the journal.
 */
object AgentAuto {
    /** candidate name (lower case) -> original name, for the sends in progress. */
    private val pending = ConcurrentHashMap<String, String>()
    /** Same pairs, kept until the process ends (bounded) so that a screen can still match "the job that finished" with the file the user picked. */
    private val renamed = ConcurrentHashMap<String, String>()

    /** The name to try for [original]; [original] itself when the option is off, a child profile is active, or the rules are not sure. */
    fun nameFor(ctx: Context, original: String): String {
        return try {
            AgentStore.init(ctx)
            if (!AgentStore.settings.autoRename || AgentStore.guard.childProfileActive) return original
            val n = AutoRename.nameFor(original, learned = AgentStore.learned) ?: return original
            pending[n.lowercase()] = original
            if (renamed.size > 200) renamed.clear()
            renamed[n.lowercase()] = original
            n
        } catch (e: Exception) { original }
    }

    /** The name the user's file had before the automatic rename (the name itself when it was not renamed). */
    fun originalOf(name: String): String = renamed[name.lowercase()] ?: name

    /** Called with the job's resolved TV address: keep the candidate name only if the TV is reachable and does not already hold that name. */
    fun settle(job: UploadService.Job, resolve: () -> String?, waitMs: Long = 10_000): UploadService.Job {
        val original = pending[job.fileName.lowercase()] ?: return job
        fun fallback(): UploadService.Job { pending.remove(job.fileName.lowercase()); renamed.remove(job.fileName.lowercase()); return job.copy(fileName = original) }
        var base: String? = resolve()
        val end = System.currentTimeMillis() + waitMs
        while (base == null && System.currentTimeMillis() < end) { Thread.sleep(500); base = resolve() }
        if (base == null) return fallback()
        val taken = runCatching { TvInfo.parse(TvClient(base, job.pin).info()).files.any { it.name.equals(job.fileName, ignoreCase = true) } }.getOrDefault(true)
        return if (taken) fallback() else job
    }

    /** The file arrived under [name]: if that is a renamed candidate, the rename is journalled (so that it can be undone). */
    fun completed(ctx: Context, name: String) {
        val original = pending.remove(name.lowercase()) ?: return
        runCatching {
            AgentStore.init(ctx)
            val j = AgentStore.journal
            // volume "" = wherever the TV stores it; DONE from the start: the rename IS the name the file was sent under
            j.append(Entry(j.nextSeq(), "tv-auto-" + java.lang.Long.toString(System.currentTimeMillis() / 60_000, 36), System.currentTimeMillis(), Op.RENAME, "auto:$original",
                Loc("", "", original), Loc("", "", name), State.DONE, "renommé à l'envoi"))
        }
    }
}
