package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Licences: creation, change, suspension, revocation, extension, seats. The seat count is safe under concurrency: every
 * seat operation first takes the row lock of the licence (SELECT … FOR UPDATE), counts the active seats, and takes the
 * lowest free slot number; the database then enforces UNIQUE (licence, slot) with slot ≤ quota and the check constraint
 * "ACTIVE ⇔ slot number set". Two simultaneous requests can therefore never both take the last seat.
 */
@Service
public class LicenseService {
    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final LicenseProperties props;
    private final RegistryStore registry;
    private final SecureRandom random = new SecureRandom();

    public LicenseService(JdbcTemplate jdbc, AuditLog audit, LicenseProperties props, RegistryStore registry) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
        this.registry = registry;
    }

    // ------------------------------------------------------------------ views

    public record LicenseRow(long id, String licenseId, long clientId, String clientName, String kind, String state, String stateReason,
                             int seatsAllowed, int seatsUsed, Instant startAt, Instant endAt, int graceDays, int transferCap,
                             Instant createdAt, String createdBy) {
        /** ACTIVE, GRACE (ended, inside the grace period), SUSPENDED, REVOKED or EXPIRED. */
        public String effectiveState() { return effective(state, endAt, graceDays, Instant.now()); }

        /** The licence identifier as written in the activations: "trial" for a trial licence. */
        public String wireId() { return "TRIAL".equals(kind) ? WireActivation.TRIAL_LICENSE : licenseId; }
    }

    public record SeatRow(long id, String seatId, String subject, String deviceCode, String state, Instant firstSeen, Instant lastSeen, Instant releasedAt,
                          String releasedReason, boolean anonymized, int factors, int k, Integer slotNo, String factorsText) {}

    public record ProductRef(long id, String productId, String title, String kind, Instant addedAt, Instant endsAt) {}

    public record IssuanceRow(long id, String seatId, String deviceCode, String kind, String subject, String kid, String nonce, Instant issuedAt, Instant notAfter,
                              String issuer, String channel, String fingerprint, String source) {}

    public record TransferRow(long id, String seatId, String fromDevice, String toDevice, String signedBy, Instant at, boolean accepted) {}

    public record Detail(LicenseRow license, List<SeatRow> seats, List<ProductRef> products, List<IssuanceRow> issuances,
                         List<TransferRow> transfers, List<AuditLog.Entry> history, int transfersLastYear) {}

    public static String effective(String state, Instant endAt, int graceDays, Instant now) {
        if (!"ACTIVE".equals(state)) return state;
        if (endAt == null || !now.isAfter(endAt)) return "ACTIVE";
        return now.isAfter(endAt.plus(Duration.ofDays(graceDays))) ? "EXPIRED" : "GRACE";
    }

    static Timestamp ts(Instant i) { return i == null ? null : Timestamp.from(i); }

    static Instant inst(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static final String SELECT = "SELECT l.*, c.name AS client_name, (SELECT COUNT(*) FROM lic_seat s WHERE s.license_pk = l.id AND s.state = 'ACTIVE') AS used"
            + " FROM lic_license l JOIN lic_client c ON c.id = l.client_id";

    private static LicenseRow row(ResultSet rs, int i) throws SQLException {
        return new LicenseRow(rs.getLong("id"), rs.getString("license_id"), rs.getLong("client_id"), rs.getString("client_name"),
                rs.getString("kind"), rs.getString("state"), rs.getString("state_reason"), rs.getInt("seats_allowed"), rs.getInt("used"),
                inst(rs, "start_at"), inst(rs, "end_at"), rs.getInt("grace_days"), rs.getInt("transfer_cap"), inst(rs, "created_at"),
                rs.getString("created_by"));
    }

    static SeatRow seat(ResultSet rs) throws SQLException {
        String factors = rs.getString("factors");
        return new SeatRow(rs.getLong("id"), rs.getString("seat_id"), rs.getString("subject"), rs.getString("device_code"), rs.getString("state"), inst(rs, "first_seen"),
                inst(rs, "last_seen"), inst(rs, "released_at"), rs.getString("released_reason"), rs.getBoolean("anonymized"),
                factors == null || factors.isBlank() ? 0 : factors.split(",").length, rs.getInt("k"), (Integer) rs.getObject("slot_no"), factors);
    }

    // ------------------------------------------------------------------ queries

    public record Filter(String q, String state, Long clientId, String productId, Integer expiringDays, String sort, boolean desc) {}

    public Page<LicenseRow> list(Filter f, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        if (f.q() != null && !f.q().isBlank()) {
            String q = f.q().trim();
            where.append(" AND (l.license_id LIKE ? ESCAPE '!' OR c.name LIKE ? ESCAPE '!' OR EXISTS (SELECT 1 FROM lic_seat s2 WHERE s2.license_pk = l.id"
                    + " AND (s2.device_code LIKE ? ESCAPE '!' OR s2.seat_id LIKE ? ESCAPE '!')))");
            args.add(Validate.like(q.toLowerCase(Locale.ROOT)));
            args.add(Validate.like(q));
            args.add(Validate.like(q.toUpperCase(Locale.ROOT)));
            args.add(Validate.like(q.toLowerCase(Locale.ROOT)));
        }
        if (f.state() != null && !f.state().isBlank()) {
            switch (f.state()) {
                case "ACTIVE" -> { where.append(" AND l.state = 'ACTIVE' AND (l.end_at IS NULL OR l.end_at >= ?)"); args.add(now); }
                case "EXPIRED" -> { where.append(" AND (l.state = 'EXPIRED' OR (l.state = 'ACTIVE' AND l.end_at < ?))"); args.add(now); }
                case "SUSPENDED", "REVOKED" -> { where.append(" AND l.state = ?"); args.add(f.state()); }
                default -> throw ApiException.badRequest("État inconnu : active, suspendue, révoquée ou expirée attendu");
            }
        }
        if (f.clientId() != null) { where.append(" AND l.client_id = ?"); args.add(f.clientId()); }
        if (f.productId() != null && !f.productId().isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM lic_license_product lp JOIN lic_product p ON p.id = lp.product_pk WHERE lp.license_pk = l.id AND p.product_id = ?)");
            args.add(f.productId().trim());
        }
        if (f.expiringDays() != null) {
            where.append(" AND l.state = 'ACTIVE' AND l.end_at IS NOT NULL AND l.end_at >= ? AND l.end_at < ?");
            args.add(now);
            args.add(Timestamp.from(Instant.now().plus(Duration.ofDays(Validate.range(f.expiringDays(), "Expiration sous (jours)", 1, 3650)))));
        }
        String col = switch (f.sort() == null ? "created" : f.sort()) {
            case "end" -> "l.end_at";
            case "client" -> "c.name";
            case "id" -> "l.license_id";
            case "seats" -> "l.seats_allowed";
            default -> "l.created_at";
        };
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM lic_license l JOIN lic_client c ON c.id = l.client_id" + where, Long.class, args.toArray());
        List<Object> a2 = new ArrayList<>(args);
        a2.add(size);
        a2.add((long) page * size);
        List<LicenseRow> rows = jdbc.query(SELECT + where + " ORDER BY " + col + (f.desc() ? " DESC" : " ASC") + ", l.id DESC LIMIT ? OFFSET ?",
                LicenseService::row, a2.toArray());
        return new Page<>(rows, page, size, total);
    }

    /** CSV export, newest first, at most 10 000 rows (bounded memory). */
    public List<LicenseRow> exportRows(Filter f) {
        List<LicenseRow> all = new ArrayList<>();
        for (int p = 0; p < 100; p++) {
            Page<LicenseRow> pg = list(f, p, 100);
            all.addAll(pg.items());
            if (!pg.hasNext()) break;
        }
        return all;
    }

    public LicenseRow get(String licenseId) {
        List<LicenseRow> r = jdbc.query(SELECT + " WHERE l.license_id = ?", LicenseService::row, licenseId);
        if (r.isEmpty()) throw ApiException.notFound("Licence introuvable : " + licenseId);
        return r.get(0);
    }

    @Transactional(readOnly = true)
    public Detail detail(String licenseId) {
        LicenseRow l = get(licenseId);
        List<SeatRow> seats = jdbc.query("SELECT * FROM lic_seat WHERE license_pk = ? ORDER BY first_seen, id", (rs, i) -> seat(rs), l.id());
        List<ProductRef> products = productsOf(l.id());
        List<IssuanceRow> iss = jdbc.query("SELECT * FROM lic_issuance WHERE license_pk = ? ORDER BY id DESC LIMIT 100",
                (rs, i) -> new IssuanceRow(rs.getLong("id"), rs.getString("seat_id"), rs.getString("device_code"), rs.getString("kind"), rs.getString("subject"), rs.getString("kid"),
                        rs.getString("nonce"), inst(rs, "issued_at"), inst(rs, "not_after"), rs.getString("issuer"), rs.getString("channel"), rs.getString("token_fingerprint"),
                        rs.getString("source")), l.id());
        List<TransferRow> tr = jdbc.query("SELECT * FROM lic_transfer WHERE license_pk = ? ORDER BY at DESC LIMIT 100",
                (rs, i) -> new TransferRow(rs.getLong("id"), rs.getString("seat_id"), rs.getString("from_device_code"), rs.getString("to_device_code"), rs.getString("signed_by"),
                        inst(rs, "at"), rs.getBoolean("accepted")), l.id());
        int lastYear = transfersSince(l.id(), Instant.now().minus(Duration.ofDays(365)));
        return new Detail(l, seats, products, iss, tr, audit.forTarget("LICENSE", licenseId, 200), lastYear);
    }

    List<ProductRef> productsOf(long licensePk) {
        return jdbc.query("SELECT p.id, p.product_id, p.title, p.kind, lp.added_at, lp.ends_at FROM lic_license_product lp JOIN lic_product p ON p.id = lp.product_pk"
                        + " WHERE lp.license_pk = ? ORDER BY p.product_id",
                (rs, i) -> new ProductRef(rs.getLong("id"), rs.getString("product_id"), rs.getString("title"), rs.getString("kind"), inst(rs, "added_at"),
                        inst(rs, "ends_at")), licensePk);
    }

    List<String> bundlesOf(long productPk, String productId) {
        List<String> b = jdbc.queryForList("SELECT bundle_id FROM lic_product_bundle WHERE product_pk = ? ORDER BY bundle_id", String.class, productPk);
        return b.isEmpty() ? List.of(productId) : b;
    }

    int transfersSince(long licensePk, Instant since) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lic_transfer WHERE license_pk = ? AND accepted = TRUE AND at >= ?", Integer.class, licensePk, ts(since));
    }

    /** Looks a device code up across all licences (support: "which licence is this TV on?"): current seat codes and the codes of past activations. */
    public List<Map<String, Object>> byDeviceCode(String code) {
        String c = DeviceIdentity.normalize(code);
        return jdbc.query("SELECT DISTINCT l.license_id, l.state, l.end_at, l.grace_days, c.name AS client_name, s.seat_id, s.subject, s.state AS seat_state, s.first_seen, s.last_seen"
                        + " FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk JOIN lic_client c ON c.id = l.client_id"
                        + " WHERE s.device_code = ? OR s.id IN (SELECT i.seat_pk FROM lic_issuance i WHERE i.device_code = ? AND i.seat_pk IS NOT NULL) ORDER BY s.last_seen DESC LIMIT 50",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("licenseId", rs.getString("license_id"));
                    m.put("client", rs.getString("client_name"));
                    m.put("licenseState", effective(rs.getString("state"), inst(rs, "end_at"), rs.getInt("grace_days"), Instant.now()));
                    m.put("seatId", rs.getString("seat_id"));
                    m.put("subject", rs.getString("subject"));
                    m.put("seatState", rs.getString("seat_state"));
                    m.put("firstSeen", inst(rs, "first_seen"));
                    m.put("lastSeen", inst(rs, "last_seen"));
                    return m;
                }, c, c);
    }

    // ------------------------------------------------------------------ commands

    public record NewLicense(String licenseId, Long clientId, String kind, Integer seats, Instant startAt, Instant endAt, Integer graceDays,
                             Integer transferCap, List<String> productIds) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow create(Actor actor, NewLicense n) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        if (n.clientId() == null) throw ApiException.badRequest("Client obligatoire");
        Long erased = jdbc.queryForObject("SELECT COUNT(*) FROM lic_client WHERE id = ? AND erased_at IS NULL", Long.class, n.clientId());
        if (erased == null || erased == 0) throw ApiException.notFound("Client introuvable (ou effacé)");
        String kind = n.kind() == null || n.kind().isBlank() ? "PAID" : n.kind().trim().toUpperCase(java.util.Locale.ROOT);
        if (!kind.equals("PAID") && !kind.equals("TRIAL")) throw ApiException.badRequest("Type de licence : PAID ou TRIAL");
        int seats = Validate.range(n.seats() == null ? (kind.equals("TRIAL") ? 1 : null) : n.seats(), "Nombre de postes", 1, 1000);
        Instant start = n.startAt() == null ? Instant.now() : n.startAt();
        if (n.endAt() != null && !n.endAt().isAfter(start)) throw ApiException.badRequest("La fin doit être postérieure au début");
        int grace = Validate.range(n.graceDays() == null ? props.defaultGraceDays() : n.graceDays(), "Période de grâce (jours)", 0, 365);
        int cap = Validate.range(n.transferCap() == null ? props.defaultTransferCap() : n.transferCap(), "Plafond de transferts par an", 0, 100);
        String licenseId = n.licenseId() == null || n.licenseId().isBlank() ? newLicenseId(kind.equals("TRIAL") ? "essai" : "lic") : Validate.licenseId(n.licenseId());
        Instant now = Instant.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(con -> {
                var ps = con.prepareStatement("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap,"
                        + " created_by, created_at, updated_at) VALUES (?,?,?,'ACTIVE',?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, licenseId);
                ps.setLong(2, n.clientId());
                ps.setString(3, kind);
                ps.setInt(4, seats);
                ps.setTimestamp(5, ts(start));
                ps.setTimestamp(6, ts(n.endAt()));
                ps.setInt(7, grace);
                ps.setInt(8, cap);
                ps.setString(9, actor.name());
                ps.setTimestamp(10, ts(now));
                ps.setTimestamp(11, ts(now));
                return ps;
            }, keys);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Cet identifiant de licence existe déjà : " + licenseId);
        }
        long pk = keys.getKey().longValue();
        for (String pid : n.productIds() == null ? List.<String>of() : new TreeSet<>(n.productIds())) addProductRow(pk, Validate.productId(pid), null, now);
        audit.record(actor, "LICENSE_CREATE", "LICENSE", licenseId, null,
                Map.of("client", n.clientId(), "kind", kind, "seats", seats, "end", String.valueOf(n.endAt()), "products", n.productIds() == null ? 0 : n.productIds().size()));
        return get(licenseId);
    }

    /** Name of the shared client that holds the licences generated by the server for a production key (no personal data). */
    public static final String AUTO_CLIENT = "Clés de production (licences générées)";
    private static final java.security.SecureRandom AUTO_RANDOM = new java.security.SecureRandom();

    /**
     * Creates, in the caller's transaction, the licence of a production key issued without a licence id: {@code lic-} + 10 lowercase hex digits
     * (SecureRandom, not already used), 1 seat, the default transfer cap, no bundle. Audited (licence created); nothing secret in the log.
     * {@code startAt} / {@code endAt} come from the SIGNED {@code usage} right of the key (docs/ACTIVATION-FORMAT.md, usage ceiling): {@code endAt} is null ONLY for a key that is really
     * unlimited (audit R-1: a licence without end is paid by the wallet as UNLIMITED, whatever the duration of the key).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow createAuto(Actor actor, Instant startAt, Instant endAt) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        List<Long> ids = jdbc.queryForList("SELECT id FROM lic_client WHERE name = ? AND erased_at IS NULL ORDER BY id LIMIT 1", Long.class, AUTO_CLIENT);
        long clientId;
        if (ids.isEmpty()) {
            Instant now = Instant.now();
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(con -> {
                var ps = con.prepareStatement("INSERT INTO lic_client (name, contact, notes, created_at, updated_at) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, AUTO_CLIENT);
                ps.setString(2, null);
                ps.setString(3, "Client technique créé par le serveur pour les licences générées à l'émission d'une clé de production.");
                ps.setTimestamp(4, ts(now));
                ps.setTimestamp(5, ts(now));
                return ps;
            }, keys);
            clientId = keys.getKey().longValue();
            audit.record(actor, "CLIENT_CREATE", "CLIENT", Long.toString(clientId), null, Map.of("auto", true));
        } else {
            clientId = ids.get(0);
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            byte[] b = new byte[5];
            AUTO_RANDOM.nextBytes(b);
            String id = "lic-" + java.util.HexFormat.of().formatHex(b);
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", Integer.class, id);
            if (n != null && n == 0) return create(actor, new NewLicense(id, clientId, "PAID", 1, startAt, endAt, null, null, null));
        }
        throw new IllegalStateException("no free licence id");
    }

    /** Pins the start and the end of a licence to the usage right of the key just issued (same transaction as its creation: the exact issue date is only known there). */
    void alignToKey(long licensePk, Instant startAt, Instant endAt) {
        jdbc.update("UPDATE lic_license SET start_at = ?, end_at = ?, updated_at = ?, version = version + 1 WHERE id = ?", ts(startAt), ts(endAt), ts(Instant.now()), licensePk);
    }

    String newLicenseId(String prefix) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(prefix + "-");
            for (int i = 0; i < 10; i++) {
                if (i == 5) sb.append('-');
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", Integer.class, sb.toString());
            if (n != null && n == 0) return sb.toString();
        }
        throw new IllegalStateException("no free licence id");
    }

    private void addProductRow(long licensePk, String productId, Instant endsAt, Instant now) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM lic_product WHERE product_id = ? AND active = TRUE", Long.class, productId);
        if (ids.isEmpty()) throw ApiException.notFound("Bouquet introuvable ou désactivé : " + productId);
        try {
            jdbc.update("INSERT INTO lic_license_product (license_pk, product_pk, added_at, ends_at) VALUES (?,?,?,?)", licensePk, ids.get(0), ts(now), ts(endsAt));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Ce bouquet est déjà dans la licence : " + productId);
        }
    }

    /**
     * Takes the row lock of the licence: every seat decision happens under it. The mutating methods run in READ COMMITTED
     * (MySQL's default REPEATABLE READ would freeze the snapshot at the first plain read and hide a seat committed by the
     * transaction we waited for), so each statement after the lock sees the latest committed seats.
     */
    LicenseRow lock(String licenseId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM lic_license WHERE license_id = ? FOR UPDATE", Long.class, licenseId);
        if (ids.isEmpty()) throw ApiException.notFound("Licence introuvable : " + licenseId);
        return get(licenseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow suspend(Actor actor, String licenseId, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        String why = Validate.reason(reason);
        LicenseRow l = lock(licenseId);
        if (!l.state().equals("ACTIVE")) throw ApiException.conflict("Seule une licence active peut être suspendue (état : " + l.state() + ")");
        setState(licenseId, "SUSPENDED", why);
        audit.record(actor, "LICENSE_SUSPEND", "LICENSE", licenseId, why, null);
        return get(licenseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow resume(Actor actor, String licenseId, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        String why = Validate.reason(reason);
        LicenseRow l = lock(licenseId);
        if (!l.state().equals("SUSPENDED")) throw ApiException.conflict("Seule une licence suspendue peut être réactivée (état : " + l.state() + ")");
        setState(licenseId, "ACTIVE", null);
        audit.record(actor, "LICENSE_RESUME", "LICENSE", licenseId, why, null);
        return get(licenseId);
    }

    /** Revocation is final: the licence is also written to the revocation list served to the devices. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow revoke(Actor actor, String licenseId, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        String why = Validate.reason(reason);
        LicenseRow l = lock(licenseId);
        if (l.state().equals("REVOKED")) throw ApiException.conflict("Cette licence est déjà révoquée");
        setState(licenseId, "REVOKED", why);
        // every active seat is revoked in the signed revocation list (at this instant: an activation issued before is revoked) and in the registry
        Instant now = Instant.now();
        List<String> seatIds = jdbc.queryForList("SELECT seat_id FROM lic_seat WHERE license_pk = ? AND state = 'ACTIVE'", String.class, l.id());
        jdbc.update("UPDATE lic_seat SET state = 'RELEASED', slot_no = NULL, released_at = ?, released_reason = 'licence révoquée' WHERE license_pk = ? AND state = 'ACTIVE'", ts(now), l.id());
        for (String seat : seatIds) revokeSeatRow(l.wireId(), seat, why, actor.name(), now);
        audit.record(actor, "LICENSE_REVOKE", "LICENSE", licenseId, why, Map.of("seatsFreed", l.seatsUsed()));
        return get(licenseId);
    }

    /** Sets a new end date (null = no end). Only forward in time unless a reason is given. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow extend(Actor actor, String licenseId, Instant newEnd, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        LicenseRow l = lock(licenseId);
        if (l.state().equals("REVOKED")) throw ApiException.conflict("Une licence révoquée ne se prolonge pas");
        if (newEnd != null && !newEnd.isAfter(l.startAt())) throw ApiException.badRequest("La nouvelle fin doit suivre le début");
        boolean shortens = newEnd != null && l.endAt() != null && newEnd.isBefore(l.endAt()) || newEnd != null && l.endAt() == null;
        String why = shortens ? Validate.reason(reason) : Validate.text(reason, "Motif", 500, false);
        jdbc.update("UPDATE lic_license SET end_at = ?, updated_at = ?, version = version + 1 WHERE id = ?", ts(newEnd), ts(Instant.now()), l.id());
        // an expired licence extended past now comes back to life
        if (l.state().equals("EXPIRED") && (newEnd == null || newEnd.isAfter(Instant.now()))) setState(licenseId, "ACTIVE", null);
        audit.record(actor, "LICENSE_EXTEND", "LICENSE", licenseId, why, Map.of("from", String.valueOf(l.endAt()), "to", String.valueOf(newEnd)));
        return get(licenseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow setSeats(Actor actor, String licenseId, Integer seats, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        int n = Validate.range(seats, "Nombre de postes", 1, 1000);
        LicenseRow l = lock(licenseId);
        if (n < l.seatsUsed()) {
            throw ApiException.conflict("Impossible de descendre à " + n + " poste(s) : " + l.seatsUsed() + " sont utilisés. Libérez d'abord des postes.");
        }
        String why = n < l.seatsAllowed() ? Validate.reason(reason) : Validate.text(reason, "Motif", 500, false);
        jdbc.update("UPDATE lic_license SET seats_allowed = ?, updated_at = ?, version = version + 1 WHERE id = ?", n, ts(Instant.now()), l.id());
        audit.record(actor, "LICENSE_SEATS", "LICENSE", licenseId, why, Map.of("from", l.seatsAllowed(), "to", n));
        return get(licenseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow update(Actor actor, String licenseId, Integer graceDays, Integer transferCap, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        LicenseRow l = lock(licenseId);
        int g = graceDays == null ? l.graceDays() : Validate.range(graceDays, "Période de grâce (jours)", 0, 365);
        int c = transferCap == null ? l.transferCap() : Validate.range(transferCap, "Plafond de transferts par an", 0, 100);
        jdbc.update("UPDATE lic_license SET grace_days = ?, transfer_cap = ?, updated_at = ?, version = version + 1 WHERE id = ?", g, c, ts(Instant.now()), l.id());
        audit.record(actor, "LICENSE_UPDATE", "LICENSE", licenseId, Validate.text(reason, "Motif", 500, false), Map.of("grace", g, "transferCap", c));
        return get(licenseId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow addProduct(Actor actor, String licenseId, String productId, Instant endsAt) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        LicenseRow l = lock(licenseId);
        if (l.state().equals("REVOKED")) throw ApiException.conflict("Une licence révoquée ne reçoit plus de bouquet");
        String pid = Validate.productId(productId);
        addProductRow(l.id(), pid, endsAt, Instant.now());
        audit.record(actor, "LICENSE_ADD_PRODUCT", "LICENSE", licenseId, null, Map.of("product", pid));
        return get(licenseId);
    }

    /** Frees a seat (the device loses its right at its next online check, through the signed revocation list): reason mandatory. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LicenseRow releaseSeat(Actor actor, String licenseId, String seatId, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        String why = Validate.reason(reason);
        String seat = Validate.seatId(seatId);
        LicenseRow l = lock(licenseId);
        Instant now = Instant.now();
        int n = jdbc.update("UPDATE lic_seat SET state = 'RELEASED', slot_no = NULL, released_at = ?, released_reason = ? WHERE license_pk = ? AND seat_id = ? AND state = 'ACTIVE'",
                ts(now), why, l.id(), seat);
        if (n == 0) throw ApiException.notFound("Aucun poste actif avec cet identifiant dans cette licence");
        revokeSeatRow(l.wireId(), seat, why, actor.name(), now);
        audit.record(actor, "SEAT_RELEASE", "LICENSE", licenseId, why, Map.of("seat", seat));
        return get(licenseId);
    }

    /** Writes the seat to the revocation list (and, with the server key, to the registry as a signed `revoke` event). */
    void revokeSeatRow(String wireLicense, String seatId, String reason, String by, Instant instant) {
        Instant at = instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS); // whole seconds, like the issue dates (an activation is revoked when issuedAt <= this date)
        jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?,?,?,?,?)", wireLicense, seatId, AuditLog.clip(reason, 500), AuditLog.clip(by, 64), ts(at));
        registry.emitRevokeSeat(wireLicense, seatId, at);
    }

    /** Revokes a signing key (kid): devices drop everything signed by it when they receive the list. Reason mandatory. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revokeKey(Actor actor, String kid, String reason) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        String why = Validate.reason(reason);
        if (kid == null || !kid.matches("[0-9a-f]{16}")) throw ApiException.badRequest("kid invalide (16 chiffres hexadécimaux)");
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", Integer.class, kid);
        if (n != null && n > 0) throw ApiException.conflict("Cette clé est déjà révoquée");
        Instant now = Instant.now();
        jdbc.update("INSERT INTO lic_revocation (kid, reason, revoked_by, revoked_at) VALUES (?,?,?,?)", kid, why, actor.name(), ts(now));
        registry.emitRevokeKey(kid, now);
        audit.record(actor, "KEY_REVOKE", "KEY", kid, why, null);
    }

    private void setState(String licenseId, String state, String reason) {
        jdbc.update("UPDATE lic_license SET state = ?, state_reason = ?, updated_at = ?, version = version + 1 WHERE license_id = ?", state, reason, ts(Instant.now()), licenseId);
    }

    // ------------------------------------------------------------------ seats (also used by issuance and ledger import)

    public enum SeatOutcome { CREATED, REUSED, REACTIVATED }

    /** @param seat the seat row (after the change); @param outcome what happened */
    public record SeatResult(SeatRow seat, SeatOutcome outcome) {}

    /** The ACTIVE seat of this licence and subject whose hardware matches the device (k of n factors, § 8.3), or null. MUST run under the licence lock. */
    SeatRow findMatchingSeat(LicenseRow l, String subject, DeviceIdentity.Request device) {
        for (SeatRow s : jdbc.query("SELECT * FROM lic_seat WHERE license_pk = ? AND state = 'ACTIVE' AND subject = ? AND anonymized = FALSE ORDER BY first_seen, id",
                (rs, i) -> seat(rs), l.id(), subject)) {
            if (DeviceIdentity.matches(DeviceIdentity.parseStored(s.factorsText()), s.k(), device.factors())) return s;
        }
        return null;
    }

    /**
     * Gives the device a seat of the licence (docs/ACTIVATION-FORMAT.md § 8.3). MUST be called inside a transaction that holds {@link #lock}.
     * The same hardware (at least k factors out of n, even after a module was replaced) keeps its seat and consumes nothing, whichever tool
     * answers; otherwise a new seat takes the lowest free slot in 1..quota (default seat id: SHA-256 of licence and factor set); none free = 409
     * (a transfer is then needed, and the server never signs one).
     */
    SeatResult allocateSeat(LicenseRow l, String subject, DeviceIdentity.Request device, Instant seenAt) {
        SeatRow match = findMatchingSeat(l, subject, device);
        if (match != null) {
            jdbc.update("UPDATE lic_seat SET last_seen = ? WHERE id = ?", ts(seenAt), match.id());
            return new SeatResult(match, SeatOutcome.REUSED);
        }
        String seatId = WireActivation.defaultSeat(l.wireId(), device.factors());
        List<SeatRow> same = jdbc.query("SELECT * FROM lic_seat WHERE license_pk = ? AND seat_id = ?", (rs, i) -> seat(rs), l.id(), seatId);
        int slot = freeSlot(l);
        try {
            if (same.isEmpty()) {
                jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)",
                        l.id(), seatId, subject, device.code(), device.factorsText(), device.k(), slot, ts(seenAt), ts(seenAt));
                return new SeatResult(jdbc.query("SELECT * FROM lic_seat WHERE license_pk = ? AND seat_id = ?", (rs, i) -> seat(rs), l.id(), seatId).get(0), SeatOutcome.CREATED);
            }
            // a released seat of the same hardware comes back (it takes a free slot again); never on another device (owner decision 2026-10-06: no seat changes TV)
            if (!device.code().equals(same.get(0).deviceCode())) {
                throw ApiException.conflict("Ce poste libéré est lié à un autre appareil : il ne peut être réactivé que sur le même appareil (un poste ne change pas de téléviseur)");
            }
            jdbc.update("UPDATE lic_seat SET state = 'ACTIVE', slot_no = ?, released_at = NULL, released_reason = NULL, last_seen = ?, device_code = ?, factors = ?, k = ?, anonymized = FALSE WHERE id = ?",
                    slot, ts(seenAt), device.code(), device.factorsText(), device.k(), same.get(0).id());
            return new SeatResult(jdbc.query("SELECT * FROM lic_seat WHERE id = ?", (rs, i) -> seat(rs), same.get(0).id()).get(0), SeatOutcome.REACTIVATED);
        } catch (DuplicateKeyException e) {
            // cannot happen under the row lock; the constraint is the safety net
            log.warn("seat constraint hit on licence {}", l.licenseId());
            throw ApiException.conflict("Quota de postes atteint (" + l.seatsAllowed() + "/" + l.seatsAllowed() + ")");
        }
    }

    private int freeSlot(LicenseRow l) {
        BitSet used = new BitSet();
        jdbc.query("SELECT slot_no FROM lic_seat WHERE license_pk = ? AND state = 'ACTIVE'", rs -> { used.set(rs.getInt(1)); }, l.id());
        int slot = used.nextClearBit(1);
        if (slot > l.seatsAllowed()) {
            throw ApiException.conflict("Plus de poste disponible (" + used.cardinality() + "/" + l.seatsAllowed() + ") : libérez un poste, augmentez le quota, ou faites un transfert avec le bureau ou le téléphone propriétaire (le serveur ne signe jamais un transfert)");
        }
        return slot;
    }

    int activeSeats(long licensePk) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lic_seat WHERE license_pk = ? AND state = 'ACTIVE'", Integer.class, licensePk);
    }

    // ------------------------------------------------------------------ housekeeping

    /** Marks as EXPIRED the licences whose grace period is over (the effective state is already computed on read). */
    @Scheduled(cron = "0 20 3 * * *", zone = "Africa/Douala")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int expireDue() {
        Instant now = Instant.now();
        int n = 0;
        record Due(String licenseId, Instant end, int grace) {}
        for (Due d : jdbc.query("SELECT license_id, end_at, grace_days FROM lic_license WHERE state = 'ACTIVE' AND end_at < ? LIMIT 500",
                (rs, i) -> new Due(rs.getString("license_id"), rs.getTimestamp("end_at").toInstant(), rs.getInt("grace_days")), ts(now))) {
            if (now.isAfter(d.end().plus(Duration.ofDays(d.grace())))) {
                setState(d.licenseId(), "EXPIRED", "fin de validité et période de grâce dépassées");
                audit.record(new Actor("system", Role.OWNER, "system", true), "LICENSE_EXPIRE", "LICENSE", d.licenseId(), null, null);
                n++;
            }
        }
        return n;
    }

    public Set<String> states() { return Set.of("ACTIVE", "SUSPENDED", "REVOKED", "EXPIRED"); }
}
