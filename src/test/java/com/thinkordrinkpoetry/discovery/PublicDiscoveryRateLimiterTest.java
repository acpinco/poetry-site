package com.thinkordrinkpoetry.discovery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PublicDiscoveryRateLimiterTest {

    private final PublicDiscoveryRateLimiter rateLimiter = new PublicDiscoveryRateLimiter();

    @Test
    void searchHasAStricterLimitThanBrowsing() {
        for (int request = 0; request < 30; request++) {
            assertDoesNotThrow(() -> rateLimiter.check("127.0.0.1", true));
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> rateLimiter.check("127.0.0.1", true));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }
}
