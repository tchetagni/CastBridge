package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * CSV (RFC 4180: comma, CRLF, doubled quotes; a cell that a spreadsheet would read as a formula is prefixed with an apostrophe) and JSONL exports of the activations, the TVs, the
 * alerts, the history and the audit of the reads. Needs ACT_EXPORT (second factor), 10 per hour and per actor, refuses (does not truncate) more rows than the limit, never
 * writes a whole device code (the TVs are exported by reference and by the masked end of their code), and is itself audited as an export.
 */
@Service
public class Exporter {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Set<String> WHAT = Set.of("activations", "tvs", "events", "alerts", "reads");

    /** Header -> key of the row, in order. */
    private static final Map<String, List<String[]>> COLUMNS = Map.of(
            "activations", cols("fp", "tag", "form", "kind", "subject", "license", "seat", "tv_ref:tvRef", "kid", "tool", "state", "flags:flagsRaw", "issued_at:issuedAt", "expires_at:windowClosesAt",
                    "usage_to:usageTo", "unlimited", "first_seen_tv_at:firstSeenTvAt", "last_seen_tv_at:lastSeenTvAt"),
            "tvs", cols("tv_ref:tvRef", "code_masked:codeMasked", "edition", "usage_to:usageTo", "open_all_until:openAllUntil", "unlock_until:unlockUntil", "trial_resets:trialResets",
                    "last_report_at:lastReportAt", "last_report_via:lastReportVia", "last_bt_at:lastBtAt", "app_code:appCode", "app_name:appName", "reco", "alerts_open:alertsOpen"),
            "alerts", cols("id", "type", "severity", "fp", "tv_ref:tvRef", "kid", "license", "detail", "opened_at:openedAt", "last_seen_at:lastSeenAt", "hits", "state", "decided_by:decidedBy",
                    "decided_at:decidedAt", "reason"),
            "events", cols("id", "at_ms:atMs", "recorded_ms:recordedMs", "type", "fp", "tv_ref:tvRef", "license_id:licenseId", "kid", "actor_type:actorType", "actor", "source", "before", "after",
                    "idem_key:idemKey", "prev_hash:prevHash", "hash"),
            "reads", cols("id", "at_ms:atMs", "actor", "role", "channel", "route", "params", "target", "rows", "export", "prev_hash:prevHash", "hash"));

    private static List<String[]> cols(String... names) {
        return java.util.Arrays.stream(names).map(n -> n.contains(":") ? n.split(":") : new String[] {n, n}).toList();
    }

    private final ActivationsAdminService admin;
    private final ActAccess access;
    private final ActivationsPolicy policy;
    private final ActivationsProperties props;

    public Exporter(ActivationsAdminService admin, ActAccess access, ActivationsPolicy policy, ActivationsProperties props) {
        this.admin = admin;
        this.access = access;
        this.policy = policy;
        this.props = props;
    }

    public static String contentType(String format) { return format.equals("csv") ? "text/csv;charset=UTF-8" : "application/x-ndjson;charset=UTF-8"; }

    /** @return the number of rows written */
    public int export(Actor actor, String what, String format, Map<String, String> filters, OutputStream out) throws IOException {
        access.require(actor, ActPermissions.Perm.ACT_EXPORT);
        if (what == null || !WHAT.contains(what)) throw ApiException.badRequest("what : activations, tvs, events, alerts ou reads");
        if (format == null || !(format.equals("csv") || format.equals("jsonl"))) throw ApiException.badRequest("format : csv ou jsonl");
        policy.limit("export:" + actor.name(), 10, Duration.ofHours(1), "exports");
        long bound = admin.exportBound(what, filters);
        if (bound > props.exportMaxRows()) {
            throw ApiException.badRequest("Trop de lignes pour un seul export (" + bound + ", maximum " + props.exportMaxRows() + ") : affinez les filtres (période, état, outil)");
        }
        BufferedOutputStream o = new BufferedOutputStream(out);
        int[] n = {0};
        List<String[]> columns = COLUMNS.get(what);
        try {
            if (format.equals("csv")) {
                o.write(line(columns.stream().map(c -> c[0]).toList()).getBytes(StandardCharsets.UTF_8));
            }
            admin.streamRows(what, filters, row -> {
                try {
                    if (format.equals("csv")) {
                        List<Object> cells = columns.stream().map(c -> row.get(c[1])).toList();
                        o.write(line(cells).getBytes(StandardCharsets.UTF_8));
                    } else {
                        o.write((JSON.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    n[0]++;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            o.flush();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return n[0];
    }

    private static String line(List<?> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    /** RFC 4180 quoting, and the apostrophe in front of a text that starts like a formula (= + - @ tab CR). */
    static String cell(Object v) {
        if (v == null) return "";
        String s = v instanceof Instant i ? i.toString() : v.toString();
        if (!(v instanceof Number) && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\r") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
