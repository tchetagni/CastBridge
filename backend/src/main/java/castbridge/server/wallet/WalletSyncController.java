package castbridge.server.wallet;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.StakeRules;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/wallet/sync} (jeton d'appareil, comme {@code PlayTicketController}) : la TV annonce son code d'appareil et ses activations {@code cbx1} (≤ 4, ≤ 8 192 caractères) ;
 * l'API lit l'identité et l'essai ({@link EditionReader}) et la licence ({@link LicenseFacts}), inscrit les tranches dues (idempotent), appelle les {@link SyncContributor} et rend
 * {@code {snapshot: cbw1, history: 20 lignes, contributions, notices, edition}}. La TV ne crée jamais un jeton : aucun montant n'est lu dans la requête (I-7). Aucune activation ni
 * aucun jeton d'appareil n'est journalisé.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@WalletModuleConfig.Enabled
public class WalletSyncController {
    private static final Logger log = LoggerFactory.getLogger(WalletSyncController.class);
    static final String LICENSE_PENDING_TEXT = "Licence en attente d'enregistrement";

    private final DeviceService devices;
    private final EditionReader reader;
    private final GrantService grants;
    private final JdbcLedger ledger;
    private final WalletRepository repo;
    private final WalletPolicyService policies;
    private final SnapshotSigner signer;
    private final WalletModuleConfig.WalletClock clock;
    private final ObjectProvider<SyncContributor> contributors;

    public WalletSyncController(DeviceService devices, EditionReader reader, GrantService grants, JdbcLedger ledger, WalletRepository repo, WalletPolicyService policies,
                                SnapshotSigner signer, WalletModuleConfig.WalletClock clock, ObjectProvider<SyncContributor> contributors) {
        this.devices = devices;
        this.reader = reader;
        this.grants = grants;
        this.ledger = ledger;
        this.repo = repo;
        this.policies = policies;
        this.signer = signer;
        this.clock = clock;
        this.contributors = contributors;
    }

    /** Authentifie l'appareil (401), exige la clé « portefeuille » (503) : partagé par les routes de l'appareil. */
    static Device device(DeviceService devices, SnapshotSigner signer, String authorization) {
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Jeton d'appareil inconnu"));
        if (!signer.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Portefeuille indisponible");
        return d;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(@RequestHeader(name = "Authorization", required = false) String authorization, @RequestBody(required = false) JsonNode body) {
        Device d = device(devices, signer, authorization);
        if (!"tv".equals(d.app)) throw new ApiException(HttpStatus.FORBIDDEN, "Seule une TV CastBridge-TV a un portefeuille");
        if (d.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        if (body == null || !body.isObject()) throw ApiException.badRequest("Corps JSON attendu : {\"deviceCode\":\"XXXX-XXXX-XXXX-XXXX\",\"activations\":[…]}");
        String code = DeviceIdentity.parseCode(body.path("deviceCode").asText(null));
        if (code == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        List<String> activations = new ArrayList<>();
        JsonNode arr = body.path("activations");
        if (arr.isArray()) for (JsonNode a : arr) if (a.isTextual()) activations.add(a.asText());

        Instant now = clock.now();
        EditionReader.Reading reading = reader.read(code, activations, now);
        List<Map<String, String>> notices = new ArrayList<>();
        GrantService.Standing st;
        boolean boundOther;
        if (reading.accepted()) {
            policies.checkWrite(code);
            GrantService.Outcome o = grants.sync(code, d.id, reading, now);
            st = o.standing();
            boundOther = o.boundOther();
        } else {
            // aucune activation acceptée : on n'ouvre rien et on n'inscrit rien ; une identité déjà ouverte peut seulement être LUE
            WalletRepository.Identity known = repo.identity(code).orElseThrow(() -> new LedgerException(reading.clockDoubt() ? WalletReason.CLOCK : WalletReason.ACTIVATE));
            st = grants.readOnly(code, reading, now);
            boundOther = known.apiDeviceId() != d.id;
            WalletReason why = reading.clockDoubt() ? WalletReason.CLOCK : WalletReason.ACTIVATE;
            notices.add(notice(why.name(), why.text()));
        }
        if (st.licensePending()) notices.add(notice("LICENSE_PENDING", LICENSE_PENDING_TEXT));
        if (boundOther) notices.add(notice(WalletReason.BOUND_OTHER_TV.name(), WalletReason.BOUND_OTHER_TV.text()));

        WalletRepository.Identity row = repo.identity(code).orElseThrow();
        WalletPolicyService.Switches sw = policies.switches();
        boolean stakesN = sw.stakesNdem() && !row.frozen() && StakeRules.mayStake(st.edition(), Currency.NDEM, st.grace());
        boolean stakesM = sw.stakesMboko() && !row.frozen() && StakeRules.mayStake(st.edition(), Currency.MBOKO, st.grace());
        String snapshot = signer.sign(new SnapshotSigner.Snapshot(code, st.ed(), ledger.balance(AccountRef.dispo(code, Currency.NDEM)), ledger.balance(AccountRef.bloque(code, Currency.NDEM)),
                ledger.balance(AccountRef.dispo(code, Currency.MBOKO)), ledger.balance(AccountRef.bloque(code, Currency.MBOKO)), ledger.lastEntryId(code), now.toEpochMilli(),
                row.frozen(), stakesN, stakesM));

        Map<String, Object> contributions = new LinkedHashMap<>();
        SyncContributor.SyncContext ctx = new SyncContributor.SyncContext(code, now, body, st);
        contributors.orderedStream().forEach(c -> {
            try {
                contributions.put(c.name(), c.contribute(ctx));
            } catch (RuntimeException e) {
                log.warn("wallet : le contributeur {} a échoué ({})", c.name(), e.getClass().getSimpleName());
                contributions.put(c.name(), Map.of("erreur", "indisponible"));
            }
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("snapshot", snapshot);
        out.put("history", WalletHistoryController.lines(repo.history(code, 20, null)));
        out.put("contributions", contributions);
        out.put("notices", notices);
        Map<String, Object> ed = new LinkedHashMap<>();
        ed.put("ed", st.ed());
        ed.put("license", st.licenseState());
        ed.put("grace", st.grace());
        ed.put("boundOther", boundOther);
        out.put("edition", ed);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    private static Map<String, String> notice(String reason, String text) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("reason", reason);
        m.put("text", text);
        return m;
    }
}
