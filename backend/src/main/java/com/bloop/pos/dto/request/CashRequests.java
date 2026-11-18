package com.bloop.pos.dto.request;

import com.bloop.pos.entity.CashMovementType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Peticiones de caja: apertura, movimiento de efectivo y cierre (arqueo). */
public final class CashRequests {

    private CashRequests() {
    }

    /** Fondo inicial de la caja (efectivo para dar cambio). */
    public record Open(
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal openingFloat) {
    }

    public record Movement(
            @NotNull CashMovementType type,
            @NotNull @DecimalMin("0.01") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String reason) {
    }

    /** Efectivo contado al cerrar. La diferencia con el esperado queda registrada. */
    public record Close(
            @NotNull @DecimalMin("0.00") @DecimalMax("999999.99") @Digits(integer = 8, fraction = 2) BigDecimal countedCash,
            @Size(max = 500) String notes) {
    }
}
