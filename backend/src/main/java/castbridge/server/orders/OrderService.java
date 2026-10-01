package castbridge.server.orders;

import castbridge.server.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server's queue of deferred orders (docs/ORDRES.md § Serveur): targeted, signed (envelope cbx1, type `order`, key scope `policy`), handed to the phone that carries them to the TV,
 * acknowledged by the TV. States of one order for one TV: PENDING (created, known TV) → HANDED (given to a phone) → APPLIED | REFUSED (the TV's acknowledgement) ; EXPIRED is deduced
 * from the date. Everything created, released, handed and acknowledged goes into a hash-chained audit journal. The server NEVER signs an action outside [PolicyCatalog].
 * Wire format of the acknowledgement = castbridge.core.policy.OrderAck.toText (technical fields only).
 */
@Service
public class OrderService {
    public static final Pattern TV_CODE = Pattern.compile("^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$");
    private static final Pattern KID = Pattern.compile("^[0-9a-f]{16}$");
    private static final Pattern REF = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final long MAX_HOURS = 366L * 24;
    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final OrderSigner signer;
    private final ObjectMapper json;
    private final boolean enabled;
    private final java.time.Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Object auditLock = new Object();

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(JdbcTemplate jdbc, OrderSigner signer, ObjectMapper json, @Value("${castbridge.orders.enabled:false}") boolean enabled) {
        this(jdbc, signer, json, enabled, java.time.Clock.systemUTC());
    }

    public OrderService(JdbcTemplate jdbc, OrderSigner signer, ObjectMapper json, boolean enabled, java.time.Clock clock) {
        this.jdbc = jdbc; this.signer = signer; this.json = json; this.enabled = enabled; this.clock = clock;
    }

    /** Feature switch: off by default (docs/ORDRES.md § Déploiement). Off = device routes answer 404 and nothing is created or signed. */
    public boolean on() { return enabled && signer.enabled(); }
    public void requireOn() { if (!on()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'ordres différés est désactivé (CASTBRIDGE_ORDERS_ENABLED et clé de signature)"); }

    private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private static Timestamp ts(Instant i) { return i == null ? null : Timestamp.from(i); }

    // ---------------------------------------------------------------- creation / release

    public record NewOrder(String targetKind, String targetValue, String action, Map<String, String> params, int priority, Integer expiresInHours, boolean hold) {}

    @Transactional
    public long create(NewOrder o, String actor) {
        requireOn();
        Map<String, String> params = o.params() == null ? Map.of() : new TreeMap<>(o.params());
        String why = PolicyCatalog.check(o.action(), params, now().toEpochMilli());
        if (why != null) throw ApiException.badRequest(why);
        String kind = o.targetKind() == null ? "" : o.targetKind();
        String value = o.targetValue();
        switch (kind) {
            case "any" -> value = null;
            case "device" -> {
                if (value == null || !TV_CODE.matcher(value).matches()) throw ApiException.badRequest("Code d'appareil invalide");
                if (jdbc.queryForObject("select count(*) from order_tv where tv_code = ?", Integer.class, value) == 0)
                    throw ApiException.badRequest("TV inconnue du serveur : un téléphone appairé doit d'abord envoyer sa demande d'appareil");
            }
            case "license", "group" -> { if (value == null || !REF.matcher(value).matches()) throw ApiException.badRequest("Identifiant de cible invalide"); }
            default -> throw ApiException.badRequest("Cible : any, device, license ou group");
        }
        long hours = o.expiresInHours() == null ? 24 * 30 : o.expiresInHours();
        if (hours < 1 || hours > MAX_HOURS) throw ApiException.badRequest("Validité : de 1 heure à 366 jours");
        Instant n = now();
        String paramsJson;
        try { paramsJson = json.writeValueAsString(params); } catch (JsonProcessingException e) { throw ApiException.badRequest("Paramètres illisibles"); }
        jdbc.update("insert into order_msg (state, target_kind, target_value, action, params, priority, created_at, created_by, expires_at) values ('QUEUED',?,?,?,?,?,?,?,?)",
                kind, value, o.action(), paramsJson, o.priority(), ts(n), actor, ts(n.plusSeconds(hours * 3600)));
        long id = jdbc.queryForObject("select max(id) from order_msg", Long.class);
        audit("ORDER_CREATED", id, null, actor, o.action() + " → " + kind + (value == null ? "" : ":" + value));
        if (!o.hold()) release(actor);
        return id;
    }

    /**
     * Signs every held order, highest priority first then oldest, giving each the next sequence number of the key (the TV demands STRICTLY increasing numbers per key: the order of
     * signing IS the order of application). The sequence counter row is locked for the whole release, so two releases never hand out the same number.
     */
    @Transactional
    public int release(String actor) {
        requireOn();
        List<Map<String, Object>> queued = jdbc.queryForList("select * from order_msg where state = 'QUEUED' order by priority desc, id asc");
        if (queued.isEmpty()) return 0;
        String kid = signer.keyId();
        try { jdbc.update("insert into order_key_seq (kid, last_seq) select ?, 0 where not exists (select 1 from order_key_seq where kid = ?)", kid, kid); }
        catch (org.springframework.dao.DuplicateKeyException e) { /* another release created the row first: it is locked below */ }
        long seq = jdbc.queryForObject("select last_seq from order_key_seq where kid = ? for update", Long.class, kid);
        for (Map<String, Object> q : queued) {
            seq++;
            long id = ((Number) q.get("id")).longValue();
            Instant issued = now();
            Instant expires = ((Timestamp) q.get("expires_at")).toInstant();
            Map<String, String> params = readParams((String) q.get("params"));
            OrderEnvelope.Target t = target((String) q.get("target_kind"), (String) q.get("target_value"));
            String nonce = HexFormat.of().formatHex(randomBytes(8));
            String payload = OrderEnvelope.payload(kid, seq, nonce, issued.toEpochMilli(), issued.toEpochMilli(), expires.toEpochMilli(), t, (String) q.get("action"), params);
            String token = OrderEnvelope.token(payload, signer.sign(payload.getBytes(StandardCharsets.UTF_8)));
            jdbc.update("update order_msg set state='RELEASED', kid=?, seq=?, nonce=?, token=?, released_at=? where id=? and state='QUEUED'", kid, seq, nonce, token, ts(issued), id);
            audit("ORDER_RELEASED", id, null, actor, "seq " + seq + ", clé " + kid);
        }
        jdbc.update("update order_key_seq set last_seq = ? where kid = ?", seq, kid);
        return queued.size();
    }

    private OrderEnvelope.Target target(String kind, String value) {
        return switch (kind) {
            case "any" -> OrderEnvelope.Target.any();
            case "license" -> OrderEnvelope.Target.license(value);
            case "group" -> OrderEnvelope.Target.group(value);
            default -> {
                Map<String, Object> tv = jdbc.queryForMap("select k, factors from order_tv where tv_code = ?", value);
                Map<String, String> f = new LinkedHashMap<>();
                for (String l : ((String) tv.get("factors")).split("\n")) { String[] p = l.split("\\|"); f.put(p[0], p[1]); }
                yield OrderEnvelope.Target.device(((Number) tv.get("k")).intValue(), f);
            }
        };
    }

    /** A held order is dropped; a released one stops being handed out (what a TV already received cannot be recalled: send a contrary order). */
    @Transactional
    public void cancel(long id, String actor) {
        int n = jdbc.update("update order_msg set state='CANCELLED' where id=? and state in ('QUEUED','RELEASED')", id);
        if (n == 0) throw ApiException.notFound("Ordre introuvable ou déjà annulé");
        audit("ORDER_CANCELLED", id, null, actor, "annulé");
    }

    // ---------------------------------------------------------------- phone side

    /** A phone registers a TV it is paired with: the TV's "demande d'appareil" (code, k, factors), checked against its own code. */
    @Transactional
    public void pair(String phoneId, String deviceInfo) {
        requireOn();
        String code = null; Integer k = null; Map<String, String> f = new TreeMap<>(Comparator.comparingInt(OrderEnvelope.FACTOR_ORDER::indexOf));
        try {
            for (String l : deviceInfo.split("\n")) {
                if (l.startsWith("code=")) code = l.substring(5);
                else if (l.startsWith("k=")) k = Integer.parseInt(l.substring(2));
                else if (l.startsWith("factor=")) { String[] p = l.substring(7).split("\\|"); if (!OrderEnvelope.FACTOR_ORDER.contains(p[0]) || !p[1].matches("^[0-9a-f]{32}$")) throw new IllegalArgumentException(); f.put(p[0], p[1]); }
                else throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) { throw ApiException.badRequest("Demande d'appareil illisible"); }
        int n = f.size();
        if (code == null || k == null || n == 0 || n > 5 || k != (n >= 3 ? n - 1 : Math.max(n, 1)) || !code.equals(DeviceCodes.of(f))) throw ApiException.badRequest("Demande d'appareil incohérente (le code ne correspond pas aux facteurs)");
        StringBuilder fs = new StringBuilder();
        f.forEach((a, b) -> fs.append(fs.length() == 0 ? "" : "\n").append(a).append('|').append(b));
        Instant n0 = now();
        jdbc.update("insert into order_tv (tv_code, k, factors, registered_at) select ?,?,?,? where not exists (select 1 from order_tv where tv_code = ?)", code, k, fs.toString(), ts(n0), code);
        int added = jdbc.update("insert into order_pairing (phone_public_id, tv_code, created_at) select ?,?,? where not exists (select 1 from order_pairing where phone_public_id=? and tv_code=?)", phoneId, code, ts(n0), phoneId, code);
        if (added > 0) audit("TV_PAIRED", null, code, "phone:" + phoneId.substring(0, 8), "TV appairée à un téléphone");
    }

    public record Outgoing(long id, String tv, String token) {}
    public record Fetched(long cursor, List<Outgoing> orders) {}

    /** GET /api/v1/orders?since=: the orders of the TVs paired with THIS phone, never any other. */
    @Transactional
    public Fetched fetch(String phoneId, long since) {
        requireOn();
        List<String> tvs = jdbc.queryForList("select tv_code from order_pairing where phone_public_id = ?", String.class, phoneId);
        long maxReleased = jdbc.queryForObject("select coalesce(max(id),0) from order_msg where state='RELEASED'", Long.class);
        long minQueued = jdbc.queryForObject("select coalesce(min(id),0) from order_msg where state='QUEUED'", Long.class);
        long cursor = minQueued > 0 ? Math.min(maxReleased, minQueued - 1) : maxReleased;     // a held order released later must not be skipped
        cursor = Math.max(cursor, since);
        List<Outgoing> out = new ArrayList<>();
        Instant n = now();
        for (String tv : tvs) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select m.id, m.token, m.target_kind, m.target_value, m.kid, m.seq from order_msg m
                    where m.state = 'RELEASED' and m.expires_at > ? and (m.id > ?
                       or exists (select 1 from order_delivery d where d.order_id = m.id and d.tv_code = ? and d.state = 'HANDED' and d.handed_at < ?))
                      and not exists (select 1 from order_delivery d where d.order_id = m.id and d.tv_code = ? and d.state in ('APPLIED','REFUSED'))
                    order by m.kid, m.seq""", ts(n), since, tv, ts(n.minusSeconds(3600)), tv);
            for (Map<String, Object> r : rows) {
                if (!targets((String) r.get("target_kind"), (String) r.get("target_value"), tv)) continue;
                long id = ((Number) r.get("id")).longValue();
                int up = jdbc.update("update order_delivery set handed_at=?, handed_to=? where order_id=? and tv_code=? and state='HANDED'", ts(n), phoneId, id, tv);
                if (up == 0 && jdbc.update("insert into order_delivery (order_id, tv_code, state, handed_at, handed_to) select ?,?,'HANDED',?,? where not exists (select 1 from order_delivery where order_id=? and tv_code=?)", id, tv, ts(n), phoneId, id, tv) > 0)
                    audit("ORDER_HANDED", id, tv, "phone:" + phoneId.substring(0, 8), "remis au téléphone");
                out.add(new Outgoing(id, tv, (String) r.get("token")));
            }
        }
        return new Fetched(cursor, out);
    }

    private boolean targets(String kind, String value, String tv) {
        return switch (kind) {
            case "any" -> true;
            case "device" -> tv.equals(value);
            default -> jdbc.queryForObject("select count(*) from order_membership where kind=? and ref_id=? and tv_code=?", Integer.class, kind, value, tv) > 0;
        };
    }

    public record Ack(String tv, String ack) {}

    /** POST /api/v1/orders/acks: technical acknowledgements from the TVs paired with THIS phone. Idempotent; the first answer for an (order, TV) wins. Returns how many were taken in. */
    @Transactional
    public int acks(String phoneId, List<Ack> acks) {
        requireOn();
        if (acks == null || acks.size() > 200) throw ApiException.badRequest("Au plus 200 accusés à la fois");
        int taken = 0;
        for (Ack a : acks) {
            if (a == null || a.tv() == null || a.ack() == null || a.ack().length() > 400) continue;
            Map<String, String> f = new LinkedHashMap<>();
            for (String l : a.ack().split("\n")) { int i = l.indexOf('='); if (i > 0) f.put(l.substring(0, i), l.substring(i + 1)); }
            String kid = f.get("kid"); String reason = f.get("reason"); String result = f.get("result");
            long seq, version;
            try { seq = Long.parseLong(f.get("seq")); version = Long.parseLong(f.get("policyVersion")); } catch (RuntimeException e) { continue; }
            if (kid == null || !KID.matcher(kid).matches() || reason == null || !reason.matches("^[A-Z_]{1,24}$") || !("applied".equals(result) || "refused".equals(result))) continue;
            if (jdbc.queryForObject("select count(*) from order_pairing where phone_public_id=? and tv_code=?", Integer.class, phoneId, a.tv()) == 0) continue;
            List<Long> ids = jdbc.queryForList("select id from order_msg where kid=? and seq=?", Long.class, kid, seq);
            if (ids.isEmpty()) continue;
            String state = "applied".equals(result) ? "APPLIED" : "REFUSED";
            int up = jdbc.update("update order_delivery set state=?, acked_at=?, reason=?, policy_version=? where order_id=? and tv_code=? and state='HANDED'", state, ts(now()), reason, version, ids.get(0), a.tv());
            if (up > 0) { taken++; audit("ORDER_ACKED", ids.get(0), a.tv(), "phone:" + phoneId.substring(0, 8), state + " " + reason); }
            else if (jdbc.queryForObject("select count(*) from order_delivery where order_id=? and tv_code=? and state in ('APPLIED','REFUSED')", Integer.class, ids.get(0), a.tv()) > 0) taken++;   // already known: idempotent
        }
        return taken;
    }

    // ---------------------------------------------------------------- administration

    @Transactional
    public void setMembership(String kind, String ref, String tv, boolean member, String actor) {
        if (!Set.of("license", "group").contains(kind) || !REF.matcher(ref).matches() || !TV_CODE.matcher(tv).matches()) throw ApiException.badRequest("Appartenance invalide");
        if (member) jdbc.update("insert into order_membership (kind, ref_id, tv_code) select ?,?,? where not exists (select 1 from order_membership where kind=? and ref_id=? and tv_code=?)", kind, ref, tv, kind, ref, tv);
        else jdbc.update("delete from order_membership where kind=? and ref_id=? and tv_code=?", kind, ref, tv);
        audit("MEMBERSHIP", null, tv, actor, (member ? "+ " : "- ") + kind + ":" + ref);
    }

    public record DeliveryView(String tv, String state, String reason, Long policyVersion, Instant handedAt, Instant ackedAt) {}
    public record OrderView(long id, String state, String action, Map<String, String> params, String targetKind, String targetValue, int priority, Long seq, String kid, Instant createdAt, Instant expiresAt, String createdBy, List<DeliveryView> deliveries) {}

    public List<OrderView> list(int limit, int offset) {
        return views("select * from order_msg order by id desc limit ? offset ?", Math.max(1, Math.min(limit, 200)), Math.max(0, offset));
    }

    public OrderView get(long id) {
        List<OrderView> v = views("select * from order_msg where id = ?", id);
        if (v.isEmpty()) throw ApiException.notFound("Ordre introuvable");
        return v.get(0);
    }

    private List<OrderView> views(String sql, Object... args) {
        Instant n = now();
        List<OrderView> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(sql, args)) {
            long id = ((Number) r.get("id")).longValue();
            Instant exp = ((Timestamp) r.get("expires_at")).toInstant();
            List<DeliveryView> ds = new ArrayList<>();
            for (Map<String, Object> d : jdbc.queryForList("select * from order_delivery where order_id = ? order by tv_code", id)) {
                String st = (String) d.get("state");
                if (st.equals("HANDED") && exp.isBefore(n)) st = "EXPIRED";
                ds.add(new DeliveryView((String) d.get("tv_code"), st, (String) d.get("reason"), d.get("policy_version") == null ? null : ((Number) d.get("policy_version")).longValue(),
                        d.get("handed_at") == null ? null : ((Timestamp) d.get("handed_at")).toInstant(), d.get("acked_at") == null ? null : ((Timestamp) d.get("acked_at")).toInstant()));
            }
            String state = (String) r.get("state");
            if (state.equals("RELEASED") && exp.isBefore(n) && ds.stream().noneMatch(x -> x.state().equals("APPLIED") || x.state().equals("REFUSED"))) state = "EXPIRED";
            out.add(new OrderView(id, state, (String) r.get("action"), readParams((String) r.get("params")), (String) r.get("target_kind"), (String) r.get("target_value"), ((Number) r.get("priority")).intValue(),
                    r.get("seq") == null ? null : ((Number) r.get("seq")).longValue(), (String) r.get("kid"), ((Timestamp) r.get("created_at")).toInstant(), exp, (String) r.get("created_by"), ds));
        }
        return out;
    }

    // ---------------------------------------------------------------- audit (hash chain)

    private static final String GENESIS = "0".repeat(64);

    private void audit(String event, Long orderId, String tv, String actor, String detail) {
        synchronized (auditLock) {
            List<String> last = jdbc.queryForList("select hash from order_audit order by id desc limit 1", String.class);
            String prev = last.isEmpty() ? GENESIS : last.get(0);
            Instant at = now();
            String d = detail.length() > 255 ? detail.substring(0, 255) : detail;
            String hash = chain(prev, at, event, orderId, tv, actor, d);
            jdbc.update("insert into order_audit (at, event, order_id, tv_code, actor, detail, prev_hash, hash) values (?,?,?,?,?,?,?,?)", ts(at), event, orderId, tv, actor, d, prev, hash);
        }
    }

    static String chain(String prev, Instant at, String event, Long orderId, String tv, String actor, String detail) {
        String text = String.join("|", prev, Long.toString(at.toEpochMilli() * 1000 + at.getNano() / 1000 % 1000), event, orderId == null ? "" : orderId.toString(), tv == null ? "" : tv, actor, detail);
        return HexFormat.of().formatHex(DeviceCodes.sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Id of the first audit line that does not match its hash or its predecessor, or -1 when the whole chain is intact. */
    public long verifyAudit() {
        String prev = GENESIS;
        for (Map<String, Object> r : jdbc.queryForList("select * from order_audit order by id")) {
            Instant at = ((Timestamp) r.get("at")).toInstant();
            String expect = chain(prev, at, (String) r.get("event"), r.get("order_id") == null ? null : ((Number) r.get("order_id")).longValue(), (String) r.get("tv_code"), (String) r.get("actor"), (String) r.get("detail"));
            if (!prev.equals(r.get("prev_hash")) || !expect.equals(r.get("hash"))) return ((Number) r.get("id")).longValue();
            prev = (String) r.get("hash");
        }
        return -1;
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, String> readParams(String text) {
        try { return json.readValue(text, STRING_MAP); } catch (JsonProcessingException e) { throw new IllegalStateException("paramètres illisibles"); }
    }

    private byte[] randomBytes(int n) { byte[] b = new byte[n]; random.nextBytes(b); return b; }
}
