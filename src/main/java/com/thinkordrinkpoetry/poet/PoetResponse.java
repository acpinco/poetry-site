package com.thinkordrinkpoetry.poet;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record PoetResponse(
        UUID poetId,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String penName,
        String bio,
        Instant createdAt,
        Instant updatedAt,
        AccountStatus accountStatus,
        PoetRole role,
        Instant lastSeenAt) {

    public static PoetResponse from(Poet poet) {
        // Historical poets "joined" when they first submitted in 1999.
        Instant createdAt = poet.getLegacySubmittedOn() == null
                ? poet.getCreatedAt()
                : poet.getLegacySubmittedOn().atStartOfDay().toInstant(ZoneOffset.UTC);
        // Without a pen name, a poet is shown by their full name.
        String penName = poet.getPenName() == null ? poet.getFullName() : poet.getPenName();
        return new PoetResponse(
                poet.getId(),
                poet.getEmail(),
                poet.getFirstName(),
                poet.getLastName(),
                poet.getFullName(),
                penName,
                poet.getBio(),
                createdAt,
                poet.getUpdatedAt(),
                poet.getAccountStatus(),
                poet.getRole(),
                poet.getLastSeenAt());
    }
}
