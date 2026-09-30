package castbridge.server.updates;

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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the APK files. Downloads through the phone's Bluetooth link are slow and get cut: single byte ranges
 * (206 / 416), If-Range, ETag (= "sha256"), If-None-Match (304), Content-Length and a long immutable cache (a file name
 * never changes content: it carries the version and the start of its hash).
 */
@RestController
@RequestMapping("/dl")
public class DownloadController {
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private static final Pattern FILE = Pattern.compile("[A-Za-z0-9._-]{1,200}\\.apk");

    private final ReleaseService service;

    public DownloadController(ReleaseService service) { this.service = service; }

    @RequestMapping(path = "/{app}/{file:.+}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void download(@PathVariable String app, @PathVariable String file, HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        if (!ReleaseService.APPS.contains(app) || !FILE.matcher(file).matches()) throw ApiException.notFound("Fichier introuvable");
        Release r = service.byFile(app, file).orElseThrow(() -> ApiException.notFound("Fichier introuvable"));
        if (r.isRevoked()) throw new ApiException(HttpStatus.GONE, "Cette version a été retirée");
        Path path = service.file(r);
        if (!Files.isRegularFile(path)) throw ApiException.notFound("Fichier absent du stockage du serveur");
        long size = Files.size(path);
        String etag = "\"" + r.getSha256() + "\"";

        res.setHeader("ETag", etag);
        res.setHeader("Accept-Ranges", "bytes");
        res.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        res.setHeader("Content-Disposition", "attachment; filename=\"" + file + "\"");
        res.setHeader("X-Content-SHA256", r.getSha256());

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
                    if (m.group(1).isEmpty()) {                        // bytes=-N : the last N bytes
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
            } // several ranges or odd syntax: ignored, the whole file is sent (allowed by RFC 9110)
        }

        long length = end - start + 1;
        res.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        if (partial) res.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        res.setContentType("application/vnd.android.package-archive");
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
