package castbridge.server.wallet.ops;

import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Codes de réception (conception § 1.4, R-E4) : la TV qui reçoit demande un code {@code R} + 8 caractères Crockford + 1 de contrôle (≈ 40 bits tirés par {@link SecureRandom}), valable
 * 10 minutes, à usage unique, 3 actifs au plus par identité, jamais d'annuaire. L'émetteur le consulte (destinataire MASQUÉ : initiale du nom de TV s'il est connu, 4 derniers caractères du
 * code d'appareil ; 10 consultations par heure et par identité, 1 000 par heure au total ⇒ alerte au journal) puis le consomme avec {@link TransferService}. Affichage {@code R7K2-M9QX-4F} ;
 * la saisie accepte minuscules, tirets et confusions classiques (O/0, I/L/1).
 */
@Service
@WalletModuleConfig.Enabled
public class ReceiveCodeService {
    private static final Logger log = LoggerFactory.getLogger(ReceiveCodeService.class);
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final Duration TTL = Duration.ofMinutes(10);
    public static final int MAX_ACTIVE = 3;
    static final int LOOKUPS_PER_HOUR = 10, LOOKUPS_PER_HOUR_GLOBAL = 1_000;
    private static final long HOUR_MS = 3_600_000L;

    private final JdbcTemplate jdbc;
    private final WalletModuleConfig.WalletClock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ArrayDeque<Long>> lookups = new LinkedHashMap<>(16, 0.75f, true);
    private final ArrayDeque<Long> allLookups = new ArrayDeque<>();
    private final Object[] stripes = new Object[64];
    private long lastAlertHour = -1;

    public ReceiveCodeService(JdbcTemplate jdbc, WalletModuleConfig.WalletClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        for (int i = 0; i < stripes.length; i++) stripes[i] = new Object();
    }

    /** Un code connu du serveur. */
    public record Row(String code, String holder, Instant exp, Instant usedAt) {}

    public record Created(String display, long exp) {}

    // ---- forme du code ----

    static char check(String nine) {
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (i + 1) * ALPHABET.indexOf(nine.charAt(i));
        return ALPHABET.charAt(sum % 32);
    }

    static String display(String canonical) { return canonical.substring(0, 4) + "-" + canonical.substring(4, 8) + "-" + canonical.substring(8); }

    /** Forme canonique (10 caractères) d'une saisie, ou null si elle ne peut pas être un code valide (longueur, alphabet, contrôle). */
    public static String parse(String typed) {
        if (typed == null || typed.length() > 24) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : typed.toCharArray()) {
            if (c == '-' || c == ' ') continue;
            char u = Character.toUpperCase(c);
            if (u == 'O') u = '0';
            else if (u == 'I' || u == 'L') u = '1';
            sb.append(u);
        }
        String s = sb.toString();
        if (s.length() != 10 || s.charAt(0) != 'R') return null;
        for (int i = 0; i < 10; i++) if (ALPHABET.indexOf(s.charAt(i)) < 0) return null;
        return check(s.substring(0, 9)) == s.charAt(9) ? s : null;
    }

    private Object lock(String holder) { return stripes[Math.floorMod(holder.hashCode(), stripes.length)]; }

    // ---- création ----

    public Created create(String holder) {
        synchronized (lock(holder)) {
            Instant now = clock.now();
            long active = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_recv_code WHERE holder = ? AND used_at IS NULL AND exp_at > ?", Long.class, holder, Timestamp.from(now));
            if (active >= MAX_ACTIVE) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trois codes de réception sont déjà actifs : attendez l'expiration de l'un d'eux", List.of("CODE_LIMIT"));
            Instant exp = now.plus(TTL);
            for (int attempt = 0; attempt < 8; attempt++) {
                StringBuilder sb = new StringBuilder("R");
                for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(random.nextInt(32)));
                sb.append(check(sb.toString()));
                String code = sb.toString();
                try {
                    jdbc.update("INSERT INTO wallet_recv_code (code, holder, exp_at) VALUES (?, ?, ?)", code, holder, Timestamp.from(exp));
                    return new Created(display(code), exp.toEpochMilli());
                } catch (DuplicateKeyException e) {
                    // collision (2^-40) : on retire
                }
            }
            throw new IllegalStateException("code de réception : tirage répété sans succès");
        }
    }

    // ---- lecture ----

    public Optional<Row> find(String canonical) {
        return jdbc.query("SELECT code, holder, exp_at, used_at FROM wallet_recv_code WHERE code = ?", (rs, i) -> new Row(rs.getString("code"), rs.getString("holder"), rs.getTimestamp("exp_at").toInstant(),
                rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()), canonical).stream().findFirst();
    }

    /** Le code valide et libre, sinon {@code CODE_UNKNOWN} (inconnu, mal saisi ou déjà utilisé) ou {@code CODE_EXPIRED}. */
    public Row usable(String typed, Instant now) {
        String canonical = parse(typed);
        Row row = canonical == null ? null : find(canonical).orElse(null);
        if (row == null || row.usedAt() != null) throw new LedgerException(WalletReason.CODE_UNKNOWN);
        if (!now.isBefore(row.exp())) throw new LedgerException(WalletReason.CODE_EXPIRED);
        return row;
    }

    /** Consultation de l'émetteur : débit (10 / h / identité, 1 000 / h global) puis destinataire masqué. */
    public String lookup(String requester, String typed) {
        Instant now = clock.now();
        countLookup(requester, now.toEpochMilli());
        return mask(usable(typed, now).holder());
    }

    private synchronized void countLookup(String requester, long now) {
        while (!allLookups.isEmpty() && now - allLookups.peekFirst() >= HOUR_MS) allLookups.pollFirst();
        ArrayDeque<Long> q = lookups.computeIfAbsent(requester, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() >= HOUR_MS) q.pollFirst();
        if (lookups.size() > 50_000) lookups.values().removeIf(d -> d.isEmpty() || now - d.peekLast() >= HOUR_MS);
        if (q.size() >= LOOKUPS_PER_HOUR) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de consultations de codes de réception cette heure : réessayez plus tard", List.of("LOOKUP_LIMIT"));
        q.addLast(now);
        allLookups.addLast(now);
        if (allLookups.size() > LOOKUPS_PER_HOUR_GLOBAL && lastAlertHour != now / HOUR_MS) {
            lastAlertHour = now / HOUR_MS;
            log.warn("wallet : ALERTE plus de {} consultations de codes de réception en une heure (sondage possible)", LOOKUPS_PER_HOUR_GLOBAL);
        }
    }

    /** « TV de K… · …4F2Q » : initiale du nom de TV s'il est connu, 4 derniers caractères du code d'appareil ; jamais le code entier. */
    public String mask(String holder) {
        String tail = holder.substring(holder.length() - 4);
        List<String> names = jdbc.queryForList("SELECT COALESCE(NULLIF(d.device_name, ''), NULLIF(d.label, '')) FROM wallet_identity i JOIN device d ON d.id = i.api_device_id WHERE i.holder = ?", String.class, holder);
        String name = names.isEmpty() ? null : names.get(0);
        if (name != null) {
            int cp = name.strip().codePoints().filter(Character::isLetterOrDigit).findFirst().orElse(-1);
            if (cp > 0) return "TV de " + new String(Character.toChars(Character.toUpperCase(cp))) + "… · …" + tail;
        }
        return "TV · …" + tail;
    }

    // ---- consommation (usage unique) ----

    /** Prend le code (UPDATE conditionnel, atomique) ; faux si un autre l'a pris ou s'il a expiré. */
    public boolean claim(String canonical, Instant now) {
        return jdbc.update("UPDATE wallet_recv_code SET used_at = ? WHERE code = ? AND used_at IS NULL AND exp_at > ?", Timestamp.from(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS)), canonical, Timestamp.from(now)) == 1;
    }

    /** Rend le code quand le grand livre a refusé le transfert (solde insuffisant, etc.) : seul celui qui l'avait pris peut le rendre (même instant). */
    public void release(String canonical, Instant claimedAt) {
        jdbc.update("UPDATE wallet_recv_code SET used_at = NULL WHERE code = ? AND used_at = ?", canonical, Timestamp.from(claimedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }

    public Object stripeFor(String holder) { return lock(holder); }
}
