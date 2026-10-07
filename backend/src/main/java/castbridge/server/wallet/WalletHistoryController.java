package castbridge.server.wallet;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.WalletPolicy;
import castbridge.server.wallet.core.WalletReason;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/wallet/history?before=} (50 lignes, libellés français, contrepartie masquée « TV …4F2Q ») et {@code GET /api/v1/wallet/policy} (affichage : taux, frais, bornes de
 * mise, plafonds, interrupteurs). Jeton d'appareil ; l'historique est celui de l'identité liée à cet appareil.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@WalletModuleConfig.Enabled
public class WalletHistoryController {
    static final int PAGE = 50;
    private final DeviceService devices;
    private final SnapshotSigner signer;
    private final WalletRepository repo;
    private final WalletPolicyService policies;

    public WalletHistoryController(DeviceService devices, SnapshotSigner signer, WalletRepository repo, WalletPolicyService policies) {
        this.devices = devices;
        this.signer = signer;
        this.repo = repo;
        this.policies = policies;
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(@RequestHeader(name = "Authorization", required = false) String authorization, @RequestParam(required = false) Long before) {
        Device d = WalletSyncController.device(devices, signer, authorization);
        String identity = repo.identityOfDevice(d.id).orElseThrow(() -> new LedgerException(WalletReason.ACTIVATE));
        List<WalletRepository.HistoryLine> lines = repo.history(identity, PAGE, before);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lines", lines(lines));
        out.put("next", lines.size() == PAGE ? lines.get(lines.size() - 1).id() : null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    @GetMapping("/policy")
    public ResponseEntity<Map<String, Object>> policy(@RequestHeader(name = "Authorization", required = false) String authorization) {
        WalletSyncController.device(devices, signer, authorization);
        WalletPolicy p = policies.get();
        WalletPolicyService.Switches s = policies.switches();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rate", p.rate());
        out.put("reverseFeeBp", p.reverseFeeBp());
        out.put("stake", Map.of("NDEM", Map.of("min", p.stakeMinNdem(), "max", p.stakeMaxNdem()), "MBOKO", Map.of("min", p.stakeMinMboko(), "max", p.stakeMaxMboko())));
        out.put("transferDailyCap", Map.of("NDEM", p.transferCapNdem(), "MBOKO", p.transferCapMboko()));
        Map<String, Object> sw = new LinkedHashMap<>();
        sw.put("stakesNdem", s.stakesNdem());
        sw.put("stakesMboko", s.stakesMboko());
        sw.put("transfer", s.transfer());
        sw.put("convert", s.convert());
        sw.put("vouchers", s.vouchers());
        out.put("switches", sw);
        // jeux misés (échecs en ligne) : ce que la TV AFFICHE (échelle de mises, frais, plafonds de parties gagnées) ; le serveur reste l'autorité de chaque blocage
        Map<String, Object> games = new LinkedHashMap<>();
        for (String g : WalletPolicyService.GAMES.stream().sorted().toList()) {
            policies.game(g).ifPresent(gp -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("enabled", gp.enabled());
                m.put("stakes", Map.of("NDEM", gp.scaleNdem(), "MBOKO", gp.scaleMboko()));
                m.put("feeBp", gp.feeBp());
                m.put("winCaps", Map.of("day", gp.capDay(), "week", gp.capWeek(), "month", gp.capMonth()));
                m.put("seats", 1);
                m.put("trialStakes", false);
                games.put(g, m);
            });
        }
        out.put("games", games);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(out);
    }

    // ---- lignes ----

    static List<Map<String, Object>> lines(List<WalletRepository.HistoryLine> lines) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WalletRepository.HistoryLine l : lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id());
            m.put("kind", l.kind());
            m.put("currency", l.currency());
            m.put("amount", l.amount());
            m.put("at", l.at().toEpochMilli());
            m.put("label", label(l));
            m.put("counterparty", l.counterparty() == null ? null : mask(l.counterparty()));
            out.add(m);
        }
        return out;
    }

    /** Libellé français d'une ligne (jamais un texte saisi par un tiers). */
    static String label(WalletRepository.HistoryLine l) {
        return switch (l.kind()) {
            case "GRANT" -> l.idemKey().endsWith(":open-unlimited") ? "Ouverture illimitée" : "Attribution mensuelle";
            case "CONVERT" -> "Conversion de jetons";
            case "TRANSFER" -> l.amount() < 0 ? "Transfert envoyé" : "Transfert reçu";
            case "ESCROW_LOCK" -> "Mise bloquée";
            case "SETTLE" -> "Règlement de partie";
            case "ESCROW_REFUND" -> "Mise rendue";
            case "VOUCHER" -> "Bon utilisé";
            case "ADJUST" -> l.amount() < 0 ? "Reprise de l'administrateur" : "Don de l'administrateur";
            default -> "Opération";
        };
    }

    /** « TV …4F2Q » : les quatre derniers caractères du code, jamais le code entier. */
    static String mask(String identity) {
        String compact = identity.replace("-", "");
        return "TV …" + compact.substring(Math.max(0, compact.length() - 4));
    }
}
