package com.bloop.vipservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pedido de una mesa VIP. idempotencyKey la genera el movil o la tablet: si la peticion se repite
 * (doble toque, reintento por mala cobertura) se devuelve el mismo pedido en lugar de crear otro.
 */
public record VipOrderRequest(
        @NotBlank @Size(max = 80) String idempotencyKey,
        @NotEmpty @Size(max = 10) List<@Valid Line> lines,
        @Size(max = 300) String notes) {

    public record Line(@NotNull UUID variantId, @Min(1) @Max(20) int quantity) {
    }
}
