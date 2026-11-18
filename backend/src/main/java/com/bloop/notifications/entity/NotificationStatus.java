package com.bloop.notifications.entity;

/** SENT: enviado por SMTP. LOGGED: correo desactivado, solo se escribio en el log. FAILED: error SMTP. */
public enum NotificationStatus {
    SENT,
    LOGGED,
    FAILED
}
