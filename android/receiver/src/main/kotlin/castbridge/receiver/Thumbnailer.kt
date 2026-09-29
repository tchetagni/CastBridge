package castbridge.receiver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import castbridge.core.tv.LibraryDb
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibraryProvider
import castbridge.core.tv.ThumbCache
import castbridge.core.tv.ThumbJob
import castbridge.core.tv.ThumbResult
import castbridge.core.tv.ThumbWorker
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Thumbnails for the library: ~320 px JPEG from Android's MediaMetadataRetriever (hardware decoder, scaled frame, so no
 * full-size bitmap on API 27+), then, if it fails (codec or container it does not know), from libVLC. Called only by the
 * single [ThumbWorker] thread, and only while nothing plays: one decode at a time on this 32-bit, 1 GB TV.
 */
class Thumbnailer(private val ctx: Context) {

    fun generate(job: ThumbJob): ThumbResult? {
        val r = runCatching { fromRetriever(job.file) }.onFailure { Log.w(TAG, "retriever: ${it.javaClass.simpleName}") }.getOrNull()
        if (r?.jpeg != null) return r
        val v = runCatching { fromVlc(job.file, r?.durationMs ?: 0) }.onFailure { Log.w(TAG, "vlc: ${it.javaClass.simpleName}") }.getOrNull()
        return when {
            v?.jpeg != null -> v
            else -> ThumbResult(null, maxOf(r?.durationMs ?: 0, v?.durationMs ?: 0))
        }
    }

    private fun fromRetriever(f: File): ThumbResult {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(f.absolutePath)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val t = LibraryLogic.thumbTimeMs(dur) * 1000
            var bmp: Bitmap? = null
            if (hasVideo) {
                bmp = if (Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
                else mmr.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaled(it) }
            }
            if (bmp == null) bmp = mmr.embeddedPicture?.let { decodeSmall(it) }     // audio: the album cover, if any
            return ThumbResult(bmp?.let { jpeg(it) }, dur)
        } finally { runCatching { mmr.release() } }
    }

    /**
     * libVLC fallback: a throw-away instance (no audio, no window) with the "scene" video filter writing one PNG of the
     * frame at the thumbnail time, software decoding (a MediaCodec surface cannot be read back). Bounded to [TIMEOUT_MS].
     */
    private fun fromVlc(f: File, knownDur: Long): ThumbResult? {
        val out = File(ctx.cacheDir, "vlcthumb").apply { deleteRecursively(); mkdirs() }
        val lv = LibVLC(ctx, arrayListOf("--no-audio", "--no-spu", "--no-osd", "--no-stats", "--vout=dummy",
            "--video-filter=scene", "--scene-format=png", "--scene-ratio=1", "--scene-width=$WIDTH", "--scene-replace",
            "--scene-prefix=thumb", "--scene-path=${out.absolutePath}", "--avcodec-threads=1"))
        val mp = MediaPlayer(lv)
        try {
            val m = Media(lv, f.absolutePath)
            var dur = knownDur
            if (dur <= 0) runCatching { if (m.parse()) dur = m.duration.coerceAtLeast(0) }
            m.setHWDecoderEnabled(false, false)
            m.addOption(":start-time=${LibraryLogic.thumbTimeMs(dur) / 1000.0}")
            m.addOption(":no-audio")
            mp.media = m; m.release()
            mp.play()
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            var png: File? = null
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(150)
                png = out.listFiles()?.firstOrNull { it.name.endsWith(".png") && it.length() > 0 }
                if (png != null) { Thread.sleep(100); break }
            }
            val bmp = png?.let { p -> BitmapFactory.decodeFile(p.absolutePath, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) }
            return ThumbResult(bmp?.let { jpeg(scaled(it)) }, dur)
        } finally {
            runCatching { mp.stop() }; runCatching { mp.release() }; runCatching { lv.release() }
            out.deleteRecursively()
        }
    }

    private fun decodeSmall(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= WIDTH) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 })
            ?.let { scaled(it) }
    }

    private fun scaled(b: Bitmap): Bitmap {
        if (b.width <= WIDTH) return b
        val h = (b.height.toLong() * WIDTH / b.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(b, WIDTH, h, true).also { if (it !== b) b.recycle() }
    }

    private fun jpeg(b: Bitmap): ByteArray = ByteArrayOutputStream(24 * 1024).use { o ->
        b.compress(Bitmap.CompressFormat.JPEG, 75, o); b.recycle(); o.toByteArray()
    }

    companion object {
        private const val TAG = "CastBridgeThumb"
        const val WIDTH = 320
        const val HEIGHT = 180
        private const val TIMEOUT_MS = 8_000L

        /** The TV library backend: saved positions in the app's files, thumbnails in its cache (20 MB at most). */
        fun provider(ctx: Context, canRun: () -> Boolean, onThumb: (String) -> Unit): LibraryProvider {
            val t = Thumbnailer(ctx.applicationContext)
            return LibraryProvider(LibraryDb(File(ctx.filesDir, "library.db")), ThumbCache(File(ctx.cacheDir, "thumbs"), 20L shl 20)) { p ->
                ThumbWorker(p.cache, t::generate, canRun, onDone = { j, r -> p.onGenerated(j, r); onThumb(j.name) })
            }
        }
    }
}
