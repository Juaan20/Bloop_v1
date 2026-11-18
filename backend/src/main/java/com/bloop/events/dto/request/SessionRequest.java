package com.bloop.events.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Un dia (sesion) de un evento: fechas en UTC (ISO-8601), aforo y, opcionalmente, su titulo,
 * descripcion y cartel propios. imageUrl debe ser una imagen subida con /api/media.
 */
public record SessionRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        Instant doorsOpenAt,
        @Min(1) @Max(100000) int capacity,
        @Size(max = 120) String title,
        @Size(max = 1000) String description,
        @Size(max = 500) String imageUrl,
        Boolean salesPaused) {

    /** Constructor corto (fechas y aforo), usado por los datos de demostracion y los tests. */
    public SessionRequest(Instant startsAt, Instant endsAt, Instant doorsOpenAt, int capacity) {
        this(startsAt, endsAt, doorsOpenAt, capacity, null, null, null, null);
    }
}
