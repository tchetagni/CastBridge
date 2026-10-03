package castbridge.receiver

import android.content.Context
import castbridge.core.langues.EmbeddedLangSource
import castbridge.core.langues.LangLotConsumer
import castbridge.core.langues.LangPack
import castbridge.core.connect.ServerUrl
import castbridge.core.lots.LotConsumer
import castbridge.core.lots.SecureHttpLotRemote
import castbridge.core.lots.TvLotFetcher
import castbridge.core.update.UpdateKeys
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import java.io.File

/**
 * « Langues » on the TV (docs/LANGUES.md): the installed `langues` text lots (one folder per lot in filesDir/lots/langues) and the
 * [LotConsumer] the lot store uses to install them. Lots only arrive from the phone (the TV is offline). The media twin
 * (`langues-media`: audio, video) is NOT delivered to the TV yet; [mediaFile] looks for a media file in filesDir/lots/langues-media for when it is.
 */
object LanguesHub {
    @Volatile private var app: Context? = null
    @Volatile private var consumer: LangLotConsumer? = null
    /** The screen while it is shown, so a lot that arrives (or is removed) refreshes it. */
    @Volatile var screen: LanguesActivity? = null

    private fun init(ctx: Context) { if (app == null) app = ctx.applicationContext }

    @Synchronized fun lots(ctx: Context): LangLotConsumer { init(ctx); return consumer ?: LangLotConsumer(File(app!!.filesDir, "lots/langues")).also { consumer = it } }

    /** The consumer registered in [LotsHub]: installs / removes, then tells the open screen. */
    fun lotsConsumer(ctx: Context): LotConsumer {
        val d = lots(ctx)
        fun refresh() { forgetCount(); screen?.let { s -> runCatching { s.runOnUiThread { s.reload() } } } }
        return object : LotConsumer by d {
            override fun install(meta: LotMeta, data: File): Boolean = d.install(meta, data).also { if (it) refresh() }
            override fun remove(id: LotId) { d.remove(id); refresh() }
        }
    }

    /**
     * Does the system say this TV has working Internet (its own validation, no traffic from CastBridge-TV)? Same check as the Internet badge of TvService.
     * The « Mettre à jour les lots Langues » button is shown only then.
     */
    fun hasInternet(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.let {
            it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
    }.getOrDefault(false)

    /**
     * The server -> TV downloader, built ONLY when the user presses the button (nothing is scheduled, nothing listens). HTTPS only, no redirect,
     * the server's production key (plus the optional test key of a local build); installs through the same [LotsHub] store as a phone upload.
     * @throws IllegalArgumentException if the configured server address is not HTTPS (a test build pointing at plain http: use the phone)
     */
    fun fetcher(ctx: Context): TvLotFetcher {
        val server = ServerUrl.normalize(castbridge.receiver.TvConnect.link?.state?.baseUrl ?: BuildConfig.DEFAULT_SERVER.ifBlank { null }) ?: ServerUrl.DEFAULT
        val keys = (UpdateKeys.PUBLIC_KEYS + BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() }
        return TvLotFetcher(SecureHttpLotRemote(server), LotsHub.store(ctx), keys, LotsHub.appVersion(ctx), { hasInternet(ctx) })
    }

    /** The free starter bundled in the APK (zh-a0): usable on a fresh TV without any lot. */
    private val starter by lazy { EmbeddedLangSource() }

    /** What the screen lists: the starter + the installed lots (an installed lot of the same name and a version at least as recent replaces the starter). */
    fun packs(ctx: Context): List<LangPack> = EmbeddedLangSource.merge(starter.packs, runCatching { lots(ctx).packs() }.getOrDefault(emptyList()))

    /**
     * Number of packs for the home tile, kept until a lot is installed or removed ([lotsConsumer]). The tile is refreshed every 4 s on the main thread:
     * counting meant opening and parsing EVERY installed lot zip, twice (status + hasContent) — 46 lots on the owner's TV (docs/agent-reports/tv-perf.md, R-11).
     */
    @Volatile private var packCount: Int? = null
    private fun count(ctx: Context): Int = packCount ?: runCatching { packs(ctx).size }.getOrDefault(0).also { packCount = it }
    private fun forgetCount() { packCount = null }

    /** Status line of the home tile. */
    fun status(ctx: Context): String {
        val n = count(ctx)
        return if (n == 0) "Aucune langue" else "$n pack(s)"
    }

    /** True when something can be learnt (starter or lot). */
    fun hasContent(ctx: Context): Boolean = count(ctx) > 0

    /** A media file referenced as `m:<id>` by [file] (the name in media.json), if a media lot holding it is installed; null otherwise. */
    fun mediaFile(ctx: Context, mediaScope: String, file: String): File? {
        init(ctx)
        if (file.contains("..") || file.startsWith("/")) return null
        return File(app!!.filesDir, "lots/langues-media/$mediaScope/$file").takeIf { it.isFile }
    }
}
