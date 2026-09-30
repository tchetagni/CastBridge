package castbridge.core.connect

import castbridge.core.device.DeviceClient
import castbridge.core.device.DeviceFacts
import castbridge.core.device.DeviceReport
import castbridge.core.net.HttpLite
import castbridge.core.quiz.QuizSync
import castbridge.core.telemetry.Consent
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.Telemetry
import castbridge.core.telemetry.TelemetryUploader
import castbridge.core.update.UpdateClient
import castbridge.core.update.UpdateManifest
import castbridge.core.update.UpdateSchedule
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The app's link with the CastBridge server, pure logic shared by the TV and the phone (the apps only give it Android
 * facts, storage and an installer). Nothing leaves the device before the information screen was answered
 * ([ConnectState.needsConsent]). Then, from [tick] (every minute, on one background thread):
 *
 * - register at the first contact, heartbeat every 15 min (or what the server asks), apply its directives (check for an
 *   update now, device blocked = no update and no online quiz, beta channel);
 * - send the crashes recorded at the previous run;
 * - send the usage events (only those allowed by the consent: [Telemetry]) every 15 min;
 * - check for updates at start-up then every 12 h ([UpdateSchedule]), verify the signed manifest, download (resumable),
 *   verify SHA-256, hand the file to the app's installer;
 * - refresh the quiz questions once a day (TV).
 *
 * Every call goes through [Routes]: the device's network first, then the phone's Bluetooth gateway (TV).
 */
