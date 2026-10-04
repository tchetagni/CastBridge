package castbridge.server.wallet.ops;

import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.WalletRepository;
import castbridge.server.wallet.core.Conversion;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletPolicy;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
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

    public ConvertService(JdbcLedger ledger, WalletPolicyService policies) {
        this.ledger = ledger;
        this.policies = policies;
    }

    public record Done(Conversion.Quote quote, WalletPolicy policy, boolean replayed) {}

    public Done convert(String code, WalletRepository.Identity row, Conversion.Direction dir, long q, String idem) {
        if (idem == null || !EscrowService.IDEM.matcher(idem).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (row.frozen()) throw new LedgerException(WalletReason.FROZEN);
        if (!policies.switches().convert()) throw new ApiException(HttpStatus.CONFLICT, "Conversion suspendue pour maintenance", List.of("CONVERT_SUSPENDED"));
        WalletPolicy policy = policies.get();   // à chaque appel
        Txn txn = Txn.convert(code, dir, q, policy, "cvt:" + code + ":" + idem);
        Ledger.Posted p = ledger.post(txn, "tv", code, null);
        return new Done(Conversion.quote(dir, q, policy), policy, p.replayed());
    }
}
