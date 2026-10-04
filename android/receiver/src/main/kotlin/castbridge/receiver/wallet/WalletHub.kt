package castbridge.receiver.wallet

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import castbridge.core.connect.Routes
import castbridge.core.crypto.PlainWrapper
import castbridge.core.lots.Right
import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.FileInstallSignerStore
import castbridge.core.owner.InstallSigner
import castbridge.core.owner.KeyRing
import castbridge.core.owner.SafeFile
import castbridge.core.owner.TrustedKeyParser
import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.VoucherKeys
import castbridge.core.wallet.WalletCache
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletStore
import castbridge.core.wallet.ui.ConvertDir
import castbridge.core.wallet.ui.ConvertDone
import castbridge.core.wallet.ui.HistoryPage
import castbridge.core.wallet.ui.PolicyView
import castbridge.core.wallet.ui.ReceiveCodeView
import castbridge.core.wallet.ui.ServerWallet
import castbridge.core.wallet.ui.SyncData
import castbridge.core.wallet.ui.TransferDone
import castbridge.core.wallet.ui.WalletActivations
import castbridge.core.wallet.ui.WalletClient
import castbridge.core.wallet.ui.WalletGate
import castbridge.core.wallet.ui.WalletIdentity
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletReplies
import castbridge.core.wallet.ui.WalletResult
import castbridge.core.wallet.ui.WalletStatus
import castbridge.core.wallet.ui.WalletSyncSchedule
import castbridge.core.wallet.ui.WalletSyncSchedule.Trigger
import java.time.ZoneId
import castbridge.core.wallet.ui.WalletTransport
import castbridge.receiver.ActivationCenter
import castbridge.receiver.BuildConfig
import castbridge.receiver.TvConnect
import castbridge.receiver.TvPrefs
import castbridge.receiver.TvService
import java.io.File
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le portefeuille de CETTE TV côté application (cahier w22-07a) : relie le client HTTPS ([WalletClient]), le cache SIGNÉ en lecture seule ([WalletCache]) et les écrans.
 * Aucun solde n'est calculé ni gardé ici : l'écran lit [snapshot], c'est-à-dire le dernier `cbw1` vérifié. Tout réseau passe par un fil unique ; les rappels reviennent sur le fil principal.
 */
object WalletHub {
    private const val TAG = "WalletHub"
    private lateinit var app: Context
    @Volatile private var ready = false
    private val schedule = WalletSyncSchedule()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-wallet").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val inFlight = AtomicBoolean(false)
    private var cache: WalletCache? = null
    private var client: WalletClient? = null
    @Volatile private var signer: InstallSigner? = null
    private var timer: Timer? = null

    @Volatile var server: ServerWallet = ServerWallet.UNKNOWN; private set
    @Volatile var policy: PolicyView? = null; private set
    @Volatile var lastSync: SyncData? = null; private set
    @Volatile var lastSyncAt: Long? = null; private set
    @Volatile var lastFail: WalletMessages.Shown? = null; private set
    /** Le dernier appel a échoué faute de réseau (une réponse du serveur, même un refus, remet à faux). */
    @Volatile private var lastCallOffline = false

    private fun prefs() = app.getSharedPreferences("castbridge_wallet", Context.MODE_PRIVATE)
    private fun dir() = File(app.filesDir, "wallet").also { it.mkdirs() }

    @Synchronized fun init(ctx: Context) {
        if (ready) return
        app = ctx.applicationContext
        runCatching { ActivationCenter.init(app) }
        val identity = castbridge.core.owner.DeviceCode.parse(runCatching { ActivationCenter.deviceCode }.getOrDefault("")) ?: return
        val ring = KeyRing(TrustedKeyParser.parse(BuildConfig.WALLET_KEYS.replace("\\n", "\n")).keys)
        val store = object : WalletStore {
            private val f = File(dir(), "cache.txt")
            override fun read(): String? = SafeFile.read(f)?.text
            override fun write(text: String) = SafeFile.write(f, text)
        }
        val c = WalletCache(store, ring, VoucherKeys(KeyRing(emptyList()), emptyMap()), identity)
        cache = c
        server = runCatching { ServerWallet.valueOf(prefs().getString("server", null) ?: "UNKNOWN") }.getOrDefault(ServerWallet.UNKNOWN)
        lastSyncAt = prefs().getLong("last_sync", 0L).takeIf { it > 0 }
        SafeFile.read(File(dir(), "last-sync.json"))?.text?.let { lastSync = WalletReplies.parseSync(it) }
        client = WalletClient(RoutesTransport(), ::identity, c, System::currentTimeMillis)
        ready = true
        timer = Timer("cb-wallet-tick", true).also { t -> t.schedule(object : TimerTask() { override fun run() { if (flag() && activated()) refresh(Trigger.TICK) } }, 60_000L, 60_000L) }
    }

