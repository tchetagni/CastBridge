package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.WalletRepository;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Conversion;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletPolicy;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Conversion à la demande, dans les deux sens, à 1 000 NDEM = 1 MBOKO (frais du sens inverse 0 % par défaut, réglables) : une transaction {@code CONVERT} par appel, clé
 * {@code cvt:<identité>:<clé de la TV>}. Le taux ET les frais sont relus dans {@code wallet_policy} à CHAQUE appel (aucune valeur en mémoire) et rendus dans la réponse. Permise à toute
 * TV activée, essai compris (un essai peut détenir, recevoir et reconvertir des MBOKO : il ne peut pas en miser). Même clé + autre sens ou autre quantité : {@code IDEM_CONFLICT}.
 */
@Service
@WalletModuleConfig.Enabled
public class ConvertService {
    private final JdbcLedger ledger;
    private final WalletPolicyService policies;
    private final JdbcTemplate jdbc;

    public ConvertService(JdbcLedger ledger, WalletPolicyService policies, JdbcTemplate jdbc) {
        this.ledger = ledger;
        this.policies = policies;
        this.jdbc = jdbc;
    }

    /** {@code rate} et {@code reverseFeeBp} : ceux de l'exécution d'origine pour un rejeu, ceux de maintenant pour une première exécution. */
    public record Done(Conversion.Quote quote, long rate, int reverseFeeBp, boolean replayed) {}

    public Done convert(String code, WalletRepository.Identity row, Conversion.Direction dir, long q, String idem) {
        if (idem == null || !EscrowService.IDEM.matcher(idem).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (row.frozen()) throw new LedgerException(WalletReason.FROZEN);
        if (!policies.switches().convert()) throw new ApiException(HttpStatus.CONFLICT, "Conversion suspendue pour maintenance", List.of("CONVERT_SUSPENDED"));
        WalletPolicy policy = policies.get();   // à chaque appel
        String key = "cvt:" + code + ":" + idem;
        Txn txn = Txn.convert(code, dir, q, policy, key);
        Ledger.Posted p;
        try {
            p = ledger.post(txn, "tv", code, null);
        } catch (LedgerException e) {
            // F2 : le propriétaire a changé le taux ou les frais entre l'exécution et le réessai de la TV : la même demande (sens, quantité) rend le résultat ORIGINAL, rien n'est reposé
            if (e.reason() == WalletReason.IDEM_CONFLICT) {
                Done original = original(code, dir, q, key);
                if (original != null) return original;
            }
            throw e;
        }
        return new Done(Conversion.quote(dir, q, policy), policy.rate(), policy.reverseFeeBp(), p.replayed());
    }

    /** Relit les écritures de la conversion déjà posée ; null si la demande n'est pas la même (autre sens ou autre quantité : vrai conflit). */
    private Done original(String code, Conversion.Direction dir, long q, String key) {
        long mboko = 0, ndemPlayer = 0, ndemConvert = 0, fee = 0;
        boolean found = false;
        for (java.util.Map<String, Object> r : jdbc.queryForList("SELECT a.holder, a.cur, e.amount FROM wallet_txn t JOIN wallet_entry e ON e.txn_id = t.id JOIN wallet_account a ON a.id = e.account_id "
                + "WHERE t.idem_key = ? AND t.kind = 'CONVERT'", key)) {
            found = true;
            String holder = (String) r.get("holder"), cur = (String) r.get("cur");
            long amount = ((Number) r.get("amount")).longValue();
            if (holder.equals(code) && cur.equals("MBOKO")) mboko += amount;
            else if (holder.equals(code) && cur.equals("NDEM")) ndemPlayer += amount;
            else if (holder.equals(AccountRef.CONVERT) && cur.equals("NDEM")) ndemConvert += amount;
            else if (holder.equals(AccountRef.FEE) && cur.equals("NDEM")) fee += amount;
        }
        if (!found) return null;
        if (dir == Conversion.Direction.N2M) {
            if (mboko != q || ndemPlayer >= 0) return null;
            long gross = -ndemPlayer;
            return new Done(new Conversion.Quote(dir, q, gross, 0, gross), gross / q, 0, true);
        }
        if (mboko != -q || ndemConvert >= 0) return null;
        long gross = -ndemConvert;
        int bp = fee == 0 ? 0 : (int) ((fee * 10_000L) / gross);   // le taux rond qui redonne ces frais (le plus grand qui les produit ; les frais appliqués sont exacts dans tous les cas)
        return new Done(new Conversion.Quote(dir, q, gross, fee, ndemPlayer), gross / q, bp, true);
    }
}
