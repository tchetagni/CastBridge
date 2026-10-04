package castbridge.server.activations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * The clock of the module. The system clock in production; the tests (and only they) fix it with {@link #set(Instant)} to play the grace periods (72 h, 7 days...)
 * without waiting. Every time the module writes is truncated to the millisecond, so that a hash never depends on the precision of the database.
 */
@Component
public class ActClock {
    private volatile Instant fixed;
    /** A row of a licence table younger than this is not final for the taps: a row of a smaller id may still be committed after it (audit M7). */
    static final java.time.Duration DEFAULT_TAP_LAG = java.time.Duration.ofSeconds(30);
    private volatile java.time.Duration tapLag = DEFAULT_TAP_LAG;

    public Instant now() { return (fixed != null ? fixed : Instant.now()).truncatedTo(ChronoUnit.MILLIS); }

    public long nowMs() { return now().toEpochMilli(); }

    /** Test seam: freezes the clock. */
    public void set(Instant t) { fixed = t; }

    /** Test seam: back to the system clock (and the default tap lag). */
    public void reset() {
        fixed = null;
        tapLag = DEFAULT_TAP_LAG;
    }

    public java.time.Duration tapLag() { return tapLag; }

    /** Test seam: the safety lag of the taps (zero for the tests that create a row and read it at once). */
    public void setTapLag(java.time.Duration d) { tapLag = d; }
}
