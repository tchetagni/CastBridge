package castbridge.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import castbridge.core.owner.*
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
    private fun remember(a: Activation) { synchronized(everyActivation) { if (everyActivation.none { it.signature == a.signature }) everyActivation += a } }
    // monotonic time of the TV (counts through sleep, never goes back during a boot): a wall clock wound back cannot freeze the usage ceilings (audit finding: clock rollback)
    private val clock = TvClock(mono = android.os.SystemClock::elapsedRealtime)
    private lateinit var fp: Fingerprints
    lateinit var deviceCode: String; private set
    private var existingInstall = false
    /** Activation lines (installedAt, text) as last known: the in-memory truth, rewritten whole by [flushIfUnsaved]; [unsaved] = the last write failed and is retried. */
    private val stored = ArrayList<Pair<Long, String>>()
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
        loadClock(); loadGrace()
        loadStored(); reload(); flushIfUnsaved()
        ready = true
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
    fun requestText(): String = OwnerFrames.deviceInfo(deviceCode, fp)

    // ---- state ----
    private fun wall() = System.currentTimeMillis()
    fun now(): Long = clock.now(wall())
    private fun access(): TvAccess = TvGate.evaluate(installed.all(), emptyList(), now(), if (ready) RentalHub.statuses(app) else emptyList(),
        clockDoubt = castbridge.core.lots.RentalEngine.judge(clock, wall()).doubt, monotonicNowMs = clock.monotonicNow())
    private val migration: FleetMigration get() = FleetMigration(existingInstall, BuildConfig.LOCK_GRACE_START_MS)

    fun state(): GateState { if (!ready) init(app); return FeatureGate.state(requirement, access(), now(), migration) }
    fun locked(): Boolean = state() is GateState.Locked

    /** A TRIAL key only (with the activation requirement on): copy / move, downloads, the library and every game but the Sudoku are closed ([castbridge.core.owner.TrialPolicy]). */
    fun trial(): Boolean = BuildConfig.REQUIRE_ACTIVATION && ready && access().trial

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
    private fun receiver() = ActivationReceiver(ring, trusted, fp, Subject.TV)

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
            val text = String(payload, Charsets.UTF_8).removePrefix("﻿").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            persist(text, t)
            flushIfUnsaved()
            runCatching { RentalHub.onActivation(app, r.activation) }          // the keys of its rentals go into the rental safe
        }
        return r
    }

    /** Looks for the `activation` file of the USB drive (Download/CastBridge/activation, Download/activation) and of the internal Download folder. */
    fun scanFiles(): ActivationResult? {
        for (f in candidateFiles()) {
            val bytes = runCatching { if (f.isFile && f.length() in 1..16_384) f.readBytes() else null }.getOrNull() ?: continue
            // one line (CRLF, BOM, final newline tolerated); full token, grouped text or compact key: one verification path
            val line = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: continue
            val r = accept(Channel.MANUAL, line.toByteArray(Charsets.UTF_8))
            if (r is ActivationResult.Accepted) return r
            return r                      // a file was there but refused: say why
        }
        return null
    }

    /**
     * Writes the « demande d'appareil » (code + full fingerprint set) as `device-request.txt` in Download/CastBridge of the USB drive and of the internal storage, so the owner
     * can take it to his tool (`emettre --appareil device-request.txt`). Falls back to the app's own folders when the system refuses. Returns where it was written.
     */
    fun exportRequest(): List<String> {
        val text = requestText() + "\n"; val out = ArrayList<String>()
        val dirs = LinkedHashSet<File>()
        candidateFiles().mapNotNull { it.parentFile }.forEach { d -> dirs += if (d.name == "CastBridge") d else File(d, "CastBridge") }
        app.getExternalFilesDirs(null).filterNotNull().forEach { dirs += it }
        for (d in dirs) { if (runCatching { d.mkdirs(); File(d, "device-request.txt").writeText(text); true }.getOrDefault(false)) out += File(d, "device-request.txt").path }
        return out
    }

    private fun candidateFiles(): List<File> {
        val roots = LinkedHashSet<File>()
        roots += Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        app.getExternalFilesDirs(null).forEach { d -> d?.path?.substringBefore("/Android/", "")?.takeIf { it.isNotEmpty() }?.let { roots += File(it, "Download") } }
        runCatching { File("/storage").listFiles()?.forEach { roots += File(it, "Download") } }
        return roots.flatMap { listOf(File(it, "CastBridge/${Activation.FILE_NAME}"), File(it, Activation.FILE_NAME)) }
    }

    // ---- persistence (private files; the activation is verified AS OF the day it was installed: its install window is not a validity limit) ----
    // One line per activation: « installedAt<TAB>text », always ending with a newline. Written through [SafeFile] (temporary file in the same folder, fsync, `.bak` of the last good file, rename);
    // read with a fallback on the `.bak`. A failed write is LOGGED, the in-memory list stays the truth and the write is retried (init, next activation, daily prompt).
    private fun storedFile() = File(app.filesDir, "activations.txt")
    private fun parseEntries(text: String): List<Pair<Long, String>>? {
        if (text.isNotEmpty() && !text.endsWith("\n")) return null                       // truncated last line
        val out = ArrayList<Pair<Long, String>>()
        for (l in text.lines()) {
            if (l.isBlank()) continue
            val p = l.split('\t', limit = 2); val at = p.getOrNull(0)?.toLongOrNull() ?: return null
            if (p.size != 2 || p[1].isBlank()) return null
            out += at to p[1]
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
        stored += at to text
    }
    private fun persist(text: String, installedAt: Long) {
        synchronized(stored) { if (stored.any { it.second == text }) return; stored += installedAt to text; unsaved = true }
    }
    @Synchronized private fun flushIfUnsaved() {
        if (!unsaved) return
        val snapshot = synchronized(stored) { stored.toList() }
        try { SafeFile.write(storedFile(), snapshot.joinToString("") { "${it.first}\t${it.second}\n" }) { parseEntries(it) != null }; unsaved = false }
        catch (e: Exception) { Log.e(TAG, "écriture d'activations.txt impossible (nouvel essai plus tard, l'activation reste valable jusqu'au redémarrage)", e) }
    }

    private fun reload() {
        for ((at, text) in synchronized(stored) { stored.toList() }) {
            val r = receiver().receive(Channel.MANUAL, text.toByteArray(Charsets.UTF_8), at)       // verified AS OF the day it was installed
            if (r is ActivationResult.Accepted) { installed.install(r.activation); remember(r.activation); clock.observe(wall(), r.activation.issuedAt) }
        }
    }

    private fun clockFile() = File(app.filesDir, "clock.txt")
    private fun parseClock(t: String): Pair<Long, Long>? = t.trim().split(' ').takeIf { it.size == 2 }?.let { p -> (p[0].toLongOrNull() ?: return null) to (p[1].toLongOrNull() ?: return null) }
    private fun loadClock() {
        SafeFile.read(clockFile()) { parseClock(it) != null }?.let { r -> parseClock(r.text)?.let { clock.lastSeen = it.first; clock.floor = it.second } }
        clock.observe(wall()); saveClock()
    }
    fun saveClock() {
        try { SafeFile.write(clockFile(), "${clock.lastSeen} ${clock.floor}") { parseClock(it) != null }; clockUnsaved = false }
        catch (e: Exception) { clockUnsaved = true; Log.e(TAG, "écriture de clock.txt impossible (nouvel essai au prochain enregistrement)", e) }
    }

    /**
     * Grace period of the locked build: ABSOLUTE and never restarted ([FleetMigration]). It applies only to an install that existed BEFORE the lock was introduced: the package's
     * firstInstallTime survives « clear data » and an over-install, so neither can restart it; a fresh install, or an uninstall then an install, is later than the constant and is locked at once.
     * Nothing is stored for it (the old first_run files are ignored).
     */
    private fun loadGrace() {
        val first = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime }.getOrDefault(Long.MAX_VALUE)        // unreadable => treated as a fresh install (locked)
        existingInstall = FleetMigration.of(first, BuildConfig.LOCK_GRACE_START_MS).existingInstall
    }
}
