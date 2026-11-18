-- =====================================================================
-- V7 - Reservas VIP: zonas, mesas y reservas sin solapamientos.
-- =====================================================================

-- btree_gist permite usar "=" sobre UUID dentro de una restriccion EXCLUDE (indice GiST).
-- Es una extension "trusted": la puede crear el propietario de la base de datos.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Zona VIP de un local (p. ej. "Reservado escenario", "Terraza alta").
CREATE TABLE vip_zones (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL REFERENCES tenants (id),
    venue_id     UUID         NOT NULL REFERENCES venues (id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_vip_zones_venue ON vip_zones (venue_id);

-- Mesa de una zona, con su capacidad, consumo minimo y anticipo.
CREATE TABLE vip_tables (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    zone_id         UUID          NOT NULL REFERENCES vip_zones (id) ON DELETE CASCADE,
    name            VARCHAR(60)   NOT NULL,
    capacity        INTEGER       NOT NULL,
    min_spend       NUMERIC(10, 2) NOT NULL DEFAULT 0,
    deposit_amount  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_vip_tables_capacity CHECK (capacity BETWEEN 1 AND 100),
    CONSTRAINT ck_vip_tables_amounts CHECK (min_spend >= 0 AND deposit_amount >= 0)
);
CREATE INDEX ix_vip_tables_zone ON vip_tables (zone_id);
CREATE UNIQUE INDEX ux_vip_tables_zone_name ON vip_tables (zone_id, LOWER(name));

CREATE TABLE reservations (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    venue_id          UUID          NOT NULL REFERENCES venues (id),
    table_id          UUID          NOT NULL REFERENCES vip_tables (id),
    -- Sesion del evento (opcional: tambien se puede reservar una noche sin evento).
    session_id        UUID REFERENCES event_sessions (id),
    customer_id       UUID REFERENCES customers (id) ON DELETE SET NULL,
    customer_user_id  UUID REFERENCES users (id),
    reference         VARCHAR(20)   NOT NULL UNIQUE,
    contact_name      VARCHAR(200)  NOT NULL,
    contact_email     VARCHAR(255)  NOT NULL,
    contact_phone     VARCHAR(40),
    guests            INTEGER       NOT NULL,
    starts_at         TIMESTAMPTZ   NOT NULL,
    ends_at           TIMESTAMPTZ   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    -- Importes "congelados" al reservar: si la mesa cambia de precio, la reserva no cambia.
    min_spend         NUMERIC(10, 2) NOT NULL,
    deposit_amount    NUMERIC(10, 2) NOT NULL,
    deposit_paid_at   TIMESTAMPTZ,
    notes             VARCHAR(1000),
    source            VARCHAR(20)   NOT NULL,
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_reservations_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_reservations_guests CHECK (guests > 0),
    CONSTRAINT ck_reservations_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_reservations_source CHECK (source IN ('WEB', 'BACKOFFICE')),
    -- PREVENCION DE SOLAPAMIENTOS: dos reservas activas de la misma mesa no pueden
    -- tener intervalos de tiempo que se crucen. PostgreSQL lo garantiza incluso con
    -- peticiones simultaneas (la segunda falla con exclusion_violation).
    CONSTRAINT ex_reservations_no_overlap EXCLUDE USING gist (
        table_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED', 'SEATED'))
);
CREATE INDEX ix_reservations_tenant_starts ON reservations (tenant_id, starts_at);
CREATE INDEX ix_reservations_customer_user ON reservations (customer_user_id);
CREATE INDEX ix_reservations_customer ON reservations (customer_id);

-- ---------------------------------------------------------------------
-- Permisos nuevos (zonas y mesas se configuran con VENUE_MANAGE)
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, description) VALUES
    ('RESERVATION_VIEW',   'Consultar las reservas VIP'),
    ('RESERVATION_MANAGE', 'Crear, confirmar y cancelar reservas VIP y registrar anticipos');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'RESERVATION_VIEW'),
    ('TENANT_ADMIN', 'RESERVATION_MANAGE'),
    ('MANAGER', 'RESERVATION_VIEW'),
    ('MANAGER', 'RESERVATION_MANAGE'),
    ('DOOR_STAFF', 'RESERVATION_VIEW'),
    ('CASHIER', 'RESERVATION_VIEW');
