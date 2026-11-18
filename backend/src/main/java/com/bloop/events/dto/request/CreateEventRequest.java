package com.bloop.events.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Crea un evento en borrador junto con su primera sesion. */
public record CreateEventRequest(
        @NotNull UUID venueId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 8000) String description,
        @Size(max = 500) @Pattern(regexp = "^$|https?://.+", message = "Debe ser una URL http(s)") String imageUrl,
        @Min(0) @Max(99) Integer minAge,
        @NotNull @Valid SessionRequest firstSession) {
}
