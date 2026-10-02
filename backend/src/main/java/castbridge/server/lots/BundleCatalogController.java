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
    private final CastbridgeProperties props;
    private final ObjectMapper json;

    public BundleCatalogController(CastbridgeProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    @GetMapping("/bundles")
    public ResponseEntity<byte[]> bundles() throws IOException {
        Path f = props.catalog().bundlesFile();
        if (!Files.isRegularFile(f)) throw ApiException.notFound("Catalogue des bouquets non publié sur le serveur");
        byte[] raw = Files.readAllBytes(f);
        try {
            JsonNode n = json.readTree(raw);
            if (n == null || !n.path("bundles").isArray() || !n.hasNonNull("signature") || !n.hasNonNull("generatedAt"))
                throw new IllegalArgumentException("incomplete");
        } catch (IOException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Catalogue des bouquets illisible sur le serveur");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(new MediaType("application", "json", StandardCharsets.UTF_8)).body(raw);
    }
}
