package castbridge.server.lots;

import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (devices, i.e. the phones; the TV never calls the server for lots): the signed catalog and the lot files. Downloads get
 * cut on bad networks: single byte ranges (206 / 416), If-Range, ETag = "sha256", Content-Length, immutable cache (a version never
 * changes). Never serves a lot the admin has not published (404) or has revoked (410). A device blocked by the admin gets 403.
 */
@RestController
@RequestMapping("/api/v1/lots")
public class LotController {
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");

    private final LotService lots;
    private final DeviceService devices;

    public LotController(LotService lots, DeviceService devices) {
        this.lots = lots;
        this.devices = devices;
    }

    @GetMapping("/catalog")
    public ResponseEntity<LotCatalog> catalog(@RequestParam(required = false) String feature, @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestHeader(name = "Authorization", required = false) String authorization) {
        devices.refuseIfBlocked(authorization, deviceId);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(lots.catalog(feature, channel, deviceId));
    }

    @RequestMapping(path = "/{feature}/{scope}/{version}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(@PathVariable String feature, @PathVariable String scope, @PathVariable int version,
                         @RequestParam(required = false) String deviceId,
                         @RequestHeader(name = "Authorization", required = false) String authorization,
                         HttpServletRequest req, HttpServletResponse res) throws IOException {
        devices.refuseIfBlocked(authorization, deviceId);
        if (!SEGMENT.matcher(feature).matches() || !SEGMENT.matcher(scope).matches()) throw ApiException.notFound("Lot introuvable");
        Lot l = lots.servable(feature, scope, version).orElseThrow(() -> ApiException.notFound("Lot introuvable"));
        if (l.isRevoked()) throw new ApiException(HttpStatus.GONE, "Ce lot a été retiré");
        Path path = lots.file(l);
        if (!Files.isRegularFile(path)) throw ApiException.notFound("Fichier absent du stockage du serveur");
        long size = Files.size(path);
        String etag = "\"" + l.getSha256() + "\"";
        res.setHeader("ETag", etag);
        res.setHeader("Accept-Ranges", "bytes");
        res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        res.setHeader("X-Content-SHA256", l.getSha256());
        res.setHeader("Content-Encoding", "identity");

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
                        start = Math.max(0, size - Long.parseLong(m.group(2)));
                    } else {
                        start = Long.parseLong(m.group(1));
                        if (!m.group(2).isEmpty()) end = Math.min(end, Long.parseLong(m.group(2)));
                    }
                } catch (NumberFormatException e) {
                    start = size;
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
        res.setContentType("application/octet-stream");
        res.setContentLengthLong(length);
        if ("HEAD".equals(req.getMethod())) return;
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ);
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
}
