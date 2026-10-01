package castbridge.sender

import android.content.Context
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteTransport
import castbridge.core.remote.smart.Attempt
import castbridge.core.remote.smart.Orchestrator
import castbridge.core.remote.smart.SendResult
import castbridge.core.remote.smart.StrategyCatalog
import castbridge.core.remote.smart.StrategyEnv
import castbridge.core.remote.smart.StrategyIds
import castbridge.core.remote.smart.StrategyStatus
import castbridge.core.remote.smart.TvFingerprint
import castbridge.core.remote.smart.TvIdentifier
import castbridge.core.remote.smart.TvProbe
import castbridge.core.remote.smart.TvTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/** One line of « Ma TV » for a strategy. */
data class StrategyRow(val id: String, val label: String, val status: StrategyStatus, val limits: String, val inPlan: Boolean)

/** What the « Ma TV » screen shows. */
data class SmartUi(
    val host: String? = null,
    val tvName: String? = null,
    val fingerprint: TvFingerprint? = null,
    val activeId: String? = null,
    val activeLabel: String? = null,
    val attempts: List<Attempt> = emptyList(),
    val strategies: List<StrategyRow> = emptyList(),
    val limits: String = "",
    val warnings: List<String> = emptyList(),
    val busy: String? = null,
    val message: String? = null,
    val pairing: String? = null,
    val askVolume: Boolean = false,
    val experimental: Boolean = false,
    val forced: String? = null,
)

/**
 * The phone's « smart remote » (docs/REMOTE.md): for the TV the user chose, identify it passively, then try the remote strategies
 * in order (Orchestrator). CastBridge-TV stays on the existing RemoteController path; every other strategy goes through here.
 * Nothing is sent before the user chose the TV; everything blocking runs on one background thread.
 */
object SmartRemote {
    private val work = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-smart").apply { isDaemon = true } }
    private val sends = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-smart-send").apply { isDaemon = true } }
    private val _ui = MutableStateFlow(SmartUi())
    val ui: StateFlow<SmartUi> = _ui
    private val _available = MutableStateFlow<Set<RemoteKey>?>(null)
    /** Keys the active non-CastBridge strategy can send; null = no restriction (CastBridge-TV, or nothing chosen). */
    val available: StateFlow<Set<RemoteKey>?> = _available

    @Volatile private var orch: Orchestrator? = null
    @Volatile private var ctxApp: Context? = null
    @Volatile private var nativeOpen: (() -> RemoteTransport)? = null
    @Volatile private var btAddress: String? = null

    /** True when keys must go through the orchestrator instead of the CastBridge session. */
    fun handles(): Boolean = orch?.active?.let { it.id != StrategyIds.CASTBRIDGE } == true

    /** Chooses [host] (typed or picked by the user), identifies the TV and connects. [native]: how to open the CastBridge link if that TV is one. */
    fun attach(ctx: Context, host: String, name: String?, btAddress: String? = null, native: (() -> RemoteTransport)? = null) {
        val app = ctx.applicationContext; ctxApp = app; nativeOpen = native; this.btAddress = btAddress
        val prefs = SmartPrefs(app); prefs.tvHost = host; prefs.tvName = name
        work.execute {
            runCatching {
                orch?.close(); orch = null; _available.value = null
                _ui.value = SmartUi(host = host, tvName = name, busy = "Identification de la TV…", experimental = prefs.experimental)
                val fp = TvIdentifier.identify(TvProbe.gather(host, mdns = MdnsHints(app).collect(host)))
                val tv = TvTarget(host, host, name ?: host)
                val diagLog = castbridge.core.remote.smart.DiagLog()
                val env = StrategyEnv(prefs, { diagLog.add(it) }, native, AndroidHidPort(app, btAddress), AndroidIrEmitter(app), AndroidAppLauncher(app))
                val o = Orchestrator(tv.id, fp, StrategyCatalog.build(tv, fp, env), prefs, diag = diagLog)
                o.allowExperimental = prefs.experimental; o.forced = prefs.forced(tv.id)
                orch = o
                _ui.value = _ui.value.copy(fingerprint = fp, busy = "Recherche d'une stratégie qui fonctionne…")
                o.connect(); publish("Prêt.".takeIf { o.active != null } ?: "Aucune stratégie n'a répondu : voir le diagnostic.")
            }.onFailure { _ui.value = _ui.value.copy(busy = null, message = "Erreur : ${it.message}") }
        }
    }

