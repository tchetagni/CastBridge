package castbridge.server.wallet.ops;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.SnapshotSigner;
import castbridge.server.wallet.WalletModuleConfig;
import castbridge.server.wallet.WalletPolicyService;
import castbridge.server.wallet.WalletRepository;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Conversion;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.StakeRules;
import castbridge.server.wallet.core.WalletReason;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les opérations qui DÉPLACENT des jetons, sous {@code /api/v1/wallet} : blocage, règlement, conversion, codes de réception, transfert. Toute opération de la TV : jeton d'appareil (401),
 * clé « portefeuille » présente (503), appareil de type TV non bloqué, {@code deviceCode} dans le corps (comme {@code sync}), identité connue du grand livre (jamais synchronisée :
 * « Activez la TV »), et appareil = celui de la liaison d'ouverture du compte, sinon {@code BOUND_OTHER_TV} (R-E3) ; puis le débit d'écriture par identité. Toute réponse d'écriture porte
 * un {@code cbw1} neuf ; tout refus un motif fermé et son texte français. La TV ne dit jamais un montant à créditer : seul le règlement d'un résultat SIGNÉ ({@code /settle}, sans
 * authentification, débit par adresse) rend des gains, et seulement entre blocages déjà posés.
 */
@RestController
@WalletModuleConfig.Enabled
public class WalletOpsController {
    private final DeviceService devices;
    private final SnapshotSigner signer;
    private final WalletRepository repo;
    private final WalletPolicyService policies;
    private final JdbcLedger ledger;
    private final EscrowService escrows;
    private final SettleService settles;
    private final ConvertService converts;
    private final ReceiveCodeService codes;
    private final TransferService transfers;
    private final EscrowExpiryContributor expiry;
    private final WalletModuleConfig.WalletClock clock;
    private final int settlePerMinute;
    private final Map<String, ArrayDeque<Long>> settleWindows = new LinkedHashMap<>(16, 0.75f, true);

    public WalletOpsController(DeviceService devices, SnapshotSigner signer, WalletRepository repo, WalletPolicyService policies, JdbcLedger ledger, EscrowService escrows, SettleService settles,
                               ConvertService converts, ReceiveCodeService codes, TransferService transfers, EscrowExpiryContributor expiry, WalletModuleConfig.WalletClock clock,
                               @Value("${castbridge.wallet.settle-per-minute:60}") int settlePerMinute) {
        this.devices = devices;
        this.signer = signer;
        this.repo = repo;
        this.policies = policies;
        this.ledger = ledger;
        this.escrows = escrows;
        this.settles = settles;
        this.converts = converts;
        this.codes = codes;
        this.transfers = transfers;
        this.expiry = expiry;
        this.clock = clock;
        this.settlePerMinute = Math.max(1, settlePerMinute);
    }

    /** L'identité qui parle, après toutes les vérifications d'appareil. */
    private record Who(String code, WalletRepository.Identity row) {}

