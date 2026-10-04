package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Hashing;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/admin/activations/** : the read API of the console (admin bearer token = OWNER, audited as {@code api-token}) and the few writes of the module (journal upload,
 * acknowledge or decide an alert, cold archive). Every read is audited by {@link ReadAuditInterceptor}; every function checks its permission in the service, never here.
 */
@RestController
@RequestMapping(ActivationsModuleConfig.ADMIN_API)
public class ActivationsAdminController {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ActivationsAdminService admin;
    private final ActAccess access;
    private final JournalService journals;
    private final Exporter exporter;
    private final Archiver archiver;
    private final ReadAudit readAudit;
    private final ActClock clock;

    private final Map<String, Boolean> waiting = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPoll = new ConcurrentHashMap<>();

    public ActivationsAdminController(ActivationsAdminService admin, ActAccess access, JournalService journals, Exporter exporter, Archiver archiver, ReadAudit readAudit, ActClock clock) {
        this.admin = admin;
        this.access = access;
        this.journals = journals;
        this.exporter = exporter;
        this.archiver = archiver;
        this.readAudit = readAudit;
        this.clock = clock;
    }

    private Actor actor(Authentication auth) { return access.actorOf(auth); }

    private static Map<String, String> filters(Map<String, String> params) {
        Map<String, String> f = new LinkedHashMap<>(params);
        f.remove("cursor");
        f.remove("limit");
        return f;
    }

    // ------------------------------------------------------------------ lists and fiches

    @GetMapping("/activations")
    public ActivationsAdminService.CursorPage activations(@RequestParam Map<String, String> params, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                                                          Authentication auth, HttpServletRequest req) {
        ActivationsAdminService.CursorPage p = admin.activations(actor(auth), filters(params), cursor, limit);
        return p;
    }

    @GetMapping("/activations/{fp}")
    public Map<String, Object> activation(@PathVariable String fp, Authentication auth, HttpServletRequest req) {
        Map<String, Object> r = admin.activation(actor(auth), fp);
        return r;
    }

    @GetMapping("/tvs")
    public ActivationsAdminService.CursorPage tvs(@RequestParam Map<String, String> params, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                                                  Authentication auth, HttpServletRequest req) {
        ActivationsAdminService.CursorPage p = admin.tvs(actor(auth), filters(params), cursor, limit);
        return p;
    }

    @GetMapping("/tvs/{deviceCode}")
    public Map<String, Object> tv(@PathVariable String deviceCode, Authentication auth, HttpServletRequest req) {
        Map<String, Object> r = admin.tv(actor(auth), deviceCode);
        @SuppressWarnings("unchecked")
        Map<String, Object> tv = (Map<String, Object>) r.get("tv");
        return r;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestParam(required = false) String day, Authentication auth, HttpServletRequest req) {
        Map<String, Object> r = admin.dashboard(actor(auth), day);
        return r;
    }

    @GetMapping("/alerts")
    public ActivationsAdminService.CursorPage alerts(@RequestParam Map<String, String> params, @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                                                     Authentication auth, HttpServletRequest req) {
        ActivationsAdminService.CursorPage p = admin.alerts(actor(auth), filters(params), cursor, limit);
        return p;
    }

    @GetMapping("/tools")
    public java.util.List<Map<String, Object>> tools(Authentication auth, HttpServletRequest req) {
        java.util.List<Map<String, Object>> r = admin.tools(actor(auth));
        return r;
    }

    @GetMapping("/integrity")
    public Map<String, Object> integrity(Authentication auth, HttpServletRequest req) {
        Map<String, Object> r = admin.integrity(actor(auth));
        return r;
    }

    @GetMapping("/checkpoints")
    public Map<String, Object> checkpoints(@RequestParam(required = false) String from, @RequestParam(required = false) String to, Authentication auth, HttpServletRequest req) {
        Map<String, Object> r = admin.checkpoints(actor(auth), from, to);
        return r;
    }

    @GetMapping("/read-audit")
    public ActivationsAdminService.CursorPage readAudit(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit, Authentication auth, HttpServletRequest req) {
        ActivationsAdminService.CursorPage p = admin.readAudit(actor(auth), cursor, limit);
        return p;
    }

    // ------------------------------------------------------------------ export

    /** Writes the headers only when the first byte goes out: a refusal before that (400, 403, 429) is a normal JSON error. */
    private static final class LazyOut extends OutputStream {
        private final HttpServletResponse res;
        private final String type, name;
        private OutputStream out;

        LazyOut(HttpServletResponse res, String type, String name) {
            this.res = res;
            this.type = type;
            this.name = name;
        }

        private OutputStream o() throws IOException {
            if (out == null) {
                res.setContentType(type);
                res.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
                out = res.getOutputStream();
            }
            return out;
        }

        @Override
        public void write(int b) throws IOException { o().write(b); }

        @Override
        public void write(byte[] b, int off, int len) throws IOException { o().write(b, off, len); }

        @Override
        public void flush() throws IOException { if (out != null) out.flush(); }
    }

