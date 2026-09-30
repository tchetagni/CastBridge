package castbridge.receiver

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import castbridge.core.remote.KeyAction
import castbridge.core.remote.Outcome
import castbridge.core.remote.RemoteApi
import castbridge.core.remote.RemoteBt
import castbridge.core.remote.RemoteGlobal
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteSink
import castbridge.core.remote.RemoteTarget
import castbridge.core.remote.TextMode
import castbridge.core.tv.ReceiverServer.Companion.q
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phone remote on the TV side (docs/REMOTE.md). Keys from the phone (POST /api/remote/…, PIN) are given to the CastBridge
 * screen in front (whichever activity [ScreenCapture] saw resumed, or a dialog/popup of it that has the focus) through
 * View.dispatchKeyEvent on the main thread, exactly as the TV's own remote would — plus the focus move that Android's input
 * pipeline does for arrows nobody consumed. Nothing is injected into other apps: an app cannot. Outside CastBridge the
 * optional accessibility service ([RemoteAccessibilityService], enabled by the user) moves the focus and performs Back/Home;
 * media keys go to the active media session through AudioManager; volume through AudioManager.
 */
object RemoteHub {
    const val EXTRA_HOME = "castbridge.remote.HOME"
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var svc: TvService? = null
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-remote-hold").apply { isDaemon = true } }

    val api: RemoteApi = RemoteApi(Sink)

    fun install(s: TvService) {
        if (svc == null) watchdog.scheduleWithFixedDelay({ runCatching { if (api.holding) api.releaseStale() } }, 200, 200, TimeUnit.MILLISECONDS)
        svc = s
    }

    /** Bluetooth remote (CBTR on the Bluetooth file service), after the PIN check. */
    fun serveBt(input: InputStream, output: OutputStream, onActivity: () -> Unit) = RemoteBt.serve(input, output, api, onActivity)

    // ------------------------------------------------------------------ helpers

    private fun <T> onMain(timeoutMs: Long = 1500, block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        var r: T? = null
        val l = CountDownLatch(1)
        main.post { try { r = block() } catch (_: Throwable) {} finally { l.countDown() } }
        l.await(timeoutMs, TimeUnit.MILLISECONDS)
        return r
    }

    /**
     * Runs a key on the main thread AHEAD of queued work (the TV's own input events also jump the queue: they are handled
     * before animation and drawing). Waits at most [waitMs] for the result so the phone's next key is never held up by a busy
     * screen; the key still runs, in order. Returns (finished, result).
     */
    private fun <T> onMainFirst(waitMs: Long, block: () -> T): Pair<Boolean, T?> {
        if (Looper.myLooper() == Looper.getMainLooper()) return true to runCatching(block).getOrNull()
        var r: T? = null
        val l = CountDownLatch(1)
        // Keys keep their order: they wait in this FIFO, and whichever "drain" runs first empties it (posting at the front of
        // the looper alone would put a later key before an earlier one that is still waiting).
        keyQueue.add(Runnable { try { r = block() } catch (_: Throwable) {} finally { l.countDown() } })
        main.postAtFrontOfQueue(drain)
        val done = l.await(waitMs, TimeUnit.MILLISECONDS)
        return done to r
    }
    private val keyQueue = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
    private val drain = Runnable { while (true) (keyQueue.poll() ?: break).run() }

    /** The CastBridge screen in front, if any. */
    private fun front(): Activity? = ScreenCapture.resumed?.takeIf { !it.isFinishing }
        // The first screen may have resumed before the lifecycle hook was installed (it starts the service): ask the service.
        ?: svc?.screen?.takeIf { it.shown }?.activity

    /**
     * The window that has the focus among this app's windows (a dialog, a popup, else the activity). WindowManagerGlobal is not
     * public: read by reflection (as UI test tools do); if that is refused, the activity's own window.
     */
    private fun focusedRoot(a: Activity): View {
        val roots = runCatching {
            val c = Class.forName("android.view.WindowManagerGlobal")
            val g = c.getMethod("getInstance").invoke(null)
            val f = c.getDeclaredField("mViews").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") (f.get(g) as List<View>).toList()
        }.getOrNull().orEmpty()
        return roots.lastOrNull { it.hasWindowFocus() && it.isAttachedToWindow } ?: a.window.decorView
    }

