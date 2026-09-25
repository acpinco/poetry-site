package com.thinkordrinkpoetry.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                    "timberlinelab@gmail.com"));

    @Test
    void allowsTheConfiguredNumberOfRequestsPerEmailAndIp() {
        assertDoesNotThrow(() -> rateLimiter.check("poet@example.com", "127.0.0.1"));
        assertDoesNotThrow(() -> rateLimiter.check("poet@example.com", "127.0.0.1"));
    }

    @Test
    void rejectsRequestsAfterTheConfiguredLimit() {
        rateLimiter.check("poet@example.com", "127.0.0.1");
        rateLimiter.check("poet@example.com", "127.0.0.1");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rateLimiter.check("poet@example.com", "127.0.0.1"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }
}
