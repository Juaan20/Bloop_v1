package com.bloop.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos que el propio usuario puede modificar en "Mi cuenta". */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 40) String phone,
        boolean marketingConsent) {
}
