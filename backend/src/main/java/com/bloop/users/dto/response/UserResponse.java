package com.bloop.users.dto.response;

import com.bloop.users.entity.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Datos publicos de un usuario. Nunca incluye el hash de la contrasena. */
public record UserResponse(UUID id, String email, String firstName, String lastName, String phone,
                           UserStatus status, Set<String> roles, Set<UUID> venueIds,
                           Instant lastLoginAt, Instant createdAt) {
}
