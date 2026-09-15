package com.thinkordrinkpoetry.admin;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="admin_audit_event")
class AdminAuditEvent {
    @Id private UUID id;
    @Column(name="actor_poet_id") private UUID actorPoetId;
    private String action;
    @Column(name="target_type") private String targetType;
    @Column(name="target_id") private UUID targetId;
    private String reason;
    protected AdminAuditEvent() {}
    AdminAuditEvent(UUID actor,String action,String targetType,UUID target,String reason) { id=UuidCreator.getTimeOrderedEpoch(); actorPoetId=actor; this.action=action; this.targetType=targetType; targetId=target; this.reason=reason; }
}
