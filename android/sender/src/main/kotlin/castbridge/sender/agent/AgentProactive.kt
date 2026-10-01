package castbridge.sender.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import castbridge.core.library.agent.LibraryAgent
import castbridge.core.library.agent.ProactivePolicy
import castbridge.sender.CastTheme
import castbridge.sender.R

/**
 * « Suggestions proactives » as a notification. OFF by default; when on: at most one a week, silent (low importance, no sound, no vibration),
 * about the phone folder the user picked, computed locally (no network). It only ever opens the assistant; it never does anything by itself.
 * The discreet line inside the TV library screen ([AssistantBanner]) does not depend on it.
 *
 * Mechanism: a periodic [JobScheduler] job (system-managed, battery-friendly, survives reboots with RECEIVE_BOOT_COMPLETED). No WorkManager:
 * it would add a library (~1 MB) for one job a day.
 */
object AgentProactive {
    private const val JOB_ID = 4417
    private const val CHANNEL = "castbridge_agent"
    private const val NOTIF_ID = 4418

    /** Schedules or cancels the daily check according to the setting. Idempotent. */
    fun sync(ctx: Context) {
        AgentStore.init(ctx)
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (!AgentStore.settings.proactiveNotify) { js.cancel(JOB_ID); return }
        if (js.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, ProactiveJob::class.java))
            .setPeriodic(24L * 3_600_000L)
            .setRequiresBatteryNotLow(true).setRequiresDeviceIdle(false)
            .setPersisted(ctx.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED) == PackageManager.PERMISSION_GRANTED)
            .build()
        js.schedule(info)
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One check: reads the picked folder (without reading new video headers), and notifies only if [ProactivePolicy] allows it. Returns true if it notified. */
    fun check(ctx: Context, now: Long = System.currentTimeMillis()): Boolean {
        AgentStore.init(ctx)
        val s = AgentStore.settings
        if (!s.proactiveNotify || !canNotify(ctx)) return false
        val tree = s.phoneTreeUri?.let(Uri::parse) ?: return false
        if (ctx.contentResolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) return false
        if (now - s.lastNotifiedAt < ProactivePolicy.MIN_GAP_MS) return false          // cheap exit before reading anything
        val snap = SafLibrary(ctx, tree, AgentStore.cache).snapshot(maxFiles = 5_000)
        val a = LibraryAgent(AgentStore.agentContext(folders = true), AgentStore.learned).analyze(snap)
        val m = ProactivePolicy.decide(true, AgentStore.guard.childProfileActive, s.lastNotifiedAt, s.lastNotifiedSignature, a.insights, s.hiddenUntil(), now) ?: return false
        notify(ctx, m)
        s.markNotified(now, m.signature)
        return true
    }

    private fun notify(ctx: Context, m: ProactivePolicy.Message) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Suggestions de rangement", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Au plus un conseil par semaine, sans son. Désactivable dans Ranger ma bibliothèque → Réglages."
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, AssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIF_ID, android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_castbridge).setContentTitle(m.title).setContentText(m.text).setStyle(android.app.Notification.BigTextStyle().bigText(m.text))
            .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(open).build())
    }
}

class ProactiveJob : JobService() {
    @Volatile private var thread: Thread? = null
    override fun onStartJob(params: JobParameters): Boolean {
        thread = Thread {
            runCatching { AgentProactive.check(applicationContext) }
            jobFinished(params, false)
        }.also { it.start() }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { thread?.interrupt(); return false }
}

/** Opens the assistant on the phone folder (target of the notification). Not exported: only this app can start it. */
class AssistantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { LibraryAssistantDialog(null) { finish() } } }
    }
}
