package castbridge.receiver

import android.content.Context
import castbridge.core.langues.EmbeddedLangSource
import castbridge.core.langues.LangLotConsumer
import castbridge.core.langues.LangPack
import castbridge.core.lots.LotConsumer
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
        fun refresh() { screen?.let { s -> runCatching { s.runOnUiThread { s.reload() } } } }
        return object : LotConsumer by d {
            override fun install(meta: LotMeta, data: File): Boolean = d.install(meta, data).also { if (it) refresh() }
            override fun remove(id: LotId) { d.remove(id); refresh() }
        }
    }

    /** The free starter bundled in the APK (zh-a0): usable on a fresh TV without any lot. */
    private val starter by lazy { EmbeddedLangSource() }

    /** What the screen lists: the starter + the installed lots (an installed lot of the same name and a version at least as recent replaces the starter). */
    fun packs(ctx: Context): List<LangPack> = EmbeddedLangSource.merge(starter.packs, runCatching { lots(ctx).packs() }.getOrDefault(emptyList()))

    /** Status line of the home tile. */
    fun status(ctx: Context): String {
        val n = runCatching { packs(ctx).size }.getOrDefault(0)
        return if (n == 0) "Aucune langue" else "$n pack(s)"
    }

    /** True when something can be learnt (starter or lot). */
    fun hasContent(ctx: Context): Boolean = runCatching { packs(ctx).isNotEmpty() }.getOrDefault(false)

    /** A media file referenced as `m:<id>` by [file] (the name in media.json), if a media lot holding it is installed; null otherwise. */
    fun mediaFile(ctx: Context, mediaScope: String, file: String): File? {
        init(ctx)
        if (file.contains("..") || file.startsWith("/")) return null
        return File(app!!.filesDir, "lots/langues-media/$mediaScope/$file").takeIf { it.isFile }
    }
}
