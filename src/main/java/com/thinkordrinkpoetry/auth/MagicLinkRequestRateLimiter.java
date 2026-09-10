package com.thinkordrinkpoetry.auth;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class MagicLinkRequestRateLimiter {
    private final AuthProperties properties;
    private final ConcurrentHashMap<String, RequestWindow> windows = new ConcurrentHashMap<>();

    MagicLinkRequestRateLimiter(AuthProperties properties) {
        this.properties = properties;
    }

    void check(String email, String clientIp) {
        Instant now = Instant.now();
        String key = email + "|" + clientIp;
        RequestWindow window = windows.compute(key, (ignored, current) -> {
            if (current == null || !current.startedAt().plus(properties.magicLinkTtl()).isAfter(now)) {
                return new RequestWindow(now, 1);
            }
            return new RequestWindow(current.startedAt(), current.requestCount() + 1);
        });
        if (window.requestCount() > properties.magicLinkRequestLimit()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before requesting another sign-in link.");
        }
    }

    private record RequestWindow(Instant startedAt, int requestCount) {
    }
}
