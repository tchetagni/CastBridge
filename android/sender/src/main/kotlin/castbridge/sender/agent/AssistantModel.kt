package castbridge.sender.agent

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import castbridge.core.library.agent.*
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.tv.TvClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The screens of the assistant. The guided path has three steps: Analyser (INTRO, ANALYZING) → Vérifier (PLAN) → Appliquer (RECAP, RUNNING, DONE).
 * The others (bin, history, settings) are side rooms.
 */
enum class Step { INTRO, ANALYZING, PLAN, RECAP, RUNNING, DONE, TRASH, HISTORY, SETTINGS }

/** 1, 2 or 3 for the stepper at the top of the guided path; 0 outside of it. */
val Step.wizard: Int get() = when (this) {
    Step.INTRO, Step.ANALYZING -> 1
    Step.PLAN -> 2
    Step.RECAP, Step.RUNNING, Step.DONE -> 3
    else -> 0
}

/** An item of the "Corbeille CastBridge" (TV or phone). */
data class BinItem(val id: String, val name: String, val size: Long, val expiresAt: Long = 0)

/** What is already known while the folder is still being read ("au fil de l'eau"). */
data class Live(val files: Int = 0, val folders: Int = 0, val toRename: Int = 0, val examples: List<Pair<String, String>> = emptyList(), val note: String = "")

/**
 * The process-wide home of the assistant's work. The analysis and the rangement run in THIS scope, not in the screen's: closing the dialog (or
 * turning the phone) does not stop them, and reopening the assistant shows where they are. They live as long as the app process does; there is no
 * foreground service (a long analysis in a killed process simply restarts, and the caches below make the restart cheap).
 */
object AssistantHost {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var model: AssistantModel? = null
    private var key: String? = null

    /** The model of the current TV (or of the phone alone). A model that is idle or finished is replaced by a fresh one. */
    fun modelFor(ctx: Context, client: TvClient?): AssistantModel {
        val k = client?.base ?: "phone"
        val m = model
        if (m != null && key == k && m.keepAlive()) return m
        return AssistantModel(ctx.applicationContext, client, scope).also { model = it; key = k; if (client == null) it.source = Origin.PHONE }
    }
}

/**
 * The assistant's state and actions. The agent PROPOSES ([analyze]), the user VALIDATES (ticks, edits, confirms), only then
 * [apply] changes anything; [undo] puts names and places back. All heavy work runs off the main thread, in [AssistantHost.scope].
 */
class AssistantModel(private val ctx: Context, private val client: TvClient?, private val scope: CoroutineScope) {
    var step by mutableStateOf(Step.INTRO)
    var source by mutableStateOf(Origin.TV)
    var phoneTree by mutableStateOf<Uri?>(AgentStore.settings.phoneTreeUri?.let(Uri::parse))
    var progress by mutableStateOf(Progress(Phase.READ))
    var live by mutableStateOf(Live())
    var analysis by mutableStateOf<Analysis?>(null)
    var plan by mutableStateOf(Plan(emptyList()))
    var selected by mutableStateOf<Set<String>>(emptySet())
    var message by mutableStateOf<String?>(null)
    /** « Retenu : … » after a manual correction became a rule. */
    var learnedNote by mutableStateOf<String?>(null)
    var running by mutableStateOf(ExecProgress(0, 0, ""))
    var result by mutableStateOf<RunResult?>(null)
    var undone by mutableStateOf<UndoResult?>(null)
    var bin by mutableStateOf<List<BinItem>>(emptyList())
    var binBusy by mutableStateOf(false)
    var sentPreview by mutableStateOf<String?>(null)
    var filter by mutableStateOf(PlanFilter.ALL)
    /** The proposal is on screen but duplicates / AI are still being looked for (they are added to the plan when ready). */
    var refining by mutableStateOf(false)
    var refineProgress by mutableStateOf(Progress(Phase.FINGERPRINT))
    /** What the previous analysis found: shown on the home screen so that the user knows how fresh it is. */
    var last by mutableStateOf(AgentStore.settings.lastAnalysis)

    @Volatile private var cancel = false
    @Volatile private var skipRefine = false
    @Volatile private var generation = 0
    private var job: Job? = null
    private var lastRun: String? = null
    private var touched = false
    private val edits = LinkedHashMap<String, String>()
    private var saf: Pair<Uri, SafLibrary>? = null

    /** Keep this model when the assistant is reopened: it is busy, or holds a proposal that is still fresh. */
    fun keepAlive(): Boolean = step == Step.ANALYZING || step == Step.RUNNING || (step == Step.PLAN && plan.changes.isNotEmpty() && System.currentTimeMillis() - lastAt < 30 * 60_000L)
    private var lastAt = 0L

