package castbridge.receiver

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import castbridge.core.tv.LibraryItem
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.MediaType
import castbridge.core.tv.StorageLine
import castbridge.core.tv.VolumeKind
import java.util.concurrent.Executors

/** Colours and sizes of the TV screens (dark, blue accent: the phone app's palette). */
object TvStyle {
    const val BG = 0xFF0E1116.toInt()
    const val CARD = 0xFF1B1F27.toInt()
    const val CARD_FOCUS = 0xFF26303D.toInt()
    const val ACCENT = 0xFF33B5E5.toInt()
    const val MUTED = 0xFFA8AEB8.toInt()
    const val GOOD = 0xFF2E7D32.toInt()

    fun dp(ctx: Context, v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun rounded(ctx: Context, fill: Int, radiusDp: Int = 10, stroke: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat(); setColor(fill); if (strokeDp > 0) setStroke(dp(ctx, strokeDp), stroke)
    }

    /** Zoom + glow when a focusable view gets the D-pad focus (GPU property animations only). */
    fun focusZoom(v: View, scale: Float = 1.1f, onFocus: (Boolean) -> Unit = {}) {
        v.setOnFocusChangeListener { view, has ->
            view.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).translationZ(if (has) dp(view.context, 12).toFloat() else 0f)
                .setDuration(160).start()
            onFocus(has)
        }
    }
}

/**
 * Thumbnails for the TV screens: ~320 px JPEGs from the service's disk cache, decoded as RGB_565 (115 kB each), at most 4 MB in
 * RAM (LRU), one decode at a time. A thumbnail that is not made yet is asked again when the service says it is ready.
 */
class TvThumbs(private val fetch: (name: String, volume: String) -> ByteArray?) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-thumbs-ui").apply { isDaemon = true } }
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
    private val asked = HashSet<String>()

    fun key(i: LibraryItem) = "${i.volumeId}\u0000${i.name}\u0000${i.size}"
    fun cached(i: LibraryItem): Bitmap? = cache.get(key(i))

    /** [done] runs on the main thread with the bitmap, once it is available (not called if it cannot be made). */
    fun load(i: LibraryItem, done: (Bitmap) -> Unit) {
        cache.get(key(i))?.let { done(it); return }
        if (i.type == MediaType.OTHER || !asked.add(key(i))) return
        runCatching {
            io.execute {
                val bytes = runCatching { fetch(i.name, i.volumeId) }.getOrNull() ?: return@execute
                val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) }.getOrNull()
                    ?: return@execute
                main.post { cache.put(key(i), bmp); done(bmp) }
            }
        }
    }

    /** The service made the thumbnail of [name]: the next [load] asks for it again. */
    fun ready(name: String) { asked.removeAll { it.contains("\u0000$name\u0000") } }

    fun trim() = cache.evictAll()
    fun release() { cache.evictAll(); io.shutdownNow() }
}

/**
 * A media card: 16:9 thumbnail (placeholder with the file type while it is being made), badges (drive/internal, "VU",
 * duration), resume bar, two-line title and a detail line. The focus zooms the card and lights a blue frame around it.
 */
class MediaCard(ctx: Context, private val widthPx: Int = ViewGroup.LayoutParams.MATCH_PARENT) : LinearLayout(ctx) {
    val image = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val placeholder = TextView(ctx).apply { setTextColor(0x66FFFFFF); textSize = 34f; gravity = Gravity.CENTER }
    private val volume = badge(0xCC12384A.toInt())
    private val watched = badge(0xCC1B5E20.toInt()).apply { text = "VU" }
    private val duration = badge(0xCC000000.toInt())
    private val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(TvStyle.ACCENT)
    }
    val title = TextView(ctx).apply {
        setTextColor(Color.WHITE); textSize = 17f; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        setPadding(TvStyle.dp(ctx, 10), TvStyle.dp(ctx, 8), TvStyle.dp(ctx, 10), 0)
    }
    private val sub = TextView(ctx).apply { setTextColor(TvStyle.MUTED); textSize = 13f; maxLines = 1; setPadding(TvStyle.dp(ctx, 10), TvStyle.dp(ctx, 2), TvStyle.dp(ctx, 10), TvStyle.dp(ctx, 10)) }
    var item: LibraryItem? = null; private set

    init {
        orientation = VERTICAL
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true; isLongClickable = true
        clipToOutline = true
        val m = TvStyle.dp(ctx, 10)
        layoutParams = ViewGroup.MarginLayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(m, m, m, m) }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(ctx, TvStyle.CARD_FOCUS, 12, TvStyle.ACCENT, 3))
            addState(intArrayOf(), TvStyle.rounded(ctx, TvStyle.CARD, 12))
        }
        val pic = Aspect169(ctx)
        pic.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
        pic.addView(image, FrameLayout.LayoutParams(-1, -1))
        val b = TvStyle.dp(ctx, 8)
        pic.addView(volume, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(b, b, 0, 0) })
        pic.addView(watched, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, b, b, 0) })
        pic.addView(duration, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, b, b + 4) })
        pic.addView(progress, FrameLayout.LayoutParams(-1, TvStyle.dp(ctx, 5), Gravity.BOTTOM))
        addView(pic, LayoutParams(-1, -2))
        addView(title, LayoutParams(-1, -2))
        addView(sub, LayoutParams(-1, -2))
    }

    fun bind(i: LibraryItem, thumbs: TvThumbs) {
        item = i
        val m = i.meta
        title.text = i.title
        sub.text = buildString {
            if (m.durationMs > 0) append(LibraryLogic.clock(m.durationMs)).append("  ·  ")
            append(StorageLine.size(i.size))
        }
        volume.text = when (i.volumeKind) { VolumeKind.INTERNAL -> "Interne"; VolumeKind.SAF -> "Dossier"; else -> "Clé USB" }
        watched.visibility = if (m.watched) View.VISIBLE else View.GONE
        duration.visibility = if (m.durationMs > 0) View.VISIBLE else View.GONE
        duration.text = LibraryLogic.clock(m.durationMs)
        val p = if (m.resumeMs > 0 && !m.watched) LibraryLogic.progress(m.resumeMs, m.durationMs) else 0f
        progress.visibility = if (p > 0f) View.VISIBLE else View.GONE
        progress.progress = (p * 1000).toInt()
        placeholder.text = when (i.type) { MediaType.VIDEO -> "▶"; MediaType.AUDIO -> "♪"; MediaType.OTHER -> i.name.substringAfterLast('.', "?").uppercase().take(4) }
        val bmp = thumbs.cached(i)
        image.setImageBitmap(bmp); placeholder.visibility = if (bmp == null) View.VISIBLE else View.GONE
        if (bmp == null) thumbs.load(i) { b -> if (item == i) { image.alpha = 0f; image.setImageBitmap(b); placeholder.visibility = View.GONE; image.animate().alpha(1f).setDuration(200).start() } }
    }

    private fun badge(color: Int) = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        setPadding(TvStyle.dp(context, 7), TvStyle.dp(context, 2), TvStyle.dp(context, 7), TvStyle.dp(context, 2)); background = TvStyle.rounded(context, color, 6)
    }

    /** Keeps the thumbnail area at 16:9 of the card width. */
    private class Aspect169(ctx: Context) : FrameLayout(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            val width = MeasureSpec.getSize(w)
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY))
        }
    }
}

