package castbridge.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import castbridge.core.owner.*
import castbridge.core.tv.activation.*
import castbridge.receiver.BuildConfig
import java.io.File

/**
 * Activation of this TV (docs/TRIAL-EDITION.md, docs/ACTIVATION-FORMAT.md): reads the hardware identity, verifies and keeps the activation, and says whether the app is
 * [GateState.Locked]. The requirement is a COMPILE SWITCH ([BuildConfig.REQUIRE_ACTIVATION], off in development builds); the PUBLIC keys that may sign an activation are
 * injected at build time from a file outside the repository. Nothing secret lives here: a patched app only unlocks itself, it can never forge an activation another TV accepts.
 */
object ActivationCenter {
    private lateinit var app: Context
    @Volatile private var ready = false
    private val installed = InstalledActivations()
    /** EVERY accepted activation, deduplicated by its text only (never by seat): rentals, their renewals and the `super` right must coexist, which the "newest per seat" view of [installed] would not allow. */
    private val everyActivation = ArrayList<Activation>()
    fun allActivations(): List<Activation> = synchronized(everyActivation) { everyActivation.toList() }
    fun fingerprints(): Fingerprints = fp
    /** The public keys this build trusts (activation, orders, owner-signed lists such as the experts of the remote assistance). */
    fun trustedKeys(): List<TrustedKey> = trusted
    private fun remember(a: Activation) { synchronized(everyActivation) { if (everyActivation.none { it.signature == a.signature }) everyActivation += a } }
    // monotonic time of the TV (counts through sleep, never goes back during a boot): a wall clock wound back cannot freeze the usage ceilings (audit finding: clock rollback)
    private val clock = TvClock(mono = android.os.SystemClock::elapsedRealtime)
    private lateinit var fp: Fingerprints
    lateinit var deviceCode: String; private set
    private var existingInstall = false
    /** [TvClock.uptimeNow] when each activation was installed (key = its signature; persisted as the 2nd field of `activations.txt`): the running-time ceiling counts from there. */
    private val uptimeAtInstall = HashMap<String, Long>()
    private var clockTimer: java.util.Timer? = null
    /** Activation lines (installedAt, uptimeAtInstall or -1 = unknown (old format), text) as last known: the in-memory truth, rewritten whole by [flushIfUnsaved]; [unsaved] = the last write failed and is retried. */
    private val stored = ArrayList<Triple<Long, Long, String>>()
    @Volatile private var unsaved = false
    @Volatile private var clockUnsaved = false
    private const val TAG = "ActivationCenter"
    private var trusted: List<TrustedKey> = emptyList()
    private var ring = KeyRing(emptyList())

    val required: Boolean get() = BuildConfig.REQUIRE_ACTIVATION
    private val requirement get() = ActivationRequirement(required, BuildConfig.ACTIVATION_GRACE_DAYS)

    @Synchronized fun init(ctx: Context) {
        if (ready) return
        app = ctx.applicationContext
        val parsed = TrustedKeyParser.parse(BuildConfig.TRUSTED_KEYS)
        parsed.warnings.forEach { Log.w(TAG, it) }
        trusted = parsed.keys
        ring = KeyRing(trusted)
        fp = DeviceIdentity.fingerprints(rawFactors())
        deviceCode = DeviceCode.of(fp)
        val clockExisted = clockFile().isFile || SafeFile.bak(clockFile()).isFile        // BEFORE loadClock() creates it
        loadClock(); loadGrace(clockExisted)
        loadStored(); reload(); flushIfUnsaved()
        ready = true
        startClockTimer()
        RentalHub.warm(app)          // the Keystore key is made off the main thread, before the first screen asks for the rentals
    }

