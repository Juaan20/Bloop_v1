-- =====================================================================
-- V16 - Inventario como en una discoteca:
--   * Proveedores: traen la mercancia con su albaran y se llevan los envases vacios.
--   * Entradas de proveedor (albaran y coste), traspasos almacen -> barra y recuentos.
--   * Las ventas descuentan pero nunca bloquean: el stock puede quedar en negativo hasta
--     el recuento, que fija la cantidad real (la diferencia es la merma).
--   * Envases retornables: se apunta lo que se devuelve al proveedor.
-- =====================================================================

CREATE TABLE suppliers (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    name          VARCHAR(150) NOT NULL,
    contact_name  VARCHAR(150),
    phone         VARCHAR(40),
    email         VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_suppliers_name ON suppliers (tenant_id, LOWER(name));

-- Entradas de proveedor y traspasos entre almacenes
ALTER TABLE stock_movements
    ADD COLUMN supplier_id  UUID REFERENCES suppliers (id),
    ADD COLUMN document_ref VARCHAR(60),
    ADD COLUMN unit_cost    NUMERIC(10, 2),
    DROP CONSTRAINT ck_stock_movements_type,
    ADD CONSTRAINT ck_stock_movements_type
        CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT', 'SALE', 'SALE_VOID', 'TRANSFER_IN', 'TRANSFER_OUT'));
CREATE INDEX ix_stock_movements_variant ON stock_movements (warehouse_id, variant_id, created_at);

-- Las ventas no se bloquean por stock: la cantidad teorica puede ser negativa hasta el recuento.
ALTER TABLE stock_levels
    DROP CONSTRAINT ck_stock_levels_quantity,
    ADD CONSTRAINT ck_stock_levels_min CHECK (min_quantity >= 0);

-- Envase retornable (botella, botellin, caja): el proveedor se lo lleva vacio.
ALTER TABLE product_variants ADD COLUMN returnable BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE container_returns (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    supplier_id   UUID        NOT NULL REFERENCES suppliers (id),
    variant_id    UUID        NOT NULL REFERENCES product_variants (id),
    quantity      INTEGER     NOT NULL,
    document_ref  VARCHAR(60),
    user_id       UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_container_returns_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_container_returns_supplier ON container_returns (supplier_id, created_at DESC);
