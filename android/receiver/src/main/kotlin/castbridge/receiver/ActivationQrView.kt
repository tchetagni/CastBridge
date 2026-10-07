package castbridge.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import castbridge.core.quiz.QrCode
import castbridge.core.tv.activation.ActivationQr

/**
 * The QR of the activation screen: black modules on a white square with its quiet zone, every module a WHOLE number of pixels (no anti-aliasing: crisp edges whatever the panel), the
 * whole square at least a quarter of the screen height ([ActivationQr.layout], pure and tested). A phone camera reads it from the sofa and offers to join the Wi-Fi Direct group.
 */
class ActivationQrView(ctx: Context, private val qr: QrCode) : View(ctx) {
    private val layout = ActivationQr.layout(ctx.resources.displayMetrics.heightPixels, qr.size)
    private val dark = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
    private val light = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = false }

    init { contentDescription = "Code QR du réseau Wi-Fi d'activation de la TV" }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(layout.sidePx, layout.sidePx)

    override fun onDraw(c: Canvas) {
        val side = layout.sidePx.toFloat()
        c.drawRect(0f, 0f, side, side, light)
        val m = layout.modulePx; val q = layout.quietPx
        for (y in 0 until qr.size) {
            var x = 0
            while (x < qr.size) {
                if (!qr[x, y]) { x++; continue }
                var run = 1
                while (x + run < qr.size && qr[x + run, y]) run++            // a run of dark modules is one rectangle
                c.drawRect((q + x * m).toFloat(), (q + y * m).toFloat(), (q + (x + run) * m).toFloat(), (q + (y + 1) * m).toFloat(), dark)
                x += run
            }
        }
    }
}
