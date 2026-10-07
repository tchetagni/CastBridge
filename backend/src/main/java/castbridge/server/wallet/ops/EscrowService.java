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
import castbridge.server.wallet.core.EditionSpan;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.StakeRules;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.wallet.core.WinWindows;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    static final Pattern ROOM = Pattern.compile("^[A-Za-z0-9._:-]{1,32}$");
    private static final String DOMAIN = "castbridge-wallet-escrow-v1";

    private final JdbcLedger ledger;
    private final WalletPolicyService policies;
    private final LicenseFacts licenses;
    private final EditionReader reader;
    private final WalletModuleConfig.WalletClock clock;
    private final JdbcTemplate jdbc;
    private final WalletKey key;
    private final GrantService grants;
    private final GameJournal journal;

    public EscrowService(JdbcLedger ledger, WalletPolicyService policies, LicenseFacts licenses, EditionReader reader, WalletModuleConfig.WalletClock clock,
                         JdbcTemplate jdbc, WalletProperties props, GrantService grants, PlayResultKeys playKeys, GameJournal journal) {
        this.grants = grants;
        this.journal = journal;
        this.ledger = ledger;
        this.policies = policies;
        this.licenses = licenses;
        this.reader = reader;
        this.clock = clock;
        this.jdbc = jdbc;
        this.key = WalletKey.fromFile(props.keyFile());
        if (key != null) playKeys.assertNotWalletKey(key.kid());   // F10 : trois clés distinctes
    }

    /** Ce que le serveur sait de l'édition de la TV À CET INSTANT ({@code ed} = étiquette de {@code cbw1}). */
    public record Eff(Edition edition, boolean grace, boolean pending, String ed) {}

    /** Le blocage rendu à la TV. */
    public record Issued(String cbe1, String eid, long iat, long exp, boolean replayed) {}

    /**
     * L'édition effective à cet instant. Si la TV joint ses activations {@code cbx1} on rejoue exactement le calcul de la synchronisation. Sinon (cbw1 signé à chaque écriture) on part de ce que
     * la synchronisation a MÉMORISÉ comme faits, jamais d'une étiquette d'édition : la clé « super » ({@code super_key}) et la fin de l'essai ({@code trial_end_at}) ; la LICENCE est relue maintenant
     * (licence révoquée, suspendue, poste libéré ou parti : plus aucun droit de production).
     */
    public Eff effective(String code, WalletRepository.Identity row, List<String> activations, Instant now) {
        if (!activations.isEmpty()) {
            EditionReader.Reading reading = reader.read(code, activations, now);
            if (reading.accepted()) {
                GrantService.Standing st = grants.readOnly(code, reading, now);
                return new Eff(st.edition(), st.grace(), st.licensePending(), st.ed());
            }
        }
        return stored(code, row, now);
    }

    private Eff stored(String code, WalletRepository.Identity row, Instant now) {
        List<EditionSpan> spans = new ArrayList<>();
        if (row.superKey()) spans.add(new EditionSpan(Edition.SUPER, Instant.EPOCH.plusSeconds(1), null));
        if (row.trialEndAt() != null && row.trialEndAt().isAfter(Instant.EPOCH.plusSeconds(1))) spans.add(new EditionSpan(Edition.TRIAL, Instant.EPOCH.plusSeconds(1), row.trialEndAt()));
        EditionReader.Reading memory = new EditionReader.Reading(code, List.copyOf(spans), false, row.superKey(), false, null, List.of());
        GrantService.Standing st = grants.readOnly(code, memory, now);
        return new Eff(st.edition(), st.grace(), false, st.ed());
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

    public Issued lock(String code, WalletRepository.Identity row, Currency cur, long per, int k, String idem, List<String> activations, String room) {
        return lock(code, row, cur, per, k, idem, activations, room, null);
    }

    /** Un nom de jeu : minuscules, chiffres, `_` et `-`, 1 à 32 caractères. */
    static final Pattern GAME = Pattern.compile("^[a-z0-9_-]{1,32}$");

    /**
     * Blocage d'une mise, avec le JEU pour lequel elle est faite ({@code game}, facultatif : null = usages d'avant, sans règles de jeu). Un jeu misé connu ({@link WalletPolicyService#GAMES} : les échecs et le
     * Quiz) ajoute ses règles, lues de la table de politique à chaque blocage : interrupteur du jeu, mise parmi l'ÉCHELLE du jeu, nombre de sièges (aux échecs UN, une mise par TV ; au Quiz de 1 à 8), TV d'essai
     * refusée (parties libres seulement), plafonds de parties GAGNÉES par identité et PAR JEU (jour, semaine, mois civil d'Africa/Douala) relus du journal. Un rejeu (même clé, même contenu) rend le même
     * {@code cbe1} sans revérifier : le blocage existe déjà.
     */
    public Issued lock(String code, WalletRepository.Identity row, Currency cur, long per, int k, String idem, List<String> activations, String room, String game) {
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Portefeuille indisponible");
        if (idem == null || !IDEM.matcher(idem).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (per < 1 || per > 1_000_000_000L) throw new LedgerException(WalletReason.BAD_TXN, "Mise hors bornes");
        if (room != null && !ROOM.matcher(room).matches()) throw ApiException.badRequest("Salle invalide : 1 à 32 caractères parmi A-Z a-z 0-9 . _ : -");
        if (game != null && !GAME.matcher(game).matches()) throw ApiException.badRequest("Jeu invalide : 1 à 32 caractères parmi a-z 0-9 _ -");
        WalletPolicyService.GamePolicy gp = game == null ? null : policies.game(game).orElseThrow(() -> ApiException.badRequest("Jeu inconnu : les parties misées en ligne ne sont ouvertes qu'aux échecs (chess) et au Quiz (quiz)"));
        String eid = eidOf(code, idem);
        Txn txn = Txn.lock(code, cur, per, k, eid);
        boolean known = !jdbc.queryForList("SELECT eid FROM wallet_escrow WHERE eid = ?", String.class, eid).isEmpty();
        if (!known) {
            Instant now = clock.now();
            if (row.frozen()) throw new LedgerException(WalletReason.FROZEN);
            WalletPolicyService.Switches sw = policies.switches();
            if (!(cur == Currency.NDEM ? sw.stakesNdem() : sw.stakesMboko())) throw new LedgerException(WalletReason.STAKES_SUSPENDED);
            policies.get().checkStake(cur, per);
            // E1 : une mise exige les activations de la TV (comme la synchronisation) : les droits viennent de la licence VIVANTE et des activations, jamais d'une édition mémorisée
            if (activations.isEmpty() || !reader.read(code, activations, now).accepted()) throw new LedgerException(WalletReason.ACTIVATE);
            Eff eff = effective(code, row, activations, now);
            if (eff.pending() && eff.edition() == Edition.NONE && !eff.grace()) {
                throw new ApiException(HttpStatus.CONFLICT, "Licence en attente d'enregistrement", List.of("LICENSE_PENDING"));
            }
            StakeRules.refusal(eff.edition(), cur, eff.grace()).ifPresent(r -> {
                throw new LedgerException(r);
            });
            if (gp != null) gameRules(gp, code, cur, per, k, eff, now);
        }
        Ledger.Posted posted = ledger.post(txn, "tv", code, null);
        Stored st = readAndFill(eid, per, k, room, game);
        return new Issued(sign(eid, code, cur, st.per(), st.k(), st.iat(), st.exp()), eid, st.iat(), st.exp(), posted.replayed());
    }

    /** Le jeu dans une phrase française (« aux échecs », « au Quiz ») ; un jeu sans formule connue est cité par son identifiant. */
    private static String at(String game) {
        return switch (game) {
            case "chess" -> "aux échecs";
            case "quiz" -> "au Quiz";
            default -> "à " + game;
        };
    }

    /** Les règles propres à un jeu misé (échecs, Quiz) ; chaque refus a son motif fermé et son texte français, propre au jeu. */
    private void gameRules(WalletPolicyService.GamePolicy gp, String code, Currency cur, long per, int k, Eff eff, Instant now) {
        if (!gp.enabled()) throw new LedgerException(WalletReason.STAKES_SUSPENDED);
        // règle du propriétaire : l'essai joue en ligne sans mise (ni NDEM, ni MBOKO) ; seules les TV de production, illimitées, en grâce ou « super » misent
        if (eff.edition() == Edition.TRIAL) throw new ApiException(HttpStatus.CONFLICT, "Version d'essai : parties libres seulement, sans mise", List.of("TRIAL_FREE_ONLY"));
        // les sièges qui misent : de 1 à `seats` du jeu (une mise est PAR SIÈGE, payée par le compte de la TV ; aux échecs une seule, au Quiz les téléphones relayés de la TV et sa télécommande)
        if (k < 1 || k > gp.seats()) {
            String where = at(gp.game());
            String phrase = gp.seats() == 1 ? ", une seule mise par TV (un siège)" : ", 1 à " + gp.seats() + " sièges par TV";
            throw new LedgerException(WalletReason.BAD_TXN, Character.toUpperCase(where.charAt(0)) + where.substring(1) + phrase);
        }
        List<Long> scale = gp.scale(cur);
        if (!scale.contains(per)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette mise n'est pas proposée " + at(gp.game()) + " : " + scale.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")) + " " + cur + " par joueur", List.of("STAKE_NOT_OFFERED"));
        }
        WinWindows.Windows w = WinWindows.of(now);
        WinWindows.reached(w, journal.wins(code, gp.game(), w.dayStart()), journal.wins(code, gp.game(), w.weekStart()), journal.wins(code, gp.game(), w.monthStart()), gp.capDay(), gp.capWeek(), gp.capMonth())
                .ifPresent(c -> {
                    throw new ApiException(HttpStatus.CONFLICT, c.text(), List.of("STAKE_WIN_CAP"));
                });
    }

    private record Stored(long per, int k, long iat, long exp) {}

    /**
     * Renseigne per, k, salle, jeu et échéance du blocage (une seule fois) puis RELIT la base : le {@code cbe1} est toujours signé depuis ce que la base contient (M1) ; une requête qui diffère de
     * ce qui est inscrit (autre mise, autres sièges, autre salle, autre jeu) est un {@code IDEM_CONFLICT}, jamais un second {@code cbe1} qui contredirait le blocage (M4 : la salle ne se change pas).
     */
    private Stored readAndFill(String eid, long per, int k, String room, String game) {
        List<Object[]> r = jdbc.query("SELECT created_at, exp_at FROM wallet_escrow WHERE eid = ?", (rs, i) -> new Object[] {rs.getTimestamp("created_at"), rs.getTimestamp("exp_at")}, eid);
        Timestamp created = (Timestamp) r.get(0)[0], exp = (Timestamp) r.get(0)[1];
        long iat = created.toInstant().toEpochMilli();
        long expMs = exp == null ? created.toInstant().plus(TTL).toEpochMilli() : exp.toInstant().toEpochMilli();
        jdbc.update("UPDATE wallet_escrow SET per = ?, k = ?, room = ?, game = ?, exp_at = ? WHERE eid = ? AND per IS NULL", per, k, room, game, Timestamp.from(Instant.ofEpochMilli(expMs)), eid);
        Object[] now = jdbc.query("SELECT per, k, room, amount, game FROM wallet_escrow WHERE eid = ?", (rs, i) -> new Object[] {rs.getLong("per"), rs.getInt("k"), rs.getString("room"), rs.getLong("amount"), rs.getString("game")}, eid).get(0);
        long dbPer = (Long) now[0];
        int dbK = (Integer) now[1];
        String dbRoom = (String) now[2];
        if (dbPer != per || dbK != k || !java.util.Objects.equals(dbRoom, room) || !java.util.Objects.equals(now[4], game) || dbPer * dbK != (Long) now[3]) throw new LedgerException(WalletReason.IDEM_CONFLICT);
        return new Stored(dbPer, dbK, iat, expMs);
    }

    private String sign(String eid, String id, Currency cur, long per, int k, long iat, long exp) {
        String json = "{\"aud\":\"castbridge-play\",\"kid\":\"" + key.kid() + "\",\"eid\":\"" + eid + "\",\"id\":\"" + id + "\",\"cur\":\"" + cur + "\",\"per\":" + per + ",\"k\":" + k
                + ",\"amt\":" + per * k + ",\"iat\":" + iat + ",\"exp\":" + exp + "}";
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        byte[] sig = key.sign((DOMAIN + "\ncbe1." + b64).getBytes(StandardCharsets.US_ASCII));
        return "cbe1." + b64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    }
}
