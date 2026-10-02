package castbridge.server.licenses;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.DeviceIdentity.Factor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a device does with a signed {@link Envelope} (docs/ACTIVATION-FORMAT.md §§ 3, 7): Java port of {@code castbridge.core.owner.ActivationVerifier}, {@code OrderVerifier}
 * and {@code RevocationNotice.verify}, same checks in the same order, so that the server READS exactly what the other tools wrote and that what it signs can be checked
 * on the spot. Pure: no clock of its own (the caller passes the time), no I/O. The server does not install activations; this class is for registry tooling and for the
 * end-to-end tests (an activation issued by the server must be accepted by this verifier AND by the Kotlin core, see tools/activation/server-issued.json).
 */
public final class EnvelopeVerifier {
    public static final long MAX_WINDOW_MS = WireActivation.MAX_WINDOW_HOURS * WireActivation.HOUR_MS;
    public static final long SKEW_MS = WireActivation.DAY_MS;

    /** A public key the device accepts, with its scopes. */
    public record TrustedKey(String kid, byte[] publicKey, Set<SignerScope> scopes) {
        public boolean allows(SignerScope s) { return scopes.contains(s); }
    }

    /** Keys the device trusts plus the revoked key ids. */
    public static final class Ring {
        private final Map<String, TrustedKey> keys = new HashMap<>();
        private final Set<String> revoked = new HashSet<>();

        public Ring add(TrustedKey k) {
            keys.put(k.kid(), k);
            return this;
        }

        public Ring revoke(String kid) {
            revoked.add(kid);
            return this;
        }

        public TrustedKey find(String kid) { return keys.get(kid); }

        public boolean isRevoked(String kid) { return revoked.contains(kid); }
    }

    /** Highest sequence number accepted per key (the anti-replay / anti-rollback memory of a device). */
    public static final class SeqState {
        private final Map<String, Long> last = new HashMap<>();

        public long last(String kid) { return last.getOrDefault(kid, 0L); }

        public void record(String kid, long seq) { if (seq > last(kid)) last.put(kid, seq); }
    }

    /** What the device has been told to forget: keys, and seats with the date ({@code licence|poste} → ms). */
    public record Revocations(Set<String> keys, Map<String, Long> seats) {
        public static Revocations none() { return new Revocations(Set.of(), Map.of()); }

        /** Revoked when the activation was issued at or before the date of the revocation of its seat (a later re-issue is valid again). */
        public boolean seatRevoked(WireActivation.Fields a) {
            Long at = seats.get(a.license() + "|" + a.seat());
            return at != null && a.issuedAt() <= at;
        }

        public Revocations merge(Revocations o) {
            Set<String> k = new TreeSet<>(keys);
            k.addAll(o.keys);
            Map<String, Long> s = new TreeMap<>(seats);
            o.seats.forEach((x, d) -> s.merge(x, d, Math::max));
            return new Revocations(k, s);
        }
    }

    /** The reasons of the format (docs/ACTIVATION-FORMAT.md §§ 3.3, 3.4). */
    public enum Reason { MALFORMED, UNKNOWN_TYPE, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, KEY_NOT_ALLOWED, BAD_RIGHTS, WRONG_SUBJECT, WINDOW_TOO_LONG, WRONG_DEVICE, REVOKED_SEAT,
        STALE_SEQUENCE, NOT_YET_VALID, WINDOW_CLOSED, BAD_ORDER, WRONG_TARGET }

    /** @param reason null = accepted; @param suspect signature good but hardware does not match (the device keeps what it has: never a flat refusal) */
    public record Result(Reason reason, boolean suspect, WireActivation.Fields activation, boolean weakIdentity) {
        public boolean accepted() { return reason == null; }

        /** End of the implicit usage ceiling of an accepted trial activation without a `usage` right (null: none), see {@link WireActivation#implicitUsageEnd}. */
        public Long usageEnd() { return activation == null ? null : WireActivation.implicitUsageEnd(activation); }

        static Result no(Reason r) { return new Result(r, false, null, false); }
    }

    /** An accepted order: the generic checks only (the actions are the policy engine's closed list). */
    public record OrderResult(Reason reason, Envelope envelope, String action, Map<String, String> params) {
        public boolean accepted() { return reason == null; }
    }

