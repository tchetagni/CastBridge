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

    public Instant now() { return (fixed != null ? fixed : Instant.now()).truncatedTo(ChronoUnit.MILLIS); }

    public long nowMs() { return now().toEpochMilli(); }

    /** Test seam: freezes the clock. */
    public void set(Instant t) { fixed = t; }

    /** Test seam: back to the system clock. */
    public void reset() { fixed = null; }
}
