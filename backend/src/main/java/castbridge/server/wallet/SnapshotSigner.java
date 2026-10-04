package castbridge.server.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Instantané de solde {@code cbw1} (conception W22 § 3.4) : {@code cbw1.<b64url de la charge JSON>.<b64url de la signature Ed25519>}, signature sur
 * {@code castbridge-wallet-snapshot-v1\ncbw1.<charge b64url>} (ASCII). Charge JSON compacte, signée TELLE QU'ENVOYÉE, champs dans l'ordre
 * {@code kid, id, ed, n, nb, m, mb, seq, at, flags{frozen, stakesN, stakesM}}, entiers seulement (aucun flottant). Le Kotlin (w22-03) lit exactement ce format (vecteurs communs).
 * Deux {@code kid} sont acceptés en vérification pendant une rotation : la clé courante signe, l'ancienne reste reconnue.
 */
public final class SnapshotSigner {
    public static final String PREFIX = "cbw1";
    public static final String DOMAIN = "castbridge-wallet-snapshot-v1";
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final Set<String> EDITIONS = Set.of("TRIAL", "PROD", "UNLIMITED", "NONE");
    private static final Set<String> KEYS = Set.of("kid", "id", "ed", "n", "nb", "m", "mb", "seq", "at", "flags");
    private static final Set<String> FLAG_KEYS = Set.of("frozen", "stakesN", "stakesM");
    private static final ObjectMapper STRICT = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /** Le contenu d'un instantané : {@code n}/{@code m} disponibles, {@code nb}/{@code mb} bloqués, {@code seq} = dernière écriture du compte, {@code at} en ms. */
    public record Snapshot(String id, String ed, long n, long nb, long m, long mb, long seq, long at, boolean frozen, boolean stakesN, boolean stakesM) {}

    private final WalletKey current;
    private final WalletKey previous;

    public SnapshotSigner(WalletKey current, WalletKey previous) {
        this.current = current;
        this.previous = previous;
    }

    public boolean enabled() { return current != null; }

    public String kid() { return current == null ? null : current.kid(); }

    public Set<String> acceptedKids() {
        Set<String> s = new LinkedHashSet<>();
        if (current != null) s.add(current.kid());
        if (previous != null) s.add(previous.kid());
        return s;
    }

    public String sign(Snapshot s) {
        if (current == null) throw new IllegalStateException("Portefeuille indisponible : pas de clé");
        if (!ID.matcher(s.id()).matches() || !EDITIONS.contains(s.ed())) throw new IllegalArgumentException("Instantané invalide");
        for (long v : new long[] {s.n(), s.nb(), s.m(), s.mb(), s.seq(), s.at()}) if (v < 0) throw new IllegalArgumentException("Instantané invalide : montant négatif");
        String json = "{\"kid\":\"" + current.kid() + "\",\"id\":\"" + s.id() + "\",\"ed\":\"" + s.ed() + "\",\"n\":" + s.n() + ",\"nb\":" + s.nb() + ",\"m\":" + s.m() + ",\"mb\":" + s.mb()
                + ",\"seq\":" + s.seq() + ",\"at\":" + s.at() + ",\"flags\":{\"frozen\":" + s.frozen() + ",\"stakesN\":" + s.stakesN() + ",\"stakesM\":" + s.stakesM() + "}}";
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        byte[] sig = current.sign((DOMAIN + "\n" + PREFIX + "." + b64).getBytes(StandardCharsets.US_ASCII));
        return PREFIX + "." + b64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }

    /** La charge d'un instantané authentique (signé par l'un des deux {@code kid} acceptés), sinon vide. Sert aux tests et aux services de ce serveur ; la TV vérifie avec le Kotlin. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> verify(String token) {
        try {
            if (token == null || token.length() > 1_200) return Optional.empty();
            String[] p = token.split("\\.", -1);
            if (p.length != 3 || !p[0].equals(PREFIX)) return Optional.empty();
            byte[] payload = Base64.getUrlDecoder().decode(p[1]);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(payload).equals(p[1])) return Optional.empty();   // base64url canonique
            String text = new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> body = STRICT.readValue(text, Map.class);
            // lecture stricte, comme le Kotlin : ensemble de clés exact, réécriture compacte identique octet pour octet (ni espace, ni clé en double, ni décimal)
            if (!body.keySet().equals(KEYS) || !(body.get("flags") instanceof Map<?, ?> fl) || !fl.keySet().equals(FLAG_KEYS) || !STRICT.writeValueAsString(body).equals(text)) return Optional.empty();
            for (String k : List.of("n", "nb", "m", "mb", "seq", "at")) if (!(body.get(k) instanceof Integer || body.get(k) instanceof Long)) return Optional.empty();   // entiers seulement
            Object kid = body.get("kid");
            WalletKey key = current != null && current.kid().equals(kid) ? current : previous != null && previous.kid().equals(kid) ? previous : null;
            if (key == null) return Optional.empty();
            byte[] sig = Base64.getUrlDecoder().decode(p[2]);
            if (!key.verify((DOMAIN + "\n" + PREFIX + "." + p[1]).getBytes(StandardCharsets.US_ASCII), sig)) return Optional.empty();
            return Optional.of(body);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
