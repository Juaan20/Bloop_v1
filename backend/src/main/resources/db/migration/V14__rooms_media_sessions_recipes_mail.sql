-- =====================================================================
-- V14 - Salas con plano propio, imagenes en la nube, carteles por sesion,
--       recetas de combinados (inventario por ml) y correos HTML.
-- =====================================================================

-- ---------------------------------------------------------------- Sesiones (dias) de un evento
-- Cada dia de un evento puede tener su titulo, su descripcion y su propio cartel.
-- La imagen general sigue en events.image_url.
ALTER TABLE event_sessions
    ADD COLUMN title        VARCHAR(120),
    ADD COLUMN description  VARCHAR(1000),
    ADD COLUMN image_url    VARCHAR(500),
    -- Permite pausar la venta de un dia concreto sin tocar sus tipos de entrada.
    ADD COLUMN sales_paused BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------- Imagenes (Cloudinary o disco local en dev)
-- La BD guarda solo la URL y el identificador del proveedor, nunca los bytes.
CREATE TABLE media_assets (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id),
    provider      VARCHAR(20)  NOT NULL,
    -- Identificador en el proveedor (public_id de Cloudinary o nombre del fichero local).
    storage_key   VARCHAR(255) NOT NULL,
    url           VARCHAR(500) NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    size_bytes    INTEGER      NOT NULL,
    width         INTEGER,
    height        INTEGER,
    original_name VARCHAR(200),
    uploaded_by   UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_media_provider CHECK (provider IN ('CLOUDINARY', 'LOCAL')),
    CONSTRAINT ck_media_size CHECK (size_bytes > 0),
    CONSTRAINT ux_media_storage_key UNIQUE (provider, storage_key)
);
CREATE INDEX ix_media_assets_tenant ON media_assets (tenant_id, created_at DESC);

INSERT INTO permissions (code, description) VALUES
    ('MEDIA_MANAGE', 'Subir, reutilizar y borrar imagenes de la empresa');
INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'MEDIA_MANAGE'),
    ('MANAGER', 'MEDIA_MANAGE');

-- ---------------------------------------------------------------- Salas VIP con plano propio
-- Una zona VIP pasa a ser una "sala" del local: tiene su forma, su aforo y su color.
ALTER TABLE vip_zones
    ADD COLUMN layout   TEXT,
    ADD COLUMN capacity INTEGER,
    ADD COLUMN color    VARCHAR(20) NOT NULL DEFAULT 'violet',
    ADD CONSTRAINT ck_vip_zones_capacity CHECK (capacity IS NULL OR capacity > 0);

-- La forma que se disenaba por local se copia a cada una de sus salas (no se pierde nada).
UPDATE vip_zones z SET layout = p.layout
FROM venue_floor_plans p
WHERE p.venue_id = z.venue_id;

DROP TABLE venue_floor_plans;

-- ---------------------------------------------------------------- Inventario por ml y recetas
-- stock_unit = UNIT: las existencias se cuentan en unidades (latas, botellines...).
-- stock_unit = ML: se cuentan en mililitros; unit_size_ml dice cuantos ml trae cada botella.
ALTER TABLE product_variants
    ADD COLUMN stock_unit     VARCHAR(10) NOT NULL DEFAULT 'UNIT',
    ADD COLUMN unit_size_ml   INTEGER,
    ADD COLUMN cost           NUMERIC(10, 2),
    -- Copa que se sirve con refresco: el TPV pide elegir el refresco.
    ADD COLUMN requires_mixer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_variants_stock_unit CHECK (stock_unit IN ('UNIT', 'ML')),
    ADD CONSTRAINT ck_variants_unit_size CHECK (
        (stock_unit = 'UNIT' AND unit_size_ml IS NULL) OR (stock_unit = 'ML' AND unit_size_ml > 0)),
    ADD CONSTRAINT ck_variants_cost CHECK (cost IS NULL OR cost >= 0);

-- Producto que se puede elegir como refresco de un combinado (Coca-Cola, Tonica, Red Bull...).
-- NULL = no es refresco de combinado. Valor = lo que suma al precio de la copa (0 = sin suplemento).
-- Es distinto del precio del producto: una lata de Coca-Cola vale 3 EUR sola y 0 EUR dentro de un cubata.
ALTER TABLE products
    ADD COLUMN mixer_surcharge NUMERIC(10, 2),
    ADD CONSTRAINT ck_products_mixer_surcharge CHECK (mixer_surcharge IS NULL OR mixer_surcharge >= 0);

-- Receta: lo que se descuenta del inventario al vender una variante.
-- Ej.: "Ron Barcelo - Copa" -> 50 ml de "Ron Barcelo - Botella 70cl".
CREATE TABLE variant_recipe_items (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID    NOT NULL REFERENCES tenants (id),
    variant_id            UUID    NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    component_variant_id  UUID    NOT NULL REFERENCES product_variants (id),
    -- En la unidad de inventario del componente (ml o unidades).
    quantity              INTEGER NOT NULL,
    CONSTRAINT ck_recipe_quantity CHECK (quantity > 0),
    CONSTRAINT ck_recipe_not_self CHECK (variant_id <> component_variant_id),
    CONSTRAINT ux_recipe_component UNIQUE (variant_id, component_variant_id)
);
CREATE INDEX ix_recipe_items_variant ON variant_recipe_items (variant_id);

-- Refresco elegido en una linea del TPV (el precio de la linea ya incluye su suplemento).
ALTER TABLE pos_sale_items
    ADD COLUMN mixer_variant_id UUID REFERENCES product_variants (id),
    ADD COLUMN mixer_name       VARCHAR(120);

-- ---------------------------------------------------------------- Correos HTML
-- Version HTML del correo (la columna body conserva la version de texto plano).
ALTER TABLE notifications ADD COLUMN html_body TEXT;
