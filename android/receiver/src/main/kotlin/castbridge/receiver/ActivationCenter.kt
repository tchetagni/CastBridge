package castbridge.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
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
    private val clock = TvClock()
    private lateinit var fp: Fingerprints
    lateinit var deviceCode: String; private set
    private var firstRunAt = 0L
    private var existingInstall = false
    private var trusted: List<TrustedKey> = emptyList()
    private var ring = KeyRing(emptyList())

    val required: Boolean get() = BuildConfig.REQUIRE_ACTIVATION
    private val requirement get() = ActivationRequirement(required, BuildConfig.ACTIVATION_GRACE_DAYS)

    @Synchronized fun init(ctx: Context) {
        if (ready) return
        app = ctx.applicationContext
        trusted = BuildConfig.TRUSTED_KEYS.split('\n').mapNotNull(::parseKey)
        ring = KeyRing(trusted)
        fp = DeviceIdentity.fingerprints(rawFactors())
        deviceCode = DeviceCode.of(fp)
        loadClock(); loadFirstRun()
        reload()
        ready = true
    }

    // ---- hardware identity (the factors of docs/ACTIVATION-FORMAT.md § 1; each one only if readable and meaningful) ----
    private fun read(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    private fun getprop(name: String): String? = runCatching {
        Runtime.getRuntime().exec(arrayOf("getprop", name)).inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun rawFactors() = RawFactors(
        flashSerial = read("/sys/block/mmcblk0/device/serial"), flashCid = read("/sys/block/mmcblk0/device/cid"),
        ethernetMac = read("/sys/class/net/eth0/address"),
        wifiMac = read("/sys/class/net/wlan0/address"), wifiSysfsPath = runCatching { File("/sys/class/net/wlan0/device").canonicalPath }.getOrNull(),
        systemSerial = getprop("ro.serialno"), bluetoothAddress = null,
    )

    /** The « demande d'appareil » (code + full fingerprint set): what the owner's tools need for a complete activation. */
    fun requestText(): String = OwnerFrames.deviceInfo(deviceCode, fp)

    private fun parseKey(line: String): TrustedKey? = runCatching {
        val kv = line.trim().split(' ').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val scopes = kv["scopes"]?.split(',')?.mapNotNull { runCatching { KeyScope.valueOf(it) }.getOrNull() }?.toSet() ?: KeyScope.ALL
        TrustedKey(kv.getValue("kid"), kv.getValue("pub"), scopes)
    }.getOrNull()

    // ---- state ----
    private fun wall() = System.currentTimeMillis()
    private fun now(): Long = clock.now(wall())
    private fun access(): TvAccess = TvGate.evaluate(installed.all(), emptyList(), now(), if (ready) RentalHub.statuses(app) else emptyList())
    private val migration: FleetMigration get() = FleetMigration(existingInstall, firstRunAt)

    fun state(): GateState { if (!ready) init(app); return FeatureGate.state(requirement, access(), now(), migration) }
    fun locked(): Boolean = state() is GateState.Locked
    fun label(): String = access().label
    fun graceUntil(): Long? = migration.graceUntil(requirement)

    /** In the grace period the activation screen is offered once a day (never again the same day). */
    @Synchronized fun dailyPrompt(): Boolean {
        val f = File(app.filesDir, "grace_prompt.txt"); val today = wall() / 86_400_000L
        if (read(f.path)?.toLongOrNull() == today) return false
        runCatching { f.writeText(today.toString()) }; return true
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
    private fun stored() = File(app.filesDir, "activations.txt")          // one line per activation: « installedAt<TAB>text » (the old single-activation file is still read)
    private fun entries(): List<Pair<Long, String>> {
        val f = stored()
        if (f.isFile) return runCatching { f.readLines() }.getOrDefault(emptyList()).mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { p -> p[0].toLongOrNull()?.let { it to p[1] } } }
        val legacy = runCatching { File(app.filesDir, "activation.txt").readLines() }.getOrNull() ?: return emptyList()
        val at = legacy.getOrNull(0)?.toLongOrNull() ?: return emptyList(); val text = legacy.getOrNull(1) ?: return emptyList()
        return listOf(at to text)
    }
    private fun persist(text: String, installedAt: Long) {
        val old = entries(); if (old.any { it.second == text }) return
        runCatching { stored().writeText((old + (installedAt to text)).joinToString("") { "${it.first}\t${it.second}\n" }) }
    }

    private fun reload() {
        for ((at, text) in entries()) {
            val r = receiver().receive(Channel.MANUAL, text.toByteArray(Charsets.UTF_8), at)       // verified AS OF the day it was installed
            if (r is ActivationResult.Accepted) { installed.install(r.activation); remember(r.activation); clock.observe(wall(), r.activation.issuedAt) }
        }
    }

    private fun loadClock() { read(File(app.filesDir, "clock.txt").path)?.split(' ')?.let { if (it.size == 2) { clock.lastSeen = it[0].toLongOrNull() ?: 0; clock.floor = it[1].toLongOrNull() ?: 0 } }; clock.observe(wall()); saveClock() }
    fun saveClock() { runCatching { File(app.filesDir, "clock.txt").writeText("${clock.lastSeen} ${clock.floor}") } }

    private fun loadFirstRun() {
        val f = File(app.filesDir, "first_run.txt")
        val saved = read(f.path)?.toLongOrNull()
        if (saved != null) { firstRunAt = saved; existingInstall = File(app.filesDir, "first_run_existing").exists(); return }
        firstRunAt = wall()
        // an UPDATE of an install that already existed (the owner's TV, beta testers) gets the grace period; a fresh install is locked at once
        val pi = app.packageManager.getPackageInfo(app.packageName, 0)
        existingInstall = pi.lastUpdateTime > pi.firstInstallTime + 60_000
        runCatching { f.writeText(firstRunAt.toString()); if (existingInstall) File(app.filesDir, "first_run_existing").writeText("1") }
    }
}
