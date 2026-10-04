package castbridge.server.wallet;

import castbridge.server.wallet.LicenseFacts.LicenseView;
import castbridge.server.wallet.LicenseFacts.StateEvent;
import castbridge.server.wallet.WalletRepository.SpanRow;
import castbridge.server.wallet.core.Edition;
import castbridge.server.wallet.core.EditionSpan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Les intervalles pendant lesquels une licence était ACTIVE, FIGÉS à la première observation (audit H1). Conception § 1.2 : « une tranche est due si, au début de sa période, une licence
 * ACTIVE couvrait cet instant » ; une suspension ou une révocation ferme l'intervalle, une reprise en ouvre un NOUVEAU : aucun rattrapage des périodes de suspension.
 * <p>Source des dates, dans l'ordre : (1) le journal d'audit des licences (suspension, reprise, révocation, échéance, prolongation : dates exactes, même si le portefeuille n'a rien vu) ;
 * (2) à défaut, {@code min(updated_at, maintenant)} la PREMIÈRE fois que le portefeuille voit l'état, puis la date est figée et ne glisse plus jamais (une modification ultérieure du délai de
 * grâce, des postes ou de la fin ne la déplace pas). Une première lecture tardive d'une licence déjà suspendue ET modifiée depuis, sans journal, peut surestimer la fin : limite connue.
 */
public class LicenseSpanBook {
    private static final java.util.Set<String> CLOSING = java.util.Set.of("LICENSE_SUSPEND", "LICENSE_REVOKE", "LICENSE_EXPIRE");
    private static final java.util.Set<String> OPENING = java.util.Set.of("LICENSE_RESUME", "LICENSE_EXTEND");

    private final WalletRepository repo;
    private final LicenseFacts facts;

    public LicenseSpanBook(WalletRepository repo, LicenseFacts facts) {
        this.repo = repo;
        this.facts = facts;
    }

    private static final class Span {
        final Instant start;
        Instant end;
        final boolean stored;
        boolean closedNow;

        Span(Instant start, Instant end, boolean stored) {
            this.start = start;
            this.end = end;
            this.stored = stored;
        }
    }

    /**
     * Les intervalles ACTIFS de la licence ({@code Edition.UNLIMITED} si elle n'a pas de fin, sinon {@code PRODUCTION}), bornés par la fin de la licence et par la libération du poste.
     * {@code persist} = figer ce qui vient d'être décidé (écriture) ; faux = simple lecture (identité non prouvée à ce contact).
     */
    public List<EditionSpan> active(LicenseView l, Instant now, boolean persist) {
        List<Span> spans = new ArrayList<>();
        for (SpanRow r : repo.licenseSpans(l.licenseId())) spans.add(new Span(r.start(), r.end(), true));
        if (spans.isEmpty()) spans.add(new Span(l.startAt(), null, false));
        // 1. journal d'audit : dates exactes des changements d'état arrivés depuis le dernier intervalle
        List<StateEvent> events = facts.history(l.licenseId());
        for (StateEvent e : events) {
            if (e.at().isAfter(now)) continue;
            Span last = spans.get(spans.size() - 1);
            if (last.end == null && CLOSING.contains(e.action()) && e.at().isAfter(last.start)) {
                last.end = e.at();
                last.closedNow = last.stored;
            } else if (last.end != null && OPENING.contains(e.action()) && !e.at().isBefore(last.end)) {
                spans.add(new Span(e.at(), null, false));
            }
        }
        // 2. l'état courant ; à défaut de journal, première observation (updated_at, jamais plus tard que maintenant)
        Span last = spans.get(spans.size() - 1);
        Instant observed = l.updatedAt().isAfter(now) ? now : l.updatedAt();
        switch (l.state()) {
            case ACTIVE -> {
                if (last.end != null) spans.add(new Span(observed.isBefore(last.end) ? last.end : observed, null, false));
            }
            case SUSPENDED, REVOKED -> {
                if (last.end == null) {
                    last.end = observed.isBefore(last.start) ? last.start : observed;
                    last.closedNow = last.stored;
                }
            }
            case EXPIRED -> {
                if (last.end == null) {
                    Instant e = l.endAt() != null ? l.endAt() : observed;
                    last.end = e.isBefore(last.start) ? last.start : e;
                    last.closedNow = last.stored;
                }
            }
        }
        if (persist) {
            for (Span s : spans) {
                if (!s.stored) repo.insertLicenseSpan(l.licenseId(), s.start, s.end);
                else if (s.closedNow) repo.closeLicenseSpan(l.licenseId(), s.start, s.end);
            }
        }
        // 3. bornes : fin de la licence, libération du poste
        Edition ed = l.endAt() == null ? Edition.UNLIMITED : Edition.PRODUCTION;
        List<EditionSpan> out = new ArrayList<>();
        for (Span s : spans) {
            Instant end = s.end;
            if (l.endAt() != null && (end == null || l.endAt().isBefore(end))) end = l.endAt();
            if (l.seatReleasedAt() != null && (end == null || l.seatReleasedAt().isBefore(end))) end = l.seatReleasedAt();
            if (end == null || end.isAfter(s.start)) out.add(new EditionSpan(ed, s.start, end));
        }
        return out;
    }
}
