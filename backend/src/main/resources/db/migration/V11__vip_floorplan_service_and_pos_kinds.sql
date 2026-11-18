-- =====================================================================
-- V11 - Plano VIP, paquetes, QR de mesa, pedidos de servicio VIP, cuenta de mesa
--       y tipos de categoria del TPV (barra / botellas VIP / servicio / otros).
-- =====================================================================

-- ---------------------------------------------------------------- Plano de mesas
-- Posicion y tamano en % del lienzo (0-100) para que el plano se adapte a cualquier pantalla.
-- NULL = la mesa aun no se ha colocado en el plano (el editor la deja en la bandeja lateral).
ALTER TABLE vip_tables
    ADD COLUMN pos_x  NUMERIC(5, 2),
    ADD COLUMN pos_y  NUMERIC(5, 2),
    ADD COLUMN width  NUMERIC(5, 2) NOT NULL DEFAULT 8,
    ADD COLUMN height NUMERIC(5, 2) NOT NULL DEFAULT 8,
    ADD COLUMN shape  VARCHAR(10)   NOT NULL DEFAULT 'ROUND',
    ADD CONSTRAINT ck_vip_tables_shape CHECK (shape IN ('ROUND', 'RECT')),
    ADD CONSTRAINT ck_vip_tables_position CHECK (
        (pos_x IS NULL OR pos_x BETWEEN 0 AND 100) AND (pos_y IS NULL OR pos_y BETWEEN 0 AND 100)
        AND width BETWEEN 2 AND 40 AND height BETWEEN 2 AND 40);

-- ---------------------------------------------------------------- Catalogo del TPV
-- Tipo de categoria: separa la barra normal de las botellas VIP y del resto.
ALTER TABLE product_categories
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'BAR',
    ADD CONSTRAINT ck_product_categories_kind CHECK (kind IN ('BAR', 'VIP_BOTTLE', 'SERVICE', 'OTHER'));

-- Foto del producto (carta VIP). URL externa, igual que la imagen de los eventos.
ALTER TABLE products ADD COLUMN image_url VARCHAR(500);

-- Tipos de categoria que puede vender cada terminal (lista separada por comas).
ALTER TABLE pos_terminals ADD COLUMN allowed_kinds VARCHAR(60) NOT NULL DEFAULT 'BAR';

-- Datos existentes: la taquilla vende "otros" (entradas en puerta, guardarropa) y la barra solo barra.
UPDATE product_categories SET kind = 'OTHER'
 WHERE LOWER(name) LIKE '%taquilla%' OR LOWER(name) LIKE '%guardarrop%';
UPDATE product_categories SET kind = 'VIP_BOTTLE' WHERE LOWER(name) LIKE '%botell%';
UPDATE pos_terminals SET allowed_kinds = 'OTHER' WHERE type = 'BOX_OFFICE';

-- ---------------------------------------------------------------- Paquete VIP de cada mesa
-- Productos incluidos en el precio de la reserva (p. ej. 1 botella de ginebra + 6 refrescos).
CREATE TABLE vip_table_package_items (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    table_id    UUID        NOT NULL REFERENCES vip_tables (id) ON DELETE CASCADE,
    variant_id  UUID        NOT NULL REFERENCES product_variants (id),
    quantity    INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_vip_package_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ux_vip_package_item UNIQUE (table_id, variant_id)
);

-- ---------------------------------------------------------------- QR de la mesa reservada
-- Token aleatorio (32 bytes) que abre la carta de la mesa. Se guarda en claro porque el personal
-- necesita volver a mostrarlo; se puede desactivar y deja de valer al cerrar la reserva.
ALTER TABLE reservations
    ADD COLUMN table_token        VARCHAR(64),
    ADD COLUMN table_token_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN account_closed_at  TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_reservations_table_token ON reservations (table_token);

