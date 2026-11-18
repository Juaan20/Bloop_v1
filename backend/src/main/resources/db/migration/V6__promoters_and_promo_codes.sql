-- =====================================================================
-- V6 - Promotores / RRPP, codigos promocionales y atribucion de ventas.
-- =====================================================================

-- Un promotor es un empleado con rol PROMOTER y una configuracion comercial.
CREATE TABLE promoters (
    id                UUID PRIMARY KEY,
    tenant_id         UUID          NOT NULL REFERENCES tenants (id),
    user_id           UUID          NOT NULL REFERENCES users (id),
    -- Codigo del enlace de venta: /eventos/{slug}?rrpp=CODIGO. Unico en toda la plataforma
    -- porque la URL publica no indica la empresa.
    referral_code     VARCHAR(30)   NOT NULL UNIQUE,
    commission_type   VARCHAR(20)   NOT NULL,
    commission_value  NUMERIC(10, 2) NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ux_promoters_user UNIQUE (user_id),
    CONSTRAINT ck_promoters_commission_type CHECK (commission_type IN ('PERCENTAGE', 'FIXED_PER_TICKET')),
    CONSTRAINT ck_promoters_commission_value CHECK (commission_value >= 0
        AND (commission_type <> 'PERCENTAGE' OR commission_value <= 100))
);
CREATE INDEX ix_promoters_tenant ON promoters (tenant_id);

-- Eventos asignados a cada promotor, con un objetivo opcional de entradas vendidas.
CREATE TABLE promoter_events (
    promoter_id     UUID    NOT NULL REFERENCES promoters (id) ON DELETE CASCADE,
    event_id        UUID    NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    target_tickets  INTEGER,
    PRIMARY KEY (promoter_id, event_id),
    CONSTRAINT ck_promoter_events_target CHECK (target_tickets IS NULL OR target_tickets > 0)
);
CREATE INDEX ix_promoter_events_event ON promoter_events (event_id);

-- Codigos promocionales. times_used se incrementa con un UPDATE condicional atomico
-- (igual que el stock de entradas) y el CHECK impide superar max_uses.
CREATE TABLE promo_codes (
    id              UUID PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    code            VARCHAR(30)   NOT NULL,
    description     VARCHAR(255),
    discount_type   VARCHAR(20)   NOT NULL,
    discount_value  NUMERIC(10, 2) NOT NULL,
    -- NULL = valido para todos los eventos de la empresa.
    event_id        UUID REFERENCES events (id) ON DELETE CASCADE,
    -- Si pertenece a un promotor, las ventas con este codigo se le atribuyen.
    promoter_id     UUID REFERENCES promoters (id) ON DELETE SET NULL,
    max_uses        INTEGER,
    times_used      INTEGER       NOT NULL DEFAULT 0,
    valid_from      TIMESTAMPTZ,
    valid_until     TIMESTAMPTZ,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ux_promo_codes_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_promo_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT ck_promo_discount_value CHECK (discount_value > 0
        AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
    CONSTRAINT ck_promo_uses CHECK (times_used >= 0 AND (max_uses IS NULL OR (max_uses > 0 AND times_used <= max_uses))),
    CONSTRAINT ck_promo_window CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from)
);
CREATE INDEX ix_promo_codes_promoter ON promo_codes (promoter_id);

-- Pedido: importe antes del descuento, descuento aplicado, codigo usado y promotor atribuido.
ALTER TABLE ticket_orders ADD COLUMN subtotal_amount NUMERIC(10, 2);
UPDATE ticket_orders SET subtotal_amount = total_amount;
ALTER TABLE ticket_orders ALTER COLUMN subtotal_amount SET NOT NULL;
ALTER TABLE ticket_orders ADD COLUMN discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE ticket_orders ADD COLUMN promo_code_id UUID REFERENCES promo_codes (id) ON DELETE SET NULL;
-- Copia del codigo: el pedido historico conserva el texto aunque el codigo se borre.
ALTER TABLE ticket_orders ADD COLUMN promo_code VARCHAR(30);
ALTER TABLE ticket_orders ADD COLUMN promoter_id UUID REFERENCES promoters (id) ON DELETE SET NULL;
ALTER TABLE ticket_orders ADD COLUMN commission_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE ticket_orders ADD CONSTRAINT ck_orders_discount
    CHECK (discount_amount >= 0 AND discount_amount <= subtotal_amount AND commission_amount >= 0);
CREATE INDEX ix_orders_promoter ON ticket_orders (promoter_id) WHERE promoter_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Permisos nuevos
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, description) VALUES
    ('PROMOTER_MANAGE', 'Gestionar promotores, comisiones, objetivos y codigos promocionales'),
    ('PROMOTER_PANEL',  'Consultar el panel individual de promotor (ventas propias y comisiones)');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'PROMOTER_MANAGE'),
    ('MANAGER', 'PROMOTER_MANAGE'),
    ('PROMOTER', 'PROMOTER_PANEL');

UPDATE roles SET description = 'Vende con su enlace y sus codigos; consulta sus ventas y comisiones'
WHERE code = 'PROMOTER';
