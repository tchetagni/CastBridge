package castbridge.core.library.agent

data class StepReport(val changeId: String, val name: String, val state: State, val note: String? = null)

data class RunResult(val runId: String, val reports: List<StepReport>) {
    val done get() = reports.count { it.state == State.DONE }
    val failed get() = reports.count { it.state == State.FAILED }
    val skipped get() = reports.count { it.state == State.SKIPPED }
}

data class ExecProgress(val index: Int, val total: Int, val name: String, val bytesDone: Long = 0, val bytesTotal: Long = 0)

data class UndoResult(val runId: String?, val reports: List<StepReport>) {
    val restored get() = reports.count { it.state == State.UNDONE }
    val failed get() = reports.count { it.state == State.FAILED }
}

/**
 * Applies the changes the user ticked, one at a time, safely:
 *  - only what was selected, in the order renames, moves, trash;
 *  - never an overwrite (a taken name gets a suffix), never a path outside the library, never a forbidden character;
 *  - never while the file is playing, never a protected file, never a file that changed since the analysis;
 *  - a move between volumes only if >= [AgentContext.minFreeAfterBytes] stays free on the destination;
 *  - nothing goes to the trash without `confirmDeletions = true` (the UI asks), and never the last copy of a file;
 *  - every step is written in the [Journal] BEFORE (PENDING) and AFTER (DONE / FAILED), so a cut can be recovered and undone.
 */
