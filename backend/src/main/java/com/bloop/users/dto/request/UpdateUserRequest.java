package com.bloop.users.dto.request;

import com.bloop.users.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** Edicion de un empleado: datos, estado, roles y locales asignados. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Size(max = 40) String phone,
        @NotNull UserStatus status,
        @NotEmpty(message = "Selecciona al menos un rol") Set<String> roles,
        Set<UUID> venueIds) {
}
