package castbridge.core.library.agent

/** RENAME (and, on the phone, put in a folder), MOVE to another volume, TRASH = "Corbeille CastBridge" (recoverable 30 days, never a real delete). */
enum class ChangeType { RENAME, MOVE, TRASH }

/** Who proposed the change: the local rules, what the user corrected before, or the optional AI model. */
enum class Source { RULES, LEARNED, AI }

enum class TrashWhy { DUPLICATE, LOWER_QUALITY, WATCHED_OLD,
    /** Interrupted download (.part, .aria2…): only proposed by [Health], never ticked. */
    PARTIAL,
    /** File of 0 byte: only proposed by [Health], never ticked. */
    EMPTY }

/**
 * One proposed change, shown to the user with its "before → after". Nothing happens until the user ticks it and confirms.
 * [checked] is only the initial state of the checkbox: it is never "accept all" by default, and TRASH is always unticked.
 */
data class Change(
    val id: String,
    val type: ChangeType,
    val file: FileRef,
    val kind: Kind,
    val toName: String? = null,
    /** Phone only (the TV library is flat). Relative folder, never absolute, never "..". */
    val toFolder: String? = null,
    val toVolume: String? = null,
    val why: TrashWhy? = null,
    /** French sentence explaining the proposal. */
    val reason: String,
    val confidence: Double,
    val source: Source = Source.RULES,
    val checked: Boolean = false,
    /** Bytes moved (MOVE) or freed (TRASH). */
    val bytes: Long = file.size,
    /** The file that stays, for TRASH of a duplicate. */
    val keep: FileRef? = null,
    /** Matching key of the title, used to learn from the user's edits. */
    val titleKey: String = "",
    /** Changes that must go together (a video and its subtitles share the same pair key). */
    val pairKey: String? = null,
) {
    val after: String get() = when (type) {
        ChangeType.RENAME -> listOfNotNull(toFolder?.takeIf { it.isNotEmpty() }, toName ?: file.name).joinToString("/")
        ChangeType.MOVE -> toVolume.orEmpty()
        ChangeType.TRASH -> "Corbeille CastBridge"
    }
}

/** A file the agent deliberately leaves alone, and why (shown in the plan, never hidden). */
data class Skipped(val file: FileRef, val reason: String)

data class Plan(val changes: List<Change>, val skipped: List<Skipped> = emptyList(), val notes: List<String> = emptyList()) {
    val renames get() = changes.filter { it.type == ChangeType.RENAME }
    val moves get() = changes.filter { it.type == ChangeType.MOVE }
    val trash get() = changes.filter { it.type == ChangeType.TRASH }
    val freedBytes: Long get() = trash.sumOf { it.bytes }

    /** The checkboxes as they start: what the rules are confident about. Never includes a TRASH. */
    fun defaultSelection(): Set<String> = changes.filter { it.checked && it.type != ChangeType.TRASH }.map { it.id }.toSet()

    /** "Tout accepter": every non-destructive change. Deletions (trash) still need their own confirmation. */
    fun allSafe(): Set<String> = changes.filter { it.type != ChangeType.TRASH }.map { it.id }.toSet()

    fun byId(id: String): Change? = changes.firstOrNull { it.id == id }

    /** A paired change (subtitles) follows its video. */
    fun withPairs(selected: Set<String>): Set<String> {
        val pairs = changes.filter { it.id in selected && it.pairKey != null }.map { it.pairKey }.toSet()
        return selected + changes.filter { it.pairKey in pairs && it.type != ChangeType.TRASH }.map { it.id }
    }

    sealed class Edit {
        data class Ok(val plan: Plan, val learned: Boolean) : Edit()
        data class Refused(val reason: String) : Edit()
    }

    /** The user typed another target name: validated like any other name; teaches [learned] the title correction. */
    fun withEditedName(id: String, newName: String, learned: LearnedRules? = null): Edit {
        val c = byId(id) ?: return Edit.Refused("changement inconnu")
        if (c.type != ChangeType.RENAME) return Edit.Refused("seul un renommage peut être modifié")
        val n = newName.trim()
        SafeName.checkName(n)?.let { return Edit.Refused(it) }
        val did = learned?.recordEdit(c.titleKey, n) ?: false
        return Edit.Ok(copy(changes = changes.map { if (it.id == id) it.copy(toName = n, source = Source.LEARNED, checked = true, confidence = 1.0, reason = "Nom choisi par vous") else it }), did)
    }
}

/** What the agent concluded about one file, before the plan. */
data class ItemInfo(val file: FileRef, val parsed: Parsed, val proposal: Proposal, val source: Source)

data class Stats(val files: Int, val wellNamed: Int, val toRename: Int, val unknown: Int, val duplicateGroups: Int, val duplicateBytes: Long, val aiUsed: Int)

data class Analysis(val snapshot: LibrarySnapshot, val items: List<ItemInfo>, val plan: Plan, val insights: List<Insight>, val stats: Stats, val habits: Habits = Habits.NONE,
                    /** State of the library: incomplete series, quality duplicates, broken files, recoverable space, priorities (see [Health]). */
                    val health: HealthReport = HealthReport.EMPTY)
