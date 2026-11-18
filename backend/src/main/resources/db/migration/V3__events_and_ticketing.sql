-- =====================================================================
-- V3 - Eventos, sesiones, tipos de entrada, pedidos, pagos, entradas
--      y control de acceso.
--
-- Relaciones principales:
--   venue 1--N event 1--N event_session 1--N ticket_type
--   ticket_order 1--N ticket_order_item 1--N ticket
--   ticket_order 1--N payment
--   ticket 1--N access_validation
-- Todas las tablas de negocio llevan tenant_id para filtrar por empresa.
-- =====================================================================

CREATE TABLE events (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    venue_id      UUID         NOT NULL REFERENCES venues (id),
    name          VARCHAR(200) NOT NULL,
    slug          VARCHAR(220) NOT NULL UNIQUE,
    description   TEXT,
    image_url     VARCHAR(500),
    min_age       INTEGER,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT ck_events_min_age CHECK (min_age IS NULL OR min_age BETWEEN 0 AND 99)
);
CREATE INDEX ix_events_tenant ON events (tenant_id);
CREATE INDEX ix_events_venue ON events (venue_id);
CREATE INDEX ix_events_status ON events (status);

-- Una sesion es una fecha concreta de un evento (un evento puede repetirse).
CREATE TABLE event_sessions (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL REFERENCES tenants (id),
    event_id       UUID        NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    starts_at      TIMESTAMPTZ NOT NULL,
    ends_at        TIMESTAMPTZ NOT NULL,
    doors_open_at  TIMESTAMPTZ,
    capacity       INTEGER     NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sessions_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_sessions_capacity CHECK (capacity > 0),
    CONSTRAINT ck_sessions_status CHECK (status IN ('SCHEDULED', 'CANCELLED'))
);
CREATE INDEX ix_sessions_event ON event_sessions (event_id);
CREATE INDEX ix_sessions_starts ON event_sessions (starts_at);

-- Tipos de entrada de una sesion. quantity_sold se actualiza con un UPDATE
-- condicional atomico, y el CHECK impide sobreventa incluso ante errores de codigo.
CREATE TABLE ticket_types (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    session_id      UUID          NOT NULL REFERENCES event_sessions (id) ON DELETE CASCADE,
    name            VARCHAR(120)  NOT NULL,
    description     VARCHAR(500),
    category        VARCHAR(20)   NOT NULL,
    price           NUMERIC(10, 2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    quantity_total  INTEGER       NOT NULL,
    quantity_sold   INTEGER       NOT NULL DEFAULT 0,
    max_per_order   INTEGER       NOT NULL DEFAULT 10,
    sales_start_at  TIMESTAMPTZ,
    sales_end_at    TIMESTAMPTZ,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_tt_category CHECK (category IN ('GENERAL', 'EARLY_BIRD', 'VIP', 'FREE')),
    CONSTRAINT ck_tt_price CHECK (price >= 0),
    CONSTRAINT ck_tt_free_price CHECK (category <> 'FREE' OR price = 0),
    CONSTRAINT ck_tt_quantities CHECK (quantity_total >= 0 AND quantity_sold >= 0 AND quantity_sold <= quantity_total),
    CONSTRAINT ck_tt_max_per_order CHECK (max_per_order BETWEEN 1 AND 50),
    CONSTRAINT ck_tt_sales_window CHECK (sales_end_at IS NULL OR sales_start_at IS NULL OR sales_end_at > sales_start_at)
);
CREATE INDEX ix_ticket_types_session ON ticket_types (session_id);

-- Pedido de entradas. customer_user_id es el cliente que compra.
-- (customer_user_id, idempotency_key) unico => reintentos del mismo checkout no duplican pedidos.
CREATE TABLE ticket_orders (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    reference         VARCHAR(20)   NOT NULL UNIQUE,
    customer_user_id  UUID          NOT NULL REFERENCES users (id),
    session_id        UUID          NOT NULL REFERENCES event_sessions (id),
    status            VARCHAR(20)   NOT NULL,
    total_amount      NUMERIC(10, 2) NOT NULL,
    currency          VARCHAR(3)    NOT NULL DEFAULT 'EUR',
    buyer_email       VARCHAR(255)  NOT NULL,
    buyer_name        VARCHAR(200)  NOT NULL,
    idempotency_key   VARCHAR(80)   NOT NULL,
    expires_at        TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0),
    CONSTRAINT ux_orders_idempotency UNIQUE (customer_user_id, idempotency_key)
);
CREATE INDEX ix_orders_tenant_created ON ticket_orders (tenant_id, created_at DESC);
CREATE INDEX ix_orders_customer ON ticket_orders (customer_user_id);
CREATE INDEX ix_orders_session ON ticket_orders (session_id);
CREATE INDEX ix_orders_pending_expiry ON ticket_orders (expires_at) WHERE status = 'PENDING_PAYMENT';

