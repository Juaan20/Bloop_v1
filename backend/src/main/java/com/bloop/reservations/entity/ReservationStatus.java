package com.bloop.reservations.entity;

import java.util.Set;

/**
 * Estados de una reserva y transiciones permitidas:
 *
 * <pre>
 * PENDING ---> CONFIRMED ---> SEATED ---> COMPLETED
 *    |             |  \
 *    v             v   -> NO_SHOW
 * CANCELLED    CANCELLED
 * </pre>
 *
 * Las reservas PENDING, CONFIRMED y SEATED "ocupan" la mesa (cuentan para los solapamientos).
 */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    SEATED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    /** Estados que bloquean la mesa. Deben coincidir con el WHERE de la restriccion EXCLUDE (V7). */
    public static final Set<ReservationStatus> ACTIVE = Set.of(PENDING, CONFIRMED, SEATED);

    public boolean canChangeTo(ReservationStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SEATED || next == CANCELLED || next == NO_SHOW;
            case SEATED -> next == COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
