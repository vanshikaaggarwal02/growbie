package com.launchmate.contact;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Allows at most {@code limit} events per key within a sliding window. Kept in memory, so it resets on restart. */
public class RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window) {
        this(limit, window, System::currentTimeMillis);
    }

    RateLimiter(int limit, Duration window, LongSupplier clock) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Records an attempt and returns false when the key is over its limit. */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMillis) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }
}
