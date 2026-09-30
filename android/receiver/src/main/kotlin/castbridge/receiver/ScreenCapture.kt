package castbridge.receiver

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Screenshot of whichever CastBridge TV screen is in front (home, library, player, quiz…), for remote layout checks
 * through GET /api/screenshot (PIN). Only this app's own windows: no permission, nothing from other apps.
 */
object ScreenCapture {
    @Volatile var resumed: Activity? = null; private set
    private val main = Handler(Looper.getMainLooper())

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) { resumed = a }
        override fun onActivityPaused(a: Activity) { if (resumed === a) resumed = null }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) { if (resumed === a) resumed = null }
    })

    /** PNG of [a]'s window; called from an HTTP thread (waits for the main-thread copy). null if it fails. */
    fun capture(a: Activity): ByteArray? {
        val w = a.window ?: return null
        val v = w.decorView
        if (v.width == 0 || v.height == 0) return null
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1); var ok = false
        main.post {
            runCatching { PixelCopy.request(w, bmp, { r -> ok = r == PixelCopy.SUCCESS; done.countDown() }, main) }
                .onFailure { runCatching { v.draw(android.graphics.Canvas(bmp)); ok = true }; done.countDown() }
        }
        if (!done.await(5, TimeUnit.SECONDS) || !ok) { bmp.recycle(); return null }
        return ByteArrayOutputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o); bmp.recycle(); o.toByteArray() }
    }
}
