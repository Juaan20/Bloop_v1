package com.bloop.audit.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, String userEmail, String action, String entityType, String entityId,
                               String details, String ipAddress, Instant createdAt) {
}
