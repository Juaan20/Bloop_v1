-- =====================================================================
-- V5 - CRM de clientes por empresa y consentimientos (RGPD).
--
-- Un mismo usuario cliente (users) puede comprar a varias empresas: cada empresa
-- tiene SU propia ficha de cliente (customers) con sus estadisticas y su consentimiento
-- comercial. Asi una empresa nunca ve lo que el cliente compra a otra.
-- =====================================================================

CREATE TABLE customers (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID          NOT NULL REFERENCES tenants (id),
    -- Cuenta del cliente en la web. NULL si la ficha se anonimizo (derecho de supresion).
    user_id            UUID REFERENCES users (id),
    email              VARCHAR(255)  NOT NULL,
    first_name         VARCHAR(80)   NOT NULL,
    last_name          VARCHAR(120)  NOT NULL,
    phone              VARCHAR(40),
    -- Notas internas y etiquetas libres separadas por comas (segmentacion manual).
    notes              VARCHAR(2000),
    tags               VARCHAR(500),
    -- Consentimiento para comunicaciones comerciales DE ESTA EMPRESA.
    marketing_consent  BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Estadisticas que se actualizan con cada pedido pagado.
    orders_count       INTEGER       NOT NULL DEFAULT 0,
    tickets_count      INTEGER       NOT NULL DEFAULT 0,
    total_spent        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    first_purchase_at  TIMESTAMPTZ,
    last_purchase_at   TIMESTAMPTZ,
    anonymized_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_customers_counts CHECK (orders_count >= 0 AND tickets_count >= 0 AND total_spent >= 0)
);
-- Una ficha por cuenta y empresa.
CREATE UNIQUE INDEX ux_customers_tenant_user ON customers (tenant_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX ix_customers_tenant_last_purchase ON customers (tenant_id, last_purchase_at DESC);
CREATE INDEX ix_customers_user ON customers (user_id);

-- Historial de consentimientos: cada cambio queda registrado (prueba ante una reclamacion).
CREATE TABLE customer_consents (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    customer_id  UUID        NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL,
    granted      BOOLEAN     NOT NULL,
    -- Donde se dio o retiro: compra, panel del cliente o backoffice.
    source       VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_consents_type CHECK (type IN ('MARKETING')),
    CONSTRAINT ck_consents_source CHECK (source IN ('CHECKOUT', 'CUSTOMER', 'BACKOFFICE', 'MIGRATION'))
);
CREATE INDEX ix_consents_customer ON customer_consents (customer_id, created_at DESC);

-- El cliente puede marcar en la compra que acepta comunicaciones del organizador.
ALTER TABLE ticket_orders ADD COLUMN marketing_consent BOOLEAN NOT NULL DEFAULT FALSE;

-- Fichas para los pedidos ya pagados antes de esta migracion.
INSERT INTO customers (id, tenant_id, user_id, email, first_name, last_name, phone, orders_count, tickets_count,
                       total_spent, first_purchase_at, last_purchase_at, created_at, updated_at)
SELECT gen_random_uuid(), o.tenant_id, u.id, u.email, u.first_name, u.last_name, u.phone,
       COUNT(DISTINCT o.id),
       COALESCE(SUM(i.quantity), 0),
       COALESCE((SELECT SUM(o2.total_amount) FROM ticket_orders o2
                 WHERE o2.tenant_id = o.tenant_id AND o2.customer_user_id = u.id AND o2.status = 'PAID'), 0),
       MIN(o.paid_at), MAX(o.paid_at), NOW(), NOW()
FROM ticket_orders o
JOIN users u ON u.id = o.customer_user_id
JOIN ticket_order_items i ON i.order_id = o.id
WHERE o.status = 'PAID'
GROUP BY o.tenant_id, u.id, u.email, u.first_name, u.last_name, u.phone;

-- ---------------------------------------------------------------------
-- Permisos nuevos
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, description) VALUES
    ('CUSTOMER_VIEW',   'Consultar el CRM de clientes'),
    ('CUSTOMER_MANAGE', 'Editar clientes y atender derechos RGPD (exportacion y supresion)'),
    ('ORDER_MANAGE',    'Reenviar entradas y cambiar el titular de una entrada');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'CUSTOMER_VIEW'),
    ('TENANT_ADMIN', 'CUSTOMER_MANAGE'),
    ('TENANT_ADMIN', 'ORDER_MANAGE'),
    ('MANAGER', 'CUSTOMER_VIEW'),
    ('MANAGER', 'ORDER_MANAGE');
