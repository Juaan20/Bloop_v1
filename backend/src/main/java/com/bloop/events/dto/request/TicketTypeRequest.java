package com.bloop.events.dto.request;

import com.bloop.events.entity.TicketCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketTypeRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull TicketCategory category,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal price,
        @Min(0) @Max(100000) int quantityTotal,
        @Min(1) @Max(50) int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        boolean active,
        @Min(0) @Max(1000) int sortOrder) {
}
