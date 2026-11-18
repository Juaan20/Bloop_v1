package com.bloop.tenants.controller;

import com.bloop.shared.response.PageResponse;
import com.bloop.tenants.dto.request.ChangeTenantStatusRequest;
import com.bloop.tenants.dto.request.CreateTenantRequest;
import com.bloop.tenants.dto.request.UpdateTenantRequest;
import com.bloop.tenants.dto.response.TenantResponse;
import com.bloop.tenants.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Administracion de empresas. Solo para el SUPER_ADMIN de la plataforma. */
@RestController
@RequestMapping("/api/admin/tenants")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public PageResponse<TenantResponse> list(@RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return tenantService.list(search, page, size);
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return tenantService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        return tenantService.create(request);
    }

    @PutMapping("/{id}")
    public TenantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public TenantResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeTenantStatusRequest request) {
        return tenantService.changeStatus(id, request.status());
    }
}