    @GetMapping("/export")
    public void export(@RequestParam Map<String, String> params, Authentication auth, HttpServletRequest req, HttpServletResponse res) throws IOException {
        Map<String, String> f = filters(params);
        String what = f.remove("what"), format = f.remove("format");
        String ext = "csv".equals(format) ? "csv" : "jsonl";
        int rows = exporter.export(actor(auth), what, format, f, new LazyOut(res, Exporter.contentType(ext), (what == null ? "export" : what) + "." + ext));
        if (!res.isCommitted() && res.getContentType() == null) {
            // an empty JSONL export is still a file, never an error page
            res.setContentType(Exporter.contentType(ext));
            res.setHeader("Content-Disposition", "attachment; filename=\"" + what + "." + ext + "\"");
        }
    }

    // ------------------------------------------------------------------ long poll

    /**
     * Events after an id (at most 200). Long poll, up to 25 s: it crosses nginx and mobile networks without any buffering set-up and resumes without state (the client keeps
     * {@code after}). One poll at a time per session, 20 in all (so at most 20 request threads wait). Audited ONCE per tracking session (a new session after a minute of
     * silence), not once per turn.
     *
     * <p>The wait blocks the request thread on purpose: a {@code DeferredResult} is re-dispatched through the security chain, whose bearer filter (a OncePerRequestFilter in
     * SecurityConfig, not owned by this module) does not authenticate an asynchronous dispatch, so the answer would be a 401.
     */
    @GetMapping("/changes")
    public ResponseEntity<Map<String, Object>> changes(@RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "0") int wait, Authentication auth, HttpServletRequest req)
            throws InterruptedException {
        Actor actor = actor(auth);
        Map<String, Object> answer = admin.changes(actor, after);   // the permission check, and the answer if something is already there
        String session = Hashing.sha256Hex(actor.name() + "|" + String.valueOf(req.getHeader("Authorization")) + "|" + String.valueOf(req.getRequestedSessionId())).substring(0, 16);
        long now = clock.nowMs();
        Long last = lastPoll.put(session, now);
        if (lastPoll.size() > 5000) lastPoll.values().removeIf(t -> t < now - 600_000);
        if (last == null || now - last > 60_000) {
            readAudit.recordRead(actor, ReadAuditInterceptor.CHANGES, Map.of("after", Long.toString(after)), null, 0, false);   // before the answer, and fail closed
        }
        int w = Math.max(0, Math.min(wait, 25));
        if (w > 0 && ((java.util.List<?>) answer.get("events")).isEmpty()) {
            if (waiting.size() >= 20 || waiting.putIfAbsent(session, Boolean.TRUE) != null) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'interrogations longues : une par session, vingt en tout");
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(w).toNanos();
                while (System.nanoTime() < deadline && !admin.hasChangesSince(after)) Thread.sleep(500);
                answer = admin.changesSince(after);
            } finally {
                waiting.remove(session);
            }
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(answer);
    }

    // ------------------------------------------------------------------ writes

    @PostMapping(path = "/journal")
    public ResponseEntity<Map<String, Object>> journal(@RequestBody(required = false) String body, Authentication auth) {
        Actor actor = actor(auth);
        String token = body == null ? "" : body.trim();
        if (token.startsWith("{")) {
            try {
                JsonNode n = JSON.readTree(token);
                token = n.path("envelope").asText("");
            } catch (IOException e) {
                throw ApiException.badRequest("Corps invalide : l'enveloppe cbx1 du journal, ou {\"envelope\":\"…\"}");
            }
        }
        JournalService.Result r = journals.upload(actor, token, "api".equals(actor.channel()) ? "api" : actor.channel().equals("phone") ? "phone" : "web");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", r.status().name());
        if (r.reason() != null) out.put("reason", r.reason().name());
        out.put("accepted", r.accepted());
        out.put("duplicate", r.duplicate());
        out.put("rejected", r.rejected());
        if (r.gap() != null) out.put("gap", Map.of("from", r.gap().from(), "to", r.gap().to()));
        if (r.batchSha256() != null) out.put("batch", r.batchSha256());
        if (r.kid() != null) out.put("kid", r.kid());
        return ResponseEntity.status(JournalService.httpStatus(r)).contentType(MediaType.APPLICATION_JSON).body(out);
    }

    @PostMapping("/alerts/{id}/ack")
    public Map<String, Object> ack(@PathVariable long id, Authentication auth) {
        admin.ackAlert(actor(auth), id);
        return admin.alert(id);
    }

    @PostMapping("/alerts/{id}/close")
    public Map<String, Object> close(@PathVariable long id, @RequestBody(required = false) Map<String, String> body, Authentication auth) {
        admin.closeAlert(actor(auth), id, body == null ? null : body.get("reason"));
        return admin.alert(id);
    }

    @PostMapping("/archive")
    public Map<String, Object> archive(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
        boolean remove = body != null && Boolean.TRUE.equals(body.get("remove"));
        Archiver.Result r = archiver.archive(actor(auth), reason, remove);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", r.rowsByTable());
        out.put("files", r.files());
        out.put("removed", r.removed());
        out.put("directory", archiver.directory());
        return out;
    }
}
