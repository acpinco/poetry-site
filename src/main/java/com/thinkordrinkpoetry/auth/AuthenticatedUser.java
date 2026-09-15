package com.thinkordrinkpoetry.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthenticatedUser(UUID sessionId, String email, UUID poetId, Instant expiresAt) {
}
