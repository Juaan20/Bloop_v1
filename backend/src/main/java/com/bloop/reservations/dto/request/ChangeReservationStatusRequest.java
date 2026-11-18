package com.bloop.reservations.dto.request;

import com.bloop.reservations.entity.ReservationStatus;
import jakarta.validation.constraints.NotNull;

/** Nuevo estado de la reserva (las transiciones validas estan en ReservationStatus). */
public record ChangeReservationStatusRequest(@NotNull ReservationStatus status) {
}
