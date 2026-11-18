package com.bloop.notifications.repository;

import com.bloop.notifications.entity.Notification;
import com.bloop.notifications.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    /** Historial de correos de una empresa (backoffice). */
    Page<Notification> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /** Para limitar reenvios: hay ya un correo de ese tipo enviado a ese destinatario despues de "since"? */
    boolean existsByRecipientEmailAndTypeAndCreatedAtAfter(String recipientEmail, NotificationType type,
                                                           Instant since);
}