    // ---- état lu par les écrans ----

    fun flag(): Boolean = ready && TvPrefs(app).getBool("wallet.enabled", true)
    fun setFlag(on: Boolean) { if (ready) { TvPrefs(app).putBool("wallet.enabled", on); notifyListeners() } }
    fun activated(): Boolean = ready && WalletGate.activated(ActivationCenter.allActivations().size, ActivationCenter.locked())
    fun snapshot(): Snapshot? = cache?.snapshot
    fun cardVisible(): Boolean = ready && WalletGate.cardVisible(activated(), flag(), server, snapshot() != null)
    fun screenOpenable(): Boolean = ready && WalletGate.screenOpenable(activated(), flag())
    /** L'état à dire : jamais synchronisé (aucun chiffre), synchronisé, hors ligne avec instantané, trop ancien (> 35 jours), licence en attente. */
    fun statusView(): WalletStatus.View =
        WalletStatus.of(snapshot(), online(), System.currentTimeMillis(), lastFail?.code, lastSync?.edition?.license, ZoneId.systemDefault())
    fun cardStatus(): String = WalletStatus.cardLine(statusView(), snapshot())
    fun online(): Boolean = networkUp() && !lastCallOffline

    /** Même règle que la tuile « Internet » de l'accueil. */
    fun networkUp(): Boolean = TvService.running?.let { it.netDirectMs != null || it.netGatewayMs != null || it.netCheckedAt == 0L } != false

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyListeners() { main.post { listeners.forEach { runCatching { it() } } } }

    fun cachedHistory(): HistoryPage? =
        SafeFile.read(File(dir(), "history.json"))?.text?.let { WalletReplies.parseHistory(it) } ?: lastSync?.history?.takeIf { it.isNotEmpty() }?.let { HistoryPage(it, null) }

    // ---- synchronisation ----

    /** Synchronise si la règle du calendrier le veut ([Trigger.OPEN] : à l'ouverture ; [Trigger.AFTER_OPERATION] : après chaque opération ; [Trigger.TICK] : toutes les 15 min). */
    fun refresh(trigger: Trigger, done: (() -> Unit)? = null) {
        if (!ready || !flag() || !activated()) return
        io.execute {
            if (!schedule.shouldSync(trigger, networkUp(), System.currentTimeMillis(), lastSyncAt, inFlight.get())) return@execute
            if (!inFlight.compareAndSet(false, true)) return@execute
            try {
                val r = client!!.sync()
                apply(r)
                if (r is WalletResult.Ok) {
                    lastSync = r.value; lastSyncAt = System.currentTimeMillis()
                    prefs().edit().putLong("last_sync", lastSyncAt!!).apply()
                    runCatching { SafeFile.write(File(dir(), "last-sync.json"), syncJson(r.value)) }
                }
            } finally { inFlight.set(false) }
            notifyListeners()
            if (done != null) main.post { done() }
        }
    }

    private fun syncJson(s: SyncData): String = JsonLite.write(linkedMapOf(
        "snapshot" to s.snapshotToken,
        "history" to s.history.map { linkedMapOf("id" to it.id, "kind" to it.kind, "currency" to it.currency, "amount" to it.amount, "at" to it.at, "label" to it.label, "counterparty" to it.counterparty) },
        "notices" to s.notices.map { linkedMapOf("reason" to it.reason, "text" to it.text) },
        "edition" to s.edition?.let { linkedMapOf("ed" to it.ed, "license" to it.license, "grace" to it.grace, "boundOther" to it.boundOther) },
    ))

    /** Met à jour l'état du service et la connexion d'après une réponse (ou son absence). */
    private fun apply(r: WalletResult<*>) {
        when (r) {
            is WalletResult.Ok -> { lastCallOffline = false; lastFail = null; setServer(WalletGate.serverAfter(server, 200, null)) }
            is WalletResult.Fail -> { lastCallOffline = r.network; lastFail = r.shown; setServer(WalletGate.serverAfter(server, if (r.network) null else r.status, r.reason)) }
        }
    }

    private fun setServer(s: ServerWallet) { if (s != server) { server = s; prefs().edit().putString("server", s.name).apply() } }

