package com.bloop.tenants.controller;

import com.bloop.tenants.dto.request.UpdateTenantRequest;
import com.bloop.tenants.dto.response.TenantResponse;
import com.bloop.tenants.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Datos de la empresa del usuario autenticado (configuracion de empresa). */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class MyTenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAuthority('VENUE_VIEW')")
    public TenantResponse get() {
        return tenantService.getMyTenant();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_SETTINGS')")
    public TenantResponse update(@Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.updateMyTenant(request);
    }
}
