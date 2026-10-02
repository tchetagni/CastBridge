package castbridge.server.lots;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only: the free content archive (CC BY-SA) that users can download even without activation.
 * The file is produced and dropped offline by the owner (tools/free-content/build_free_archive.py)
 * at {@code castbridge.free-content.file}. The server relays it with streaming support for large files.
 * Rate-limited like other public routes.
 */
@RestController
@RequestMapping("/api/v1/free-content")
public class FreeContentController {
    /** Maximum file size: 200 MiB. */
    static final long MAX_BYTES = 200L * 1024 * 1024;
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");

    private final CastbridgeProperties props;
    private final ObjectMapper json;
    private record Cached(Path file, long lastModified, long size, String etag, String sha256) {}
    private volatile Cached cache;

    public FreeContentController(CastbridgeProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    @RequestMapping(path = "", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Path f = props.freeContent().file();
        if (!Files.isRegularFile(f)) {
            cache = null;
            throw ApiException.notFound("Archive des contenus libres non publiée sur le serveur");
        }
        long size = Files.size(f);
        long modified = Files.getLastModifiedTime(f).toMillis();
        if (size > MAX_BYTES)
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Archive des contenus libres trop volumineuse (200 Mio au plus)");

        Cached c = cache;
        if (c == null || !c.file().equals(f) || c.lastModified() != modified || c.size() != size) {
            c = new Cached(f, modified, size, "\"" + shortSha(Files.readAllBytes(f)) + "\"", null);
            cache = c;
        }

        String etag = c.etag();
        res.setHeader("ETag", etag);
        res.setHeader("Accept-Ranges", "bytes");
        res.setHeader("Cache-Control", "no-cache");
        res.setHeader("Content-Disposition", "attachment; filename=\"castbridge-contenus-libres.zip\"");

        String inm = req.getHeader("If-None-Match");
        if (inm != null && (inm.trim().equals("*") || inm.contains(etag))) {
            res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        long start = 0, end = size - 1;
        boolean partial = false;
        String range = req.getHeader("Range");
        String ifRange = req.getHeader("If-Range");
        if (range != null && (ifRange == null || ifRange.trim().equals(etag))) {
            Matcher m = RANGE.matcher(range.trim());
            if (m.matches() && !(m.group(1).isEmpty() && m.group(2).isEmpty())) {
                try {
                    if (m.group(1).isEmpty()) {
                        long n = Long.parseLong(m.group(2));
                        start = Math.max(0, size - n);
                    } else {
                        start = Long.parseLong(m.group(1));
                        if (!m.group(2).isEmpty()) end = Math.min(end, Long.parseLong(m.group(2)));
                    }
                } catch (NumberFormatException e) {
                    start = size; // unsatisfiable
                }
                if (start >= size || start > end) {
                    res.setHeader("Content-Range", "bytes */" + size);
                    res.setHeader("Cache-Control", "no-store");
                    res.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return;
                }
                partial = true;
            }
        }

        long length = end - start + 1;
        res.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        if (partial) res.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        res.setContentType("application/zip");
        res.setContentLengthLong(length);
        if ("HEAD".equals(req.getMethod())) return;

        try (FileChannel ch = FileChannel.open(f, StandardOpenOption.READ);
             InputStream in = Channels.newInputStream(ch.position(start))) {
            OutputStream out = res.getOutputStream();
            byte[] buf = new byte[64 * 1024];
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                out.write(buf, 0, n);
                left -= n;
            }
        }
    }

    @GetMapping("/info")
    public ResponseEntity<ObjectNode> info() throws IOException {
        Path f = props.freeContent().file();
        if (!Files.isRegularFile(f))
            throw ApiException.notFound("Archive des contenus libres non publiée sur le serveur");

        long size = Files.size(f);
        long modified = Files.getLastModifiedTime(f).toMillis();
        if (size > MAX_BYTES)
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Archive des contenus libres trop volumineuse");

        Cached c = cache;
        if (c == null || !c.file().equals(f) || c.lastModified() != modified || c.size() != size) {
            byte[] raw = Files.readAllBytes(f);
            String sha = fullSha(raw);
            c = new Cached(f, modified, size, "\"" + sha.substring(0, 16) + "\"", sha);
            cache = c;
        }

        ObjectNode result = json.createObjectNode();
        result.put("available", true);
        result.put("sizeBytes", c.size());
        result.put("sha256", c.sha256());
        result.put("generatedAt", Instant.ofEpochMilli(c.lastModified()).toString());
        result.put("licence", "CC BY-SA");
        result.put("fileName", "castbridge-contenus-libres.zip");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .body(result);
    }

    private static String shortSha(byte[] raw) {
        return fullSha(raw).substring(0, 16);
    }

    private static String fullSha(byte[] raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
