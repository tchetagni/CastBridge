package castbridge.receiver

import android.app.Application

/**
 * Process start of CastBridge TV: the crash recorder (a small file written at once, sent to the server at the next start)
 * and the server link used by every screen for its usage events. The link itself starts working from [TvService].
 */
class TvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TvConnect.installCrashHandler(this)
        runCatching { TvConnect.init(this) }.onFailure { TvConnect.logw("init: ${it.javaClass.simpleName}") }
        registerActivityLifecycleCallbacks(TvConnect.lifecycle)
    }
}
