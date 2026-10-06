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

/**
 * Colours, sizes and motion of the TV screens: the charte graphique (branding/design-tokens.json, generated BrandTokens).
 * Dark theme first; gold primary; focus = 3 dp ring #FFE1A6 + scale 1.04. Text sizes are the TV scale of the guide (px on a
 * 1920 canvas) converted to sp for the 1280x720 dp canvas of the TV (x 2/3): never under 16 sp (= 24 px at 1080p).
 */
object TvStyle {
    private val T = castbridge.core.brand.BrandTokens
    const val BG = castbridge.core.brand.BrandTokens.Dark.BACKGROUND
    const val BG_ELEVATED = castbridge.core.brand.BrandTokens.Dark.BACKGROUND_ELEVATED
    const val CARD = castbridge.core.brand.BrandTokens.Dark.SURFACE
    const val CARD_FOCUS = castbridge.core.brand.BrandTokens.Dark.SURFACE_HIGH
    const val OUTLINE = castbridge.core.brand.BrandTokens.Dark.OUTLINE
    const val TEXT = castbridge.core.brand.BrandTokens.Dark.TEXT_HIGH
    const val TEXT2 = castbridge.core.brand.BrandTokens.Dark.TEXT_MEDIUM
    const val TEXT3 = castbridge.core.brand.BrandTokens.Dark.TEXT_LOW
    /** Primary: gold. Used for accents, selection and progress (the focus ring is [RING]). */
    const val ACCENT = castbridge.core.brand.BrandTokens.Dark.PRIMARY
    const val ON_ACCENT = castbridge.core.brand.BrandTokens.Dark.ON_PRIMARY
    const val RING = castbridge.core.brand.BrandTokens.Dark.FOCUS_RING
    const val MUTED = castbridge.core.brand.BrandTokens.Dark.TEXT_MEDIUM
    const val GOOD = castbridge.core.brand.BrandTokens.Dark.SECONDARY
    const val GOOD_TEXT = castbridge.core.brand.BrandTokens.Semantic.SUCCESS_DARK
    const val ERROR = castbridge.core.brand.BrandTokens.Semantic.ERROR_DARK
    const val INFO = castbridge.core.brand.BrandTokens.Semantic.INFO_DARK

    /** Corner radii (dp): sm 8, md 12, lg 16, xl 24. */
    const val R_SM = castbridge.core.brand.BrandTokens.RADIUS_SM_DP
    const val R_MD = castbridge.core.brand.BrandTokens.RADIUS_MD_DP
    const val R_LG = castbridge.core.brand.BrandTokens.RADIUS_LG_DP
    const val R_XL = castbridge.core.brand.BrandTokens.RADIUS_XL_DP
    const val R_PILL = 999

    /** Motion (ms). */
    const val FAST = castbridge.core.brand.BrandTokens.DURATION_FAST_MS
    const val BASE = castbridge.core.brand.BrandTokens.DURATION_BASE_MS
    const val SLOW = castbridge.core.brand.BrandTokens.DURATION_SLOW_MS

    /** TV type scale (sp on the 1280 dp canvas = guide px x 2/3). */
    object Type {
        const val DISPLAY = 48f      // 72 px
        const val HEADLINE = 32f     // 48 px
        const val TITLE = 27f        // 40 px
        const val SUBTITLE = 21f     // 32 px
        const val BODY = 19f         // 28 px
        const val BUTTON = 19f       // 28 px
        const val CAPTION = 16f      // 24 px = guide minimum
    }

    fun easing(kind: String = "standard"): android.view.animation.Interpolator {
        val e = when (kind) { "decelerate" -> T.EASING_DECELERATE; "accelerate" -> T.EASING_ACCELERATE; else -> T.EASING_STANDARD }
        return android.view.animation.PathInterpolator(e[0], e[1], e[2], e[3])
    }

    /** Titles (Bricolage Grotesque), body (Inter): see [TvFonts]. */
    fun display(ctx: Context): Typeface = TvFonts.display(ctx)
    fun body(ctx: Context): Typeface = TvFonts.body(ctx)

