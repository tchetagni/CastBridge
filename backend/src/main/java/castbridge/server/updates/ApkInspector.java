package castbridge.server.updates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the package name, versionCode, versionName and minSdkVersion from the binary AndroidManifest.xml of an APK
 * (Android "AXML" format: string pool + resource map + XML element chunks). Best effort: any unexpected layout gives
 * {@link Optional#empty()} and the upload then trusts the form.
 */
public final class ApkInspector {
    private static final int MAX_MANIFEST = 4 << 20;
    private static final int ATTR_VERSION_CODE = 0x0101021b;
    private static final int ATTR_VERSION_NAME = 0x0101021c;
    private static final int ATTR_MIN_SDK = 0x0101020c;

    private ApkInspector() {}

    public record ApkInfo(String packageName, Integer versionCode, String versionName, Integer minSdk) {}

    public static Optional<ApkInfo> inspect(Path apk) {
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) return Optional.empty();
            try (InputStream in = zip.getInputStream(entry)) {
                return Optional.ofNullable(parseManifest(in.readNBytes(MAX_MANIFEST)));
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** @return the manifest facts, or null if this is not a binary manifest we understand */
    static ApkInfo parseManifest(byte[] b) {
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (b.length < 8 || u16(buf, 0) != 0x0003) return null;
        String[] strings = new String[0];
        int[] resIds = new int[0];
        String pkg = null, versionName = null;
        Integer versionCode = null, minSdk = null;
        int pos = u16(buf, 2);
        while (pos + 8 <= b.length) {
            int type = u16(buf, pos);
            int headerSize = u16(buf, pos + 2);
            int size = buf.getInt(pos + 4);
            if (size < 8 || headerSize < 8 || (long) pos + size > b.length) break;
            switch (type) {
                case 0x0001 -> strings = stringPool(buf, pos);
                case 0x0180 -> {
                    int n = (size - headerSize) / 4;
                    resIds = new int[n];
                    for (int i = 0; i < n; i++) resIds[i] = buf.getInt(pos + headerSize + 4 * i);
                }
                case 0x0102 -> {
                    int ext = pos + headerSize;
                    String element = str(strings, buf.getInt(ext + 4));
                    int attrStart = u16(buf, ext + 8), attrSize = u16(buf, ext + 10), attrCount = u16(buf, ext + 12);
                    for (int i = 0; i < attrCount; i++) {
                        int a = ext + attrStart + i * attrSize;
                        int nameIdx = buf.getInt(a + 4);
                        int raw = buf.getInt(a + 8);
                        int dataType = buf.get(a + 15) & 0xff;
                        int data = buf.getInt(a + 16);
                        int resId = nameIdx >= 0 && nameIdx < resIds.length ? resIds[nameIdx] : 0;
                        String name = str(strings, nameIdx);
                        String sval = dataType == 0x03 ? str(strings, data) : str(strings, raw);
                        Integer ival = dataType >= 0x10 && dataType <= 0x1f ? data : null;
                        if ("manifest".equals(element)) {
                            if ("package".equals(name) && resId == 0) pkg = sval;
                            else if (resId == ATTR_VERSION_CODE || "versionCode".equals(name)) versionCode = ival != null ? ival : parseInt(sval);
                            else if (resId == ATTR_VERSION_NAME || "versionName".equals(name)) versionName = sval;
                        } else if ("uses-sdk".equals(element) && (resId == ATTR_MIN_SDK || "minSdkVersion".equals(name))) {
                            minSdk = ival != null ? ival : parseInt(sval);
                        }
                    }
                }
                default -> { }
            }
            pos += size;
        }
        if (pkg == null && versionCode == null) return null;
        return new ApkInfo(pkg, versionCode, versionName, minSdk);
    }

    private static String[] stringPool(ByteBuffer buf, int p) {
        int count = buf.getInt(p + 8);
        int flags = buf.getInt(p + 16);
        int stringsStart = buf.getInt(p + 20);
        int headerSize = u16(buf, p + 2);
        boolean utf8 = (flags & 0x100) != 0;
        if (count < 0 || count > 1_000_000) return new String[0];
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            int off = p + stringsStart + buf.getInt(p + headerSize + 4 * i);
            if (utf8) {
                int[] o = {off};
                lenUtf8(buf, o); // length in UTF-16 units, unused
                int bytes = lenUtf8(buf, o);
                byte[] s = new byte[bytes];
                buf.get(o[0], s);
                out[i] = new String(s, StandardCharsets.UTF_8);
            } else {
                int len = u16(buf, off);
                off += 2;
                if ((len & 0x8000) != 0) {
                    len = ((len & 0x7fff) << 16) | u16(buf, off);
                    off += 2;
                }
                byte[] s = new byte[len * 2];
                buf.get(off, s);
                out[i] = new String(s, StandardCharsets.UTF_16LE);
            }
        }
        return out;
    }

    private static int lenUtf8(ByteBuffer buf, int[] off) {
        int b0 = buf.get(off[0]++) & 0xff;
        if ((b0 & 0x80) == 0) return b0;
        return ((b0 & 0x7f) << 8) | (buf.get(off[0]++) & 0xff);
    }

    private static int u16(ByteBuffer buf, int at) { return buf.getShort(at) & 0xffff; }

    private static String str(String[] pool, int idx) { return idx >= 0 && idx < pool.length ? pool[idx] : null; }

    private static Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
