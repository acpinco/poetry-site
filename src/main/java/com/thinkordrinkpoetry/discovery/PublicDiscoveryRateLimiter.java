package com.thinkordrinkpoetry.discovery;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class PublicDiscoveryRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    void check(String clientIp, boolean search) {
        int limit = search ? 30 : 120;
        Instant now = Instant.now();
        String key = (search ? "search|" : "browse|") + clientIp;
        Window window = windows.compute(key, (ignored, current) -> current == null || !current.startedAt().plusSeconds(60).isAfter(now)
                ? new Window(now, 1) : new Window(current.startedAt(), current.count() + 1));
        if (window.count() > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please slow down and try again shortly.");
    }

    private record Window(Instant startedAt, int count) {}
}
