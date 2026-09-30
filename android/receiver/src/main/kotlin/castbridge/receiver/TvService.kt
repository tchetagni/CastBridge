package castbridge.receiver

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.then
import castbridge.core.tv.ApiReply
import castbridge.core.tv.Device
import castbridge.core.tv.LaunchPolicy
import castbridge.core.tv.LibraryProvider
import castbridge.core.tv.NeedsForeground
import castbridge.core.tv.PinGuard
import castbridge.core.tv.Player
import castbridge.core.tv.PlayerCommand
import castbridge.core.tv.PlayerState
import castbridge.core.tv.PlayerTracks
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.Storage
import castbridge.core.tv.SysInfo
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.VolumeRegistry
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * CastBridge TV in the background: everything but the display lives here, in a foreground service that starts with the TV
 * (BootReceiver) and survives the screen being closed: HTTP API and web page, uploads and downloads, library API, storage
 * volumes and hot plug, Bluetooth (files and SSH tunnel), Wi-Fi Direct, SSH, APK installs, mDNS. [PlayerActivity] is only the
 * screen (waiting screen, library, playback): it binds to this service and registers itself as the player.
 *
 * Foreground service type: connectedDevice (the service serves a phone over the network / Bluetooth). Not dataSync: Android 15
 * forbids starting dataSync from BOOT_COMPLETED and limits it to 6 h a day; connectedDevice has neither restriction and its
 * prerequisite (CHANGE_WIFI_MULTICAST_STATE / CHANGE_WIFI_STATE) is a normal permission this app already holds.
 */
class TvService : Service(), Device {
    /** What the screen offers to the service. Implemented by PlayerActivity. */
    interface Screen : Player {
        val shown: Boolean
        val activity: Activity
        fun notice(msg: String)
        fun statusesChanged()
        fun thumbReady(name: String)
        /** A playback request that arrived while the screen was hidden, to run now. */
        fun runPending(r: Pending)
    }

    /** A play request kept until the screen is up. */
    sealed class Pending(val at: Long = System.currentTimeMillis()) {
        class Local(val file: File, val pos: Long) : Pending()
        class Stream(val url: String, val name: String, val pos: Long) : Pending()
        class Saf(val name: String, val size: Long, val pos: Long) : Pending()
    }

    inner class Local : Binder() { val service: TvService get() = this@TvService }

    val main = Handler(Looper.getMainLooper())
    /** One background thread for everything that touches a USB drive (scan, write test) or small files: never block the UI. */
    val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cb-storage").apply { isDaemon = true } }
    val statuses = ConcurrentHashMap<String, String>()
    lateinit var prefs: TvPrefs; private set
    lateinit var volProvider: AndroidVolumeProvider; private set
    lateinit var registry: VolumeRegistry; private set
    lateinit var videosDir: File; private set
    lateinit var guard: PinGuard; private set
    var pin = ""; private set
    var server: ReceiverServer? = null; private set
    var library: LibraryProvider? = null; private set
    var bt: BtServer? = null; private set
    /** « Téléphones de confiance » (docs/BT-PLUG-AND-PLAY.md): who may use the TV without the PIN, and the pairing window. */
    lateinit var trust: castbridge.core.trust.TrustRegistry; private set
    lateinit var pairing: castbridge.core.trust.PairingSession; private set
    private val lastBanner = ConcurrentHashMap<String, Long>()
    var wd: WifiDirectGroup? = null; private set
    var usb: UsbImporter? = null; private set
    var ssh: SshControl? = null; private set
    var updater: UpdateInstaller? = null; private set
    var gateway: BtGatewayHost? = null; private set
    @Volatile var screen: Screen? = null; private set
    @Volatile private var pending: Pending? = null
    @Volatile private var pendingDone: CountDownLatch? = null
    private var started = false
    private var storageReceiver: BroadcastReceiver? = null
    private var volumeCallback: Any? = null
    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder = Local()