-- Lineas del pedido. Guardamos nombre y precio "congelados" en el momento de la compra.
CREATE TABLE ticket_order_items (
    id                UUID PRIMARY KEY,
    order_id          UUID          NOT NULL REFERENCES ticket_orders (id) ON DELETE CASCADE,
    ticket_type_id    UUID          NOT NULL REFERENCES ticket_types (id),
    ticket_type_name  VARCHAR(120)  NOT NULL,
    quantity          INTEGER       NOT NULL,
    unit_price        NUMERIC(10, 2) NOT NULL,
    line_total        NUMERIC(10, 2) NOT NULL,
    CONSTRAINT ck_items_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_order_items_order ON ticket_order_items (order_id);

-- Pagos. No se guarda NINGUN dato de tarjeta: solo el identificador del proveedor.
CREATE TABLE payments (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL REFERENCES tenants (id),
    order_id             UUID          NOT NULL REFERENCES ticket_orders (id),
    provider             VARCHAR(20)   NOT NULL,
    provider_payment_id  VARCHAR(100)  NOT NULL UNIQUE,
    status               VARCHAR(20)   NOT NULL,
    amount               NUMERIC(10, 2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    failure_reason       VARCHAR(255),
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('SIMULATED', 'STRIPE', 'FREE'))
);
CREATE INDEX ix_payments_order ON payments (order_id);

-- Entradas emitidas. "code" es un valor aleatorio de 128 bits; el QR contiene code + firma HMAC.
CREATE TABLE tickets (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    order_id        UUID         NOT NULL REFERENCES ticket_orders (id),
    order_item_id   UUID         NOT NULL REFERENCES ticket_order_items (id),
    ticket_type_id  UUID         NOT NULL REFERENCES ticket_types (id),
    session_id      UUID         NOT NULL REFERENCES event_sessions (id),
    -- Cliente propietario de la entrada (permite listar "mis entradas" sin pasar por el pedido).
    owner_user_id   UUID         NOT NULL REFERENCES users (id),
    code            VARCHAR(40)  NOT NULL UNIQUE,
    holder_name     VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'VALID',
    used_at         TIMESTAMPTZ,
    used_by_user_id UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_tickets_status CHECK (status IN ('VALID', 'USED', 'CANCELLED'))
);
CREATE INDEX ix_tickets_order ON tickets (order_id);
CREATE INDEX ix_tickets_session_status ON tickets (session_id, status);
CREATE INDEX ix_tickets_owner ON tickets (owner_user_id);

-- Historial de cada escaneo (aceptado o rechazado) en la puerta.
CREATE TABLE access_validations (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID        NOT NULL REFERENCES tenants (id),
    session_id         UUID        NOT NULL REFERENCES event_sessions (id),
    ticket_id          UUID REFERENCES tickets (id),
    validated_by       UUID        NOT NULL REFERENCES users (id),
    result             VARCHAR(30) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_access_result CHECK (result IN
        ('ACCEPTED', 'ALREADY_USED', 'INVALID_CODE', 'WRONG_SESSION', 'CANCELLED', 'NOT_PAID'))
);
CREATE INDEX ix_access_session_created ON access_validations (session_id, created_at DESC);
CREATE INDEX ix_access_tenant_created ON access_validations (tenant_id, created_at DESC);
