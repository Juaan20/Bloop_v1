package com.bloop.pos.dto.request;

import com.bloop.pos.entity.PaymentMethod;
import com.bloop.promoters.entity.DiscountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Venta del TPV.
 *
 * @param cashReceived efectivo entregado por el cliente (obligatorio si se paga en efectivo)
 * @param discount     descuento opcional; requiere autorizacion de un supervisor
 */
public record PosSaleRequest(
        @NotEmpty(message = "El ticket esta vacio") @Size(max = 50) List<@Valid Line> items,
        @NotNull PaymentMethod paymentMethod,
        @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal cashReceived,
        @Valid Discount discount) {

    /** @param mixerVariantId refresco elegido (obligatorio si la variante lo pide) */
    public record Line(@NotNull UUID variantId, @Min(1) @Max(100) int quantity, UUID mixerVariantId) {

        public Line(UUID variantId, int quantity) {
            this(variantId, quantity, null);
        }
    }

    /**
     * Si quien vende no tiene POS_SUPERVISE, un supervisor autoriza el descuento escribiendo
     * su email y su contrasena en el propio TPV.
     */
    public record Discount(
            @NotNull DiscountType type,
            @NotNull @DecimalMin("0.01") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal value,
            @NotBlank @Size(max = 255) String reason,
            @Email @Size(max = 255) String supervisorEmail,
            @Size(max = 100) String supervisorPassword) {
    }
}
