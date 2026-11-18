package com.bloop.reservations.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Reserva creada por el equipo (telefono, WhatsApp, en persona...).
 * Siempre para una fiesta completa (sessionId): no hay reservas por horas.
 */
public record CreateReservationRequest(
        @NotNull UUID tableId,
        /** Fiesta (sesion del evento): la mesa se reserva para la fiesta completa, nunca por horas. */
        @NotNull(message = "Elige la fiesta") UUID sessionId,
        @Min(1) @Max(100) int guests,
        @NotBlank @Size(max = 200) String contactName,
        /** Opcional: sin email no se envian correos ni se enlaza con el CRM. */
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 40) String contactPhone,
        @Size(max = 1000) String notes) {
}
