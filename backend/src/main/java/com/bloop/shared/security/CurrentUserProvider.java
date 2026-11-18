package com.bloop.shared.security;

import com.bloop.shared.exception.ForbiddenException;
import com.bloop.shared.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Da acceso al usuario autenticado desde los servicios.
 *
 * REGLA MULTI-TENANT: el tenant SIEMPRE se obtiene de aqui (del token firmado por el
 * backend), nunca de un parametro enviado por Angular.
 */
@Component
public class CurrentUserProvider {

    public CurrentUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("No hay un usuario autenticado");
        }
        String tenant = jwt.getClaimAsString(JwtClaims.TENANT_ID);
        return new CurrentUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(JwtClaims.EMAIL),
                tenant == null ? null : UUID.fromString(tenant),
                toSet(jwt.getClaimAsStringList(JwtClaims.ROLES)),
                toSet(jwt.getClaimAsStringList(JwtClaims.PERMISSIONS)));
    }

    /** Tenant del usuario actual; lanza 403 si el usuario no pertenece a ninguna empresa. */
    public UUID requireTenantId() {
        UUID tenantId = get().tenantId();
        if (tenantId == null) {
            throw new ForbiddenException("Esta operacion requiere un usuario de empresa");
        }
        return tenantId;
    }

    private static Set<String> toSet(List<String> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }
}
