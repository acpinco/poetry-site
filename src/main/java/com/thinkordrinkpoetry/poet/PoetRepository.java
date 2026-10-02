package com.thinkordrinkpoetry.poet;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PoetRepository extends JpaRepository<Poet, UUID> {
    Optional<Poet> findByEmail(String email);
    long countByRole(PoetRole role);

    // lower() on both sides matches the uq_poet_pen_name_ci index.
    @Query("select count(p) > 0 from Poet p where lower(p.penName) = lower(:penName)")
    boolean penNameTaken(@Param("penName") String penName);

    @Query("select count(p) > 0 from Poet p where lower(p.penName) = lower(:penName) and p.id <> :poetId")
    boolean penNameTakenByAnotherPoet(@Param("penName") String penName, @Param("poetId") UUID poetId);

    /**
     * Records a visit unless one was already recorded after {@code staleBefore}. The condition keeps
     * this to an index lookup on most requests instead of a row write.
     */
    @Modifying
    @Query(value = """
            update poet set last_seen_at = :now
            where id = :poetId and (last_seen_at is null or last_seen_at < :staleBefore)
            """, nativeQuery = true)
    int markSeen(@Param("poetId") UUID poetId, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
