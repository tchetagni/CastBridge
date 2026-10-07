package castbridge.server.updates;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.guide.GuideController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Public download page: {@code GET /telecharger} (and {@code /telecharger/}). For each application, the release the stable address
 * {@code /dl/{app}/latest.apk} leads to (same rule: {@link ReleaseService#latestForDownload}): never a retired version, never a beta, never a
 * partial rollout. Same security chain, same CSP and same cache as the guide ({@code /guide/}); no authentication, no cookie.
 */
@RestController
public class DownloadPageController {
    /** The applications of the page, in display order. */
    private static final List<String> APPS = List.of("tv", "phone");
    /** A path prefix set by a reverse proxy ("/castbridge"): plain segments only (never "." or ".."), at most four. */
    private static final Pattern PREFIX = Pattern.compile("(/[A-Za-z0-9_~-][A-Za-z0-9._~-]{0,63}){0,4}");

    private final ReleaseService releases;
    private final CastbridgeProperties props;

    public DownloadPageController(ReleaseService releases, CastbridgeProperties props) {
        this.releases = releases;
        this.props = props;
    }

    /** The prefix a reverse proxy put in front of the site ("/castbridge"), or "" when there is none or it is not a plain prefix: it ends up in links and redirects. */
    static String safePrefix(HttpServletRequest req) {
        String p = req.getContextPath();
        return p != null && PREFIX.matcher(p).matches() ? p : "";
    }

    @GetMapping({"/telecharger", "/telecharger/"})
    public ResponseEntity<String> page(HttpServletRequest req, HttpServletResponse res, WebRequest web) {
        // same base as the update manifests: the configured public URL, else the one of this request
        String base = props.publicBaseUrl().isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : props.publicBaseUrl().replaceAll("/+$", "");
        List<DownloadPage.Offer> offers = APPS.stream()
                .map(app -> new DownloadPage.Offer(app, releases.latestForDownload(app, null).orElse(null))).toList();
        String html = DownloadPage.render(offers, base, safePrefix(req));

        res.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", GuideController.CSP);
        String etag = "\"" + HexFormat.of().formatHex(ManifestSigner.sha256(html.getBytes(StandardCharsets.UTF_8))) + "\"";
        if (web.checkNotModified(etag)) return null; // 304, ETag already set
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).body(html);
    }
}
