package com.github.bgalek;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window request limiter keyed by caller.
 * <p>
 * Guards two things that were previously unbounded: password submission, where the ~190-word list
 * is otherwise brute-forceable in seconds, and login, which had no throttle at all.
 */
public class RateLimiter {
    private final Cache<String, AtomicInteger> windows;
    private final int maxPerWindow;

    public RateLimiter(int maxPerWindow, Duration window) {
        this.maxPerWindow = maxPerWindow;
        this.windows = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(window)
                .build();
    }

    /** Returns true when the call is allowed, false when the caller has exhausted the window. */
    public boolean tryAcquire(String key) {
        AtomicInteger count = windows.get(key, k -> new AtomicInteger());
        return count.incrementAndGet() <= maxPerWindow;
    }
}
