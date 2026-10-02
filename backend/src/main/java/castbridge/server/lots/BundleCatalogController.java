package castbridge.server.lots;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only: the bundle catalogue (bouquets: id, type, lots, title, rawBytes, rentalDays) that the owner's tools import
 * ("Mettre à jour le catalogue depuis le serveur"). The file is produced and SIGNED OFFLINE by the owner
 * ({@code python3 tools/trial-edition/trial_edition.py sign-catalog}), then dropped at {@code castbridge.catalog.bundles-file}: the
 * server only relays it, it never signs it (so a server compromise cannot invent bundles or rental durations: the clients verify the
 * signature with the update keys, castbridge.core.lots.SignedBundleCatalog). The signed text is
 * {@code castbridge-bundle-catalog-v1}; keep it identical to the Kotlin and Python sides.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class BundleCatalogController {
    /** The catalogue is a few KiB: anything above 1 MiB is a mistake (or an attack on the memory of a 512 MiB JVM). */
    static final long MAX_BYTES = 1024 * 1024;

    private final CastbridgeProperties props;
    private final ObjectMapper json;
    private record Cached(Path file, long lastModified, long size, byte[] raw, String etag, boolean valid) {}
    private volatile Cached cache;

    public BundleCatalogController(CastbridgeProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    @GetMapping("/bundles")
    public ResponseEntity<byte[]> bundles(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) throws IOException {
        Path f = props.catalog().bundlesFile();
        if (!Files.isRegularFile(f)) { cache = null; throw ApiException.notFound("Catalogue des bouquets non publié sur le serveur"); }
        long size = Files.size(f), modified = Files.getLastModifiedTime(f).toMillis();
        if (size > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Catalogue des bouquets trop volumineux sur le serveur (1 Mio au plus)");
        Cached c = cache;
        if (c == null || !c.file().equals(f) || c.lastModified() != modified || c.size() != size) {
            byte[] raw = Files.readAllBytes(f);
            if (raw.length > MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Catalogue des bouquets trop volumineux sur le serveur (1 Mio au plus)");
            boolean ok = true;
            try {
                JsonNode n = json.readTree(raw);
                if (n == null || !n.path("bundles").isArray() || !n.hasNonNull("signature") || !n.hasNonNull("generatedAt")) ok = false;
            } catch (IOException e) {
                ok = false;
            }
            c = new Cached(f, modified, size, raw, "\"" + shortSha(raw) + "\"", ok);
            cache = c;
        }
        if (!c.valid()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalogue des bouquets illisible sur le serveur");
        String tag = c.etag();
        if (ifNoneMatch != null && java.util.Arrays.stream(ifNoneMatch.split(",")).map(String::trim).anyMatch(t -> t.equals(tag) || t.equals("*")))
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(c.etag()).cacheControl(CacheControl.noCache()).build();
        return ResponseEntity.ok().eTag(c.etag()).cacheControl(CacheControl.noCache())
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8)).body(c.raw());
    }

    private static String shortSha(byte[] raw) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
