package castbridge.server.wallet.ops;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Settlement;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Les clés PUBLIQUES du service de jeu qui vérifient les résultats {@code cbr1} ({@code castbridge.wallet.play-result-pubkeys} : une ou deux clés séparées par des virgules, rotation comme
 * {@code TICKET_PUBKEY_2} ; chaque élément est « base64 de la clé brute de 32 octets » ou « nom:base64:… » comme les clés de confiance). Format (conception § 3.4) :
 * {@code cbr1.<b64url charge>.<b64url signature>}, signature Ed25519 sur {@code castbridge-play-result-v1\ncbr1.<charge>}, charge JSON compacte aux clés exactes
 * {@code kid, rid, room, game, cur, per, kind, at, lines=[[eid, id, used, pay]…]} (≤ 16 lignes), entiers seulement : la lecture est aussi stricte que celle du Kotlin
 * (réécriture compacte identique octet pour octet, aucune clé en double ni en trop). Aucune clé privée ici : l'API ne signe JAMAIS un résultat.
 */
@Component
@WalletModuleConfig.Enabled
public class PlayResultKeys {
    public static final String PREFIX = "cbr1";
    public static final String DOMAIN = "castbridge-play-result-v1";
    public static final int MAX_LINES = 16;
    private static final int MAX_TOKEN = 4_096;
    private static final Set<String> KEYS = Set.of("kid", "rid", "room", "game", "cur", "per", "kind", "at", "lines");
    private static final Pattern RID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern EID = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9._:-]{1,32}");
    private static final ObjectMapper STRICT = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /** Un résultat AUTHENTIQUE (signature vérifiée) et bien formé ; {@code sha} = empreinte de la charge, pour distinguer un rejeu d'un autre contenu sous le même {@code rid}. */
    public record Result(String kid, String rid, String room, String game, Currency cur, long per, Settlement.Kind kind, long at, List<Settlement.Line> lines, String sha) {}

    private final Map<String, byte[]> keys = new LinkedHashMap<>();

    public PlayResultKeys(@Value("${castbridge.wallet.play-result-pubkeys:}") String configured) {
        for (String raw : configured.split(",")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            String[] parts = s.split(":");
            String b64 = parts.length >= 2 ? parts[1] : parts[0];
            byte[] pub = Base64.getDecoder().decode(b64.trim());
            if (pub.length != 32) throw new IllegalArgumentException("castbridge.wallet.play-result-pubkeys : clé publique de 32 octets attendue");
            keys.put(LicenseKeyring.kidOf(pub), pub);
        }
        if (keys.size() > 2) throw new IllegalArgumentException("castbridge.wallet.play-result-pubkeys : deux clés au plus (rotation)");
    }

    public boolean configured() { return !keys.isEmpty(); }

    public Set<String> kids() { return keys.keySet(); }

    private static ApiException bad(String why) { return new ApiException(HttpStatus.BAD_REQUEST, "Résultat illisible : " + why, List.of("RESULT_BAD")); }

    private static ApiException forged(String why) { return new ApiException(HttpStatus.FORBIDDEN, "Résultat non authentique : " + why, List.of("RESULT_FORGED")); }

    /** Lit et vérifie un {@code cbr1} ; 400 s'il est mal formé, 403 s'il n'est pas signé par l'une des clés du service de jeu. */
    @SuppressWarnings("unchecked")
    public Result verify(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN) throw bad("jeton absent ou trop long");
        String[] p = token.trim().split("\\.", -1);
        if (p.length != 3 || !p[0].equals(PREFIX)) throw bad("format cbr1 attendu");
        String text;
        Map<String, Object> m;
        try {
            byte[] payload = Base64.getUrlDecoder().decode(p[1]);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(payload).equals(p[1])) throw bad("base64url non canonique");
            text = new String(payload, StandardCharsets.UTF_8);
            m = STRICT.readValue(text, Map.class);
            if (!m.keySet().equals(KEYS) || !STRICT.writeValueAsString(m).equals(text)) throw bad("champs inattendus");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw bad("charge illisible");
        }
        String kid = str(m.get("kid"), "kid");
        byte[] pub = keys.get(kid);
        if (pub == null) throw forged("clé inconnue");
        try {
            byte[] sig = Base64.getUrlDecoder().decode(p[2]);
            if (!LicenseKeyring.verify(pub, (DOMAIN + "\n" + PREFIX + "." + p[1]).getBytes(StandardCharsets.US_ASCII), sig)) throw forged("signature fausse");
        } catch (IllegalArgumentException e) {
            throw forged("signature illisible");
        }
        String rid = str(m.get("rid"), "rid");
        if (!RID.matcher(rid).matches()) throw bad("rid : 128 bits en hexadécimal minuscule");
        String room = str(m.get("room"), "room"), game = str(m.get("game"), "game");
        if (!LABEL.matcher(room).matches() || !LABEL.matcher(game).matches()) throw bad("room ou game invalide");
        Currency cur;
        Settlement.Kind kind;
        try {
            cur = Currency.valueOf(str(m.get("cur"), "cur"));
            kind = Settlement.Kind.valueOf(str(m.get("kind"), "kind"));
        } catch (IllegalArgumentException e) {
            throw bad("monnaie ou genre inconnu");
        }
        long per = num(m.get("per"), "per"), at = num(m.get("at"), "at");
        if (per < 1 || per > 1_000_000_000L || at < 0) throw bad("mise ou date hors bornes");
        if (!(m.get("lines") instanceof List<?> raw) || raw.isEmpty() || raw.size() > MAX_LINES) throw bad("1 à " + MAX_LINES + " lignes");
        List<Settlement.Line> lines = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof List<?> l) || l.size() != 4) throw bad("ligne [eid, id, utilisé, payé] attendue");
            String eid = str(l.get(0), "eid"), id = str(l.get(1), "id");
            if (!EID.matcher(eid).matches() || !AccountRef.IDENTITY.matcher(id).matches()) throw bad("eid ou identité invalide");
            long used = num(l.get(2), "utilisé"), pay = num(l.get(3), "payé");
            if (used < 0 || pay < 0 || used > 1_000_000_000_000L || pay > 8_000_000_000_000L) throw bad("montants hors bornes");
            lines.add(new Settlement.Line(eid, id, 0, used, pay));
        }
        return new Result(kid, rid, room, game, cur, per, kind, at, List.copyOf(lines), sha(text));
    }

    private static String str(Object o, String what) {
        if (!(o instanceof String s)) throw bad(what + " : texte attendu");
        return s;
    }

    private static long num(Object o, String what) {
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return l;
        throw bad(what + " : entier attendu");
    }

    static String sha(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
