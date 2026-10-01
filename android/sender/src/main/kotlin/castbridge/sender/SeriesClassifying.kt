package castbridge.sender

import android.content.Context
import castbridge.core.library.agent.SeriesClassifier
import castbridge.core.tv.TvClient

/**
 * Classifies the series of the TV library in « Titre / Saison NN » (virtual folders: no byte moves, see [SeriesClassifier]).
 * Automatic at the end of each send (setting, on by default) and on demand for what is already there; the last classification can be undone.
 */
object SeriesClassifying {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("series_classifier", Context.MODE_PRIVATE)
    fun auto(ctx: Context) = prefs(ctx).getBoolean("auto", true)
    fun setAuto(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("auto", on).apply() }

    /** The last classification, to undo it (in memory: undo is offered right after, not weeks later). */
    @Volatile var last: List<SeriesClassifier.Move> = emptyList(); private set

    /** After a send: the folder of one new file, silently (a failure never disturbs the send). */
    fun afterSend(ctx: Context, client: TvClient, name: String) {
        if (!auto(ctx)) return
        val folder = SeriesClassifier.folderFor(name) ?: return
        runCatching { client.setFolder(name, folder) }
    }

    /** What would be classified among [files] (name, folder) : [SeriesClassifier.plan]. */
    fun preview(files: List<Pair<String, String>>) = SeriesClassifier.plan(files)

    /** Applies a plan; returns (done, failed). Stops at the first connection error (no point hammering). */
    fun apply(client: TvClient, moves: List<SeriesClassifier.Move>): Pair<Int, Int> {
        var ok = 0; var ko = 0
        val done = ArrayList<SeriesClassifier.Move>()
        for (m in moves) {
            if (runCatching { client.setFolder(m.name, m.folder) }.isSuccess) { ok++; done += m } else { ko++; if (ko >= 3) break }
        }
        last = done
        return ok to ko
    }

    fun undoLast(client: TvClient): Int {
        val back = SeriesClassifier.undo(last); var n = 0
        for (m in back) if (runCatching { client.setFolder(m.name, "") }.isSuccess) n++
        last = emptyList(); return n
    }
}
