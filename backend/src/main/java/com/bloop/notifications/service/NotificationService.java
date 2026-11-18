package com.bloop.notifications.service;

import com.bloop.notifications.entity.Notification;
import com.bloop.notifications.entity.NotificationStatus;
import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.repository.NotificationRepository;
import com.bloop.shared.config.BloopProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Envia los correos de la aplicacion (HTML + texto plano) y guarda una copia en la tabla notifications.
 *
 * - bloop.mail.enabled=true: se envian por SMTP (Mailpit en local con Docker, o tu proveedor real).
 * - bloop.mail.enabled=false (modo pruebas): NO se envian; se guardan con estado LOGGED y se
 *   escriben en el log. El historial de correos del backoffice muestra claramente que no salieron.
 *
 * Un fallo de correo NUNCA rompe la operacion principal (una compra, por ejemplo): queda FAILED con
 * el motivo, que se ve en el historial de correos y en el log.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer renderer;
    private final BloopProperties properties;
    private final Clock clock;

    /** Se envio ya un correo de ese tipo a ese destinatario despues de "since"? (limita reenvios). */
    @Transactional(readOnly = true)
    public boolean wasSentSince(String recipientEmail, NotificationType type, Instant since) {
        return notificationRepository.existsByRecipientEmailAndTypeAndCreatedAtAfter(recipientEmail, type, since);
    }

    /** Envia un correo y devuelve la copia guardada (con su estado: SENT, LOGGED o FAILED). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification sendEmail(UUID tenantId, String to, NotificationType type, String subject,
                                  EmailMessage message) {
        String text = renderer.text(message);
        String html = renderer.html(message);

        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setRecipientEmail(to);
        notification.setType(type);
        notification.setSubject(subject);
        notification.setCreatedAt(Instant.now(clock));
        // La copia guardada del correo de contrasena no lleva el enlace: quien vea el historial
        // (un administrador) no debe poder usarlo para entrar en la cuenta de otra persona.
        boolean secret = type == NotificationType.PASSWORD_RESET;
        notification.setBody(secret ? redactLinks(text) : text);
        notification.setHtmlBody(secret ? redactLinks(html) : html);

        if (!properties.mail().enabled()) {
            log.info("[CORREO EN MODO PRUEBAS - NO ENVIADO] Para: {} | Asunto: {}\n{}", to, subject, text);
            notification.setStatus(NotificationStatus.LOGGED);
        } else {
            try {
                mailSender.send(build(to, subject, text, html, message));
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(Instant.now(clock));
                log.info("Correo enviado a {} ({})", to, type);
            } catch (MailException | MessagingException | UnsupportedEncodingException e) {
                String reason = MailErrors.describe(e);
                log.warn("No se pudo enviar el correo a {} ({}): {}", to, type, reason);
                notification.setStatus(NotificationStatus.FAILED);
                notification.setErrorMessage(reason.substring(0, Math.min(500, reason.length())));
            }
        }
        return notificationRepository.save(notification);
    }

    private MimeMessage build(String to, String subject, String text, String html, EmailMessage message)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mime = mailSender.createMimeMessage();
        // multipart: texto + HTML + imagenes incrustadas (QR).
        MimeMessageHelper helper = new MimeMessageHelper(mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());
        var mail = properties.mail();
        helper.setFrom(new InternetAddress(mail.from(), mail.fromName() == null ? "Bloop" : mail.fromName(),
                StandardCharsets.UTF_8.name()));
        if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
            helper.setReplyTo(mail.replyTo());
        }
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        for (EmailMessage.InlineImage image : message.images()) {
            helper.addInline(image.contentId(), new ByteArrayResource(image.png()), "image/png");
        }
        return mime;
    }

    private static String redactLinks(String content) {
        return content.replaceAll("token=[A-Za-z0-9_\\-]+", "token=[oculto]");
    }
}
