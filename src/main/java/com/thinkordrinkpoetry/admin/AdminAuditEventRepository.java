package com.thinkordrinkpoetry.admin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {}
