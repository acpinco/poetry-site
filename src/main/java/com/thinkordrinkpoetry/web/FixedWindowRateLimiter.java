package com.thinkordrinkpoetry.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory fixed-window counter: at most {@code limit} requests per key in each {@code window}. */
public final class FixedWindowRateLimiter {
    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Records one request for {@code key} and returns whether it is within the limit. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Window current = windows.compute(key, (ignored, existing) ->
                existing == null || !existing.expiresAt().isAfter(now)
                        ? new Window(now.plus(window), 1)
                        : new Window(existing.expiresAt(), existing.count() + 1));
        return current.count() <= limit;
    }

    /** Drops finished windows so the map does not grow with every key ever seen. */
    public void removeExpired() {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Window(Instant expiresAt, int count) {}
}
