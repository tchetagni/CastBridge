package castbridge.sender.agent

import android.annotation.SuppressLint
import android.content.Context
import castbridge.core.library.agent.AgentContext
import castbridge.core.library.agent.AgentSettings
import castbridge.core.library.agent.ContentGuard
import castbridge.core.library.agent.FileAnalysisCache
import castbridge.core.library.agent.FileJournal
import castbridge.core.library.agent.LearnedRules
import castbridge.core.library.agent.NamingModel
import castbridge.core.library.agent.ServerNamingModel
import castbridge.sender.PhoneConnect
import castbridge.sender.PrefsStore
import java.io.File

/**
 * What the library assistant keeps on the phone, in private storage only: its settings, what it learned from the user's
 * corrections, and the journal of what it did (for "Annuler"). Nothing here is sent anywhere.
 */
@SuppressLint("StaticFieldLeak")
object AgentStore {
    private var app: Context? = null
    private val kv by lazy { PrefsStore(app!!, "castbridge_agent") }

    fun init(ctx: Context) { app = ctx.applicationContext }
    private fun ctx(): Context = app ?: error("AgentStore.init(context) first")

    val settings: AgentSettings by lazy { AgentSettings(kv) }
    val learned: LearnedRules by lazy { LearnedRules(kv) }
    val journal: FileJournal by lazy { FileJournal(File(ctx().filesDir, "agent/journal.jsonl")).also { runCatching { it.compact() } } }

    /** Durations of the phone's videos and fingerprints of TV files, kept between two analyses (the second one only works on what is new). */
    val cache: FileAnalysisCache by lazy { FileAnalysisCache(File(ctx().filesDir, "agent/analysis-cache.txt")) }

    /**
     * Parental control hook. The assistant never renames, moves, trashes or sends content this guard protects, and only advises
     * while a child profile is active. Replace it from the parental-control code when it is merged (it is a plain interface).
     */
    @Volatile var guard: ContentGuard = ContentGuard.NONE

    fun agentContext(folders: Boolean): AgentContext = AgentContext(
        uiLang = if (java.util.Locale.getDefault().language == "en") "en" else "fr", guard = guard, aiAllowed = settings.aiEnabled, folders = folders)

    /** The model on the CastBridge server, only when the user gave the separate consent. */
    fun serverModel(): NamingModel? =
        if (settings.aiEnabled) ServerNamingModel(PhoneConnect.state.baseUrl, { PhoneConnect.state.deviceToken }) else null
}
