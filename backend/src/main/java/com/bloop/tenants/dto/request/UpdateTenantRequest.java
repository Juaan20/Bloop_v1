package com.bloop.tenants.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos editables de una empresa (los usa tanto el super admin como el admin de la empresa). */
public record UpdateTenantRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 200) String legalName,
        @Size(max = 40) String taxId,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @Size(max = 40) String contactPhone) {
}
