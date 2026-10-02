package com.thinkordrinkpoetry.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * The signed-in person behind a request. {@code poetId} is null until they create a profile;
 * {@code admin} is true only for an active admin, and grants ROLE_ADMIN.
 */
public record AuthenticatedUser(UUID sessionId, String email, UUID poetId, Instant expiresAt, boolean admin) {}