    /** A logo of the charte (VectorDrawable of branding/logo), [heightDp] high, width from its own aspect ratio. */
    fun logo(ctx: Context, res: Int, heightDp: Int) = ImageView(ctx).apply {
        setImageResource(res); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_START
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, heightDp))
    }

    fun dp(ctx: Context, v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun rounded(ctx: Context, fill: Int, radiusDp: Int = R_MD, stroke: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(ctx, radiusDp).toFloat(); setColor(fill); if (strokeDp > 0) setStroke(dp(ctx, strokeDp), stroke)
    }

    /** The focus look of the charte: ring (3 dp #FFE1A6) on [fill], the ring is drawn by the state drawable of the view. */
    fun focusable(ctx: Context, fill: Int, radiusDp: Int = R_MD, focusFill: Int = CARD_FOCUS): android.graphics.drawable.StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(ctx, focusFill, radiusDp, RING, T.FOCUS_RING_WIDTH_DP))
            addState(intArrayOf(), rounded(ctx, fill, radiusDp))
        }

    /** A charte button (surface-high fill, ring on focus, scale 1.04) for the classic android.widget.Button of the secondary screens. */
    fun styleButton(b: android.widget.Button) = b.apply {
        isAllCaps = false; typeface = TvFonts.bold; setTextColor(TEXT); textSize = Type.BUTTON
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(context, CARD_FOCUS, R_MD, RING, T.FOCUS_RING_WIDTH_DP))
            addState(intArrayOf(), rounded(context, CARD, R_MD, OUTLINE, 1))
        }
        setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 10)); stateListAnimator = null
        focusZoom(this)
    }

    /** Scale 1.04 + lift when a focusable view gets the D-pad focus (GPU property animations only, 120 ms, standard curve). */
    fun focusZoom(v: View, scale: Float = T.FOCUS_SCALE, onFocus: (Boolean) -> Unit = {}) {
        v.setOnFocusChangeListener { view, has ->
            if (!ResourceProfiles.peek().homeAnimations) { onFocus(has); return@setOnFocusChangeListener }   // TV à faibles ressources : le cadre bleu suffit, ni zoom ni animation
            view.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).translationZ(if (has) dp(view.context, 12).toFloat() else 0f)
                .setDuration(FAST.toLong()).setInterpolator(easing()).start()
            onFocus(has)
        }
    }
}

/**
 * Thumbnails for the TV screens: ~320 px JPEGs from the service's disk cache, decoded as RGB_565 (115 kB each), at most 4 MB (1 to 3 MB on a low-resource TV, ResourceProfile.thumbCacheBytes) in
 * RAM (LRU), one decode at a time. A thumbnail that is not made yet is asked again when the service says it is ready.
 */
class TvThumbs(private val cacheBytes: Int = 4 * 1024 * 1024, private val fetch: (name: String, volume: String) -> ByteArray?) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-thumbs-ui").apply { isDaemon = true } }
    private val cache = object : LruCache<String, Bitmap>(cacheBytes) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
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
    private val volume = badge(0xCC1B2542.toInt())
    private val watched = badge(0xE61E6B48.toInt()).apply { text = "VU" }
    private val duration = badge(0xCC000000.toInt())
    private val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(TvStyle.ACCENT)
    }
    val title = TextView(ctx).apply {
        setTextColor(Color.WHITE); textSize = TvStyle.Type.BODY; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        setPadding(TvStyle.dp(ctx, 10), TvStyle.dp(ctx, 8), TvStyle.dp(ctx, 10), 0)
    }
    private val sub = TextView(ctx).apply { setTextColor(TvStyle.MUTED); textSize = TvStyle.Type.CAPTION; maxLines = 1; setPadding(TvStyle.dp(ctx, 10), TvStyle.dp(ctx, 2), TvStyle.dp(ctx, 10), TvStyle.dp(ctx, 10)) }
    var item: LibraryItem? = null; private set

    init {
        orientation = VERTICAL
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true; isLongClickable = true
        clipToOutline = true
        val m = TvStyle.dp(ctx, 10)
        layoutParams = ViewGroup.MarginLayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(m, m, m, m) }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(ctx, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
            addState(intArrayOf(), TvStyle.rounded(ctx, TvStyle.CARD, TvStyle.R_MD))
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
        if (bmp == null) thumbs.load(i) { b -> if (item == i) { image.setImageBitmap(b); placeholder.visibility = View.GONE; if (ResourceProfiles.peek().homeAnimations) { image.alpha = 0f; image.animate().alpha(1f).setDuration(200).start() } else image.alpha = 1f } }
    }

    private fun badge(color: Int) = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = TvStyle.Type.CAPTION; typeface = TvFonts.bold
        setPadding(TvStyle.dp(context, 7), TvStyle.dp(context, 2), TvStyle.dp(context, 7), TvStyle.dp(context, 2)); background = TvStyle.rounded(context, color, TvStyle.R_SM)
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
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(ctx, TvStyle.CARD_FOCUS, TvStyle.R_MD, TvStyle.RING, 3))
            addState(intArrayOf(), TvStyle.rounded(ctx, 0xCC151D37.toInt(), TvStyle.R_MD))
        }
        addView(TextView(ctx).apply { text = glyph; textSize = 40f; setTextColor(TvStyle.ACCENT); gravity = Gravity.CENTER })
        addView(TextView(ctx).apply { text = label; textSize = TvStyle.Type.BODY; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(m, m / 2, m, 0) })
        TvStyle.focusZoom(this)
    }
}

