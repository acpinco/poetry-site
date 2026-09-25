package com.thinkordrinkpoetry.discovery;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class PublicDiscoveryRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    void check(String clientIp, boolean search) {
        int limit = search ? 30 : 120;
        Instant now = Instant.now();
        String key = (search ? "search|" : "browse|") + clientIp;
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
        if (window.count() > limit) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please slow down and try again shortly.");
        }
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void removeExpiredWindows() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Window(Instant startedAt, int count) {
        Instant expiresAt() {
            return startedAt.plusSeconds(60);
        }
    }
}