class ServerLink(
    val app: String,
    val installed: Installed,
    val state: ConnectState,
    private val facts: () -> DeviceFacts,
    private val salt: String,
    val routes: Routes,
    val queue: EventQueue,
    val crashes: CrashStore,
    private val keys: List<String>,
    private val hooks: Hooks,
    private val quiz: QuizSync? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val schedule: UpdateSchedule = UpdateSchedule(deviceSeed = state.installId.hashCode().toLong()),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    data class Installed(val versionCode: Int, val versionName: String?, val supportedAbis: List<String>, val sdk: Int)

    interface Hooks {
        /** Folder for an APK of [size] bytes (TV: storage policy, USB first; phone: app storage), or null if no room. */
        fun downloadDir(size: Long): File?
        /**
         * A verified APK is ready. Start installing it (silently if Android allows, else with the system confirmation) and
         * report through [installFailed] / the next start; return false to postpone (e.g. a video is playing).
         */
        fun installReady(apk: File, m: UpdateManifest, mandatory: Boolean, userAsked: Boolean): Boolean
        /** Something shown on a screen changed (status, progress). Called on the link's thread. */
        fun changed() {}
    }

    enum class Phase { IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, INSTALLING, FAILED, BLOCKED }

    data class UpdateStatus(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val manifest: UpdateManifest? = null,
        val mandatory: Boolean = false,
        val done: Long = 0,
        val total: Long = 0,
        val file: File? = null,
    )

    @Volatile var update = UpdateStatus(); private set

    /** Usage events, filtered by the consent in force at each event. */
    val telemetry = Telemetry(app, installed.versionCode, queue, consent = { state.effectiveConsent }, clock = clock)

    private val lock = Any()
    @Volatile private var forcedCheck = false
    /** Version whose installation the user cancelled: not offered again by itself (the "Installer" button still works). */
    @Volatile private var declinedVersion = 0
    private var lastProgressAt = 0L

    private fun http(proxy: Proxy?) = HttpLite(proxy, connectTimeoutMs = 10_000, readTimeoutMs = 30_000, userAgent = "CastBridge-$app/${installed.versionCode}")

    /** The facts of the app, with the consent and channel known here. */
    private fun report(): DeviceReport {
        val f = facts()
        val wrapped = object : DeviceFacts by f {
            override val installId = state.installId
            override val channel = state.channel
            override val consent: Consent = state.effectiveConsent
            override val consentVersion = if (state.needsConsent) null else state.consentVersion
        }
        return DeviceReport.collect(wrapped, salt)
    }

    // ------------------------------------------------------------------ consent and rights

    /** Choice on the information screen (or later in the settings). Withdrawing drops the usage events not sent yet. */
    fun setConsent(usage: Boolean) = synchronized(lock) {
        state.setConsent(if (usage) Consent.USAGE else Consent.ESSENTIAL, clock())
        if (!usage) queue.removeIf { line -> ESSENTIAL_EVENT.none { line.contains("\"name\":\"$it\"") } }
        state.lastContactAt = 0                          // tell the server at once (next tick)
        hooks.changed()
    }

    /** Right of access: what the server holds about this device (JSON), after registering if needed. */
    @Throws(IOException::class)
    fun myData(): String = synchronized(lock) {
        if (state.needsConsent) throw IOException("Répondez d'abord à l'écran d'information")
        if (state.deviceToken == null) contact()
        routes.call { p -> DeviceClient(state.baseUrl, state, http = http(p)).myData() }
    }

    /**
     * Right to erasure: the server deletes the device and all its data; locally the event queue, the crash reports and the
     * identity go too, and the information screen will be shown again before anything is sent.
     */
    @Throws(IOException::class)
    fun eraseMyData() = synchronized(lock) {
        if (state.deviceToken != null) routes.call { p -> DeviceClient(state.baseUrl, state, http = http(p)).eraseMe() }
        queue.clear(); crashes.clear(); state.resetIdentity()
        hooks.changed()
    }

    // ------------------------------------------------------------------ periodic work

    /** Once per process start, before the first [tick]: result of an update installed meanwhile. */
    fun onStartup() = synchronized(lock) {
        val p = state.pendingInstall ?: return@synchronized
        if (installed.versionCode >= p.second) {
            telemetry.track("update_install", mapOf("from" to p.first, "to" to p.second, "ok" to true))
            state.pendingInstall = null
            state.lastContactAt = 0                      // heartbeat right after an update
        }
    }

    /** Everything that is due. [startup]: first tick of the process (contact and update check at once). */
    fun tick(startup: Boolean = false) = synchronized(lock) {
        if (state.needsConsent) return@synchronized
        val now = clock()
        val period = state.heartbeatSeconds * 1000L
        val retry = if (state.lastContactOk) period else minOf(period, 2 * 60_000L)
        if (startup || state.lastContactAt == 0L || now - maxOf(state.lastContactAt, state.lastAttemptAt) >= retry || now < state.lastContactAt) contact()
        if (state.lastContactOk) {
            sendCrashes()
            if (TelemetryUploader.shouldFlush(state.lastFlushAt, now, queue.size()) || (startup && queue.size() > 0)) flush()
        }
        val trigger = when {
            forcedCheck -> UpdateSchedule.Trigger.FORCED
            startup -> UpdateSchedule.Trigger.STARTUP
            else -> UpdateSchedule.Trigger.TIMER
        }
        val u = update
        if (u.phase == Phase.READY && u.file != null && u.manifest != null && u.manifest.versionCode != declinedVersion) offerInstall(userAsked = false)
        else checkUpdate(trigger)
        if (quiz != null) {
            val due = state.quizSyncedAt == 0L || now - state.quizSyncedAt >= QUIZ_PERIOD_MS || now < state.quizSyncedAt
            if (due && now - state.quizAttemptAt >= QUIZ_RETRY_MS) syncQuiz()
        }
    }

    /** Heartbeat (registers first if needed); true if the server answered. */
    fun contact(): Boolean = synchronized(lock) {
        if (state.needsConsent) return false
        val now = clock()
        state.lastAttemptAt = now
        return try {
            val r = report()
            val d = routes.call { p -> DeviceClient(state.baseUrl, state, http = http(p)).heartbeat(r) }
            state.lastContactAt = now; state.lastContactOk = true; state.lastContactVia = routes.lastVia?.key
            state.lastContactMessage = "Connecté" + if (routes.lastVia == Routes.Via.GATEWAY) " (via la passerelle Bluetooth du téléphone)" else ""
            val wasBlocked = state.blocked
            state.blocked = d.blocked
            state.serverChannel = d.channel
            state.heartbeatSeconds = d.heartbeatSeconds
            if (d.checkUpdate) forcedCheck = true
            if (d.blocked) update = UpdateStatus(Phase.BLOCKED, "Appareil bloqué par l'administrateur : pas de mise à jour ni de quiz en ligne")
            else if (wasBlocked && update.phase == Phase.BLOCKED) update = UpdateStatus()
            true
        } catch (e: IOException) {
            state.lastContactOk = false
            state.lastContactMessage = "Serveur injoignable : " + Scrub.text(e.message ?: e.javaClass.simpleName).take(160)
            false
        } finally {
            hooks.changed()
        }
    }

    private fun sendCrashes() {
        for (c in crashes.pending()) {
            try {
                val r = report()
                routes.call { p -> DeviceClient(state.baseUrl, state, http = http(p)).crash(r, c.message, c.detail) }
                crashes.remove(c)
            } catch (e: DeviceClient.ServerError) {
                if (e.code in 400..499 && e.code != 401 && e.code != 429) crashes.remove(c) else return
            } catch (e: IOException) { return }
        }
    }

    /** Sends the queued events; returns a short French status. */
    fun flush(): String = synchronized(lock) {
        val token = state.deviceToken ?: return "pas encore enregistré"
        val res = try {
            routes.call({ it is TelemetryUploader.Result.Failed && it.reason.startsWith("serveur injoignable") }) { p ->
                TelemetryUploader(state.baseUrl, http = http(p)).flush(queue, token, app, installed.versionCode)
            }
        } catch (e: IOException) { TelemetryUploader.Result.Failed(e.message ?: "erreur réseau") }
        return when (res) {
            is TelemetryUploader.Result.Sent -> { state.lastFlushAt = clock(); "${res.accepted} événement(s) envoyé(s)" }
            TelemetryUploader.Result.NeedsRegistration -> { state.deviceToken = null; if (contact()) flush() else "enregistrement impossible" }
            is TelemetryUploader.Result.Failed -> res.reason
        }
    }

    // ------------------------------------------------------------------ updates

    /** "Vérifier maintenant" (user) or a check triggered by the timer / the server. */
    fun checkUpdate(trigger: UpdateSchedule.Trigger): UpdateStatus = synchronized(lock) {
        if (state.needsConsent) return update
        if (state.blocked) { update = UpdateStatus(Phase.BLOCKED, "Appareil bloqué par l'administrateur : pas de mise à jour"); hooks.changed(); return update }
        val now = clock()
        if (!schedule.isDue(state.updateSchedule, now, trigger)) return update
        if (trigger == UpdateSchedule.Trigger.FORCED) forcedCheck = false
        update = UpdateStatus(Phase.CHECKING, "Recherche d'une mise à jour…"); hooks.changed()
        val me = UpdateClient.Installed(installed.versionCode, installed.supportedAbis, installed.sdk, state.channel, state.deviceId, state.deviceToken)
        val r = routes.call({ it is UpdateClient.Check.Failed && it.network }) { p ->
            UpdateClient(state.baseUrl, app, keys, http = http(p), sleep = sleep).check(me)
        }
        when (r) {
            UpdateClient.Check.UpToDate -> {
                state.updateSchedule = schedule.onSuccess(state.updateSchedule, now)
                update = UpdateStatus(Phase.UP_TO_DATE, "À jour (version ${installed.versionName ?: installed.versionCode})")
            }
            UpdateClient.Check.Blocked -> {
                state.updateSchedule = schedule.onSuccess(state.updateSchedule, now)
                state.blocked = true
                update = UpdateStatus(Phase.BLOCKED, "Appareil bloqué par l'administrateur : pas de mise à jour")
            }
            is UpdateClient.Check.Failed -> {
                state.updateSchedule = schedule.onFailure(state.updateSchedule, now)
                update = UpdateStatus(Phase.FAILED, r.reason)
            }
            is UpdateClient.Check.Available -> {
                state.updateSchedule = schedule.onSuccess(state.updateSchedule, now)
                if (!ServerUrl.allowedDownload(r.manifest.url)) update = UpdateStatus(Phase.FAILED, "Lien de téléchargement refusé (HTTPS obligatoire)")
                else download(r.manifest, r.mandatory, trigger == UpdateSchedule.Trigger.USER)
            }
        }
        state.lastUpdateMessage = update.message
        hooks.changed()
        return update
    }

    private fun download(m: UpdateManifest, mandatory: Boolean, userAsked: Boolean) {
        val name = "version ${m.versionName} (${m.versionCode})"
        val dir = hooks.downloadDir(m.size)
        if (dir == null) { update = UpdateStatus(Phase.FAILED, "Pas assez de place pour télécharger la $name", m, mandatory); return }
        update = UpdateStatus(Phase.DOWNLOADING, "Téléchargement de la $name…", m, mandatory, 0, m.size); hooks.changed()
        val file = try {
            routes.call { p ->
                UpdateClient(state.baseUrl, app, keys, http = http(p), sleep = sleep).download(m, dir, maxAttempts = 3, progress = { done, total ->
                    val now = clock()
                    if (now - lastProgressAt >= 1000 || done == total) {
                        lastProgressAt = now
                        update = update.copy(done = done, total = total); hooks.changed()
                    }
                })
            }
        } catch (e: IOException) {
            update = UpdateStatus(Phase.FAILED, "Téléchargement interrompu (reprise à la prochaine vérification) : ${Scrub.text(e.message ?: "")}".take(200), m, mandatory)
            state.updateSchedule = schedule.onFailure(state.updateSchedule, clock())
            return
        }
        // the file was checked (size + SHA-256 of the signed manifest) by UpdateClient.download
        update = UpdateStatus(Phase.READY, "Mise à jour prête : $name", m, mandatory, m.size, m.size, file)
        offerInstall(userAsked)
    }

    /** "Installer" (user) or automatic, once the verified APK is there. */
    fun offerInstall(userAsked: Boolean): UpdateStatus = synchronized(lock) {
        val u = update
        val m = u.manifest ?: return u
        val f = u.file?.takeIf { it.isFile } ?: run { update = UpdateStatus(); return update }
        if (u.phase == Phase.INSTALLING && !userAsked) return u
        if (userAsked) declinedVersion = 0
        if (hooks.installReady(f, m, u.mandatory, userAsked)) {
            state.pendingInstall = installed.versionCode to m.versionCode
            update = u.copy(phase = Phase.INSTALLING, message = "Installation de la version ${m.versionName}…")
        } else if (u.phase != Phase.READY) update = u.copy(phase = Phase.READY)
        hooks.changed()
        return update
    }

    /** The installer reported a failure (cancelled by the user, refused by Android…). */
    fun installFailed(error: String) = synchronized(lock) {
        val u = update
        val m = u.manifest
        telemetry.track("update_install", mapOf("from" to installed.versionCode, "to" to (m?.versionCode ?: 0), "ok" to false, "error" to error))
        state.pendingInstall = null
        declinedVersion = m?.versionCode ?: 0
        update = u.copy(phase = if (u.file?.isFile == true) Phase.READY else Phase.FAILED, message = "Installation non faite : $error")
        hooks.changed()
    }

    // ------------------------------------------------------------------ quiz (TV)

    /** Refreshes the quiz questions from the server ("Mettre à jour les questions", or daily); French status. */
    fun syncQuiz(): String = synchronized(lock) {
        val q = quiz ?: return "indisponible"
        if (state.needsConsent) return "Répondez d'abord à l'écran d'information"
        if (state.blocked) return "Appareil bloqué : questions en ligne désactivées".also { state.quizMessage = it }
        state.quizAttemptAt = clock()
        val reset = state.takeQuizReset()
        val msg = try {
            when (val r = routes.call { p -> q.sync(state.baseUrl, http(p), state.deviceToken, state.deviceId, reset) }) {
                is QuizSync.Result.Updated -> { state.quizSyncedAt = clock()
                    if (r.changed == 0 && r.deleted == 0) "Questions déjà à jour" else "Questions à jour : ${r.total} du serveur (${r.changed} reçue(s), ${r.deleted} retirée(s))" }
                QuizSync.Result.NotModified -> { state.quizSyncedAt = clock(); "Questions déjà à jour" }
                is QuizSync.Result.Failed -> "Questions non mises à jour : ${r.reason}"
            }
        } catch (e: DeviceClient.ServerError) {
            if (e.code == 403) { state.blocked = true; "Appareil bloqué : questions en ligne désactivées" } else "Serveur : ${e.message}"
        } catch (e: IOException) { "Serveur injoignable : questions embarquées utilisées" }
        state.quizMessage = msg
        hooks.changed()
        return msg
    }

    companion object {
        const val QUIZ_PERIOD_MS = 24 * 3_600_000L
        const val QUIZ_RETRY_MS = 30 * 60_000L
        private val ESSENTIAL_EVENT = castbridge.core.telemetry.EventCatalog.ESSENTIAL
    }
}

/** Runs a [ServerLink] on one background thread: start-up work, then [ServerLink.tick] every [tickMs]. */
class ConnectAgent(val link: ServerLink, private val tickMs: Long = 60_000) {
    private val ex: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-connect").apply { isDaemon = true } }
    @Volatile private var started = false

    @Synchronized fun start() {
        if (started) return
        started = true
        ex.execute { runCatching { link.onStartup(); link.tick(startup = true) } }
        ex.scheduleWithFixedDelay({ runCatching { link.tick() } }, tickMs, tickMs, TimeUnit.MILLISECONDS)
    }

    /** Runs [block] on the link's thread (never on the UI thread: it does network I/O). */
    fun post(block: ServerLink.() -> Unit) { runCatching { ex.execute { runCatching { link.block() } } } }

    fun stop() { ex.shutdownNow() }
}
