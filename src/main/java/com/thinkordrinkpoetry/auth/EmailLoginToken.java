package com.thinkordrinkpoetry.auth;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "email_login_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EmailLoginToken {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    EmailLoginToken(String email, String tokenHash, Instant expiresAt) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }
}
