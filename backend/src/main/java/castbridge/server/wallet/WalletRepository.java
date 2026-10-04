package castbridge.server.wallet;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Accès SQL du module (identités, historique, politique, réconciliation). Le grand livre lui-même est {@link JdbcLedger}. Aucune écriture hors de ce module. */
@Repository
@WalletModuleConfig.Enabled
public class WalletRepository {
    private final JdbcTemplate jdbc;

    public WalletRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---- identités ----

    public record Identity(String holder, long apiDeviceId, Instant anchorAt, boolean openedUnlimited, String edition, Instant lastSyncAt, boolean frozen, String frozenReason, String installPub, boolean superKey, Instant trialEndAt) {}

    private static Instant instant(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    public Optional<Identity> identity(String holder) {
        return jdbc.query("SELECT holder, api_device_id, anchor_at, opened_unlimited, edition, last_sync_at, frozen, frozen_reason, install_pub, super_key, trial_end_at FROM wallet_identity WHERE holder = ?",
                (rs, i) -> new Identity(rs.getString("holder"), rs.getLong("api_device_id"), instant(rs, "anchor_at"), rs.getBoolean("opened_unlimited"), rs.getString("edition"),
                        instant(rs, "last_sync_at"), rs.getBoolean("frozen"), rs.getString("frozen_reason"), rs.getString("install_pub"), rs.getBoolean("super_key"), instant(rs, "trial_end_at")), holder).stream().findFirst();
    }

    /** L'identité (la plus récemment ouverte) liée à cet appareil API, s'il en a une. */
    public Optional<String> identityOfDevice(long apiDeviceId) {
        return jdbc.queryForList("SELECT holder FROM wallet_identity WHERE api_device_id = ? ORDER BY created_at DESC, holder LIMIT 1", String.class, apiDeviceId).stream().findFirst();
    }

    /** Ouvre l'identité si elle n'existe pas (liaison à l'appareil API, anti-vol) ; vrai si elle vient d'être ouverte. Une course sur la clé primaire est un simple « déjà ouverte ». */
    public boolean openIdentity(String holder, long apiDeviceId, Instant now) {
        if (identity(holder).isPresent()) return false;
        try {
            jdbc.update("INSERT INTO wallet_identity (holder, api_device_id, created_at) VALUES (?, ?, ?)", holder, apiDeviceId, Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** L'ancre ne se déplace jamais une fois posée (les clés d'idempotence des tranches en dépendent). */
    public void setAnchorIfAbsent(String holder, Instant anchor) {
        jdbc.update("UPDATE wallet_identity SET anchor_at = ? WHERE holder = ? AND anchor_at IS NULL", Timestamp.from(anchor), holder);
    }

    public void markOpenedUnlimited(String holder) { jdbc.update("UPDATE wallet_identity SET opened_unlimited = TRUE WHERE holder = ?", holder); }

    /** {@code edition} n'est qu'une étiquette d'affichage ; les DROITS de mise viennent de {@code superKey} et {@code trialEnd} (fin du dernier essai lu, ou null), jamais de l'étiquette (audit w22-05, E1). */
    public void touchSync(String holder, String edition, boolean superKey, Instant trialEnd, Instant now) {
        jdbc.update("UPDATE wallet_identity SET edition = ?, super_key = ?, trial_end_at = ?, last_sync_at = ? WHERE holder = ?", edition, superKey, trialEnd == null ? null : Timestamp.from(trialEnd),
                Timestamp.from(now), holder);
    }

    /** Clés d'idempotence des attributions déjà inscrites pour cette identité (le calcul paresseux n'en inscrit jamais deux fois). */
    public Set<String> grantKeys(String holder) {
        // « _ » et « % » sont des jokers de LIKE : l'identité n'en contient pas (XXXX-XXXX-XXXX-XXXX) ; on borne quand même par le préfixe exact après lecture
        String prefix = "grant:" + holder + ":";
        Set<String> out = new HashSet<>();
        for (String k : jdbc.queryForList("SELECT idem_key FROM wallet_txn WHERE kind = 'GRANT' AND idem_key LIKE ?", String.class, prefix + "%")) if (k.startsWith(prefix)) out.add(k);
        return out;
    }

    /**
     * Réaffectation (administrateur) : l'identité n'est plus liée à aucun appareil ({@code api_device_id = 0}) et perd sa clé d'installation ; le prochain appareil qui prouve la possession la lie.
     * Rend vrai si l'identité existe.
     */
    public boolean clearBinding(String holder) { return jdbc.update("UPDATE wallet_identity SET api_device_id = 0, install_pub = NULL WHERE holder = ?", holder) == 1; }

    /** Lie une identité libérée ({@code api_device_id = 0}) à cet appareil : le premier qui arrive l'emporte ; vrai si CET appel a lié. */
    public boolean bindIfFree(String holder, long apiDeviceId) { return jdbc.update("UPDATE wallet_identity SET api_device_id = ? WHERE holder = ? AND api_device_id = 0", apiDeviceId, holder) == 1; }

    /** Retient la clé d'installation de la TV liée à CET appareil si elle n'en a pas encore (jamais remplacée sans réaffectation). */
    public void adoptInstallKey(String holder, long apiDeviceId, String installPub) {
        jdbc.update("UPDATE wallet_identity SET install_pub = ? WHERE holder = ? AND api_device_id = ? AND install_pub IS NULL", installPub, holder, apiDeviceId);
    }

    /**
     * Le {@code seq} de {@code cbw1} doit changer quand N'IMPORTE QUOI de signé change (audit M4) : {@code seq = Σ des versions des comptes de l'identité + snap_bump}. Chaque écriture d'une
     * transaction du grand livre incrémente la {@code version} des soldes qu'elle touche (même ligne, même transaction que le solde) ; {@code snap_bump} compte les changements des champs signés
     * hors soldes. {@code digest} = condensé de ces champs (édition, gel, mises permises) ; un condensé différent du dernier émis incrémente {@code snap_bump} (comparaison et mise à jour en UNE
     * instruction : sûr en concurrence). Seules les écritures de CETTE identité font bouger son seq.
     */
    public void bumpIfChanged(String holder, String digest) {
        jdbc.update("UPDATE wallet_identity SET snap_digest = ?, snap_bump = snap_bump + (CASE WHEN snap_digest IS NULL THEN 0 ELSE 1 END) WHERE holder = ? AND (snap_digest IS NULL OR snap_digest <> ?)",
                digest, holder, digest);
    }

    /** Les quatre soldes et le {@code seq} d'une identité. */
    public record SnapshotRead(long ndem, long ndemLocked, long mboko, long mbokoLocked, long seq) {}

    /**
     * Soldes ET {@code seq} lus par UNE SEULE instruction SQL (l'audit M4 a relevé cinq lectures séparées qui pouvaient « verrouiller » un seq incohérent) : le seq vient des {@code version} des
     * MÊMES lignes que les soldes, donc chaque solde est toujours lu avec la version qui lui correspond (même ligne, validée avec lui), quelle que soit l'isolation de la base.
     */
    public SnapshotRead snapshotRead(String holder) {
        long[] v = new long[4];
        long[] seq = new long[] {0};
        boolean[] any = new boolean[1];
        jdbc.query("SELECT a.cur, a.pocket, b.balance, b.version, (SELECT COALESCE(MAX(i.snap_bump), 0) FROM wallet_identity i WHERE i.holder = ?) AS bump FROM wallet_account a "
                + "JOIN wallet_balance b ON b.account_id = a.id WHERE a.holder = ? AND a.pocket IN ('DISPO', 'BLOQUE') ORDER BY a.id", rs -> {
            boolean n = "NDEM".equals(rs.getString("cur")), locked = "BLOQUE".equals(rs.getString("pocket"));
            v[(n ? 0 : 2) + (locked ? 1 : 0)] = rs.getLong("balance");
            if (!any[0]) seq[0] = rs.getLong("bump");
            any[0] = true;
            seq[0] += rs.getLong("version");
        }, holder, holder);
        if (!any[0]) {   // aucun compte : seulement le compteur (identité sans écriture)
            Long bump = jdbc.queryForObject("SELECT COALESCE(MAX(snap_bump), 0) FROM wallet_identity WHERE holder = ?", Long.class, holder);
            seq[0] = bump == null ? 0 : bump;
        }
        return new SnapshotRead(v[0], v[1], v[2], v[3], seq[0]);
    }

    // ---- licences : réclamation et intervalles figés ----

    /** Réclame la licence pour cette identité si personne ne l'a réclamée, puis rend l'identité qui la détient (une licence ne paie qu'UNE identité, pour toujours). */
    public String claimLicense(String licenseId, String holder, Instant now) {
        try {
            jdbc.update("INSERT INTO wallet_license_claim (license_id, holder, claimed_at) VALUES (?, ?, ?)", licenseId, holder, Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            // déjà réclamée
        }
        return jdbc.queryForObject("SELECT holder FROM wallet_license_claim WHERE license_id = ?", String.class, licenseId);
    }

    /** L'identité qui détient la licence, s'il y en a une (lecture seule). */
    public Optional<String> licenseHolder(String licenseId) {
        return jdbc.queryForList("SELECT holder FROM wallet_license_claim WHERE license_id = ?", String.class, licenseId).stream().findFirst();
    }

    /** Clés d'idempotence de tranches déjà inscrites sous ce préfixe exact (clés de licence : {@code grant:lic:<licence>:} ; un identifiant de licence ne contient ni « % » ni « _ »). */
    public Set<String> grantKeysWithPrefix(String prefix) {
        Set<String> out = new HashSet<>();
        for (String k : jdbc.queryForList("SELECT idem_key FROM wallet_txn WHERE kind = 'GRANT' AND idem_key LIKE ?", String.class, prefix + "%")) if (k.startsWith(prefix)) out.add(k);
        return out;
    }

    // ---- périodes payées : (identité, monnaie, case) UNIQUE, indépendante de la licence (audit w23-05 HIGH-3) ----

    /** Les cases déjà payées de l'identité, sous la forme {@code <monnaie>|<case>} ({@code OPEN|0} = l'ouverture illimitée). */
    public Set<String> periodClaims(String holder) {
        Set<String> out = new HashSet<>();
        jdbc.query("SELECT cur, slot FROM wallet_period_claim WHERE holder = ?", rs -> { out.add(rs.getString("cur") + "|" + rs.getLong("slot")); }, holder);
        return out;
    }

    /** Inscrit une case payée (rejeu et course : un doublon est ignoré). Sert à rattraper les paiements d'avant la table (clés {@code grant:lic:…} déjà posées). */
    public void backfillPeriodClaim(String holder, String cur, long slot, Instant periodStart, String idemKey, Instant now) {
        try {
            jdbc.update("INSERT INTO wallet_period_claim (holder, cur, slot, period_start, idem_key, claimed_at) VALUES (?,?,?,?,?,?)", holder, cur, slot, Timestamp.from(periodStart), idemKey, Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            // déjà réclamée
        }
    }

    public record SpanRow(Instant start, Instant end) {}

    public List<SpanRow> licenseSpans(String licenseId) {
        return jdbc.query("SELECT start_at, end_at FROM wallet_license_span WHERE license_id = ? ORDER BY start_at", (rs, i) -> new SpanRow(instant(rs, "start_at"), instant(rs, "end_at")), licenseId);
    }

    public void insertLicenseSpan(String licenseId, Instant start, Instant end) {
        try {
            jdbc.update("INSERT INTO wallet_license_span (license_id, start_at, end_at) VALUES (?, ?, ?)", licenseId, Timestamp.from(start), end == null ? null : Timestamp.from(end));
        } catch (DuplicateKeyException e) {
            // un autre contact l'a déjà figé
        }
    }

    /** Ferme un intervalle encore ouvert ; la première fermeture est définitive (jamais déplacée). */
    public void closeLicenseSpan(String licenseId, Instant start, Instant end) {
        jdbc.update("UPDATE wallet_license_span SET end_at = ? WHERE license_id = ? AND start_at = ? AND end_at IS NULL", Timestamp.from(end), licenseId, Timestamp.from(start));
    }

    // ---- dons de l'administration (H2) ----

    public record AdminGrant(long id, String idemKey, String requestedBy, String identity, String currency, long amount, String reason, String state, String approvedBy, Instant createdAt) {}

    private static AdminGrant adminGrant(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminGrant(rs.getLong("id"), rs.getString("idem_key"), rs.getString("requested_by"), rs.getString("identity"), rs.getString("cur"), rs.getLong("amount"), rs.getString("reason"),
                rs.getString("state"), rs.getString("approved_by"), instant(rs, "created_at"));
    }

    private static final String ADMIN_GRANT_COLS = "SELECT id, idem_key, requested_by, identity, cur, amount, reason, state, approved_by, created_at FROM wallet_admin_grant ";

    public Optional<AdminGrant> adminGrantByKey(String idemKey) { return jdbc.query(ADMIN_GRANT_COLS + "WHERE idem_key = ?", (rs, i) -> adminGrant(rs), idemKey).stream().findFirst(); }

    public Optional<AdminGrant> adminGrantById(long id) { return jdbc.query(ADMIN_GRANT_COLS + "WHERE id = ?", (rs, i) -> adminGrant(rs), id).stream().findFirst(); }

    public long insertAdminGrant(String idemKey, String by, String identity, String cur, long amount, String reason, String state, Instant now) {
        jdbc.update("INSERT INTO wallet_admin_grant (idem_key, requested_by, identity, cur, amount, reason, state, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", idemKey, by, identity, cur, amount, reason, state,
                Timestamp.from(now));
        return jdbc.queryForObject("SELECT id FROM wallet_admin_grant WHERE idem_key = ?", Long.class, idemKey);
    }

    /** Passe la demande de PENDING à {@code state} (une seule fois) ; vrai si CET appel a décidé. */
    public boolean decideAdminGrant(long id, String state, String approvedBy, Instant now) {
        return jdbc.update("UPDATE wallet_admin_grant SET state = ?, approved_by = ?, decided_at = ? WHERE id = ? AND state = 'PENDING'", state, approvedBy, Timestamp.from(now), id) == 1;
    }

    public long adminGrantsSince(Instant since) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_admin_grant WHERE created_at > ?", Long.class, Timestamp.from(since)); }

    public long adminAppliedSince(String cur, Instant since) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM wallet_admin_grant WHERE cur = ? AND state = 'APPLIED' AND created_at > ?", Long.class, cur, Timestamp.from(since));
    }

    // ---- historique ----

    public record HistoryLine(long id, String kind, String currency, long amount, Instant at, String idemKey, String actor, String reason, String counterparty) {}

    /** 50 lignes au plus, de la plus récente à la plus ancienne ; les écritures d'un même compte dans une même transaction sont cumulées ; {@code before} = identifiant de la dernière ligne vue. */
    public List<HistoryLine> history(String holder, int limit, Long before) {
        String sql = "SELECT MAX(e.id) AS id, t.id AS txn_id, t.kind, a.cur, SUM(e.amount) AS amount, t.created_at, t.idem_key, t.actor, t.reason FROM wallet_entry e "
                + "JOIN wallet_account a ON a.id = e.account_id JOIN wallet_txn t ON t.id = e.txn_id WHERE a.holder = ? AND a.pocket = 'DISPO' "
                + "GROUP BY t.id, t.kind, t.created_at, t.idem_key, t.actor, t.reason, a.id, a.cur " + (before == null ? "" : "HAVING MAX(e.id) < ? ") + "ORDER BY MAX(e.id) DESC LIMIT ?";
        Object[] args = before == null ? new Object[] {holder, limit} : new Object[] {holder, before, limit};
        List<Object[]> raw = jdbc.query(sql, (rs, i) -> new Object[] {rs.getLong("id"), rs.getLong("txn_id"), rs.getString("kind"), rs.getString("cur"), rs.getLong("amount"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("idem_key"), rs.getString("actor"), rs.getString("reason")}, args);
        List<HistoryLine> out = new ArrayList<>();
        for (Object[] r : raw) {
            String counterparty = null;
            if ("TRANSFER".equals(r[2])) {
                List<String> other = jdbc.queryForList("SELECT a.holder FROM wallet_entry e JOIN wallet_account a ON a.id = e.account_id WHERE e.txn_id = ? AND a.holder <> ? AND a.pocket = 'DISPO'",
                        String.class, r[1], holder);
                if (!other.isEmpty()) counterparty = other.get(0);
            }
            out.add(new HistoryLine((Long) r[0], (String) r[2], (String) r[3], (Long) r[4], (Instant) r[5], (String) r[6], (String) r[7], (String) r[8], counterparty));
        }
        return out;
    }

    // ---- réconciliation (I-1, I-3, I-8) ----

    /** Nombre de comptes dont le solde en cache diffère de la somme de leurs écritures (I-8). */
    public long derivedMismatches() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_balance b LEFT JOIN (SELECT account_id, SUM(amount) AS s FROM wallet_entry GROUP BY account_id) e ON e.account_id = b.account_id "
                + "WHERE b.balance <> COALESCE(e.s, 0)", Long.class);
    }

    public List<Long> derivedMismatchSample(int limit) {
        return jdbc.queryForList("SELECT b.account_id FROM wallet_balance b LEFT JOIN (SELECT account_id, SUM(amount) AS s FROM wallet_entry GROUP BY account_id) e ON e.account_id = b.account_id "
                + "WHERE b.balance <> COALESCE(e.s, 0) ORDER BY b.account_id LIMIT ?", Long.class, limit);
    }

    /** Σ des soldes par monnaie, ventilée : joueurs / système (pocket SYS) / poches BLOQUE. */
    public Map<String, Long> balanceSums(String cur) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("all", jdbc.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.cur = ?", Long.class, cur));
        m.put("players", jdbc.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.cur = ? AND a.pocket <> 'SYS'", Long.class, cur));
        m.put("system", jdbc.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.cur = ? AND a.pocket = 'SYS'", Long.class, cur));
        m.put("bloque", jdbc.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.cur = ? AND a.pocket = 'BLOQUE'", Long.class, cur));
        m.put("entries", jdbc.queryForObject("SELECT COALESCE(SUM(e.amount), 0) FROM wallet_entry e JOIN wallet_account a ON a.id = e.account_id WHERE a.cur = ?", Long.class, cur));
        m.put("openEscrows", jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM wallet_escrow WHERE cur = ? AND state = 'OPEN'", Long.class, cur));
        return m;
    }

    public long systemBalance(String holder, String cur) {
        List<Long> r = jdbc.queryForList("SELECT b.balance FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.holder = ? AND a.cur = ? AND a.pocket = 'SYS'", Long.class, holder, cur);
        return r.isEmpty() ? 0 : r.get(0);
    }

    // ---- politique ----

    public record PolicyRow(String name, long value, long min, long max) {}

    public Map<String, PolicyRow> policyRows() {
        Map<String, PolicyRow> m = new LinkedHashMap<>();
        jdbc.query("SELECT name, val, min_value, max_value FROM wallet_policy ORDER BY name", rs -> {
            m.put(rs.getString("name"), new PolicyRow(rs.getString("name"), rs.getLong("val"), rs.getLong("min_value"), rs.getLong("max_value")));
        });
        return m;
    }

    public int updatePolicy(String name, long value, String by, Instant now) {
        return jdbc.update("UPDATE wallet_policy SET val = ?, updated_at = ?, updated_by = ? WHERE name = ?", value, Timestamp.from(now), by, name);
    }
}
