package com.launchmate.domain;

import java.time.Duration;
import java.util.ArrayDeque;

/** Sliding-window limiter that keeps all users together under the provider's per-minute limit. */
final class RateWindow {

    private final int max;
    private final long windowNanos;
    private final ArrayDeque<Long> hits = new ArrayDeque<>();

    RateWindow(int max, Duration window) {
        this.max = max;
        this.windowNanos = window.toNanos();
    }

    synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        while (!hits.isEmpty() && now - hits.peekFirst() > windowNanos) {
            hits.pollFirst();
        }
        if (hits.size() >= max) {
            return false;
        }
        hits.addLast(now);
        return true;
    }
}
