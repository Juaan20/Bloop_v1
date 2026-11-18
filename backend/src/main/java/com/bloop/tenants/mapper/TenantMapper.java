package com.bloop.tenants.mapper;

import com.bloop.tenants.dto.request.UpdateTenantRequest;
import com.bloop.tenants.dto.response.TenantResponse;
import com.bloop.tenants.entity.Tenant;
import org.springframework.stereotype.Component;

/**
 * Conversion entre la entidad Tenant y sus DTOs.
 * Usamos mappers escritos a mano (no MapStruct): son pocos campos y asi el codigo
 * se lee y depura sin codigo generado.
 */
@Component
public class TenantMapper {

    public TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getLegalName(),
                tenant.getTaxId(), tenant.getContactEmail(), tenant.getContactPhone(), tenant.getStatus(),
                tenant.getCreatedAt());
    }

    public void updateEntity(Tenant tenant, UpdateTenantRequest request) {
        tenant.setName(request.name().trim());
        tenant.setLegalName(request.legalName());
        tenant.setTaxId(request.taxId());
        tenant.setContactEmail(request.contactEmail().trim().toLowerCase());
        tenant.setContactPhone(request.contactPhone());
    }
}
