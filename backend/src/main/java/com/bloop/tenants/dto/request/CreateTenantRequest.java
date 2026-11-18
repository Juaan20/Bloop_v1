package com.bloop.tenants.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de una empresa junto con su primer usuario administrador (TENANT_ADMIN).
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 200) String legalName,
        @Size(max = 40) String taxId,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @Size(max = 40) String contactPhone,
        @NotBlank @Size(max = 80) String adminFirstName,
        @NotBlank @Size(max = 120) String adminLastName,
        @NotBlank @Email @Size(max = 255) String adminEmail,
        @NotBlank @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
        String adminPassword) {
}
