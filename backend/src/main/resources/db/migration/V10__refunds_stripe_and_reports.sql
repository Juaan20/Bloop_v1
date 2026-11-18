-- =====================================================================
-- V10 - Fase 4: reembolsos, Stripe en modo test, informes y rendimiento.
-- =====================================================================

-- ---------- Pagos: referencia del cobro y datos del reembolso ----------
-- provider_reference: en Stripe, el PaymentIntent (pi_...) que se reembolsa.
ALTER TABLE payments
    ADD COLUMN provider_reference  VARCHAR(100),
    ADD COLUMN refunded_amount     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN provider_refund_id  VARCHAR(100),
    ADD COLUMN refunded_at         TIMESTAMPTZ,
    ADD CONSTRAINT ck_payments_refund CHECK (refunded_amount >= 0 AND refunded_amount <= amount);

-- ---------- Pedidos: cuando y por que se reembolsaron ----------
ALTER TABLE ticket_orders
    ADD COLUMN refunded_at    TIMESTAMPTZ,
    ADD COLUMN refund_reason  VARCHAR(255);

-- ---------- Webhooks de Stripe ya procesados ----------
-- Stripe puede enviar el mismo evento varias veces: guardar su id evita procesarlo dos veces.
CREATE TABLE stripe_webhook_events (
    event_id     VARCHAR(100) PRIMARY KEY,
    type         VARCHAR(100) NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL
);

-- ---------- Permisos nuevos ----------
INSERT INTO permissions (code, description) VALUES
    ('ORDER_REFUND', 'Reembolsar pedidos de entradas'),
    ('REPORT_VIEW',  'Consultar la analitica avanzada y descargar informes');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('TENANT_ADMIN', 'ORDER_REFUND'),
    ('TENANT_ADMIN', 'REPORT_VIEW'),
    ('MANAGER',      'ORDER_REFUND'),
    ('MANAGER',      'REPORT_VIEW');

-- ---------- Indices para la analitica (revision de rendimiento) ----------
-- Ventas de entradas por fecha de pago (solo las filas que cuentan como venta o reembolso).
CREATE INDEX ix_orders_tenant_paid ON ticket_orders (tenant_id, paid_at)
    WHERE status IN ('PAID', 'REFUNDED');
-- Reembolsos por fecha.
CREATE INDEX ix_payments_tenant_refunded ON payments (tenant_id, refunded_at) WHERE refunded_at IS NOT NULL;
-- Productos mas vendidos del TPV.
CREATE INDEX ix_pos_sale_items_variant ON pos_sale_items (variant_id);
-- Ventas del TPV por terminal.
CREATE INDEX ix_pos_sales_terminal_created ON pos_sales (terminal_id, created_at DESC);
