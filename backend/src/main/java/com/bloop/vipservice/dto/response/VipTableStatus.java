package com.bloop.vipservice.dto.response;

/** Estado de una mesa en el plano en vivo (se calcula, no se guarda). */
public enum VipTableStatus {
    /** Sin reserva en ese momento. */
    AVAILABLE,
    /** Reservada sin importe previo que pagar (o solicitud web pendiente de confirmar). */
    RESERVED,
    /** Reservada pero falta pagar la reserva y el paquete. */
    PENDING_PAYMENT,
    /** Reserva y paquete pagados: el grupo puede entrar. */
    PAID,
    /** El grupo ya esta en la mesa. */
    OCCUPIED,
    /** En la mesa y con pedidos sin entregar. */
    SERVICE_REQUESTED,
    /** Mesa desactivada. */
    INACTIVE
}
