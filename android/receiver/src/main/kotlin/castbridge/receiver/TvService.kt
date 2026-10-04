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
import castbridge.core.status.IconKind
import castbridge.core.status.Tech
import castbridge.core.net.NetState
import castbridge.core.net.NetStateTracker
import castbridge.core.net.netJson
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
        /** The permanent status bar ([TvService.icons]) may have changed: redraw it (cheap, diffed by the screen). */
        fun iconsChanged()
        fun thumbReady(name: String)
        /** A playback request that arrived while the screen was hidden, to run now. */
        fun runPending(r: Pending)
        /** A reception began, moved on or ended (ReceiverServer.progress): refresh the live line (about once a second at most). */
        fun transfersChanged() {}
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
    /** « Générer un nouveau PIN » (écran « Code PIN de la TV ») : règles, écriture, garde et service mis à jour ensemble ; journal « PIN régénéré » sans valeur. */
    val pinRegenerator by lazy { castbridge.core.tv.pin.PinRegenerator(prefs.pinStore(), guard, { Log.i(TAG, it) }, { pin = it }, System::currentTimeMillis) }
    /** Une copie venant d'un téléphone est en cours (tous chemins) : le code ne change pas pendant ce temps. */
    fun transferInProgress(): Boolean = reception.active().isNotEmpty() || server?.receiving().orEmpty().isNotEmpty()
    var server: ReceiverServer? = null; private set
    /** What the TV is receiving, from every path. Owned here, not by the HTTP server: a Bluetooth copy shows even if the port was taken. */
    val reception = castbridge.core.xfer.TransferProgress()
    var library: LibraryProvider? = null; private set
    /** Virtual folders of the library (docs/LIBRARY-AGENT.md, "Dossiers sur la TV"): files stay flat, a folder is a label kept here. */
    private val folderIndex by lazy { castbridge.core.tv.FolderIndex(File(filesDir, "folders.db")) }
    var bt: BtServer? = null; private set
    /** « Téléphones de confiance » (docs/BT-PLUG-AND-PLAY.md): who may use the TV without the PIN, and the pairing window. */
    lateinit var trust: castbridge.core.trust.TrustRegistry; private set
    lateinit var pairing: castbridge.core.trust.PairingSession; private set
    /** A TV synchronizes with at most 8 phones: the ninth waits for its owner to choose the one to remove (docs/BT-PLUG-AND-PLAY.md, « Téléphones synchronisés : 8 au plus »). */
    lateinit var capacity: castbridge.core.trust.PairCapacityFlow; private set
    /** The permanent status bar of the screen (docs/ADMIN.md, « Barre d'icônes ») : fed here from what the service already knows. */
    val icons = castbridge.core.status.StatusIconModel({ System.currentTimeMillis() })
    /** The measured status badges (core [castbridge.core.tv.status.StatusSnapshot] read by [StatusFeed]); grey until measured, grey « périmé » when not refreshed. */
    val statusBoard = castbridge.core.tv.status.StatusBoard { System.currentTimeMillis() }
    private val lanSeen = ConcurrentHashMap<String, Long>()
    private val iconsPosted = java.util.concurrent.atomic.AtomicBoolean(false)
    var wd: WifiDirectGroup? = null; private set
    var usb: UsbImporter? = null; private set
    var ssh: SshControl? = null; private set
    /** « API par Bluetooth »: the HTTP API over RFCOMM (docs/ADMIN.md). */
    var btApi: BtApiControl? = null; private set
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
    /** « Diffusion YouTube vers cette TV (DIAL) » (docs/TV-CAST-DIAL.md) : réglage `cast.dial`, actif par défaut. */
    private var dial: DialHost? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder = Local()

    override fun onCreate() {
        super.onCreate()
        hookCapture()                      // before any screen resumes, so /api/screenshot always knows the front screen
        running = this
        runCatching { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CH_TRANSFER, "Réceptions en cours", NotificationManager.IMPORTANCE_LOW)) }
        reception.addListener(receptionNotifier)
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
            .setSmallIcon(R.drawable.ic_stat_castbridge).setOngoing(true).setContentIntent(open).build()
    }

    // ------------------------------------------------------------------ activation
    private val activationWatch = object : Runnable {
        override fun run() {
            if (started) return
            if (TunnelHub.termsAccepted(this@TvService)) ActivationCenter.scanFiles()      // a key is read only after the terms of use were accepted on this TV (activation screen)
            if (!ActivationCenter.locked()) { startCore(); return }
            main.postDelayed(this, 5_000)
        }
    }
    private var ownerBt: OwnerBtHost? = null
    fun ownerStatus(): String = ownerBt?.state ?: "service non démarré"
    private val rentalTick = object : Runnable { override fun run() { Thread { RentalHub.sweep(this@TvService, castbridge.core.lots.SweepTrigger.PERIODIC).forEach { notice(it) } }.start(); main.postDelayed(this, 15 * 60_000L) } }
    fun startOwnerChannel() { if (ownerBt == null) ownerBt = OwnerBtHost(this); runCatching { ownerBt?.start() } }

    private fun watchForActivation() {
        setStatus("0-storage", "Usage soumis à autorisation : activation requise")
        main.removeCallbacks(activationWatch); main.postDelayed(activationWatch, 5_000)
    }

    // ------------------------------------------------------------------ core

    private fun startCore() {
        if (started) return
        // A locked TV starts no server, no pairing, no sending, no telemetry: only a watch for the activation (the activation screen or the USB file unlocks it)
        ActivationCenter.init(this)
        startOwnerChannel()                                      // the owner's phone can push the activation by Bluetooth, locked or not
        if (ActivationCenter.locked()) { watchForActivation(); return }
        started = true
        prefs = TvPrefs(this)
        volProvider = AndroidVolumeProvider(this, prefs)
        registry = VolumeRegistry(volProvider).also { it.refresh() }        // fast scan, no speed test on the main thread
        videosDir = registry.volumes().first().dir                         // internal storage is always first
        pin = prefs.pin()
        guard = PinGuard(pin)
        trust = castbridge.core.trust.TrustRegistry(TrustFile(File(filesDir, "trusted_phones.txt")))
        pairing = castbridge.core.trust.PairingSession(trust)
        capacity = castbridge.core.trust.PairCapacityFlow(trust, onDenied = { pairing.recordDenial(it) }, active = { presence.statuses().filter { it.state != castbridge.core.trust.PhonePresence.State.DISCONNECTED }.map { it.address }.toSet() })
        // never silent: what happens to a ninth phone is said on the TV (the same reason is given to the phone, see HelloHandler)
        capacity.addListener { e ->
            e.removedAddress?.let { presence.forget(it); icons.remove(castbridge.core.status.IconKind.PHONE, it); iconsChanged() }
            castbridge.core.trust.PhonesTexts.tvMessage(e).let { notice(it); setStatus("1-phone", it) }
        }
        val profile = prefs.profile()
        logResources(videosDir, profile)
        // Thumbnails only while nothing plays (one decode at a time on this TV).
        library = Thumbnailer.provider(this, canRun = { playerBridge.state().state in setOf("idle", "ended", "error") },
            onThumb = { n -> screen?.thumbReady(n) })
        btApi = BtApiControl(this, prefs, ::btBonded, ::btTrusted, { (server?.activeTransfers() ?: 0) > 0 }) { setStatus("4-api-bt", it) }
        startServer()
        register()
        LotsHub.startup(this, videosDir)
        Thread { RentalHub.sweep(this, castbridge.core.lots.SweepTrigger.APP_START).forEach { notice(it) }; main.postDelayed(rentalTick, 15 * 60_000L) }.start()   // the autonomous deletion of ended rentals
        bt = BtServer(this, videosDir, guard, negotiate = ::linkInfo, hello = ::btHello, trusted = ::btTrusted, parental = ParentalHub.syncHost,
            progress = { reception }) { setStatus("1-bt", it) }
        wd = WifiDirectGroup(this, prefs) { setStatus("2-wd", it); syncIconsAsync() }
        usb = UsbImporter(this, videosDir) { setStatus("3-usb", it) }
        ssh = SshControl(this, { setStatus("4-ssh", it) }, { setStatus("4-ssh-bt", it) },
            onSessions = { n ->
                setStatus("4-ssh-n", if (n > 0) (if (n == 1) "SSH : 1 connexion active" else "SSH : $n connexions actives") else null)
                icons.setSsh(n, ssh?.btSessions() ?: 0); iconsChanged()
            })
        updater = UpdateInstaller(this, { registry.volumes().filter { it.kind != VolumeKind.SAF }.map { it.dir } },
            launch = { i, what -> launchScreen(i, what) }) { m -> setStatus("5-update", m); notice(m) }
        onPermissionsReady()                                    // Bluetooth starts if its permission was granted earlier
        // server link (docs/API-SERVER.md): registration, heartbeat every 15 min, updates, usage events, quiz questions
        TvConnect.start(this)
        TunnelHub.start(this)                                    // remote administration of the editor (docs/REMOTE-TUNNEL-TV.md): waits for the terms, a key and Internet
        bg.execute { cleanUpdateFiles() }
        registerStorageEvents()
        rescanAsync(remeasure = true)
        main.postDelayed(transferTick, 5000)
        main.postDelayed(netTick, 3000)
        icons.setInternet(NetState.CHECKING)
        main.postDelayed(iconTick, 2000)
        watchNetwork()
    }

    /** 32 random bytes made at the first start, in the app's private files (never a volume): the HMAC key of the content index caches (R-12). */
    private fun contentIndexKey(): ByteArray {
        val f = java.io.File(filesDir, "content-index.key")
        if (f.isFile && f.length() == 32L) return f.readBytes()
        val k = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        castbridge.core.tv.AtomicFile.write(f, k)
        return k
    }

    /** Starts the HTTP server once (only this service does: no second server fighting for port 8765); retries if the port is taken. */
    private fun startServer(attempt: Int = 0) {
        val s = ReceiverServer(registry, playerBridge, pin = pin, guard = guard, device = this,
            // downloads (aria2, docs/DOWNLOADS.md): its own manager, independent of any screen
            extension = ApiExtension(::extraApi).then(RemoteHub.api.also { RemoteHub.install(this) }).then(TvDownloads.start(this, registry) { server?.target ?: "auto" }.manager.apiExtension).then(ParentalHub.api).then(castbridge.core.tv.TvDeviceRequestApi { ActivationCenter.init(this); ActivationCenter.requestText() })
                .then(LearnHub.also { it.attach(this) }.api(this))   // « Apprendre » (docs/LEARN.md)
                // « Corbeille CastBridge » of the phone's library assistant (docs/LIBRARY-AGENT.md): recoverable for 30 days, behind the PIN
                .then(castbridge.core.library.agent.TrashApi(registry, playing = { playerBridge.state().takeIf { it.state != "idle" }?.name }, library = library,
                    busy = { name -> server?.busyReason(name) }, folders = folderIndex, changed = { server?.changed() }))
                .then(castbridge.core.tv.FoldersApi(folderIndex) { server?.libraryItems()?.map { it.name }?.toSet().orEmpty() })
                .then(QuizHub.packApi(this))   // question packs pushed by the phone (docs/QUIZ.md)
                .then(LotsHub.api(this))        // lots (Apprendre / Quiz data, 10 Mo cap) pushed by the phone, never downloaded by the TV
                .then(RentalHub.api(this))      // LAZY (never touches the Keystore here: a Keystore failure answers 503, it cannot bring onCreate down): rented lots (sealed, opened with the rental key), the rentals' state, the sweep, activation install (docs/LOTS.md)
                .then(castbridge.core.content.ContentFeedbackApi { TvConnect.feedback }),   // reports handed to the phone (docs/CONTENT-VALIDATION.md)
            progress = reception,
            profile = prefs.profile(), onSettings = { prefs.saveProfile(it); updateStorageStatus() },
            onNotice = { n -> notice(n); setStatus("5-notice", n) },
            safPicker = ::launchSafPicker, settingsOpener = ::openStorageSettings, library = library, readoptJson = { readoptState },
            publicRoutes = castbridge.core.tv.CombinedRoutes(QuizHub.http, ChessHub.http),
            routeGuard = { path -> if (ActivationCenter.trial() && castbridge.core.owner.TrialPolicy.routeBlocked(path)) castbridge.core.owner.TrialPolicy.MESSAGE else null },
            tokenAuth = { t -> trust.verifyToken(t)?.also { a -> phoneSeen(a); presence.seen(a) } }, peers = btApi?.peers,
            // the phone's library assistant never touches what the parental control protects (docs/LIBRARY-AGENT.md)
            contentFlags = castbridge.core.library.agent.EngineContentFlags(ParentalHub.engine), folders = folderIndex,
            // « Rangement à la réception » (docs/STORAGE.md): received files go to real category folders under a clean name; setting "file_on_receive", on by default
            filingLang = { if (prefs.getBool("file_on_receive", true)) "fr" else null },
            sourceName = { a -> trust.get(a)?.name },
            // « la lecture d'abord »: a copy's threads go to the background while a video plays (docs/agent-reports/fluid-playback-during-copy.md)
            receivePriority = ReceivePriority,
            // « déjà sur la TV ? » par contenu (R-12) : empreintes calculées en tâche de fond, à basse priorité, jamais pendant une lecture ou une copie
            contentIndexing = true,
            // R-17 : une ligne INFO par requête refusée (route, statut, code ; jamais de code PIN, jeton ni corps) : `adb logcat -s CastBridgeTV` ou ssh logcat
            onLog = { Log.i(TAG, it) },
            // clé propre à cette TV (stockage privé de l'app, jamais sur la clé USB) : signe les caches .cbhash ; un cache forgé ou venu d'ailleurs est ignoré
            contentIndexKey = runCatching { contentIndexKey() }.getOrNull(),
            // pas d'empreintes pendant un téléchargement ni un import USB (R-06, R-11 : bus USB et Wi-Fi partagés)
            indexBusy = { usb?.isRunning() == true || TvDownloads.get()?.manager?.views()?.any { v ->
                v.state == castbridge.core.dl.DlState.DOWNLOADING || v.state == castbridge.core.dl.DlState.MOVING || v.state == castbridge.core.dl.DlState.CHECKING } == true })
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
     * What a phone gets over Bluetooth when it asks for a faster link (CBTN, [peer] = the paired socket's device, already checked: trusted phone or PIN
     * holder): the TV's addresses and, if the group exists or may be created now (LinkPlanner.mayStartWifiDirect), its Wi-Fi Direct network. Waits up to
     * 8 s for a new group. « Seul le Bluetooth » (docs/agent-reports/auto-wifi-direct.md): a phone that cannot reach the TV's network
     * ([castbridge.core.tv.BtProtocol.WD_LAN_UNREACHABLE]) gets an AUTOMATIC group (random name, fresh password, removed by [wdLeaseCheck]);
     * [castbridge.core.tv.BtProtocol.WD_RELEASE] gives it back. [peer] null = the HELLO answer: an automatic group's password is never in it.
     */
    fun linkInfo(peer: String?, flags: Int): castbridge.core.tv.LinkInfo {
        val ips = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
        val host = wdHost ?: return castbridge.core.tv.LinkInfo(ReceiverServer.PORT, castbridge.core.link.HelloIps.lanOnly(ips))
        // all the decisions (group addresses never announced, one creation at a time, a lease per phone, trial first) live in core TvWdHost (tested)
        val r = host.answer(peer, flags, ips, ReceiverServer.PORT, ActivationCenter.trial(), prefs.getBool("wd_enabled", false))
        if (peer != null && flags and castbridge.core.tv.BtProtocol.WD_RELEASE != 0) main.post { wdLeaseCheck() }
        return r
    }

    /** The core side of the automatic group; its Android calls run on the main looper like the MENU's. */
    private val wdHost: castbridge.core.link.TvWdHost? by lazy {
        val g = wd ?: return@lazy null
        castbridge.core.link.TvWdHost(object : castbridge.core.link.WdGroupDriver by g {
            override fun start(forPhone: Boolean) { main.post { g.start(forPhone) } }
        }, System::currentTimeMillis)
    }

    /**
     * The automatic Wi-Fi Direct group ([castbridge.core.link.WdGroupLease]): removed once every phone gave it back, they left (no client after 45 s), or it
     * stopped receiving; never during a reception. Only HTTP receptions keep it ([castbridge.core.link.LeaseBusy]: an open phone remote never does). Called
     * every 5 s by [transferTick] and at once after a WD_RELEASE. The owner's group (MENU) is never touched.
     */
    private fun wdLeaseCheck() {
        val g = wd ?: return
        val host = wdHost ?: return
        if (g.active == null || !g.auto) return
        val busy = castbridge.core.link.LeaseBusy.count(httpTransfers = server?.activeTransfers() ?: 0, btReceptions = if (bt?.busy == true) 1 else 0, remoteSessions = 0)
        g.clients { n -> if (host.leaseCheck(busy, n)) syncIconsAsync() }
    }

    // ---- plug and play: a trusted phone asks "who am I?" over Bluetooth (CBTH) and gets the TV's Wi-Fi address and its own token ----

    /** The TV's name as shown to its owner and to the phone: the Bluetooth name (what Android shows in the pairing dialogs), else the model. */
    @SuppressLint("MissingPermission")
    fun tvName(): String = (if (hasBtPermission(this)) runCatching { getSystemService(BluetoothManager::class.java)?.adapter?.name }.getOrNull() else null)
        ?.takeIf { it.isNotBlank() } ?: "CastBridge TV"

    private val helloHandler by lazy {
        castbridge.core.trust.HelloHandler(trust, pairing, ::btBonded, ::tvName, runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?",
            { "CastBridge TV " + (Build.MODEL ?: "") }, { linkInfo(null, 0) }, { p -> phoneConnected(p) }, castbridge.core.trust.AttemptLimiter(global = 40, perPeer = 10),
            { name, d -> castbridge.core.trust.TvRefusals.message(name, d)?.let { notice(it); setStatus("1-phone", it) } }, capacity)
    }

    /** « Retirer » on the TV (any screen): the phone loses its access and its tokens at once, and disappears from the status bar. */
    fun removePhone(address: String): Boolean {
        val had = trust.revoke(address)
        presence.forget(address); icons.remove(castbridge.core.status.IconKind.PHONE, address); iconsChanged()
        return had
    }

    fun btHello(peer: String, peerName: String?, requestTrust: Boolean) = helloHandler.handle(peer, peerName, requestTrust)

    /** A trusted phone: in the registry AND still paired at the Bluetooth level (the owner can also remove it in Android's settings). */
    fun btTrusted(peer: String) = trust.isTrusted(peer) && btBonded(peer)

    @SuppressLint("MissingPermission")
    private fun btBonded(address: String): Boolean = hasBtPermission(this) && runCatching {
        getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
    }.getOrDefault(false)

    /** Who is around, truthfully (« connecté », « liaison reprise », « téléphone déconnecté »): see castbridge.core.trust.PhonePresence. */
    val presence = castbridge.core.trust.PhonePresence()

    /**
     * A trusted phone said hello over Bluetooth (CBTH): its icon appears in the status bar and stays while the phone keeps coming back
     * (the lease is renewed by every hello; the phone renews its token in the background). No banner: the icon is the signal.
     */
    private fun phoneConnected(p: castbridge.core.trust.TrustedPhone) {
        icons.up(castbridge.core.status.IconKind.PHONE, p.address, castbridge.core.status.Tech.BLUETOOTH, p.name, leaseMs = PHONE_LEASE_MS)
        when (presence.seen(p.address, p.name)) {
            castbridge.core.trust.PhonePresence.Event.RECONNECTED -> setStatus("1-phone", "Liaison reprise : ${p.name}")
            else -> setStatus("1-phone", "Téléphone connecté : ${p.name}")
        }
        iconsChanged()
    }

    /**
     * A trusted phone used its token over HTTP (Wi-Fi LAN). The hook for the Bluetooth API tunnel (branch bt-everything) is the same
     * call with Tech.BLUETOOTH_TUNNEL. Throttled: the token is checked on every request.
     */
    private fun phoneSeen(address: String, tech: castbridge.core.status.Tech = castbridge.core.status.Tech.WIFI_LAN) {
        val key = address + tech.wire
        val t = System.currentTimeMillis()
        if (t - (lanSeen[key] ?: 0L) < 20_000) return
        lanSeen[key] = t
        val name = trust.list().firstOrNull { it.address == address }?.name ?: return
        icons.up(castbridge.core.status.IconKind.PHONE, address, tech, name, leaseMs = PHONE_LEASE_MS)
        iconsChanged()
    }

    /** The phone remote over [tech]: one icon while the Bluetooth link lasts ([on] false = it closed). */
    fun remoteLink(tech: castbridge.core.status.Tech, on: Boolean) {
        val k = castbridge.core.status.IconKind.REMOTE_CONTROL
        if (on) icons.up(k, "", tech, "Télécommande") else icons.down(k, "", tech)
        iconsChanged()
    }

    private var lastRemoteNote = 0L
    /** A key reached the HTTP remote: keeps its icon for 20 s after the last one. */
    fun remoteKeySeen() {
        val t = System.currentTimeMillis()
        icons.up(castbridge.core.status.IconKind.REMOTE_CONTROL, "", castbridge.core.status.Tech.WIFI_LAN, "Télécommande", leaseMs = 20_000)
        if (t - lastRemoteNote > 3_000) { lastRemoteNote = t; iconsChanged() }
    }

    fun iconsChanged() {
        if (iconsPosted.compareAndSet(false, true)) main.post { iconsPosted.set(false); screen?.iconsChanged() }
    }

    /** Things that are states of the service (not events): read again every few seconds, which also lets held icons expire on screen. */
    private fun syncIcons() {
        fun set(kind: IconKind, on: Boolean, tech: Tech, label: String) =
            if (on) icons.up(kind, "", tech, label) else icons.down(kind, "")
        runCatching {
            val gw = gateway?.takeIf { it.connected }
            set(IconKind.GATEWAY, gw != null, Tech.BLUETOOTH, statuses["6-gw"]?.substringAfter("(", "")?.substringBeforeLast(")")?.ifBlank { null } ?: "Internet du téléphone")
            set(IconKind.WIFI_DIRECT_GROUP, wd?.active != null, Tech.WIFI_DIRECT, "Wi-Fi Direct")
            val usbN = registry.volumes().count { it.kind == VolumeKind.REMOVABLE }
            set(IconKind.USB_DRIVE, usbN > 0, Tech.USB, if (usbN > 1) "$usbN clés USB" else "Clé USB")
            val dl = TvDownloads.get()?.manager?.views()?.count { it.state == castbridge.core.dl.DlState.DOWNLOADING || it.state == castbridge.core.dl.DlState.METADATA || it.state == castbridge.core.dl.DlState.CONNECTING } ?: 0
            set(IconKind.DOWNLOAD, dl > 0, Tech.NONE, if (dl > 1) "$dl téléchargements" else "Téléchargement")
            set(IconKind.PARENTAL_MODE, runCatching { ParentalHub.engine.config().let { it.enabled && it.activeProfile != null } }.getOrDefault(false), Tech.NONE, "Mode enfant")
            val qr = QuizHub.room; val cr = ChessHub.room
            syncRoom(IconKind.QUIZ_PLAYER, qr?.players()?.map { Triple(it.id, it.name, qr.isConnected(it)) })
            syncRoom(IconKind.CHESS_PLAYER, cr?.players()?.map { Triple(it.id, it.name, cr.isConnected(it)) })
        }
        runCatching { statusBoard.update(StatusFeed.snapshot(this)) }      // measured badges (Wi-Fi, Bluetooth, Stockage...): same 5 s tick, local reads only
        iconsChanged()
    }

    private val roomRefs = HashMap<castbridge.core.status.IconKind, Set<String>>()
    /** Quiz / chess players that joined from their phone (Wi-Fi): one icon each while connected; a closed room (null) clears them. */
    private fun syncRoom(kind: castbridge.core.status.IconKind, players: List<Triple<String, String, Boolean>>?) {
        val now = players.orEmpty().filter { it.third }.map { it.first }.toSet()
        for ((id, name, on) in players.orEmpty()) if (on) icons.up(kind, id, castbridge.core.status.Tech.WIFI_LAN, name)
        for (id in roomRefs[kind].orEmpty() - now) icons.down(kind, id)
        roomRefs[kind] = now
    }

    /** Own thread: reading the downloads asks aria2 over RPC, which must never block the main thread or the storage thread. */
    private val iconBg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cb-icons").apply { isDaemon = true } }
    private fun syncIconsAsync() { runCatching { iconBg.execute { syncIcons() } } }

    private val iconTick = object : Runnable {
        override fun run() { syncIconsAsync(); main.postDelayed(this, 5_000) }
    }

    private var captureHooked = false
    private fun hookCapture() { if (!captureHooked) { captureHooked = true; ScreenCapture.install(application) } }

    /** From the activation screen: permissions were just granted. The owner channel always starts; the rest only if the core runs (a locked TV has none of it). */
    fun onActivationPermissions() { startOwnerChannel(); if (started) onPermissionsReady() }

    fun onPermissionsReady() {
        hookCapture()
        startOwnerChannel()
        bt?.start()
        btApi?.start()
        gateway = gateway ?: BtGatewayHost(this, guard, ::btTrusted, ::gatewayStatus)
        gateway?.start()
        // Wi-Fi Direct is opt-in (MENU): creating a group can disturb the TV's own Wi-Fi connection.
        if (prefs.getBool("wd_enabled", false) && wd?.hasPermission() == true) wd?.start()
    }

    /** Wake lock (partial) and Wi-Fi lock only while something is being transferred. */
    /** Latest connectivity per path, for the home tile: ms of a 204 check, or null. */
    @Volatile var netDirectMs: Long? = null; private set
    @Volatile var netGatewayMs: Long? = null; private set
    @Volatile var netCheckedAt = 0L; private set
    /** True when the last round really measured (204 probe); false = state taken from the system callbacks only (no outgoing traffic): [netDirectMs] 0 then means « the system says validated ». */
    @Volatile var netMeasured = false; private set
    /** A manual test (Tests Internet screen) asked for one real probe round. */
    @Volatile private var netManual = false
    /** Debounced state for the TV badge (core NetStateTracker, fed by the same probes — no second loop). */
    private val netTracker = NetStateTracker()
    @Volatile var netState = NetState.CHECKING; private set
    @Volatile var netGatewayAlso = false; private set
    private val netBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var netAgain = false
    private val netTick = object : Runnable {
        override fun run() {
            if (!netBusy.compareAndSet(false, true)) { netAgain = true; return }      // a round is running: it will run once more
            main.removeCallbacks(this)
            var delay = NetStateTracker.STEADY_MS
            try {
                bg.execute {
                    try {
                        val wasDirect = netDirectMs != null; val wasGateway = netGatewayMs != null; val first = netCheckedAt == 0L
                        // The probe (a request to a third party) is never periodic by default: only on a manual test, when the user turned « netProbe » on,
                        // or when the remote-assistance tunnel is enabled (terms accepted) and needs to know whether Internet is reachable.
                        val manual = netManual; netManual = false
                        val probe = manual || prefs.netProbe || runCatching { TunnelHub.termsAccepted(this@TvService) }.getOrDefault(false)
                        val link = TvNetDiag.linkKind(this@TvService)
                        if (probe) {
                            netDirectMs = TvNetDiag.probe(null)
                            netGatewayMs = gateway?.proxy()?.let { TvNetDiag.probe(it) }
                        } else {
                            // No traffic: the system's own validation (NET_CAPABILITY_VALIDATED) and the gateway state.
                            netDirectMs = if (systemValidated()) 0L else null
                            netGatewayMs = if (gateway?.connected == true) 0L else null
                        }
                        netMeasured = probe
                        netCheckedAt = System.currentTimeMillis()
                        setStatus("7-net", netSummary())
                        synchronized(netTracker) {
                            // Without a probe, a link that the system did not validate is « not verified » (CHECKING), never a red « Pas d'Internet »: only « no link at all » is NONE.
                            if (probe || netDirectMs != null || netGatewayMs != null || link == castbridge.core.net.LinkKind.NONE)
                                netState = netTracker.update(link, netDirectMs, gateway?.connected == true, netGatewayMs, android.os.SystemClock.elapsedRealtime())
                            netGatewayAlso = netTracker.gatewayAlsoAvailable
                            delay = if (probe) netTracker.nextDelayMs() else NetStateTracker.STEADY_MS   // 60 s while Internet works, 10-30 s while it does not (probing only)
                        }
                        icons.setInternet(netState)
                        main.post { screen?.statusesChanged() }; iconsChanged(); syncIconsAsync()      // the network just changed: re-read the badges now (no extra loop)
                        TunnelHub.poke()                                   // the path to the Internet (own network / phone gateway / none) may have changed
                        // connectivity_check: at start and when the state changes (not every minute)
                        if (first || wasDirect != (netDirectMs != null)) connectivityEvent(null, netDirectMs?.takeIf { it > 0 }, netDirectMs != null)
                        if (gateway?.connected == true && (first || wasGateway != (netGatewayMs != null))) connectivityEvent("bluetooth", netGatewayMs?.takeIf { it > 0 }, netGatewayMs != null)
                        if (netDirectMs != null && !wasDirect && !first) TvConnect.post { flush() }      // network back: send what waits
                        libraryStatsDaily()
                    } finally {
                        netBusy.set(false)
                        main.postDelayed(this, if (netAgain) 1000L else delay)
                        netAgain = false
                    }
                }
            } catch (e: Exception) { netBusy.set(false); main.postDelayed(this, delay) }   // executor shut down
        }
    }

    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var netGwUp = false

    /** Network lost/available (and gateway connect/disconnect): probe again soon instead of waiting for the next tick. */
    private fun netChanged() { main.removeCallbacks(netKick); main.postDelayed(netKick, 1500) }
    private val netKick = Runnable { if (netCheckedAt > 0) netTick.run() }

    private fun watchNetwork() {
        if (netCallback != null) return
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = netChanged()
            override fun onLost(network: android.net.Network) = netChanged()
        }
        runCatching {
            getSystemService(android.net.ConnectivityManager::class.java)
                .registerNetworkCallback(android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), cb)
            netCallback = cb
        }
    }

    private fun unwatchNetwork() {
        netCallback?.let { cb -> runCatching { getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(cb) } }
        netCallback = null
    }

    /** Gateway status callback: re-probe when a phone connects or disconnects. */
    private fun gatewayStatus(st: String?) {
        setStatus("6-gw", st); syncIconsAsync()
        if ((st != null) != netGwUp) { netGwUp = st != null; netChanged() }
    }

    /** « connectivity_check » event: [via] null = the TV's own link (Wi-Fi / Ethernet). */
    fun connectivityEvent(via: String?, latencyMs: Long?, ok: Boolean = latencyMs != null) {
        val link = TvNetDiag.localLink(this)
        val v = via ?: when {
            "Ethernet" in link -> "ethernet"
            link == "Wi-Fi" -> "wifi"
            else -> if (!ok) "none" else "wifi"
        }
        TvConnect.track("connectivity_check", mapOf("via" to v, "ok" to ok, "latency_ms" to latencyMs))
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
    /** Manual action (Tests Internet screen): one real probe round, now. */
    fun checkNetNow() { netManual = true; main.removeCallbacks(netTick); main.post(netTick) }

    /** What the system itself says (its own validation, no traffic from CastBridge-TV): the active network has Internet validated. */
    private fun systemValidated(): Boolean = runCatching {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

    /** « ✓ Wi-Fi 40 ms · ✓ Passerelle S21+ » */
    fun netSummary(): String {
        val link = TvNetDiag.localLink(this)
        val d = when {
            netDirectMs != null && netMeasured -> "✓ $link ${netDirectMs} ms"
            netDirectMs != null -> "✓ $link connecté"
            netMeasured -> "✗ $link sans Internet"
            link == "aucun réseau" -> "✗ aucun réseau"
            else -> "$link connecté · Internet non vérifié"
        }
        val g = if (gateway?.connected == true) (if (netMeasured) (netGatewayMs?.let { " · ✓ passerelle $it ms" } ?: " · ✗ passerelle sans Internet") else " · passerelle connectée") else ""
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
            runCatching { wdLeaseCheck() }
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

    // ------------------------------------------------------------------ reception progress (one notification per transfer)

    /**
     * Every reception (Wi-Fi, Wi-Fi multivoie, Bluetooth) as a notification of its own, fed by ReceiverServer.progress (already throttled to about one
     * update a second): progress bar while it runs, then « Vidéo reçue ✓ » or the error, removed a few seconds later. The screen is told too.
     */
    private val notifTokens = ConcurrentHashMap<Int, Any>()
    /** Per notification id: the [TransferProgress.Item.updatedAt] last shown (main thread only): an older item (lanes race) is ignored. */
    private val notifShownAt = HashMap<Int, Long>()
    private var sweeping = false
    /** Nobody reads [reception] while a video plays or the app is in the background: this purges an abandoned copy (and its « reprise en attente » notification). */
    private val sweepTick = object : Runnable {
        override fun run() {
            reception.sweep()
            if (reception.active().isNotEmpty()) main.postDelayed(this, 30_000) else sweeping = false
        }
    }
    private val receptionNotifier = castbridge.core.xfer.TransferProgress.Listener { item ->
        main.post {
            screen?.transfersChanged()
            if (!item.ended && !sweeping) { sweeping = true; main.postDelayed(sweepTick, 30_000) }
            showReception(item)
        }
    }

    private fun showReception(item: castbridge.core.xfer.TransferProgress.Item) {
        runCatching {
            val id = NOTIF_TRANSFER + item.seq % 1000
            if (item.updatedAt < (notifShownAt[id] ?: Long.MIN_VALUE)) return
            notifShownAt[id] = item.updatedAt
            val nm = getSystemService(NotificationManager::class.java)
            val token = notifTokens.getOrPut(id) { Any() }                // Handler compares tokens by identity: one object per id
            val open = PendingIntent.getActivity(this, 2, Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
            val b = Notification.Builder(this, CH_TRANSFER).setSmallIcon(R.drawable.ic_stat_castbridge).setContentIntent(open).setOnlyAlertOnce(true)
            if (!item.ended) {
                b.setContentTitle("Réception : ${item.title}").setContentText(item.detail()).setOngoing(true)
                    .setProgress(100, item.percent, item.total <= 0).setCategory(Notification.CATEGORY_PROGRESS)
                main.removeCallbacksAndMessages(token)
            } else {
                b.setContentTitle(item.endLine()).setContentText(item.title).setOngoing(false).setAutoCancel(true)
                main.removeCallbacksAndMessages(token)
                main.postAtTime({ runCatching { nm.cancel(id) } }, token, android.os.SystemClock.uptimeMillis() + 10_000)
            }
            nm.notify(id, b.build())
        }.onFailure { Log.w(TAG, "reception notification: ${it.javaClass.simpleName}") }   // POST_NOTIFICATIONS refused (Android 13+): the screen still shows it
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
            .setSmallIcon(R.drawable.ic_stat_castbridge).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_CALL)
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
        // Parental control: a refused video throws NeedsForeground with the reason (TV library and phone both show it)
        override fun play(file: File, posMs: Long) { ParentalHub.gatePlay(file.name); request(Pending.Local(file, posMs)) { it.play(file, posMs) } }
        override fun playStream(url: String, name: String, posMs: Long) { ParentalHub.gatePlay(name); request(Pending.Stream(url, name, posMs)) { it.playStream(url, name, posMs) } }
        override fun playSaf(name: String, size: Long, posMs: Long) { ParentalHub.gatePlay(name); request(Pending.Saf(name, size, posMs)) { it.playSaf(name, size, posMs) } }
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
        runCatching { bg.execute { runCatching { server?.storageChanged(remeasure) }; runCatching { readopt() }; updateStorageStatus() } }
    }

    /** JSON of the last re-adoption scan (additive `heavy.readopt` of /api/storage). */
    @Volatile private var readoptState: String? = null
    private val readoptDone = HashSet<String>()

    /**
     * Re-adoption (docs/STORAGE.md): once per plug-in of a drive, scan `Download/CastBridge/` only (bounded), rewrite the index
     * (atomic), and let the library rebuild itself from the files (listings read the folder; thumbnails are a regenerable cache).
     * `.part` files are found by the registry and resumed by the phone. Never deletes, never executes anything found.
     */
    private fun readopt() {
        val drives = registry.volumes().filter { it.kind == VolumeKind.REMOVABLE && it.heavyRoot != null }
        readoptDone.retainAll(drives.map { it.id }.toSet())          // a drive that left is scanned again when it returns
        for (v in drives) {
            if (!readoptDone.add(v.id)) continue
            val root = v.heavyRoot ?: continue
            val r = castbridge.core.tv.UsbReadopt.scan(root)
            castbridge.core.tv.UsbReadopt.refreshIndex(root, v.id, r, System.currentTimeMillis())
            runCatching { castbridge.core.tv.SafeSettings.read(root) }                     // validated, not applied automatically
            readoptState = "{\"drive\":${ReceiverServer.q(v.id)},\"index\":${ReceiverServer.q(r.index.name.lowercase())},\"files\":${r.files.size}," +
                "\"parts\":${r.parts.size},\"duplicates\":${r.duplicates.size},\"ignored\":${r.ignored.size},\"truncated\":${r.truncated}}"
            server?.storageChanged()
        }
    }

    fun updateStorageStatus() {
        runCatching { bg.execute {
            val line = runCatching { server?.storageLine() }.getOrNull(); if (line != null) setStatus("0-storage", line)
            // The drive's own identity (maker, model, serial, negotiated USB speed) and what limits it, on the TV's status screen.
            val usb = runCatching { registry.volumes().firstOrNull { it.kind == VolumeKind.REMOVABLE }?.usb }.getOrNull()
            setStatus("0-usbkey", usb?.let { "Clé USB : " + it.details().joinToString(" · ") { (k, v) -> "$k : $v" } })
            setStatus("0-usbwarn", usb?.warnings()?.joinToString("\n")?.ifBlank { null })
        } }
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
        path.startsWith("/api/bluetooth/tunnel") -> btApi?.api(path, method)
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
        // What the TV's status bar shows (labels only: no token, PIN or Bluetooth address)
        path == "/api/connections" && method == "GET" -> ApiReply(200, castbridge.core.status.StatusIconModel.json(icons.snapshot(), System.currentTimeMillis()))
        path == "/api/net" && method == "GET" -> ApiReply(200, synchronized(netTracker) {
            netJson(netTracker, TvNetDiag.linkKind(this), netDirectMs, gateway?.connected == true, netGatewayMs, netCheckedAt) })
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
        path == "/api/activation" && method == "GET" -> { ActivationCenter.init(this); ApiReply(200, "{\"required\":${BuildConfig.REQUIRE_ACTIVATION},\"locked\":${ActivationCenter.locked()},\"label\":${ReceiverServer.q(ActivationCenter.label())},\"code\":${ReceiverServer.q(ActivationCenter.requestText().lineSequence().first().removePrefix("code="))},\"ownerChannel\":${ownerBt != null}${ActivationCenter.statusFields()},\"installKeyProtection\":${ReceiverServer.q(RentalHub.protectionStatus(this))},\"installId\":${ReceiverServer.q(RentalHub.installIdOrEmpty(this))},\"remoteAssist\":${ReceiverServer.q(TunnelHub.statusLine(this))}}") }
        // EXPLICIT owner reset of the installation key (PIN only: TvAuth keeps every /api/activation/install* path for the PIN; closed in the trial). The old files are renamed, never deleted.
        path == "/api/activation/install-key/reset" && method == "POST" ->
            if (params["confirm"] != "RESET") ApiReply(400, """{"error":"confirm=RESET requis : la réinitialisation rend les locations existantes inutilisables"}""")
            else try { ApiReply(200, """{"reset":true,"note":${ReceiverServer.q(RentalHub.resetInstallKey(this))}}""") }
            catch (e: castbridge.core.lots.InstallKeyResetRefusedException) { ApiReply(409, """{"error":${ReceiverServer.q(castbridge.core.lots.InstallKeyPolicy.RESET_REFUSED)}}""") }
            catch (e: castbridge.core.lots.InstallKeyUnavailableException) { ApiReply(503, """{"error":${ReceiverServer.q(castbridge.core.lots.InstallKeyPolicy.UNAVAILABLE_MESSAGE)}}""") }
            catch (e: Exception) { android.util.Log.e("TvService", "réinitialisation de la clé d'installation en échec", e); ApiReply(500, """{"error":${ReceiverServer.q(castbridge.core.lots.InstallKeyPolicy.RESET_FAILED)}}""") }
        path == "/api/bluetooth" && method == "GET" -> bt?.let { ApiReply(200, it.stateJson(statuses["1-bt"])) }
        path == "/api/bluetooth/discoverable" && method == "POST" -> {
            if (bt?.hasPermission() == true) bt?.start()
            startOwnerChannel()
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
        applyDial()
    }

    /** Démarre ou arrête le récepteur DIAL selon le réglage ; sans effet sur le reste du service. */
    fun applyDial() {
        val on = DialHost.enabled(prefs)
        if (on) { if (dial == null) dial = DialHost(this, prefs); runCatching { dial?.start() } }
        else { runCatching { dial?.stop() }; dial = null }
    }
    fun dialStatus(): String = dial?.status() ?: "DIAL arrêté"

    private fun stopCore() {
        runCatching { dial?.stop() }; dial = null
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }; nsdListener = null
        runCatching { multicastLock?.release() }
        runCatching { storageReceiver?.let { unregisterReceiver(it) } }; storageReceiver = null
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            (volumeCallback as? android.os.storage.StorageManager.StorageVolumeCallback)?.let { getSystemService(android.os.storage.StorageManager::class.java).unregisterStorageVolumeCallback(it) }
        }
        main.removeCallbacks(storageTick)
        TunnelHub.stop()
        ownerBt?.stop(); bt?.stop(); btApi?.stop(); wd?.stop(); ssh?.stop(); updater?.stop(); gateway?.stop()
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
        unwatchNetwork()
        reception.removeListener(receptionNotifier)
        runCatching { val nm = getSystemService(NotificationManager::class.java); notifTokens.keys.forEach { nm.cancel(it) } }   // no « reprise en attente » left behind
        main.removeCallbacksAndMessages(null)
        bg.shutdownNow(); iconBg.shutdownNow()
        if (running === this) running = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastBridgeTV"
        private const val CH_SERVICE = "tv-service"
        private const val CH_LAUNCH = "tv-launch"
        private const val CH_TRANSFER = "tv-transfer"
        private const val NOTIF_TRANSFER = 5000
        private const val NOTIF = 1
        /** A phone that said hello (or used its token) is shown connected this long without news; each sign of life renews it. */
        private const val PHONE_LEASE_MS = 10 * 60_000L
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
