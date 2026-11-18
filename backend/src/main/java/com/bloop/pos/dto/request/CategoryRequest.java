package com.bloop.pos.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta y edicion de una categoria del TPV. color: clave de la paleta de botones. */
public record CategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Pattern(regexp = "violet|amber|emerald|sky|rose|zinc", message = "Color no valido") String color,
        @Min(0) @Max(1000) int sortOrder,
        boolean active,
        /** Tipo de categoria (barra, botellas VIP, servicio, otros). null = BAR. */
        com.bloop.pos.entity.CategoryKind kind) {
}