    // ---- hardware identity (the factors of docs/ACTIVATION-FORMAT.md § 1; each one only if readable and meaningful) ----
    private fun read(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    private fun getprop(name: String): String? = runCatching {
        Runtime.getRuntime().exec(arrayOf("getprop", name)).inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun rawFactors(): RawFactors {
        val base = RawFactors(
            flashSerial = read("/sys/block/mmcblk0/device/serial"), flashCid = read("/sys/block/mmcblk0/device/cid"),
            ethernetMac = read("/sys/class/net/eth0/address"),
            wifiMac = read("/sys/class/net/wlan0/address"), wifiSysfsPath = runCatching { File("/sys/class/net/wlan0/device").canonicalPath }.getOrNull(),
            systemSerial = getprop("ro.serialno"), bluetoothAddress = null,
        )
        if (!BuildConfig.DEBUG) return base
        // DEBUG builds only (the emulator has no hardware identity, so a rental could not wrap its key): factors derived from a test word in files/test-factors.txt. Never in a release.
        val w = runCatching { File(app.filesDir, "test-factors.txt").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return base
        return base.copy(flashSerial = "TESTFLASH-$w", flashCid = "testcid-$w", systemSerial = "TESTSYS-$w")
    }

    /** The « demande d'appareil » (code + full fingerprint set): what the owner's tools need for a complete activation. */
    fun requestText(): String = OwnerFrames.deviceInfo(deviceCode, fp, RentalHub.installPubOrNull(app),      // never blocks the main thread on the Keystore
        castbridge.receiver.wallet.WalletHub.installSigner()?.publicKey)      // the Ed25519 key of the wallet `bind` proof (file, no Keystore): the issuer signs it into the activation (W23-05 audit HIGH-1)

    /**
     * The device request in the form the licence server reads: `code=`, `k=`, `factor=` and `install_sig=` lines, NEVER the private `install=` line (it is not even built, so the Keystore is
     * not touched). What the locked route `GET /api/activation/device-request` hands to a phone that holds the connection code (docs/TV-ACTIVATION-CLE-USB.md); never logged.
     */
    fun serverRequestText(): String {
        if (!ready) init(app)
        return OwnerFrames.deviceInfo(deviceCode, fp, null, castbridge.receiver.wallet.WalletHub.installSigner()?.publicKey)
    }

    /** A phone presented the right connection code to the locked route (any address: Wi-Fi network or Wi-Fi Direct group): the screen then says « Téléphone relié ». The address is not kept. */
    @Volatile var phoneAuthorizedAt = 0L; private set
    fun phoneAuthorized() { phoneAuthorizedAt = android.os.SystemClock.elapsedRealtime() }

    /** What the last accepted activation said about its rentals (« enveloppée pour une autre installation… »), for the activation screen; empty when all went well. */
    @Volatile var lastRentalNotes: List<String> = emptyList(); private set

    // ---- state ----
    private fun wall() = System.currentTimeMillis()
    fun now(): Long = clock.now(wall())
    private fun access(): TvAccess = TvGate.evaluate(installed.all(), emptyList(), now(), if (ready) RentalHub.statuses(app) else emptyList(),
        clockDoubt = castbridge.core.lots.RentalEngine.judge(clock, wall()).doubt, monotonicNowMs = clock.monotonicNow(),
        uptimeNowMs = clock.uptimeNow(), uptimeAtInstall = { a -> synchronized(uptimeAtInstall) { uptimeAtInstall[a.signature] } ?: 0L })
    private val migration: FleetMigration get() = FleetMigration(existingInstall, BuildConfig.LOCK_GRACE_START_MS)

    fun state(): GateState { if (!ready) init(app); return FeatureGate.state(requirement, access(), now(), migration) }
    fun locked(): Boolean = state() is GateState.Locked

    /** A TRIAL key only (with the activation requirement on): copy / move, downloads, the library and every game but the Sudoku are closed ([castbridge.core.owner.TrialPolicy]). */
    fun trial(): Boolean = BuildConfig.REQUIRE_ACTIVATION && ready && (access().trial || state().let { it is GateState.Grace && it.trialRestricted })

    /** The wall clock jumped more than 45 days ahead: usage ceilings are suspended and the screens must ask « Vérifiez l'heure de la TV » ([TvAccess.suspended]). */
    fun clockSuspended(): Boolean = ready && access().suspended
    /** The user says « l'heure est juste » after an AHEAD suspension (accepted up to 400 days ahead, never a clock behind). */
    fun confirmClockAhead(): Boolean { val ok = clock.confirmAhead(wall()); if (ok) saveClock(); return ok }

    /** The edition and the key's properties, shown on every screen of the TV. */
    fun badge(): castbridge.core.owner.Badge = castbridge.core.owner.KeyBadge.of(allActivations(), now(), if (ready) RentalHub.statuses(app) else emptyList())
    fun label(): String = access().label
    /** Extra JSON fields of GET /api/activation: edition, badge, trial window... (see [castbridge.core.owner.KeyStatusJson]). */
    fun statusFields(): String = castbridge.core.owner.KeyStatusJson.fields(allActivations(), now(), if (ready) RentalHub.statuses(app) else emptyList(), trial())
    fun graceUntil(): Long? = migration.graceUntil(requirement)

    /** In the grace period the activation screen is offered once a day (never again the same day). */
    @Synchronized fun dailyPrompt(): Boolean {
        val f = File(app.filesDir, "grace_prompt.txt"); val today = wall() / 86_400_000L
        if (read(f.path)?.toLongOrNull() == today) return false
        runCatching { f.writeText(today.toString()) }; flushIfUnsaved(); return true
    }

    // ---- activation channels (file, typed text): one verification path ----
    private fun receiver() = ActivationReceiver(ring, trusted, fp, Subject.TV, ownInstallKey = runCatching { castbridge.receiver.wallet.WalletHub.installSigner()?.publicKey }.getOrNull())

    /** A key pushed by the owner's phone over Bluetooth: verified (nothing installed) and kept here for the activation screen to paste in its field; the owner confirms with « Valider ». */
    @Volatile var pending: String? = null
    fun stage(text: String): ActivationResult {
        if (!ready) init(app)
        val r = receiver().receive(Channel.MANUAL, text.toByteArray(Charsets.UTF_8), now())
        if (r is ActivationResult.Accepted) pending = text
        return r
    }

    @Synchronized fun accept(channel: Channel, payload: ByteArray): ActivationResult {
        val t = wall()
        val r = receiver().receive(channel, payload, now())
        if (r is ActivationResult.Accepted) {
            pending = null
            installed.install(r.activation); remember(r.activation)
            clock.observe(wall(), r.activation.issuedAt); saveClock()
            synchronized(uptimeAtInstall) { uptimeAtInstall.getOrPut(r.activation.signature) { clock.uptimeNow() } }
            val text = String(payload, Charsets.UTF_8).removePrefix("﻿").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            persist(text, t, clock.uptimeNow())
            flushIfUnsaved()
            lastRentalNotes = runCatching { RentalHub.onActivation(app, r.activation) }.getOrElse { listOf("coffre de clés indisponible : les locations de cette activation n'ont pas pu être ouvertes, réinstallez-la plus tard") }          // the keys of its rentals go into the rental safe
        }
        return r
    }

    /** The verdict the pure lookup / file scan needs from a verification result (never the key). */
    fun verdictOf(r: ActivationResult): Verdict = when (r) {
        is ActivationResult.Accepted -> Verdict.ACCEPTED
        is ActivationResult.Rejected -> when (r.reason) {
            Rejection.WRONG_DEVICE -> Verdict.WRONG_DEVICE
            Rejection.WINDOW_CLOSED, Rejection.NOT_YET_VALID -> Verdict.EXPIRED
            else -> Verdict.NOT_VALID
        }
    }

    /** An activation accepted over the Wi-Fi while the activation screen is open: that screen takes it ([takeWifiAccepted]) and opens the TV. */
    private val wifiAccepted = java.util.concurrent.atomic.AtomicReference<ActivationResult?>(null)
    /** Atomic (audit L7): the activation is taken exactly once, even if the screen and the HTTP thread race. */
    fun takeWifiAccepted(): ActivationResult? = wifiAccepted.getAndSet(null)

    /**
     * POST /api/activation/install of a LOCKED TV ([LockedActivationApi], docs/TV-ACTIVATION-CLE-USB.md): the same verification as a pasted key ([accept], MANUAL channel).
     * A key spread over several lines is joined on one line (the parsers ignore spaces), so that the line kept in `activations.txt` is the whole key.
     */
    fun installFromWifi(text: String): LockedActivationApi.Install {
        val one = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return when (val r = accept(Channel.MANUAL, one.toByteArray(Charsets.UTF_8))) {
            is ActivationResult.Accepted -> { wifiAccepted.set(r); LockedActivationApi.Install.Accepted(label()) }
            is ActivationResult.Rejected -> LockedActivationApi.Install.Rejected(r.message)
        }
    }

    /** Lines for the activation screen explaining what the last [scanFiles] saw (volumes, folders, file state); built by the pure `ActivationLookupReport`, never carries the key. */
    @Volatile var lastReport: List<String> = emptyList()
    /** What the last lookup saw (paths, counts, file states; never the key): the pure `UsbActivationBanner` turns it into the banner. */
    @Volatile var lastFacts: LookupFacts? = null
    private val scanning = java.util.concurrent.atomic.AtomicBoolean(false)

    /** [facts] = what the lookup saw; [result] = the first accepted key, else the first refusal, else null (no readable file). With `install = false` an ACCEPTED result was only VERIFIED, nothing is installed. */
    class UsbLookup(val facts: LookupFacts, val result: ActivationResult?)

    /**
     * Verifies a key WITHOUT installing it: the same verifier as [accept] (signature by a trusted key, binding to this TV's device code, window), no side effect. The USB banner uses it to say
     * « activation trouvée pour cette TV » before the owner presses « Activer ».
     */
    @Synchronized fun check(text: String): ActivationResult {
        if (!ready) init(app)
        return receiver().receive(Channel.MANUAL, text.toByteArray(Charsets.UTF_8), now())
    }

    /**
     * Looks for the `activation` file: `Download/CastBridge/activation` and `Download/activation` of every volume and of the public Download folder, then (always readable under scoped
     * storage) `<Android/data/castbridge.receiver/files>/activation` and `.../CastBridge/activation` of every mounted volume. Read-only; the first accepted file wins; sets [lastReport]
     * and [lastFacts]. [install] false = verify only (the banner), true = the accepted key is installed (the « Activer » button). null = another lookup is running (one at a time).
     */
    fun lookup(install: Boolean): UsbLookup? {
        if (!scanning.compareAndSet(false, true)) return null            // one scan at a time (mount event, screen opening, button)
        try {
            val own = ownDirs()
            val downloads = downloadDirs(own)
            val volumes = own.mapNotNull { (d, id) -> id?.let { VolumeFact(it, readOnly(d)) } }.distinctBy { it.id }
            var accepted: ActivationResult? = null; var refused: ActivationResult? = null
            val o = ActivationLookup.run(ActivationLookup.candidates(downloads, own), ActivationLookup.dirsToList(downloads, own), volumes, access = storageAccess()) { line ->
                val r = if (install) accept(Channel.MANUAL, line.toByteArray(Charsets.UTF_8)) else check(line)
                if (r is ActivationResult.Accepted) { if (accepted == null) accepted = r } else if (refused == null) refused = r
                verdictOf(r)
            }
            lastReport = ActivationLookupReport.lines(o.facts)
            lastFacts = o.facts
            return UsbLookup(o.facts, accepted ?: refused)
        } finally { scanning.set(false) }
    }

    /** The historical entry point: looks and INSTALLS the first accepted key. null = busy or no readable file. */
    fun scanFiles(): ActivationResult? = lookup(install = true)?.result

    /** Is an activation still useful on this TV (locked, grace period or trial key)? Then the USB key is watched at plug-in and the home says what it found. */
    fun usbWatchWanted(): Boolean = ready && required && (state() !is GateState.Activated || trial())

    /** Can the app read the shared Download folder? (all files access on API 30+, READ_EXTERNAL_STORAGE below 33.) */
    fun storageAccess(): StorageAccess = runCatching {
        val manager = android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
        val read = app.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        StoragePermission.decide(android.os.Build.VERSION.SDK_INT, manager, read)
    }.getOrDefault(StorageAccess.MISSING)

    /** Real path of the first readable drop folder (the app's own folder of a removable volume first). */
    fun ownPath(): String? = runCatching { ownDirs().let { l -> (l.firstOrNull { it.second != null } ?: l.firstOrNull())?.first?.path } }.getOrNull()

    /** Each mounted volume's drop folder with the names the app sees in it (names only), for the activation screen. */
    fun dropFolders(): List<DropFolder> = runCatching {
        ownDirs().map { (d, id) -> DropFolder(id, d.path, try { d.list()?.toList() } catch (e: SecurityException) { null }) }.sortedBy { it.volumeId == null }
    }.getOrDefault(emptyList())

    /** Ids of the removable volumes seen right now (for the initial folder of the system picker). */
    fun volumeIds(): List<String> = runCatching { ownDirs().mapNotNull { it.second }.distinct() }.getOrDefault(emptyList())

    private fun readOnly(d: File) = runCatching { Environment.getExternalStorageState(d) == Environment.MEDIA_MOUNTED_READ_ONLY }.getOrDefault(false)

    /** The app's own folder on every volume (id = volume id such as A379-E209; null for the primary storage). */
    private fun ownDirs(): List<Pair<File, String?>> =
        app.getExternalFilesDirs(null).filterNotNull().map { d ->
            val root = Regex("^/storage/([^/]+)/Android/").find(d.path)?.groupValues?.get(1)
            d to (if (root == null || root == "emulated" || root == "self") null else root)
        }

    private fun downloadDirs(own: List<Pair<File, String?>>): List<Pair<File, String?>> {
        val out = LinkedHashMap<String, Pair<File, String?>>()
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).let { out[it.path] = it to null }
        own.forEach { (d, id) -> d.path.substringBefore("/Android/", "").takeIf { it.isNotEmpty() }?.let { out.putIfAbsent("$it/Download", File(it, "Download") to id) } }
        runCatching { File("/storage").listFiles()?.filter { it.name != "emulated" && it.name != "self" }?.forEach { out.putIfAbsent("${it.path}/Download", File(it, "Download") to it.name) } }
        return out.values.toList()
    }

    /**
     * Writes the « demande d'appareil » (code + full fingerprint set) as `device-request.txt` in Download/CastBridge of the USB drive and of the internal storage, so the owner
     * can take it to his tool (`emettre --appareil device-request.txt`). Falls back to the app's own folders when the system refuses. Returns where it was written.
     */
    fun exportRequest(): List<String> {
        val text = requestText() + "\n"; val out = ArrayList<String>()
        val dirs = LinkedHashSet<File>()
        downloadDirs(ownDirs()).forEach { (d, _) -> dirs += File(d, "CastBridge") }
        app.getExternalFilesDirs(null).filterNotNull().forEach { dirs += it }
        for (d in dirs) { if (runCatching { d.mkdirs(); File(d, "device-request.txt").writeText(text); true }.getOrDefault(false)) out += File(d, "device-request.txt").path }
        return out
    }

    // ---- persistence (private files; the activation is verified AS OF the day it was installed: its install window is not a validity limit) ----
    // One line per activation: « installedAt<TAB>uptimeAtInstall<TAB>text » (old format « installedAt<TAB>text » still read: its uptime starts at the first start of this version), always ending with a newline. Written through [SafeFile] (temporary file in the same folder, fsync, `.bak` of the last good file, rename);
    // read with a fallback on the `.bak`. A failed write is LOGGED, the in-memory list stays the truth and the write is retried (init, next activation, daily prompt).
    private fun storedFile() = File(app.filesDir, "activations.txt")
    private fun parseEntries(text: String): List<Triple<Long, Long, String>>? {
        if (text.isNotEmpty() && !text.endsWith("\n")) return null                       // truncated last line
        val out = ArrayList<Triple<Long, Long, String>>()
        for (l in text.lines()) {
            if (l.isBlank()) continue
            val p = l.split('\t', limit = 3); val at = p.getOrNull(0)?.toLongOrNull() ?: return null
            when (p.size) {
                2 -> { if (p[1].isBlank()) return null; out += Triple(at, -1L, p[1]) }
                3 -> { val up = p[1].toLongOrNull() ?: return null; if (p[2].isBlank()) return null; out += Triple(at, up, p[2]) }
                else -> return null
            }
        }
        return out
    }
    private fun loadStored() {
        stored.clear()
        val f = storedFile()
        if (f.isFile || SafeFile.bak(f).isFile) {
            val r = SafeFile.read(f) { parseEntries(it) != null }
            if (r == null) Log.e(TAG, "activations.txt et sa copie .bak illisibles : aucune activation relue")
            else { if (r.fromBackup) Log.w(TAG, "activations.txt illisible : relu depuis activations.txt.bak"); stored += parseEntries(r.text).orEmpty(); if (r.fromBackup) unsaved = true }
            return
        }
        val legacy = runCatching { File(app.filesDir, "activation.txt").readLines() }.getOrNull() ?: return
        val at = legacy.getOrNull(0)?.toLongOrNull() ?: return; val text = legacy.getOrNull(1) ?: return
        stored += Triple(at, -1L, text)
    }
    private fun persist(text: String, installedAt: Long, uptimeAtInstall: Long) {
        synchronized(stored) { if (stored.any { it.third == text }) return; stored += Triple(installedAt, uptimeAtInstall, text); unsaved = true }
    }
    @Synchronized private fun flushIfUnsaved() {
        if (!unsaved) return
        val snapshot = synchronized(stored) { stored.toList() }
        try { SafeFile.write(storedFile(), snapshot.joinToString("") { "${it.first}\t${it.second}\t${it.third}\n" }) { parseEntries(it) != null }; unsaved = false }
        catch (e: Exception) { Log.e(TAG, "écriture d'activations.txt impossible (nouvel essai plus tard, l'activation reste valable jusqu'au redémarrage)", e) }
    }

    private fun reload() {
        for ((at, up, text) in synchronized(stored) { stored.toList() }) {
            val r = receiver().receive(Channel.MANUAL, text.toByteArray(Charsets.UTF_8), at)       // verified AS OF the day it was installed
            if (r is ActivationResult.Accepted) {
                installed.install(r.activation); remember(r.activation); clock.observe(wall(), r.activation.issuedAt)
                // an entry of the old format has no uptime: its running-time ceiling starts now (benefit of the doubt, once), and the line is rewritten with it
                val upAt = if (up >= 0) up else clock.uptimeNow().also { fixed ->
                    synchronized(stored) { val i = stored.indexOfFirst { it.third == text }; if (i >= 0) { stored[i] = Triple(at, fixed, text); unsaved = true } } }
                synchronized(uptimeAtInstall) { uptimeAtInstall[r.activation.signature] = upAt }
            }
        }
    }

    private fun clockFile() = File(app.filesDir, "clock.txt")
    private fun loadClock() {
        SafeFile.read(clockFile()) { TvClock.decode(it) != null }?.let { r -> TvClock.decode(r.text)?.let { clock.lastSeen = it.lastSeen; clock.floor = it.floor; clock.uptimeMs = it.uptimeMs } }
        clock.observe(wall()); saveClock()
    }
    /** Every 5 minutes of running: observes the clock (high-water mark and cumulative uptime) and writes `clock.txt`, so a reboot loses at most 5 minutes. */
    private fun startClockTimer() {
        if (clockTimer != null) return
        clockTimer = java.util.Timer("castbridge-clock", true).also { t ->
            t.schedule(object : java.util.TimerTask() { override fun run() { tick() } }, CLOCK_SAVE_MS, CLOCK_SAVE_MS)
        }
    }
    const val CLOCK_SAVE_MS = 5 * 60_000L
    /** One observation + save (the timer, and the service's `onDestroy` where it can be called). */
    fun tick() { clock.observe(wall()); saveClock() }
    fun saveClock() {
        try { SafeFile.write(clockFile(), clock.encode()) { TvClock.decode(it) != null }; clockUnsaved = false }
        catch (e: Exception) { clockUnsaved = true; Log.e(TAG, "écriture de clock.txt impossible (nouvel essai au prochain enregistrement)", e) }
    }

    /**
     * Grace period of the locked build: ABSOLUTE and never restarted ([FleetMigration]). It applies only to an install that existed BEFORE the lock was introduced: the package's
     * firstInstallTime survives « clear data » and an over-install, so neither can restart it; a fresh install, or an uninstall then an install, is later than the constant and is locked at once.
     * Nothing is stored for it (the old first_run files are ignored).
     */
    private fun loadGrace(clockFileExistedAtStart: Boolean) {
        val first = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime }.getOrDefault(Long.MAX_VALUE)        // unreadable => treated as a fresh install (locked)
        // no clock.txt at start while the install time predates the lock = uninstall, date wound back, reinstall: no grace
        existingInstall = FleetMigration.of(first, BuildConfig.LOCK_GRACE_START_MS, clockFileExistedAtStart).existingInstall
    }
}
