package castbridge.receiver

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import castbridge.core.owner.ActivationResult
import castbridge.core.tv.activation.UsbActivationBanner
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Looks for the USB activation file at PLUG-IN (broadcast « media mounted »), when the activation screen OPENS and on the « Chercher » BUTTON (docs/TV-ACTIVATION-CLE-USB.md): no silent
 * search every 15 s any more. Keeps what it found, for two readers: the activation screen shows [view] as its banner (a focusable « Activer » button when a key verifies for this TV);
 * the home shows [homeLine] when the activation screen is not open (trial or grace TV).
 *
 * The decisions (states, texts, retries) live in the pure `UsbActivationBanner`; here only the threads, the timers and the listeners. A search only VERIFIES (`ActivationCenter.lookup(false)`):
 * nothing is installed before [install] (the button). Nothing is read before the terms of use are accepted on this TV. No key, no file content and no file path is ever logged.
 */
object UsbActivationWatch {
    enum class Trigger { OPEN, MOUNT, RETRY, BUTTON, TERMS }

    /** What the screens show now. */
    @Volatile var view: UsbActivationBanner.View = UsbActivationBanner.idle(); private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-usb-activation").apply { isDaemon = true } }
    @Volatile private var app: Context? = null
    private var retries = 0
    private val retryRun = Runnable { app?.let { search(it, Trigger.RETRY) } }

    /** Called on the main thread whenever [view] changes. */
    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun publish(v: UsbActivationBanner.View) {
        view = v
        main.post { listeners.forEach { runCatching { it() } } }
    }

    /** Looks now (in a thread). A new request replaces a pending retry; a MOUNT starts a new series of retries. */
    fun search(ctx: Context, trigger: Trigger) {
        app = ctx.applicationContext
        main.removeCallbacks(retryRun)
        if (trigger == Trigger.MOUNT) retries = 0
        if (!TunnelHub.termsAccepted(ctx)) { publish(UsbActivationBanner.termsPending()); return }
        // a button press (or the terms just accepted) answers at once, whatever the lookup takes
        if (trigger == Trigger.BUTTON || trigger == Trigger.TERMS) publish(UsbActivationBanner.searching(view.keyPresent, view.presence))
        io.execute { lookup(trigger) }
    }

    private fun lookup(trigger: Trigger) {
        val ctx = app ?: return
        ActivationCenter.init(ctx)
        val found = ActivationCenter.lookup(install = false) ?: return          // another lookup is running: its answer comes
        val v = UsbActivationBanner.from(found.facts)
        publish(v)
        // the volume needs a moment to settle after the mount: an empty answer is retried twice, then left to the « Chercher » button
        if (trigger == Trigger.MOUNT || trigger == Trigger.RETRY) UsbActivationBanner.nextRetryDelayMs(v.state, retries)?.let { delay -> retries++; main.postDelayed(retryRun, delay) }
    }

    /** The system's storage broadcasts (the TV service registers them: locked, trial and grace TVs alike). Only while an activation is still useful on this TV. */
    fun onStorageEvent(ctx: Context, action: String?) {
        when (action) {
            Intent.ACTION_MEDIA_MOUNTED -> if (ActivationCenter.usbWatchWanted()) search(ctx, Trigger.MOUNT)
            Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_REMOVED, Intent.ACTION_MEDIA_BAD_REMOVAL -> reset()
        }
    }

    /** The « Activer » button: looks again and INSTALLS the first key that verifies; [done] runs on the main thread (null = a lookup was already running). */
    fun install(ctx: Context, done: (ActivationResult?) -> Unit) {
        app = ctx.applicationContext
        io.execute {
            ActivationCenter.init(ctx)
            val r = ActivationCenter.lookup(install = true)?.result
            main.post { done(r) }
        }
    }

    /** The key was taken out, or the TV was activated: nothing to say any more. */
    fun reset() {
        main.removeCallbacks(retryRun)
        retries = 0
        publish(UsbActivationBanner.idle())
    }

    /** The one status line of the home screen (null = nothing to say, or the TV needs no activation any more). */
    fun homeLine(): String? = UsbActivationBanner.homeLine(view)?.takeIf { ActivationCenter.usbWatchWanted() }       // the cheap test first: almost always nothing to say
}
