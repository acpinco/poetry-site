package com.thinkordrinkpoetry.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    @Query("""
            select session from UserSession session
            where session.sessionTokenHash = :tokenHash
              and session.expiresAt > :now
              and session.revokedAt is null
            """)
    Optional<UserSession> findActiveByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession session where session.expiresAt <= :now or session.revokedAt is not null")
    int deleteExpiredOrRevokedBefore(@Param("now") Instant now);
}
