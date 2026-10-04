package castbridge.server.wallet;

import castbridge.server.wallet.LicenseFacts.LicenseView;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.EditionSpan;
import castbridge.server.wallet.core.GrantSchedule;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.WalletPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Matérialise les tranches dues à chaque contact (conception W22 § 1.2) : essai lu dans {@code cbx1} ({@link EditionReader}), production lue dans la LICENCE du serveur
 * ({@link LicenseFacts}) ; échéancier du cœur ({@link GrantSchedule}) ; chaque tranche porte sa clé d'idempotence (aucune perdue par une TV restée hors ligne, aucune doublée par deux
 * synchronisations). La TV ne fait que PROUVER son identité : jamais un montant.
 * <p>Règles de l'audit Opus (correctifs C1, H1, M2) et décisions du propriétaire du 2026-10-04 :
 * <ul>
 *   <li>une licence est liée à SA TV et ne se transfère jamais : la clé d'une tranche de licence est {@code grant:lic:<licence>:<monnaie>:p<k>} (licence, période), sur la grille de 30 jours
 *       ancrée à {@code start_at} de la licence, indépendante du poste et de l'appareil ; la première identité qui reçoit une tranche d'une licence la RÉCLAME pour toujours
 *       ({@code wallet_license_claim}) ; un changement de code d'appareil sur le poste, une libération puis réattribution ou un deuxième poste ne créent AUCUNE tranche pour une autre TV
 *       (ni les périodes déjà versées, ni les futures) tant que le propriétaire n'a pas décidé une autre règle ;</li>
 *   <li>une licence ILLIMITÉE verse d'abord 5 000 NDEM + 50 MBOKO (ouverture) ; sa première tranche mensuelle n'est due qu'à ancre + 1 période (jamais de {@code p0}) ;</li>
 *   <li>les intervalles ACTIFS d'une licence sont figés ({@link LicenseSpanBook}) : une suspension coupe, une reprise ouvre un NOUVEL intervalle, aucun rattrapage ; une licence révoquée reste
 *       lisible (ses postes libérés par la révocation sont lus) : ses tranches passées non versées restent acquises ;</li>
 *   <li>l'essai garde sa grille d'identité ({@code grant:<identité>:…}, ancre figée à la première lecture) et ne paie pas une période qu'une licence de cette identité couvre déjà.</li>
 * </ul>
 */
@Service
@WalletModuleConfig.Enabled
public class GrantService {
    private static final Pattern PERIOD = Pattern.compile(":p(\\d+)$");
    private static final String OPEN_SUFFIX = ":open-unlimited";

    private final WalletRepository repo;
    private final JdbcLedger ledger;
    private final WalletPolicyService policies;
    private final LicenseFacts licenses;
    private final LicenseSpanBook spanBook;

    public GrantService(WalletRepository repo, JdbcLedger ledger, WalletPolicyService policies, LicenseFacts licenses) {
        this.repo = repo;
        this.ledger = ledger;
        this.policies = policies;
        this.licenses = licenses;
        this.spanBook = new LicenseSpanBook(repo, licenses);
    }

    /**
     * La situation d'une identité à l'instant {@code now} : intervalles d'édition (essai de {@code cbx1}, licences, super), édition, grâce, état de la licence.
     *
     * @param ed           étiquette de {@code cbw1} : TRIAL, PROD, UNLIMITED ou NONE (les seules valeurs que le lecteur Kotlin de w22-03 accepte ; GRÂCE y est PROD, SUPER y est UNLIMITED)
     * @param licenseState ACTIVE, SUSPENDED, REVOKED, EXPIRED (état de la licence retenue) ; PENDING (activation de production sans licence connue) ; OTHER_TV (la licence de ce poste paie une autre TV) ; NONE
     */
    public record Standing(Edition edition, boolean grace, boolean licensePending, String ed, String licenseState, Instant anchor, List<EditionSpan> spans) {
        Standing withAnchor(Instant a) { return new Standing(edition, grace, licensePending, ed, licenseState, a, spans); }

