package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.SyncContributor;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rend les blocages restés sans résultat : un blocage {@code OPEN} dont {@code exp + 6 h} est atteint est rendu en entier ({@code ESCROW_REFUND}, clé {@code refund:<eid>}, donc une seule
 * fois). Appelé à chaque synchronisation du titulaire (ses blocages) et par la réconciliation de l'administrateur (tous). Une course avec un règlement est sans danger : le grand livre ne
 * ferme un blocage qu'une fois, sous verrou ; le perdant est ignoré ici (rendu déjà fait ou règlement arrivé). Un résultat qui arrive APRÈS le rendu est refusé par {@link SettleService}.
 */
@Component
@WalletModuleConfig.Enabled
public class EscrowExpiryContributor implements SyncContributor {
    private static final Logger log = LoggerFactory.getLogger(EscrowExpiryContributor.class);
    private final JdbcLedger ledger;
    private final JdbcTemplate jdbc;

    public EscrowExpiryContributor(JdbcLedger ledger, JdbcTemplate jdbc) {
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    @Override public String name() { return "escrow-expiry"; }

    @Override
    public Object contribute(SyncContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("refunded", refundDue(ctx.identity(), ctx.now()));
        return out;
    }

    /** Rend les blocages échus de {@code holder} (ou de tous si {@code holder} est null) ; rend les {@code eid} rendus par CET appel. */
    public List<String> refundDue(String holder, Instant now) {
        String sql = "SELECT eid, holder, cur, amount, created_at, exp_at FROM wallet_escrow WHERE state = 'OPEN'" + (holder == null ? "" : " AND holder = ?") + " ORDER BY created_at, eid";
        List<Object[]> open = jdbc.query(sql, (rs, i) -> new Object[] {rs.getString("eid"), rs.getString("holder"), rs.getString("cur"), rs.getLong("amount"), rs.getTimestamp("created_at"),
                rs.getTimestamp("exp_at")}, holder == null ? new Object[0] : new Object[] {holder});
        List<String> refunded = new ArrayList<>();
        for (Object[] o : open) {
            Instant exp = o[5] != null ? ((Timestamp) o[5]).toInstant() : ((Timestamp) o[4]).toInstant().plus(EscrowService.TTL);
            if (now.isBefore(exp.plus(EscrowService.REFUND_AFTER_EXP))) continue;
            try {
                ledger.post(Txn.refund((String) o[0], (String) o[1], Currency.valueOf((String) o[2]), (Long) o[3]), "system", (String) o[1], "échéance du blocage");
                refunded.add((String) o[0]);
                log.info("wallet : blocage {} rendu à l'échéance (aucun résultat reçu)", o[0]);
            } catch (LedgerException e) {
                if (e.reason() != WalletReason.ESCROW_CLOSED) throw e;   // réglé ou rendu entre-temps : rien à faire
            }
        }
        return refunded;
    }
}
