package castbridge.receiver

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import castbridge.core.parental.AppEnv
import castbridge.core.parental.Category
import castbridge.core.parental.ChildProfile
import castbridge.core.parental.KvStore
import castbridge.core.parental.ParentalApi
import castbridge.core.parental.ParentalEngine
import castbridge.core.parental.ParentalReports
import castbridge.core.parental.ReportOutbox
import castbridge.core.parental.ReportRecipients
import castbridge.core.parental.ReportSyncHost
import castbridge.core.parental.SupervisionInfo
import castbridge.core.parental.SupervisionState
import castbridge.core.parental.PinResult
import castbridge.core.parental.Rating
import castbridge.core.parental.RatingRule
import castbridge.core.parental.RuleKind
import castbridge.core.parental.UseKind
import castbridge.core.tv.LibraryItem
import castbridge.core.tv.NeedsForeground

/**
 * Parental control on the TV (docs/PARENTAL.md): glue between the pure logic of `core/parental` and Android. Everything stays on
 * the TV: nothing here talks to the server (no TvConnect call, no statistics). The parental PIN is stored hashed, never logged.
 *
 * Where the rules bite:
 *  - [lifecycle]: an activity of a blocked category (games, downloads, settings...) is closed at once and replaced by the lock screen;
 *  - [gatePlay]: the TV's player bridge refuses a video above the profile's age, outside the hours or beyond the daily time;
 *  - [guardTile] / [wrapMenu]: home tiles and menu entries of a blocked category;
 *  - [tick]: every 15 s, counts the time spent (video, games, downloads), warns 5 minutes before the end, then stops;
 *  - whole-TV supervision ([ForegroundWatcher] -> [onForeground]): another app in front is checked against the per-app rules, counted into
 *    the daily quota, and, when refused, the TV goes HOME and the lock screen comes in front ([enforceApp]);
 *  - reports for the parent's phone: queued in an outbox on the TV ([reports]) and pulled by the designated phone over Bluetooth (CBTP).
 * BACK and HOME are never intercepted; the lock screen always offers "Saisir le PIN parental" and "Retour à l'accueil".
 */
object ParentalHub {
    private const val PREFS = "castbridge_parental"
    const val PREFS_REPORTS = "castbridge_parental_reports"
    private const val TICK_MS = 15_000L

    /** SharedPreferences with synchronous writes: a lockout counter must survive a kill of the app. */
    private class PrefsKv(private val p: SharedPreferences) : KvStore {
        override fun get(key: String): String? = p.getString(key, null)
        override fun put(key: String, value: String?) { p.edit().apply { if (value == null) remove(key) else putString(key, value) }.commit() }
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var app: Context? = null
    @Volatile private var engineOrNull: ParentalEngine? = null
    @Volatile private var apiOrNull: ParentalApi? = null
    @Volatile private var top: Activity? = null
    private var lastTick = 0L
    @Volatile private var reportsOrNull: ParentalReports? = null
    @Volatile private var syncOrNull: ReportSyncHost? = null
    // whole-TV supervision (main thread only)
    private var fgPkg: String? = null
    private var lastApp = 0L
    private var lastSyncApps = 0L
    private val lastEnforce = HashMap<String, Long>()
    @Volatile private var superviseCache = false
    @Volatile private var superviseAt = 0L

    val engine: ParentalEngine get() = engineOrNull ?: error("ParentalHub.init")

    /** The HTTP routes (docs/PARENTAL.md), chained into the TV's API by TvService. */
    val api: ParentalApi get() = apiOrNull ?: error("ParentalHub.init")

    /** Reports for the parent's phone (docs/PARENTAL.md): designation, options, outbox. */
    val reports: ParentalReports get() = reportsOrNull ?: error("ParentalHub.init")

    /** TV side of the CBTP message of the Bluetooth link: the designated phone pulls its reports here. */
    val syncHost: ReportSyncHost get() = syncOrNull ?: error("ParentalHub.init")