    /** What the device knows about itself, to decide whether a targeted message is for it. */
    public record DeviceContext(Map<Factor, String> fingerprints, Set<String> licenses, Set<String> groups) {}

    private final Ring ring;
    private final Revocations revocations;
    private final SeqState seqState;
    private final String expectSubject;

    public EnvelopeVerifier(Ring ring, Revocations revocations, SeqState seqState, String expectSubject) {
        this.ring = ring;
        this.revocations = revocations;
        this.seqState = seqState;
        this.expectSubject = expectSubject;
    }

    private boolean signatureOk(TrustedKey key, Envelope e) {
        try {
            return LicenseKeyring.verify(key.publicKey(), e.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.getDecoder().decode(e.signature()));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** The checks of § 3.3, in order; the first failure is the reason. On acceptance the sequence number is recorded. */
    public Result verifyActivation(String token, Map<Factor, String> device, long nowMs) {
        Envelope env = Envelope.decode(token);
        if (env == null) return Result.no(Reason.MALFORMED);
        if (!env.type().equals(WireActivation.TYPE)) return Result.no(Reason.UNKNOWN_TYPE);
        WireActivation.Fields a = WireActivation.fieldsOf(env);
        if (a == null) return Result.no(Reason.MALFORMED);
        TrustedKey key = ring.find(a.kid());
        if (key == null) return Result.no(Reason.UNKNOWN_KEY);
        if (ring.isRevoked(a.kid()) || revocations.keys().contains(a.kid())) return Result.no(Reason.REVOKED_KEY);
        if (!signatureOk(key, env)) return Result.no(Reason.BAD_SIGNATURE);
        boolean trial = a.kind().equals("trial");
        boolean allowed = trial ? key.allows(SignerScope.ISSUE_TRIAL) : key.allows(SignerScope.ISSUE_PRODUCTION) || key.allows(SignerScope.REACTIVATE);
        if (!allowed) return Result.no(Reason.KEY_NOT_ALLOWED);
        List<String[]> openAll = new ArrayList<>();
        for (String r : a.rights()) if (r.startsWith("openall|")) openAll.add(r.split("\\|", -1));
        if (!openAll.isEmpty() && !key.allows(SignerScope.COMMAND_OPEN_ALL)) return Result.no(Reason.KEY_NOT_ALLOWED);
        if (a.rights().stream().anyMatch(WireActivation::isSuper) && !key.allows(SignerScope.SUPER_UNLIMITED)) return Result.no(Reason.KEY_NOT_ALLOWED);
        if (trial && !a.rights().stream().allMatch(WireActivation::isTrialRight)) return Result.no(Reason.BAD_RIGHTS);
        if (a.rights().stream().anyMatch(r -> WireActivation.isRental(r) && WireActivation.rentalBounds(r) != null)) return Result.no(Reason.BAD_RIGHTS);
        for (String[] r : openAll) {
            long d = Long.parseLong(r[3]) - Long.parseLong(r[2]);
            if (d > WireActivation.MAX_OPEN_ALL_MS || d <= 0) return Result.no(Reason.BAD_RIGHTS);
        }
        if (!a.subject().equals(expectSubject)) return Result.no(Reason.WRONG_SUBJECT);
        if (a.notAfter() - a.notBefore() > MAX_WINDOW_MS) return Result.no(Reason.WINDOW_TOO_LONG);
        if (!DeviceIdentity.matches(a.factors(), a.k(), device)) return new Result(Reason.WRONG_DEVICE, true, null, false);
        if (revocations.seatRevoked(a)) return Result.no(Reason.REVOKED_SEAT);
        if (a.seq() < seqState.last(a.kid())) return Result.no(Reason.STALE_SEQUENCE);
        long now = Math.max(nowMs, a.issuedAt()); // a signed message proves time has reached its issue date
        if (now + SKEW_MS < a.notBefore()) return Result.no(Reason.NOT_YET_VALID);
        if (now > a.notAfter()) return Result.no(Reason.WINDOW_CLOSED);
        seqState.record(a.kid(), a.seq());
        return new Result(null, false, a, device.keySet().stream().noneMatch(f -> f.soldered));
    }

    /** The generic checks of an order (§ 3.4); a refused order does NOT advance the sequence memory. {@code nowMs} = the TV's own clock logic. */
    public OrderResult verifyOrder(String token, DeviceContext device, long nowMs) {
        Envelope env = Envelope.decode(token);
        if (env == null) return new OrderResult(Reason.MALFORMED, null, null, null);
        if (!env.type().equals("order")) return new OrderResult(Reason.UNKNOWN_TYPE, env, null, null);
        TrustedKey key = ring.find(env.kid());
        if (key == null) return new OrderResult(Reason.UNKNOWN_KEY, env, null, null);
        if (ring.isRevoked(env.kid()) || revocations.keys().contains(env.kid())) return new OrderResult(Reason.REVOKED_KEY, env, null, null);
        if (!signatureOk(key, env)) return new OrderResult(Reason.BAD_SIGNATURE, env, null, null);
        if (!key.allows(SignerScope.POLICY)) return new OrderResult(Reason.KEY_NOT_ALLOWED, env, null, null);
        String action;
        Map<String, String> params = new TreeMap<>();
        try {
            List<String> b = env.body();
            action = b.get(0);
            if (!action.startsWith("action=")) throw new IllegalArgumentException();
            action = action.substring(7);
            for (String l : b.subList(1, b.size())) {
                if (!l.startsWith("param=")) throw new IllegalArgumentException();
                String r = l.substring(6);
                int bar = r.indexOf('|');
                params.put(bar < 0 ? r : r.substring(0, bar), bar < 0 ? "" : r.substring(bar + 1));
            }
            if (!action.matches("[a-z][a-z0-9_.-]{0,47}") || params.size() > 16) throw new IllegalArgumentException();
            for (var e : params.entrySet()) if (!e.getKey().matches("[a-z][a-z0-9_.-]{0,31}") || e.getValue().length() > 512 || e.getValue().indexOf('\r') >= 0) throw new IllegalArgumentException();
            List<String> rebuilt = new ArrayList<>(List.of("action=" + action));
            params.forEach((k, v) -> rebuilt.add("param=" + k + "|" + v));
            if (!rebuilt.equals(b)) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            return new OrderResult(Reason.BAD_ORDER, env, null, null);
        }
        boolean forMe = switch (env.target().kind()) {
            case ANY -> true;
            case DEVICE -> DeviceIdentity.matches(env.target().factors(), env.target().k(), device.fingerprints());
            case LICENSE -> device.licenses().contains(env.target().id());
            case GROUP -> device.groups().contains(env.target().id());
        };
        if (!forMe) return new OrderResult(Reason.WRONG_TARGET, env, null, null);
        if (env.seq() <= seqState.last(env.kid())) return new OrderResult(Reason.STALE_SEQUENCE, env, null, null);
        if (nowMs + SKEW_MS < env.notBefore()) return new OrderResult(Reason.NOT_YET_VALID, env, null, null);
        if (nowMs > env.expiresAt()) return new OrderResult(Reason.WINDOW_CLOSED, env, null, null);
        seqState.record(env.kid(), env.seq());
        return new OrderResult(null, env, action, params);
    }

    /** The revocations of a verified list, or null (unknown or revoked key, no REVOKE scope, bad signature, non canonical body, other type). */
    public Revocations verifyRevocation(String token) {
        try {
            Envelope env = Envelope.decode(token);
            if (env == null || !env.type().equals("revocation") || env.target().kind() != Envelope.Target.Kind.ANY) return null;
            TrustedKey key = ring.find(env.kid());
            if (key == null || ring.isRevoked(env.kid()) || !key.allows(SignerScope.REVOKE) || !signatureOk(key, env)) return null;
            Set<String> keys = new TreeSet<>();
            Map<String, Long> seats = new TreeMap<>();
            for (String l : env.body()) {
                if (l.startsWith("key=")) {
                    keys.add(l.substring(4));
                } else if (l.startsWith("seat=")) {
                    String[] p = l.substring(5).split("\\|", -1);
                    if (p.length != 3) return null;
                    seats.put(p[0] + "|" + p[1], Long.parseLong(p[2]));
                } else {
                    return null;
                }
            }
            List<String> rebuilt = new ArrayList<>();
            keys.forEach(k -> rebuilt.add("key=" + k));
            seats.forEach((s, d) -> rebuilt.add("seat=" + s + "|" + d));
            return rebuilt.equals(env.body()) ? new Revocations(keys, seats) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
