-- =====================================================================
-- V8 - Alertas administrativas y cambios de titular de entradas.
-- =====================================================================

-- Avisos internos para el equipo de una empresa (campana del backoffice).
CREATE TABLE admin_alerts (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    type         VARCHAR(40)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    -- Ruta del backoffice relacionada (p. ej. /admin/reservas).
    link         VARCHAR(255),
    -- Entidad que origino la alerta: evita duplicar la misma alerta.
    entity_id    UUID,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_admin_alerts_tenant_created ON admin_alerts (tenant_id, created_at DESC);
CREATE UNIQUE INDEX ux_admin_alerts_type_entity ON admin_alerts (tenant_id, type, entity_id) WHERE entity_id IS NOT NULL;

-- Historial de cambios de titular (quien tenia la entrada y a quien se paso).
CREATE TABLE ticket_transfers (
    id                UUID PRIMARY KEY,
    tenant_id         UUID         NOT NULL REFERENCES tenants (id),
    ticket_id         UUID         NOT NULL REFERENCES tickets (id),
    from_user_id      UUID REFERENCES users (id),
    to_user_id        UUID REFERENCES users (id),
    from_holder_name  VARCHAR(200) NOT NULL,
    to_holder_name    VARCHAR(200) NOT NULL,
    -- TRANSFER: cambia el propietario (nuevo QR). RENAME: solo cambia el nombre del titular.
    type              VARCHAR(20)  NOT NULL,
    performed_by      UUID REFERENCES users (id),
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_ticket_transfers_type CHECK (type IN ('TRANSFER', 'RENAME'))
);
CREATE INDEX ix_ticket_transfers_ticket ON ticket_transfers (ticket_id, created_at DESC);