    @Synchronized fun init(ctx: Context) {
        if (engineOrNull != null) return
        val c = ctx.applicationContext
        app = c
        engineOrNull = ParentalEngine(PrefsKv(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)))
        // reports and their signing keys live in their own preferences file (excluded from backups, see backup_rules.xml)
        val rkv = PrefsKv(c.getSharedPreferences(PREFS_REPORTS, Context.MODE_PRIVATE))
        val trusted = { addr: String -> TvService.running?.btTrusted(addr) == true }
        val tvName = { TvService.running?.tvName() ?: "CastBridge TV" }
        val recipients = ReportRecipients(rkv, trusted)
        val outbox = ReportOutbox(rkv)
        val rp = ParentalReports(rkv, engine, recipients, outbox, tvName)
        reportsOrNull = rp
        syncOrNull = ReportSyncHost(recipients, outbox, trusted, tvName)
        // Events reach the reports layer through the main thread, never under the engine's lock (the reports layer calls the engine: one lock order only).
        engine.onEvent = { ev -> main.post { runCatching { rp.onEvent(ev) } } }
        engine.supervisionProbe = { supervisionInfo() }
        apiOrNull = ParentalApi(engine, learnProfiles = { learnProfiles() }, installed = { AppCatalog.launcherApps(c) }, appEnv = { AppCatalog.env(c) },
            supervisionSetup = { setupMap() }, reports = rp,
            trustedPhones = { TvService.running?.trust?.list()?.map { it.address to it.name }.orEmpty() }) { }
        ForegroundWatcher.onChange = { pkg -> main.post { onForeground(pkg) } }
        ForegroundWatcher.start(c)
        lastTick = SystemClock.elapsedRealtime()
        main.postDelayed(ticker, TICK_MS)
    }

    private fun learnProfiles(): List<Triple<String, String, String?>> =
        runCatching { LearnHub.progress().profiles.map { Triple(it.id, it.name, it.level) } }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ categories of screens

    fun categoryOf(a: Activity): Category? = when (a) {
        is QuizActivity, is ChessActivity -> Category.GAMES
        is DownloadsActivity -> Category.DOWNLOADS
        is ServerActivity, is RemoteSetupActivity -> Category.SETTINGS
        else -> null
    }

    /** Category of a home tile (feature ids of PlayerActivity.tile), null = always open (library, Apprendre, help, receiving). */
    fun categoryOfFeature(feature: String): Category? = when (feature) {
        "quiz", "chess" -> Category.GAMES
        "downloads" -> Category.DOWNLOADS
        "internet" -> Category.INTERNET
        "admin" -> Category.ADMIN
        "remote", "usb", "bluetooth", "wifi_direct", "updates", "settings", "dev_options" -> Category.SETTINGS
        else -> null
    }

    /** Category of a menu line, from its French label (the MENU key lists, "Administration" dialog...). */
    fun categoryOfLabel(label: String): Category? {
        val l = label.lowercase()
        return when {
            "ssh" in l -> Category.SSH
            "internet" in l || "tester" in l -> Category.INTERNET
            "téléchargements" in l -> Category.DOWNLOADS
            "quiz" in l -> Category.GAMES
            "adresse de la page web" in l -> Category.ADMIN
            "bibliothèque" in l -> null
            l.startsWith("bluetooth") || l.startsWith("wi-fi direct") || l.startsWith("usb") || l.startsWith("stockage") ||
                "démarrer avec la tv" in l || "lecture à distance" in l || l.startsWith("mises à jour") || l.startsWith("confidentialité") ||
                l.startsWith("connexion au serveur") || "développeur" in l || "importer les vidéos" in l || "choisir un dossier" in l ||
                "où ranger" in l || "re-détecter" in l || "réglages de stockage" in l || "annuler l'import" in l || "rendre la tv visible" in l -> Category.SETTINGS
            else -> null
        }
    }

    // ------------------------------------------------------------------ enforcement

