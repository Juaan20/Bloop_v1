package com.bloop.reservations.dto.request;

import com.bloop.pos.entity.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Mesa vendida en taquilla: el grupo llega sin reserva. Se crea la reserva para la fiesta,
 * se cobra la reserva + paquete (si tiene precio) y el grupo pasa directamente a la mesa.
 */
public record WalkInRequest(
        @NotNull UUID tableId,
        @Min(1) @Max(100) int guests,
        @NotBlank @Size(max = 200) String contactName,
        @Size(max = 40) String contactPhone,
        @Email @Size(max = 255) String contactEmail,
        /** Fiesta en curso (sesion del evento): la mesa se vende para toda la fiesta. */
        @NotNull(message = "Elige la fiesta") UUID sessionId,
        /** Obligatorio si la mesa tiene precio. */
        PaymentMethod paymentMethod,
        @Size(max = 1000) String notes) {
}