/** One feature of the home "Fonctions" row: vector icon, name, live status (e.g. "Prêt", "Actif"), and what OK does. */
/** [id] : identifiant d'accueil (castbridge.core.tv.home.HomeGroups) ; vide = tuile inconnue des groupes, montrée telle quelle sur l'accueil. */
data class HomeTool(val icon: Int, val label: String, val description: String, val status: String?, val on: Boolean, val warn: Boolean = false, val id: String = "", val action: () -> Unit)

/** Icon tile for [HomeTool]: the icon lights up (accent) when the feature is active, and a small status line sits under the name. */
open class IconTile(ctx: Context, tool: HomeTool, widthPx: Int, labelSp: Float = 16f, statusSp: Float = TvStyle.Type.CAPTION, iconDp: Int = 46, heightPx: Int = 0, ringIdle: Boolean = false) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        val m = TvStyle.dp(ctx, 10)
        layoutParams = ViewGroup.MarginLayoutParams(widthPx, if (heightPx > 0) heightPx else widthPx * 3 / 4 + TvStyle.dp(ctx, 40)).apply { setMargins(m, m, m, m) }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), TvStyle.rounded(ctx, TvStyle.CARD_FOCUS, TvStyle.R_LG, TvStyle.RING, 3))
            addState(intArrayOf(), if (ringIdle) TvStyle.rounded(ctx, 0xCC1B2547.toInt(), TvStyle.R_LG, 0x66FFE1A6, 2) else TvStyle.rounded(ctx, 0xCC151D37.toInt(), TvStyle.R_LG))
        }
        val size = TvStyle.dp(ctx, iconDp)
        addView(android.widget.ImageView(ctx).apply {
            setImageResource(tool.icon)
            imageTintList = android.content.res.ColorStateList.valueOf(if (tool.on) TvStyle.ACCENT else TvStyle.TEXT2)
        }, LayoutParams(size, size))
        addView(TextView(ctx).apply { text = tool.label; textSize = labelSp; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 2; setPadding(m, m / 2, m, 0) })
        tool.status?.let { st ->
            addView(TextView(ctx).apply {
                text = (if (tool.warn) "▲ " else if (tool.on) "● " else "") + st; textSize = statusSp; gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (tool.warn) castbridge.core.ux.SignalColors.ORANGE else if (tool.on) TvStyle.GOOD_TEXT else TvStyle.TEXT3)
            })
        }
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
        background = TvStyle.rounded(ctx, 0xE6151D37.toInt(), TvStyle.R_XL, TvStyle.ACCENT, 1)
        visibility = View.GONE; elevation = TvStyle.dp(ctx, 16).toFloat()
    }
    private val hide = Runnable {
        view.animate().alpha(0f).translationY(-TvStyle.dp(ctx, 30).toFloat()).setDuration(250).withEndAction { view.visibility = View.GONE }.start()
    }

    init { parent.addView(view, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = maxOf(TvStyle.dp(ctx, 28), castbridge.core.tv.PlayerIcons.safe(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels).vertical) }) }

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
fun View.fadeTo(visible: Boolean, ms: Long = TvStyle.BASE.toLong()) {
    animate().cancel()
    if (visible) {
        if (visibility != View.VISIBLE) { alpha = 0f; visibility = View.VISIBLE }
        animate().alpha(1f).setDuration(ms).start()
    } else if (visibility == View.VISIBLE) {
        animate().alpha(0f).setDuration(ms).withEndAction { visibility = View.GONE; alpha = 1f }.start()
    }
}

/**
 * A slow back-and-forth zoom of a background image ("Ken Burns"); [stop] cancels it. Stepped every [castbridge.core.ux.SlowZoomCurve.STEP_MS]
 * (4 images/s, under 2 px per step) instead of a 60 images/s animator: the infinite animator redrew the whole home 58 times a second at rest and
 * woke the main thread on every vsync, even under the Quiz (docs/agent-reports/tv-perf.md, R-11).
 */
class SlowZoom(private val v: View) {
    private var startedAt = 0L
    private var running = false
    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            val s = castbridge.core.ux.SlowZoomCurve.scaleAt(android.os.SystemClock.uptimeMillis() - startedAt)
            v.scaleX = s; v.scaleY = s; v.translationX = (s - 1f) * v.width * 0.15f
            v.postDelayed(this, castbridge.core.ux.SlowZoomCurve.STEP_MS)
        }
    }
    fun start() { if (running) return; running = true; startedAt = android.os.SystemClock.uptimeMillis(); v.removeCallbacks(step); v.post(step) }
    fun stop() { running = false; v.removeCallbacks(step) }
}
