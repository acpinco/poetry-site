package com.thinkordrinkpoetry.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class MagicLinkRequestRateLimiterTest {

    private final MagicLinkRequestRateLimiter rateLimiter = new MagicLinkRequestRateLimiter(
            new AuthProperties(
                    "http://localhost:8080",
                    "http://localhost:5173",
                    Duration.ofMinutes(15),
                    2,
                    Duration.ofDays(7),
                    "poetry_session",
                    false,
                    "",
                    "support@example.test",
                    3,
                    4,
                    "",
                    ""));

    @Test
    void rejectsAClientAfterItsLimitAcrossDifferentEmails() {
        for (int request = 0; request < 3; request++) {
            assertDoesNotThrow(() -> rateLimiter.checkClient("203.0.113.7"));
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rateLimiter.checkClient("203.0.113.7"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertDoesNotThrow(() -> rateLimiter.checkClient("198.51.100.4"));
    }

    @Test
    void stopsAllowingAnEmailAfterItsLimitWithoutThrowing() {
        assertTrue(rateLimiter.allowEmail("poet@example.com"));
        assertTrue(rateLimiter.allowEmail("poet@example.com"));
        assertFalse(rateLimiter.allowEmail("poet@example.com"));
        assertTrue(rateLimiter.allowEmail("other@example.com"));
    }

    @Test
    void rejectsEmailSiteWideAfterTheGlobalLimit() {
        for (int email = 0; email < 4; email++) {
            assertDoesNotThrow(rateLimiter::checkGlobal);
        }

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, rateLimiter::checkGlobal);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }
}
