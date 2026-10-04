package castbridge.server.wallet;

import castbridge.server.licenses.Actor;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletPolicy;
import castbridge.server.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private static final Object LOCK = new Object();

    private final JdbcLedger ledger;
    private final WalletRepository repo;
    private final WalletPolicyService policies;
    private final AdminAccess access;
    private final WalletModuleConfig.WalletClock clock;

    public WalletAdminController(JdbcLedger ledger, WalletRepository repo, WalletPolicyService policies, AdminAccess access, WalletModuleConfig.WalletClock clock) {
        this.ledger = ledger;
        this.repo = repo;
        this.policies = policies;
        this.access = access;
        this.clock = clock;
    }

    public record GrantBody(String identity, String currency, Long amount, String reason, String idem) {}

    public record RebindBody(String identity, String reason) {}

    private static Map<String, Object> done(boolean replayed, long balance, long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", "APPLIED");
        out.put("requestId", id);
        out.put("replayed", replayed);
        out.put("balance", balance);
        return out;
    }

    private static ResponseEntity<Map<String, Object>> pending(long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("status", "PENDING");
        out.put("requestId", id);
        out.put("message", "Au-delà du plafond : un second administrateur doit approuver cette demande");
        return ResponseEntity.status(202).cacheControl(CacheControl.noStore()).body(out);
    }

    /**
     * Don d'un administrateur (audit H2) : jeton porteur + COMPTE nommé + TOTP à usage unique (voir {@link AdminAccess}) ; plafond par don et par jour glissant dans la table de politique, au-delà
     * duquel la demande reste {@code PENDING} jusqu'à l'approbation d'un SECOND administrateur ({@code /grant/{id}/approve}, TOTP distinct) ; au plus {@code admin.grantsPerHour} demandes par heure ;
     * chaque étape est inscrite au journal d'audit chaîné. La clé d'idempotence ({@code idem}) rejoue sans doublon.
     */
    @PostMapping("/grant")
    public ResponseEntity<Map<String, Object>> grant(@RequestHeader(name = "X-Admin-User", required = false) String user, @RequestHeader(name = "X-Totp", required = false) String totp,
                                                     @RequestBody(required = false) GrantBody b) {
        Actor actor = access.verify(user, totp);
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
        String reason = b.reason().trim();
        synchronized (LOCK) {
            Optional<WalletRepository.AdminGrant> prior = repo.adminGrantByKey(key);
            if (prior.isPresent()) {
                WalletRepository.AdminGrant g = prior.get();
                if (!g.identity().equals(b.identity()) || !g.currency().equals(cur.name()) || g.amount() != b.amount()) throw ApiException.conflict("Cette clé d'idempotence a déjà servi pour un autre don");
                if (g.state().equals("APPLIED")) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(done(true, ledger.balance(AccountRef.dispo(g.identity(), cur)), g.id()));
                if (g.state().equals("PENDING")) return pending(g.id());
                throw ApiException.conflict("Cette demande a été refusée");
            }
            Instant now = clock.now();
            WalletPolicyService.AdminCaps caps = policies.adminCaps(cur);
            if (repo.adminGrantsSince(now.minus(Duration.ofHours(1))) >= caps.perHour()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de dons en une heure (" + caps.perHour() + " au plus, toutes monnaies) : réessayez plus tard");
            }
            long today = repo.adminAppliedSince(cur.name(), now.minus(Duration.ofHours(24)));
            boolean needsSecond = b.amount() > caps.grantMax() || today + b.amount() > caps.dailyMax();
            long id = repo.insertAdminGrant(key, actor.name(), b.identity(), cur.name(), b.amount(), reason, "PENDING", now);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("requestId", id);
            details.put("currency", cur.name());
            details.put("amount", b.amount());
            if (needsSecond) {
                access.record(actor, "WALLET_GRANT_REQUEST", b.identity(), reason, details);
                log.info("wallet : don de {} {} au-delà du plafond, demande {} en attente d'un second administrateur", b.amount(), cur, id);
                return pending(id);
            }
            Ledger.Posted p = ledger.post(Txn.adjust(b.identity(), cur, b.amount(), key), "admin:" + actor.name(), b.identity(), reason);
            repo.decideAdminGrant(id, "APPLIED", null, now);
            access.record(actor, "WALLET_GRANT", b.identity(), reason, details);
            log.info("wallet : don de l'administrateur {} {} (motif consigné), rejeu={}", b.amount(), cur, p.replayed());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(done(p.replayed(), ledger.balance(AccountRef.dispo(b.identity(), cur)), id));
        }
    }

    /** Un SECOND administrateur (compte et TOTP distincts du demandeur) approuve une demande en attente : seule l'approbation pose l'écriture (clé = celle de la demande). */
    @PostMapping("/grant/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable long id, @RequestHeader(name = "X-Admin-User", required = false) String user,
                                                       @RequestHeader(name = "X-Totp", required = false) String totp) {
        Actor actor = access.verify(user, totp);
        synchronized (LOCK) {
            WalletRepository.AdminGrant g = repo.adminGrantById(id).orElseThrow(() -> ApiException.notFound("Demande inconnue"));
            if (!g.state().equals("PENDING")) throw ApiException.conflict("Cette demande n'est plus en attente (" + g.state() + ")");
            if (g.requestedBy().equals(actor.name())) throw new ApiException(HttpStatus.FORBIDDEN, "Un autre administrateur que le demandeur doit approuver");
            Currency cur = Currency.valueOf(g.currency());
            Ledger.Posted p = ledger.post(Txn.adjust(g.identity(), cur, g.amount(), g.idemKey()), "admin:" + g.requestedBy(), g.identity(), g.reason());
            repo.decideAdminGrant(id, "APPLIED", actor.name(), clock.now());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("requestId", id);
            details.put("requestedBy", g.requestedBy());
            details.put("currency", g.currency());
            details.put("amount", g.amount());
            access.record(actor, "WALLET_GRANT_APPROVE", g.identity(), g.reason(), details);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(done(p.replayed(), ledger.balance(AccountRef.dispo(g.identity(), cur)), id));
        }
    }

    /** Refuse une demande en attente : rien n'est posé. */
    @PostMapping("/grant/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable long id, @RequestHeader(name = "X-Admin-User", required = false) String user,
                                                      @RequestHeader(name = "X-Totp", required = false) String totp) {
        Actor actor = access.verify(user, totp);
        synchronized (LOCK) {
            WalletRepository.AdminGrant g = repo.adminGrantById(id).orElseThrow(() -> ApiException.notFound("Demande inconnue"));
            if (!repo.decideAdminGrant(id, "REJECTED", actor.name(), clock.now())) throw ApiException.conflict("Cette demande n'est plus en attente (" + g.state() + ")");
            access.record(actor, "WALLET_GRANT_REJECT", g.identity(), g.reason(), Map.of("requestId", id, "requestedBy", g.requestedBy()));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("status", "REJECTED");
            out.put("requestId", id);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
        }
    }

    /**
     * Réaffecte la liaison d'une identité (audit M5) : à n'utiliser que si la liaison a été prise par une copie de {@code cbx1} ; l'identité n'est plus liée à aucun appareil et perd sa clé
     * d'installation, le prochain appareil qui prouve la possession la lie. Compte nommé, TOTP, motif obligatoire, journal d'audit chaîné.
     */
    @PostMapping("/rebind")
    public ResponseEntity<Map<String, Object>> rebind(@RequestHeader(name = "X-Admin-User", required = false) String user, @RequestHeader(name = "X-Totp", required = false) String totp,
                                                      @RequestBody(required = false) RebindBody b) {
        Actor actor = access.verify(user, totp);
        if (b == null || b.identity() == null || !AccountRef.IDENTITY.matcher(b.identity()).matches()) throw ApiException.badRequest("Identité invalide : XXXX-XXXX-XXXX-XXXX");
        if (b.reason() == null || b.reason().isBlank() || b.reason().length() > 200) throw ApiException.badRequest("Motif obligatoire (200 caractères au plus)");
        if (!repo.clearBinding(b.identity())) throw ApiException.notFound("Identité inconnue : aucune synchronisation de cette TV");
        access.record(actor, "WALLET_REBIND", b.identity(), b.reason().trim(), Map.of());
        log.info("wallet : liaison d'une identité réaffectée par {}", actor.name());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
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
