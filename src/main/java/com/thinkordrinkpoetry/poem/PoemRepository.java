package com.thinkordrinkpoetry.poem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoemRepository extends JpaRepository<Poem, UUID> {
    Optional<Poem> findByIdAndPoetId(UUID id, UUID poetId);

    List<Poem> findAllByOrderByUpdatedAtDescIdDesc(Pageable page);

    List<Poem> findByPoetIdOrderByUpdatedAtDesc(UUID poetId);
}
