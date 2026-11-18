package com.bloop.authentication.dto.response;

import java.util.Set;
import java.util.UUID;

/** Perfil del usuario autenticado que Angular usa para mostrar menus segun permisos. */
public record AuthUserResponse(UUID id, String email, String firstName, String lastName, UUID tenantId,
                               String tenantName, Set<String> roles, Set<String> permissions) {
}
