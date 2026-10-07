package castbridge.server.updates;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test helper: reads an inline QR SVG back (rebuilds the dark modules from the path data, then decodes them with ZXing's reader). */
final class QrDecode {
    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"");
    private static final Pattern DARK_MODULE = Pattern.compile("M(\\d+) (\\d+)h1v1h-1z");

    private QrDecode() {}

    /** The text the QR code carries; throws if the image cannot be read as a QR code. */
    static String decode(String svg) throws Exception {
        Matcher box = VIEW_BOX.matcher(svg);
        if (!box.find()) throw new IllegalArgumentException("pas de viewBox dans le SVG");
        int w = Integer.parseInt(box.group(1)), h = Integer.parseInt(box.group(2));
        int scale = 6, quiet = 4; // a screen shows the code on a white card: add the quiet zone it would get there
        int pw = (w + 2 * quiet) * scale, ph = (h + 2 * quiet) * scale;
        int[] pixels = new int[pw * ph];
        Arrays.fill(pixels, 0xFFFFFFFF);
        Matcher dark = DARK_MODULE.matcher(svg);
        while (dark.find()) {
            int x0 = (Integer.parseInt(dark.group(1)) + quiet) * scale, y0 = (Integer.parseInt(dark.group(2)) + quiet) * scale;
            for (int y = y0; y < y0 + scale; y++) {
                for (int x = x0; x < x0 + scale; x++) pixels[y * pw + x] = 0xFF000000;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(pw, ph, pixels)));
        return new QRCodeReader().decode(bitmap).getText();
    }
}
