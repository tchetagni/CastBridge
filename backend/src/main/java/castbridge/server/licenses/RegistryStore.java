package castbridge.server.licenses;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The registry of signed events (table lic_event): what the server itself emits (licence created, activation issued, seat or key revoked,
 * signed with the server key) and what it imports from the offline tools. Union without duplicate by event id. Server events need the server
 * key; without it nothing is emitted (and the module cannot issue anyway).
 */
@Service
public class RegistryStore {
    private final JdbcTemplate jdbc;
    private final LicenseKeyring keyring;

    public RegistryStore(JdbcTemplate jdbc, LicenseKeyring keyring) {
        this.jdbc = jdbc;
        this.keyring = keyring;
    }

    public boolean has(String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM lic_event WHERE id = ?", Integer.class, id) > 0; }

    /** Stores the event; false if it was already there (same id). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean put(RegistryEvent e, String source, Long importId, boolean applied) {
        var f = e.fields();
        String seat = f.get("seat");
        if (seat == null && "seat".equals(f.get("target")) && f.get("value") != null && f.get("value").contains("|")) seat = f.get("value").split("\\|")[1];
        String license = f.get("license");
        if (license == null && "seat".equals(f.get("target")) && f.get("value") != null) license = f.get("value").split("\\|")[0];
        try {
            jdbc.update("INSERT INTO lic_event (id, kid, type, license_id, seat_id, kind, at_ms, text, signature, source, import_id, applied, erased) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE)",
                    e.id(), e.kid(), e.type(), license, seat != null && seat.matches("[0-9a-f]{16}") ? seat : null, f.get("kind"), e.at(), e.text(), e.signature(), source, importId, applied);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markApplied(String id) { jdbc.update("UPDATE lic_event SET applied = TRUE WHERE id = ?", id); }

    /** The `license` event of a paid licence (once), so that the offline tools know the licence, its seats and its transfer cap. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureLicense(String license, int seats, int cap, Instant createdAt) {
        if (!keyring.present()) return;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_event WHERE type = 'license' AND license_id = ?", Integer.class, license);
        if (n != null && n > 0) return;
        put(RegistryEvent.license(keyring, createdAt.toEpochMilli(), license, seats, cap), "SERVER", null, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emitIssue(ActivationSigner.SignedActivation a, String subject, String kind, String license, int k, java.util.Map<DeviceIdentity.Factor, String> factors, long issuedAt) {
        if (!keyring.present()) return;
        put(RegistryEvent.issue(keyring, a, subject, kind, license, k, factors, issuedAt), "SERVER", null, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emitRevokeSeat(String license, String seat, Instant at) {
        if (!keyring.present()) return;
        put(RegistryEvent.revokeSeat(keyring, at.toEpochMilli(), license, seat), "SERVER", null, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void emitRevokeKey(String kid, Instant at) {
        if (!keyring.present()) return;
        put(RegistryEvent.revokeKey(keyring, at.toEpochMilli(), kid), "SERVER", null, true);
    }

    public record Stored(String id, String kid, String text, String signature) {}

    /** Events for the export (never the erased ones), in canonical order, at most {@code limit}. */
    public List<Stored> all(int limit) {
        return jdbc.query("SELECT id, kid, text, signature FROM lic_event WHERE erased = FALSE AND text IS NOT NULL ORDER BY at_ms, id LIMIT ?",
                (rs, i) -> new Stored(rs.getString("id"), rs.getString("kid"), rs.getString("text"), rs.getString("signature")), limit);
    }

    public Stored get(String id) {
        List<Stored> r = jdbc.query("SELECT id, kid, text, signature FROM lic_event WHERE id = ?", (rs, i) -> new Stored(rs.getString("id"), rs.getString("kid"), rs.getString("text"), rs.getString("signature")), id);
        return r.isEmpty() ? null : r.get(0);
    }

    static Timestamp ts(Instant i) { return Timestamp.from(i); }
}
