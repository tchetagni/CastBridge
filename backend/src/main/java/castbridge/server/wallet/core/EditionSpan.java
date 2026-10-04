package castbridge.server.wallet.core;

import java.time.Instant;

/** Intervalle {@code [start, endExclusive)} pendant lequel une activation valide de l'identité donnait cette édition ({@code endExclusive = null} : sans fin). Une révocation à {@code t} ferme l'intervalle à {@code t}. */
public record EditionSpan(Edition edition, Instant start, Instant endExclusive) {
    public EditionSpan {
        if (edition == null || start == null) throw new IllegalArgumentException("Intervalle d'édition incomplet");
        if (endExclusive != null && !endExclusive.isAfter(start)) throw new IllegalArgumentException("La fin d'un intervalle d'édition doit suivre son début");
    }

    public boolean covers(Instant t) { return !t.isBefore(start) && (endExclusive == null || t.isBefore(endExclusive)); }
}
