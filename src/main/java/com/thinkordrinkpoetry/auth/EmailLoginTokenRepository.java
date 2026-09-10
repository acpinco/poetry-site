package com.thinkordrinkpoetry.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailLoginTokenRepository extends JpaRepository<EmailLoginToken, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailLoginToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from EmailLoginToken token where token.expiresAt <= :now")
    int deleteExpiredBefore(@Param("now") Instant now);
}
