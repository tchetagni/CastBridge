package castbridge.server.activations;

import castbridge.server.licenses.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** What the two hash chains of the module (act_event, adm_read_audit) share: genesis, key, hashing, the head row, the anchor left by a cold archive. */
final class Chains {
    static final String GENESIS = "0".repeat(64);
    /** The shape of a whole device code: it must never enter the history. */
    static final Pattern DEVICE_CODE = Pattern.compile("(?<![0-9A-Za-z-])[0-9A-Z]{4}(-[0-9A-Z]{4}){3}(?![0-9A-Za-z-])", Pattern.CASE_INSENSITIVE);

    private Chains() {}

    /** The HMAC key of the chains (SHA-256 of the file act-audit.key), or null: then the chains are plain SHA-256 (weaker, still tamper evident). */
    static byte[] readKey(Path file) {
        try {
            return Files.isReadable(file) ? Hashing.sha256(Files.readAllBytes(file)) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String hash(byte[] key, String... parts) {
        byte[] bytes = String.join("\u001f", parts).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(key == null ? Hashing.sha256(bytes) : Hashing.hmac("HmacSHA256", key, bytes));
    }

    static String nz(Object o) { return o == null ? "" : o.toString(); }

    /** Control characters out, trimmed, cut. */
    static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Locks the head of a chain (serializes every writer: concurrent appends cannot fork it) and returns {last id, last hash}. */
    static Object[] lockHead(JdbcTemplate jdbc, int headId) {
        Map<String, Object> head = jdbc.queryForMap("SELECT last_id, last_hash FROM act_event_head WHERE id = ? FOR UPDATE", headId);
        return new Object[] {((Number) head.get("last_id")).longValue(), (String) head.get("last_hash")};
    }

    static void setHead(JdbcTemplate jdbc, int headId, long id, String hash) {
        jdbc.update("UPDATE act_event_head SET last_id = ?, last_hash = ? WHERE id = ?", id, hash, headId);
    }

    /** Where verification starts after a cold archive that removed the beginning of a chain: {id of the last removed row, its hash}, or {0, genesis}. */
    static Object[] anchor(JdbcTemplate jdbc, String table) {
        List<Map<String, Object>> a = jdbc.queryForList("SELECT to_id, last_hash FROM act_archive WHERE table_name = ? AND removed = TRUE AND last_hash IS NOT NULL ORDER BY to_id DESC LIMIT 1", table);
        if (a.isEmpty()) return new Object[] {0L, GENESIS};
        return new Object[] {((Number) a.get(0).get("to_id")).longValue(), (String) a.get(0).get("last_hash")};
    }
}
