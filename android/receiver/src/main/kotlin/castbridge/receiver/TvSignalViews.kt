package castbridge.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.tv.VolumeKind
import castbridge.core.ux.BtState
import castbridge.core.ux.Indicator
import castbridge.core.ux.IndicatorKind
import castbridge.core.ux.LanKind
import castbridge.core.ux.Shape
import castbridge.core.ux.SignalColors
import castbridge.core.ux.SignalLevel
import castbridge.core.ux.TvFacts
import castbridge.core.ux.TvSignal
import castbridge.core.ux.TvSignalView

/** Pastille de signalétique dessinée (cercle / triangle / carré / anneau) : la forme porte le sens même sans les couleurs, et ne dépend d'aucune police. */
class SignalDrawable(private val level: SignalLevel, private val px: Int) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px / 7f; color = SignalColors.BLACK_OUTLINE }
    override fun getIntrinsicWidth() = px
    override fun getIntrinsicHeight() = px
    override fun draw(c: Canvas) {
        val r = RectF(bounds).apply { inset(px / 8f, px / 8f) }
        fill.color = if (level == SignalLevel.BLACK) SignalColors.BLACK_FILL else SignalColors.of(level)
        when (level.shape) {
            Shape.CIRCLE -> c.drawOval(r, fill)
            Shape.SQUARE -> c.drawRoundRect(r, px / 6f, px / 6f, fill)
            Shape.RING -> { c.drawOval(r, fill); c.drawOval(r, line) }
            Shape.TRIANGLE -> c.drawPath(Path().apply { moveTo(r.centerX(), r.top); lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close() }, fill)
        }
    }
    override fun setAlpha(a: Int) { fill.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { fill.colorFilter = f }
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

object TvSignalViews {
    fun drawable(ctx: Context, level: SignalLevel, dp: Int = 14): Drawable = SignalDrawable(level, TvStyle.dp(ctx, dp))

    /** Texte coloré d'un indicateur : pastille + mot court (jamais la couleur seule). */
    fun style(t: TextView, level: SignalLevel, sizeDp: Int = 14) {
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable(t.context, level, sizeDp), null, null, null)
        t.compoundDrawablePadding = TvStyle.dp(t.context, 8)
        t.setTextColor(SignalColors.TEXT)
    }

    /** La rangée d'indicateurs (hors Réception : la puce) et, sous elle, l'action unique quand la puce n'est pas verte. */
    class Row(ctx: Context) : LinearLayout(ctx) {
        private val line = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        private val action = TextView(ctx).apply { textSize = TvStyle.Type.CAPTION; maxLines = 2; visibility = GONE; setPadding(0, TvStyle.dp(ctx, 4), 0, 0) }
        init { orientation = VERTICAL; addView(line, LayoutParams(-1, -2)); addView(action, LayoutParams(-1, -2)) }

        fun render(v: TvSignalView) {
            line.removeAllViews()
            v.indicators.filter { it.kind != IndicatorKind.RECEPTION }.forEach { i: Indicator ->
                line.addView(TextView(context).apply { text = i.short; textSize = TvStyle.Type.CAPTION; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; style(this, i.level) },
                    LayoutParams(0, -2, 1f).apply { rightMargin = TvStyle.dp(context, 12) })
            }
            if (v.level == SignalLevel.GREEN || v.action == null) action.visibility = GONE
            else { action.visibility = VISIBLE; action.text = v.action; action.setTextColor(SignalColors.of(v.level)) }
        }
    }

    /** Relevé des faits de la TV (mince : aucune décision ici, tout est décidé par [TvSignal.of]). */
    @Suppress("DEPRECATION")
    fun facts(ctx: Context, svc: TvService?, listening: Boolean): TvFacts {
        // Any network with a LAN transport and an IPv4 address counts (not only the system's default one: mobile data may be the default and is no LAN).
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        fun lanWith(transport: Int) = runCatching {
            cm.allNetworks.any { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(transport) == true &&
                    cm.getLinkProperties(n)?.linkAddresses?.any { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress } == true
            }
        }.getOrDefault(false)
        val ethernetUp = lanWith(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        val wifiUp = lanWith(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        val wm = runCatching { ctx.applicationContext.getSystemService(WifiManager::class.java) }.getOrNull()
        val info = runCatching { wm?.connectionInfo }.getOrNull()
        val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && !it.startsWith("<unknown") }
        val associated = info != null && info.networkId != -1 && info.ipAddress == 0
        val lan = when {
            ethernetUp -> LanKind.ETHERNET
            wifiUp -> LanKind.WIFI
            associated -> LanKind.WIFI
            else -> LanKind.NONE
        }
        val weak = lan == LanKind.WIFI && info != null && info.rssi in -99..-80
        val ad = runCatching { android.bluetooth.BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        val bt = when {
            ad == null || !TvService.hasBtPermission(ctx) -> BtState.UNUSABLE
            runCatching { ad.isEnabled }.getOrDefault(false) -> BtState.ON
            else -> BtState.OFF
        }
        val vols = runCatching { svc?.registry?.volumes().orEmpty().filter { it.writable } }.getOrDefault(emptyList())
        val free = vols.map { it.free }.filter { it >= 0 }.maxOrNull() ?: -1L
        val freeText = if (free < 0) null else if (free >= (1L shl 30)) "${free / (1L shl 30)} Go" else "${free shr 20} Mo"
        return TvFacts(
            lan = lan, wifiNoAddress = lan == LanKind.WIFI && !wifiUp, wifiName = ssid, weakSignal = weak,
            bluetooth = bt, pairedPhones = svc?.trust?.list()?.size ?: 0, listening = listening,
            storage = TvSignal.storageOf(free), freeText = freeText,
            usbKey = runCatching { svc?.registry?.volumes().orEmpty().any { it.kind == VolumeKind.REMOVABLE } }.getOrDefault(false),
            internet = svc?.let { it.netDirectMs != null || it.netGatewayMs != null } == true,
        )
    }
}
