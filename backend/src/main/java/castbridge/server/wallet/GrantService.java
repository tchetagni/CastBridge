package castbridge.server.wallet;

import castbridge.server.wallet.LicenseFacts.LicenseView;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.EditionSpan;
import castbridge.server.wallet.core.GrantSchedule;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.WalletPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Matérialise les tranches dues à chaque contact (conception W22 § 1.2) : essai lu dans {@code cbx1} ({@link EditionReader}), production lue dans la LICENCE du serveur
 * ({@link LicenseFacts}) ; échéancier du cœur ({@link GrantSchedule}) ; chaque tranche porte sa clé d'idempotence (aucune perdue par une TV restée hors ligne, aucune doublée par deux
 * synchronisations). La TV ne fait que PROUVER son identité : jamais un montant. Choix à relire à l'audit : l'ANCRE se fige à la première lecture et ne bouge plus (les clés
 * {@code p<k>} en dépendent) ; une licence suspendue ou révoquée ferme son intervalle à {@code updated_at} (date de la mesure) ; une licence {@code TRIAL} est ignorée.
 */
@Service
@WalletModuleConfig.Enabled
public class GrantService {
    private final WalletRepository repo;
    private final JdbcLedger ledger;
    private final WalletPolicyService policies;
    private final LicenseFacts licenses;

    public GrantService(WalletRepository repo, JdbcLedger ledger, WalletPolicyService policies, LicenseFacts licenses) {
        this.repo = repo;
        this.ledger = ledger;
        this.policies = policies;
        this.licenses = licenses;
    }

    /**
     * La situation d'une identité à l'instant {@code now} : intervalles d'édition (essai de {@code cbx1}, licences, super), édition, grâce, état de la licence.
     *
     * @param ed           étiquette de {@code cbw1} : TRIAL, PROD, UNLIMITED ou NONE (les seules valeurs que le lecteur Kotlin de w22-03 accepte ; GRÂCE y est PROD, SUPER y est UNLIMITED)
     * @param licenseState ACTIVE, SUSPENDED, REVOKED, EXPIRED (état de la licence retenue) ; PENDING (activation de production sans licence connue) ; NONE
     */
    public record Standing(Edition edition, boolean grace, boolean licensePending, String ed, String licenseState, Instant anchor, List<EditionSpan> spans) {
        Standing withAnchor(Instant a) { return new Standing(edition, grace, licensePending, ed, licenseState, a, spans); }
    }

    /** @param granted nombre de tranches NOUVELLES inscrites par cet appel ; @param boundOther l'identité est liée à un autre appareil API (lecture permise, sorties refusées plus tard) */
    public record Outcome(String identity, Standing standing, int granted, boolean boundOther) {}

    /** Calcul pur de la situation (sans base) : testable seul. */
    public static Standing standing(EditionReader.Reading reading, List<LicenseView> licenses, Instant now) {
        List<EditionSpan> spans = new ArrayList<>(reading.spans());
        Instant firstLicense = null;
        boolean grace = false;
        LicenseView shown = null;
        for (LicenseView l : licenses) {
            if (firstLicense == null || l.startAt().isBefore(firstLicense)) firstLicense = l.startAt();
            Instant end = l.endAt();
            switch (l.state()) {
                case SUSPENDED, REVOKED -> end = end == null || l.updatedAt().isBefore(end) ? l.updatedAt() : end;   // les tranches futures cessent à la date de la mesure
                case EXPIRED -> end = end == null ? l.updatedAt() : end;
                default -> { }
            }
            if (end == null || end.isAfter(l.startAt())) spans.add(new EditionSpan(l.endAt() == null ? Edition.UNLIMITED : Edition.PRODUCTION, l.startAt(), end));
            if ((l.state() == LicenseFacts.State.ACTIVE || l.state() == LicenseFacts.State.EXPIRED) && l.endAt() != null && !now.isBefore(l.endAt())
                    && now.isBefore(l.endAt().plusSeconds(l.graceDays() * 86_400L))) grace = true;
            if (shown == null || (shown.state() != LicenseFacts.State.ACTIVE && (l.state() == LicenseFacts.State.ACTIVE || l.updatedAt().isAfter(shown.updatedAt())))) shown = l;
        }
        Instant anchor = reading.firstTrialStart();
        if (firstLicense != null && (anchor == null || firstLicense.isBefore(anchor))) anchor = firstLicense;
        Edition edition = editionAt(spans, now);
        boolean pending = reading.productionKey() && licenses.isEmpty();
        String ed = switch (edition) {
            case TRIAL -> "TRIAL";
            case PRODUCTION -> "PROD";
            case UNLIMITED, SUPER -> "UNLIMITED";
            case NONE -> grace ? "PROD" : "NONE";
        };
        return new Standing(edition, grace, pending, ed, shown != null ? shown.state().name() : pending ? "PENDING" : "NONE", anchor, List.copyOf(spans));
    }

    private static Edition editionAt(List<EditionSpan> spans, Instant t) {
        Edition best = Edition.NONE;
        for (EditionSpan s : spans) {
            if (!s.covers(t)) continue;
            if (s.edition() == Edition.SUPER) return Edition.SUPER;
            if (s.edition().rank() > best.rank()) best = s.edition();
        }
        return best;
    }

    /** Lecture seule : la situation actuelle d'une identité, sans rien inscrire (activation absente ou refusée à ce contact). */
    public Standing readOnly(String identity, EditionReader.Reading reading, Instant now) {
        Standing st = standing(reading, licenses.forDevice(identity), now);
        Instant stored = repo.identity(identity).map(WalletRepository.Identity::anchorAt).orElse(null);
        return st.withAnchor(stored != null ? stored : st.anchor());
    }

    /** Ouvre l'identité si besoin (liaison à l'appareil API) puis inscrit toutes les tranches dues jusqu'à {@code now}, chacune une seule fois. */
    public Outcome sync(String identity, long apiDeviceId, EditionReader.Reading reading, Instant now) {
        repo.openIdentity(identity, apiDeviceId, now);
        WalletRepository.Identity row = repo.identity(identity).orElseThrow();
        Standing st = standing(reading, licenses.forDevice(identity), now);
        Instant anchor = row.anchorAt();
        if (anchor == null && st.anchor() != null) {
            repo.setAnchorIfAbsent(identity, st.anchor());
            anchor = repo.identity(identity).orElseThrow().anchorAt();
        }
        int granted = 0;
        if (anchor != null) {
            WalletPolicy policy = policies.get();
            Set<String> already = repo.grantKeys(identity);
            for (GrantSchedule.Due due : GrantSchedule.due(identity, policy, anchor, st.spans(), already, now)) {
                Ledger.Posted p = ledger.post(due.toTxn(identity), "system", identity, null);
                if (!p.replayed()) granted++;
                if (due.key().equals(GrantSchedule.openKey(identity))) repo.markOpenedUnlimited(identity);
            }
        }
        repo.touchSync(identity, st.ed(), now);
        return new Outcome(identity, st.withAnchor(anchor), granted, row.apiDeviceId() != apiDeviceId);
    }
}
