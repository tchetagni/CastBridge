package castbridge.receiver

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import castbridge.core.parental.AppCategory
import castbridge.core.parental.AppEnv
import castbridge.core.parental.InstalledApp

/**
 * What the TV knows about its apps (docs/PARENTAL.md, « Surveillance de toute la TV »): the launcher-visible ones (the leanback
 * launcher included), the launcher(s) and system essentials that can never be blocked, and the Settings packages.
 * Read from PackageManager only, nothing leaves the TV. Android 11+ package visibility is declared with <queries> in the manifest.
 */
object AppCatalog {
    private const val ENV_TTL_MS = 60_000L
    @Volatile private var envCache: AppEnv? = null
    @Volatile private var envAt = 0L

    /** Apps with a launcher icon (TV or phone style), without duplicates. */
    fun launcherApps(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, InstalledApp>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val list = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) }.getOrDefault(emptyList())
            for (ri in list) {
                val info = ri.activityInfo ?: continue
                val pkg = info.packageName ?: continue
                if (pkg in seen) continue
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
                seen[pkg] = InstalledApp(pkg, label, categoryOf(info.applicationInfo, pkg))
            }
        }
        return seen.values.toList()
    }

    @Suppress("DEPRECATION")
    private fun categoryOf(ai: ApplicationInfo?, pkg: String): AppCategory {
        val game = ai != null && (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        val cat = if (ai != null && Build.VERSION.SDK_INT >= 26) ai.category else -1
        return AppCategory.guess(pkg, game, cat)
    }

    /** Friendly name of a package (the label the user sees in the launcher), or the package name when it cannot be resolved. */
    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun pkgsOf(ctx: Context, i: Intent): Set<String> =
        runCatching { ctx.packageManager.queryIntentActivities(i, 0).mapNotNull { it.activityInfo?.packageName }.toSet() }.getOrDefault(emptySet())

    /** CastBridge TV, the home launcher(s) and the system UI are never blockable; Settings and the package installer follow « Réglages ». */
    fun env(ctx: Context): AppEnv {
        val t = System.currentTimeMillis()
        envCache?.let { if (t - envAt < ENV_TTL_MS) return it }
        val homes = pkgsOf(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        val settings = pkgsOf(ctx, Intent(Settings.ACTION_SETTINGS)) + pkgsOf(ctx, Intent(Settings.ACTION_WIFI_SETTINGS)) +
            pkgsOf(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://castbridge.probe/x.apk"), "application/vnd.android.package-archive"))
        val e = AppEnv(ctx.packageName, neverBlock = homes + "com.android.systemui", settingsPkgs = settings - homes - ctx.packageName)
        envCache = e; envAt = t
        return e
    }
}
