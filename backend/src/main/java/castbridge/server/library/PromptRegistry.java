package castbridge.server.library;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Versioned prompts: {@code library/prompts/suggest-<version>.txt} in the classpath. A version is never edited once used in production
 * (add a new file and switch CASTBRIDGE_LIBRARY_PROMPT_VERSION): the answer reports {@code promptVersion} so that results can be compared.
 */
public final class PromptRegistry {
    private static final Pattern VERSION = Pattern.compile("[a-z0-9][a-z0-9.-]{0,15}");

    public record Prompt(String version, String text, String sha256) {}

    private PromptRegistry() {}

    public static Prompt load(String version) {
        if (!VERSION.matcher(version).matches()) throw new IllegalArgumentException("version de prompt invalide : " + version);
        try (InputStream in = PromptRegistry.class.getResourceAsStream("/library/prompts/suggest-" + version + ".txt")) {
            if (in == null) throw new IllegalArgumentException("prompt introuvable : suggest-" + version + ".txt");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            return new Prompt(version, text, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