class Executor(
    private val ops: LibraryOps,
    private val journal: Journal,
    private val ctx: AgentContext = AgentContext(),
    private val newId: () -> String = { java.util.UUID.randomUUID().toString().take(8) },
) {
    private fun order(t: ChangeType) = when (t) { ChangeType.RENAME -> 0; ChangeType.MOVE -> 1; ChangeType.TRASH -> 2 }

    fun run(plan: Plan, selected: Set<String>, confirmDeletions: Boolean, runId: String = newId(),
            progress: (ExecProgress) -> Unit = {}, cancelled: () -> Boolean = { false }): RunResult {
        val sel = plan.withPairs(selected)
        val changes = plan.changes.filter { it.id in sel }.sortedWith(compareBy({ order(it.type) }, { it.file.name }))
        val already = journal.entries().filter { it.runId == runId && it.state == State.DONE }.map { it.changeId }.toSet()
        val reports = ArrayList<StepReport>()
        val trashedKeys = HashSet<String>()
        changes.forEachIndexed { i, c ->
            if (cancelled()) return@forEachIndexed
            if (c.id in already) { reports += StepReport(c.id, c.file.name, State.DONE, "déjà fait (reprise)"); if (c.type == ChangeType.TRASH) trashedKeys += c.file.key; return@forEachIndexed }
            progress(ExecProgress(i, changes.size, c.file.name))
            reports += runChange(c, runId, confirmDeletions, plan.childActive, trashedKeys, { d, t -> progress(ExecProgress(i, changes.size, c.file.name, d, t)) }, cancelled)
        }
        return RunResult(runId, reports)
    }

    // ------------------------------------------------------------------ validation

    private fun sourceProblem(f: FileRef): String? {
        if (f.name.isEmpty() || f.name == "." || f.name == ".." || f.name.any { it == '/' || it == '\\' || it == '\u0000' }) return "nom de fichier invalide"
        if (f.folder.isNotEmpty() && (f.folder.startsWith("/") || f.folder.startsWith("\\") || f.folder.split('/').any { it == ".." || it == "." })) return "chemin hors de la bibliothèque"
        return null
    }

    private fun problem(c: Change): String? {
        sourceProblem(c.file)?.let { return it }
        c.toName?.let { SafeName.checkName(it)?.let { r -> return "nom refusé : $r" } }
        c.toFolder?.let { f -> SafeName.checkFolder(f)?.let { r -> return "dossier refusé : $r" }; if (!ops.folders) return "cette bibliothèque n'a pas de dossiers" }
        when (c.type) {
            ChangeType.RENAME -> if (c.toName == null && c.toFolder == null) return "rien à changer"
            ChangeType.MOVE -> if (c.toVolume.isNullOrEmpty()) return "destination manquante"
            ChangeType.TRASH -> {}
        }
        return null
    }

    // ------------------------------------------------------------------ one change

    private fun entry(runId: String, op: Op, c: Change, from: Loc, to: Loc?, state: State, note: String? = null, trashId: String? = null, seq: Long = journal.nextSeq()) =
        Entry(seq, runId, System.currentTimeMillis(), op, c.id, from, to, state, note, trashId, c.file.size).also { journal.append(it) }

    private fun skipped(runId: String, c: Change, why: String): StepReport {
        entry(runId, when (c.type) { ChangeType.RENAME -> Op.RENAME; ChangeType.MOVE -> Op.MOVE_VOLUME; ChangeType.TRASH -> Op.TRASH }, c, c.file.loc, null, State.SKIPPED, why)
        return StepReport(c.id, c.file.name, State.SKIPPED, why)
    }

    private fun runChange(c: Change, runId: String, confirm: Boolean, planChild: Boolean, trashed: MutableSet<String>, onBytes: (Long, Long) -> Unit, cancelled: () -> Boolean): StepReport {
        val f = c.file
        problem(c)?.let { return skipped(runId, c, it) }
        if (planChild || ctx.guard.childProfileActive) return skipped(runId, c, "profil enfant actif : aucune modification")
        if (f.guarded || ctx.guard.isProtected(f)) return skipped(runId, c, "protégé par le contrôle parental")
        if (c.type == ChangeType.TRASH && !confirm) return skipped(runId, c, "suppression non confirmée")
        val loc = f.loc
        if (f.playing || ops.isPlaying(loc)) return skipped(runId, c, "en cours de lecture")
        val st = ops.stat(loc) ?: return skipped(runId, c, "fichier introuvable (déplacé ou supprimé depuis l'analyse)")
        if (st.size != f.size) return skipped(runId, c, "le fichier a changé depuis l'analyse")
        return when (c.type) {
            ChangeType.RENAME -> rename(c, runId, loc)
            ChangeType.MOVE -> move(c, runId, loc, onBytes, cancelled)
            ChangeType.TRASH -> trash(c, runId, loc, trashed)
        }
    }

    private fun splitExt(n: String): Pair<String, String> {
        val dot = n.lastIndexOf('.')
        return if (dot > 0 && n.length - dot <= 6) n.substring(0, dot) to n.substring(dot) else n to ""
    }

    /** The wanted name if free, else "name (2).ext", "name (3).ext"… Never an existing name. */
    private fun freeName(volume: String, folder: String, wanted: String): String {
        if (!ops.nameTaken(Loc(volume, folder, wanted))) return wanted
        val (b, e) = splitExt(wanted)
        var i = 2
        while (i < 1000) { val n = "$b ($i)$e"; if (!ops.nameTaken(Loc(volume, folder, n))) return n; i++ }
        return "$b (${System.nanoTime()})$e"
    }

    private fun rename(c: Change, runId: String, start: Loc): StepReport {
        var cur = start
        // 1. the name, in place
        val wantName = c.toName
        if (wantName != null && wantName != cur.name) {
            val final = freeName(cur.volume, cur.folder, wantName)
            val to = cur.copy(name = final)
            val seq = journal.nextSeq()
            entry(runId, Op.RENAME, c, cur, to, State.PENDING, seq = seq)
            when (val r = ops.rename(cur, final)) {
                is OpResult.Ok -> { val real = r.loc.copy(folder = cur.folder, volume = cur.volume); entry(runId, Op.RENAME, c, cur, real, State.DONE, if (final != wantName) "nom déjà pris : $final" else if (real.name != final) "nom changé par le système : ${real.name}" else null, seq = seq); cur = real }
                is OpResult.Fail -> { entry(runId, Op.RENAME, c, cur, to, State.FAILED, r.reason, seq = seq); return StepReport(c.id, c.file.name, State.FAILED, r.reason) }
            }
        }
        // 2. the folder (phone)
        val wantFolder = c.toFolder
        if (wantFolder != null && !wantFolder.equals(cur.folder, ignoreCase = true)) {
            when (val m = ops.mkdirs(cur.volume, wantFolder)) {
                is OpResult.Fail -> return StepReport(c.id, c.file.name, State.FAILED, "dossier impossible : ${m.reason}")
                else -> {}
            }
            var name = cur.name
            if (!ops.flatNames && ops.nameTaken(Loc(cur.volume, wantFolder, name))) {           // same name already in the target folder: make ours unique first
                val free = freeName(cur.volume, wantFolder, name)
                val seq = journal.nextSeq()
                val to = cur.copy(name = free)
                entry(runId, Op.RENAME, c, cur, to, State.PENDING, seq = seq)
                when (val r = ops.rename(cur, free)) {
                    is OpResult.Ok -> { entry(runId, Op.RENAME, c, cur, to, State.DONE, "nom déjà pris dans le dossier", seq = seq); cur = to; name = free }
                    is OpResult.Fail -> { entry(runId, Op.RENAME, c, cur, to, State.FAILED, r.reason, seq = seq); return StepReport(c.id, c.file.name, State.FAILED, r.reason) }
                }
            }
            val to = Loc(cur.volume, wantFolder, name)
            val seq = journal.nextSeq()
            entry(runId, Op.MOVE_FOLDER, c, cur, to, State.PENDING, seq = seq)
            when (val r = ops.moveToFolder(cur, wantFolder)) {
                is OpResult.Ok -> { entry(runId, Op.MOVE_FOLDER, c, cur, to, State.DONE, seq = seq); cur = to }
                is OpResult.Fail -> { entry(runId, Op.MOVE_FOLDER, c, cur, to, State.FAILED, r.reason, seq = seq); return StepReport(c.id, c.file.name, State.FAILED, r.reason) }
            }
        }
        return StepReport(c.id, c.file.name, State.DONE, if (cur.name != wantName && wantName != null) "renommé en ${cur.name}" else null)
    }

    private fun move(c: Change, runId: String, from: Loc, onBytes: (Long, Long) -> Unit, cancelled: () -> Boolean): StepReport {
        val destId = c.toVolume!!
        val vols = ops.volumes()
        val dest = vols.firstOrNull { it.id == destId } ?: return skipped(runId, c, "destination absente (clé retirée ?)")
        if (dest.id == from.volume) return skipped(runId, c, "déjà sur ce volume")
        if (!dest.writable) return skipped(runId, c, "destination en lecture seule")
        val size = c.file.size
        if (size > dest.maxFileBytes) return skipped(runId, c, "fichier trop gros pour ${dest.fs.ifEmpty { "ce volume" }}")
        if (dest.free >= 0 && dest.free - size < ctx.minFreeAfterBytes)
            return skipped(runId, c, "pas assez de place : il doit rester ${Text.size(ctx.minFreeAfterBytes)} libre sur « ${dest.label} » (${Text.size(maxOf(dest.free, 0))} libre, fichier ${Text.size(size)})")
        val to = Loc(destId, from.folder, from.name)
        if (ops.stat(to) != null) return skipped(runId, c, "un fichier du même nom existe déjà sur « ${dest.label} »")
        val seq = journal.nextSeq()
        entry(runId, Op.MOVE_VOLUME, c, from, to, State.PENDING, seq = seq)
        return when (val r = ops.moveToVolume(from, destId, onBytes, cancelled)) {
            is OpResult.Ok -> { entry(runId, Op.MOVE_VOLUME, c, from, r.loc, State.DONE, seq = seq); StepReport(c.id, c.file.name, State.DONE) }
            is OpResult.Fail -> { entry(runId, Op.MOVE_VOLUME, c, from, to, State.FAILED, r.reason, seq = seq); StepReport(c.id, c.file.name, State.FAILED, r.reason) }
        }
    }

    private fun trash(c: Change, runId: String, from: Loc, trashed: MutableSet<String>): StepReport {
        val keep = c.keep
        if (keep != null) {
            if (keep.key in trashed) return skipped(runId, c, "la copie à garder a elle-même été mise à la corbeille : rien n'est supprimé")
            val ks = ops.stat(keep.loc)
            if (ks == null || ks.size != keep.size) return skipped(runId, c, "la copie à garder est introuvable : rien n'est supprimé")
        }
        val seq = journal.nextSeq()
        entry(runId, Op.TRASH, c, from, null, State.PENDING, seq = seq)
        return when (val r = ops.trash(from)) {
            is OpResult.Ok -> { trashed += c.file.key; entry(runId, Op.TRASH, c, from, null, State.DONE, trashId = r.trashId, seq = seq); StepReport(c.id, c.file.name, State.DONE, "dans la corbeille") }
            is OpResult.Fail -> { entry(runId, Op.TRASH, c, from, null, State.FAILED, r.reason, seq = seq); StepReport(c.id, c.file.name, State.FAILED, r.reason) }
        }
    }

    // ------------------------------------------------------------------ recovery after a cut

    /**
     * Looks at the steps left PENDING by a crash and decides, from the real state of the files, whether they happened.
     * Nothing is ever redone or undone here: it only makes the journal tell the truth. Returns what it found.
     */
    fun recover(): List<StepReport> {
        val out = ArrayList<StepReport>()
        for (e in journal.entries().filter { it.state == State.PENDING }) {
            val fromHere = ops.stat(e.from) != null
            val toHere = e.to?.let { ops.stat(it) != null } ?: false
            val re: Pair<State, String?> = when (e.op) {
                Op.TRASH -> {
                    val id = ops.findInTrash(e.from)
                    when {
                        id != null && !fromHere -> { journal.append(e.copy(state = State.DONE, trashId = id, note = "reprise après coupure", at = System.currentTimeMillis())); out += StepReport(e.changeId, e.from.name, State.DONE, "reprise après coupure"); continue }
                        fromHere -> State.FAILED to "interrompu : le fichier est resté à sa place"
                        else -> State.FAILED to "état incertain : fichier introuvable"
                    }
                }
                else -> when {
                    toHere && !fromHere -> State.DONE to "reprise après coupure"
                    fromHere && !toHere -> State.FAILED to "interrompu : rien n'a changé"
                    else -> State.FAILED to "état incertain : vérifiez la bibliothèque"
                }
            }
            journal.append(e.copy(state = re.first, note = re.second, at = System.currentTimeMillis()))
            out += StepReport(e.changeId, e.from.name, re.first, re.second)
        }
        return out
    }

    /** Runs with no entry lost: the changes of [runId] that did not finish, to offer "Reprendre". */
    fun unfinished(plan: Plan, selected: Set<String>, runId: String): Set<String> {
        val done = journal.entries().filter { it.runId == runId && it.state == State.DONE }.map { it.changeId }.toSet()
        return plan.withPairs(selected) - done
    }

    // ------------------------------------------------------------------ undo

    /** Restores the names and places of the last run (or of [runId]), newest step first. Never overwrites; reports what it could not restore. */
    fun undo(runId: String? = null, only: Set<Long>? = null): UndoResult {
        val all = journal.entries()
        val target = runId ?: all.filter { it.state == State.DONE }.maxByOrNull { it.seq }?.runId ?: return UndoResult(null, emptyList())
        val steps = all.filter { it.runId == target && it.state == State.DONE && (only == null || it.seq in only) }.sortedByDescending { it.seq }
        val reports = ArrayList<StepReport>()
        for (e in steps) {
            val to = e.to
            fun fail(why: String): StepReport { journal.append(e.copy(note = "annulation impossible : $why", at = System.currentTimeMillis())); return StepReport(e.changeId, e.from.name, State.FAILED, why) }
            fun ok(): StepReport { journal.append(e.copy(state = State.UNDONE, at = System.currentTimeMillis())); return StepReport(e.changeId, e.from.name, State.UNDONE) }
            val r: StepReport = when (e.op) {
                Op.TRASH -> {
                    val id = e.trashId
                    if (id == null) fail("pas d'identifiant de corbeille")
                    else when (val x = ops.restore(id, e.from)) { is OpResult.Ok -> ok(); is OpResult.Fail -> fail(x.reason) }
                }
                Op.RENAME -> when {
                    to == null -> fail("journal incomplet")
                    ops.stat(to) == null -> fail("le fichier « ${to.name} » n'est plus là")
                    ops.isPlaying(to) -> fail("en cours de lecture")
                    ops.nameTaken(e.from) -> fail("le nom d'origine « ${e.from.name} » est pris")
                    else -> when (val x = ops.rename(to, e.from.name)) { is OpResult.Ok -> ok(); is OpResult.Fail -> fail(x.reason) }
                }
                Op.MOVE_FOLDER -> when {
                    to == null -> fail("journal incomplet")
                    ops.stat(to) == null -> fail("le fichier « ${to.name} » n'est plus là")
                    ops.isPlaying(to) -> fail("en cours de lecture")
                    !ops.flatNames && ops.nameTaken(Loc(e.from.volume, e.from.folder, to.name)) -> fail("un fichier du même nom est déjà dans « ${e.from.folder.ifEmpty { "la racine" }} »")
                    else -> when (val x = ops.moveToFolder(to, e.from.folder)) { is OpResult.Ok -> ok(); is OpResult.Fail -> fail(x.reason) }
                }
                Op.MOVE_VOLUME -> when {
                    to == null -> fail("journal incomplet")
                    ops.stat(to) == null -> fail("le fichier « ${to.name} » n'est plus là")
                    ops.isPlaying(to) -> fail("en cours de lecture")
                    else -> {
                        val src = ops.volumes().firstOrNull { it.id == e.from.volume }
                        if (src == null) fail("volume d'origine absent")
                        else if (src.free >= 0 && src.free - e.size < ctx.minFreeAfterBytes) fail("pas assez de place sur « ${src.label} »")
                        else when (val x = ops.moveToVolume(to, e.from.volume, { _, _ -> }, { false })) { is OpResult.Ok -> ok(); is OpResult.Fail -> fail(x.reason) }
                    }
                }
            }
            reports += r
        }
        return UndoResult(target, reports)
    }
}
