package com.bloop.promoters.dto.request;

import com.bloop.promoters.entity.DiscountType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Alta y edicion de un codigo promocional. Al editar, el codigo (texto) no se puede cambiar.
 * eventId y promoterId son opcionales.
 */
public record PromoCodeRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{3,30}", message = "De 3 a 30 letras, numeros o guiones") String code,
        @Size(max = 255) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") @DecimalMax("99999.99") @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        UUID eventId,
        UUID promoterId,
        @Min(1) @Max(1000000) Integer maxUses,
        Instant validFrom,
        Instant validUntil,
        boolean active) {
}
