package castbridge.core.content

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import java.io.File

/**
 * One decision of a reviewer about one item (docs/CONTENT-VALIDATION.md § 3). Records live in `content/validation/<lot>.jsonl`
 * (one JSON object per line, append-only history) or on the server; they are NEVER part of a signed lot. The tool
 * tools/content-validation applies the effective decisions to the sources and rebuilds the lots.
 *
 * @param hash content hash ([ContentHash]) the reviewer looked at; a `validated` decision is void when the content has changed
 */
data class ValidationRecord(
    val id: String, val kind: ContentKind, val state: ContentState, val reviewer: String, val date: String, val note: String = "",
    val hash: String? = null, val lot: String? = null,
) {
    fun toJson(): String = JsonLite.write(linkedMapOf("id" to id, "kind" to kind.key, "state" to state.key, "reviewer" to reviewer,
        "date" to date, "note" to note.ifEmpty { null }, "hash" to hash, "lot" to lot))

    /** Problems of the record itself (empty = acceptable). */
    fun problems(): List<String> {
        val e = ArrayList<String>()
        if (!ID.matches(id)) e += "id invalide « $id »"
        if (reviewer.isBlank() || reviewer.length > 60 || reviewer.any { it.isISOControl() }) e += "relecteur manquant ou invalide"
        if (!DATE.matches(date)) e += "date invalide « $date » (AAAA-MM-JJ)"
        if (note.length > 500) e += "note trop longue (500 caractères au plus)"
        if (state != ContentState.REVIEW && state != ContentState.VALIDATED && note.isBlank()) e += "une note est obligatoire pour ${state.key}"
        if (state == ContentState.VALIDATED && hash.isNullOrBlank()) e += "l'empreinte (hash) est obligatoire pour valider"
        if (hash != null && !HASH.matches(hash)) e += "empreinte invalide « $hash »"
        return e
    }

    companion object {
        val ID = Regex("[A-Za-z0-9_.:+/-]{1,64}")
        private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val HASH = Regex("[0-9a-f]{16}")

        fun parse(line: String): ValidationRecord {
            val m = JsonLite.obj(line)
            fun req(k: String) = m.str(k) ?: throw IllegalArgumentException("« $k » manquant")
            return ValidationRecord(req("id"), ContentKind.of(m.str("kind")) ?: throw IllegalArgumentException("« kind » inconnu"),
                ContentState.of(req("state")) ?: throw IllegalArgumentException("« state » inconnu"), req("reviewer"), req("date"),
                m.str("note").orEmpty(), m.str("hash"), m.str("lot"))
        }
    }
}

/** What the ledger concludes for one item. */
data class Effective(val state: ContentState, val record: ValidationRecord?, val stale: Boolean)

/** The history of decisions: the last record of an item wins; a decision made on another content hash is stale. */
class ValidationLedger(records: List<ValidationRecord> = emptyList()) {
    private val byId = LinkedHashMap<String, MutableList<ValidationRecord>>()
    val errors = ArrayList<String>()

    init { records.forEach { add(it) } }

    /** Adds a record if it is well formed and a legal move from the item's last state; otherwise remembers why in [errors]. */
    fun add(r: ValidationRecord): Boolean {
        val bad = r.problems()
        if (bad.isNotEmpty()) { errors += "${r.id}: ${bad.joinToString(", ")}"; return false }
        val history = byId.getOrPut(r.id) { ArrayList() }
        val from = history.lastOrNull()?.state ?: ContentState.REVIEW
        if (!from.canMoveTo(r.state)) { errors += "${r.id}: ${from.key} → ${r.state.key} n'est pas une transition permise"; return false }
        history += r
        return true
    }

    fun history(id: String): List<ValidationRecord> = byId[id].orEmpty()
    val ids: Set<String> get() = byId.keys

    /** State of [id] given the hash of its current content (null = unknown: the decision is trusted). */
    fun effective(id: String, currentHash: String?): Effective {
        val last = byId[id]?.lastOrNull() ?: return Effective(ContentState.REVIEW, null, false)
        val stale = last.hash != null && currentHash != null && last.hash != currentHash
        // content changed after the decision: back to review whatever it was (a rejected text that was rewritten is a new text)
        return if (stale) Effective(ContentState.REVIEW, last, true) else Effective(last.state, last, false)
    }

    companion object {
        fun load(files: List<File>): ValidationLedger {
            val l = ValidationLedger()
            for (f in files.sortedBy { it.name }) f.readLines(Charsets.UTF_8).forEachIndexed { i, line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                try { l.add(ValidationRecord.parse(line)) } catch (e: IllegalArgumentException) { l.errors += "${f.name}:${i + 1}: ${e.message}" }
            }
            return l
        }
    }
}
