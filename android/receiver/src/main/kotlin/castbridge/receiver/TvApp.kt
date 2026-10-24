package castbridge.receiver

import android.app.Application

/**
 * Process start of CastBridge TV: the crash recorder (a small file written at once, sent to the server at the next start)
 * and the server link used by every screen for its usage events. The link itself starts working from [TvService].
 */
class TvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        castbridge.core.brand.Brand.init(BuildConfig.BRAND_ID, BuildConfig.BRAND_NAME, BuildConfig.BRAND_TV_NAME, BuildConfig.BRAND_OWNER, BuildConfig.BRAND_TAGLINE, BuildConfig.SUPPORT_PHONE, BuildConfig.BRAND_PALETTE, BuildConfig.BRAND_PHONE_THEME, BuildConfig.BRAND_MARKETED_BY, BuildConfig.BRAND_LABELS)   // marque blanche (docs/WHITE-LABEL.md)
        if (castbridge.core.brand.Brand.isCustom) registerActivityLifecycleCallbacks(BrandViews)   // texte vu : la marque à la place de CastBridge (aucun effet pour CastBridge)
        TvFonts.init(this)
        TvConnect.installCrashHandler(this)
        runCatching { TvConnect.init(this) }.onFailure { TvConnect.logw("init: ${it.javaClass.simpleName}") }
        registerActivityLifecycleCallbacks(TvConnect.lifecycle)
        ParentalHub.init(this)                                          // parental control: local only (docs/PARENTAL.md)
        registerActivityLifecycleCallbacks(ParentalHub.lifecycle)
        registerActivityLifecycleCallbacks(KeyBadgeOverlay)               // licence label: 15 s on the home, the red one (key over) on every screen
        runCatching { UsbVolumeWatch.start(this) }                       // the state of every USB key as Android says it (checking, mounted, unmountable, pulled): docs/STORAGE.md
    }
}
