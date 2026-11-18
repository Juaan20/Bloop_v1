package com.bloop.promoters.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Lista COMPLETA de eventos asignados al promotor (sustituye a la anterior). */
public record PromoterAssignmentsRequest(@NotNull @Size(max = 200) List<@Valid Item> assignments) {

    /** targetTickets: objetivo de entradas vendidas (opcional). */
    public record Item(@NotNull UUID eventId, @Min(1) @Max(100000) Integer targetTickets) {
    }
}
