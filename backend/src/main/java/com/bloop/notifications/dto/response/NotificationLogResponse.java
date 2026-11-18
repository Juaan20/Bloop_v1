package com.bloop.notifications.dto.response;

import com.bloop.notifications.entity.NotificationStatus;
import com.bloop.notifications.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

/** Correo enviado (o registrado en el log si el correo esta desactivado). */
public record NotificationLogResponse(UUID id, String recipientEmail, NotificationType type, String subject,
                                      String body, String htmlBody, NotificationStatus status, String errorMessage,
                                      Instant createdAt) {
}
