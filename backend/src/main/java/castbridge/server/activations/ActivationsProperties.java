package castbridge.server.activations;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the activation tracking module (CASTBRIDGE_ACTIVATIONS_*). OFF by default: every route of the module answers 404 until the owner turns it on
 * (docs/ACTIVATION-TRACKING.md). Secrets (act-ref.key, act-audit.key, act-checkpoint.key) are FILES in the secrets folder of the licence module, never values here.
 *
 * @param enabled         switch of the module (routes, scheduled jobs)
 * @param consoleSessions option A of D-W23-3: a console session signed by the owner phone key (off: the challenge routes answer 404)
 * @param archiveDir      folder of the cold archive (act_event-YYYY-MM.jsonl.gz ...), outside the image
 * @param exportMaxRows   most rows one export may hold (a bigger one is refused, not truncated)
 */
@ConfigurationProperties(prefix = "castbridge.activations")
public record ActivationsProperties(boolean enabled, boolean consoleSessions, Path archiveDir, Integer exportMaxRows) {
    public ActivationsProperties {
        if (archiveDir == null) archiveDir = Path.of("/var/lib/castbridge/act-archive");
        if (exportMaxRows == null || exportMaxRows < 1) exportMaxRows = 200_000;
    }
}
