-- =====================================================================
-- V9 - TPV (barra y taquilla), cajas e inventario.
--
--   product_category 1--N product 1--N product_variant
--   venue 1--N warehouse 1--N stock_level (una fila por variante)
--   venue 1--N pos_terminal --> warehouse (de donde descuenta el stock)
--   pos_terminal 1--N cash_session 1--N pos_sale 1--N pos_sale_item
--                                 1--N cash_movement
-- =====================================================================

-- ---------------------------------------------------------------- Catalogo

CREATE TABLE product_categories (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    name        VARCHAR(80)  NOT NULL,
    -- Color del boton en la pantalla tactil (clave de una paleta fija).
    color       VARCHAR(20)  NOT NULL DEFAULT 'violet',
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_product_categories_name ON product_categories (tenant_id, LOWER(name));

CREATE TABLE products (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants (id),
    category_id  UUID          NOT NULL REFERENCES product_categories (id),
    name         VARCHAR(120)  NOT NULL,
    description  VARCHAR(500),
    -- false para productos sin existencias (p. ej. un coctel preparado o una entrada de taquilla).
    track_stock  BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order   INTEGER       NOT NULL DEFAULT 0,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_products_category ON products (category_id);

-- Variante = lo que se vende y tiene precio (Copa, Botella, Cana, Doble...). Todo producto tiene al menos una.
CREATE TABLE product_variants (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    product_id  UUID          NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name        VARCHAR(60)   NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    sku         VARCHAR(40),
    sort_order  INTEGER       NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_variants_price CHECK (price >= 0)
);
CREATE INDEX ix_variants_product ON product_variants (product_id);

-- ---------------------------------------------------------------- Inventario

CREATE TABLE warehouses (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenants (id),
    venue_id    UUID         NOT NULL REFERENCES venues (id),
    name        VARCHAR(80)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_warehouses_venue ON warehouses (venue_id);

-- Existencias de una variante en un almacen. El CHECK impide quedar en negativo incluso ante errores de codigo.
CREATE TABLE stock_levels (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    warehouse_id  UUID        NOT NULL REFERENCES warehouses (id),
    variant_id    UUID        NOT NULL REFERENCES product_variants (id),
    quantity      INTEGER     NOT NULL DEFAULT 0,
    -- Por debajo de este valor se avisa al equipo (alerta de stock bajo). 0 = sin aviso.
    min_quantity  INTEGER     NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_stock_levels UNIQUE (warehouse_id, variant_id),
    CONSTRAINT ck_stock_levels_quantity CHECK (quantity >= 0 AND min_quantity >= 0)
);

-- Historial de movimientos (solo se inserta). quantity con signo: +entrada, -salida.
CREATE TABLE stock_movements (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    warehouse_id  UUID         NOT NULL REFERENCES warehouses (id),
    variant_id    UUID         NOT NULL REFERENCES product_variants (id),
    type          VARCHAR(20)  NOT NULL,
    quantity      INTEGER      NOT NULL,
    reason        VARCHAR(255),
    sale_id       UUID,
    user_id       UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_stock_movements_type CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT', 'SALE', 'SALE_VOID')),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity <> 0)
);
CREATE INDEX ix_stock_movements_warehouse ON stock_movements (warehouse_id, created_at DESC);

-- ---------------------------------------------------------------- Terminales y cajas

CREATE TABLE pos_terminals (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    venue_id      UUID         NOT NULL REFERENCES venues (id),
    warehouse_id  UUID         NOT NULL REFERENCES warehouses (id),
    name          VARCHAR(80)  NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_pos_terminals_type CHECK (type IN ('BAR', 'BOX_OFFICE'))
);
CREATE INDEX ix_pos_terminals_venue ON pos_terminals (venue_id);

-- Turno de caja: apertura con fondo inicial y cierre con arqueo.
CREATE TABLE cash_sessions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    terminal_id     UUID          NOT NULL REFERENCES pos_terminals (id),
    status          VARCHAR(20)   NOT NULL,
    opened_by       UUID          NOT NULL REFERENCES users (id),
    opened_at       TIMESTAMPTZ   NOT NULL,
    opening_float   NUMERIC(10, 2) NOT NULL,
    closed_by       UUID REFERENCES users (id),
    closed_at       TIMESTAMPTZ,
    expected_cash   NUMERIC(10, 2),
    counted_cash    NUMERIC(10, 2),
    difference      NUMERIC(10, 2),
    closing_notes   VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_cash_sessions_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_cash_sessions_float CHECK (opening_float >= 0 AND (counted_cash IS NULL OR counted_cash >= 0))
);
-- BLOQUEO POR TERMINAL: como mucho una caja abierta por terminal, aunque dos personas la abran a la vez.
CREATE UNIQUE INDEX ux_cash_sessions_one_open ON cash_sessions (terminal_id) WHERE status = 'OPEN';
CREATE INDEX ix_cash_sessions_terminal ON cash_sessions (terminal_id, opened_at DESC);

-- Entradas y salidas de efectivo que no son ventas (cambio, pago a un proveedor, retirada a caja fuerte...).
CREATE TABLE cash_movements (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL REFERENCES tenants (id),
    cash_session_id  UUID          NOT NULL REFERENCES cash_sessions (id),
    type             VARCHAR(20)   NOT NULL,
    amount           NUMERIC(10, 2) NOT NULL,
    reason           VARCHAR(255)  NOT NULL,
    user_id          UUID          NOT NULL REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_cash_movements_type CHECK (type IN ('CASH_IN', 'CASH_OUT')),
    CONSTRAINT ck_cash_movements_amount CHECK (amount > 0)
);
CREATE INDEX ix_cash_movements_session ON cash_movements (cash_session_id);

-- ---------------------------------------------------------------- Ventas

CREATE TABLE pos_sales (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID          NOT NULL REFERENCES tenants (id),
    terminal_id             UUID          NOT NULL REFERENCES pos_terminals (id),
    cash_session_id         UUID          NOT NULL REFERENCES cash_sessions (id),
    reference               VARCHAR(20)   NOT NULL UNIQUE,
    status                  VARCHAR(20)   NOT NULL,
    subtotal_amount         NUMERIC(10, 2) NOT NULL,
    discount_amount         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total_amount            NUMERIC(10, 2) NOT NULL,
    payment_method          VARCHAR(20)   NOT NULL,
    cash_received           NUMERIC(10, 2),
    change_given            NUMERIC(10, 2),
    discount_reason         VARCHAR(255),
    discount_authorized_by  UUID REFERENCES users (id),
    sold_by                 UUID          NOT NULL REFERENCES users (id),
    -- Clave del cliente TPV: un doble toque o un reintento no cobran dos veces.
    idempotency_key         VARCHAR(80)   NOT NULL,
    voided_by               UUID REFERENCES users (id),
    voided_at               TIMESTAMPTZ,
    void_reason             VARCHAR(255),
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_pos_sales_status CHECK (status IN ('COMPLETED', 'VOIDED')),
    CONSTRAINT ck_pos_sales_method CHECK (payment_method IN ('CASH', 'CARD')),
    CONSTRAINT ck_pos_sales_amounts CHECK (subtotal_amount >= 0 AND discount_amount >= 0
        AND discount_amount <= subtotal_amount AND total_amount = subtotal_amount - discount_amount),
    CONSTRAINT ux_pos_sales_idempotency UNIQUE (terminal_id, idempotency_key)
);
CREATE INDEX ix_pos_sales_session ON pos_sales (cash_session_id, created_at DESC);
CREATE INDEX ix_pos_sales_tenant_created ON pos_sales (tenant_id, created_at DESC);

CREATE TABLE pos_sale_items (
    id             UUID PRIMARY KEY,
    sale_id        UUID          NOT NULL REFERENCES pos_sales (id) ON DELETE CASCADE,
    variant_id     UUID          NOT NULL REFERENCES product_variants (id),
    product_name   VARCHAR(120)  NOT NULL,
    variant_name   VARCHAR(60)   NOT NULL,
    quantity       INTEGER       NOT NULL,
    unit_price     NUMERIC(10, 2) NOT NULL,
    line_total     NUMERIC(10, 2) NOT NULL,
    CONSTRAINT ck_pos_items_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_pos_sale_items_sale ON pos_sale_items (sale_id);

-- ---------------------------------------------------------------- Permisos

INSERT INTO permissions (code, description) VALUES
    ('POS_SELL',         'Abrir y cerrar caja, vender en el TPV y registrar movimientos de efectivo'),
    ('POS_SUPERVISE',    'Autorizar descuentos y anular ventas del TPV'),
    ('POS_MANAGE',       'Gestionar el catalogo de productos, los terminales y consultar los cierres de caja'),
    ('INVENTORY_VIEW',   'Consultar existencias y movimientos de inventario'),
    ('INVENTORY_MANAGE', 'Gestionar almacenes y registrar entradas, salidas y ajustes de inventario');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'POS_SELL'),
    ('TENANT_ADMIN', 'POS_SUPERVISE'),
    ('TENANT_ADMIN', 'POS_MANAGE'),
    ('TENANT_ADMIN', 'INVENTORY_VIEW'),
    ('TENANT_ADMIN', 'INVENTORY_MANAGE'),
    ('MANAGER', 'POS_SELL'),
    ('MANAGER', 'POS_SUPERVISE'),
    ('MANAGER', 'POS_MANAGE'),
    ('MANAGER', 'INVENTORY_VIEW'),
    ('MANAGER', 'INVENTORY_MANAGE'),
    ('CASHIER', 'POS_SELL'),
    ('CASHIER', 'INVENTORY_VIEW');

UPDATE roles SET description = 'Opera el TPV de barra y taquilla en los locales asignados' WHERE code = 'CASHIER';
