package com.bloop.notifications.entity;

/** Tipos de alertas administrativas. */
public enum AlertType {
    /** Un cliente ha solicitado una reserva VIP desde la web (hay que confirmarla). */
    RESERVATION_REQUESTED,
    /** Un cliente ha cancelado su reserva VIP. */
    RESERVATION_CANCELLED,
    /** Un tipo de entrada ha vendido el 90 % de su stock. */
    LOW_STOCK,
    /** Un tipo de entrada se ha agotado. */
    SOLD_OUT,
    /** Las existencias de un producto en un almacen han bajado del minimo. */
    LOW_INVENTORY,
    /** Un reembolso automatico (p. ej. por cancelacion de un evento) ha fallado y hay que revisarlo. */
    REFUND_FAILED
}
