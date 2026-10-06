package castbridge.server.guide;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Public, read-only user guide: serves {@code <castbridge.guide.dir>/index.html} at {@code /guide/} and the other files of
 * that folder (extension allow-list, flat names only). No authentication, no cookie. The folder lives in the APK volume
 * and is filled by hand (docs/GUIDE-PUBLICATION.md).
 */
@RestController
public class GuideController {
    /** The guide is one HTML file with inline CSS and data: images, no script. */
    public static final String CSP = "default-src 'self' data:; style-src 'self' 'unsafe-inline'; img-src 'self' data:";

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html;charset=UTF-8",
            "css", "text/css;charset=UTF-8",
            "js", "text/javascript;charset=UTF-8",
            "json", "application/json;charset=UTF-8",
            "txt", "text/plain;charset=UTF-8",
            "svg", "image/svg+xml;charset=UTF-8",
            "png", "image/png",
            "jpg", "image/jpeg",
            "webp", "image/webp");

    private final Path dir;

    public GuideController(@Value("${castbridge.guide.dir:/data/apk/guide}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    @GetMapping("/guide")
    public ResponseEntity<Void> redirect() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).header(HttpHeaders.LOCATION, "/guide/").build();
    }

    @GetMapping("/guide/")
    public ResponseEntity<?> index(WebRequest req, HttpServletResponse res) throws IOException {
        return serve("index.html", "Guide non publié", req, res);
    }

    @GetMapping("/guide/{file:.+}")
    public ResponseEntity<?> file(@PathVariable String file, WebRequest req, HttpServletResponse res) throws IOException {
        return serve(file, "Fichier introuvable", req, res);
    }

    /** A flat file name with an allowed extension; no "..", separator or leading dot. */
    static boolean allowedName(String name) {
        if (name == null || name.isEmpty() || name.length() > 120) return false;
        if (name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0 || name.startsWith(".")) return false;
        int dot = name.lastIndexOf('.');
        return dot > 0 && TYPES.containsKey(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private ResponseEntity<?> serve(String name, String notFoundText, WebRequest req, HttpServletResponse res) throws IOException {
        Path p = allowedName(name) ? dir.resolve(name).normalize() : null;
        if (p == null || !p.startsWith(dir) || !Files.isRegularFile(p) || !Files.isReadable(p) || !p.toRealPath().startsWith(dir.toRealPath())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(notFoundText);
        }
        String type = TYPES.get(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
        long lastModified = Files.getLastModifiedTime(p).toMillis();
        String etag = "\"" + Long.toHexString(Files.size(p)) + "-" + Long.toHexString(lastModified) + "\"";

        res.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=" + (name.equals("index.html") ? 300 : 86400));
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", CSP);
        if (req.checkNotModified(etag, lastModified)) return null; // 304, ETag and Last-Modified already set
        Resource body = new FileSystemResource(p);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(body);
    }
}
