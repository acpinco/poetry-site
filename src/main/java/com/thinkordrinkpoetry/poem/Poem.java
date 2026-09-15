package com.thinkordrinkpoetry.poem;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "poem") @Getter @NoArgsConstructor
public class Poem {
    @Id private UUID id;
    @Column(name="poet_id", nullable=false) private UUID poetId;
    @Column(nullable=false) private String title;
    @Column(name="poem", nullable=false) private String body;
    @Column(name="legacy_submitted_on") private LocalDate legacySubmittedOn;
    @Column(name="created_at", insertable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", insertable=false, updatable=false) private Instant updatedAt;
    public Poem(UUID poetId,String title,String body,LocalDate legacySubmittedOn) { this.id=UuidCreator.getTimeOrderedEpoch(); this.poetId=poetId; this.title=title; this.body=body; this.legacySubmittedOn=legacySubmittedOn; }
    public void update(String title,String body) { this.title=title; this.body=body; }
}
