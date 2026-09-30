package castbridge.server.updates;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

/**
 * What a device receives when an update is available. The signature covers {@link #canonicalPayload()}, a line-based
 * text built from the fields (not the JSON bytes, so the client can re-build it from the parsed values whatever the
 * JSON formatting). The Kotlin side (android/core: castbridge.core.update.UpdateManifest) builds the exact same text:
 * change both together (and bump the first line).
 */
public record UpdateManifest(
        String app,
        String channel,
        String abi,
        int versionCode,
        String versionName,
        String url,
        String sha256,
        long size,
        Integer minSdk,
        String notes,
        boolean mandatory,
        int minSupportedVersionCode,
        String publishedAt,
        String keyId,
        String signature) {

    public static final String FORMAT = "castbridge-update-manifest-v1";

    public String canonicalPayload() {
        String notesHash = HexFormat.of().formatHex(ManifestSigner.sha256((notes == null ? "" : notes).getBytes(StandardCharsets.UTF_8)));
        return String.join("\n", List.of(
                FORMAT,
                "app=" + app,
                "channel=" + channel,
                "abi=" + abi,
                "versionCode=" + versionCode,
                "versionName=" + versionName,
                "url=" + url,
                "sha256=" + sha256,
                "size=" + size,
                "minSdk=" + (minSdk == null ? "" : minSdk),
                "mandatory=" + mandatory,
                "minSupportedVersionCode=" + minSupportedVersionCode,
                "publishedAt=" + publishedAt,
                "notesSha256=" + notesHash));
    }

    public UpdateManifest withSignature(String keyId, String signature) {
        return new UpdateManifest(app, channel, abi, versionCode, versionName, url, sha256, size, minSdk, notes, mandatory,
                minSupportedVersionCode, publishedAt, keyId, signature);
    }
}
