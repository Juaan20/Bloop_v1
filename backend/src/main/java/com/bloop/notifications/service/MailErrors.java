package com.bloop.notifications.service;

import jakarta.mail.AuthenticationFailedException;
import org.springframework.mail.MailAuthenticationException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Traduce los errores SMTP a un mensaje que explica la causa probable y que revisar.
 * Se guarda en el historial de correos y se escribe en el log. Nunca incluye la contrasena.
 */
public final class MailErrors {

    private MailErrors() {
    }

    public static String describe(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof AuthenticationFailedException || t instanceof MailAuthenticationException) {
                return "Autenticacion SMTP rechazada: revisa MAIL_USERNAME y MAIL_PASSWORD "
                        + "(en Gmail hace falta una contrasena de aplicacion). Detalle: " + safe(t);
            }
            if (t instanceof UnknownHostException) {
                return "No se encuentra el servidor SMTP '" + t.getMessage() + "': revisa MAIL_HOST.";
            }
            if (t instanceof ConnectException) {
                return "Conexion rechazada por el servidor SMTP: revisa MAIL_HOST y MAIL_PORT. Dentro de Docker, "
                        + "'localhost' es el propio contenedor (usa 'mailpit' o el host de tu proveedor). Detalle: "
                        + safe(t);
            }
            if (t instanceof SocketTimeoutException) {
                return "Tiempo de espera agotado con el servidor SMTP: el puerto puede estar bloqueado "
                        + "(prueba 587 con STARTTLS) o el host no responde. Detalle: " + safe(t);
            }
            if (t instanceof SSLException) {
                return "Error TLS/SSL: con el puerto 587 usa MAIL_STARTTLS_ENABLE=true y MAIL_SSL_ENABLE=false; "
                        + "con el 465, MAIL_SSL_ENABLE=true. Detalle: " + safe(t);
            }
            String message = t.getMessage();
            if (message != null && message.contains("STARTTLS")) {
                return "El servidor exige STARTTLS: pon MAIL_STARTTLS_ENABLE=true. Detalle: " + safe(t);
            }
            if (message != null && (message.contains("Sender address rejected") || message.contains("553")
                    || message.contains("550 5.7"))) {
                return "El servidor rechaza el remitente: MAIL_FROM debe ser una direccion autorizada en tu "
                        + "proveedor (normalmente la misma cuenta o un dominio verificado). Detalle: " + safe(t);
            }
        }
        return "Error SMTP: " + safe(error);
    }

    private static String safe(Throwable t) {
        String message = t.getMessage();
        return message == null ? t.getClass().getSimpleName() : message.replaceAll("\\s+", " ").trim();
    }
}
