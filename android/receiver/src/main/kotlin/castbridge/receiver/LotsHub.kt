package castbridge.receiver

import android.content.Context
import android.os.Build
import castbridge.core.lots.*
import castbridge.core.tv.ApiExtension
import castbridge.core.update.UpdateKeys
import java.io.File

/**
 * « Lots » on the TV (docs/LOTS.md): the Apprendre / Quiz data the phone pushes to this TV, under a STRICT cap of 10 Mo
 * (the starter data bundled in the APK counts in it). The TV is assumed OFFLINE: it NEVER downloads a lot from the Internet and
 * never contacts the server for lots; everything arrives from the phone (PIN / trusted-phone routes /api/lots/…, or Bluetooth
 * files adopted from the reception folder), is verified (server-signed catalog, size, SHA-256) and installed by the feature's
 * [LotConsumer]. Until a feature registers its consumer ([register]), the starter data keeps working unchanged and pushed lots
 * are refused with a clear reason.
 */
object LotsHub {
    private val registered = ArrayList<LotConsumer>()
    @Volatile private var storeRef: TvLotStore? = null

    /** A feature plugs its [LotConsumer] here (before the first use, e.g. from its hub's init). */
    @Synchronized fun register(consumer: LotConsumer) { registered.removeAll { it.feature == consumer.feature }; registered += consumer }

    @Synchronized fun store(ctx: Context): TvLotStore = storeRef ?: run {
        val app = ctx.applicationContext
        val pi = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION") val code = pi?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else it.versionCode } ?: 0
        val consumers = listOf("learn", "quiz").associateWith { f -> registered.firstOrNull { it.feature == f } ?: StarterOnlyConsumer(f) } +
            registered.associateBy { it.feature }
        TvLotStore(File(app.filesDir, "lots"), consumers, (UpdateKeys.PUBLIC_KEYS + BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() }, code,
            starterBytes = { StarterBudget.bytes }).also { storeRef = it }
    }

    /** PIN routes /api/lots/… (the existing authentication applies: nothing here is public). */
    fun api(ctx: Context): ApiExtension = TvLotApi(store(ctx))

    /** At service start: back under the cap, stale partial files removed, Bluetooth deliveries adopted. */
    fun startup(ctx: Context, receivedDir: File) {
        val s = store(ctx)
        runCatching { s.startup() }
        runCatching { s.adoptFrom(receivedDir) }
    }

    /** After a Bluetooth transfer: the lot and its proof, if both arrived, are verified and installed. */
    fun adopt(ctx: Context, receivedDir: File) { runCatching { store(ctx).adoptFrom(receivedDir) } }

    /** « 4,2 Mo utilisés sur 10 Mo — 1,3 Mo de données reçues du téléphone » for the settings screen. */
    fun budgetText(ctx: Context): String {
        val m = store(ctx).manifest()
        val received = m.lots.sumOf { it.meta.bytes }
        return "${LotStore.mo(m.usedBytes)} utilisés sur ${LotStore.mo(m.maxBytes)} (livrés avec l'application : ${LotStore.mo(m.starterBytes)}, reçus du téléphone : ${LotStore.mo(received)})" +
            if (store(ctx).overBudget()) " — place dépassée : mettre CastBridge TV à jour" else ""
    }

    /** What the phone delivered and how old it is, or the plain statement that the starter data is used (no Internet needed). */
    fun ageText(ctx: Context): String {
        val lots = store(ctx).manifest().lots
        if (lots.isEmpty()) return "Aucune donnée reçue du téléphone : les leçons et questions livrées avec l'application sont utilisées."
        val newest = lots.maxOf { it.installedAt }
        return "${lots.size} lot(s) reçu(s) du téléphone, le plus récent ${LotStatusText.age(newest, System.currentTimeMillis())}"
    }
}
