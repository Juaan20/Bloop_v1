package com.bloop.shared.security;

import com.bloop.shared.enums.RoleCode;

import java.util.Set;
import java.util.UUID;

/**
 * Usuario autenticado de la peticion actual, reconstruido a partir del JWT.
 *
 * @param tenantId empresa del usuario; null para SUPER_ADMIN y clientes finales
 */
public record CurrentUser(UUID userId, String email, UUID tenantId, Set<String> roles, Set<String> permissions) {

    public boolean hasRole(RoleCode role) {
        return roles.contains(role.name());
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
