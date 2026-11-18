package com.bloop.notifications.service;

import com.bloop.notifications.dto.response.TestEmailResponse;
import com.bloop.notifications.entity.Notification;
import com.bloop.notifications.entity.NotificationType;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.security.CurrentUser;
import com.bloop.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Correo de prueba para comprobar la configuracion SMTP desde el backoffice.
 * Solo se envia al email del propio usuario: el endpoint no sirve para mandar correos a terceros.
 */
@Service
@RequiredArgsConstructor
public class MailTestService {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final BloopProperties properties;

    public TestEmailResponse sendToMe() {
        CurrentUser user = currentUserProvider.get();
        EmailMessage message = EmailMessage.of("Correo de prueba", "Hola,")
                .paragraph("Si estas leyendo esto, Bloop puede enviar correos con la configuracion actual.")
                .detail("Remitente", properties.mail().from())
                .button("Abrir Bloop", properties.publicUrl());
        Notification sent = notificationService.sendEmail(user.tenantId(), user.email(), NotificationType.TEST,
                "Prueba de correo de Bloop", message);
        return new TestEmailResponse(user.email(), sent.getStatus(), sent.getErrorMessage(),
                properties.mail().enabled());
    }
}
