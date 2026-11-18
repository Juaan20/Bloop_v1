package com.bloop.orders.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Carrito que envia la web publica: una sesion y las cantidades de cada tipo de entrada.
 *
 * @param promoCode        codigo promocional escrito por el cliente (opcional)
 * @param referralCode     codigo del enlace de un promotor (?rrpp=...), opcional
 * @param marketingConsent el cliente acepta comunicaciones comerciales del organizador
 */
public record CheckoutRequest(
        @NotNull UUID sessionId,
        @NotEmpty(message = "El carrito esta vacio") @Size(max = 10) List<@Valid CheckoutItemRequest> items,
        @Size(max = 30) String promoCode,
        @Size(max = 30) String referralCode,
        boolean marketingConsent,
        /** Telefono de contacto del comprador (opcional). */
        @Size(max = 40) @jakarta.validation.constraints.Pattern(regexp = "^[0-9+()\\s-]{0,40}$", message = "Telefono no valido") String buyerPhone,
        /** Un asistente por entrada. Obligatorio si el evento tiene edad minima. */
        @Size(max = 50) List<@Valid AttendeeRequest> attendees) {
}