    override fun onCreate() {
        super.onCreate()
        hookCapture()                      // before any screen resumes, so /api/screenshot always knows the front screen
        running = this
        startInForeground()
        startCore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!started) startCore()
        return START_STICKY                                      // killed for memory: Android starts it again
    }

    private fun startInForeground() {
        val n = notification("Prêt à recevoir des vidéos")
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIF, n)
        } catch (e: Exception) {
            // Refused (restricted start): the service keeps running while the screen is bound; logged, never fatal.
            Log.w(TAG, "startForeground: ${e.javaClass.simpleName}")
        }
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "CastBridge TV actif", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH_SERVICE).setContentTitle("CastBridge TV actif").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setOngoing(true).setContentIntent(open).build()
    }

    // ------------------------------------------------------------------ core

    private fun startCore() {
        if (started) return
        started = true
        prefs = TvPrefs(this)
        volProvider = AndroidVolumeProvider(this, prefs)
        registry = VolumeRegistry(volProvider).also { it.refresh() }        // fast scan, no speed test on the main thread
        videosDir = registry.volumes().first().dir                         // internal storage is always first
        pin = prefs.pin()
        guard = PinGuard(pin)
        trust = castbridge.core.trust.TrustRegistry(TrustFile(File(filesDir, "trusted_phones.txt")))
        pairing = castbridge.core.trust.PairingSession(trust)
        val profile = prefs.profile()
        logResources(videosDir, profile)
        // Thumbnails only while nothing plays (one decode at a time on this TV).
        library = Thumbnailer.provider(this, canRun = { playerBridge.state().state in setOf("idle", "ended", "error") },
            onThumb = { n -> screen?.thumbReady(n) })
        startServer()
        register()
        bt = BtServer(this, videosDir, guard, negotiate = ::linkInfo, hello = ::btHello, trusted = ::btTrusted) { setStatus("1-bt", it) }
        wd = WifiDirectGroup(this, prefs) { setStatus("2-wd", it) }
        usb = UsbImporter(this, videosDir) { setStatus("3-usb", it) }
        ssh = SshControl(this, { setStatus("4-ssh", it) }, { setStatus("4-ssh-bt", it) })
        updater = UpdateInstaller(this, { registry.volumes().filter { it.kind != VolumeKind.SAF }.map { it.dir } },
            launch = { i, what -> launchScreen(i, what) }) { m -> setStatus("5-update", m); notice(m) }
        onPermissionsReady()                                    // Bluetooth starts if its permission was granted earlier
        // server link (docs/API-SERVER.md): registration, heartbeat every 15 min, updates, usage events, quiz questions
        TvConnect.start(this)
        bg.execute { cleanUpdateFiles() }
        registerStorageEvents()
        rescanAsync(remeasure = true)
        main.postDelayed(transferTick, 5000)
        main.postDelayed(netTick, 3000)
    }

    /** Starts the HTTP server once (only this service does: no second server fighting for port 8765); retries if the port is taken. */
    private fun startServer(attempt: Int = 0) {
        val s = ReceiverServer(registry, playerBridge, pin = pin, guard = guard, device = this,
            // downloads (aria2, docs/DOWNLOADS.md): its own manager, independent of any screen
            extension = ApiExtension(::extraApi).then(RemoteHub.api.also { RemoteHub.install(this) }).then(TvDownloads.start(this, registry) { server?.target ?: "auto" }.manager.apiExtension)
                .then(LearnHub.also { it.attach(this) }.api(this)),   // « Apprendre » (docs/LEARN.md)
            profile = prefs.profile(), onSettings = { prefs.saveProfile(it); updateStorageStatus() },
            onNotice = { n -> notice(n); setStatus("5-notice", n) },
            safPicker = ::launchSafPicker, settingsOpener = ::openStorageSettings, library = library,
            publicRoutes = castbridge.core.tv.CombinedRoutes(QuizHub.http, ChessHub.http),
            tokenAuth = trust::verifyToken)
        try {
            s.start(15_000, false); server = s
        } catch (e: Exception) {
            Log.e(TAG, "server: ${e.javaClass.simpleName} ${e.message}")
            setStatus("9-server", "Serveur indisponible (port ${ReceiverServer.PORT} occupé ?) : nouvel essai…")
            if (attempt < 20) main.postDelayed({ startServer(attempt + 1) }, 3000)
            return
        }
        setStatus("9-server", null)
    }

    /**
     * What a phone gets over Bluetooth when it asks for a faster link (CBTN): the TV's addresses and, if the group exists or
     * may be created now (LinkPlanner.mayStartWifiDirect), its Wi-Fi Direct network. Waits up to 8 s for a new group.
     */
    fun linkInfo(wantWifiDirect: Boolean): castbridge.core.tv.LinkInfo {
        val ips = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
        val g = wd
        val hasLan = ips.any { !it.startsWith("192.168.49.") }
        if (g != null && g.active == null && g.hasPermission() &&
            castbridge.core.tv.LinkPlanner.mayStartWifiDirect(wantWifiDirect, prefs.getBool("wd_enabled", false), hasLan)) {
            main.post { g.start() }
            val until = System.currentTimeMillis() + 8000
            while (g.active == null && System.currentTimeMillis() < until) Thread.sleep(200)
        }
        val a = g?.active
        return castbridge.core.tv.LinkInfo(ReceiverServer.PORT, ips, a?.first, a?.second, a?.let { castbridge.core.tv.WifiDirect.GROUP_OWNER_IP })
    }

    // ---- plug and play: a trusted phone asks "who am I?" over Bluetooth (CBTH) and gets the TV's Wi-Fi address and its own token ----

    /** The TV's name as shown to its owner and to the phone: the Bluetooth name (what Android shows in the pairing dialogs), else the model. */
    @SuppressLint("MissingPermission")
    fun tvName(): String = (if (hasBtPermission(this)) runCatching { getSystemService(BluetoothManager::class.java)?.adapter?.name }.getOrNull() else null)
        ?.takeIf { it.isNotBlank() } ?: "CastBridge TV"

    private val helloHandler by lazy {
        castbridge.core.trust.HelloHandler(trust, pairing, ::btBonded, ::tvName, runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?",
            { "CastBridge TV " + (Build.MODEL ?: "") }, { linkInfo(false) }) { p -> phoneConnected(p) }
    }

    fun btHello(peer: String, peerName: String?, requestTrust: Boolean) = helloHandler.handle(peer, peerName, requestTrust)

    /** A trusted phone: in the registry AND still paired at the Bluetooth level (the owner can also remove it in Android's settings). */
    fun btTrusted(peer: String) = trust.isTrusted(peer) && btBonded(peer)

    @SuppressLint("MissingPermission")
    private fun btBonded(address: String): Boolean = hasBtPermission(this) && runCatching {
        getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
    }.getOrDefault(false)

    /** Banner « <téléphone> connecté », at most once per 10 minutes per phone (the phone renews its token in the background). */
    private fun phoneConnected(p: castbridge.core.trust.TrustedPhone) {
        val t = System.currentTimeMillis()
        val last = lastBanner[p.address] ?: 0
        lastBanner[p.address] = t
        if (t - last > 10 * 60_000L) { notice("${p.name} connecté"); setStatus("1-phone", "Téléphone connecté : ${p.name}") }
    }

    private var captureHooked = false
    private fun hookCapture() { if (!captureHooked) { captureHooked = true; ScreenCapture.install(application) } }

    fun onPermissionsReady() {
        hookCapture()
        bt?.start()
        gateway = gateway ?: BtGatewayHost(this, guard, ::btTrusted) { st -> setStatus("6-gw", st) }
        gateway?.start()
        // Wi-Fi Direct is opt-in (MENU): creating a group can disturb the TV's own Wi-Fi connection.
        if (prefs.getBool("wd_enabled", false) && wd?.hasPermission() == true) wd?.start()
    }

    /** Wake lock (partial) and Wi-Fi lock only while something is being transferred. */
    /** Latest connectivity per path, for the home tile: ms of a 204 check, or null. */
    @Volatile var netDirectMs: Long? = null; private set
    @Volatile var netGatewayMs: Long? = null; private set
    @Volatile var netCheckedAt = 0L; private set
    private val netTick = object : Runnable {
        override fun run() {
            bg.execute {
                val wasDirect = netDirectMs != null; val wasGateway = netGatewayMs != null; val first = netCheckedAt == 0L
                netDirectMs = TvNetDiag.probe(null)
                netGatewayMs = gateway?.proxy()?.let { TvNetDiag.probe(it) }
                netCheckedAt = System.currentTimeMillis()
                setStatus("7-net", netSummary())
                // connectivity_check: at start and when the state changes (not every minute)
                if (first || wasDirect != (netDirectMs != null)) connectivityEvent(null, netDirectMs)
                if (gateway?.connected == true && (first || wasGateway != (netGatewayMs != null))) connectivityEvent("bluetooth", netGatewayMs)
                if (netDirectMs != null && !wasDirect && !first) TvConnect.post { flush() }      // network back: send what waits
                libraryStatsDaily()
            }
            main.postDelayed(this, 60_000)
        }
    }

    /** « connectivity_check » event: [via] null = the TV's own link (Wi-Fi / Ethernet). */
    fun connectivityEvent(via: String?, latencyMs: Long?) {
        val link = TvNetDiag.localLink(this)
        val v = via ?: when {
            "Ethernet" in link -> "ethernet"
            link == "Wi-Fi" -> "wifi"
            else -> if (latencyMs == null) "none" else "wifi"
        }
        TvConnect.track("connectivity_check", mapOf("via" to v, "ok" to (latencyMs != null), "latency_ms" to latencyMs))
    }

    /** « library_stats » once a day (number of files and bytes, never their names). */
    private fun libraryStatsDaily() {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("library_stats_at", 0) < 24 * 3_600_000L) return
        val items = runCatching { server?.libraryItems() }.getOrNull() ?: return
        prefs.putLong("library_stats_at", now)
        TvConnect.track("library_stats", mapOf("files" to items.size, "bytes" to items.sumOf { it.size }))
    }

    /** Downloaded update APKs of versions already installed (or older) take room: removed at start. */
    private fun cleanUpdateFiles() = runCatching {
        val installed = TvConnect.link?.installed?.versionCode ?: return@runCatching
        registry.volumes().filter { it.kind != VolumeKind.SAF }.map { File(it.dir, ".castbridge-update") }.plus(File(filesDir, "updates"))
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { f -> Regex("castbridge-tv-(\\d+)\\.apk").find(f.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it <= installed } == true }
            .forEach { it.delete() }
    }
    fun checkNetNow() { main.removeCallbacks(netTick); main.post(netTick) }

    /** « ✓ Wi-Fi 40 ms · ✓ Passerelle S21+ » */
    fun netSummary(): String {
        val d = netDirectMs?.let { "✓ ${TvNetDiag.localLink(this)} $it ms" } ?: "✗ ${TvNetDiag.localLink(this)} sans Internet"
        val g = if (gateway?.connected == true) (netGatewayMs?.let { " · ✓ passerelle $it ms" } ?: " · ✗ passerelle sans Internet") else ""
        return d + g
    }

    private val transferTick = object : Runnable {
        override fun run() {
            val busy = (server?.activeTransfers() ?: 0) > 0 || bt?.busy == true || usb?.isRunning() == true
            if (busy) {
                if (wake?.isHeld != true) wake = runCatching {
                    getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "castbridge:transfer").also { it.acquire(10 * 60_000L) }
                }.getOrNull()
                if (wifiLock?.isHeld != true) wifiLock = runCatching {
                    @Suppress("DEPRECATION")
                    (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "castbridge-transfer").also { it.acquire() }
                }.getOrNull()
            } else releaseLocks()
            main.postDelayed(this, 5000)
        }
    }

    private fun releaseLocks() {
        runCatching { wake?.let { if (it.isHeld) it.release() } }; wake = null
        runCatching { wifiLock?.let { if (it.isHeld) it.release() } }; wifiLock = null
    }

    // ------------------------------------------------------------------ screen

    fun attach(s: Screen) {
        screen = s
        takePending()?.let { p -> main.post { s.runPending(p) } }
    }

    fun detach(s: Screen) { if (screen === s) screen = null }

    fun takePending(): Pending? {
        val p = pending ?: return null
        pending = null
        return p.takeIf { LaunchPolicy.pendingValid(it.at, System.currentTimeMillis()) }
    }

    /** Called by the screen once a pending request started playing. */
    fun pendingPlayed() { pendingDone?.countDown() }

    fun notice(msg: String) { main.post { screen?.takeIf { it.shown }?.notice(msg) } }

    /** Status line of a channel (Bluetooth, Wi-Fi Direct, USB, SSH), shown on the screen. Thread-safe. */
    fun setStatus(key: String, text: String?) {
        if (text == null) statuses.remove(key) else statuses[key] = text
        main.post { screen?.statusesChanged() }
    }

    private fun canUseFullScreen(): Boolean = Build.VERSION.SDK_INT < 34 || runCatching { getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }.getOrDefault(false)
    private fun notificationsAllowed(): Boolean = runCatching { getSystemService(NotificationManager::class.java).areNotificationsEnabled() }.getOrDefault(false)
    fun overlayAllowed(): Boolean = Build.VERSION.SDK_INT < 23 || runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false)

    private fun launchState() = LaunchPolicy.State(screen?.shown == true, overlayAllowed(), canUseFullScreen(), notificationsAllowed(), Build.VERSION.SDK_INT)

    /**
     * Shows [intent] (the player screen, a settings screen) from the background as Android allows it: through the visible
     * screen, a direct start (display-over-apps granted), or a notification. Returns null when shown, else what to tell the user.
     */
    fun launchScreen(intent: Intent, what: String): String? {
        val ways = LaunchPolicy.ways(launchState())
        for (w in ways) {
            val ok = when (w) {
                LaunchPolicy.Way.DIRECT -> onMainWait { runCatching { screen!!.activity.startActivity(intent) }.isSuccess } == true
                LaunchPolicy.Way.START_ACTIVITY -> runCatching { startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                LaunchPolicy.Way.FULL_SCREEN_NOTIFICATION -> notifyLaunch(intent, what, fullScreen = true)
                LaunchPolicy.Way.NOTIFICATION -> notifyLaunch(intent, what, fullScreen = false)
            }
            if (ok) return if (w == LaunchPolicy.Way.DIRECT || w == LaunchPolicy.Way.START_ACTIVITY) null else LaunchPolicy.message(ways)
        }
        return LaunchPolicy.message(emptyList())
    }

    private fun notifyLaunch(intent: Intent, what: String, fullScreen: Boolean): Boolean = runCatching {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LAUNCH, "Demandes du téléphone", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(this, 1, Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(this, CH_LAUNCH).setContentTitle("CastBridge TV").setContentText(what)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_CALL)
        if (fullScreen) b.setFullScreenIntent(pi, true)
        nm.notify(NOTIF_LAUNCH, b.build())
        true
    }.getOrDefault(false)

    private fun playerIntent() = Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    /**
     * The player the HTTP server talks to: the screen when it is up; otherwise the request is kept, the screen is brought up
     * if Android allows it (and the answer waits for playback to start, 6 s at most), else [NeedsForeground] with the reason.
     */
    val playerBridge: Player = object : Player {
        private fun s() = screen
        private fun request(p: Pending, direct: (Screen) -> Unit) {
            val s = s()
            if (s != null && s.shown) { direct(s); return }
            val latch = CountDownLatch(1)
            pending = p; pendingDone = latch
            val why = launchScreen(playerIntent(), "Le téléphone demande de lire une vidéo : ouvrez CastBridge TV")
            if (why == null && latch.await(6, TimeUnit.SECONDS)) return
            if (why == null) return                              // started, still opening: it will play when up
            throw NeedsForeground(why)
        }
        override fun play(file: File, posMs: Long) = request(Pending.Local(file, posMs)) { it.play(file, posMs) }
        override fun playStream(url: String, name: String, posMs: Long) = request(Pending.Stream(url, name, posMs)) { it.playStream(url, name, posMs) }
        override fun playSaf(name: String, size: Long, posMs: Long) = request(Pending.Saf(name, size, posMs)) { it.playSaf(name, size, posMs) }
        override fun pause() { s()?.pause() }
        override fun resume() { s()?.resume() }
        override fun seek(posMs: Long) { s()?.seek(posMs) }
        override fun stop() { pending = null; s()?.stop() }
        override fun state(): PlayerState = s()?.state() ?: PlayerState()
        override fun tracks(): PlayerTracks? = s()?.tracks()
        override fun command(c: PlayerCommand): Boolean = s()?.command(c) ?: false
        override fun subtitleFile(file: File): Boolean = s()?.subtitleFile(file) ?: false
    }

    private fun <T> onMainWait(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var r: T? = null
        val l = CountDownLatch(1)
        main.post { r = runCatching(block).getOrNull(); l.countDown() }
        l.await(5, TimeUnit.SECONDS)
        return r
    }

    /** Runs [f] on the visible screen (dialogs, system pickers); else returns the message for the phone. */
    private fun onScreen(f: (Activity) -> String?): String? {
        val s = screen
        if (s == null || !s.shown) return "L'écran de CastBridge TV n'est pas affiché sur la TV : ouvrez l'app CastBridge TV avec la télécommande, puis réessayez."
        return onMainWait { f(s.activity) } ?: null
    }

    // ------------------------------------------------------------------ storage (docs/STORAGE.md)

    fun rescanAsync(remeasure: Boolean = false) {
        runCatching { bg.execute { runCatching { server?.storageChanged(remeasure) }; updateStorageStatus() } }
    }

    fun updateStorageStatus() {
        runCatching { bg.execute { val line = runCatching { server?.storageLine() }.getOrNull(); if (line != null) setStatus("0-storage", line) } }
    }

    private val storageTick = object : Runnable {
        override fun run() { rescanAsync(); main.postDelayed(this, 15_000) }    // safety net if a broadcast is never delivered (unknown firmware)
    }

    private fun registerStorageEvents() {
        val f = IntentFilter().apply {
            listOf(Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_REMOVED, Intent.ACTION_MEDIA_BAD_REMOVAL).forEach { addAction(it) }
            addDataScheme("file")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val path = i.data?.path
                if (i.action != Intent.ACTION_MEDIA_MOUNTED && path != null)
                    registry.volumes().filter { it.kind == VolumeKind.REMOVABLE && it.dir.absolutePath.startsWith(path) }.forEach { registry.markRemoved(it.id) }
                rescanAsync(remeasure = i.action == Intent.ACTION_MEDIA_MOUNTED)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED) else registerReceiver(r, f)
            storageReceiver = r
        }.onFailure { Log.w(TAG, "storage receiver: ${it.javaClass.simpleName}") }
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val cb = object : android.os.storage.StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: android.os.storage.StorageVolume) { rescanAsync() }
            }
            getSystemService(android.os.storage.StorageManager::class.java).registerStorageVolumeCallback(mainExecutor, cb)
            volumeCallback = cb
        }
        main.postDelayed(storageTick, 15_000)
    }

    /** Opens the system folder picker on the TV screen (only possible while the screen is shown). */
    fun launchSafPicker(): String = onScreen { act -> (act as? PlayerActivity)?.pickSafFolder() } ?: "Sélecteur ouvert sur l'écran de la TV."

    /** The TV's storage settings, most specific first; null = opened, else why not. */
    fun openStorageSettings(): String? = onScreen { act ->
        for (action in listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "android.settings.MEMORY_CARD_SETTINGS", Settings.ACTION_SETTINGS)) {
            if (runCatching { act.startActivity(Intent(action)) }.isSuccess) return@onScreen null
        }
        "Aucun écran de réglages de stockage n'a pu être ouvert sur cette TV : ouvrez les réglages à la main, ou branchez la clé sur un ordinateur (docs/STORAGE.md). " +
            "CastBridge ne peut pas formater une clé lui-même."
    }

    /** Opens the "display over other apps" setting for this app (lets remote playback bring the screen up by itself). */
    fun openOverlaySettings(from: Activity?): String? {
        val tries = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
        val act = from ?: screen?.takeIf { it.shown }?.activity
            ?: return "L'écran de CastBridge TV n'est pas affiché : ouvrez l'app sur la TV, puis MENU > Lecture à distance."
        for (i in tries) if (onMainWait { runCatching { act.startActivity(i) }.isSuccess } == true) return null
        return "Cette TV n'a pas l'écran « Afficher par-dessus les autres apps » (GaiaOS) : la lecture à distance demandera d'ouvrir l'app, ou passera par une notification."
    }

    private fun logResources(dir: File, p: castbridge.core.tv.TvProfile) {
        val am = getSystemService(android.app.ActivityManager::class.java)
        Log.i(TAG, "profile: videos used=${Storage.used(dir) shr 20}MB free=${dir.usableSpace shr 20}MB quota=${Storage.quota(dir, p) shr 20}MB " +
            "heap=${Runtime.getRuntime().maxMemory() shr 20}MB memClass=${am.memoryClass}MB lowRam=${am.isLowRamDevice} pss=${android.os.Debug.getPss() / 1024}MB")
    }

    // ------------------------------------------------------------------ extra API

    fun backgroundJson(): String =
        """{"autostart":${prefs.getBool("autostart", true)},"overlay":${overlayAllowed()},"fullScreenIntent":${canUseFullScreen()},""" +
            """"notifications":${notificationsAllowed()},"screenVisible":${screen?.shown == true},"sdk":${Build.VERSION.SDK_INT}}"""

    private fun extraApi(path: String, method: String, params: Map<String, String>): ApiReply? = when {
        path.startsWith("/api/ssh") -> ssh?.api(path, method, params)
        // What the TV screen shows right now (the app's own window only), to check the layout remotely.
        path == "/api/screenshot" && method == "GET" -> ScreenCapture.resumed?.let { a ->
            ScreenCapture.capture(a)?.let { ApiReply.binary(it, "image/png") } ?: ApiReply(500, """{"error":"capture impossible"}""")
        } ?: ApiReply(409, """{"error":"Aucun écran de CastBridge TV n'est affiché"}""")
        // The quiz screen opens from the visible TV screen only (Android 14 blocks background activity starts).
        path.startsWith("/api/quiz") -> QuizHub.api(screen?.takeIf { it.shown }?.activity, path, method)
        path.startsWith("/api/games") -> GamesHub.api(screen?.takeIf { it.shown }?.activity, this, path, method, params)
        path.startsWith("/api/sudoku") -> SudokuHub.api(screen?.takeIf { it.shown }?.activity, this, path, method, params)
        path.startsWith("/api/chess") -> ChessHub.api(screen?.takeIf { it.shown }?.activity, this, path, method, params)
        path == "/api/update" && method == "GET" -> updater?.let { ApiReply(200, it.infoJson()) }
        path == "/api/update/install" && method == "POST" -> updater?.install(listOf(params["name"].orEmpty()), params["force"] == "1")
        path == "/api/devsettings" && method == "POST" -> {
            val msg = onScreen { act -> (act as? PlayerActivity)?.openDevSettings() } ?: "Réglages ouverts sur la TV."
            ApiReply(200, """{"message":${ReceiverServer.q(msg)}}""")
        }
        path == "/api/gateway" && method == "GET" -> ApiReply(200, gateway?.json() ?: """{"listening":false,"connected":false}""")
        path == "/api/gateway/test" && method == "GET" -> gateway?.test() ?: ApiReply(409, """{"error":"passerelle non démarrée"}""")
        path == "/api/gateway/speed" && method == "GET" -> gateway?.speed(params["bytes"]?.toLongOrNull() ?: 2_000_000)
            ?: ApiReply(409, """{"error":"passerelle non démarrée"}""")
        path == "/api/gateway/diag" && method == "GET" -> gateway?.let { g ->
            val host = params["host"]?.takeIf { it.isNotBlank() } ?: "8.8.8.8"
            val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
            val act = screen?.takeIf { it.shown }?.activity as? PlayerActivity
            act?.let { a -> main.post { a.showDiag(host) } }                 // also shown on the TV when the screen is up
            g.diagnose(host) { l -> lines += l; act?.let { a -> main.post { a.appendDiag(l) } } }
            ApiReply(200, "{\"host\":${ReceiverServer.q(host)},\"lines\":[" + lines.joinToString(",") { ReceiverServer.q(it) } + "]}")
        } ?: ApiReply(409, """{"error":"passerelle non démarrée"}""")
        path == "/api/bluetooth" && method == "GET" -> bt?.let { ApiReply(200, it.stateJson(statuses["1-bt"])) }
        path == "/api/bluetooth/discoverable" && method == "POST" -> {
            if (bt?.hasPermission() == true) bt?.start()
            val msg = onScreen { act -> (act as? PlayerActivity)?.makeDiscoverable(); "Demande envoyée : acceptez-la sur l'écran de la TV" }
            ApiReply(202, """{"message":${ReceiverServer.q(msg ?: "")}}""")
        }
        path == "/api/apk" && method == "GET" -> updater?.let { ApiReply(200, it.listJson()) }
        path == "/api/apk/install" && method == "POST" ->
            updater?.install(params["names"].orEmpty().split('/').filter { it.isNotEmpty() }, params["force"] == "1")
        path.startsWith("/api/server") -> serverApi(path, method, params)
        path == "/api/usb" && method == "GET" -> ApiReply(200, usbJson())
        path == "/api/usb/import" && method == "POST" -> ApiReply(200, usbJson(usb?.importFromVolumes() ?: "indisponible"))
        path == "/api/background" && method == "GET" -> ApiReply(200, backgroundJson())
        path == "/api/autostart" && method == "POST" -> {
            val v = params["enabled"] ?: return ApiReply(400, """{"error":"enabled=1|0 required"}""")
            prefs.putBool("autostart", v == "1" || v == "true"); ApiReply(200, backgroundJson())
        }
        path == "/api/overlay-permission" && method == "POST" -> {
            val why = openOverlaySettings(null)
            ApiReply(200, """{"opened":${why == null},"message":${why?.let(ReceiverServer::q) ?: "null"}}""")
        }
        else -> null
    }

    /**
     * Server link, for the phone's advanced screen (PIN-protected like the other API routes): GET /api/server (state),
     * POST /api/server/url?url= (empty = official server), /contact, /check-update, /install, /quiz-sync, GET /api/server/me,
     * POST /api/server/erase. The consent is only given on the TV screen (information screen), never through the API.
     */
    private fun serverApi(path: String, method: String, params: Map<String, String>): ApiReply? {
        val link = TvConnect.link ?: return ApiReply(503, """{"error":"lien serveur indisponible"}""")
        fun accepted(msg: String) = ApiReply(202, """{"message":${ReceiverServer.q(msg)},"state":${TvConnect.stateJson()}}""")
        return when {
            path == "/api/server" && method == "GET" -> ApiReply(200, TvConnect.stateJson())
            path == "/api/server/url" && method == "POST" -> {
                val raw = params["url"].orEmpty().trim()
                val url = if (raw.isEmpty() || raw == "default") castbridge.core.connect.ServerUrl.DEFAULT else raw
                val problem = castbridge.core.connect.ServerUrl.problem(url)
                if (problem != null) ApiReply(400, """{"error":${ReceiverServer.q(problem)}}""")
                else { TvConnect.post { state.baseUrl = url; tick() }; accepted("Serveur : ${castbridge.core.connect.ServerUrl.normalize(url)}") }
            }
            path == "/api/server/check-update" && method == "POST" -> {
                TvConnect.feature("updates", "phone")
                TvConnect.post { checkUpdate(castbridge.core.update.UpdateSchedule.Trigger.USER) }; accepted("Recherche d'une mise à jour…")
            }
            path == "/api/server/contact" && method == "POST" -> {
                TvConnect.post { if (contact()) flush() }; accepted("Contact du serveur et envoi des statistiques en attente…")
            }
            path == "/api/server/install" && method == "POST" -> { TvConnect.post { offerInstall(userAsked = true) }; accepted("Installation demandée : validez sur la TV si elle le demande") }
            path == "/api/server/quiz-sync" && method == "POST" -> { TvConnect.post { syncQuiz() }; accepted("Mise à jour des questions…") }
            path == "/api/server/me" && method == "GET" ->
                TvConnect.call(30_000) { myData() }?.let { ApiReply(200, it) } ?: ApiReply(502, """{"error":"serveur injoignable ou occupé"}""")
            path == "/api/server/erase" && method == "POST" ->
                if (TvConnect.call(30_000) { eraseMyData(); true } == true) accepted("Données effacées ; l'écran d'information sera affiché sur la TV")
                else ApiReply(502, """{"error":"effacement impossible (serveur injoignable ?)"}""")
            else -> null
        }
    }

    private fun usbJson(msg: String? = null): String {
        val u = usb
        return "{\"running\":${u?.isRunning() == true},\"message\":${ReceiverServer.q(msg ?: u?.message ?: "")}," +
            "\"volumes\":[${u?.volumeRoots().orEmpty().joinToString(",") { ReceiverServer.q(it.absolutePath) }}]}"
    }

    // ------------------------------------------------------------------ Device

    override fun sysinfo(): SysInfo {
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))  // sticky
        val present = b?.getBooleanExtra(android.os.BatteryManager.EXTRA_PRESENT, false) == true
        val level = b?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = b?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val mem = runCatching { android.app.ActivityManager.MemoryInfo().also { getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) } }.getOrNull()
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        return SysInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            ip = localIp(),
            batteryPct = if (present && level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (present) status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL else null,
            uptimeMs = android.os.SystemClock.elapsedRealtime(),
            appVersion = ver,
            pssMb = (android.os.Debug.getPss() / 1024).toInt(),
            memAvailMb = mem?.let { (it.availMem shr 20).toInt() },
            memTotalMb = mem?.let { (it.totalMem shr 20).toInt() },
            lowMemory = mem?.lowMemory,
        )
    }

    private val audio by lazy { getSystemService(AudioManager::class.java) }

    override fun volume(): Int? = runCatching {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else null
    }.getOrNull()

    override fun setVolume(pct: Int) {
        // Fixed-volume TVs (HDMI-CEC / external amp) may ignore or refuse this: nothing more an app can do.
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct * max / 100f), 0)
        }
        notice("Volume ${volume() ?: pct} %")
    }

    /** Restarts the app's parts (server, channels) and its screen, inside the same process (an app cannot reboot the TV). */
    override fun restartApp() {
        main.post {
            stopCore()
            started = false
            startCore()
            screen?.activity?.recreate()
        }
    }

    // ------------------------------------------------------------------ mDNS announce: _castbridge._tcp with role=receiver

    private fun register() {
        multicastLock = runCatching {
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock("castbridge-tv").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val info = NsdServiceInfo().apply {
            serviceName = "CastBridge TV " + (Build.MODEL ?: "")
            serviceType = ReceiverServer.SERVICE_TYPE
            port = ReceiverServer.PORT
            setAttribute("role", "receiver")
            setAttribute("v", "0.2")
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "mDNS ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { Log.w(TAG, "mDNS failed $e") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        nsd = getSystemService(NsdManager::class.java)
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l); nsdListener = l }
    }

    private fun stopCore() {
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }; nsdListener = null
        runCatching { multicastLock?.release() }
        runCatching { storageReceiver?.let { unregisterReceiver(it) } }; storageReceiver = null
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            (volumeCallback as? android.os.storage.StorageManager.StorageVolumeCallback)?.let { getSystemService(android.os.storage.StorageManager::class.java).unregisterStorageVolumeCallback(it) }
        }
        main.removeCallbacks(storageTick)
        bt?.stop(); wd?.stop(); ssh?.stop(); updater?.stop(); gateway?.stop()
        server?.stop(); server = null
        library?.worker?.stopped = true
        releaseLocks()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) System.gc()      // nothing big is cached here (thumbnails are on disk)
    }

    override fun onDestroy() {
        stopCore()
        main.removeCallbacksAndMessages(null)
        bg.shutdownNow()
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeTV"
        private const val CH_SERVICE = "tv-service"
        private const val CH_LAUNCH = "tv-launch"
        private const val NOTIF = 1
        private const val NOTIF_LAUNCH = 2
        @Volatile var running: TvService? = null; private set

        fun start(ctx: Context) {
            val i = Intent(ctx, TvService::class.java)
            runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
                .onFailure { Log.w(TAG, "start: ${it.javaClass.simpleName}") }
        }

        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()

        fun hasBtPermission(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
}

/** Starts the background service when the TV boots (if « Démarrer avec la TV » is on) and after a Wi-Fi update of the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val on = TvPrefs(c).getBool("autostart", true)
        if (castbridge.core.tv.BootPolicy.shouldStart(i.action, on)) TvService.start(c)
    }
}
