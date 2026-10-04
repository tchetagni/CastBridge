package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.WalletRepository;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Transfert libre entre deux TV (conception § 1.4) : le code de réception du destinataire, la monnaie, le montant et une clé d'idempotence ; UNE transaction {@code TRANSFER}
 * (clé {@code xfer:<émetteur>:<clé de la TV>}). Plafond simple de niveau 1 lu dans {@code wallet_policy} (10 000 NDEM / 100 MBOKO par jour UTC et par identité émettrice) ; destinataire
 * différent de l'émetteur ; code valide, non expiré, à usage unique. Seuls NDEM et MBOKO se transfèrent : jamais une licence, une activation ni un droit.
 *
 * <p>UNE SEULE transaction SQL (audit w22-05, M2) : le grand livre exécute, après avoir verrouillé les comptes (ordre croissant des identifiants), vérifié l'idempotence et le découvert, une
 * garde ({@link JdbcLedger.InTx}) qui relit le plafond du jour SOUS le verrou du compte de l'émetteur (exact même avec plusieurs instances de l'API), applique les limites de l'essai (M5) et
 * consomme le code par un {@code UPDATE} conditionnel ({@code used_at IS NULL AND exp_at > ?}) ; un échec annule tout : le code n'est jamais brûlé sans transfert ni rendu après un transfert.
 * Un rejeu (même émetteur, même clé) retrouve le transfert par sa clé AVANT tout contrôle d'état (gel, interrupteur) et ne repose rien.
 */
@Service
@WalletModuleConfig.Enabled
public class TransferService {
    private final JdbcLedger ledger;
    private final JdbcTemplate jdbc;
    private final WalletPolicyService policies;
    private final ReceiveCodeService codes;
    private final EscrowService escrows;
    private final WalletModuleConfig.WalletClock clock;

    public TransferService(JdbcLedger ledger, JdbcTemplate jdbc, WalletPolicyService policies, ReceiveCodeService codes, EscrowService escrows, WalletModuleConfig.WalletClock clock) {
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.policies = policies;
        this.codes = codes;
        this.escrows = escrows;
        this.clock = clock;
    }

    public record Done(boolean replayed, String to) {}