    private fun tvOps() = TvLibraryOps(client ?: error("TV"))
    private fun saf(): SafLibrary {
        val t = phoneTree ?: error("dossier")
        saf?.let { if (it.first == t) return it.second }
        return SafLibrary(ctx, t, AgentStore.cache).also { saf = t to it }
    }

    fun pickedTree(uri: Uri) { phoneTree = uri; AgentStore.settings.phoneTreeUri = uri.toString(); source = Origin.PHONE; saf = null }

    // ------------------------------------------------------------------ analysis

    /**
     * Two phases so that the user sees something quickly. Phase 1 (local rules, a fraction of a second even for thousands of files) gives the
     * proposal; the screen opens on it. Phase 2 (fingerprints of same-size TV files, the optional AI) runs while the user reads and ticks, and ADDS
     * its findings to the plan (the user's ticks and manual names are kept). It reads only what earlier analyses had not (cache).
     */
    fun analyze() {
        cancel = false; skipRefine = false; message = null; sentPreview = null; learnedNote = null; touched = false; edits.clear(); refining = false; filter = PlanFilter.ALL
        val my = ++generation
        step = Step.ANALYZING; live = Live(); progress = Progress(Phase.READ, 0, 0, "Lecture de la bibliothèque…")
        job = scope.launch {
            try {
                val tv = source == Origin.TV
                val seen = ArrayList<FileRef>()
                var nextPreview = 100
                val snap = if (tv) TvSnapshot.read(client ?: error("TV"))
                else saf().snapshot(onProgress = { w ->
                    seen += w.latest
                    live = live.copy(files = w.filesFound, folders = w.foldersRead)
                    progress = Progress(Phase.READ, w.filesFound, 0, "${w.filesFound} fichiers lus dans ${w.foldersRead} dossier(s)…")
                    if (seen.size >= nextPreview) { nextPreview = seen.size * 2; preview(seen) }
                }, cancelled = { cancel })
                if (cancel || my != generation) { if (my == generation) step = Step.INTRO; return@launch }
                val ctx0 = AgentStore.agentContext(folders = !tv)
                progress = Progress(Phase.UNDERSTAND, 0, snap.files.size)
                val quick = LibraryAgent(ctx0, AgentStore.learned).analyze(snap, { p -> progress = p }, { cancel })
                if (cancel || my != generation) { if (my == generation) step = Step.INTRO; return@launch }
                publish(quick)
                AgentStore.settings.lastAnalysis = LastAnalysis.of(quick, System.currentTimeMillis()).also { last = it }
                lastAt = System.currentTimeMillis()
                step = Step.PLAN
                val needRefine = (tv && Duplicates.candidates(snap.files).isNotEmpty()) || (AgentStore.settings.aiEnabled)
                if (needRefine) refine(snap, tv, my)
            } catch (e: Exception) {
                if (my == generation) { message = describe(e); step = Step.INTRO }
            }
        }
    }

    /** What the rules already find in the files read so far (cheap: tens of milliseconds per thousand names). */
    private fun preview(files: List<FileRef>) {
        runCatching {
            val a = LibraryAgent(AgentStore.agentContext(folders = true), AgentStore.learned).analyze(LibrarySnapshot(Origin.PHONE, files.toList(), emptyList()))
            val r = a.plan.renames.filter { it.toName != null }
            live = live.copy(toRename = r.size, examples = r.take(4).map { it.file.name to it.after })
        }
    }

    private fun refine(snap: LibrarySnapshot, tv: Boolean, my: Int) {
        refining = true; refineProgress = Progress(Phase.FINGERPRINT, 0, 0, "")
        try {
            val recorder = AgentStore.serverModel()?.let { Recording(it) }
            val fpr = if (tv) CachingFingerprinter(TvFingerprinter(client!!), AgentStore.cache) else null
            val a = LibraryAgent(AgentStore.agentContext(folders = !tv), AgentStore.learned, recorder, fpr)
                .analyze(snap, { p -> if (my == generation) refineProgress = p }, { cancel || skipRefine || my != generation })
            if (my != generation || skipRefine || cancel) return
            publish(a)
            sentPreview = recorder?.sent?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            if (my == generation) message = "La recherche des doublons s'est arrêtée : ${describe(e)}"
        } finally { if (my == generation) refining = false }
    }

