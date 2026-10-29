package castbridge.receiver

import android.app.Application

/**
 * Process start of CastBridge TV: the crash recorder (a small file written at once, sent to the server at the next start)
 * and the server link used by every screen for its usage events. The link itself starts working from [TvService].
 */
class TvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TvFonts.init(this)
        runCatching { TvStyle.load(this) }                              // thème de la TV (clair par défaut) avant le premier écran
        TvConnect.installCrashHandler(this)
        runCatching { TvConnect.init(this) }.onFailure { TvConnect.logw("init: ${it.javaClass.simpleName}") }
        registerActivityLifecycleCallbacks(TvConnect.lifecycle)
        // Fond de fenêtre du thème choisi sur les écrans secondaires (pas de flash sombre sur le thème clair) ; le lecteur et les jeux restent sombres.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private val dark = setOf(PlayerActivity::class.java, QuizActivity::class.java, ChessActivity::class.java, SudokuActivity::class.java, GameActivity::class.java,
                LearnActivity::class.java, ActivationActivity::class.java, FilePickActivity::class.java, PairActivity::class.java)
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {
                if (a.javaClass !in dark) runCatching { a.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(TvStyle.BG)) }
            }
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        ParentalHub.init(this)                                          // parental control: local only (docs/PARENTAL.md)
        registerActivityLifecycleCallbacks(ParentalHub.lifecycle)
        registerActivityLifecycleCallbacks(KeyBadgeOverlay)               // licence label: 15 s on the home, the red one (key over) on every screen
        runCatching { UsbVolumeWatch.start(this) }                       // the state of every USB key as Android says it (checking, mounted, unmountable, pulled): docs/STORAGE.md
    }
}
