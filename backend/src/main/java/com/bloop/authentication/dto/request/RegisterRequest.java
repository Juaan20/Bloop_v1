package com.bloop.authentication.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registro de un cliente final en la web publica. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres") String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 40) String phone,
        @AssertTrue(message = "Debes aceptar la politica de privacidad") boolean acceptPrivacy,
        boolean marketingConsent) {
}