    /** Shows [a]; ticks, manual names and ignored files of the user are carried over. */
    private fun publish(a: Analysis) {
        var p = a.plan
        if (a.snapshot.origin == Origin.PHONE) phoneTree?.let { t -> p = p.withoutRootSegment(runCatching { android.provider.DocumentsContract.getTreeDocumentId(t).substringAfterLast('/').substringAfterLast(':') }.getOrDefault("")) }
        for ((id, name) in edits) (p.withEditedName(id, name, null) as? Plan.Edit.Ok)?.let { p = it.plan }
        val prev = selected
        analysis = a; plan = p
        selected = if (touched) prev.filter { p.byId(it) != null }.toSet() + edits.keys.filter { p.byId(it) != null } else p.defaultSelection()
    }

    /** « Passer » : stop looking for duplicates and go on with what is already proposed (what was read stays in the cache for next time). */
    fun skipRefining() { skipRefine = true; refining = false }

    fun cancelWork() { cancel = true; generation++; refining = false; if (step == Step.ANALYZING) step = Step.INTRO }

    /** Remembers what was sent to the server model, so that the user can see it afterwards. */
    private class Recording(private val inner: NamingModel) : NamingModel {
        override val id get() = inner.id
        override val remote get() = inner.remote
        val log = StringBuilder()
        val sent: String get() = log.toString().trim()
        override fun suggest(req: SuggestRequest): SuggestResponse { log.append(req.preview()).append('\n'); return inner.suggest(req) }
    }

    private fun describe(e: Exception) = when {
        e is TvClient.HttpError && e.code == 401 -> "Le code PIN de la TV a été refusé."
        else -> e.message ?: e.javaClass.simpleName
    }

    // ------------------------------------------------------------------ choices

    fun toggle(id: String, on: Boolean) { touched = true; selected = if (on) selected + id else selected - id }
    fun toggleMany(ids: Collection<String>, on: Boolean) { touched = true; selected = if (on) selected + ids else selected - ids.toSet() }
    fun selectAllSafe() { touched = true; selected = plan.allSafe() }
    fun selectNone() { touched = true; selected = emptySet() }
    fun selectDefault() { touched = true; selected = plan.defaultSelection() }

    /**
     * The user typed another name: validated, applied to the plan, and learned. When it became a rule, the other files of the same title are
     * proposed again with it right away. Returns an error message or null.
     */
    fun edit(id: String, newName: String): String? {
        val before = plan.byId(id)
        return when (val r = plan.withEditedName(id, newName, AgentStore.learned)) {
            is Plan.Edit.Ok -> {
                touched = true; plan = r.plan; selected = selected + id; edits[id] = newName.trim()
                if (r.learned && before != null) {
                    learnedNote = "Retenu : les fichiers « ${NameParser.parse(before.file.name, before.file.folder).title.ifBlank { before.titleKey }} » seront désormais nommés comme vous l'avez écrit. Effaçable dans les réglages."
                    reapplyLearned(before.titleKey)
                } else learnedNote = null
                null
            }
            is Plan.Edit.Refused -> r.reason
        }
    }

    /** The files of the same title as a corrected one get the learned title in the plan (their ticks stay as they were). */
    private fun reapplyLearned(titleKey: String) {
        val a = analysis ?: return
        runCatching {
            val fresh = LibraryAgent(AgentStore.agentContext(folders = a.snapshot.origin == Origin.PHONE), AgentStore.learned).analyze(a.snapshot).plan.renames
                .filter { it.titleKey == titleKey && it.id !in edits }.associateBy { it.id }
            if (fresh.isNotEmpty()) plan = plan.copy(changes = plan.changes.map { c -> fresh[c.id]?.copy(checked = c.checked) ?: c })
        }
    }

    /** "Ne plus toucher à ce fichier" : remembered, effaçable dans les réglages. */
    fun ignore(c: Change) {
        AgentStore.learned.ignore(c.titleKey.ifBlank { c.file.name.lowercase() })
        plan = plan.copy(changes = plan.changes.filter { it.id != c.id }); selected = selected - c.id
    }

    val selectedTrash: List<Change> get() = plan.trash.filter { it.id in selected }
    /** The ticked changes with the subtitles that follow their video: what will really be done. */
    val effective: Set<String> get() = plan.withPairs(selected)

    // ------------------------------------------------------------------ doing it

    private fun opsFor(origin: Origin): LibraryOps = if (origin == Origin.TV) tvOps() else saf().Ops()

    /** Step 2 → 3: nothing changes yet, the recap says exactly what will. */
    fun toRecap() { skipRefine = true; refining = false; generation++; step = Step.RECAP }

    fun apply(confirmDeletions: Boolean) {
        cancel = false; result = null; undone = null; message = null
        val origin = analysis?.snapshot?.origin ?: source
        val sel = effective
        step = Step.RUNNING; running = ExecProgress(0, sel.size, "")
        job = scope.launch {
            try {
                val ex = Executor(opsFor(origin), AgentStore.journal, AgentStore.agentContext(origin == Origin.PHONE))
                ex.recover()
                val runId = "${origin.name.lowercase()}-" + java.lang.Long.toString(System.currentTimeMillis(), 36)
                lastRun = runId
                result = ex.run(plan, sel, confirmDeletions, runId, { p -> running = p }, { cancel })
            } catch (e: Exception) { message = describe(e) }
            step = Step.DONE
        }
    }

