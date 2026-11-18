package com.bloop.promoters.dto.request;

import com.bloop.promoters.entity.CommissionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alta de un promotor a partir de un empleado con rol PROMOTER.
 * referralCode es opcional: si no se indica, se genera uno.
 */
public record CreatePromoterRequest(
        @NotNull UUID userId,
        @Pattern(regexp = "^$|[A-Za-z0-9-]{3,30}", message = "De 3 a 30 letras, numeros o guiones") String referralCode,
        @NotNull CommissionType commissionType,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal commissionValue) {
}
