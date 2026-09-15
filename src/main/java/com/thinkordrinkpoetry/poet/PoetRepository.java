package com.thinkordrinkpoetry.poet;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoetRepository extends JpaRepository<Poet, UUID> {
    Optional<Poet> findByEmail(String email);
    long countByRole(PoetRole role);
}
