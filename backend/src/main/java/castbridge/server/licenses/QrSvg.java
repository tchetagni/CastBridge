package castbridge.server.licenses;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** QR code as an SVG data URI for an {@code <img>} (the page has no inline script nor style: CSP). Returns null if the text is too long. */
public final class QrSvg {
    private QrSvg() {}

    public static String dataUri(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L, EncodeHintType.MARGIN, 2, EncodeHintType.CHARACTER_SET, "UTF-8"));
            int w = m.getWidth(), h = m.getHeight();
            StringBuilder sb = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(w).append(' ').append(h)
                    .append("\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) if (m.get(x, y)) sb.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
            }
            sb.append("\"/></svg>");
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (WriterException | RuntimeException e) {
            return null;
        }
    }
}
