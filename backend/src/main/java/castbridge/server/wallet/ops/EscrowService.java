package castbridge.server.wallet.ops;

import castbridge.server.wallet.EditionReader;
import castbridge.server.wallet.GrantService;
import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.LicenseFacts;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletKey;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.WalletProperties;
import castbridge.server.wallet.WalletRepository;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.StakeRules;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Blocage d'une mise (conception § 3.3) : {@code per × k} passe de DISPO à BLOQUE (transaction {@code ESCROW_LOCK}, clé d'opération = le blocage {@code eid}, dérivé de l'identité ET de la
 * clé d'idempotence de la TV : deux TV ne peuvent pas se heurter) puis un bon de blocage {@code cbe1} signé par la clé « portefeuille » (domaine {@code castbridge-wallet-escrow-v1}) est rendu
 * à la TV, qui le joint à sa salle. Règle du propriétaire : une mise MBOKO exige que la LICENCE de la TV soit en état de produire (active, ou en grâce) ; l'activation {@code cbx1} ne dit
 * que l'identité. Une mise NDEM est permise à toute TV activée (essai compris). Un rejeu (même clé, même contenu) rend le MÊME {@code cbe1} sans rien reposer.
 */
@Service
@WalletModuleConfig.Enabled
public class EscrowService {
    public static final Duration TTL = Duration.ofMinutes(30);
    public static final Duration REFUND_AFTER_EXP = Duration.ofHours(6);
    static final Pattern IDEM = Pattern.compile("^[A-Za-z0-9._:-]{3,64}$");
    private static final String DOMAIN = "castbridge-wallet-escrow-v1";

    private final JdbcLedger ledger;
    private final WalletPolicyService policies;
    private final LicenseFacts licenses;
    private final EditionReader reader;
    private final WalletModuleConfig.WalletClock clock;
    private final JdbcTemplate jdbc;
    private final WalletKey key;
    private final GrantService grants;

    public EscrowService(JdbcLedger ledger, WalletPolicyService policies, LicenseFacts licenses, EditionReader reader, WalletModuleConfig.WalletClock clock,
                         JdbcTemplate jdbc, WalletProperties props, GrantService grants) {
        this.grants = grants;
        this.ledger = ledger;
        this.policies = policies;
        this.licenses = licenses;
        this.reader = reader;
        this.clock = clock;
        this.jdbc = jdbc;
        this.key = WalletKey.fromFile(props.keyFile());
    }

    /** Ce que le serveur sait de l'édition de la TV À CET INSTANT ({@code ed} = étiquette de {@code cbw1}). */
    public record Eff(Edition edition, boolean grace, boolean pending, String ed) {}

    /** Le blocage rendu à la TV. */
    public record Issued(String cbe1, String eid, long iat, long exp, boolean replayed) {}

    /**
     * L'édition effective : si la TV joint ses activations {@code cbx1} (comme à la synchronisation) on rejoue exactement le calcul de la synchronisation ; sinon on part de la dernière édition
     * connue (essai) et de la LICENCE lue maintenant. Limite documentée : une fin d'essai n'est vue qu'à la synchronisation suivante.
     */
    public Eff effective(String code, WalletRepository.Identity row, List<String> activations, Instant now) {
        List<LicenseFacts.LicenseView> ls = licenses.forDevice(code);
        if (!activations.isEmpty()) {
            EditionReader.Reading reading = reader.read(code, activations, now);
            if (reading.accepted()) {
                GrantService.Standing st = grants.readOnly(code, reading, now);
                return new Eff(st.edition(), st.grace(), st.licensePending(), st.ed());
            }
        }
        boolean grace = false;
        for (LicenseFacts.LicenseView l : ls) {
            if (l.state() == LicenseFacts.State.ACTIVE && (l.endAt() == null || now.isBefore(l.endAt()))) {
                return l.endAt() == null ? new Eff(Edition.UNLIMITED, false, false, "UNLIMITED") : new Eff(Edition.PRODUCTION, false, false, "PROD");
            }
            if ((l.state() == LicenseFacts.State.ACTIVE || l.state() == LicenseFacts.State.EXPIRED) && l.endAt() != null && !now.isBefore(l.endAt())
                    && now.isBefore(l.endAt().plusSeconds(l.graceDays() * 86_400L))) grace = true;
        }
        if (grace) return new Eff(Edition.NONE, true, false, "PROD");
        String stored = row.edition() == null ? "NONE" : row.edition();
        return switch (stored) {
            case "TRIAL" -> new Eff(Edition.TRIAL, false, false, "TRIAL");
            case "UNLIMITED" -> ls.isEmpty() ? new Eff(Edition.UNLIMITED, false, false, "UNLIMITED") : new Eff(Edition.NONE, false, false, "NONE");   // sans licence : droit « super »
            default -> new Eff(Edition.NONE, false, false, "NONE");
        };
    }

