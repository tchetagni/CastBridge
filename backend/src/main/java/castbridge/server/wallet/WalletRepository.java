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

    public record Identity(String holder, long apiDeviceId, Instant anchorAt, boolean openedUnlimited, String edition, Instant lastSyncAt, boolean frozen, String frozenReason) {}

    private static Instant instant(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    public Optional<Identity> identity(String holder) {
        return jdbc.query("SELECT holder, api_device_id, anchor_at, opened_unlimited, edition, last_sync_at, frozen, frozen_reason FROM wallet_identity WHERE holder = ?",
                (rs, i) -> new Identity(rs.getString("holder"), rs.getLong("api_device_id"), instant(rs, "anchor_at"), rs.getBoolean("opened_unlimited"), rs.getString("edition"),
                        instant(rs, "last_sync_at"), rs.getBoolean("frozen"), rs.getString("frozen_reason")), holder).stream().findFirst();
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

    public void touchSync(String holder, String edition, Instant now) {
        jdbc.update("UPDATE wallet_identity SET edition = ?, last_sync_at = ? WHERE holder = ?", edition, Timestamp.from(now), holder);
    }

    /** Clés d'idempotence des attributions déjà inscrites pour cette identité (le calcul paresseux n'en inscrit jamais deux fois). */
    public Set<String> grantKeys(String holder) {
        // « _ » et « % » sont des jokers de LIKE : l'identité n'en contient pas (XXXX-XXXX-XXXX-XXXX) ; on borne quand même par le préfixe exact après lecture
        String prefix = "grant:" + holder + ":";
        Set<String> out = new HashSet<>();
        for (String k : jdbc.queryForList("SELECT idem_key FROM wallet_txn WHERE kind = 'GRANT' AND idem_key LIKE ?", String.class, prefix + "%")) if (k.startsWith(prefix)) out.add(k);
        return out;
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
