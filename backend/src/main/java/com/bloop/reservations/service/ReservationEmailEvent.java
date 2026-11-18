package com.bloop.reservations.service;

import java.util.UUID;

/**
 * Evento de aplicacion para enviar un correo de reserva DESPUES del commit
 * (ReservationNotificationListener). Asi nunca se avisa de una reserva que luego se deshizo.
 */
public record ReservationEmailEvent(UUID reservationId, Kind kind) {

    public enum Kind {
        /** Solicitud recibida desde la web (pendiente de confirmar). */
        RECEIVED,
        CONFIRMED,
        CANCELLED,
        /** Pago de la reserva registrado (en el local o en taquilla). */
        PAID
    }
}
