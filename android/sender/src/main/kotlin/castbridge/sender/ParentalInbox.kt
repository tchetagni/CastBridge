package castbridge.sender

import android.app.Notification
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
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import castbridge.core.parental.InboxPersistence
import castbridge.core.parental.ReportInbox
import castbridge.core.parental.ReportSync
import castbridge.core.parental.ReportText
import castbridge.core.trust.BtUnavailable
import castbridge.core.tv.BtProtocol
import java.io.IOException

/** The phone's inbox in a private preferences file (excluded from backups: it holds the key that signs the reports). */
private class PrefsInbox(private val sp: SharedPreferences) : InboxPersistence {
    override fun load(): String? = sp.getString("inbox", null)
    override fun save(text: String) { sp.edit().putString("inbox", text).apply() }
}

/**
 * Reports of the parental control, phone side (docs/PARENTAL.md, « Rapports envoyés au téléphone »). No server, no push service:
 *  - the phone PULLS over the paired Bluetooth link (CBTP) when the app opens, from the screen of the parent, and from a light periodic job
 *    (every 15 minutes) that runs ONLY if this phone is a designated recipient: the child's phone never polls;
 *  - each report is checked (signature of the TV) and stored here, bounded, excluded from backups;
 *  - a local notification announces it (private on the lock screen: the child's name and times are not shown there).
 * The TV keeps what it could not deliver (outbox) until this phone acknowledges it.
 */
object ParentalInbox {
    private const val JOB_ID = 4421
    private const val CHANNEL = "castbridge_parental"
    private const val NOTIF_ID = 4422
    private lateinit var sp: SharedPreferences
    lateinit var inbox: ReportInbox; private set

    @Synchronized fun init(ctx: Context) {
        if (::inbox.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("castbridge_parental_inbox", Context.MODE_PRIVATE)
        inbox = ReportInbox(PrefsInbox(sp))
    }

    /** null = not known yet (never reached the TV). */
    val designated: Boolean? get() = if (sp.contains("designated")) sp.getBoolean("designated", false) else null
    /** This phone's opaque id on the TV: the parent designates it in the TV's list (the Bluetooth address never leaves the TV). */
    val phoneId: String? get() = sp.getString("phoneId", null)
    val lastSyncAt: Long get() = sp.getLong("lastSyncAt", 0)
    val lastError: String? get() = sp.getString("lastError", null)

    /** Plans or cancels the periodic job: only a designated phone polls (battery, and nothing for the child's phone to do). */
    fun schedule(ctx: Context) {
        init(ctx)
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (designated == false || TvLinkManager.saved.list().isEmpty()) { js.cancel(JOB_ID); return }
        if (js.getPendingJob(JOB_ID) != null) return
        js.schedule(JobInfo.Builder(JOB_ID, ComponentName(ctx, ParentalSyncJob::class.java))
            .setPeriodic(15L * 60_000L)
            .setPersisted(ctx.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED) == PackageManager.PERMISSION_GRANTED)
            .build())
    }

    /** One visit to the default TV. Returns null when it went well, else the French reason (shown on the parent's screen). */
    @Synchronized fun sync(ctx: Context): String? {
        init(ctx)
        val tv = TvLinkManager.saved.default() ?: return fail("Aucune TV enregistrée dans l'app.")
        return try {
            val res = AndroidBtTransport(ctx).connect(tv.address).use { ReportSync.run(it, tv.address, inbox) }
            sp.edit().putBoolean("designated", res.designated == true).apply { res.phoneId?.let { putString("phoneId", it) } }
                .putLong("lastSyncAt", System.currentTimeMillis()).remove("lastError").apply()
            schedule(ctx)
            notify(ctx, res.stored)
            if (res.rejected > 0) "${res.rejected} rapport(s) refusé(s) : signature invalide." else null
        } catch (e: BtUnavailable) { fail("Bluetooth indisponible sur ce téléphone.")
        } catch (e: BtProtocol.Refused) {
            fail(when (e.code) {
                BtProtocol.ERR_UNTRUSTED -> "La TV ne connaît plus ce téléphone : ajoutez-la à nouveau."
                BtProtocol.ERR_MAGIC -> "Cette TV n'a pas la dernière version de CastBridge TV (rapports non disponibles)."
                else -> BtProtocol.describe(e.code)
            })
        } catch (e: IOException) { fail("TV injoignable (éteinte ou hors de portée) : nouvel essai plus tard.") }
    }

    private fun fail(why: String): String { sp.edit().putString("lastError", why).apply(); return why }

    private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(ctx: Context, batch: List<castbridge.core.parental.StoredReport>) {
        val (title, text) = ReportText.notification(batch) ?: return
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rapports du contrôle parental", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Résumés et alertes de la TV, reçus par Bluetooth. Aucun serveur n'est utilisé."
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_castbridge).setContentTitle("Rapport du contrôle parental").build()
        nm.notify(NOTIF_ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_castbridge).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setAutoCancel(true).setContentIntent(open).build())
    }
}

/** Periodic pull of the reports (see [ParentalInbox.schedule]). */
class ParentalSyncJob : JobService() {
    @Volatile private var thread: Thread? = null
    override fun onStartJob(params: JobParameters): Boolean {
        thread = Thread {
            runCatching { ParentalInbox.sync(applicationContext) }
            jobFinished(params, false)
        }.also { it.start() }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { thread?.interrupt(); return false }
}
