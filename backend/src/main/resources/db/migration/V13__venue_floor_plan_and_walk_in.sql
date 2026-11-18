-- =====================================================================
-- V13 - Forma del local (cada discoteca es distinta) y mesas vendidas en taquilla.
-- =====================================================================

-- Plano del local: rejilla de celdas pintadas (forma en L, U, T, libre...) y elementos
-- (barra, escenario, cabina DJ, pista, entrada, banos, columnas...). Se guarda como JSON validado
-- por el backend (FloorPlanRequest); las mesas siguen en vip_tables con su posicion en %.
CREATE TABLE venue_floor_plans (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    venue_id    UUID        NOT NULL UNIQUE REFERENCES venues (id) ON DELETE CASCADE,
    layout      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

-- Venta de mesa en taquilla: el grupo llega sin reserva y puede no dar email.
ALTER TABLE reservations ALTER COLUMN contact_email DROP NOT NULL;
ALTER TABLE reservations DROP CONSTRAINT ck_reservations_source;
ALTER TABLE reservations ADD CONSTRAINT ck_reservations_source CHECK (source IN ('WEB', 'BACKOFFICE', 'WALK_IN'));

-- Metodo con el que se pago la reserva + paquete (para cuadrar caja). NULL en reservas antiguas.
ALTER TABLE reservations ADD COLUMN prepaid_method VARCHAR(10);
ALTER TABLE reservations ADD CONSTRAINT ck_reservations_prepaid_method
    CHECK (prepaid_method IS NULL OR prepaid_method IN ('CASH', 'CARD'));

-- Terminal de servicio VIP: vende botellas y las carga a la cuenta de una mesa ocupada.
ALTER TABLE pos_terminals DROP CONSTRAINT ck_pos_terminals_type;
ALTER TABLE pos_terminals ADD CONSTRAINT ck_pos_terminals_type CHECK (type IN ('BAR', 'BOX_OFFICE', 'VIP_SERVICE'));
