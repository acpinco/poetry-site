package com.thinkordrinkpoetry.discovery;

import com.thinkordrinkpoetry.web.FixedWindowRateLimiter;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class PublicDiscoveryRateLimiter {
    private final FixedWindowRateLimiter browse = new FixedWindowRateLimiter(120, Duration.ofMinutes(1));
    private final FixedWindowRateLimiter search = new FixedWindowRateLimiter(30, Duration.ofMinutes(1));

    void check(String clientIp, boolean isSearch) {
        if (!(isSearch ? search : browse).tryAcquire(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please slow down and try again shortly.");
        }
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void removeExpiredWindows() {
        browse.removeExpired();
        search.removeExpired();
    }
}
