package com.bloop.reservations.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta y edicion de una sala. Desactivarla oculta sus mesas para nuevas reservas. */
public record VipZoneRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        boolean active,
        @Min(0) @Max(1000) int sortOrder,
        @Min(1) @Max(100000) Integer capacity,
        @Pattern(regexp = "violet|sky|emerald|amber|rose|zinc", message = "Color no valido") String color) {

    public VipZoneRequest(String name, String description, boolean active, int sortOrder) {
        this(name, description, active, sortOrder, null, null);
    }
}
