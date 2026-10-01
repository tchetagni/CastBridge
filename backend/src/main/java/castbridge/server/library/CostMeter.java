package castbridge.server.library;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Estimated cost of an analysis, and the daily limits that protect the owner's wallet: a budget for the whole server and a number of names per device.
 * In memory (one server instance): a restart resets the day, which can only make the limits looser for a few hours, never the data wrong.
 * Estimates come from the token counts the service reports; without them, from the size of the text (about 3 characters per token, on the safe side).
 */
public class CostMeter {
    private static final long DAY_MS = 86_400_000L;
    private final double priceIn, priceOut, dailyBudget;
    private final int perDeviceItems;
    private final LongSupplier clock;
    private long day = -1;
    private double spent;
    private final ConcurrentHashMap<Long, Integer> items = new ConcurrentHashMap<>();

    public CostMeter(double priceInPerMtok, double priceOutPerMtok, double dailyBudgetUsd, int perDeviceDailyItems, LongSupplier clock) {
        this.priceIn = priceInPerMtok; this.priceOut = priceOutPerMtok; this.dailyBudget = dailyBudgetUsd; this.perDeviceItems = perDeviceDailyItems; this.clock = clock;
    }

    public double cost(long inputTokens, long outputTokens) { return (inputTokens * priceIn + outputTokens * priceOut) / 1_000_000.0; }

    /** Rough token count of a text when the service reports none. */
    public static long estimateTokens(String text) { return (text.length() + 2) / 3; }

    /** What an analysis of [n] names is expected to cost before it is made (prompt + names in, about 60 tokens per name out). */
    public double estimateBeforehand(String prompt, int n, int namesChars, int maxOutputTokens) {
        return cost(estimateTokens(prompt) + (namesChars + n * 12L + 2) / 3, Math.min(maxOutputTokens, 60L * n + 20));
    }

    private void roll() {
        long d = clock.getAsLong() / DAY_MS;
        if (d != day) { day = d; spent = 0; items.clear(); }
    }

    public enum Refusal { NONE, DEVICE_QUOTA, BUDGET }

    /** Books the names and the expected cost, or says why not (nothing is booked then). */
    public synchronized Refusal reserve(long deviceId, int n, double expectedCost) {
        roll();
        if (items.getOrDefault(deviceId, 0) + n > perDeviceItems) return Refusal.DEVICE_QUOTA;
        if (spent + expectedCost > dailyBudget) return Refusal.BUDGET;
        items.merge(deviceId, n, Integer::sum);
        spent += expectedCost;
        return Refusal.NONE;
    }

    /** Replaces the expected cost by the real (or estimated) one once the model has answered. */
    public synchronized void settle(double expected, double actual) { roll(); spent += actual - expected; if (spent < 0) spent = 0; }

    /** Gives back a reservation when the model could not answer (nothing was spent). */
    public synchronized void release(long deviceId, int n, double expected) {
        roll();
        items.merge(deviceId, -n, Integer::sum);
        spent = Math.max(0, spent - expected);
    }

    public synchronized double spentToday() { roll(); return spent; }
    public double dailyBudget() { return dailyBudget; }
}
