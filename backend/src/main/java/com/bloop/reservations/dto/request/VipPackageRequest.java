package com.bloop.reservations.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Productos incluidos en el paquete de una mesa (sustituye el paquete anterior). */
public record VipPackageRequest(@NotNull @Size(max = 20) List<@Valid Item> items) {

    public record Item(@NotNull UUID variantId, @Min(1) @Max(99) int quantity) {
    }
}
