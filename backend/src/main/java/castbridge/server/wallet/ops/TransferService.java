package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.WalletRepository;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Transfert libre entre deux TV (conception § 1.4) : le code de réception du destinataire, la monnaie, le montant et une clé d'idempotence ; UNE transaction {@code TRANSFER}
 * (clé {@code xfer:<émetteur>:<clé de la TV>}). Plafond simple de niveau 1 lu dans {@code wallet_policy} (10 000 NDEM / 100 MBOKO par jour UTC et par identité émettrice) ; destinataire
 * différent de l'émetteur ; code valide, non expiré, à usage unique.
 *
 * <p>Usage unique : le code est PRIS d'abord par un {@code UPDATE} conditionnel atomique, puis le transfert est posé ; si le grand livre refuse (solde insuffisant), le code est rendu. Aucune
 * valeur ne bouge avant que le code soit pris : deux émetteurs en course sur un même code, un seul réussit. (Le grand livre ne se compose pas dans une transaction SQL extérieure, voir
 * {@link JdbcLedger} : la prise puis la pose sont donc deux transactions SQL ordonnées, pas une ; un arrêt entre les deux brûle le code sans bouger de jeton.) Un rejeu (même émetteur, même
 * clé) retrouve le transfert par sa clé et ne repose rien. Les opérations d'un même émetteur sont sérialisées (plafond exact).
 */
@Service
@WalletModuleConfig.Enabled
public class TransferService {
    private final JdbcLedger ledger;
    private final JdbcTemplate jdbc;
    private final WalletPolicyService policies;
    private final ReceiveCodeService codes;
    private final WalletModuleConfig.WalletClock clock;

    public TransferService(JdbcLedger ledger, JdbcTemplate jdbc, WalletPolicyService policies, ReceiveCodeService codes, WalletModuleConfig.WalletClock clock) {
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.policies = policies;
        this.codes = codes;
        this.clock = clock;
    }

    public record Done(boolean replayed, String to) {}

    public Done transfer(String sender, WalletRepository.Identity row, String typedCode, Currency cur, long amt, String idem) {
        if (idem == null || !EscrowService.IDEM.matcher(idem).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (amt < 1 || amt > 1_000_000_000L) throw new LedgerException(WalletReason.BAD_TXN, "Montant hors bornes : 1 à 1 000 000 000");
        if (row.frozen()) throw new LedgerException(WalletReason.FROZEN);
        if (!policies.switches().transfer()) throw new ApiException(HttpStatus.CONFLICT, "Transferts suspendus pour maintenance", List.of("TRANSFER_SUSPENDED"));
        String canonical = ReceiveCodeService.parse(typedCode);
        synchronized (codes.stripeFor(sender)) {
            Instant now = clock.now();
            String key = "xfer:" + sender + ":" + idem;
            ReceiveCodeService.Row code = canonical == null ? null : codes.find(canonical).orElse(null);
            boolean known = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn WHERE idem_key = ?", Long.class, key) > 0;
            if (known) {   // rejeu : la clé retrouve le transfert ; un autre contenu est un conflit
                if (code == null) throw new LedgerException(WalletReason.IDEM_CONFLICT);
                Ledger.Posted p = ledger.post(Txn.transfer(sender, code.holder(), cur, amt, key), "tv", sender, null);
                return new Done(p.replayed(), codes.mask(code.holder()));
            }
            if (code == null || code.usedAt() != null) throw new LedgerException(WalletReason.CODE_UNKNOWN);
            if (!now.isBefore(code.exp())) throw new LedgerException(WalletReason.CODE_EXPIRED);
            if (code.holder().equals(sender)) throw new LedgerException(WalletReason.BAD_TXN, "Vous ne pouvez pas vous envoyer des jetons à vous-même : donnez ce code à l'autre personne");
            policies.get().checkTransfer(cur, amt, sentToday(sender, cur, now));
            Txn txn = Txn.transfer(sender, code.holder(), cur, amt, key);
            if (!codes.claim(canonical, now)) throw new LedgerException(WalletReason.CODE_UNKNOWN);   // un autre émetteur l'a pris à l'instant
            try {
                Ledger.Posted p = ledger.post(txn, "tv", sender, null);
                return new Done(p.replayed(), codes.mask(code.holder()));
            } catch (RuntimeException e) {
                codes.release(canonical, now);   // le grand livre a refusé : le code reste utilisable
                throw e;
            }
        }
    }

    /** Total déjà sorti aujourd'hui (jour UTC) par cette identité dans cette monnaie, par transferts. */
    long sentToday(String sender, Currency cur, Instant now) {
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        Long v = jdbc.queryForObject("SELECT COALESCE(-SUM(e.amount), 0) FROM wallet_entry e JOIN wallet_account a ON a.id = e.account_id JOIN wallet_txn t ON t.id = e.txn_id "
                + "WHERE a.holder = ? AND a.cur = ? AND a.pocket = 'DISPO' AND t.kind = 'TRANSFER' AND e.amount < 0 AND t.created_at >= ?", Long.class, sender, cur.name(), Timestamp.from(dayStart));
        return v == null ? 0 : v;
    }
}
