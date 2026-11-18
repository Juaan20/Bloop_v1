package com.bloop.tenants.dto.request;

import com.bloop.tenants.entity.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeTenantStatusRequest(@NotNull TenantStatus status) {
}
