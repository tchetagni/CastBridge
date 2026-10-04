package castbridge.server.wallet;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletPolicy;
import castbridge.server.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration du portefeuille sous {@code /api/v1/admin/wallet/**} (jeton d'administration, comme toutes les routes d'administration existantes : le jeton est le facteur fort
 * des actions sensibles, voir {@code Actor.token()}) : don d'un administrateur (écriture {@code ADJUST} positive, motif OBLIGATOIRE, clé d'idempotence facultative) et réconciliation
 * des invariants I-1, I-3 et I-8 (rapport JSON). Rien ici ne signe : ces routes restent disponibles sans clé « portefeuille ».
 */
@RestController
@RequestMapping("/api/v1/admin/wallet")
@WalletModuleConfig.Enabled
public class WalletAdminController {
    private static final Logger log = LoggerFactory.getLogger(WalletAdminController.class);
    private static final Pattern IDEM = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");
    private static final long MAX_GRANT = 1_000_000_000L;
    static final String ACTOR = "admin:admin-token";

    private final JdbcLedger ledger;
    private final WalletRepository repo;
    private final WalletPolicyService policies;

    public WalletAdminController(JdbcLedger ledger, WalletRepository repo, WalletPolicyService policies) {
        this.ledger = ledger;
        this.repo = repo;
        this.policies = policies;
    }

    public record GrantBody(String identity, String currency, Long amount, String reason, String idem) {}

    @PostMapping("/grant")
    public ResponseEntity<Map<String, Object>> grant(@RequestBody(required = false) GrantBody b) {
        if (b == null) throw ApiException.badRequest("Corps JSON attendu : identité, monnaie, montant, motif");
        Currency cur;
        try {
            cur = Currency.valueOf(String.valueOf(b.currency()));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Monnaie inconnue : NDEM ou MBOKO");
        }
        if (b.amount() == null || b.amount() < 1 || b.amount() > MAX_GRANT) throw ApiException.badRequest("Montant hors bornes : 1 à " + MAX_GRANT);
        if (b.reason() == null || b.reason().isBlank() || b.reason().length() > 200) throw ApiException.badRequest("Motif obligatoire (200 caractères au plus)");
        if (b.identity() == null || !AccountRef.IDENTITY.matcher(b.identity()).matches()) throw ApiException.badRequest("Identité invalide : XXXX-XXXX-XXXX-XXXX");
        if (b.idem() != null && !IDEM.matcher(b.idem()).matches()) throw ApiException.badRequest("Clé d'idempotence invalide : 1 à 64 caractères parmi A-Z a-z 0-9 . _ : -");
        if (repo.identity(b.identity()).isEmpty()) throw ApiException.notFound("Identité inconnue : aucune synchronisation de cette TV");
        String key = "adj:admin:" + (b.idem() != null ? b.idem() : UUID.randomUUID().toString());
        Ledger.Posted p = ledger.post(Txn.adjust(b.identity(), cur, b.amount(), key), ACTOR, b.identity(), b.reason().trim());
        log.info("wallet : don de l'administrateur {} {} (motif consigné), rejeu={}", b.amount(), cur, p.replayed());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("replayed", p.replayed());
        out.put("balance", ledger.balance(AccountRef.dispo(b.identity(), cur)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    /**
     * I-1 : Σ des comptes = 0 par monnaie (soldes ET écritures) ; I-3 : masse en circulation = −Σ comptes système, SYS:FEE ≥ 0, SYS:POT = 0, BLOQUE = Σ blocages ouverts ; la relation de
     * conversion « NDEM net détruit = taux × MBOKO net créé » est RAPPORTÉE (elle ne vaut exactement que si le taux n'a jamais changé) sans faire échouer {@code ok} ; I-8 : chaque solde
     * en cache = Σ de ses écritures.
     */
    @GetMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile() {
        Map<String, Object> i1 = new LinkedHashMap<>(), i1e = new LinkedHashMap<>(), i3 = new LinkedHashMap<>();
        boolean ok = true, i3ok = true;
        for (Currency c : Currency.values()) {
            Map<String, Long> s = repo.balanceSums(c.name());
            i1.put(c.name(), s.get("all"));
            i1e.put(c.name(), s.get("entries"));
            ok &= s.get("all") == 0 && s.get("entries") == 0;
            i3.put("mass" + c.name(), s.get("players"));
            i3.put("system" + c.name(), s.get("system"));
            boolean massOk = s.get("players") + s.get("system") == 0;
            boolean bloqueOk = s.get("bloque").longValue() == s.get("openEscrows").longValue();
            boolean potOk = repo.systemBalance(AccountRef.POT, c.name()) == 0;
            i3.put("bloqueMatchesEscrows" + c.name(), bloqueOk);
            i3ok &= massOk && bloqueOk && potOk;
        }
        boolean feeOk = repo.systemBalance(AccountRef.FEE, "NDEM") >= 0 && repo.systemBalance(AccountRef.FEE, "MBOKO") == 0;
        i3ok &= feeOk;
        WalletPolicy pol = policies.get();
        long convN = repo.systemBalance(AccountRef.CONVERT, "NDEM"), convM = repo.systemBalance(AccountRef.CONVERT, "MBOKO");
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("convertNDEM", convN);
        rel.put("convertMBOKO", convM);
        rel.put("rate", pol.rate());
        rel.put("exactAtCurrentRate", convN == -pol.rate() * convM);
        i3.put("conversion", rel);
        i3.put("feeNonNegative", feeOk);
        i3.put("ok", i3ok);
        long mismatches = repo.derivedMismatches();
        Map<String, Object> i8 = new LinkedHashMap<>();
        i8.put("mismatches", mismatches);
        i8.put("sampleAccountIds", mismatches == 0 ? List.of() : repo.derivedMismatchSample(10));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", ok && i3ok && mismatches == 0);
        out.put("i1", i1);
        out.put("i1Entries", i1e);
        out.put("i3", i3);
        out.put("i8", i8);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }
}
