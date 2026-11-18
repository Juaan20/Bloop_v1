package com.bloop.notifications.dto.response;

import com.bloop.notifications.entity.NotificationStatus;

/**
 * Resultado de un correo de prueba.
 *
 * @param status       SENT (el servidor SMTP lo acepto), LOGGED (modo pruebas: no se envio) o FAILED
 * @param errorMessage causa probable si fallo
 * @param mailEnabled  si el envio real esta activado (MAIL_ENABLED)
 */
public record TestEmailResponse(String recipient, NotificationStatus status, String errorMessage,
                                boolean mailEnabled) {
}