    val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) {
            top = a
            enforce(a)
        }
        override fun onActivityPaused(a: Activity) { if (top === a) top = null }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }

    /** Closes [a] and shows the lock screen when its category is refused. Navigation (home, lock screen, parental screen) is never touched. */
    private fun enforce(a: Activity) {
        if (engineOrNull == null || a is ParentalLockActivity || a is ParentalActivity) return
        val cat = categoryOf(a) ?: return
        val d = engine.check(cat)
        if (d.allowed) return
        engine.recordBlocked(cat.label, d.reason ?: "bloqué")
        val target = a.javaClass
        a.finish()
        ParentalLockActivity.show(a, d.reason ?: "Cet écran est protégé.", target)
    }

    /** A home tile: runs [action] unless its category is refused (then the lock screen explains and offers the PIN). */
    fun guardTile(a: Activity, feature: String, action: () -> Unit) {
        val cat = categoryOfFeature(feature)
        if (cat == null || engineOrNull == null) { action(); return }
        allow(a, cat, null, action)
    }

    /** Same for a screen that is not an activity (settings panel, MENU). True = allowed. */
    fun allow(a: Activity, cat: Category, then: Class<out Activity>? = null, action: (() -> Unit)? = null): Boolean {
        if (engineOrNull == null) { action?.invoke(); return true }
        val d = engine.check(cat)
        if (d.allowed) { action?.invoke(); return true }
        engine.recordBlocked(cat.label, d.reason ?: "bloqué")
        ParentalLockActivity.show(a, d.reason ?: "Cet écran est protégé.", then)
        return false
    }

    /** Menu entries: a blocked category opens the lock screen instead of its action. */
    fun wrapMenu(a: Activity, items: List<Pair<String, () -> Unit>>): List<Pair<String, () -> Unit>> {
        if (engineOrNull == null) return items
        return items.map { (label, act) ->
            val cat = categoryOfLabel(label)
            if (cat == null) label to act else label to { allow(a, cat, null, act); Unit }
        }
    }

    // ------------------------------------------------------------------ playback

    @Volatile private var grantedName: String? = null
    @Volatile private var grantedUntil = 0L

    /** The parent typed the PIN on the TV for this over-age video: it may start during the next minute. */
    fun grantPlay(name: String) { grantedName = name; grantedUntil = SystemClock.elapsedRealtime() + 60_000 }

    /**
     * Called by the TV's player bridge before any video starts (TV library, phone, playlist). Throws [NeedsForeground] with the
     * reason in French when playback is refused (the phone shows it, the TV library flashes it).
     */
    fun gatePlay(name: String) {
        if (engineOrNull == null) return
        val vol = volumeLabelOf(name)
        val locked = engine.needsPinToPlay(name, vol)
        val granted = grantedName == name && SystemClock.elapsedRealtime() < grantedUntil
        if (locked && !granted) throw NeedsForeground("Cette vidéo est verrouillée : le code parental est demandé sur la TV.")
        val d = if (locked) engine.checkTime(UseKind.PLAY) else engine.checkPlayback(name, vol)   // unlocked by the PIN: hours and daily time still apply
        if (!d.allowed) {
            engine.recordBlocked(name, d.reason ?: "bloquée")
            throw NeedsForeground(d.reason ?: "Lecture bloquée par le contrôle parental.")
        }
    }

    private fun volumeLabelOf(name: String): String? = runCatching {
        TvService.running?.server?.libraryItems()?.firstOrNull { it.name == name }?.volumeLabel
    }.getOrNull()

    /** Library filter for the TV screens: hides what the active profile may not see (when the parent chose "masquer"). */
    fun filterItems(items: List<LibraryItem>): List<LibraryItem> {
        if (engineOrNull == null) return items
        val f = engine.libraryFilter() ?: return items
        return items.filter { it.type != castbridge.core.tv.MediaType.VIDEO || f(it.name, it.volumeLabel) }
    }

    /** Would playing this item ask the PIN (lock mode)? */
    fun needsPin(i: LibraryItem): Boolean = engineOrNull != null && i.type == castbridge.core.tv.MediaType.VIDEO && engine.needsPinToPlay(i.name, i.volumeLabel)

    // ------------------------------------------------------------------ home

    fun kidHomeActive(): Boolean = engineOrNull?.activeProfile()?.kidMode == true

    /** Home tiles: in kid mode only Apprendre, allowed games, the library, help and the parental door. */
    fun filterHome(tools: List<HomeTool>): List<HomeTool> {
        if (engineOrNull == null) return tools
        val p = engine.activeProfile() ?: return tools
        if (!p.kidMode) return tools.filter { t -> categoryOfLabel(t.label)?.let { !castbridge.core.parental.ParentalRules.categoryBlocked(p, it) } ?: true }
        val keep = castbridge.core.parental.ParentalRules.kidHome(tools.map { it.label }, p).toSet()
        return tools.filter { it.label in keep }
    }

    /** The TV's connection code is not shown on screen while a child profile is active (it is the TV administrator's secret). */
    fun shownPin(pin: String): String = if (engineOrNull?.active() == true) "••••••" else pin

    /** Status line of the "Contrôle parental" home tile. */
    fun tileStatus(): String {
        val e = engineOrNull ?: return ""
        if (!e.hasPin()) return "À configurer"
        val p = e.config().active()
        return when {
            !e.config().enabled -> "Désactivé"
            e.sessionActive() -> "Déverrouillé ${e.sessionLeftSec() / 60 + 1} min"
            p != null -> "Actif · ${p.name}"
            else -> "Aucun profil actif"
        }
    }

    // ------------------------------------------------------------------ PIN dialogs

    /** Asks the parental PIN unless a parent session is open (then [then] runs at once). Wrong PINs use the persistent lockout. */
    fun authorize(a: Activity, title: String, then: () -> Unit) {
        val e = engine
        if (!e.hasPin() || e.sessionActive()) { then(); return }
        ParentalUi.pinDialog(a, title, "Code du parent (4 à 6 chiffres)", check = { pin -> pinError(e.verifyPin(pin)) }, onOk = { then() })
    }

    fun pinError(r: PinResult): String? = when (r) {
        PinResult.Ok -> null
        PinResult.NoPin -> "Aucun code parental n'est défini."
        is PinResult.Wrong -> "Code incorrect. Il reste ${r.attemptsLeft} essai(s) avant un blocage."
        is PinResult.Locked -> "Trop d'essais. Réessayez dans ${ParentalUi.duration(r.retryAfterSec)}."
    }

    /** "Classer cette vidéo" from the library actions: asks the PIN, then the rating; saves a rule on this file. */
    fun rateDialog(a: Activity, name: String, title: String) {
        if (!engine.hasPin()) { Toast.makeText(a, "Créez d'abord le code parental : tuile « Contrôle parental ».", Toast.LENGTH_LONG).show(); return }
        authorize(a, "Classer « $title »") {
            val cur = engine.config().let { castbridge.core.parental.ParentalRules.ratingOf(it, name) }
            val labels = Rating.values().map { it.label } + "Retirer le classement de cette vidéo"
            android.app.AlertDialog.Builder(a).setTitle("Classer « $title » (actuel : ${cur.label})")
                .setItems(labels.toTypedArray()) { _, i ->
                    engine.edit { c ->
                        val rest = c.rules.filterNot { it.kind == RuleKind.FILE && it.match.equals(name, true) }
                        c.copy(rules = if (i < Rating.values().size) rest + RatingRule(RuleKind.FILE, name.take(80), Rating.values()[i]) else rest)
                    }
                    Toast.makeText(a, if (i < Rating.values().size) "Classée : ${Rating.values()[i].label}" else "Classement retiré", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Annuler", null).show()
        }
    }

    // ------------------------------------------------------------------ usage meter

    private val ticker = object : Runnable {
        override fun run() {
            runCatching { tick() }
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTick).coerceIn(0, 2 * TICK_MS); lastTick = now
        val e = engineOrNull ?: return
        runCatching { reportsOrNull?.tick() }                       // daily / weekly summaries: a few comparisons
        runCatching { superviseTick(e, now) }
        if (e.activeProfile() == null) return
        val t = top
        // another app in front: it is counted by [accountApp]; CastBridge TV's own meter must not count it a second time
        val elsewhere = e.supervising() && fgPkg != null && fgPkg != app?.packageName
        val playing = !elsewhere && TvService.running?.playerBridge?.state()?.state == "playing"
        val kind = if (elsewhere) null else when {
            playing -> UseKind.PLAY
            t is QuizActivity || t is ChessActivity -> UseKind.GAMES
            t is DownloadsActivity -> UseKind.DOWNLOADS
            else -> null
        }
        // a category that became blocked while its screen is open (end of a parent session, profile switched)
        if (t != null && t !is ParentalLockActivity) enforce(t)
        val r = e.tick(kind, dt)
        r.warnMinutes?.let { m ->
            app?.let { Toast.makeText(it, "Il reste ${if (m <= 1) "1 minute" else "$m minutes"} d'écran. Pensez à terminer.", Toast.LENGTH_LONG).show() }
        }
        if (r.blockReason != null) {
            e.recordBlocked(kind?.label ?: "Temps d'écran", r.blockReason!!)
            if (playing) runCatching { TvService.running?.playerBridge?.stop() }
            val a = top ?: app
            if (a != null && t !is ParentalLockActivity) ParentalLockActivity.show(a, r.blockReason!!, null)
        }
    }

    // ------------------------------------------------------------------ whole-TV supervision (other apps)

    /** The parent switched the supervision on (cached 10 s: asked by the detector thread at every poll). */
    fun superviseWanted(): Boolean {
        val t = SystemClock.elapsedRealtime()
        if (t - superviseAt > 10_000) { superviseCache = engineOrNull?.appSettings()?.supervise == true; superviseAt = t }
        return superviseCache
    }

    /** The real state, measured now: never « active » unless a detector works AND the lock screen can be brought in front. */
    fun supervisionInfo(): SupervisionInfo {
        val c = app ?: return SupervisionInfo(SupervisionState.UNAVAILABLE)
        val usage = ForegroundWatcher.usageGranted(c)
        val a11y = ForegroundWatcher.accessibilityOn()
        val enforce = ForegroundWatcher.canEnforce(c)
        val fresh = ForegroundWatcher.fresh(c)
        val st = SupervisionState.compute(engineOrNull?.appSettings()?.supervise == true, usage, ForegroundWatcher.usageExists(c), a11y, fresh, enforce)
        val detail = when {
            st == SupervisionState.OFF || st == SupervisionState.ACTIVE -> null
            !usage && !a11y -> "accès aux données d'utilisation non accordé"
            !enforce -> "« Afficher par-dessus les autres applications » non autorisé"
            !fresh -> "détecteur arrêté"
            else -> null
        }
        return SupervisionInfo(st, if (usage) "usage" else if (a11y) "accessibility" else "none", detail = detail)
    }

    /** What the setup screen and the phone show to help the parent (no secret). */
    fun setupMap(): Map<String, Any?> {
        val c = app ?: return emptyMap()
        val pkg = c.packageName
        fun resolves(i: Intent) = runCatching { c.packageManager.resolveActivity(i, 0) != null }.getOrDefault(false)
        return linkedMapOf(
            "package" to pkg,
            "usageGranted" to ForegroundWatcher.usageGranted(c), "usageSettingsExists" to resolves(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)),
            "overlayGranted" to runCatching { android.provider.Settings.canDrawOverlays(c) }.getOrDefault(false),
            "accessibilityOn" to ForegroundWatcher.accessibilityOn(),
            "adbUsage" to "adb shell appops set $pkg GET_USAGE_STATS allow",
            "adbOverlay" to "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow",
        )
    }

    /** State, tampering alert, new apps and the time of the app in front: every 15 s while the supervision is wanted (main thread). */
    private fun superviseTick(e: ParentalEngine, now: Long) {
        val c = app ?: return
        if (!superviseWanted()) { fgPkg = null; return }
        e.reportSupervision(supervisionInfo())
        if (now - lastSyncApps > 300_000L && ForegroundWatcher.screenOn(c)) { lastSyncApps = now; e.syncInstalled(AppCatalog.launcherApps(c)) }
        accountApp(now)
    }

    /** The detector saw [pkg] come to the front (null = nothing known: screen off, supervision off). Main thread. */
    private fun onForeground(pkg: String?) {
        val e = engineOrNull ?: return
        val c = app ?: return
        accountApp(SystemClock.elapsedRealtime())               // time of the app that just left
        fgPkg = pkg
        lastApp = SystemClock.elapsedRealtime()
        if (pkg == null || !e.supervising()) return
        val env = AppCatalog.env(c)
        if (env.isEssential(pkg)) return
        val d = e.checkApp(pkg, env)
        if (!d.allowed) {
            val label = AppCatalog.label(c, pkg)
            e.denyApp(pkg, label, d)
            enforceApp(c, pkg, d.code, d.reason ?: "Cette application est protégée.")
        }
    }

    /** Counts the time since the last call on the app in front (if any, and if it is not CastBridge TV or the launcher). */
    private fun accountApp(now: Long) {
        val e = engineOrNull ?: return
        val c = app ?: return
        val pkg = fgPkg
        val dt = (now - lastApp).coerceIn(0, 2 * TICK_MS); lastApp = now
        if (pkg == null || dt == 0L || !e.supervising()) return
        val env: AppEnv = AppCatalog.env(c)
        if (env.isEssential(pkg)) return
        val label = AppCatalog.label(c, pkg)
        val r = e.appTick(pkg, label, dt, env)
        r.warnMinutes?.let { m -> Toast.makeText(c, "Il reste ${if (m <= 1) "1 minute" else "$m minutes"} sur cette application. Pensez à terminer.", Toast.LENGTH_LONG).show() }
        if (r.blockReason != null) enforceApp(c, pkg, e.checkApp(pkg, env).code, r.blockReason!!)
    }

    /**
     * Sends the TV HOME and brings the lock screen in front. Never traps: the lock screen offers the PIN and « Retour à l'accueil », BACK and
     * HOME work. Needs « Afficher par-dessus les autres apps » (or the accessibility service) on Android 10+: without them the TV cannot do it
     * and the supervision says so ([supervisionInfo]).
     */
    private fun enforceApp(c: Context, pkg: String, code: String, reason: String) {
        val t = SystemClock.elapsedRealtime()
        if (t - (lastEnforce[pkg] ?: 0L) < 1_500L) return
        lastEnforce[pkg] = t
        val a11y = RemoteAccessibilityService.instance
        val homed = a11y?.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) == true
        if (!homed) runCatching { c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        ParentalLockActivity.showForApp(c, reason, pkg, code)
    }

    /** Learn profiles for the import screen. */
    fun learnStudents() = learnProfiles()

    fun newProfileId(existing: List<ChildProfile>): String = (1..99).map { "c$it" }.first { id -> existing.none { it.id == id } }
}
