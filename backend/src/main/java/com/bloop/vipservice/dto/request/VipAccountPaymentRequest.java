package com.bloop.vipservice.dto.request;

import com.bloop.pos.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Cobro (total o parcial) de las consumiciones adicionales de una mesa. */
public record VipAccountPaymentRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotNull PaymentMethod method,
        @NotBlank @Size(max = 80) String idempotencyKey) {
}