    private val downTimes = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    private fun dispatch(root: View, code: Int, action: Int, repeat: Int, flags: Int = 0): Boolean {
        val now = SystemClock.uptimeMillis()
        val down = if (action == KeyEvent.ACTION_DOWN && repeat == 0) now.also { downTimes[code] = it } else downTimes[code] ?: now
        if (action == KeyEvent.ACTION_UP) downTimes.remove(code)
        val source = if (code in 19..23) InputDevice.SOURCE_DPAD else InputDevice.SOURCE_KEYBOARD
        val ev = KeyEvent(down, now, action, code, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM or flags, source)
        if (root.dispatchKeyEvent(ev)) return true
        return action == KeyEvent.ACTION_DOWN && navigate(root, code)
    }

    /** What ViewRootImpl does with an arrow no view consumed: move the focus to the next view in that direction. */
    private fun navigate(root: View, code: Int): Boolean {
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP; KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return false
        }
        val focused = root.findFocus() ?: return root.requestFocus(dir)
        val next = focused.focusSearch(dir) ?: return false
        if (next === focused) return false
        val r = Rect()
        focused.getFocusedRect(r)
        (root as? ViewGroup)?.let { g -> runCatching { g.offsetDescendantRectToMyCoords(focused, r); g.offsetRectIntoDescendantCoords(next, r) } }
        return next.requestFocus(dir, r)
    }

    private fun focusedEditor(root: View): TextView? = (root.findFocus() as? TextView)?.takeIf { it.onCheckIsTextEditor() && it.isEnabled }

    private val audio: AudioManager? get() = svc?.getSystemService(AudioManager::class.java)

    private fun volume(k: RemoteKey): Outcome {
        val am = audio ?: return Outcome.refused("Volume indisponible")
        if (am.isVolumeFixed) return Outcome.refused("Le volume de cette TV est fixe (réglé par l'ampli ou la TV via HDMI) : une app ne peut pas le changer.")
        val dir = when (k) { RemoteKey.VOLUME_UP -> AudioManager.ADJUST_RAISE; RemoteKey.VOLUME_DOWN -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE }
        return if (runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }.isSuccess) Outcome.done("audio")
        else Outcome.refused("La TV a refusé de changer le volume")
    }

    /** Media keys for whatever app plays (its media session), without any special permission. */
    private fun mediaKey(k: RemoteKey): Outcome {
        val am = audio ?: return Outcome.refused("indisponible")
        return runCatching {
            val t = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, k.code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, k.code, 0))
            Outcome.done("media")
        }.getOrElse { Outcome.refused("Touche multimédia refusée par la TV") }
    }

    /** Brings the CastBridge home up (from any CastBridge screen, or from the background as Android allows it). */
    private fun home(): Outcome {
        val s = svc ?: return Outcome.refused("CastBridge TV démarre…")
        val i = Intent(s, PlayerActivity::class.java).putExtra(EXTRA_HOME, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val a = front()
        if (a != null) { onMain { a.startActivity(i) }; return Outcome.done("app") }
        val why = s.launchScreen(i, "Le téléphone demande l'accueil de CastBridge TV")
        return if (why == null) Outcome.done("app") else Outcome.refused(why)
    }

    fun systemEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, RemoteAccessibilityService::class.java).flattenToString()
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any { it.equals(me, true) || it.equals(ComponentName(ctx, RemoteAccessibilityService::class.java).flattenToShortString(), true) }
    }

    private val NO_SCREEN: String get() = if (RemoteAccessibilityService.instance != null)
        "CastBridge TV n'est pas à l'écran. Touche « Accueil » pour l'ouvrir, ou activez « Piloter toute la TV » sur le téléphone."
        else "CastBridge TV n'est pas à l'écran. Touche « Accueil » pour l'ouvrir, " +
            "ou activez le mode « toute la TV » (accessibilité) pour piloter les autres écrans de la TV."

    // ------------------------------------------------------------------ the sink

    private object Sink : RemoteSink {
        override fun key(k: RemoteKey, action: KeyAction, repeat: Int, target: RemoteTarget): Outcome {
            if (k.kind == RemoteKey.Kind.VOLUME) return if (action == KeyAction.UP) Outcome.done("audio") else volume(k)
            if (k == RemoteKey.HOME) return if (action == KeyAction.UP) Outcome.done("app") else home()
            val a = front()
            if (a != null) return appKey(a, k, action, repeat)
            if (target == RemoteTarget.APP) return Outcome.refused(NO_SCREEN)
            return systemKey(k, action)
        }

        private fun appKey(a: Activity, k: RemoteKey, action: KeyAction, repeat: Int): Outcome {
            val (done, ok) = onMainFirst(if (action == KeyAction.LONG) 300 else 150) {
                val root = focusedRoot(a)
                // Nothing has the focus yet (screen just opened): like the TV's own remote, the first OK shows the focus
                // instead of clicking something the viewer cannot see.
                if (k == RemoteKey.DPAD_CENTER && action != KeyAction.UP && root.findFocus() == null && root.requestFocus()) return@onMainFirst true
                when (action) {
                    KeyAction.PRESS -> { val d = dispatch(root, k.code, KeyEvent.ACTION_DOWN, 0); dispatch(root, k.code, KeyEvent.ACTION_UP, 0) || d }
                    KeyAction.DOWN -> dispatch(root, k.code, KeyEvent.ACTION_DOWN, repeat)
                    KeyAction.UP -> dispatch(root, k.code, KeyEvent.ACTION_UP, 0)
                    KeyAction.LONG -> {
                        dispatch(root, k.code, KeyEvent.ACTION_DOWN, 0)
                        // After the long-press delay, as a held remote key: views run their long click, activities get FLAG_LONG_PRESS.
                        main.postDelayed({
                            val r = focusedRoot(a)
                            dispatch(r, k.code, KeyEvent.ACTION_DOWN, 1, KeyEvent.FLAG_LONG_PRESS)
                            dispatch(r, k.code, KeyEvent.ACTION_UP, 0, KeyEvent.FLAG_CANCELED_LONG_PRESS)
                        }, ViewConfiguration.getLongPressTimeout() + 80L)
                        true
                    }
                }
            }
            // Not finished within the wait (busy screen): it is queued ahead of everything else and will run, in order.
            return Outcome(true, "app", if (!done || ok == true) null else "sans effet sur cet écran")
        }

        private fun systemKey(k: RemoteKey, action: KeyAction): Outcome {
            if (k.kind == RemoteKey.Kind.MEDIA) return if (action == KeyAction.UP) Outcome.done("media") else mediaKey(k)
            val s = RemoteAccessibilityService.instance ?: return Outcome.refused(NO_SCREEN)
            if (action == KeyAction.UP) return Outcome.done("system")
            val ok = onMain {
                when {
                    k.kind == RemoteKey.Kind.NAV -> s.dpad(k, long = action == KeyAction.LONG)
                    k == RemoteKey.BACK -> s.global(RemoteGlobal.BACK.action)
                    k == RemoteKey.DEL -> s.deleteChar()
                    k == RemoteKey.ENTER -> s.enter()
                    k.kind == RemoteKey.Kind.DIGIT -> s.text(k.wire, TextMode.INSERT)
                    else -> null
                }
            } ?: return Outcome.refused("« ${k.label} » n'existe pas hors de CastBridge : Android ne permet pas à une app d'envoyer cette touche aux autres applications.")
            return if (ok) Outcome.done("system") else Outcome(true, "system", "sans effet sur cet écran")
        }

        override fun text(value: String, mode: TextMode, target: RemoteTarget): Outcome {
            val a = front()
            if (a != null) {
                val r = onMain {
                    val tv = focusedEditor(focusedRoot(a)) ?: return@onMain false
                    val e = tv.editableText
                    when {
                        mode == TextMode.CLEAR -> tv.text = ""
                        mode == TextMode.REPLACE -> { tv.text = value; (tv as? android.widget.EditText)?.setSelection(tv.text.length) }
                        e != null -> {
                            val s = maxOf(0, minOf(tv.selectionStart, tv.selectionEnd)); val en = maxOf(tv.selectionStart, tv.selectionEnd)
                            if (s < 0 || en < 0) e.append(value) else e.replace(s, en, value)
                        }
                        else -> tv.append(value)
                    }
                    true
                }
                return when (r) {
                    true -> Outcome.done("app")
                    false -> Outcome.refused("Aucun champ de saisie n'est sélectionné sur la TV : placez-vous dans un champ (flèches puis OK), puis réessayez.")
                    null -> Outcome.refused("L'écran de la TV ne répond pas", 503)
                }
            }
            if (target == RemoteTarget.APP) return Outcome.refused(NO_SCREEN)
            val s = RemoteAccessibilityService.instance ?: return Outcome.refused(NO_SCREEN)
            return if (onMain { s.text(value, mode) } == true) Outcome.done("system")
            else Outcome.refused("Aucun champ de saisie modifiable n'a le focus sur la TV (ou l'app affichée refuse la saisie par l'accessibilité).")
        }

        override fun global(g: RemoteGlobal): Outcome {
            val s = RemoteAccessibilityService.instance
                ?: return Outcome.refused("Mode « toute la TV » inactif : activez « CastBridge Télécommande » dans Réglages > Accessibilité de la TV.")
            if (g == RemoteGlobal.POWER_DIALOG && Build.VERSION.SDK_INT < 21) return Outcome.refused("indisponible")
            return if (onMain { s.global(g.action) } == true) Outcome.done("system")
            else Outcome.refused("La TV a refusé « ${g.label} » (ce lanceur ne le permet pas).")
        }

        override fun openSetup(): Outcome {
            val s = svc ?: return Outcome.refused("CastBridge TV démarre…")
            val i = Intent(s, RemoteSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val a = front()
            if (a != null) { onMain { a.startActivity(i) }; return Outcome.done("app") }
            val why = s.launchScreen(i, "Le téléphone propose d'activer la télécommande « toute la TV »")
            return if (why == null) Outcome.done("app") else Outcome.refused(why)
        }

        override fun stateJson(): String {
            val s = svc
            val a = front()
            val text = a?.let { act -> onMain(300) { focusedEditor(focusedRoot(act)) != null } } ?: false
            val am = audio
            val vol = runCatching { am?.let { m -> val max = m.getStreamMaxVolume(AudioManager.STREAM_MUSIC); if (max > 0) m.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else null } }.getOrNull()
            val muted = runCatching { am?.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrNull()
            return buildString {
                append("{\"screen\":").append(a?.let { q(it.javaClass.simpleName) } ?: "null")
                append(",\"castbridgeFront\":").append(a != null)
                append(",\"textField\":").append(text)
                append(",\"system\":{\"enabled\":").append(s?.let { systemEnabled(it) } ?: false)
                append(",\"connected\":").append(RemoteAccessibilityService.instance != null)
                append(",\"dpadGlobal\":").append(Build.VERSION.SDK_INT >= 33).append('}')
                append(",\"volume\":").append(vol ?: "null")
                append(",\"muted\":").append(muted ?: "null")
                append(",\"volumeFixed\":").append(runCatching { am?.isVolumeFixed }.getOrNull() ?: false)
                append(",\"sdk\":").append(Build.VERSION.SDK_INT)
                append(",\"keys\":[").append(RemoteKey.values().joinToString(",") { q(it.wire) }).append("]}")
            }
        }
    }
}
