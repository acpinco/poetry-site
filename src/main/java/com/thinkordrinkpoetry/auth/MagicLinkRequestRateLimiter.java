package com.thinkordrinkpoetry.auth;

import com.thinkordrinkpoetry.web.FixedWindowRateLimiter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Limits sign-in email in three layers: per client IP (stops one machine from spraying addresses),
 * per email address (caps what any one inbox can receive), and site-wide (a circuit breaker that
 * protects the SMTP account and sender reputation).
 */
@Component
class MagicLinkRequestRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(MagicLinkRequestRateLimiter.class);
    private static final Duration EMAIL_WINDOW = Duration.ofMinutes(15);
    private static final Duration HOURLY = Duration.ofHours(1);

    private final FixedWindowRateLimiter perClient;
    private final FixedWindowRateLimiter perEmail;
    private final FixedWindowRateLimiter global;

    MagicLinkRequestRateLimiter(AuthProperties properties) {
        perClient = new FixedWindowRateLimiter(properties.magicLinkIpLimit(), HOURLY);
        perEmail = new FixedWindowRateLimiter(properties.magicLinkRequestLimit(), EMAIL_WINDOW);
        global = new FixedWindowRateLimiter(properties.magicLinkGlobalLimit(), HOURLY);
    }

    /** Counts every request from this client, whether or not an email is sent. */
    void checkClient(String clientIp) {
        if (!perClient.tryAcquire(clientIp)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in requests. Please try again later.");
        }
    }

    /**
     * Returns false once this address has had its share of email. Callers skip sending silently, so a
     * stranger cannot lock a poet out of sign-in by requesting links for their address.
     */
    boolean allowEmail(String email) {
        return perEmail.tryAcquire(email);
    }

    /** Counts one outgoing email against the site-wide ceiling. */
    void checkGlobal() {
        if (!global.tryAcquire("global")) {
            log.warn("Site-wide sign-in email limit reached; refusing to send more this hour.");
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Sign-in is busy right now. Please try again later.");
        }
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT15M")
    void removeExpiredWindows() {
        perClient.removeExpired();
        perEmail.removeExpired();
        global.removeExpired();
    }
}