-- Contenido del paquete "congelado" al reservar (si la mesa cambia, la reserva no cambia).
CREATE TABLE reservation_package_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    reservation_id  UUID          NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    variant_id      UUID          NOT NULL REFERENCES product_variants (id),
    product_name    VARCHAR(200)  NOT NULL,
    quantity        INTEGER       NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_res_package_quantity CHECK (quantity > 0),
    CONSTRAINT ux_res_package_item UNIQUE (reservation_id, variant_id)
);

-- ---------------------------------------------------------------- Pedidos de servicio VIP
CREATE TABLE vip_orders (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL REFERENCES tenants (id),
    venue_id           UUID         NOT NULL REFERENCES venues (id),
    reservation_id     UUID         NOT NULL REFERENCES reservations (id),
    source             VARCHAR(10)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    -- Clave que envia el movil/la tablet: un doble toque no crea dos pedidos.
    idempotency_key    VARCHAR(80)  NOT NULL,
    notes              VARCHAR(300),
    acknowledged_at    TIMESTAMPTZ,
    preparing_at       TIMESTAMPTZ,
    prepared_by_id     UUID REFERENCES users (id),
    prepared_by_name   VARCHAR(200),
    ready_at           TIMESTAMPTZ,
    on_the_way_at      TIMESTAMPTZ,
    delivered_by_id    UUID REFERENCES users (id),
    delivered_by_name  VARCHAR(200),
    delivered_at       TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_vip_orders_source CHECK (source IN ('GUEST', 'STAFF')),
    CONSTRAINT ck_vip_orders_status CHECK (status IN
        ('NEW', 'PENDING', 'PREPARING', 'READY', 'ON_THE_WAY', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ux_vip_orders_idempotency UNIQUE (reservation_id, idempotency_key)
);
CREATE INDEX ix_vip_orders_venue_status ON vip_orders (venue_id, status);
CREATE INDEX ix_vip_orders_reservation ON vip_orders (reservation_id);

CREATE TABLE vip_order_items (
    id            UUID PRIMARY KEY,
    tenant_id     UUID          NOT NULL REFERENCES tenants (id),
    order_id      UUID          NOT NULL REFERENCES vip_orders (id) ON DELETE CASCADE,
    variant_id    UUID          NOT NULL REFERENCES product_variants (id),
    product_name  VARCHAR(200)  NOT NULL,
    quantity      INTEGER       NOT NULL,
    unit_price    NUMERIC(10, 2) NOT NULL,
    -- true = sale del paquete ya pagado (importe 0); false = consumicion adicional pendiente de pago.
    included      BOOLEAN       NOT NULL,
    line_total    NUMERIC(10, 2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_vip_order_items CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
);
CREATE INDEX ix_vip_order_items_order ON vip_order_items (order_id);

-- ---------------------------------------------------------------- Cobros de la cuenta de la mesa
CREATE TABLE vip_account_payments (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL REFERENCES tenants (id),
    reservation_id   UUID          NOT NULL REFERENCES reservations (id),
    amount           NUMERIC(10, 2) NOT NULL,
    method           VARCHAR(10)   NOT NULL,
    idempotency_key  VARCHAR(80)   NOT NULL,
    created_by_id    UUID REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_vip_payments CHECK (amount > 0 AND method IN ('CASH', 'CARD')),
    CONSTRAINT ux_vip_payments_idempotency UNIQUE (reservation_id, idempotency_key)
);

-- ---------------------------------------------------------------- Permisos y rol
INSERT INTO permissions (code, description) VALUES
    ('VIP_SERVICE', 'Atender y preparar los pedidos de las mesas VIP');

INSERT INTO roles (code, name, description, assignable_by_tenant) VALUES
    ('VIP_WAITER', 'Camarero VIP', 'Prepara y lleva los pedidos de las mesas VIP', TRUE);

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'VIP_SERVICE'),
    ('MANAGER', 'VIP_SERVICE'),
    ('VIP_WAITER', 'VIP_SERVICE'),
    ('VIP_WAITER', 'RESERVATION_VIEW'),
    ('VIP_WAITER', 'VENUE_VIEW');
