package com.bloop.reservations.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Forma de un local. La sala es una rejilla de cols x rows celdas: "cells" tiene un caracter por celda
 * ('1' = suelo del local, '0' = fuera), fila a fila. Asi se dibuja cualquier forma (L, U, T, libre).
 * Los elementos (barra, escenario...) se colocan en % del lienzo, igual que las mesas.
 */
public record FloorPlanRequest(
        @Min(8) @Max(40) int cols,
        @Min(6) @Max(30) int rows,
        @NotNull @Pattern(regexp = "[01]+", message = "Celdas no validas") @Size(max = 1200) String cells,
        @NotNull @Size(max = 80) List<@Valid Element> elements) {

    public enum ElementType { STAGE, DJ, BAR, DANCEFLOOR, ENTRANCE, TOILETS, COLUMN, STAIRS, CLOAKROOM, LABEL }

    public record Element(
            @NotNull ElementType type,
            @Size(max = 40) String label,
            @DecimalMin("0") @DecimalMax("100") double x,
            @DecimalMin("0") @DecimalMax("100") double y,
            @DecimalMin("1") @DecimalMax("100") double w,
            @DecimalMin("1") @DecimalMax("100") double h) {
    }
}