    private Who who(String authorization, String deviceCode, boolean write) {
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Jeton d'appareil inconnu"));
        if (!signer.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Portefeuille indisponible");
        if (!"tv".equals(d.app)) throw new ApiException(HttpStatus.FORBIDDEN, "Seule une TV CastBridge-TV a un portefeuille");
        if (d.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        String code = DeviceIdentity.parseCode(deviceCode);
        if (code == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        WalletRepository.Identity row = repo.identity(code).orElseThrow(() -> new LedgerException(WalletReason.ACTIVATE));
        if (row.apiDeviceId() != d.id) throw new LedgerException(WalletReason.BOUND_OTHER_TV);
        if (write) policies.checkWrite(code);
        return new Who(code, row);
    }

    private static JsonNode object(JsonNode body) {
        if (body == null || !body.isObject()) throw ApiException.badRequest("Corps JSON attendu : {\"deviceCode\":\"XXXX-XXXX-XXXX-XXXX\", …}");
        return body;
    }

    private static String text(JsonNode b, String field) {
        JsonNode n = b.get(field);
        return n != null && n.isTextual() ? n.asText() : null;
    }

    private static long integer(JsonNode b, String field) {
        JsonNode n = b.get(field);
        if (n == null || !n.isIntegralNumber() || !n.canConvertToLong()) throw ApiException.badRequest("Champ entier attendu : " + field);
        return n.asLong();
    }

    private static Currency currency(JsonNode b) {
        String c = text(b, "cur");
        if ("NDEM".equals(c)) return Currency.NDEM;
        if ("MBOKO".equals(c)) return Currency.MBOKO;
        throw ApiException.badRequest("Monnaie inconnue : NDEM ou MBOKO");
    }

    /** Un {@code cbw1} neuf : les soldes et les drapeaux de mise sont ceux de MAINTENANT. */
    private String snapshot(Who w) {
        Instant now = clock.now();
        WalletRepository.Identity row = repo.identity(w.code()).orElse(w.row());
        EscrowService.Eff eff = escrows.effective(w.code(), row, List.of(), now);
        WalletPolicyService.Switches sw = policies.switches();
        boolean stakesN = sw.stakesNdem() && !row.frozen() && StakeRules.mayStake(eff.edition(), Currency.NDEM, eff.grace());
        boolean stakesM = sw.stakesMboko() && !row.frozen() && StakeRules.mayStake(eff.edition(), Currency.MBOKO, eff.grace());
        return signer.sign(new SnapshotSigner.Snapshot(w.code(), eff.ed(), ledger.balance(AccountRef.dispo(w.code(), Currency.NDEM)), ledger.balance(AccountRef.bloque(w.code(), Currency.NDEM)),
                ledger.balance(AccountRef.dispo(w.code(), Currency.MBOKO)), ledger.balance(AccountRef.bloque(w.code(), Currency.MBOKO)), ledger.lastEntryId(w.code()), now.toEpochMilli(),
                row.frozen(), stakesN, stakesM));
    }

    private static ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }

    // ---- blocage ----

    @PostMapping("/api/v1/wallet/escrow")
    public ResponseEntity<Map<String, Object>> escrow(@RequestHeader(name = "Authorization", required = false) String authorization, @RequestBody(required = false) JsonNode body) {
        JsonNode b = object(body);
        Who w = who(authorization, text(b, "deviceCode"), true);
        List<String> acts = new ArrayList<>();
        if (b.path("activations").isArray()) for (JsonNode a : b.get("activations")) if (a.isTextual() && acts.size() < 4) acts.add(a.asText());
        long k = integer(b, "k");
        if (k < 1 || k > 8) throw new LedgerException(WalletReason.BAD_TXN, "Blocage : 1 à 8 sièges");
        EscrowService.Issued i = escrows.lock(w.code(), w.row(), currency(b), integer(b, "per"), (int) k, text(b, "idem"), acts, text(b, "room"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cbe1", i.cbe1());
        out.put("eid", i.eid());
        out.put("iat", i.iat());
        out.put("exp", i.exp());
        out.put("replayed", i.replayed());
        out.put("snapshot", snapshot(w));
        return ok(out);
    }

    // ---- règlement (sans authentification : le résultat est signé) ----

    @PostMapping("/api/v1/wallet/settle")
    public ResponseEntity<Map<String, Object>> settle(HttpServletRequest request, @RequestBody(required = false) String raw) {
        limitSettle(request.getRemoteAddr());
        String token = raw == null ? "" : raw.trim();
        if (token.startsWith("{")) {
            try {
                JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(token);
                token = n.path("cbr1").asText("");
            } catch (java.io.IOException e) {
                throw ApiException.badRequest("Corps illisible : le résultat cbr1 en texte brut, ou {\"cbr1\":\"…\"}");
            }
        }
        return ok(settles.settle(token));
    }

    private void limitSettle(String address) {
        long now = clock.now().toEpochMilli();
        synchronized (settleWindows) {
            ArrayDeque<Long> q = settleWindows.computeIfAbsent(address == null ? "?" : address, k -> new ArrayDeque<>());
            while (!q.isEmpty() && now - q.peekFirst() >= 60_000L) q.pollFirst();
            if (settleWindows.size() > 20_000) settleWindows.values().removeIf(d -> d.isEmpty() || now - d.peekLast() >= 60_000L);
            if (q.size() >= settlePerMinute) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de règlements en une minute depuis cette adresse : réessayez dans un instant", java.util.List.of("RATE_LIMIT"));
            q.addLast(now);
        }
    }

    // ---- conversion ----

    @PostMapping("/api/v1/wallet/convert")
    public ResponseEntity<Map<String, Object>> convert(@RequestHeader(name = "Authorization", required = false) String authorization, @RequestBody(required = false) JsonNode body) {
        JsonNode b = object(body);
        Who w = who(authorization, text(b, "deviceCode"), true);
        Conversion.Direction dir;
        try {
            dir = Conversion.Direction.valueOf(String.valueOf(text(b, "dir")));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Sens inconnu : N2M ou M2N");
        }
        ConvertService.Done d = converts.convert(w.code(), w.row(), dir, integer(b, "q"), text(b, "idem"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dir", dir.name());
        out.put("q", d.quote().mboko());
        out.put("rate", d.rate());
        out.put("reverseFeeBp", d.reverseFeeBp());
        out.put("ndemGross", d.quote().ndemGross());
        out.put("fee", d.quote().fee());
        out.put("ndemNet", d.quote().ndemNet());
        out.put("replayed", d.replayed());
        out.put("snapshot", snapshot(w));
        return ok(out);
    }

    // ---- réception et transfert ----

    @PostMapping("/api/v1/wallet/receive-code")
    public ResponseEntity<Map<String, Object>> receiveCode(@RequestHeader(name = "Authorization", required = false) String authorization, @RequestBody(required = false) JsonNode body) {
        Who w = who(authorization, text(object(body), "deviceCode"), true);
        ReceiveCodeService.Created c = codes.create(w.code());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", c.display());
        out.put("exp", c.exp());
        return ok(out);
    }

    @GetMapping("/api/v1/wallet/receive-code/{code}")
    public ResponseEntity<Map<String, Object>> lookup(@RequestHeader(name = "Authorization", required = false) String authorization, @PathVariable("code") String code,
                                                      @RequestParam(name = "deviceCode", required = false) String deviceCode) {
        Who w = who(authorization, deviceCode, false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recipient", codes.lookup(w.code(), code));
        return ok(out);
    }

    @PostMapping("/api/v1/wallet/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestHeader(name = "Authorization", required = false) String authorization, @RequestBody(required = false) JsonNode body) {
        JsonNode b = object(body);
        Who w = who(authorization, text(b, "deviceCode"), true);
        Currency cur = currency(b);
        long amt = integer(b, "amt");
        TransferService.Done d = transfers.transfer(w.code(), w.row(), text(b, "code"), cur, amt, text(b, "idem"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("replayed", d.replayed());
        out.put("cur", cur.name());
        out.put("amt", amt);
        out.put("to", d.to());
        out.put("snapshot", snapshot(w));
        return ok(out);
    }

    // ---- administration : rendu des blocages échus de tous les titulaires (appelable par la réconciliation) ----

    /** Jeton d'administration (comme toutes les routes {@code /api/v1/admin/**}) : rend les blocages échus de TOUTES les TV ; idempotent. */
    @PostMapping("/api/v1/admin/wallet/escrow-expiry")
    public ResponseEntity<Map<String, Object>> expireAll() {
        List<String> done = expiry.refundDue(null, clock.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("refunded", done.size());
        out.put("eids", done);
        return ok(out);
    }
}