        Standing withLicense(String state, boolean pending) { return new Standing(edition, grace, pending, ed, state, anchor, spans); }
    }

    /** @param granted nombre de tranches NOUVELLES inscrites par cet appel ; @param boundOther l'identité est liée à un autre appareil API (lecture permise, sorties refusées plus tard) */
    public record Outcome(String identity, Standing standing, int granted, boolean boundOther, int held) {
        public Outcome(String identity, Standing standing, int granted, boolean boundOther) { this(identity, standing, granted, boundOther, 0); }
    }

    /** Une licence lue, avec ses intervalles ACTIFS figés. */
    record Resolved(LicenseView view, List<EditionSpan> active) {}

    /** Calcul pur de la situation (sans base) : testable seul. */
    static Standing standing(EditionReader.Reading reading, List<Resolved> licenses, Instant now) {
        List<EditionSpan> spans = new ArrayList<>(reading.spans());
        Instant firstLicense = null;
        boolean grace = false;
        LicenseView shown = null;
        for (Resolved r : licenses) {
            LicenseView l = r.view();
            if (firstLicense == null || l.startAt().isBefore(firstLicense)) firstLicense = l.startAt();
            spans.addAll(r.active());
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
        List<LicenseView> raw = licenses.forDevice(identity);
        List<Resolved> mine = new ArrayList<>();
        boolean foreign = false;
        for (LicenseView v : raw) {
            String holder = repo.licenseHolder(v.licenseId()).orElse(identity);
            if (holder.equals(identity)) mine.add(new Resolved(v, clip(spanBook.active(v, now, false), licenses.windows(v.licenseId()))));
            else foreign = true;
        }
        Standing st = standing(reading, mine, now);
        if (mine.isEmpty() && foreign) st = st.withLicense("OTHER_TV", false);
        Instant stored = repo.identity(identity).map(WalletRepository.Identity::anchorAt).orElse(null);
        return st.withAnchor(stored != null ? stored : st.anchor());
    }

    /** Ouvre l'identité si besoin (liaison à l'appareil API) puis inscrit toutes les tranches dues jusqu'à {@code now}, chacune une seule fois. */
    public Outcome sync(String identity, long apiDeviceId, EditionReader.Reading reading, Instant now) {
        repo.openIdentity(identity, apiDeviceId, now);
        WalletRepository.Identity row = repo.identity(identity).orElseThrow();
        List<Resolved> mine = new ArrayList<>();
        boolean foreign = false;
        for (LicenseView v : licenses.forDevice(identity)) {
            if (repo.claimLicense(v.licenseId(), identity, now).equals(identity)) mine.add(new Resolved(v, clip(spanBook.active(v, now, true), licenses.windows(v.licenseId()))));
            else foreign = true;   // la licence de ce poste paie une autre TV : rien pour celle-ci
        }
        Standing st = standing(reading, mine, now);
        if (mine.isEmpty() && foreign) st = st.withLicense("OTHER_TV", false);
        Instant anchor = row.anchorAt();
        if (anchor == null && st.anchor() != null) {
            repo.setAnchorIfAbsent(identity, st.anchor());
            anchor = repo.identity(identity).orElseThrow().anchorAt();
        }
        int granted = 0, held = 0;
        WalletPolicy policy = policies.get();
        List<EditionSpan> superSpans = reading.spans().stream().filter(s -> s.edition() == Edition.SUPER).toList();
        // essai : grille d'identité, jamais une période qu'une licence de cette identité couvre déjà
        if (anchor != null) {
            Set<String> already = repo.grantKeys(identity);
            for (GrantSchedule.Due due : GrantSchedule.due(identity, policy, anchor, reading.spans(), already, now)) {
                if (coveredByLicence(mine, periodStart(due.key(), anchor, policy))) continue;
                if (!ledger.post(due.toTxn(identity), "system", identity, null).replayed()) granted++;
            }
        }
        // licences : grille (licence, période) ancrée à start_at, une identité par licence ; une période d'une TV n'est payée qu'UNE fois par monnaie, quelle que soit la licence (audit HIGH-3)
        final long periodMs = Duration.ofDays(policy.periodDays()).toMillis();
        Set<String> claimed = new java.util.HashSet<>(repo.periodClaims(identity));
        if (anchor != null) backfillClaims(identity, mine, anchor, periodMs, claimed, now);
        for (int i = 0; i < mine.size(); i++) {
            Resolved r = mine.get(i);
            String id = r.view().licenseId();
            List<EditionSpan> spans = new ArrayList<>(r.active());
            spans.addAll(superSpans);
            Set<String> already = repo.grantKeysWithPrefix("grant:lic:" + id + ":");
            // licence connue seulement par la notification d'une activation hors ligne : limite de rattrapage (W23-B § 5.2) ; les autres licences n'ont aucune limite, comme avant
            LicenseFacts.Notification note = licenses.notification(id);
            Set<String> heldPeriods = new java.util.HashSet<>();
            for (GrantSchedule.Due due : GrantSchedule.due("lic:" + id, policy, r.view().startAt(), spans, already, now)) {
                boolean open = due.key().endsWith(OPEN_SUFFIX);
                Instant periodStart = r.view().startAt();
                String cur = "OPEN";
                long slot = 0;
                if (open) {
                    if (repo.identity(identity).orElseThrow().openedUnlimited()) continue;   // une seule ouverture par identité, quelle que soit la licence
                } else {
                    Matcher m = PERIOD.matcher(due.key());
                    if (!m.find()) continue;
                    int k = Integer.parseInt(m.group(1));
                    if (k == 0 && r.view().endAt() == null) continue;                            // « 5000 et 50 d'abord » : la première tranche d'une illimitée est due à ancre + 1 période
                    periodStart = r.view().startAt().plus(Duration.ofDays(policy.periodDays()).multipliedBy(k));
                    cur = due.amounts().get(0).currency().name();
                    slot = slotOf(periodStart, anchor, periodMs);
                }
                if (claimed.contains(cur + "|" + slot)) continue;                                // déjà payée à cette TV (par cette licence ou une autre) : jamais deux fois
                if (note != null) {
                    CatchUpPolicy.Verdict verdict = CatchUpPolicy.judge(periodStart, note.firstNotifiedAt(), note.declared());
                    if (verdict != CatchUpPolicy.Verdict.PAY) {
                        if (verdict == CatchUpPolicy.Verdict.HELD) heldPeriods.add(open ? "open" : "p" + periodStart.toEpochMilli());
                        continue;
                    }
                }
                final String claimCur = cur;
                final long claimSlot = slot;
                final Instant claimStart = periodStart;
                try {
                    // la case (identité, monnaie, période) est réclamée DANS la transaction de la pose : un second versement de la même période, sous une autre licence, est refusé même en parallèle
                    if (!ledger.post(due.toTxn(identity), "system", identity, null, j -> claimInTx(j, identity, claimCur, claimSlot, claimStart, due.key(), now)).replayed()) granted++;
                } catch (PeriodAlreadyPaid e) {
                    claimed.add(cur + "|" + slot);
                    continue;
                }
                claimed.add(cur + "|" + slot);
                if (open) repo.markOpenedUnlimited(identity);
            }
            held += heldPeriods.size();
        }
        Instant trialEnd = reading.spans().stream().filter(sp -> sp.edition() == Edition.TRIAL && sp.endExclusive() != null).map(EditionSpan::endExclusive).max(Instant::compareTo).orElse(null);
        repo.touchSync(identity, st.ed(), reading.superKey(), trialEnd, now);
        return new Outcome(identity, st.withAnchor(anchor), granted, row.apiDeviceId() != apiDeviceId, held);
    }

    private static Instant periodStart(String key, Instant anchor, WalletPolicy policy) {
        Matcher m = PERIOD.matcher(key);
        return m.find() ? anchor.plus(Duration.ofDays(policy.periodDays()).multipliedBy(Integer.parseInt(m.group(1)))) : null;
    }

    private static boolean coveredByLicence(List<Resolved> licences, Instant t) {
        if (t == null) return false;
        for (Resolved r : licences) for (EditionSpan s : r.active()) if (s.covers(t)) return true;
        return false;
    }

    /** Levée quand la case (identité, monnaie, période) a déjà été payée : la pose est annulée, rien n'est écrit. */
    static final class PeriodAlreadyPaid extends RuntimeException {
        PeriodAlreadyPaid() { super("période déjà payée", null, false, false); }
    }

    /** La case de la période : l'entier le plus proche de (début - ancre de l'identité) / durée d'une période. Deux licences décalées de moins d'une demi-période partagent la même case. */
    static long slotOf(Instant periodStart, Instant anchor, long periodMs) {
        long delta = periodStart.toEpochMilli() - anchor.toEpochMilli();
        return Math.floorDiv(2 * delta + periodMs, 2 * periodMs);
    }

    /** Dans la transaction de la pose, après les verrous et avant toute écriture : réclame la case, ou refuse si une autre pose l'a déjà réclamée. */
    private static void claimInTx(org.springframework.jdbc.core.JdbcTemplate j, String identity, String cur, long slot, Instant periodStart, String key, Instant now) {
        List<String> held = j.queryForList("SELECT idem_key FROM wallet_period_claim WHERE holder = ? AND cur = ? AND slot = ? FOR UPDATE", String.class, identity, cur, slot);
        if (!held.isEmpty()) {
            if (held.get(0).equals(key)) return;
            throw new PeriodAlreadyPaid();
        }
        j.update("INSERT INTO wallet_period_claim (holder, cur, slot, period_start, idem_key, claimed_at) VALUES (?,?,?,?,?,?)", identity, cur, slot, java.sql.Timestamp.from(periodStart), key, java.sql.Timestamp.from(now));
    }

    /** Les versements d'avant la table (clés {@code grant:lic:<licence>:<monnaie>:p<k>} et ouverture déjà posées) deviennent des cases : une seule fois, sans effet sur le grand livre. */
    private void backfillClaims(String identity, List<Resolved> mine, Instant anchor, long periodMs, Set<String> claimed, Instant now) {
        for (Resolved r : mine) {
            String prefix = "grant:lic:" + r.view().licenseId() + ":";
            for (String key : repo.grantKeysWithPrefix(prefix)) {
                String tail = key.substring(prefix.length());
                if (tail.equals("open-unlimited")) {
                    if (claimed.add("OPEN|0")) repo.backfillPeriodClaim(identity, "OPEN", 0, r.view().startAt(), key, now);
                    continue;
                }
                Matcher m = Pattern.compile("^(NDEM|MBOKO):p(\\d+)$").matcher(tail);
                if (!m.matches()) continue;
                Instant start = r.view().startAt().plusMillis(periodMs * Long.parseLong(m.group(2)));
                long slot = slotOf(start, anchor, periodMs);
                if (claimed.add(m.group(1) + "|" + slot)) repo.backfillPeriodClaim(identity, m.group(1), slot, start, key, now);
            }
        }
    }

    /** Restreint les intervalles actifs d'une licence aux fenêtres de ses clés signées ({@code windows} null = aucune restriction). */
    static List<EditionSpan> clip(List<EditionSpan> spans, List<LicenseFacts.Window> windows) {
        if (windows == null) return spans;
        List<EditionSpan> out = new ArrayList<>();
        for (EditionSpan s : spans) {
            for (LicenseFacts.Window w : windows) {
                Instant start = s.start().isAfter(w.from()) ? s.start() : w.from();
                Instant end = s.endExclusive() == null ? w.toExclusive() : (w.toExclusive() == null ? s.endExclusive() : (s.endExclusive().isBefore(w.toExclusive()) ? s.endExclusive() : w.toExclusive()));
                if (end == null || end.isAfter(start)) out.add(new EditionSpan(s.edition(), start, end));
            }
        }
        return out;
    }
}
