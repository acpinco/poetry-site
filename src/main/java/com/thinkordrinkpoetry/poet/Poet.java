package com.thinkordrinkpoetry.poet;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "poet")
@Getter
@NoArgsConstructor
public class Poet {
    @Id private UUID id;
    @Column(nullable = false) private String email;
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "full_name", insertable = false, updatable = false) private String fullName;
    @Column(name = "pen_name") private String penName;
    private String bio;
    @Column(name = "legacy_submitted_on") private LocalDate legacySubmittedOn;
    @Enumerated(EnumType.STRING) @Column(name = "account_status") private AccountStatus accountStatus;
    @Enumerated(EnumType.STRING) private PoetRole role;
    @Column(name = "locked_at") private Instant lockedAt;
    @Column(name = "locked_reason") private String lockedReason;
    // Set on creation, then written only by PoetRepository.markSeen so a profile save never
    // overwrites a newer visit.
    @Column(name = "last_seen_at", updatable = false) private Instant lastSeenAt;
    // Set by the column default and the trg_poet_updated_at trigger; Hibernate reads them back after writes.
    @Generated
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

    public Poet(String email, String firstName, String lastName, String penName, String bio, LocalDate legacySubmittedOn,
            AccountStatus accountStatus, PoetRole role) {
        this.id = UuidCreator.getTimeOrderedEpoch(); this.email = email; this.firstName = firstName; this.lastName = lastName;
        this.penName = penName; this.bio = bio; this.legacySubmittedOn = legacySubmittedOn;
        this.accountStatus = accountStatus; this.role = role; this.lastSeenAt = Instant.now();
    }
    public void updateProfile(String firstName, String lastName, String penName, String bio) {
        this.firstName = firstName; this.lastName = lastName; this.penName = penName; this.bio = bio;
    }
    public void lock(String reason) { accountStatus = AccountStatus.LOCKED; lockedAt = Instant.now(); lockedReason = reason; }
    public void unlock() { accountStatus = AccountStatus.ACTIVE; lockedAt = null; lockedReason = null; }
}