    /** Identifiant du blocage : 22 caractères base64url (128 bits) dérivés de l'identité et de la clé d'idempotence. */
    static String eidOf(String identity, String idem) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("castbridge-eid-v1\n" + identity + "\n" + idem).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(h, 16));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Issued lock(String code, WalletRepository.Identity row, Currency cur, long per, int k, String idem, List<String> activations) {
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Portefeuille indisponible");
        if (idem == null || !IDEM.matcher(idem).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (per < 1 || per > 1_000_000_000L) throw new LedgerException(WalletReason.BAD_TXN, "Mise hors bornes");
        String eid = eidOf(code, idem);
        Txn txn = Txn.lock(code, cur, per, k, eid);
        boolean known = !jdbc.queryForList("SELECT eid FROM wallet_escrow WHERE eid = ?", String.class, eid).isEmpty();
        if (!known) {
            Instant now = clock.now();
            if (row.frozen()) throw new LedgerException(WalletReason.FROZEN);
            WalletPolicyService.Switches sw = policies.switches();
            if (!(cur == Currency.NDEM ? sw.stakesNdem() : sw.stakesMboko())) throw new LedgerException(WalletReason.STAKES_SUSPENDED);
            policies.get().checkStake(cur, per);
            Eff eff = effective(code, row, activations, now);
            if (eff.pending() && eff.edition() == Edition.NONE && !eff.grace()) {
                throw new ApiException(HttpStatus.CONFLICT, "Licence en attente d'enregistrement", List.of("LICENSE_PENDING"));
            }
            StakeRules.refusal(eff.edition(), cur, eff.grace()).ifPresent(r -> {
                throw new LedgerException(r);
            });
        }
        Ledger.Posted posted = ledger.post(txn, "tv", code, null);
        long[] times = readAndFill(eid, per, k);
        return new Issued(sign(eid, code, cur, per, k, times[0], times[1]), eid, times[0], times[1], posted.replayed());
    }

    /** Renseigne per, k et l'échéance du blocage (une seule fois) et rend {iat, exp} en ms, calculés sur la date de création : un rejeu rend les mêmes valeurs. */
    private long[] readAndFill(String eid, long per, int k) {
        List<long[]> r = jdbc.query("SELECT created_at, exp_at FROM wallet_escrow WHERE eid = ?", (rs, i) -> {
            Timestamp created = rs.getTimestamp("created_at"), exp = rs.getTimestamp("exp_at");
            return new long[] {created.toInstant().toEpochMilli(), exp == null ? created.toInstant().plus(TTL).toEpochMilli() : exp.toInstant().toEpochMilli()};
        }, eid);
        long[] t = r.get(0);
        jdbc.update("UPDATE wallet_escrow SET per = ?, k = ?, exp_at = ? WHERE eid = ? AND per IS NULL", per, k, Timestamp.from(Instant.ofEpochMilli(t[1])), eid);
        return t;
    }

    private String sign(String eid, String id, Currency cur, long per, int k, long iat, long exp) {
        String json = "{\"aud\":\"castbridge-play\",\"kid\":\"" + key.kid() + "\",\"eid\":\"" + eid + "\",\"id\":\"" + id + "\",\"cur\":\"" + cur + "\",\"per\":" + per + ",\"k\":" + k
                + ",\"amt\":" + per * k + ",\"iat\":" + iat + ",\"exp\":" + exp + "}";
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        byte[] sig = key.sign((DOMAIN + "\ncbe1." + b64).getBytes(StandardCharsets.US_ASCII));
        return "cbe1." + b64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }
}
