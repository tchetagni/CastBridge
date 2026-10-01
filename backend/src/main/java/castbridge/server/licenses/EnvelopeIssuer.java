package castbridge.server.licenses;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The other signed messages of the {@code cbx1} envelope (docs/ACTIVATION-FORMAT.md §§ 3.4, 5, 7): owner commands, deferred orders and revocation lists, built and signed
 * with ONE key whose scopes are enforced here, in code. Java port of {@code ActivationIssuer.issueCommand}, {@code Orders.issue} and {@code RevocationNotice.issue}: same inputs,
 * same bytes (tools/activation/test-vectors.json). The server key holds {@code REVOKE} and {@code POLICY} but never a {@code COMMAND_*} scope.
 */
public final class EnvelopeIssuer {
    public static final long DAY_MS = 86_400_000L;
    private static final Pattern CHALLENGE = Pattern.compile("[0-9a-f]{16,64}");
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_.-]{0,31}");
    private static final Pattern ACTION = Pattern.compile("[a-z][a-z0-9_.-]{0,47}");
    private static final Set<String> SUPPORT_ACTIONS = Set.of("diagnostic", "reset-trial");

    /** The three powers of an owner command; each needs its own scope. */
    public enum Power {
        SUPPORT(SignerScope.COMMAND_SUPPORT, 0), UNLOCK(SignerScope.COMMAND_UNLOCK, 30), OPEN_ALL(SignerScope.COMMAND_OPEN_ALL, 30);

        final SignerScope scope;
        final int maxDays;

        Power(SignerScope scope, int maxDays) {
            this.scope = scope;
            this.maxDays = maxDays;
        }

        public String wire() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private final LicenseKeyring keyring;
    private final Set<SignerScope> scopes;

    public EnvelopeIssuer(LicenseKeyring keyring, Set<SignerScope> scopes) {
        this.keyring = keyring;
        this.scopes = Set.copyOf(scopes);
    }

    /** The issuer of the server key: its scopes are fixed in code ({@link ScopedActivationSigner#SERVER_SCOPES}). */
    public static EnvelopeIssuer server(LicenseKeyring keyring) { return new EnvelopeIssuer(keyring, ScopedActivationSigner.SERVER_SCOPES); }

    public Set<SignerScope> scopes() { return scopes; }

    private void need(SignerScope s) {
        if (!scopes.contains(s)) throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Cette clé n'a pas la portée " + s);
    }

    private Envelope sign(Envelope unsigned) {
        if (!keyring.present()) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Aucune clé de signature serveur : déposez-la dans le dossier des secrets (voir docs/LICENSE-ADMIN.md)");
        return unsigned.withSignature(Base64.getEncoder().encodeToString(keyring.sign(unsigned.payload().getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * An owner command for ONE device; {@code challenge} is the one the device issued a moment ago (it IS the envelope nonce). {@code lots} are keys {@code feature:scope}.
     * {@code seq} null = {@code issuedAt}.
     */
    public String command(Power power, DeviceIdentity.Request device, String challenge, long issuedAt, int days, String action, List<String> bundleIds, List<String> lots, Long seq) {
        need(power.scope);
        if (!CHALLENGE.matcher(challenge).matches()) throw ApiException.badRequest("Défi invalide");
        if (device.factors().isEmpty()) throw ApiException.badRequest("Aucun facteur d'identité");
        switch (power) {
            case SUPPORT -> {
                if (!SUPPORT_ACTIONS.contains(action) || days != 0) throw ApiException.badRequest("Action de support invalide");
            }
            case UNLOCK -> {
                if (days < 1 || days > power.maxDays || (bundleIds.isEmpty() && lots.isEmpty()) || !bundleIds.stream().allMatch(b -> Envelope.ID.matcher(b).matches())) {
                    throw ApiException.badRequest("Déblocage invalide (1 à " + power.maxDays + " jours, contenu obligatoire)");
                }
            }
            case OPEN_ALL -> {
                if (days < 1 || days > power.maxDays) throw ApiException.badRequest("« Tout ouvert » : 1 à " + power.maxDays + " jours");
            }
        }
        if (issuedAt <= 0) throw ApiException.badRequest("Date invalide");
        List<String> b = new ArrayList<>(bundleIds), l = new ArrayList<>(lots);
        Collections.sort(b);
        Collections.sort(l);
        List<String> body = List.of("power=" + power.wire(), "action=" + action, "bundles=" + String.join(",", b), "lots=" + String.join(",", l), "days=" + days);
        return sign(new Envelope("command", keyring.kid(), seq == null ? issuedAt : seq, challenge, issuedAt, issuedAt, issuedAt + DAY_MS, Envelope.Target.device(device.k(), device.factors()), body, "")).token();
    }

    /** A deferred order (type {@code order}); needs the {@code POLICY} scope. The list of actions is the policy engine's closed list, not this format's. */
    public String order(long seq, String nonce, long issuedAt, long notBefore, long expiresAt, Envelope.Target target, String action, Map<String, String> params) {
        need(SignerScope.POLICY);
        if (!ACTION.matcher(action).matches() || params.size() > 16) throw ApiException.badRequest("Ordre invalide");
        for (var e : params.entrySet()) {
            String v = e.getValue();
            if (!NAME.matcher(e.getKey()).matches() || v.length() > 512 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) throw ApiException.badRequest("Ordre invalide");
        }
        if (!Envelope.HEX.matcher(nonce).matches() || expiresAt <= notBefore) throw ApiException.badRequest("Ordre invalide");
        List<String> body = new ArrayList<>(List.of("action=" + action));
        new TreeMap<>(params).forEach((k, v) -> body.add("param=" + k + "|" + v));
        return sign(new Envelope("order", keyring.kid(), seq, nonce, issuedAt, notBefore, expiresAt, target, body, "")).token();
    }

    /** The revocation list: what devices must forget, keys (kid) and seats ({@code licence|poste} → date ms). Target any device, window of 366 days from {@code at}. */
    public String revocation(long at, Set<String> keys, Map<String, Long> seats, Long seq, String nonce) {
        need(SignerScope.REVOKE);
        List<String> body = new ArrayList<>();
        new TreeSet<>(keys).forEach(k -> body.add("key=" + k));
        new TreeMap<>(seats).forEach((s, d) -> body.add("seat=" + s + "|" + d));
        String n = nonce != null ? nonce : "00000000" + String.format("%8s", Long.toHexString(at)).replace(' ', '0');
        return sign(new Envelope("revocation", keyring.kid(), seq == null ? at : seq, n, at, at, at + 366L * DAY_MS, Envelope.Target.ANY, body, "")).token();
    }
}
