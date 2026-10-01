package castbridge.server.library;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** At most {@code perHour} requests per device and hour (sliding window, in memory: one server instance, nothing is persisted). */
public class SuggestLimiter {
    private static final long HOUR_MS = 3_600_000L;
    private final ConcurrentHashMap<Long, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final int perHour;
    private final LongSupplier clock;

    public SuggestLimiter(int perHour, LongSupplier clock) {
        this.perHour = perHour;
        this.clock = clock;
    }

    /** 0 = allowed (and counted); otherwise the number of seconds to wait. */
    public long tryAcquire(long deviceId) {
        long now = clock.getAsLong();
        Deque<Long> w = windows.computeIfAbsent(deviceId, k -> new ArrayDeque<>());
        synchronized (w) {
            while (!w.isEmpty() && now - w.peekFirst() >= HOUR_MS) w.pollFirst();
            if (w.size() >= perHour) return Math.max(1, (HOUR_MS - (now - w.peekFirst()) + 999) / 1000);
            w.addLast(now);
            return 0;
        }
    }

    /** Forgets devices that have been quiet for an hour (called now and then). */
    public void evictIdle() {
        long now = clock.getAsLong();
        windows.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || now - e.getValue().peekLast() >= HOUR_MS; } });
    }
}
