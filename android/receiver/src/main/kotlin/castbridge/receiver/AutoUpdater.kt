package castbridge.receiver

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import castbridge.core.update.UpdateClient
import castbridge.core.update.UpdateKeys
import castbridge.core.update.UpdateManifest
import castbridge.core.update.UpdateSchedule
import java.io.File

/**
 * Automatic updates of the TV app (the "autotéléchargement"): at start-up and then periodically ([UpdateSchedule]), the TV
 * asks the CastBridge server for a newer version, verifies its signed manifest and the APK's SHA-256, downloads it in the
 * background (resumable), then hands it to [UpdateInstaller]. Installing still follows Android's rules: if the system does
 * not allow a silent install, the TV shows its usual confirmation — nothing here tries to get around that.
 */
class AutoUpdater(
    private val ctx: Context,
    private val installer: UpdateInstaller,
    private val prefs: TvPrefs,
    /** The folder the downloaded APK lands in (also searched by the installer). */
    private val downloadDir: () -> File,
    /** Persistent status line (shown on the TV; null clears it). */
    private val onStatus: (String?) -> Unit,
    /** One-off message (toast on the TV). */
    private val onNotice: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val client = UpdateClient(baseUrl(), "tv", UpdateKeys.PUBLIC_KEYS)
    private val schedule = UpdateSchedule(deviceSeed = deviceSeed())
    private var state = UpdateSchedule.State.decode(prefs.getString("update_schedule"))
    @Volatile private var running = false
    @Volatile private var checking = false
    @Volatile var updateAvailable = false; private set

    fun start() {
        if (running) return
        running = true
        Thread { if (running) check(UpdateSchedule.Trigger.STARTUP) }.start()
        main.postDelayed(tick, TIMER_MS)
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
    }

    /** A manual "check now" (from the settings or the API); respects the same minimum interval. */
    fun checkNow() { Thread { if (running) check(UpdateSchedule.Trigger.FORCED) }.start() }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (schedule.isDue(state, System.currentTimeMillis(), UpdateSchedule.Trigger.TIMER))
                Thread { if (running) check(UpdateSchedule.Trigger.TIMER) }.start()
            main.postDelayed(this, TIMER_MS)
        }
    }

    private fun baseUrl() = prefs.getString("update_server_url")?.trimEnd('/') ?: DEFAULT_URL

    private fun deviceSeed(): Long {
        val id = prefs.getString("install_id") ?: java.util.UUID.randomUUID().toString().also { prefs.putString("install_id", it) }
        return id.hashCode().toLong()
    }

    @Suppress("DEPRECATION")
    private fun installedCode(): Int = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrDefault(0)

    private fun installed(): UpdateClient.Installed = UpdateClient.Installed(
        versionCode = installedCode(),
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        sdk = Build.VERSION.SDK_INT,
        channel = "stable",
    )

    private fun check(trigger: UpdateSchedule.Trigger) {
        if (checking) return
        val now = System.currentTimeMillis()
        if (!schedule.isDue(state, now, trigger)) return
        checking = true
        try {
            val me = installed()
            when (val c = client.check(me)) {
                is UpdateClient.Check.UpToDate -> { state = schedule.onSuccess(state, now); onStatus("Mises à jour : à jour (${me.versionCode})") }
                is UpdateClient.Check.Available -> {
                    updateAvailable = true
                    state = schedule.onSuccess(state, now)
                    val m = c.manifest
                    onStatus("Nouvelle version ${m.versionName} (${m.versionCode}) : téléchargement…")
                    onNotice("Mise à jour obligatoire en cours de téléchargement…")
                    downloadAndInstall(m)
                }
                is UpdateClient.Check.Blocked -> { updateAvailable = true; state = schedule.onSuccess(state, now); onStatus("Mises à jour bloquées par l'administrateur") }
                is UpdateClient.Check.Failed -> {
                    state = schedule.onFailure(state, now)
                    if (!c.retryable) onStatus("Mise à jour impossible : ${c.reason}")
                    Log.w(TAG, "check: ${c.reason}")
                }
            }
        } catch (e: Exception) {
            state = schedule.onFailure(state, now)
            Log.w(TAG, "check", e)
        } finally {
            prefs.putString("update_schedule", state.encode())
            checking = false
        }
    }

    private fun downloadAndInstall(m: UpdateManifest) {
        Thread {
            try {
                val f = client.download(m, downloadDir(), progress = { done, total ->
                    onStatus("Téléchargement de la mise à jour : ${if (total > 0) done * 100 / total else 0} %")
                })
                onStatus("Version ${m.versionName} téléchargée, installation…")
                installer.install(listOf(f.name), force = false)
            } catch (e: UpdateClient.Cancelled) {
                onStatus("Mise à jour retirée par le serveur")
            } catch (e: Exception) {
                onStatus("Téléchargement impossible : ${e.message ?: e.javaClass.simpleName}")
                Log.w(TAG, "download", e)
            }
        }.start()
    }

    companion object {
        private const val TAG = "CastBridgeAutoUpdate"
        const val DEFAULT_URL = "https://bridge.sti-cm.com"
        private const val TIMER_MS = 10 * 60_000L
    }
}
