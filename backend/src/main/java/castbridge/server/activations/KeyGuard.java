package castbridge.server.activations;

import castbridge.server.licenses.Hashing;
import castbridge.server.licenses.LicenseProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Audit M4: act-ref.key designates the TVs in the whole history and act-audit.key is the key of both hash chains. Changed or restored from another copy, they would turn every
 * known TV into a new one (answers in error) or every line into a « modified » one (a false suspicion of tampering). At first use a CHECK VALUE (an HMAC of a constant, never
 * the key) is stored; at every start, with the module on, a key that gives another value makes the server REFUSE TO START with the name of the key. No value in the message.
 */
@Component
public class KeyGuard implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;
    private final LicenseProperties lp;
    private final ActivationsProperties props;
    private final TvRef tvRef;
    private final ActClock clock;

    public KeyGuard(JdbcTemplate jdbc, LicenseProperties lp, ActivationsProperties props, TvRef tvRef, ActClock clock) {
        this.jdbc = jdbc;
        this.lp = lp;
        this.props = props;
        this.tvRef = tvRef;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (props.enabled() && tvRef.available()) verify();
    }

    /** Stores the check values at first use; throws when a key is not the one that served until now. */
    public void verify() {
        check("act-ref", tvRef.checkValue());
        byte[] audit = Chains.readKey(lp.secretsDir().resolve("act-audit.key"));
        check("act-audit", audit == null ? Hashing.sha256Hex("castbridge-act-audit-none".getBytes(StandardCharsets.UTF_8))
                : HexFormat.of().formatHex(Hashing.hmac("HmacSHA256", audit, "castbridge-act-audit-check-v1".getBytes(StandardCharsets.UTF_8))));
    }

    private void check(String name, String value) {
        List<String> stored = jdbc.queryForList("SELECT check_value FROM act_key_check WHERE name = ?", String.class, name);
        if (stored.isEmpty()) {
            jdbc.update("INSERT INTO act_key_check (name, check_value, created_at) VALUES (?,?,?)", name, value, Timestamp.from(clock.now()));
        } else if (!stored.get(0).equals(value)) {
            throw new IllegalStateException(name + ".key n'est plus la clé utilisée depuis la première mise en service du suivi des activations : refus de démarrer (la remettre, ou, si le changement est voulu, "
                    + "décider d'abord quoi faire de l'historique ; docs/ACTIVATION-TRACKING.md). Aucune valeur n'est affichée.");
        }
    }
}
