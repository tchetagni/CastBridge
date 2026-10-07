package castbridge.receiver

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import castbridge.core.tv.OpenTarget
import castbridge.core.tv.OpenTvPlan
import castbridge.core.tv.OpenTvReply
import castbridge.core.tv.OpenTvScreen
import castbridge.core.tv.OverlayOfferPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * « Ouvrir CastBridge-TV depuis le téléphone » (docs/REMOTE.md) : `POST /api/tv/open` et la commande `open` du canal Bluetooth CBTR arrivent ici
 * ([RemoteHub] › `openTv`). Un geste sur le téléphone fait passer CastBridge-TV devant l'application qui est à l'écran de la TV (YouTube…).
 *
 * L'ordre d'essai, la vérification après chaque essai et la réponse sont la règle pure [OpenTvPlan] (testée en JVM) ; ce fichier ne fait que les gestes Android :
 *
 *  - **direct** : depuis le service, permis par « Afficher par-dessus les autres applications » (ou avant Android 10) ;
 *  - **fullscreen** : la notification du canal « Demandes du téléphone » (le même que la lecture demandée à distance), avec l'intention plein écran quand Android l'autorise ;
 *  - **accessibility** : le service d'accessibilité « CastBridge Télécommande » (Android lui permet de démarrer un écran depuis l'arrière-plan), s'il est connecté.
 *
 * Ce qui revient à l'écran est la règle pure [OpenTvPlan.target] (R-40, audit anti-régression 2026-10-07 b, I-1) : **sans écran demandé, la TÂCHE de CastBridge-TV revient telle qu'elle
 * était** (`AppTask.moveToFront`, ou une intention vers l'écran du sommet avec `NEW_TASK | REORDER_TO_FRONT` : rien n'est fermé) ; `PlayerActivity` est `singleTask` et racine de la tâche,
 * la démarrer fermerait Quiz, partie en ligne (`PlayOnlineActivity.onDestroy` l'arrête), Échecs, Langues, Portefeuille. Un écran demandé explicitement s'ouvre (`CLEAR_TOP`).
 *
 * Rien ici n'allume une TV éteinte (pas de HDMI-CEC, pas de Wake-on-LAN) et aucune permission n'est ajoutée : `SYSTEM_ALERT_WINDOW` et `USE_FULL_SCREEN_INTENT`
 * étaient déjà déclarées. Si aucun chemin ne marche, la TV propose UNE fois dans son MENU la ligne « Autoriser CastBridge-TV à s'afficher par-dessus les autres applications ».
 */
object TvForeground {
    /** L'écran de la bibliothèque est demandé (lu par PlayerActivity, comme [RemoteHub.EXTRA_HOME] pour l'accueil). */
    const val EXTRA_LIBRARY = "castbridge.open.LIBRARY"
    private const val TAG = "CastBridgeTV"
    // le même canal et la même notification que la lecture demandée à distance (TvService.notifyLaunch) : deux demandes ne s'empilent pas
    private const val CH_LAUNCH = "tv-launch"
    private const val NOTIF_LAUNCH = 2
    private const val PREF_WANTED = "open_tv_overlay_wanted"
    private const val PREF_ASKED = "open_tv_overlay_asked"
    /** Une demande à la fois : deux téléphones (ou un double appui) ne lancent pas deux séries d'essais. */
    private val lock = ReentrantLock()

    /** L'écran de CastBridge-TV qui est devant, s'il y en a un (la même règle que la télécommande, [RemoteHub]). */
    fun front(): Activity? = ScreenCapture.resumed?.takeIf { !it.isFinishing } ?: TvService.running?.screen?.takeIf { it.shown }?.activity

    /** Appelé par un fil HTTP ou Bluetooth : peut bloquer quelques secondes (il vérifie que l'écran est vraiment là). */
    fun bringToFront(screen: OpenTvScreen?): OpenTvReply {
        val svc = TvService.running ?: return OpenTvReply(false, OpenTvReply.HOW_NONE, null)
        val got = runCatching { lock.tryLock(6, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!got) return if (front() != null) OpenTvReply.already() else OpenTvReply(false, OpenTvReply.HOW_NONE, null)
        try {
            val reply = OpenTvPlan.execute(state(svc), allowed(screen), AndroidActions(svc))
            Log.i(TAG, "ouvrir CastBridge-TV depuis le téléphone : ${if (reply.opened) "ok" else "non"} (${reply.how}${reply.needs?.let { ", il faut : $it" } ?: ""})")
            if (!reply.opened && reply.needs == OpenTvReply.NEEDS_OVERLAY) runCatching { svc.prefs.putBool(PREF_WANTED, true) }     // la ligne du MENU est proposée, une fois
            return reply
        } finally { lock.unlock() }
    }

    /** L'édition d'essai n'a ni la bibliothèque ni le quiz : un téléphone ne les ouvre pas non plus (règle pure [OpenTvScreen.forEdition]). */
    private fun allowed(screen: OpenTvScreen?): OpenTvScreen? = OpenTvScreen.forEdition(screen, ActivationCenter.trial())

    private fun state(svc: TvService): OpenTvPlan.State {
        val nm = svc.getSystemService(NotificationManager::class.java)
        return OpenTvPlan.State(
            front = front() != null,
            sdk = Build.VERSION.SDK_INT,
            overlayAllowed = svc.overlayAllowed(),
            overlayScreenExists = overlayScreenExists(svc),
            fullScreenAllowed = Build.VERSION.SDK_INT < 34 || runCatching { nm.canUseFullScreenIntent() }.getOrDefault(false),
            notificationsAllowed = runCatching { nm.areNotificationsEnabled() }.getOrDefault(false),
            accessibilityActive = RemoteAccessibilityService.instance != null,
        )
    }

    /** L'écran « Afficher par-dessus les autres applications » de CastBridge-TV (avec son paquet, sinon la liste générale) ; null = ce boîtier n'a pas cet écran. */
    private fun overlayIntent(ctx: Context): Intent? = runCatching {
        listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)), Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            .firstOrNull { it.resolveActivity(ctx.packageManager) != null }
    }.getOrNull()
    private fun overlayScreenExists(ctx: Context) = overlayIntent(ctx) != null

    /** Le nom de la classe de l'écran qui est au sommet de la tâche de CastBridge-TV (le dernier repris et encore vivant), null = aucun connu. */
    private fun topName(): String? = ScreenCapture.top?.takeIf { !it.isFinishing && !it.isDestroyed }?.javaClass?.name

    /** La tâche de CastBridge-TV, si le système en garde une (même quand notre processus a redémarré pendant que YouTube était devant). */
    private fun appTask(ctx: Context): ActivityManager.AppTask? = runCatching { ctx.getSystemService(ActivityManager::class.java).appTasks.firstOrNull() }.getOrNull()

    /** Ce que le geste ramène à l'écran : la règle pure [OpenTvPlan.target] avec ce que la TV sait de sa tâche. */
    private fun targetOf(ctx: Context, screen: OpenTvScreen?): OpenTarget = OpenTvPlan.target(screen, topName(), appTask(ctx) != null)

    /** Le système ramène la tâche de l'application devant, TELLE QU'ELLE EST (même exemption de démarrage depuis l'arrière-plan qu'un `startActivity`). Faux si elle n'existe plus. */
    private fun moveTaskToFront(ctx: Context): Boolean = runCatching { appTask(ctx)!!.moveToFront() }.isSuccess

    /**
     * L'intention du geste. [OpenTarget.Resume] : l'écran du sommet est seulement REMIS DEVANT dans sa tâche (jamais `CLEAR_TOP`, jamais `PlayerActivity` quand autre chose est au-dessus) ;
     * [OpenTarget.Screen] : l'écran demandé (une vidéo n'est pas interrompue par `player`) ; [OpenTarget.Launch] : rien de vivant, le lanceur.
     */
    internal fun intentFor(ctx: Context, target: OpenTarget): Intent = when (target) {
        is OpenTarget.Resume -> (target.top?.let { Intent().setClassName(ctx, it) } ?: Intent(ctx, PlayerActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        is OpenTarget.Screen -> screenIntent(ctx, target.screen)
        OpenTarget.Launch -> Intent(ctx, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** L'écran demandé explicitement : ce que demande le téléphone, y compris `home` qui quitte la vidéo ; les écrans au-dessus se ferment, c'est ce qui est demandé. */
    private fun screenIntent(ctx: Context, screen: OpenTvScreen): Intent {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return when (screen) {
            OpenTvScreen.GAMES -> Intent(ctx, GamesActivity::class.java)
            OpenTvScreen.QUIZ -> Intent(ctx, QuizActivity::class.java)
            else -> Intent(ctx, PlayerActivity::class.java).also {
                when (screen) {
                    OpenTvScreen.HOME -> it.putExtra(RemoteHub.EXTRA_HOME, true)
                    OpenTvScreen.LIBRARY -> it.putExtra(EXTRA_LIBRARY, true)
                    else -> {}
                }
            }
        }.addFlags(flags)
    }

    private class AndroidActions(private val svc: TvService) : OpenTvPlan.Actions {
        override fun start(way: OpenTvPlan.Way, screen: OpenTvScreen?, fullScreenIntent: Boolean): Boolean {
            val target = targetOf(svc, screen)
            val i = intentFor(svc, target)
            return when (way) {
                // sous Android 10+, un démarrage bloqué ne lève rien : awaitFront le voit
                OpenTvPlan.Way.DIRECT -> (target is OpenTarget.Resume && moveTaskToFront(svc)) || runCatching { svc.startActivity(i) }.isSuccess
                OpenTvPlan.Way.FULLSCREEN -> post(svc, i, fullScreenIntent)
                OpenTvPlan.Way.ACCESSIBILITY -> RemoteAccessibilityService.instance?.let { a -> runCatching { a.startActivity(i) }.isSuccess } ?: false
            }
        }

        override fun awaitFront(maxMs: Long): Boolean {
            val until = SystemClock.elapsedRealtime() + maxMs
            while (true) {
                if (front() != null) return true
                if (SystemClock.elapsedRealtime() >= until) return false
                try { Thread.sleep(100) } catch (_: InterruptedException) { return front() != null }
            }
        }

        override fun withdraw() { runCatching { svc.getSystemService(NotificationManager::class.java).cancel(NOTIF_LAUNCH) } }
    }

    private fun post(ctx: Context, intent: Intent, fullScreen: Boolean): Boolean = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LAUNCH, "Demandes du téléphone", NotificationManager.IMPORTANCE_HIGH))
        // its own request code (the playback request of TvService uses 1 for PlayerActivity too): the same notification slot, but never the same PendingIntent
        val pi = PendingIntent.getActivity(ctx, 3, Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(ctx, CH_LAUNCH).setContentTitle("CastBridge-TV").setContentText("Le téléphone demande d'ouvrir CastBridge-TV")
            .setSmallIcon(R.drawable.ic_stat_castbridge).setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_CALL)
        if (fullScreen) b.setFullScreenIntent(pi, true)
        nm.notify(NOTIF_LAUNCH, b.build())
        true
    }.getOrDefault(false)

    // ---------------------------------------------------------------- la ligne de MENU, proposée une seule fois

    /**
     * L'écran de réglage à ouvrir si la ligne « Autoriser CastBridge-TV à s'afficher par-dessus les autres applications » doit être proposée maintenant, sinon null
     * ([OverlayOfferPolicy] : seulement après une demande de téléphone restée sans effet, si l'écran existe sur ce boîtier, jamais deux fois). Même modèle que [BatteryExemption].
     */
    fun overlayOffer(ctx: Context, prefs: TvPrefs): Intent? {
        val i = overlayIntent(ctx) ?: return null
        val granted = Build.VERSION.SDK_INT < 23 || runCatching { Settings.canDrawOverlays(ctx) }.getOrDefault(false)
        return i.takeIf { OverlayOfferPolicy.shouldOffer(prefs.getBool(PREF_WANTED, false), granted, true, prefs.getBool(PREF_ASKED, false), Build.VERSION.SDK_INT) }
    }

    /** La ligne a été choisie (accord ou refus) : elle ne revient plus. */
    fun markOverlayAsked(prefs: TvPrefs) = prefs.putBool(PREF_ASKED, true)
}
