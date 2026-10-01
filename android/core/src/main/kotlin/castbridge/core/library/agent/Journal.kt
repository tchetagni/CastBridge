package castbridge.core.library.agent

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import java.io.File

enum class Op { RENAME, MOVE_FOLDER, MOVE_VOLUME, TRASH }

/** PENDING is written BEFORE the operation and replaced by DONE/FAILED after it: a PENDING left behind means "interrupted". */
enum class State { PENDING, DONE, FAILED, SKIPPED, UNDONE }

data class Entry(
    val seq: Long,
    val runId: String,
    val at: Long,
    val op: Op,
    val changeId: String,
    val from: Loc,
    val to: Loc?,
    val state: State,
    val note: String? = null,
    /** Id of the item in the trash, to restore it. */
    val trashId: String? = null,
    val size: Long = 0,
)

/** The log of what the agent did (and of what it refused to do), used for "Annuler" and for the recovery after a crash. */
interface Journal {
    fun append(e: Entry)
    /** The latest state of every step, in order. */
    fun entries(): List<Entry>
    fun nextSeq(): Long
    fun clear()
}

class MemoryJournal : Journal {
    private val list = ArrayList<Entry>()
    @Synchronized override fun append(e: Entry) { list += e }
    @Synchronized override fun entries(): List<Entry> = list.groupBy { it.seq }.map { (_, v) -> v.last() }.sortedBy { it.seq }
    @Synchronized override fun nextSeq(): Long = (list.maxOfOrNull { it.seq } ?: 0) + 1
    @Synchronized override fun clear() { list.clear() }
}

/**
 * Append-only JSON lines in a private file. Every state change is one more line for the same [Entry.seq]; the last line wins.
 * A torn last line (power cut while writing) is ignored. Old finished entries are dropped after [keepMs] (default 35 days,
 * a little longer than the trash).
 */
class FileJournal(private val file: File, private val keepMs: Long = 35L * 86_400_000, private val now: () -> Long = System::currentTimeMillis) : Journal {
    @Synchronized override fun append(e: Entry) {
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file, true).use { out ->
            out.write((encode(e) + "\n").toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    @Synchronized override fun entries(): List<Entry> {
        if (!file.isFile) return emptyList()
        val all = file.readLines(Charsets.UTF_8).mapNotNull { runCatching { decode(it) }.getOrNull() }
        return all.groupBy { it.seq }.map { (_, v) -> v.last() }.sortedBy { it.seq }
    }

    @Synchronized override fun nextSeq(): Long = (entries().maxOfOrNull { it.seq } ?: 0) + 1

    @Synchronized override fun clear() { file.delete() }

    /** Rewrites the file with the latest state of each step, without the old finished ones. */
    @Synchronized fun compact() {
        val keep = entries().filter { it.state == State.PENDING || now() - it.at < keepMs }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(keep.joinToString("") { encode(it) + "\n" }, Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun loc(l: Loc?) = l?.let { linkedMapOf("v" to it.volume, "f" to it.folder, "n" to it.name) }

    internal fun encode(e: Entry) = JsonLite.write(linkedMapOf("s" to e.seq, "r" to e.runId, "t" to e.at, "o" to e.op.name, "c" to e.changeId, "from" to loc(e.from),
        "to" to loc(e.to), "st" to e.state.name, "note" to e.note, "tr" to e.trashId, "z" to e.size))

    @Suppress("UNCHECKED_CAST")
    internal fun decode(line: String): Entry {
        val m = JsonLite.obj(line)
        fun l(k: String): Loc? = (m[k] as? Map<String, Any?>)?.let { Loc(it.str("v").orEmpty(), it.str("f").orEmpty(), it.str("n").orEmpty()) }
        return Entry(m.long("s")!!, m.str("r")!!, m.long("t") ?: 0, Op.valueOf(m.str("o")!!), m.str("c").orEmpty(), l("from")!!, l("to"), State.valueOf(m.str("st")!!),
            m.str("note"), m.str("tr"), m.long("z") ?: 0)
    }
}
