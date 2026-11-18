package com.bloop.orders.entity;

/**
 * Transiciones permitidas:
 * PENDING_PAYMENT -> PAID      (pago confirmado: se emiten las entradas)
 * PENDING_PAYMENT -> EXPIRED   (no se paga a tiempo: se libera el stock)
 * PENDING_PAYMENT -> CANCELLED (el cliente lo cancela: se libera el stock)
 * PAID            -> REFUNDED  (fase 4, con Stripe)
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    EXPIRED,
    REFUNDED
}
