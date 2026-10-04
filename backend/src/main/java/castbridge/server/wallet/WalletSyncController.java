package castbridge.server.wallet;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.InstallKeyFingerprint;
import castbridge.server.licenses.ReportedActivationRegistrar;
import castbridge.server.licenses.WireActivation;
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
import org.springframework.beans.factory.annotation.Value;
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
    static final String REGISTRATION_REVIEW_TEXT = "Activation en vérification au serveur : jetons de production à venir";
    static final String SEAT_OVER_QUOTA_TEXT = "Licence déjà utilisée sur une autre TV : contactez votre point focal";
    static final String NO_INSTALL_KEY_TEXT = "Activation sans clé d'installation : décision du propriétaire";
    static final String BINDING_TAKEN_OVER = "BINDING_TAKEN_OVER";
    static final String CATCHUP_HELD_TEXT = "Jetons de votre activation en vérification";

    private final DeviceService devices;
    private final EditionReader reader;
    private final GrantService grants;
    private final JdbcLedger ledger;
    private final WalletRepository repo;
    private final WalletPolicyService policies;
    private final SnapshotSigner signer;
    private final WalletModuleConfig.WalletClock clock;
    private final ObjectProvider<SyncContributor> contributors;
    private final ObjectProvider<ReportedActivationRegistrar> registrar;
    private final AdminAccess access;
    @Value("${castbridge.wallet.require-bind-proof:true}") private boolean requireBindProof;

    public WalletSyncController(DeviceService devices, EditionReader reader, GrantService grants, JdbcLedger ledger, WalletRepository repo, WalletPolicyService policies,
                                SnapshotSigner signer, WalletModuleConfig.WalletClock clock, ObjectProvider<SyncContributor> contributors,
                                ObjectProvider<ReportedActivationRegistrar> registrar, AdminAccess access) {
        this.devices = devices;
        this.reader = reader;
        this.grants = grants;
        this.ledger = ledger;
        this.repo = repo;
        this.policies = policies;
        this.signer = signer;
        this.clock = clock;
        this.contributors = contributors;
        this.registrar = registrar;
        this.access = access;
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
        // preuve de possession de la clé d'installation (audit M5) : calculée AVANT la lecture des activations, car une activation qui porte SA clé d'installation (droit « ik », audit w23-05 HIGH-1) n'est
        // lue QUE pour la TV qui la détient : la copie d'un jeton ne donne ni identité de portefeuille ni licence à un autre appareil
        BindProof.Proof proof = BindProof.parse(body.path("bind"));
        boolean proven = BindProof.valid(proof, code, d.publicId, now);
        EditionReader.Reading reading = reader.read(code, proven ? withoutForeignInstallKeys(activations, proof.key()) : activations, now);
        List<Map<String, String>> notices = new ArrayList<>();
        GrantService.Standing st;
        boolean boundOther;
        int held = 0;
        List<ReportedActivationRegistrar.Registration> registrations = List.of();
        boolean accepted = reading.accepted();
        if (accepted) {
            // liaison à l'appareil : preuve de possession de la clé d'installation (audit M5) ; une identité libérée par l'administrateur se lie au premier appareil qui prouve
            WalletRepository.Identity known = repo.identity(code).orElse(null);
            // une activation signée avec `ik`, PROUVÉE par la TV qui détient cette clé, l'emporte sur une liaison prise par un autre appareil ou une autre clé (voleur avec un jeton sans `ik`) :
            // l'identité revient à la vraie TV, auditée, alerte douce au propriétaire, aucun paiement perdu (second audit w23-05, HIGH-A)
            if (known != null && proven && reading.installKeys().contains(proof.key()) && repo.takeOver(code, d.id, proof.key())) {
                access.system("WALLET_REBIND_BY_IK", code, "activation signée avec la clé d'installation de cette TV, prouvée", Map.of("fromDevice", known.apiDeviceId(), "toDevice", d.id));
                access.system("REGISTRATION_ALERT", code, BINDING_TAKEN_OVER, Map.of("level", "soft", "device", DeviceIdentity.masked(code)));
                log.warn("wallet : liaison reprise par l'activation signée de la vraie TV ({})", DeviceIdentity.masked(code));
                known = repo.identity(code).orElse(null);
            }
            if (known == null || known.apiDeviceId() == 0) {
                if (requireBindProof && !proven) throw bindProofRequired();
                // identité réaffectée par le propriétaire : elle n'accepte QUE la clé d'installation dont il a saisi l'empreinte (lue sur l'écran de la TV) ; ni le premier appareil venu, ni un voleur
                if (known != null && known.expectedInstallFp() != null && !(proven && known.expectedInstallFp().equals(InstallKeyFingerprint.canonicalOf(proof.key())))) throw bindKeyExpected();
            } else if (known.apiDeviceId() == d.id) {
                if (known.installPub() != null ? !proven || !proof.key().equals(known.installPub()) : requireBindProof && !proven) throw bindProofRequired();
            }
            policies.checkWrite(code);
            if (known != null && known.apiDeviceId() == 0 && (proven || !requireBindProof)) repo.bindIfFree(code, d.id);
            // la clé d'installation est retenue AVANT le calcul des tranches : une licence enregistrée avec `ik` ne paie que l'identité dont la clé est celle-ci
            if (known == null && (proven || !requireBindProof)) repo.openIdentity(code, d.id, now);
            if (proven) repo.adoptInstallKey(code, d.id, proof.key());
            // une activation de production vérifiée, présentée avec la preuve de possession, ouvre (ou rattache) la licence et le poste AVANT le calcul des tranches (W23-05a) ; jamais sans preuve
            ReportedActivationRegistrar reg = registrar.getIfAvailable();
            if (reg != null && reading.productionKey()) {
                List<ReportedActivationRegistrar.Presented> items = new ArrayList<>();
                for (String a : activations) items.add(new ReportedActivationRegistrar.Presented(a, code, proven ? proof.key() : null, proven));
                registrations = reg.registerAll(items, ReportedActivationRegistrar.Via.WALLET, now);
            }
            GrantService.Outcome o = grants.sync(code, d.id, reading, now);
            st = o.standing();
            boundOther = o.boundOther();
            held = o.held();
        } else {
            // aucune activation acceptée à CE contact : on n'ouvre rien, on n'inscrit rien ; l'identité n'est lue que par l'appareil API qui la porte (audit H3), jamais par un autre
            WalletReason why = reading.clockDoubt() ? WalletReason.CLOCK : WalletReason.ACTIVATE;
            WalletRepository.Identity known = repo.identity(code).orElseThrow(() -> new LedgerException(why));
            if (known.apiDeviceId() != d.id) throw new LedgerException(why);
            st = grants.readOnly(code, reading, now);
            boundOther = false;
            notices.add(notice(why.name(), why.text()));
        }
        if (st.licensePending()) notices.add(notice("LICENSE_PENDING", LICENSE_PENDING_TEXT));
        notices.addAll(registrationNotices(registrations, hasLicence(st)));
        if (held > 0) notices.add(notice("CATCHUP_HELD", CATCHUP_HELD_TEXT));
        if (boundOther) notices.add(notice(WalletReason.BOUND_OTHER_TV.name(), WalletReason.BOUND_OTHER_TV.text()));

        WalletRepository.Identity row = repo.identity(code).orElseThrow();
        WalletPolicyService.Switches sw = policies.switches();
        // une mise exige une activation acceptée à CE contact, par l'appareil lié, sur une identité non gelée
        boolean live = accepted && !boundOther && !row.frozen();
        boolean stakesN = live && sw.stakesNdem() && StakeRules.mayStake(st.edition(), Currency.NDEM, st.grace());
        boolean stakesM = live && sw.stakesMboko() && StakeRules.mayStake(st.edition(), Currency.MBOKO, st.grace());
        // seq (audit M4) : change quand une écriture OU un champ signé change ; soldes et seq lus par UNE instruction
        repo.bumpIfChanged(code, digest(st.ed(), row.frozen(), stakesN, stakesM));
        WalletRepository.SnapshotRead bal = repo.snapshotRead(code);
        String snapshot = signer.sign(new SnapshotSigner.Snapshot(code, st.ed(), bal.ndem(), bal.ndemLocked(), bal.mboko(), bal.mbokoLocked(), bal.seq(), now.toEpochMilli(), row.frozen(), stakesN, stakesM));

        // les contributeurs ne servent que sur une identité prouvée par une activation de ce contact (contrat de SyncContributor)
        Map<String, Object> contributions = new LinkedHashMap<>();
        SyncContributor.SyncContext ctx = new SyncContributor.SyncContext(code, now, body, st);
        if (accepted) contributors.orderedStream().forEach(c -> {
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
        List<Map<String, Object>> regs = new ArrayList<>();
        for (ReportedActivationRegistrar.Registration r : registrations) {
            if (r.status() == ReportedActivationRegistrar.Status.IGNORED) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", r.status().name());
            m.put("reason", r.reason());
            m.put("installTimeUnproven", r.installTimeUnproven());
            regs.add(m);
        }
        if (!regs.isEmpty()) out.put("registration", regs);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    /** Jamais de refus silencieux : chaque activation de production qui n'a pas ouvert de licence dit pourquoi (motif fermé de {@link ReportedActivationRegistrar}, texte de la conception § 5.4). */
    static List<Map<String, String>> registrationNotices(List<ReportedActivationRegistrar.Registration> registrations, boolean tvHasLicence) {
        List<Map<String, String>> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ReportedActivationRegistrar.Registration r : registrations) {
            if (r.registered() || r.status() == ReportedActivationRegistrar.Status.IGNORED || r.reason() == null) continue;
            // un refus DÉFINITIF (clé inconnue, clone, licence révoquée, refus du propriétaire…) n'annonce aucun jeton à venir : son motif est dans « registration » (audit LOW-4)
            if (r.status() == ReportedActivationRegistrar.Status.REFUSED) continue;
            boolean quota = "OVER_QUOTA".equals(r.reason()) || "TRANSFER_CAP".equals(r.reason());
            boolean noKey = ReportedActivationRegistrar.NO_INSTALL_KEY.equals(r.reason());
            // une TV déjà payée par une autre licence n'attend pas de jetons : seul l'avis « sans clé d'installation » (décision du propriétaire) reste visible
            if (tvHasLicence && !quota && !noKey) continue;
            String reason = quota ? "SEAT_OVER_QUOTA" : "REGISTRATION_REVIEW";
            if (!seen.add(reason + "|" + r.reason())) continue;
            Map<String, String> n = notice(reason, quota ? SEAT_OVER_QUOTA_TEXT : noKey ? NO_INSTALL_KEY_TEXT : REGISTRATION_REVIEW_TEXT);
            n.put("detail", r.reason());
            out.add(n);
        }
        return out;
    }

    private static boolean hasLicence(GrantService.Standing st) { return st.licenseState() != null && java.util.Set.of("ACTIVE", "SUSPENDED", "EXPIRED", "REVOKED").contains(st.licenseState()); }

    /**
     * Retire des activations lues celles de PRODUCTION qui portent une clé d'installation (droit {@code ik}) autre que celle prouvée à ce contact : c'est le jeton d'une autre TV. Une activation sans
     * {@code ik} (toutes celles d'avant le correctif), un essai ou un jeton illisible sont laissés tels quels (le lecteur décide).
     */
    static List<String> withoutForeignInstallKeys(List<String> activations, String provenKey) {
        List<String> out = new ArrayList<>();
        for (String a : activations) {
            WireActivation.Decoded dec = a.length() > EditionReader.MAX_TOKEN_LENGTH ? null : WireActivation.decode(a.trim());
            if (dec != null && dec.fields().kind().equals("production")) {
                String ik;
                try {
                    ik = WireActivation.installKeyOf(dec.fields().rights());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (ik != null && !ik.equals(provenKey)) continue;
            }
            out.add(a);
        }
        return out;
    }

    private static ApiException bindKeyExpected() {
        return new ApiException(HttpStatus.CONFLICT, "Compte réaffecté : seule la TV dont l'empreinte de clé d'installation a été saisie par le propriétaire peut le reprendre", List.of("BIND_EXPECTED"));
    }

    private static ApiException bindProofRequired() {
        return new ApiException(HttpStatus.CONFLICT, "Preuve de possession de la TV manquante ou invalide : signez le contact avec la clé d'installation de la TV", List.of("BIND_PROOF"));
    }

    private static String digest(String ed, boolean frozen, boolean stakesN, boolean stakesM) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((ed + "|" + frozen + "|" + stakesN + "|" + stakesM).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> notice(String reason, String text) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("reason", reason);
        m.put("text", text);
        return m;
    }
}
