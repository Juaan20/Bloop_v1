package com.bloop.tenants.dto.response;

import com.bloop.tenants.entity.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(UUID id, String name, String slug, String legalName, String taxId,
                             String contactEmail, String contactPhone, TenantStatus status, Instant createdAt) {
}
