package com.bloop.authentication.service;

import com.bloop.audit.entity.AuditAction;
import com.bloop.audit.service.AuditService;
import com.bloop.authentication.security.LoginAttemptService;
import com.bloop.authentication.entity.PasswordResetToken;
import com.bloop.authentication.repository.PasswordResetTokenRepository;
import com.bloop.notifications.entity.NotificationType;
import com.bloop.notifications.service.EmailMessage;
import com.bloop.notifications.service.NotificationService;
import com.bloop.shared.config.BloopProperties;
import com.bloop.shared.exception.BadRequestException;
import com.bloop.shared.util.SecureTokens;
import com.bloop.users.entity.User;
import com.bloop.users.repository.UserRepository;
import com.bloop.users.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Recuperacion de contrasena por email.
 * Por seguridad, la solicitud responde igual exista o no el email (no revela cuentas).
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final BloopProperties properties;
    private final Clock clock;

    @Transactional
    public void requestReset(String email) {
        loginAttemptService.assertPasswordResetAllowed();
        userRepository.findByEmail(UserAccountService.normalizeEmail(email))
                .filter(User::isActive)
                .ifPresent(this::createAndSendToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken token = resetTokenRepository.findByTokenHash(SecureTokens.sha256(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BadRequestException("El enlace no es valido o ha caducado"));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("El enlace no es valido o ha caducado"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);
        // Cerramos todas las sesiones abiertas con la contrasena antigua.
        refreshTokenService.revokeAll(user.getId());
        auditService.recordFor(user.getTenantId(), user.getId(), user.getEmail(), AuditAction.PASSWORD_RESET, null);
    }

    private void createAndSendToken(User user) {
        String raw = SecureTokens.randomToken(32);
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(SecureTokens.sha256(raw));
        Instant now = Instant.now(clock);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
        resetTokenRepository.save(token);

        String link = properties.publicUrl() + "/restablecer-contrasena?token=" + raw;
        EmailMessage message = EmailMessage.of("Restablece tu contrasena", "Hola " + user.getFirstName() + ",")
                .paragraph("Hemos recibido una solicitud para cambiar la contrasena de tu cuenta de Bloop.")
                .button("Elegir una nueva contrasena", link)
                .footnote("El enlace caduca en 1 hora y solo se puede usar una vez. Si no lo has solicitado tu, "
                        + "ignora este mensaje: tu contrasena no cambiara.");
        notificationService.sendEmail(user.getTenantId(), user.getEmail(), NotificationType.PASSWORD_RESET,
                "Restablece tu contrasena de Bloop", message);
    }
}