    /** Re-attaches to the TV chosen last time (the remote screen calls this when it opens). */
    fun resume(ctx: Context) {
        if (orch != null) return
        val p = SmartPrefs(ctx); val h = p.tvHost ?: return
        attach(ctx, h, p.tvName)
    }

    private fun publish(message: String? = null) {
        val o = orch ?: return
        val a = o.active; val plan = o.plan().map { it.id }.toSet()
        _available.value = a?.takeIf { it.id != StrategyIds.CASTBRIDGE }?.capabilities?.keys
        val all = o.plan()
        _ui.value = _ui.value.copy(
            activeId = a?.id, activeLabel = a?.label, attempts = o.attempts(), limits = a?.limits.orEmpty(), warnings = o.warnings(), busy = null,
            message = message, pairing = o.attempts().lastOrNull { it.outcome == castbridge.core.remote.smart.AttemptOutcome.NEEDS_PAIRING && a?.id != it.strategyId }?.strategyId,
            experimental = o.allowExperimental, forced = o.forced,
            strategies = StrategyIds.DEFAULT_ORDER.mapNotNull { id -> o.strategy(id)?.let { s -> StrategyRow(id, s.label, s.status, s.limits, id in plan) } },
        )
        if (all.isEmpty()) _ui.value = _ui.value.copy(message = "Aucune stratégie applicable : activez les essais expérimentaux ou choisissez-en une.")
    }

    // ---- what the remote screen calls (never blocks the UI thread) ----
    fun send(k: RemoteKey) { val o = orch ?: return; sends.execute { if (o.send(k) is SendResult.NeedsPairing) publish() } }
    fun sendText(t: String) { val o = orch ?: return; sends.execute { o.sendText(t) } }

    // ---- « Ma TV » actions ----
    fun retry() = run("Nouvelle recherche…") { it.connect() }
    fun setExperimental(on: Boolean) { val ctx = ctxApp ?: return; SmartPrefs(ctx).experimental = on; run("…") { it.allowExperimental = on; it.connect() } }
    fun choose(id: String?) { val ctx = ctxApp ?: return; val o = orch ?: return; SmartPrefs(ctx).setForced(o.tvId, id); run("Connexion…") { it.choose(id) } }
    fun pair(id: String, code: String) = run("Appairage…") { it.pair(id, code) }

    /** The reversible test: volume +, then volume −. The screen then asks « Avez-vous vu le volume changer ? » ([confirm]). */
    fun test() = run("Test : volume + puis −…") { o ->
        if (o.test()) _ui.value = _ui.value.copy(askVolume = true) else _ui.value = _ui.value.copy(message = "Le test n'a pas pu être envoyé (pas de touche volume avec cette stratégie, ou TV injoignable).")
    }
    fun confirm(saw: Boolean) = run("…") { it.confirmTest(saw); _ui.value = _ui.value.copy(askVolume = false, message = if (saw) "Enregistré : cette stratégie sera essayée en premier." else "Noté : cette stratégie est écartée, essai de la suivante.") ; if (!saw) it.connect() }

    fun openVendorApp(): Boolean = (orch?.strategy(StrategyIds.VENDOR_APP) as? castbridge.core.remote.smart.VendorAppStrategy)?.open() == true
    fun diagnostic(): String = orch?.diagnostic() ?: "Aucune TV choisie."

    private fun run(busy: String, block: (Orchestrator) -> Unit) {
        val o = orch ?: return
        _ui.value = _ui.value.copy(busy = busy)
        work.execute { runCatching { block(o) }.onFailure { _ui.value = _ui.value.copy(message = "Erreur : ${it.message}") }; publish(_ui.value.message?.takeIf { it != "…" }) }
    }
}
