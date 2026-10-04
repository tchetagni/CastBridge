package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The decisions of the owner of 2026-10-04: a code (trial or production) is valid exactly 48 h after its emission (installation window); after that it expires and
 * cannot be installed; a code never seen on a TV 48 h after its emission is « expiré non utilisé »; an activation already installed keeps its OWN duration (trial
 * ceiling, unlimited production), whatever the 48 h.
 */
class OwnerRulesTest extends ActTestBase {
    @Autowired Reconciler reconciler;

    long t0;

    @BeforeEach
    void start() {
        resetModule();
        t0 = System.currentTimeMillis();
        clock.set(Instant.ofEpochMilli(t0));
    }

    @AfterEach
    void stop() { clock.reset(); }

    private void declare(String... tokens) throws Exception {
        var b = new JournalBuilder(DESK, SEQ.incrementAndGet(), "desk", t0, 1);
        for (String t : tokens) b.entry(t0, "issue", JournalBuilder.issueFields(t, null));
        assertEquals(200, uploadJournal(b.build()).getResponse().getStatus());
    }

    private String state(String token) { return jdbc.queryForObject("select state from act_key where fp = ?", String.class, sha256(token)); }

    private void at(Duration after) {
        clock.set(Instant.ofEpochMilli(t0).plus(after));
        reconciler.reconcileAll();
    }

    @Test
    void anUnseenCodeExpiresExactly48HoursAfterItsEmission() throws Exception {
        String trial = trialToken(DESK, dev(), t0), production = productionToken(DESK, "lic-ownerrule01", dev(), t0);
        declare(trial, production);
        assertEquals("EMISE", state(trial));
        at(Duration.ofHours(47).plusMinutes(59));
        assertEquals("EMISE", state(trial), "still installable one minute before the end of the window");
        assertEquals("EMISE", state(production));
        at(Duration.ofHours(48).plusMinutes(1));
        assertEquals("EXPIRED_UNUSED", state(trial), "never seen on a TV 48 h after its emission: « expiré non utilisé »");
        assertEquals("EXPIRED_UNUSED", state(production), "production codes follow the same window");
        long closes = jdbc.queryForObject("select expires_at from act_key where fp = ?", java.sql.Timestamp.class, sha256(trial)).getTime();
        assertEquals(48 * 3_600_000L, closes - t0, "the window is 48 h from the emission, not more");
    }

    @Test
    void anInstalledActivationKeepsItsOwnDurationAndIsNotAffectedByTheWindow() throws Exception {
        var d1 = dev();
        var d2 = dev();
        String production = productionToken(DESK, "lic-ownerrule02", d1, t0);         // unlimited once installed
        String trial = trialToken(DESK, d2, t0);                                       // 30-day ceiling of its own
        declare(production, trial);
        var i1 = install();
        var i2 = install();
        assertEquals(200, report(i1, reportJson(d1, t0, "PRODUCTION", java.util.List.of(production), null)).getResponse().getStatus());
        assertEquals(200, report(i2, reportJson(d2, t0, "TRIAL", java.util.List.of(trial), null)).getResponse().getStatus());
        at(Duration.ofHours(49));
        assertEquals("ACTIVATED", state(production), "installed inside the window: the 48 h do not apply any more");
        assertEquals("ACTIVATED", state(trial));
        at(Duration.ofDays(400));
        assertEquals("ACTIVATED", state(production), "an unlimited production activation never ends");
        assertEquals("ENDED", state(trial), "the trial ends at the ceiling it was installed with (30 days), not at 48 h");
    }

    @Test
    void aTrialIsStillWithinItsCeilingBefore30Days() throws Exception {
        var d = dev();
        String trial = trialToken(DESK, d, t0);
        declare(trial);
        var i = install();
        assertEquals(200, report(i, reportJson(d, t0, "TRIAL", java.util.List.of(trial), null)).getResponse().getStatus());
        at(Duration.ofDays(29));
        assertEquals("ACTIVATED", state(trial));
        at(Duration.ofDays(31));
        assertEquals("ENDED", state(trial));
    }

    @Test
    void aCodeInstalledAfterItsWindowIsFlaggedNotBelieved() throws Exception {
        var d = dev();
        String trial = trialToken(DESK, d, t0);
        declare(trial);
        String fp8 = sha256(trial).substring(0, 8);
        var i = install();
        clock.set(Instant.ofEpochMilli(t0).plus(Duration.ofDays(5)));
        long installedAt = t0 + 4 * 86_400_000L;       // 4 days after the emission: the window closed 2 days earlier
        assertEquals(200, report(i, reportJson(d, clock.nowMs(), "TRIAL", java.util.List.of(trial), "\"installedAt\":{\"" + fp8 + "\":" + installedAt + "}").replace("\"installedAt\":{},", "")).getResponse().getStatus());
        assertEquals(1, alerts("OUT_OF_WINDOW"));
    }
}
