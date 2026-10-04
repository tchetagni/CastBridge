package castbridge.server.licenses;

import castbridge.server.common.Times;
import castbridge.server.licenses.ActivationSigner.SignerScope;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Enregistre, côté serveur, une activation de PRODUCTION émise HORS LIGNE (console du téléphone, bureau) quand la TV la présente : crée ou rattache la LICENCE et le POSTE, pour que le
 * portefeuille la lise ({@code LicenseFacts}). Conception W23-B § 3 ; règles du propriétaire du 2026-10-04 : les clés d'activation restent hors ligne, la notification au serveur est
 * asynchrone (téléphone) ou synchrone (TV en ligne), une licence n'est PAS transférable, licence ≠ clé d'activation, préproduction = production (aucun raccourci).
 * <p>Seul écrivain {@code lic_*} de ce chemin (le module {@code activations} l'appelle, il n'écrit jamais {@code lic_*}). Une présentation est acceptée seulement si, dans cet ordre :
 * <ol>
 *   <li>le jeton {@code cbx1} est canonique, signé par une clé de {@link TrustedKeys} non révoquée, de genre production (l'essai n'ouvre aucune licence) et sans droit {@code super} ;</li>
 *   <li>il vise CE matériel (le code des facteurs signés = le code annoncé) et la TV a prouvé la possession de sa clé d'installation (preuve {@code bind}, exigée, jamais de raccourci) ;</li>
 *   <li>la clé porte {@code ISSUE_PRODUCTION} pour créer une licence ou un poste ({@code REACTIVATE} ne fait que rattacher un poste existant) ;</li>
 *   <li>ni la clé, ni le poste, ni la licence ne sont révoqués ;</li>
 *   <li>fenêtre d'installation de 48 h : faute de preuve de l'heure d'installation (la TV 0.14.32 n'en envoie pas), le jeton est accepté tant que sa propre fenêtre court
 *       ({@code installTimeUnproven} + alerte douce) ; au-delà il attend la décision du propriétaire sauf émission DÉCLARÉE (journal, registre ou serveur) ;</li>
 *   <li>plafond d'auto-créations par clé : 10 par jour et 50 par mois.</li>
 * </ol>
 * Jamais un refus silencieux : chaque issue porte un motif ({@link Registration#reason}) que le portefeuille affiche. Jamais de révocation automatique : les alertes sont DOUCES
 * ({@code lic_audit}, action {@code REGISTRATION_ALERT}) ; la décision est au propriétaire ({@link #decide}). Idempotent (clé : l'empreinte SHA-256 du jeton) et indépendant de l'ordre :
 * le même ensemble de présentations, du registre et des émissions du serveur donne le même état {@code lic_*}. Aucun jeton complet n'est gardé ni journalisé : empreinte seulement.
 */
@Service
public class ReportedActivationRegistrar {
    private static final Logger log = LoggerFactory.getLogger(ReportedActivationRegistrar.class);

    /** Client technique des licences créées ici (une fois, sans contact). */
    public static final String CLIENT_NAME = "Client anonyme (activation rapportée)";
    public static final String CREATED_BY_PREFIX = "report:";
    /** D-W23B-7 : plafond d'auto-créations par clé d'outil (réglable plus tard par la table de politique). */
    public static final int MAX_PER_DAY = 10, MAX_PER_MONTH = 50;
    public static final long WINDOW_SLACK_MS = 5 * 60_000L;
    public static final long LATE_NOTICE_DAYS = 400;
    static final int MAX_TOKEN_CHARS = 8_192;
    static final Actor REGISTRAR = new Actor("registrar", Role.OWNER, "system", true);

    public enum Via {
        WALLET, REPORT, ADMIN;

        String code() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** IGNORED : rien à enregistrer (essai, droit super, module éteint) ; REFUSED : jamais enregistré ; PENDING_DECISION : attend le propriétaire (motif affiché). */
    public enum Status { REGISTERED, ATTACHED, PENDING_DECISION, REFUSED, IGNORED }

    /**
     * @param token       le jeton {@code cbx1} présenté (jamais gardé)
     * @param deviceCode  le code d'appareil annoncé par la TV
     * @param installPub  clé d'installation prouvée (base64 brute), null si non prouvée
     * @param bindProven  la TV a prouvé la possession de cette clé pour ce code et cet appareil API
     */
    public record Presented(String token, String deviceCode, String installPub, boolean bindProven) {}

    public record Registration(Status status, String reason, String licenseId, String seatId, String fp, boolean installTimeUnproven) {
        public boolean registered() { return status == Status.REGISTERED || status == Status.ATTACHED; }
    }

    /** Ce que la présentation affirme, lu dans le jeton SIGNÉ. */
    private record Claims(String fp, String kid, String nonce, String license, String seat, String code, DeviceIdentity.Request device, Instant issuedAt, Instant expiresAt,
                          Instant usageFrom, Instant usageTo, boolean unlimited) {}

    private record Row(String fp, String status, String reason, boolean declared, String installPub, String licenseId, String seatId, Instant registeredAt) {}

    private final JdbcTemplate jdbc;
    private final LicenseService licenses;
    private final AuditLog audit;
    private final LicenseProperties props;
    private final TrustedKeys trusted;
    private final TransactionTemplate tx;

    public ReportedActivationRegistrar(JdbcTemplate jdbc, LicenseService licenses, AuditLog audit, LicenseProperties props, TrustedKeys trusted, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.licenses = licenses;
        this.audit = audit;
        this.props = props;
        this.trusted = trusted;
        this.tx = new TransactionTemplate(manager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    // ------------------------------------------------------------------ entrée

    /** Une présentation par jeton ; une erreur technique sur l'un ne perd pas les autres (motif {@code UNAVAILABLE}, rien n'est écrit pour lui). */
    public List<Registration> registerAll(List<Presented> items, Via via, Instant now) {
        List<Registration> out = new ArrayList<>();
        for (Presented p : items) {
            try {
                out.add(register(p, via, now));
            } catch (RuntimeException e) {
                log.warn("registrar : une présentation n'a pas pu être traitée ({})", e.getClass().getSimpleName());
                out.add(new Registration(Status.REFUSED, "UNAVAILABLE", null, null, null, false));
            }
        }
        return out;
    }

    public Registration register(Presented p, Via via, Instant now) {
        if (!props.enabled()) return new Registration(Status.IGNORED, "MODULE_OFF", null, null, null, false);
        Object checked = check(p, now);
        if (checked instanceof Registration early) return early;
        Claims c = (Claims) checked;
        ensureAnonymousClient(now);   // AVANT la transaction de la licence : jamais une seconde connexion tenue pendant qu'on en détient déjà une (épuisement du pool sous charge)
        RuntimeException last = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                return tx.execute(st -> apply(c, p, via, now, false));
            } catch (DuplicateKeyException | PessimisticLockingFailureException e) {
                last = e;   // deux présentations simultanées de la même licence : la seconde rattache ce que la première vient de créer
                try {
                    Thread.sleep(5L + (long) (Math.random() * 40));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    // ------------------------------------------------------------------ 1. contrôles sans écriture

    /** Jeton → revendications, ou la {@link Registration} qui refuse. Aucune écriture ici : un jeton inconnu ou mal signé ne laisse aucune trace en base. */
    private Object check(Presented p, Instant now) {
        String token = p.token() == null ? "" : p.token().trim();
        if (token.isEmpty() || token.length() > MAX_TOKEN_CHARS) return refused("MALFORMED", null);
        WireActivation.Decoded d = WireActivation.decode(token);
        if (d == null) return refused("MALFORMED", null);
        WireActivation.Fields a = d.fields();
        String fp = Hashing.sha256Hex(token);
        TrustedKeys.Key key = trusted.find(a.kid());
        if (key == null) return refused("UNKNOWN_KEY", fp);
        if (revoked(a.kid())) return refused("REVOKED_KEY", fp);
        boolean sigOk;
        try {
            sigOk = LicenseKeyring.verify(key.publicKey(), d.text().getBytes(StandardCharsets.UTF_8), d.signature());
        } catch (RuntimeException e) {
            sigOk = false;
        }
        if (!sigOk) return refused("BAD_SIGNATURE", fp);
        if (a.kind().equals("trial")) return new Registration(Status.IGNORED, "TRIAL", null, null, fp, false);   // licence ≠ clé d'activation : un essai n'ouvre aucune licence
        if (!a.subject().equals("tv")) return refused("WRONG_SUBJECT", fp);
        if (a.rights().stream().anyMatch(WireActivation::isSuper)) return new Registration(Status.IGNORED, "SUPER", null, null, fp, false);   // aucune attribution automatique
        String code = DeviceIdentity.parseCode(p.deviceCode());
        if (code == null || !DeviceIdentity.code(a.factors()).equals(code)) return refused("CLONE", fp);
        if (!key.allows(SignerScope.ISSUE_PRODUCTION) && !key.allows(SignerScope.REACTIVATE)) return refused("KEY_NOT_ALLOWED", fp);
        if (!p.bindProven() || p.installPub() == null) return refused("BIND_PROOF", fp);
        try {
            Validate.licenseId(a.license());
        } catch (castbridge.server.web.ApiException e) {
            return refused("MALFORMED", fp);
        }
        if (a.issuedAt() > now.toEpochMilli() + WireActivation.DAY_MS) return refused("CLOCK", fp);
        Instant usageFrom = null, usageTo = null;
        List<String> usage = a.rights().stream().filter(WireActivation::isUsage).toList();
        if (usage.size() > 1) return refused("BAD_RIGHTS", fp);
        if (usage.size() == 1) {
            String[] u = usage.get(0).split("\\|", -1);
            long from = Long.parseLong(u[2]), to = Long.parseLong(u[3]);
            if (from <= 0 || to <= from || to - from > ActivationService.PRODUCTION_MAX_DAYS * WireActivation.DAY_MS) return refused("BAD_RIGHTS", fp);
            usageFrom = Instant.ofEpochMilli(from);
            usageTo = Instant.ofEpochMilli(to);
        }
        DeviceIdentity.Request device = new DeviceIdentity.Request(a.factors(), code, a.k());
        return new Claims(fp, a.kid(), a.nonce(), a.license(), a.seat(), code, device, Instant.ofEpochMilli(a.issuedAt()), Instant.ofEpochMilli(a.notAfter()), usageFrom, usageTo, usage.isEmpty());
    }

    private static Registration refused(String reason, String fp) { return new Registration(Status.REFUSED, reason, null, null, fp, false); }

    private boolean revoked(String kid) { return count("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", kid) > 0; }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    // ------------------------------------------------------------------ 2. décision et écriture (une transaction, verrou de la licence)

    /** @param force {@code true} : le propriétaire a décidé (la fenêtre, le plafond journalier et l'heure d'installation inconnue ne bloquent plus). */
    private Registration apply(Claims c, Presented p, Via via, Instant now, boolean force) {
        Row row = row(c.fp());
        if (row != null && row.installPub() != null && p.installPub() != null && !row.installPub().equals(p.installPub())) {
            // même jeton, autre clé d'installation : la première vue fait foi (« premier gagne »), rien ne change, alerte douce
            alert("BIND_MISMATCH", c.license(), c.fp(), c.code());
            return new Registration(Status.PENDING_DECISION, "BIND_MISMATCH", c.license(), c.seat(), c.fp(), true);
        }
        if (row != null && (row.status().equals("REGISTERED") || row.status().equals("ATTACHED"))) {
            jdbc.update("UPDATE lic_registration SET last_server_at = ? WHERE fp = ?", Times.ts(now), c.fp());
            return new Registration(Status.valueOf(row.status()), row.reason(), row.licenseId(), row.seatId(), c.fp(), true);
        }
        boolean declared = force || (row != null && row.declared()) || count("SELECT COUNT(*) FROM lic_issuance WHERE kid = ? AND nonce = ? AND source <> 'REPORT'", c.kid(), c.nonce()) > 0;
        boolean unproven = true;   // la TV 0.14.32 n'envoie aucune preuve d'heure d'installation (la note scellée de w23-06 l'apportera)
        Outcome o = evaluate(c, p, now, declared, force);
        if (o.status() == Status.REGISTERED || o.status() == Status.ATTACHED) {
            if (unproven && (row == null || !row.status().equals(o.status().name()))) alert("INSTALL_TIME_UNPROVEN", c.license(), c.fp(), c.code());
        }
        persist(c, p, via, now, o, declared, row);
        return new Registration(o.status(), o.reason(), o.licenseId() != null ? o.licenseId() : c.license(), o.seatId(), c.fp(), unproven);
    }

    private record Outcome(Status status, String reason, String licenseId, String seatId) {
        static Outcome of(Status s, String reason, String lic, String seat) { return new Outcome(s, reason, lic, seat); }
    }

    private Outcome evaluate(Claims c, Presented p, Instant now, boolean declared, boolean force) {
        // révocations du poste et de la licence
        Instant seatRevokedAt = jdbc.query("SELECT MAX(revoked_at) FROM lic_revocation WHERE license_id = ? AND seat_id = ?", rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null,
                c.license(), c.seat());
        if (seatRevokedAt != null && c.issuedAt().toEpochMilli() <= seatRevokedAt.toEpochMilli()) return Outcome.of(Status.REFUSED, "REVOKED_SEAT", c.license(), c.seat());
        // fenêtre d'installation (heure du serveur) puis délai maximal de notification
        boolean inWindow = now.toEpochMilli() <= c.expiresAt().toEpochMilli() + WINDOW_SLACK_MS;
        if (!force && now.isAfter(c.issuedAt().plus(Duration.ofDays(LATE_NOTICE_DAYS)))) return Outcome.of(Status.PENDING_DECISION, "LATE_NOTICE", c.license(), c.seat());
        if (!inWindow && !declared) return Outcome.of(Status.PENDING_DECISION, "INSTALL_TIME_UNKNOWN", c.license(), c.seat());

        List<Long> pks = jdbc.queryForList("SELECT id FROM lic_license WHERE license_id = ? FOR UPDATE", Long.class, c.license());
        TrustedKeys.Key key = trusted.find(c.kid());
        boolean mayCreate = key != null && key.allows(SignerScope.ISSUE_PRODUCTION);
        if (pks.isEmpty()) return create(c, p, now, declared, force, mayCreate);

        LicenseService.LicenseRow l = licenses.get(c.license());
        if (l.kind().equals("TRIAL")) return Outcome.of(Status.PENDING_DECISION, "KIND_MISMATCH", c.license(), c.seat());
        if (l.state().equals("REVOKED")) return Outcome.of(Status.REFUSED, "LICENSE_REVOKED", c.license(), c.seat());
        String eff = l.effectiveState();
        LicenseService.SeatRow seat = seatOf(l.id(), c.seat());
        if (seat != null) {
            boolean sameHardware = DeviceIdentity.matches(DeviceIdentity.parseStored(seat.factorsText()), seat.k(), c.device().factors());
            if (!sameHardware) {
                // une licence ne se transfère jamais : le même poste réclamé par un autre matériel ne paie rien, alerte douce
                alert("SEAT_CLAIMED_BY_OTHER_HARDWARE", c.license(), c.fp(), c.code());
                return Outcome.of(Status.PENDING_DECISION, "TRANSFER_CAP", c.license(), c.seat());
            }
            if (seat.state().equals("RELEASED")) return Outcome.of(Status.PENDING_DECISION, "SEAT_RELEASED", c.license(), c.seat());
            record(l, seat.id(), c, now);
            alignEnd(l, c);
            return Outcome.of(Status.ATTACHED, l.state().equals("SUSPENDED") ? "LICENSE_SUSPENDED" : null, c.license(), seat.seatId());
        }
        LicenseService.SeatRow same = licenses.findMatchingSeat(l, "tv", c.device());
        if (same != null) {
            // le même matériel sous un autre identifiant de poste (deux outils) : alias, compté une fois
            try {
                jdbc.update("INSERT INTO lic_seat_alias (license_pk, alias_seat_id, seat_pk) VALUES (?,?,?)", l.id(), c.seat(), same.id());
            } catch (DuplicateKeyException e) {
                // déjà fusionné
            }
            record(l, same.id(), c, now);
            alignEnd(l, c);
            return Outcome.of(Status.ATTACHED, null, c.license(), same.seatId());
        }
        if (l.state().equals("SUSPENDED")) {
            record(l, null, c, now);
            return Outcome.of(Status.ATTACHED, "LICENSE_SUSPENDED", c.license(), null);
        }
        if (!eff.equals("ACTIVE") && !eff.equals("GRACE")) return Outcome.of(Status.REFUSED, "EXPIRED", c.license(), c.seat());
        if (eff.equals("GRACE")) return Outcome.of(Status.PENDING_DECISION, "EXPIRED", c.license(), c.seat());
        if (!mayCreate) return Outcome.of(Status.REFUSED, "KEY_NOT_ALLOWED", c.license(), c.seat());
        String capped = rateCapped(c.kid(), now, declared);
        if (capped != null) {
            alert("KEY_RATE", c.license(), c.fp(), c.code());
            return Outcome.of(Status.PENDING_DECISION, capped, c.license(), c.seat());
        }
        if (licenses.activeSeats(l.id()) >= l.seatsAllowed()) {
            alert("OVER_QUOTA", c.license(), c.fp(), c.code());
            return Outcome.of(Status.PENDING_DECISION, "OVER_QUOTA", c.license(), c.seat());
        }
        long seatPk = insertSeat(l, c, now);
        record(l, seatPk, c, now);
        alignEnd(l, c);
        audit.record(REGISTRAR, "SEAT_ATTACH_FROM_REPORT", "LICENSE", c.license(), null, details(c, "seat", c.seat()));
        return Outcome.of(Status.REGISTERED, null, c.license(), c.seat());
    }

    /** Licence inconnue : création depuis les revendications signées (§ 3.3). */
    private Outcome create(Claims c, Presented p, Instant now, boolean declared, boolean force, boolean mayCreate) {
        if (!mayCreate) return Outcome.of(Status.REFUSED, "KEY_NOT_ALLOWED", c.license(), c.seat());
        if (c.usageTo() != null && now.isAfter(c.usageTo().plus(Duration.ofDays(props.defaultGraceDays())))) return Outcome.of(Status.REFUSED, "EXPIRED", c.license(), c.seat());
        String capped = rateCapped(c.kid(), now, declared);
        if (capped != null) {
            alert("KEY_RATE", c.license(), c.fp(), c.code());
            return Outcome.of(Status.PENDING_DECISION, capped, c.license(), c.seat());
        }
        long clientId = jdbc.queryForList("SELECT id FROM lic_client WHERE name = ? AND erased_at IS NULL ORDER BY id LIMIT 1", Long.class, CLIENT_NAME).stream().findFirst()
                .orElseGet(() -> ensureAnonymousClient(now));
        String createdBy = AuditLog.clip(CREATED_BY_PREFIX + trusted.nameOf(c.kid()) + ":" + c.kid().substring(0, 8), 64);
        Instant start = c.usageFrom() != null ? c.usageFrom() : c.issuedAt();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at)"
                    + " VALUES (?,?,'PAID','ACTIVE',1,?,?,?,0,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, c.license());
            ps.setLong(2, clientId);
            ps.setTimestamp(3, Times.ts(start));
            ps.setTimestamp(4, Times.ts(c.usageTo()));
            ps.setInt(5, props.defaultGraceDays());
            ps.setString(6, createdBy);
            ps.setTimestamp(7, Times.ts(now));
            ps.setTimestamp(8, Times.ts(now));
            return ps;
        }, keys);
        LicenseService.LicenseRow l = licenses.get(c.license());
        long seatPk = insertSeat(l, c, now);
        record(l, seatPk, c, now);
        audit.record(REGISTRAR, "LICENSE_CREATE_FROM_REPORT", "LICENSE", c.license(), null,
                details(c, "kind", "PAID", "seats", 1, "start", start, "end", String.valueOf(c.usageTo()), "createdBy", createdBy));
        audit.record(REGISTRAR, "SEAT_ATTACH_FROM_REPORT", "LICENSE", c.license(), null, details(c, "seat", c.seat()));
        return Outcome.of(Status.REGISTERED, null, c.license(), c.seat());
    }

    /** Plafond par clé : {@code null} si rien ne bloque, sinon le motif. L'émission déclarée n'est limitée que par le plafond mensuel. */
    private String rateCapped(String kid, Instant now, boolean declared) {
        long day = count("SELECT COUNT(*) FROM lic_registration WHERE kid = ? AND status = 'REGISTERED' AND registered_at >= ?", kid, Times.ts(now.minus(Duration.ofDays(1))));
        long month = count("SELECT COUNT(*) FROM lic_registration WHERE kid = ? AND status = 'REGISTERED' AND registered_at >= ?", kid, Times.ts(now.minus(Duration.ofDays(30))));
        if (month >= MAX_PER_MONTH) return "KEY_RATE";
        if (!declared && day >= MAX_PER_DAY) return "KEY_RATE";
        return null;
    }

    /** Le client technique, créé une fois (verrou de processus + relecture ; plusieurs instances : au pire un doublon, la lecture prend toujours le plus petit identifiant). */
    private long ensureAnonymousClient(Instant now) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM lic_client WHERE name = ? AND erased_at IS NULL ORDER BY id LIMIT 1", Long.class, CLIENT_NAME);
        if (!ids.isEmpty()) return ids.get(0);
        synchronized (ReportedActivationRegistrar.class) {
            return tx.execute(st -> {
                List<Long> again = jdbc.queryForList("SELECT id FROM lic_client WHERE name = ? AND erased_at IS NULL ORDER BY id LIMIT 1", Long.class, CLIENT_NAME);
                if (!again.isEmpty()) return again.get(0);
                GeneratedKeyHolder keys = new GeneratedKeyHolder();
                jdbc.update(con -> {
                    var ps = con.prepareStatement("INSERT INTO lic_client (name, contact, notes, created_at, updated_at) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, CLIENT_NAME);
                    ps.setString(2, null);
                    ps.setString(3, "Client technique créé par le serveur pour les licences ouvertes par une activation de production notifiée (aucune donnée personnelle).");
                    ps.setTimestamp(4, Times.ts(now));
                    ps.setTimestamp(5, Times.ts(now));
                    return ps;
                }, keys);
                long id = keys.getKey().longValue();
                audit.record(REGISTRAR, "CLIENT_CREATE", "CLIENT", Long.toString(id), null, Map.of("auto", true, "origin", "report"));
                return id;
            });
        }
    }

    private long insertSeat(LicenseService.LicenseRow l, Claims c, Instant now) {
        BitSet used = new BitSet();
        jdbc.query("SELECT slot_no FROM lic_seat WHERE license_pk = ? AND state = 'ACTIVE'", rs -> { used.set(rs.getInt(1)); }, l.id());
        int slot = used.nextClearBit(1);
        if (slot > l.seatsAllowed()) throw new IllegalStateException("plus de poste libre");
        Instant seen = c.issuedAt();
        List<LicenseService.SeatRow> released = jdbc.query("SELECT * FROM lic_seat WHERE license_pk = ? AND seat_id = ?", (rs, i) -> LicenseService.seat(rs), l.id(), c.seat());
        if (!released.isEmpty()) {
            jdbc.update("UPDATE lic_seat SET state = 'ACTIVE', slot_no = ?, released_at = NULL, released_reason = NULL, last_seen = ?, device_code = ?, factors = ?, k = ?, anonymized = FALSE WHERE id = ?",
                    slot, Times.ts(now), c.code(), c.device().factorsText(), c.device().k(), released.get(0).id());
            return released.get(0).id();
        }
        jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?,?,?,?,?,?,?,'ACTIVE',?,?)",
                l.id(), c.seat(), "tv", c.code(), c.device().factorsText(), c.device().k(), slot, Times.ts(seen), Times.ts(now));
        return jdbc.queryForObject("SELECT id FROM lic_seat WHERE license_pk = ? AND seat_id = ?", Long.class, l.id(), c.seat());
    }

    /** Siège de cette licence pour un identifiant de poste, en suivant l'alias d'un doublon fusionné. */
    private LicenseService.SeatRow seatOf(long licensePk, String seatId) {
        List<LicenseService.SeatRow> r = jdbc.query("SELECT s.* FROM lic_seat s WHERE s.license_pk = ? AND s.seat_id = ? UNION ALL"
                + " SELECT s.* FROM lic_seat s JOIN lic_seat_alias a ON a.seat_pk = s.id WHERE a.license_pk = ? AND a.alias_seat_id = ?", (rs, i) -> LicenseService.seat(rs), licensePk, seatId, licensePk, seatId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** L'émission du jeton, une fois par (licence, nonce) ; empreinte SHA-256 seulement. Un doublon est ignoré. */
    private void record(LicenseService.LicenseRow l, Long seatPk, Claims c, Instant now) {
        if (count("SELECT COUNT(*) FROM lic_issuance WHERE license_pk = ? AND nonce = ?", l.id(), c.nonce()) > 0) return;
        jdbc.update("INSERT INTO lic_issuance (license_pk, seat_pk, seat_id, device_code, kind, subject, kid, nonce, issued_at, not_before, not_after, issuer, channel, token_fingerprint, source)"
                        + " VALUES (?,?,?,?,'PRODUCTION','tv',?,?,?,?,?,?,?,?,'REPORT')", l.id(), seatPk, c.seat(), c.code(), c.kid(), c.nonce(), Times.ts(c.issuedAt()), Times.ts(c.issuedAt()),
                Times.ts(c.expiresAt()), AuditLog.clip(trusted.nameOf(c.kid()), 64), "report", c.fp());
    }

    /**
     * Correction de {@code start_at} / {@code end_at} d'après le droit signé {@code usage} (audit R-1, D-W23B-3), pour les seules licences que ni le propriétaire ni le serveur n'ont datées :
     * celles du registre ({@code import:}, l'événement {@code license} ne porte aucune durée : fin inconnue = NULL) et celles ouvertes ici ({@code report:}). Règle SYMÉTRIQUE (même état final
     * quel que soit l'ordre des jetons et du registre) : une clé illimitée connue pour la licence la rend sans fin ; sinon la fin est le plus grand {@code to} des clés vérifiées (une licence
     * importée sans fin reçoit aussi le début du droit : le jeton signé fait foi). Jamais raccourcie. Si le propriétaire a prolongé la licence ({@code LICENSE_EXTEND}), sa décision prime.
     */
    private void alignEnd(LicenseService.LicenseRow l, Claims c) {
        boolean imported = l.createdBy().startsWith("import:");
        if (!imported && !l.createdBy().startsWith(CREATED_BY_PREFIX)) return;
        if (count("SELECT COUNT(*) FROM lic_audit WHERE action = 'LICENSE_EXTEND' AND target_type = 'LICENSE' AND target_id = ?", l.licenseId()) > 0) return;
        boolean unlimited = c.unlimited() || count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ? AND unlimited = TRUE AND status IN ('REGISTERED', 'ATTACHED')", l.licenseId()) > 0;
        if (unlimited) {
            if (l.endAt() != null) {
                jdbc.update("UPDATE lic_license SET end_at = NULL, updated_at = ?, version = version + 1 WHERE id = ?", Times.ts(Instant.now()), l.id());
                audit.record(REGISTRAR, "LICENSE_END_FROM_TOKEN", "LICENSE", l.licenseId(), null, Map.of("from", l.endAt().toString(), "to", "null", "fp", c.fp()));
            }
            return;
        }
        Instant to = c.usageTo();
        if (to == null) return;
        if (l.endAt() == null) {
            if (!imported) return;
            // le jeton signé fait foi pour le début comme pour la fin (l'événement `license` du registre ne porte que l'heure d'écriture de l'outil)
            Instant from = c.usageFrom() != null ? c.usageFrom() : l.startAt();
            jdbc.update("UPDATE lic_license SET start_at = ?, end_at = ?, updated_at = ?, version = version + 1 WHERE id = ?", Times.ts(from), Times.ts(to), Times.ts(Instant.now()), l.id());
            audit.record(REGISTRAR, "LICENSE_END_FROM_TOKEN", "LICENSE", l.licenseId(), null, Map.of("from", "null", "to", to.toString(), "start", from.toString(), "fp", c.fp()));
        } else if (to.isAfter(l.endAt())) {
            jdbc.update("UPDATE lic_license SET end_at = ?, updated_at = ?, version = version + 1 WHERE id = ?", Times.ts(to), Times.ts(Instant.now()), l.id());
            audit.record(REGISTRAR, "LICENSE_END_FROM_TOKEN", "LICENSE", l.licenseId(), null, Map.of("from", l.endAt().toString(), "to", to.toString(), "fp", c.fp()));
        }
    }

    // ------------------------------------------------------------------ journal de l'enregistrement

    private Row row(String fp) {
        List<Row> r = jdbc.query("SELECT fp, status, reason, declared, install_pub, license_id, seat_id, registered_at FROM lic_registration WHERE fp = ? FOR UPDATE",
                (rs, i) -> new Row(rs.getString("fp"), rs.getString("status"), rs.getString("reason"), rs.getBoolean("declared"), rs.getString("install_pub"), rs.getString("license_id"),
                        rs.getString("seat_id"), rs.getTimestamp("registered_at") == null ? null : rs.getTimestamp("registered_at").toInstant()), fp);
        return r.isEmpty() ? null : r.get(0);
    }

    private void persist(Claims c, Presented p, Via via, Instant now, Outcome o, boolean declared, Row existing) {
        boolean ok = o.status() == Status.REGISTERED || o.status() == Status.ATTACHED;
        String status = o.status().name();
        if (existing == null) {
            jdbc.update("INSERT INTO lic_registration (fp, kid, nonce, license_id, seat_id, device_code, factors, k, issued_at, expires_at, usage_from, usage_to, unlimited, install_pub,"
                            + " install_time_unproven, first_server_at, last_server_at, registered_at, via, status, reason, declared) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,?,?,?,?,?,?,?)",
                    c.fp(), c.kid(), c.nonce(), c.license(), c.seat(), c.code(), c.device().factorsText(), c.device().k(), Times.ts(c.issuedAt()), Times.ts(c.expiresAt()), Times.ts(c.usageFrom()),
                    Times.ts(c.usageTo()), c.unlimited(), p.installPub(), Times.ts(now), Times.ts(now), ok ? Times.ts(now) : null, via.code(), status, o.reason(), declared);
            if (o.status() == Status.PENDING_DECISION || o.status() == Status.REFUSED) pendingAudit(c, o);
        } else {
            jdbc.update("UPDATE lic_registration SET last_server_at = ?, registered_at = ?, status = ?, reason = ?, declared = ?, install_pub = COALESCE(install_pub, ?) WHERE fp = ?",
                    Times.ts(now), ok ? Times.ts(existing.registeredAt() != null ? existing.registeredAt() : now) : null, status, o.reason(), declared, p.installPub(), c.fp());
            if ((o.status() == Status.PENDING_DECISION || o.status() == Status.REFUSED) && (!status.equals(existing.status()) || !java.util.Objects.equals(o.reason(), existing.reason()))) pendingAudit(c, o);
        }
    }

    private void pendingAudit(Claims c, Outcome o) {
        audit.record(REGISTRAR, "REGISTRATION_PENDING", "LICENSE", c.license(), o.reason(), details(c, "status", o.status().name()));
    }

    private void alert(String kind, String licenseId, String fp, String code) {
        // alerte DOUCE, une seule fois par (jeton, nature) : une ligne du journal d'audit chaîné et une ligne de journal sans donnée sensible (jamais le code entier, jamais le jeton) ;
        // aucune révocation automatique
        if (count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND reason = ? AND details LIKE ?", kind, "%fp=" + fp + "%") > 0) return;
        audit.record(REGISTRAR, "REGISTRATION_ALERT", "LICENSE", licenseId, kind, Map.of("level", "soft", "fp", fp, "device", DeviceIdentity.masked(code)));
        log.warn("registrar : alerte douce {} (empreinte {})", kind, fp.substring(0, 8));
    }

    private static Map<String, Object> details(Claims c, Object... more) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fp", c.fp());
        m.put("kid", c.kid());
        m.put("device", DeviceIdentity.masked(c.code()));
        for (int i = 0; i + 1 < more.length; i += 2) m.put(String.valueOf(more[i]), more[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------ décision du propriétaire

    /** Une ligne en attente de décision (jamais le jeton). */
    public record Pending(String fp, String kid, String licenseId, String seatId, String deviceCode, String reason, Instant firstServerAt, String via) {}

    public List<Pending> pending(int limit) {
        return jdbc.query("SELECT fp, kid, license_id, seat_id, device_code, reason, first_server_at, via FROM lic_registration WHERE status = 'PENDING_DECISION' ORDER BY first_server_at LIMIT ?",
                (rs, i) -> new Pending(rs.getString("fp"), rs.getString("kid"), rs.getString("license_id"), rs.getString("seat_id"), DeviceIdentity.masked(rs.getString("device_code")),
                        rs.getString("reason"), rs.getTimestamp("first_server_at").toInstant(), rs.getString("via")), Math.max(1, Math.min(limit, 500)));
    }

    /**
     * Décision du propriétaire (compte nommé, TOTP, motif) sur une ligne en attente : accepter DÉCLARE l'émission (lève la fenêtre d'installation, le plafond journalier, les retenues de
     * rattrapage) puis rejoue la création ou le rattachement depuis les revendications gardées ; les autres règles (quota de postes, révocations, non-transfert) restent. Refuser clôt la ligne.
     */
    public Registration decide(Actor actor, String fp, boolean accept, String reason, Instant now) {
        actor.require(Role.Permission.LICENSE_WRITE, props.requireTotp());
        String why = Validate.reason(reason);
        return tx.execute(st -> {
            Map<String, Object> m = jdbc.queryForMap("SELECT * FROM lic_registration WHERE fp = ? FOR UPDATE", fp);
            if (!"PENDING_DECISION".equals(m.get("status"))) throw castbridge.server.web.ApiException.conflict("Cette ligne n'attend aucune décision (état : " + m.get("status") + ")");
            String license = (String) m.get("license_id");
            if (!accept) {
                jdbc.update("UPDATE lic_registration SET status = 'REFUSED', reason = 'OWNER_REFUSED', decided_by = ?, decided_at = ?, decision_reason = ? WHERE fp = ?", AuditLog.clip(actor.name(), 64), Times.ts(now), why, fp);
                audit.record(actor, "REGISTRATION_DECIDED", "LICENSE", license, why, Map.of("fp", fp, "decision", "refuse"));
                return new Registration(Status.REFUSED, "OWNER_REFUSED", license, (String) m.get("seat_id"), fp, true);
            }
            DeviceIdentity.Request dev = new DeviceIdentity.Request(DeviceIdentity.parseStored((String) m.get("factors")), (String) m.get("device_code"), ((Number) m.get("k")).intValue());
            Instant from = Times.instant(m.get("usage_from")), to = Times.instant(m.get("usage_to"));
            Claims c = new Claims(fp, (String) m.get("kid"), (String) m.get("nonce"), license, (String) m.get("seat_id"), (String) m.get("device_code"), dev, Times.instant(m.get("issued_at")),
                    Times.instant(m.get("expires_at")), from, to, bool(m.get("unlimited")));
            Presented p = new Presented(null, c.code(), (String) m.get("install_pub"), true);
            jdbc.update("UPDATE lic_registration SET declared = TRUE, decided_by = ?, decided_at = ?, decision_reason = ? WHERE fp = ?", AuditLog.clip(actor.name(), 64), Times.ts(now), why, fp);
            Registration r = apply(c, p, Via.ADMIN, now, true);
            audit.record(actor, "REGISTRATION_DECIDED", "LICENSE", license, why, Map.of("fp", fp, "decision", "accept", "result", r.status().name() + (r.reason() == null ? "" : ":" + r.reason())));
            return r;
        });
    }

    private static boolean bool(Object v) { return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0; }

    /** Étiquette de 8 hexadécimaux d'un jeton (journaux et pages) : jamais le jeton. */
    public static String label(String token) { return token == null ? "-" : Hashing.sha256Hex(token.trim()).substring(0, 8); }

    static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
}
