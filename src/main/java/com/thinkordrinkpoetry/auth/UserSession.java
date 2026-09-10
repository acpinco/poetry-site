package com.thinkordrinkpoetry.auth;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserSession {
    @Id
    private UUID id;

    @Column(name = "session_token_hash", nullable = false)
    private String sessionTokenHash;

    @Column(name = "authenticated_email", nullable = false)
    private String authenticatedEmail;

    @Column(name = "poet_id")
    private UUID poetId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    UserSession(String sessionTokenHash, String authenticatedEmail, Instant expiresAt) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.sessionTokenHash = sessionTokenHash;
        this.authenticatedEmail = authenticatedEmail;
        this.expiresAt = expiresAt;
        this.lastUsedAt = Instant.now();
    }

    void revoke(Instant instant) {
        this.revokedAt = instant;
    }

    void markUsed(Instant instant) {
        this.lastUsedAt = instant;
    }
}
