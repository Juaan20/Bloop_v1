package com.bloop.notifications.service;

import com.bloop.shared.config.BloopProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Al arrancar escribe en el log la configuracion de correo efectiva (sin la contrasena) y avisa
 * de los errores de configuracion mas habituales. Asi un "no llegan los correos" se diagnostica
 * leyendo las primeras lineas del log.
 */
@Component
@RequiredArgsConstructor
public class MailDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(MailDiagnostics.class);

    private final BloopProperties properties;
    private final Environment env;

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (!properties.mail().enabled()) {
            log.info("Correo en MODO PRUEBAS (MAIL_ENABLED=false): los correos NO se envian; se guardan en el "
                    + "historial de correos del backoffice y se escriben en este log.");
            return;
        }
        String host = env.getProperty("spring.mail.host", "");
        String port = env.getProperty("spring.mail.port", "");
        String username = env.getProperty("spring.mail.username", "");
        boolean auth = Boolean.parseBoolean(env.getProperty("spring.mail.properties.mail.smtp.auth", "false"));
        boolean starttls = Boolean.parseBoolean(
                env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "false"));
        boolean ssl = Boolean.parseBoolean(env.getProperty("spring.mail.properties.mail.smtp.ssl.enable", "false"));
        log.info("Correo ACTIVADO: host={} puerto={} auth={} starttls={} ssl={} usuario={} remitente={}",
                host, port, auth, starttls, ssl, username.isBlank() ? "(ninguno)" : username,
                properties.mail().from());

        if (insideContainer() && ("localhost".equals(host) || "127.0.0.1".equals(host))) {
            log.warn("MAIL_HOST={} dentro de un contenedor apunta al propio contenedor del backend, no a tu "
                    + "maquina. Usa 'mailpit' (servicio de docker-compose) o el host SMTP de tu proveedor.", host);
        }
        if (auth && (username.isBlank() || env.getProperty("spring.mail.password", "").isBlank())) {
            log.warn("MAIL_SMTP_AUTH=true pero falta MAIL_USERNAME o MAIL_PASSWORD: el servidor rechazara el envio.");
        }
        if (!auth && !"1025".equals(port)) {
            log.warn("MAIL_SMTP_AUTH=false con el puerto {}: casi todos los proveedores reales exigen autenticacion.",
                    port);
        }
        if ("587".equals(port) && !starttls) {
            log.warn("Puerto 587 sin STARTTLS: pon MAIL_STARTTLS_ENABLE=true.");
        }
        if ("465".equals(port) && !ssl) {
            log.warn("Puerto 465 sin SSL: pon MAIL_SSL_ENABLE=true (y MAIL_STARTTLS_ENABLE=false).");
        }
    }

    /** Docker crea /.dockerenv en todos los contenedores. */
    private static boolean insideContainer() {
        return Files.exists(Path.of("/.dockerenv"));
    }
}
