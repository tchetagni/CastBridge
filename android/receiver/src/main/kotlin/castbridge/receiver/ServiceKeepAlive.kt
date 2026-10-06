package castbridge.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import castbridge.core.tv.KeepAlivePolicy

/**
 * Chien de garde de CastBridge-TV (docs/TV-SERVICE-DEMARRAGE.md) : si Android (ou le boîtier) tue le service, il est relancé au plus tard
 * 15 minutes plus tard. Deux filets indépendants, jamais deux instances : un JobScheduler périodique persistant (survit au redémarrage)
 * et un AlarmManager inexact (repli). La décision « relancer ou non » est [KeepAlivePolicy].
 */
object ServiceKeepAlive {
    private const val TAG = "CastBridgeTV"
    private const val JOB_ID = 3101
    private const val ALARM_CODE = 3102
    private const val TASK_ALARM_CODE = 3103
    const val ACTION_WATCHDOG = "castbridge.receiver.KEEPALIVE"
    const val ACTION_TASK_REMOVED = "castbridge.receiver.TASK_REMOVED_RESTART"

    /** Arme les deux filets (idempotent : le même identifiant remplace le précédent). À appeler au démarrage du service, au boot et après une mise à jour. */
    fun arm(ctx: Context) {
        runCatching { armJob(ctx) }.onFailure { Log.w(TAG, "chien de garde (job) : ${it.javaClass.simpleName}") }
        runCatching { armAlarm(ctx) }.onFailure { Log.w(TAG, "chien de garde (alarme) : ${it.javaClass.simpleName}") }
    }

    private fun armJob(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return                      // déjà programmé : jamais deux
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, KeepAliveJob::class.java))
            .setPeriodic(KeepAlivePolicy.PERIOD_MS)
            .setPersisted(true)                                          // repart avec la TV (RECEIVE_BOOT_COMPLETED)
            .build()
        js.schedule(job)
    }

    private fun armAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(ctx, ALARM_CODE, Intent(ctx, KeepAliveReceiver::class.java).setAction(ACTION_WATCHDOG),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // setInexactRepeating : le système regroupe les réveils (économe) ; les boîtiers en veille profonde le décalent, d'où le job en parallèle.
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + KeepAlivePolicy.PERIOD_MS, KeepAlivePolicy.PERIOD_MS, pi)
    }

    /** Relance différée de 3 s après le balayage de l'app (onTaskRemoved). */
    fun scheduleTaskRemovedRestart(ctx: Context) {
        runCatching {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(ctx, TASK_ALARM_CODE, Intent(ctx, KeepAliveReceiver::class.java).setAction(ACTION_TASK_REMOVED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val at = SystemClock.elapsedRealtime() + KeepAlivePolicy.TASK_REMOVED_DELAY_MS
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            else am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }.onFailure { Log.w(TAG, "relance après balayage : ${it.javaClass.simpleName}") }
    }

    /** Le service vit-il ? ([TvService.running] : le singleton du processus ; un processus tué le remet à null.) */
    fun alive() = TvService.running != null

    /** Applique la règle pure et relance si besoin. [why] : raison journalisée et affichée dans INFO. */
    fun check(ctx: Context, why: String) {
        val prefs = TvPrefs(ctx)
        val d = KeepAlivePolicy.decide(alive(), prefs.getBool("autostart", true), prefs.getBool("service_stopped_by_owner", false))
        if (d == KeepAlivePolicy.Decision.RESTART) {
            Log.i(TAG, "chien de garde : service absent, relance ($why)")
            TvService.start(ctx, why)
        }
    }
}

/** Tâche périodique du chien de garde (déclarée avec BIND_JOB_SERVICE). Courte : une vérification, pas de travail réel. */
class KeepAliveJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean { ServiceKeepAlive.check(applicationContext, "WATCHDOG"); return false }
    override fun onStopJob(params: JobParameters?): Boolean = true
}

/** Réveil d'AlarmManager : chien de garde (repli) ou relance 3 s après un balayage. */
class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        ServiceKeepAlive.check(c, if (i.action == ServiceKeepAlive.ACTION_TASK_REMOVED) "TASK_REMOVED" else "WATCHDOG")
    }
}
