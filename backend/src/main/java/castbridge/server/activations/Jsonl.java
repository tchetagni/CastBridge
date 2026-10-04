package castbridge.server.activations;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/** The exact shape of a line of the chains in the exports and the cold archive: everything the hash covers, so that the offline verifier can recompute it. */
final class Jsonl {
    private Jsonl() {}

    static Map<String, Object> row(String table, Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (table.equals("act_event")) {
            m.put("id", ((Number) r.get("id")).longValue());
            m.put("atMs", ((Timestamp) r.get("at")).getTime());
            m.put("recordedMs", ((Timestamp) r.get("recorded_at")).getTime());
            m.put("type", r.get("type"));
            m.put("fp", r.get("fp"));
            m.put("tvRef", r.get("tv_ref"));
            m.put("licenseId", r.get("license_id"));
            m.put("kid", r.get("kid"));
            m.put("actorType", r.get("actor_type"));
            m.put("actor", r.get("actor"));
            m.put("source", r.get("source"));
            m.put("before", r.get("before_json"));
            m.put("after", r.get("after_json"));
            m.put("idemKey", r.get("idem_key"));
        } else {
            m.put("id", ((Number) r.get("id")).longValue());
            m.put("atMs", ((Timestamp) r.get("at")).getTime());
            m.put("actor", r.get("actor"));
            m.put("role", r.get("role"));
            m.put("channel", r.get("channel"));
            m.put("route", r.get("route"));
            m.put("params", r.get("params"));
            m.put("target", r.get("target"));
            m.put("rows", ((Number) r.get("rows_rendered")).intValue());
            m.put("export", r.get("export"));
        }
        m.put("prevHash", r.get("prev_hash"));
        m.put("hash", r.get("hash"));
        return m;
    }
}
