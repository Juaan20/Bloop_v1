package com.bloop.reservations.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Solicitud de reserva de mesa VIP desde la web para una sesion de un evento. */
public record RequestReservationRequest(
        @NotNull UUID sessionId,
        @NotNull UUID tableId,
        @Min(1) @Max(100) int guests,
        @Size(max = 40) String phone,
        @Size(max = 1000) String notes) {
}