/** A tool tile (icon glyph + label), same size and focus as a media card. */
class ToolTile(ctx: Context, glyph: String, label: String, widthPx: Int) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        val m = TvStyle.dp(ctx, 10)
        layoutParams = ViewGroup.MarginLayoutParams(widthPx, widthPx * 9 / 16 + TvStyle.dp(ctx, 58)).apply { setMargins(m, m, m, m) }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(ctx, TvStyle.CARD_FOCUS, 12, TvStyle.ACCENT, 3))
            addState(intArrayOf(), TvStyle.rounded(ctx, 0xCC1B1F27.toInt(), 12))
        }
        addView(TextView(ctx).apply { text = glyph; textSize = 40f; setTextColor(TvStyle.ACCENT); gravity = Gravity.CENTER })
        addView(TextView(ctx).apply { text = label; textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(m, m / 2, m, 0) })
        TvStyle.focusZoom(this)
    }
}

/** Plain-language banner that slides in at the top ("Vidéo reçue ✓", "Clé USB branchée : 57 Go libres"), then fades away. */
class Banner(private val parent: FrameLayout) {
    private val main = Handler(Looper.getMainLooper())
    private val ctx = parent.context
    private val view = TextView(ctx).apply {
        setTextColor(Color.WHITE); textSize = 19f; gravity = Gravity.CENTER
        val p = TvStyle.dp(ctx, 14); setPadding(p * 2, p, p * 2, p)
        background = TvStyle.rounded(ctx, 0xE61B1F27.toInt(), 28, TvStyle.ACCENT, 1)
        visibility = View.GONE; elevation = TvStyle.dp(ctx, 16).toFloat()
    }
    private val hide = Runnable {
        view.animate().alpha(0f).translationY(-TvStyle.dp(ctx, 30).toFloat()).setDuration(250).withEndAction { view.visibility = View.GONE }.start()
    }

    init { parent.addView(view, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = TvStyle.dp(ctx, 28) }) }

    fun show(text: String, ms: Long = 3000) {
        view.text = text
        main.removeCallbacks(hide)
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f; view.translationY = -TvStyle.dp(ctx, 30).toFloat(); view.visibility = View.VISIBLE
            view.animate().alpha(1f).translationY(0f).setDuration(250).start()
        }
        view.bringToFront()
        main.postDelayed(hide, ms)
    }
}

/** Fades a view in or out (screen changes: home <-> playback <-> library). */
fun View.fadeTo(visible: Boolean, ms: Long = 220) {
    animate().cancel()
    if (visible) {
        if (visibility != View.VISIBLE) { alpha = 0f; visibility = View.VISIBLE }
        animate().alpha(1f).setDuration(ms).start()
    } else if (visibility == View.VISIBLE) {
        animate().alpha(0f).setDuration(ms).withEndAction { visibility = View.GONE; alpha = 1f }.start()
    }
}

/** A slow back-and-forth zoom of a background image ("Ken Burns"), GPU only; [stop] cancels it. */
class SlowZoom(private val v: View) {
    private val anim = ValueAnimator.ofFloat(1f, 1.12f).apply {
        duration = 30_000; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { a -> val s = a.animatedValue as Float; v.scaleX = s; v.scaleY = s; v.translationX = (s - 1f) * v.width * 0.15f }
    }
    fun start() { if (!anim.isStarted) anim.start() }
    fun stop() { anim.cancel() }
}