    public Done transfer(String sender, WalletRepository.Identity row, String typedCode, Currency cur, long amt, String idem) {
        if (idem == null || !EscrowService.IDEM.matcher(idem).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (amt < 1 || amt > 1_000_000_000L) throw new LedgerException(WalletReason.BAD_TXN, "Montant hors bornes : 1 à 1 000 000 000");
        String canonical = ReceiveCodeService.parse(typedCode);
        Instant now = clock.now();
        String key = "xfer:" + sender + ":" + idem;
        ReceiveCodeService.Row code = canonical == null ? null : codes.find(canonical).orElse(null);
        boolean known = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn WHERE idem_key = ?", Long.class, key) > 0;
        if (known) {   // rejeu : la clé retrouve le transfert (avant tout contrôle d'état : un transfert fait reste fait) ; un autre contenu est un conflit
            if (code == null) throw new LedgerException(WalletReason.IDEM_CONFLICT);
            Ledger.Posted p = ledger.post(Txn.transfer(sender, code.holder(), cur, amt, key), "tv", sender, null);
            return new Done(p.replayed(), codes.mask(code.holder()));
        }
        if (row.frozen()) throw new LedgerException(WalletReason.FROZEN);
        if (!policies.switches().transfer()) throw new ApiException(HttpStatus.CONFLICT, "Transferts suspendus pour maintenance", List.of("TRANSFER_SUSPENDED"));
        if (code == null || code.usedAt() != null) throw new LedgerException(WalletReason.CODE_UNKNOWN);
        if (!now.isBefore(code.exp())) throw new LedgerException(WalletReason.CODE_EXPIRED);
        if (code.holder().equals(sender)) throw new LedgerException(WalletReason.BAD_TXN, "Vous ne pouvez pas vous envoyer des jetons à vous-même : donnez ce code à l'autre personne");
        policies.get().checkTransfer(cur, amt, sentToday(sender, cur, now));   // refus rapide, avant tout verrou ; la lecture FAISANT FOI est celle de la garde, sous le verrou du compte
        EscrowService.Eff eff = escrows.effective(sender, row, List.of(), now);
        boolean restricted = eff.edition() == Edition.TRIAL || (eff.edition() == Edition.NONE && !eff.grace());   // essai, ou sans droit : mêmes limites
        String recipient = code.holder();
        Instant usedAt = now.truncatedTo(ChronoUnit.MICROS);
        Ledger.Posted p = ledger.post(Txn.transfer(sender, recipient, cur, amt, key), "tv", sender, null, j -> {
            long sent = sentToday(j, sender, cur, now);   // sous le verrou du compte de l'émetteur : plafond exact
            policies.get().checkTransfer(cur, amt, sent);
            if (restricted) checkTrial(j, sender, recipient, cur, amt, sent, now);
            int n = j.update("UPDATE wallet_recv_code SET used_at = ?, used_key = ? WHERE code = ? AND holder = ? AND used_at IS NULL AND exp_at > ?", Timestamp.from(usedAt), key, canonical, recipient,
                    Timestamp.from(now));
            if (n != 1) throw new LedgerException(WalletReason.CODE_UNKNOWN);   // un autre émetteur l'a pris à l'instant, ou il vient d'expirer
        });
        return new Done(p.replayed(), codes.mask(recipient));   // hors transaction : un échec ici ne touche plus au code
    }

    /** Limites d'un compte d'essai (audit M5) ; toutes les lectures se font dans la transaction du transfert. */
    private void checkTrial(JdbcTemplate j, String sender, String recipient, Currency cur, long amt, long sentToday, Instant now) {
        WalletPolicyService.TrialLimits lim = policies.trialLimits();
        Timestamp created = j.queryForObject("SELECT created_at FROM wallet_identity WHERE holder = ?", Timestamp.class, sender);
        if (created != null && created.toInstant().plus(Duration.ofHours(lim.minAgeHours())).isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Compte d'essai trop récent : les transferts s'ouvrent " + lim.minAgeHours() + " heures après la première activation", List.of("TRIAL_LIMIT"));
        }
        long dayCap = cur == Currency.NDEM ? lim.dailyNdem() : lim.dailyMboko();
        if (sentToday + amt > dayCap) throw new LedgerException(WalletReason.DAILY_CAP);
        Instant since = now.minus(Duration.ofHours(24));
        long pair = sentToRecipient(j, sender, recipient, cur, since);
        if (pair + amt > (cur == Currency.NDEM ? lim.pairNdem() : lim.pairMboko())) throw new LedgerException(WalletReason.DAILY_CAP);
        List<String> donors = donors(j, recipient, since);
        if (!donors.contains(sender) && donors.size() >= lim.maxDonors()) throw new LedgerException(WalletReason.DAILY_CAP);
    }

    /** Total déjà sorti aujourd'hui (jour UTC) par cette identité dans cette monnaie, par transferts. */
    long sentToday(String sender, Currency cur, Instant now) { return sentToday(jdbc, sender, cur, now); }

    static long sentToday(JdbcTemplate j, String sender, Currency cur, Instant now) {
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        Long v = j.queryForObject("SELECT COALESCE(-SUM(e.amount), 0) FROM wallet_entry e JOIN wallet_account a ON a.id = e.account_id JOIN wallet_txn t ON t.id = e.txn_id "
                + "WHERE a.holder = ? AND a.cur = ? AND a.pocket = 'DISPO' AND t.kind = 'TRANSFER' AND e.amount < 0 AND t.created_at >= ?", Long.class, sender, cur.name(), Timestamp.from(dayStart));
        return v == null ? 0 : v;
    }

    /** Total reçu par ce destinataire DE CET émetteur depuis {@code since}. */
    private static long sentToRecipient(JdbcTemplate j, String sender, String recipient, Currency cur, Instant since) {
        Long v = j.queryForObject("SELECT COALESCE(SUM(e2.amount), 0) FROM wallet_entry e2 JOIN wallet_account a2 ON a2.id = e2.account_id JOIN wallet_txn t ON t.id = e2.txn_id "
                + "WHERE t.kind = 'TRANSFER' AND t.created_at >= ? AND a2.holder = ? AND a2.cur = ? AND a2.pocket = 'DISPO' AND e2.amount > 0 "
                + "AND EXISTS (SELECT 1 FROM wallet_entry e1 JOIN wallet_account a1 ON a1.id = e1.account_id WHERE e1.txn_id = t.id AND a1.holder = ? AND e1.amount < 0)", Long.class, Timestamp.from(since),
                recipient, cur.name(), sender);
        return v == null ? 0 : v;
    }

    /** Les émetteurs distincts qui ont donné à ce destinataire depuis {@code since}. */
    private static List<String> donors(JdbcTemplate j, String recipient, Instant since) {
        return j.queryForList("SELECT DISTINCT a1.holder FROM wallet_entry e1 JOIN wallet_account a1 ON a1.id = e1.account_id JOIN wallet_txn t ON t.id = e1.txn_id "
                + "WHERE t.kind = 'TRANSFER' AND t.created_at >= ? AND e1.amount < 0 AND a1.pocket = 'DISPO' "
                + "AND EXISTS (SELECT 1 FROM wallet_entry e2 JOIN wallet_account a2 ON a2.id = e2.account_id WHERE e2.txn_id = t.id AND a2.holder = ? AND e2.amount > 0)", String.class, Timestamp.from(since), recipient);
    }
}
