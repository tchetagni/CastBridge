package castbridge.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** The activation token as a QR code (read on the owner phone). The token is ASCII on one line, about 900 characters: level L, a generous size. */
object Qr {
    private fun matrix(text: String) = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2))

    fun image(text: String, scale: Int = 6): BufferedImage {
        val m = matrix(text)
        val img = BufferedImage(m.width * scale, m.height * scale, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, if (m.get(x / scale, y / scale)) 0x000000 else 0xffffff)
        return img
    }

    fun png(text: String, file: File, scale: Int = 6) { ImageIO.write(image(text, scale), "png", file) }

    /** For a terminal: two rows of modules per line with half blocks. */
    fun ascii(text: String): String {
        val m = matrix(text)
        val sb = StringBuilder()
        var y = 0
        while (y < m.height) {
            for (x in 0 until m.width) {
                val top = m.get(x, y); val bottom = y + 1 < m.height && m.get(x, y + 1)
                sb.append(when { top && bottom -> '█'; top -> '▀'; bottom -> '▄'; else -> ' ' })
            }
            sb.append('\n'); y += 2
        }
        return sb.toString()
    }
}
