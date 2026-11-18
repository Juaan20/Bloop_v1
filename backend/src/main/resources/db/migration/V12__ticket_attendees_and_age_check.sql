-- =====================================================================
-- V12 - Datos de los asistentes de cada entrada y control de edad.
--
-- Proteccion del documento de identidad (minimizacion de datos, RGPD):
--   * NO se guarda el numero completo: solo el tipo y los 4 ultimos caracteres, suficientes
--     para que el personal de puerta compare con el documento fisico.
--   * La fecha de nacimiento solo se pide si el evento tiene edad minima.
-- =====================================================================

ALTER TABLE ticket_orders ADD COLUMN buyer_phone VARCHAR(40);

-- Asistentes indicados en la compra (uno por entrada). Se copian a las entradas al confirmarse el pago.
CREATE TABLE order_attendees (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID         NOT NULL REFERENCES tenants (id),
    order_id               UUID         NOT NULL REFERENCES ticket_orders (id) ON DELETE CASCADE,
    ticket_type_id         UUID         NOT NULL REFERENCES ticket_types (id),
    position               INTEGER      NOT NULL,
    first_name             VARCHAR(100) NOT NULL,
    last_name              VARCHAR(100) NOT NULL,
    birth_date             DATE,
    document_type          VARCHAR(10),
    document_last4         VARCHAR(4),
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_order_attendees_document CHECK (document_type IS NULL OR document_type IN ('DNI', 'NIE', 'PASSPORT')),
    CONSTRAINT ux_order_attendees_position UNIQUE (order_id, position)
);
CREATE INDEX ix_order_attendees_order ON order_attendees (order_id);

ALTER TABLE tickets
    ADD COLUMN holder_birth_date      DATE,
    ADD COLUMN holder_document_type   VARCHAR(10),
    ADD COLUMN holder_document_last4  VARCHAR(4);
