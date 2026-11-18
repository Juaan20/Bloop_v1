package com.bloop.users.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Alta de un empleado de la empresa por parte de un administrador. */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 40) String phone,
        @NotBlank @Size(min = 8, max = 100, message = "La contrasena debe tener al menos 8 caracteres")
        String password,
        @NotEmpty(message = "Selecciona al menos un rol") Set<String> roles,
        Set<UUID> venueIds) {
}
