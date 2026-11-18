package com.bloop.notifications.service;

import com.bloop.notifications.dto.response.AdminAlertResponse;
import com.bloop.notifications.dto.response.AlertsResponse;
import com.bloop.notifications.dto.response.NotificationLogResponse;
import com.bloop.notifications.entity.AdminAlert;
import com.bloop.notifications.entity.AlertType;
import com.bloop.notifications.repository.AdminAlertRepository;
import com.bloop.notifications.repository.NotificationRepository;
import com.bloop.shared.exception.NotFoundException;
import com.bloop.shared.response.PageResponse;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Alertas administrativas: avisos para el equipo de la empresa que se muestran en la
 * campana del backoffice. Las alertas son de la empresa (no de cada empleado):
 * cuando alguien las marca como leidas, quedan leidas para todos.
 */
@Service
@RequiredArgsConstructor
public class AdminAlertService {

    private static final int LATEST_ALERTS = 30;

    private final AdminAlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /**
     * Crea una alerta en la transaccion actual (si la operacion que la origina falla, la alerta
     * tampoco se guarda). Con entityId, la misma alerta para la misma entidad solo se crea una vez.
     */
    @Transactional
    public void raise(UUID tenantId, AlertType type, String title, String message, String link, UUID entityId) {
        alertRepository.insertIfAbsent(tenantId, type.name(), truncate(title, 200), truncate(message, 1000), link,
                entityId, Instant.now(clock));
    }

    /** Retira la alerta de una entidad (la situacion se ha resuelto) para que pueda volver a saltar. */
    @Transactional
    public void clear(UUID tenantId, AlertType type, UUID entityId) {
        alertRepository.deleteForEntity(tenantId, type, entityId);
    }

    @Transactional(readOnly = true)
    public AlertsResponse latest() {
        UUID tenantId = currentUserProvider.requireTenantId();
        var alerts = alertRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, LATEST_ALERTS))
                .map(this::toResponse).getContent();
        return new AlertsResponse(alertRepository.countByTenantIdAndReadAtIsNull(tenantId), alerts);
    }

    @Transactional
    public void markRead(UUID id) {
        UUID tenantId = currentUserProvider.requireTenantId();
        AdminAlert alert = alertRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tenantId))
                .orElseThrow(() -> NotFoundException.of("Alerta"));
        if (alert.getReadAt() == null) {
            alert.setReadAt(Instant.now(clock));
        }
    }

    @Transactional
    public void markAllRead() {
        alertRepository.markAllRead(currentUserProvider.requireTenantId(), Instant.now(clock));
    }

    /** Historial de correos enviados por la empresa. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationLogResponse> emailLog(int page, int size) {
        var result = notificationRepository.findByTenantIdOrderByCreatedAtDesc(currentUserProvider.requireTenantId(),
                PageRequest.of(page, Math.min(size, 100)));
        return PageResponse.of(result, n -> new NotificationLogResponse(n.getId(), n.getRecipientEmail(),
                n.getType(), n.getSubject(), n.getBody(), n.getHtmlBody(), n.getStatus(), n.getErrorMessage(), n.getCreatedAt()));
    }

    private AdminAlertResponse toResponse(AdminAlert a) {
        return new AdminAlertResponse(a.getId(), a.getType(), a.getTitle(), a.getMessage(), a.getLink(),
                a.getReadAt() != null, a.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
