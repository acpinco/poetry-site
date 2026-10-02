package com.thinkordrinkpoetry.poem;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "poem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Poem {
    @Id
    private UUID id;

    @Column(name = "poet_id", nullable = false)
    private UUID poetId;

    @Column(nullable = false)
    private String title;

    @Column(name = "poem", nullable = false)
    private String body;

    @Column(name = "legacy_submitted_on")
    private LocalDate legacySubmittedOn;

    // Set by the column default and the trg_poem_updated_at trigger; Hibernate reads them back after writes.
    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Poem(UUID poetId, String title, String body, LocalDate legacySubmittedOn) {
        this.id = UuidCreator.getTimeOrderedEpoch();
        this.poetId = poetId;
        this.title = title;
        this.body = body;
        this.legacySubmittedOn = legacySubmittedOn;
    }

    public void update(String title, String body) {
        this.title = title;
        this.body = body;
    }
}
