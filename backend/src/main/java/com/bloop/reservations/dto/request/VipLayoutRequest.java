package com.bloop.reservations.dto.request;

import com.bloop.reservations.entity.VipTableShape;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Plano completo de un local: posicion, tamano y forma de cada mesa (en % del lienzo). */
public record VipLayoutRequest(@NotNull @Size(max = 300) List<@Valid TablePosition> tables) {

    /** posX/posY null = mesa sin colocar en el plano. */
    public record TablePosition(
            @NotNull UUID id,
            @DecimalMin("0") @DecimalMax("100") BigDecimal posX,
            @DecimalMin("0") @DecimalMax("100") BigDecimal posY,
            @NotNull @DecimalMin("2") @DecimalMax("40") BigDecimal width,
            @NotNull @DecimalMin("2") @DecimalMax("40") BigDecimal height,
            @NotNull VipTableShape shape) {
    }
}