    fun undo(runId: String? = lastRun) {
        message = null
        job = scope.launch {
            try {
                val id = runId ?: AgentStore.journal.entries().filter { it.state == State.DONE }.maxByOrNull { it.seq }?.runId
                val origin = if (id?.startsWith("phone") == true) Origin.PHONE else Origin.TV
                if (origin == Origin.PHONE && phoneTree == null) { message = "Choisissez d'abord le dossier du téléphone (Ranger ma bibliothèque → Un dossier du téléphone) : l'annulation en a besoin."; return@launch }
                undone = Executor(opsFor(origin), AgentStore.journal, AgentStore.agentContext(origin == Origin.PHONE)).undo(id)
                // the folders the rangement created are empty now: remove them (only empty ones, never anything else)
                if (origin == Origin.PHONE) runCatching { saf().Ops().pruneEmpty(AgentStore.journal.entries().filter { it.runId == id && it.op == Op.MOVE_FOLDER }.mapNotNull { it.to?.folder }) }
            } catch (e: Exception) { message = describe(e) }
            refreshHistory()
        }
    }

    // ------------------------------------------------------------------ history

    data class RunSummary(val runId: String, val at: Long, val renamed: Int, val moved: Int, val trashed: Int, val undone: Int, val undoable: Int)

    var history by mutableStateOf<List<RunSummary>>(emptyList())

    fun refreshHistory() {
        val all = AgentStore.journal.entries()
        history = all.groupBy { it.runId }.map { (id, es) ->
            val done = es.filter { it.state == State.DONE }
            RunSummary(id, es.maxOf { it.at }, done.count { it.op == Op.RENAME || it.op == Op.MOVE_FOLDER }, done.count { it.op == Op.MOVE_VOLUME }, done.count { it.op == Op.TRASH },
                es.count { it.state == State.UNDONE }, done.size)
        }.filter { it.undoable + it.undone > 0 }.sortedByDescending { it.at }
    }

    // ------------------------------------------------------------------ the bin

    fun loadBin() {
        binBusy = true
        scope.launch {
            try {
                bin = if (source == Origin.TV) {
                    @Suppress("UNCHECKED_CAST")
                    (JsonLite.obj(client!!.raw("GET", "/api/trash"))["items"] as? List<Map<String, Any?>>).orEmpty().map { BinItem(it.str("id").orEmpty(), it.str("name").orEmpty(), it.long("size") ?: 0, it.long("expiresAt") ?: 0) }
                } else saf().Ops().binItems().map { BinItem(it.first, it.first, it.second) }
            } catch (e: Exception) {
                bin = emptyList()
                message = if (e is TvClient.HttpError && e.code == 404) "Cette TV ne gère pas encore la corbeille : mettez à jour CastBridge TV." else describe(e)
            }
            binBusy = false
        }
    }

    /** Where a file of the phone's bin came from, from the journal (so that "Restaurer" puts it back in its own folder, not in the root). */
    private fun originalPlace(binName: String): Loc? =
        AgentStore.journal.entries().filter { it.op == Op.TRASH && it.state == State.DONE && it.trashId == binName && it.runId.startsWith("phone") }.maxByOrNull { it.seq }?.from

    fun restore(item: BinItem) {
        binBusy = true
        scope.launch {
            val r = try {
                if (source == Origin.TV) tvOps().restore(item.id, Loc("", "", item.name))
                else saf().Ops().restore(item.name, originalPlace(item.name) ?: Loc("phone", "", item.name))
            } catch (e: Exception) { OpResult.Fail(describe(e)) }
            message = if (r is OpResult.Fail) "Restauration impossible : ${r.reason}" else "« ${item.name} » est de retour dans la bibliothèque."
            binBusy = false
            loadBin()
        }
    }

    /** Really deletes from the bin. Only called after the user confirmed in a dialog that says so. */
    fun purge(item: BinItem?) {
        binBusy = true
        scope.launch {
            try {
                if (source == Origin.TV) client!!.raw("POST", if (item == null) "/api/trash/empty" else "/api/trash/purge?id=${TvClient.enc(item.id)}")
                else saf().Ops().let { o -> if (item == null) bin.forEach { o.purge(it.name) } else o.purge(item.name) }
            } catch (e: Exception) { message = describe(e) }
            binBusy = false
            loadBin()
        }
    }
}
