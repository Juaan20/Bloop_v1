package com.bloop.audit.service;

import com.bloop.audit.dto.response.AuditLogResponse;
import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.entity.AuditLog;
import com.bloop.audit.repository.AuditLogRepository;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Registra acciones relevantes. Usa REQUIRES_NEW para que la entrada quede guardada
 * aunque la operacion principal falle (p. ej. un intento de login fallido).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /** Registra una accion del usuario autenticado actual. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entityType, Object entityId, String details) {
        CurrentUser user = isAuthenticated() ? currentUserProvider.get() : null;
        save(user == null ? null : user.tenantId(), user == null ? null : user.userId(),
                user == null ? null : user.email(), action, entityType, entityId, details);
    }

    /**
     * Registra una accion del usuario actual en la auditoria de una empresa concreta.
     * Necesario cuando el usuario no pertenece a la empresa afectada: p. ej. un cliente paga
     * un pedido y la operacion economica debe constar en la auditoria del organizador.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInTenant(UUID tenantId, AuditAction action, String entityType, Object entityId, String details) {
        CurrentUser user = isAuthenticated() ? currentUserProvider.get() : null;
        save(tenantId, user == null ? null : user.userId(), user == null ? null : user.email(), action, entityType,
                entityId, details);
    }

    /** Registra una accion indicando explicitamente el usuario (login, cuando aun no hay token). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFor(UUID tenantId, UUID userId, String email, AuditAction action, String details) {
        save(tenantId, userId, email, action, "User", userId, details);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(String action, int page, int size) {
        UUID tenantId = currentUserProvider.requireTenantId();
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        var result = (action == null || action.isBlank())
                ? auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                : auditLogRepository.findByTenantIdAndActionOrderByCreatedAtDesc(tenantId, action, pageable);
        return PageResponse.of(result, log -> new AuditLogResponse(log.getId(), log.getUserEmail(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt()));
    }

    private void save(UUID tenantId, UUID userId, String email, AuditAction action, String entityType,
                      Object entityId, String details) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setUserEmail(email);
        log.setAction(action.name());
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : entityId.toString());
        log.setDetails(details == null ? null : truncate(details, 1000));
        log.setIpAddress(currentIp());
        log.setCreatedAt(Instant.now(clock));
        auditLogRepository.save(log);
    }

    private static boolean isAuthenticated() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Jwt;
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
