package castbridge.server.lots;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The signed answer of GET /api/v1/lots/catalog: ONE signed catalog per answer (all lots of the channel, or one feature). The
 * signature covers {@link #canonicalPayload()}, a line-based text rebuilt from the fields exactly as the Kotlin side does
 * (android/core: castbridge.core.lots.LotManifest): change both together and bump the first line.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LotCatalog(String channel, String feature, String generatedAt, List<Entry> lots, String keyId, String signature) {
    public static final String FORMAT = "castbridge-lot-catalog-v1";

    public record Entry(String feature, String scope, int version, long bytes, String sha256, String title, int minAppVersion) {}

    public String canonicalPayload() {
        List<String> lines = new ArrayList<>();
        lines.add(FORMAT);
        lines.add("channel=" + channel);
        lines.add("feature=" + (feature == null ? "*" : feature));
        lines.add("generatedAt=" + generatedAt);
        lots.stream()
                .sorted(Comparator.comparing(Entry::feature).thenComparing(Entry::scope).thenComparingInt(Entry::version))
                .forEach(e -> lines.add("lot=" + e.feature() + "|" + e.scope() + "|" + e.version() + "|" + e.bytes() + "|" + e.sha256() + "|"
                        + e.minAppVersion() + "|" + HexFormat.of().formatHex(sha256(e.title().getBytes(StandardCharsets.UTF_8)))));
        return String.join("\n", lines);
    }

    public LotCatalog withSignature(String keyId, String signature) {
        return new LotCatalog(channel, feature, generatedAt, lots, keyId, signature);
    }

    static byte[] sha256(byte[] b) {
        try { return MessageDigest.getInstance("SHA-256").digest(b); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
