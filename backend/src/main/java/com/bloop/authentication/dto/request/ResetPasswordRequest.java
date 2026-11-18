package com.bloop.authentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
        String newPassword) {
}
