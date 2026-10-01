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
import kotlinx.coroutines.launch

/** The screens of the assistant, in the order a user goes through them. */
enum class Step { INTRO, ANALYZING, PLAN, RUNNING, DONE, TRASH, HISTORY, SETTINGS }

/** An item of the "Corbeille CastBridge" (TV or phone). */
data class BinItem(val id: String, val name: String, val size: Long, val expiresAt: Long = 0)

/**
 * The assistant's state and actions. The agent PROPOSES ([analyze]), the user VALIDATES (ticks, edits, confirms), only then
 * [apply] changes anything; [undo] puts names and places back. All heavy work runs off the main thread.
 */
class AssistantModel(private val ctx: Context, private val client: TvClient?, private val scope: CoroutineScope) {
    var step by mutableStateOf(Step.INTRO)
    var source by mutableStateOf(Origin.TV)
    var phoneTree by mutableStateOf<Uri?>(AgentStore.settings.phoneTreeUri?.let(Uri::parse))
    var progress by mutableStateOf(Progress(Phase.READ))
    var analysis by mutableStateOf<Analysis?>(null)
    var plan by mutableStateOf(Plan(emptyList()))
    var selected by mutableStateOf<Set<String>>(emptySet())
    var message by mutableStateOf<String?>(null)
    var running by mutableStateOf(ExecProgress(0, 0, ""))
    var result by mutableStateOf<RunResult?>(null)
    var undone by mutableStateOf<UndoResult?>(null)
    var bin by mutableStateOf<List<BinItem>>(emptyList())
    var binBusy by mutableStateOf(false)
    var sentPreview by mutableStateOf<String?>(null)

    @Volatile private var cancel = false
    private var job: Job? = null
    private var lastRun: String? = null

    private fun tvOps() = TvLibraryOps(client ?: error("TV"))
    private fun saf(): SafLibrary = SafLibrary(ctx, phoneTree ?: error("dossier"))

    fun pickedTree(uri: Uri) { phoneTree = uri; AgentStore.settings.phoneTreeUri = uri.toString(); source = Origin.PHONE }

    // ------------------------------------------------------------------ analysis

    fun analyze() {
        cancel = false; message = null; sentPreview = null
        step = Step.ANALYZING; progress = Progress(Phase.READ, 0, 0, "Lecture de la bibliothèque…")
        job = scope.launch(Dispatchers.IO) {
            try {
                val snap = if (source == Origin.TV) TvSnapshot.read(client ?: error("TV")) else saf().snapshot(progress = { n -> progress = Progress(Phase.READ, n, 0, "$n fichiers lus…") }, cancelled = { cancel })
                if (cancel) { step = Step.INTRO; return@launch }
                val recorder = AgentStore.serverModel()?.let { Recording(it) }
                val agent = LibraryAgent(AgentStore.agentContext(folders = source == Origin.PHONE), AgentStore.learned, recorder,
                    fingerprinter = if (source == Origin.TV) TvFingerprinter(client!!) else null)
                val a = agent.analyze(snap, { p -> progress = p }, { cancel })
                if (cancel) { step = Step.INTRO; return@launch }
                analysis = a; plan = a.plan; selected = a.plan.defaultSelection()
                sentPreview = recorder?.sent?.takeIf { it.isNotBlank() }
                step = Step.PLAN
            } catch (e: Exception) {
                message = describe(e); step = Step.INTRO
            }
        }
    }

    fun cancelWork() { cancel = true }

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

    fun toggle(id: String, on: Boolean) { selected = if (on) selected + id else selected - id }
    fun selectAllSafe() { selected = plan.allSafe() }
    fun selectNone() { selected = emptySet() }
    fun selectDefault() { selected = plan.defaultSelection() }

    /** The user typed another name: validated, applied to the plan, and learned. Returns an error message or null. */
    fun edit(id: String, newName: String): String? = when (val r = plan.withEditedName(id, newName, AgentStore.learned)) {
        is Plan.Edit.Ok -> { plan = r.plan; selected = selected + id; null }
        is Plan.Edit.Refused -> r.reason
    }

    /** "Ne plus toucher à ce fichier" : remembered, effaçable dans les réglages. */
    fun ignore(c: Change) {
        AgentStore.learned.ignore(c.titleKey.ifBlank { c.file.name.lowercase() })
        plan = plan.copy(changes = plan.changes.filter { it.id != c.id }); selected = selected - c.id
    }

    val selectedTrash: List<Change> get() = plan.trash.filter { it.id in selected }

    // ------------------------------------------------------------------ doing it

    private fun opsFor(origin: Origin): LibraryOps = if (origin == Origin.TV) tvOps() else saf().Ops()

    fun apply(confirmDeletions: Boolean) {
        cancel = false; result = null; undone = null; message = null
        val origin = analysis?.snapshot?.origin ?: source
        val sel = selected
        step = Step.RUNNING; running = ExecProgress(0, sel.size, "")
        job = scope.launch(Dispatchers.IO) {
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
        job = scope.launch(Dispatchers.IO) {
            try {
                val id = runId ?: AgentStore.journal.entries().filter { it.state == State.DONE }.maxByOrNull { it.seq }?.runId
                val origin = if (id?.startsWith("phone") == true) Origin.PHONE else Origin.TV
                undone = Executor(opsFor(origin), AgentStore.journal, AgentStore.agentContext(origin == Origin.PHONE)).undo(id)
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
        scope.launch(Dispatchers.IO) {
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

    fun restore(item: BinItem) {
        binBusy = true
        scope.launch(Dispatchers.IO) {
            val r = if (source == Origin.TV) tvOps().restore(item.id, Loc("", "", item.name)) else saf().Ops().restore(item.name, Loc("phone", "", item.name))
            message = if (r is OpResult.Fail) "Restauration impossible : ${r.reason}" else "« ${item.name} » est de retour dans la bibliothèque."
            binBusy = false
            loadBin()
        }
    }

    /** Really deletes from the bin. Only called after the user confirmed in a dialog that says so. */
    fun purge(item: BinItem?) {
        binBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                if (source == Origin.TV) client!!.raw("POST", if (item == null) "/api/trash/empty" else "/api/trash/purge?id=${TvClient.enc(item.id)}")
                else saf().Ops().let { o -> if (item == null) bin.forEach { o.purge(it.name) } else o.purge(item.name) }
            } catch (e: Exception) { message = describe(e) }
            binBusy = false
            loadBin()
        }
    }
}