    // ---- opérations (fil réseau, rappel sur le fil principal) ----

    private fun <T> run(op: () -> WalletResult<T>, done: (WalletResult<T>) -> Unit) {
        io.execute {
            val r = try { op() } catch (e: Exception) { Log.w(TAG, "opération en échec (${e.javaClass.simpleName})"); WalletResult.Fail(WalletMessages.of(500, null), null, null, false) }
            apply(r)
            notifyListeners()
            main.post { done(r) }
        }
    }

    fun loadPolicy(done: (WalletResult<PolicyView>) -> Unit) = run({ client!!.policy().also { if (it is WalletResult.Ok) policy = it.value } }, done)
    fun convert(dir: ConvertDir, q: Long, idem: String, done: (WalletResult<ConvertDone>) -> Unit) =
        run({ client!!.convert(dir, q, idem) }) { r -> done(r); if (r is WalletResult.Ok) refresh(Trigger.AFTER_OPERATION) }
    fun transfer(cur: WalletCurrency, code: String, amt: Long, idem: String, done: (WalletResult<TransferDone>) -> Unit) =
        run({ client!!.transfer(cur, code, amt, idem) }) { r -> done(r); if (r is WalletResult.Ok) refresh(Trigger.AFTER_OPERATION) }
    fun receiveCode(done: (WalletResult<ReceiveCodeView>) -> Unit) = run({ client!!.receiveCode() }, done)
    fun history(before: Long?, done: (WalletResult<HistoryPage>) -> Unit) = run({
        client!!.history(before).also { r -> if (r is WalletResult.Ok && before == null) runCatching { SafeFile.write(File(dir(), "history.json"), historyJson(r.value)) } }
    }, done)

    private fun historyJson(p: HistoryPage) = JsonLite.write(linkedMapOf(
        "lines" to p.lines.map { linkedMapOf("id" to it.id, "kind" to it.kind, "currency" to it.currency, "amount" to it.amount, "at" to it.at, "label" to it.label, "counterparty" to it.counterparty) },
        "next" to p.next,
    ))

    // ---- identité de cette TV pour le serveur ----

    private fun identity(): WalletIdentity? {
        val code = runCatching { ActivationCenter.deviceCode }.getOrNull() ?: return null
        val acts = ActivationCenter.allActivations().map { a ->
            val prio = when { a.rights.any { it is Right.Super } -> 0; a.kind == ActivationKind.PRODUCTION -> 1; a.kind == ActivationKind.TRIAL -> 2; else -> 3 }
            WalletActivations.Candidate(a.encode(), prio, a.issuedAt)
        }
        return WalletIdentity(code, TvConnect.link?.state?.deviceId, WalletActivations.pick(acts), installSigner())
    }

    /**
     * La clé de signature d'installation (Ed25519, créée une fois, gardée dans le dossier privé de l'application) : elle prouve que cette TV détient bien ce que le serveur a lié.
     * Choix assumé : fichier privé (PlainWrapper) et non le coffre Android : un changement de coffre ferait changer d'identité et le serveur refuserait la TV (BIND_PROOF) jusqu'à une
     * réaffectation par l'administrateur ; la preuve vise la copie d'une clé d'activation, pas un accès root à la TV.
     */
    @Synchronized fun installSigner(): InstallSigner? {
        signer?.let { return it }
        return runCatching { InstallSigner.loadOrCreate(FileInstallSignerStore(File(dir(), "install-signer.b64")), PlainWrapper()).signer }
            .onFailure { Log.w(TAG, "clé d'installation indisponible (${it.javaClass.simpleName})") }.getOrNull()?.also { signer = it }
    }

    /** HTTPS vers l'API par les mêmes chemins que le reste de la TV (réseau direct, puis passerelle Bluetooth du téléphone), avec le jeton d'appareil existant. */
    private class RoutesTransport : WalletTransport {
        override fun send(method: String, path: String, body: String?): HttpLite.Response {
            val link = TvConnect.link ?: throw IOException("liaison avec le serveur pas prête")
            val state = link.state
            val token = state.deviceToken ?: throw IOException("TV pas encore enregistrée auprès du serveur")
            val base = state.baseUrl.trimEnd('/')
            return link.routes.call<HttpLite.Response>(networkFailure = { it.code in 502..504 }) { proxy ->
                HttpLite(proxy, userAgent = "CastBridge-TV-wallet").request(method, base + path, body, mapOf("Authorization" to "Bearer $token"))
            }
        }
    }
}
