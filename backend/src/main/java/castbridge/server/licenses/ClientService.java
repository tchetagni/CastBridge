package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Clients: minimum of personal data (name, contact, notes), access/export and erasure rights (the seat count is untouched). */
@Service
public class ClientService {
    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final LicenseProperties props;
    private final LicenseService licenses;

    public ClientService(JdbcTemplate jdbc, AuditLog audit, LicenseProperties props, LicenseService licenses) {
        this.licenses = licenses;
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
    }

    public record ClientRow(long id, String name, String contact, String notes, Instant createdAt, Instant erasedAt, int licenses) {}

    private static ClientRow row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ClientRow(rs.getLong("id"), rs.getString("name"), rs.getString("contact"), rs.getString("notes"), LicenseService.inst(rs, "created_at"),
                LicenseService.inst(rs, "erased_at"), rs.getInt("n"));
    }

    private static final String SELECT = "SELECT c.*, (SELECT COUNT(*) FROM lic_license l WHERE l.client_id = c.id) AS n FROM lic_client c";

    public Page<ClientRow> list(String q, int page, int size) {
        String where = q == null || q.isBlank() ? "" : " WHERE c.name LIKE ? ESCAPE '!' OR c.contact LIKE ? ESCAPE '!'";
        List<Object> args = new ArrayList<>();
        if (!where.isEmpty()) { args.add(Validate.like(q.trim())); args.add(Validate.like(q.trim())); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM lic_client c" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        return new Page<>(jdbc.query(SELECT + where + " ORDER BY c.name, c.id LIMIT ? OFFSET ?", ClientService::row, args.toArray()), page, size, total);
    }

    public ClientRow get(long id) {
        List<ClientRow> r = jdbc.query(SELECT + " WHERE c.id = ?", ClientService::row, id);
        if (r.isEmpty()) throw ApiException.notFound("Client introuvable");
        return r.get(0);
    }

    @Transactional
    public ClientRow create(Actor actor, String name, String contact, String notes) {
        actor.require(Role.Permission.CLIENT_WRITE, props.requireTotp());
        Instant now = Instant.now();
        String n = Validate.text(name, "Nom", 120, true), c = Validate.text(contact, "Contact", 160, false), no = Validate.text(notes, "Notes", 1000, false);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_client (name, contact, notes, created_at, updated_at) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, n);
            ps.setString(2, c);
            ps.setString(3, no);
            ps.setTimestamp(4, LicenseService.ts(now));
            ps.setTimestamp(5, LicenseService.ts(now));
            return ps;
        }, keys);
        long id = keys.getKey().longValue();
        audit.record(actor, "CLIENT_CREATE", "CLIENT", Long.toString(id), null, null);
        return get(id);
    }

    @Transactional
    public ClientRow update(Actor actor, long id, String name, String contact, String notes) {
        actor.require(Role.Permission.CLIENT_WRITE, props.requireTotp());
        ClientRow c = get(id);
        if (c.erasedAt() != null) throw ApiException.conflict("Ce client a été effacé");
        jdbc.update("UPDATE lic_client SET name = ?, contact = ?, notes = ?, updated_at = ? WHERE id = ?", Validate.text(name, "Nom", 120, true),
                Validate.text(contact, "Contact", 160, false), Validate.text(notes, "Notes", 1000, false), LicenseService.ts(Instant.now()), id);
        audit.record(actor, "CLIENT_UPDATE", "CLIENT", Long.toString(id), null, null);
        return get(id);
    }

    /** Right of access / portability: everything held about the client, as JSON-ready data (no activation text: none is stored). */
    @Transactional // not read-only: the export itself is written to the audit log (MySQL refuses writes on a read-only connection)
    public Map<String, Object> export(Actor actor, long id) {
        actor.require(Role.Permission.CLIENT_PRIVACY, props.requireTotp());
        ClientRow c = get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("client", c);
        List<Map<String, Object>> lic = new ArrayList<>();
        for (String licenseId : jdbc.queryForList("SELECT license_id FROM lic_license WHERE client_id = ? ORDER BY id", String.class, id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            LicenseService.LicenseRow l = licenses.get(licenseId);
            m.put("licenseId", l.licenseId());
            m.put("kind", l.kind());
            m.put("state", l.effectiveState());
            m.put("seatsAllowed", l.seatsAllowed());
            m.put("startAt", l.startAt());
            m.put("endAt", l.endAt());
            m.put("seats", jdbc.queryForList("SELECT seat_id, subject, device_code, state, first_seen, last_seen, anonymized FROM lic_seat WHERE license_pk = ? ORDER BY id", l.id()));
            m.put("issuances", jdbc.queryForList("SELECT seat_id, device_code, kind, kid, issued_at, not_after, issuer, channel, source FROM lic_issuance WHERE license_pk = ? ORDER BY id", l.id()));
            lic.add(m);
        }
        out.put("licenses", lic);
        audit.record(actor, "CLIENT_EXPORT", "CLIENT", Long.toString(id), null, Map.of("licenses", lic.size()));
        return out;
    }

    /**
     * Right to erasure: name, contact and notes are wiped, the seats are ANONYMIZED, not deleted (so the seat count and the
     * licence history stay coherent): the device code becomes ANON-&lt;hash&gt;, factor hashes and sightings are deleted, and the registry events
     * carrying the hardware fingerprints are no longer kept nor exported (a tombstone stops a later import from bringing them back).
     * A device anonymized this way that comes back is a new device for the licence (documented limit).
     */
    @Transactional
    public ClientRow erase(Actor actor, long id, String reason) {
        actor.require(Role.Permission.CLIENT_PRIVACY, props.requireTotp());
        String why = Validate.reason(reason);
        ClientRow c = get(id);
        if (c.erasedAt() != null) throw ApiException.conflict("Ce client est déjà effacé");
        Instant now = Instant.now();
        jdbc.update("UPDATE lic_client SET name = ?, contact = NULL, notes = NULL, erased_at = ?, updated_at = ? WHERE id = ?", "Client effacé n° " + id, LicenseService.ts(now), LicenseService.ts(now), id);
        int seats = 0;
        for (Map<String, Object> s : jdbc.queryForList("SELECT s.id, s.license_pk, s.seat_id, s.device_code FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.client_id = ? AND s.anonymized = FALSE", id)) {
            String code = (String) s.get("device_code");
            String anon = "ANON-" + Hashing.sha256Hex("anon|" + id + "|" + code).substring(0, 16).toUpperCase(java.util.Locale.ROOT);
            long lp = ((Number) s.get("license_pk")).longValue();
            // every device code this seat ever had (a replaced module changes it) becomes the same opaque value
            jdbc.update("UPDATE lic_issuance SET device_code = ? WHERE license_pk = ? AND seat_id = ?", anon, lp, s.get("seat_id"));
            jdbc.update("UPDATE lic_transfer SET from_device_code = ? WHERE license_pk = ? AND seat_id = ?", anon, lp, s.get("seat_id"));
            jdbc.update("UPDATE lic_transfer SET to_device_code = ? WHERE license_pk = ? AND seat_id = ?", anon, lp, s.get("seat_id"));
            jdbc.update("DELETE FROM lic_sighting WHERE device_code = ?", code);
            // the registry copies of the seat's events carry the hardware fingerprints: the server forgets them (the signed originals stay with the tools)
            jdbc.update("UPDATE lic_event SET text = NULL, erased = TRUE WHERE type IN ('issue','transfer') AND seat_id = ?", s.get("seat_id"));
            jdbc.update("UPDATE lic_seat SET device_code = ?, factors = '', anonymized = TRUE WHERE id = ?", anon, s.get("id"));
            seats++;
        }
        audit.record(actor, "CLIENT_ERASE", "CLIENT", Long.toString(id), why, Map.of("seatsAnonymized", seats));
        return get(id);
    }
}
