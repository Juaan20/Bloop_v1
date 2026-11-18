package com.bloop.reservations.entity;

/** Origen de la reserva: solicitada por el cliente en la web o creada por el equipo. */
public enum ReservationSource {
    WEB,
    BACKOFFICE,
    /** Vendida en taquilla a un grupo que llega sin reserva. */
    WALK_IN
}
