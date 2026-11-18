-- =====================================================================
-- V4 - Auditoria y notificaciones.
-- =====================================================================

-- Registro inmutable de acciones relevantes (logins, cambios, operaciones economicas...).
CREATE TABLE audit_logs (
    id           UUID PRIMARY KEY,
    tenant_id    UUID REFERENCES tenants (id),
    user_id      UUID REFERENCES users (id),
    user_email   VARCHAR(255),
    action       VARCHAR(60)  NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    VARCHAR(60),
    details      VARCHAR(1000),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_tenant_created ON audit_logs (tenant_id, created_at DESC);

-- Notificaciones enviadas (o registradas en log si el correo esta desactivado).
CREATE TABLE notifications (
    id               UUID PRIMARY KEY,
    tenant_id        UUID REFERENCES tenants (id),
    recipient_email  VARCHAR(255) NOT NULL,
    type             VARCHAR(40)  NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    body             TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    error_message    VARCHAR(500),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_notifications_status CHECK (status IN ('SENT', 'LOGGED', 'FAILED'))
);
CREATE INDEX ix_notifications_recipient ON notifications (LOWER(recipient_email));
