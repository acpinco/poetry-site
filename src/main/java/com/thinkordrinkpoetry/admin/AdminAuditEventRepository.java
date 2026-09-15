package com.thinkordrinkpoetry.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {}
