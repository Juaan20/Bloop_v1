package com.bloop.tenants.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.shared.enums.RoleCode;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import com.bloop.shared.util.SlugUtils;
import com.bloop.tenants.dto.request.CreateTenantRequest;
import com.bloop.tenants.dto.request.UpdateTenantRequest;
import com.bloop.tenants.dto.response.TenantResponse;
import com.bloop.tenants.entity.Tenant;
import com.bloop.tenants.entity.TenantStatus;
import com.bloop.tenants.mapper.TenantMapper;
import com.bloop.tenants.repository.TenantRepository;
import com.bloop.users.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** Gestion de empresas (tenants). */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final UserAccountService userAccountService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    // ---------- Operaciones del SUPER_ADMIN ----------

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> list(String search, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name"));
        return PageResponse.of(tenantRepository.search(search == null ? "" : search.trim(), pageable),
                tenantMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID id) {
        return tenantMapper.toResponse(findTenant(id));
    }

    /** Crea la empresa y su primer administrador en una unica transaccion. */
    @Transactional
    public TenantResponse create(CreateTenantRequest request) {
        Tenant tenant = new Tenant();
        tenantMapper.updateEntity(tenant, new UpdateTenantRequest(request.name(), request.legalName(),
                request.taxId(), request.contactEmail(), request.contactPhone()));
        tenant.setSlug(SlugUtils.uniqueSlug(request.name(), tenantRepository::existsBySlug));
        tenant = tenantRepository.save(tenant);

        userAccountService.createAccount(tenant.getId(), request.adminEmail(), request.adminPassword(),
                request.adminFirstName(), request.adminLastName(), null,
                Set.of(RoleCode.TENANT_ADMIN.name()), Set.of());

        auditService.record(AuditAction.TENANT_CREATED, "Tenant", tenant.getId(), tenant.getName());
        return tenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse update(UUID id, UpdateTenantRequest request) {
        Tenant tenant = findTenant(id);
        tenantMapper.updateEntity(tenant, request);
        auditService.record(AuditAction.TENANT_UPDATED, "Tenant", id, "Datos de empresa actualizados");
        return tenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse changeStatus(UUID id, TenantStatus status) {
        Tenant tenant = findTenant(id);
        tenant.setStatus(status);
        auditService.record(AuditAction.TENANT_UPDATED, "Tenant", id, "Estado: " + status);
        return tenantMapper.toResponse(tenant);
    }

    // ---------- Operaciones sobre la empresa del usuario actual ----------

    @Transactional(readOnly = true)
    public TenantResponse getMyTenant() {
        return tenantMapper.toResponse(findTenant(currentUserProvider.requireTenantId()));
    }

    @Transactional
    public TenantResponse updateMyTenant(UpdateTenantRequest request) {
        return update(currentUserProvider.requireTenantId(), request);
    }

    private Tenant findTenant(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> NotFoundException.of("Empresa"));
    }
}
