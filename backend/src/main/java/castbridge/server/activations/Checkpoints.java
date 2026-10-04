package castbridge.server.activations;

import castbridge.server.licenses.Hashing;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.LicenseProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily checkpoint (00:10 Douala): the heads of the two chains plus a few counters, SIGNED so that the owner can keep the signed lines OUTSIDE the server: someone who
 * rewrites the whole database without the key and without that file is seen at the next verification. Signature: Ed25519 with the optional key file
 * {@code act-checkpoint.key} (kid in sig_kid), else an HMAC with {@code act-audit.key} (sig_kid "hmac"), else a bare SHA-256 (sig_kid "sha256").
 *
 * <p>Signed text: {@code castbridge-act-checkpoint-v1|<day>|<last event id>|<event head>|<last read id>|<read head>|<counters JSON>}.
 */
@Service
public class Checkpoints {
    private static final Logger log = LoggerFactory.getLogger(Checkpoints.class);
    public static final String FORMAT = "castbridge-act-checkpoint-v1";

    public record Checkpoint(LocalDate day, long eventLastId, String eventHead, long readLastId, String readHead, String countsJson, String sigKid, String signature, String payload) {}

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final ActivationsProperties props;
    private final Ed25519PrivateKeyParameters signer;
    private final byte[] hmacKey;

    public Checkpoints(JdbcTemplate jdbc, ActClock clock, LicenseProperties lp, ActivationsProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.props = props;
        this.signer = readSigner(lp.secretsDir().resolve("act-checkpoint.key"));
        this.hmacKey = Chains.readKey(lp.secretsDir().resolve("act-audit.key"));
    }

    private static Ed25519PrivateKeyParameters readSigner(Path file) {
        try {
            if (!Files.isReadable(file)) return null;
            byte[] der = Base64.getDecoder().decode(new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            if (der.length == Ed25519PrivateKeyParameters.KEY_SIZE) return new Ed25519PrivateKeyParameters(der, 0);
            AsymmetricKeyParameter k = PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(der));
            return k instanceof Ed25519PrivateKeyParameters ed ? ed : null;
        } catch (IOException | RuntimeException e) {
            log.error("invalid act-checkpoint.key: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Africa/Douala")
    void nightly() {
        if (!props.enabled()) return;
        try {
            create(clock.now().atZone(ZoneId.of("Africa/Douala")).toLocalDate());
        } catch (RuntimeException e) {
            log.error("activation checkpoint failed: {}", e.getClass().getSimpleName());
        }
    }

    public static String payload(LocalDate day, long eventLastId, String eventHead, long readLastId, String readHead, String countsJson) {
        return String.join("|", FORMAT, day.toString(), Long.toString(eventLastId), eventHead, Long.toString(readLastId), readHead, countsJson);
    }

    /** The checkpoint of a day (the one already written if there is one: a day is signed once). */
    @Transactional
    public Checkpoint create(LocalDate day) {
        List<Checkpoint> have = jdbc.query("SELECT * FROM act_checkpoint WHERE cp_day = ?", (rs, i) -> map(rs), Date.valueOf(day));
        if (!have.isEmpty()) return have.get(0);
        Map<String, Object> ev = jdbc.queryForMap("SELECT last_id, last_hash FROM act_event_head WHERE id = 1"), rd = jdbc.queryForMap("SELECT last_id, last_hash FROM act_event_head WHERE id = 2");
        long keys = jdbc.queryForObject("SELECT COUNT(*) FROM act_key", Long.class), tvs = jdbc.queryForObject("SELECT COUNT(*) FROM act_tv", Long.class),
                open = jdbc.queryForObject("SELECT COUNT(*) FROM act_alert WHERE state <> 'CLOSED'", Long.class);
        String counts = "{\"activations\":" + keys + ",\"tvs\":" + tvs + ",\"openAlerts\":" + open + "}";
        long evId = ((Number) ev.get("last_id")).longValue(), rdId = ((Number) rd.get("last_id")).longValue();
        String evHead = (String) ev.get("last_hash"), rdHead = (String) rd.get("last_hash");
        String payload = payload(day, evId, evHead, rdId, rdHead, counts);
        String kid, sig;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (signer != null) {
            Ed25519Signer s = new Ed25519Signer();
            s.init(true, signer);
            s.update(bytes, 0, bytes.length);
            kid = LicenseKeyring.kidOf(signer.generatePublicKey().getEncoded());
            sig = Base64.getEncoder().encodeToString(s.generateSignature());
        } else if (hmacKey != null) {
            kid = "hmac";
            sig = HexFormat.of().formatHex(Hashing.hmac("HmacSHA256", hmacKey, bytes));
        } else {
            kid = "sha256";
            sig = Hashing.sha256Hex(bytes);
        }
        jdbc.update("INSERT INTO act_checkpoint (cp_day, event_last_id, event_head, read_last_id, read_head, counts_json, sig_kid, signature, created_at) VALUES (?,?,?,?,?,?,?,?,?)", Date.valueOf(day), evId, evHead,
                rdId, rdHead, counts, kid, sig, Timestamp.from(clock.now()));
        return new Checkpoint(day, evId, evHead, rdId, rdHead, counts, kid, sig, payload);
    }

    public List<Checkpoint> list(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM act_checkpoint WHERE cp_day >= ? AND cp_day <= ? ORDER BY cp_day", (rs, i) -> map(rs), Date.valueOf(from == null ? LocalDate.of(2000, 1, 1) : from),
                Date.valueOf(to == null ? LocalDate.of(2999, 12, 31) : to));
    }

    private static Checkpoint map(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate day = rs.getDate("cp_day").toLocalDate();
        long ev = rs.getLong("event_last_id"), rd = rs.getLong("read_last_id");
        String eh = rs.getString("event_head"), rh = rs.getString("read_head"), counts = rs.getString("counts_json");
        return new Checkpoint(day, ev, eh, rd, rh, counts, rs.getString("sig_kid"), rs.getString("signature"), payload(day, ev, eh, rd, rh, counts));
    }
}
