package com.bloop.promoters.dto.request;

import com.bloop.promoters.entity.CommissionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Cambio de comision o desactivacion de un promotor (su codigo de enlace no cambia). */
public record UpdatePromoterRequest(
        @NotNull CommissionType commissionType,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal commissionValue,
        boolean active) {
}
