package com.bloop.reservations.dto.request;

import com.bloop.reservations.entity.VipTableShape;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Alta y edicion de una mesa VIP. Las reservas ya hechas conservan sus importes.
 * depositAmount = precio de la reserva con su paquete (se paga antes de entrar).
 * shape/width/height son opcionales: si no llegan, se mantienen los actuales.
 */
public record VipTableRequest(
        @NotBlank @Size(max = 60) String name,
        @Min(1) @Max(100) int capacity,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal minSpend,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal depositAmount,
        boolean active,
        VipTableShape shape,
        @DecimalMin("2") @DecimalMax("40") BigDecimal width,
        @DecimalMin("2") @DecimalMax("40") BigDecimal height,
        java.util.UUID zoneId) {

    /** zoneId: sala a la que se mueve la mesa (opcional; debe ser del mismo local). */
    public VipTableRequest(String name, int capacity, BigDecimal minSpend, BigDecimal depositAmount, boolean active,
                           VipTableShape shape, BigDecimal width, BigDecimal height) {
        this(name, capacity, minSpend, depositAmount, active, shape, width, height, null);
    }
}
